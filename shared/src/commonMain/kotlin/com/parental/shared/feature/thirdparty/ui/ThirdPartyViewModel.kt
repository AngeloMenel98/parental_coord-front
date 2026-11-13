package com.parental.shared.feature.thirdparty.ui

import com.parental.shared.core.error.ApiResult
import com.parental.shared.core.files.ImageCompressor
import com.parental.shared.core.network.ApiClient
import com.parental.shared.core.network.CategoriesApi
import com.parental.shared.core.ui.components.CategoryInfo
import com.parental.shared.feature.activities.data.remote.ActivitiesApi
import com.parental.shared.feature.activities.domain.model.Actividad
import com.parental.shared.feature.activities.domain.model.ActividadDetalle
import com.parental.shared.feature.activities.domain.model.attachments.ActivityAttachmentDto
import com.parental.shared.feature.activities.domain.model.attachments.ConfirmAttachmentReq
import com.parental.shared.feature.activities.domain.model.attachments.PresignAttachmentReq
import com.parental.shared.feature.activities.domain.model.attachments.PresignAttachmentRes
import com.parental.shared.feature.activities.ui.currentDateIn
import com.parental.shared.feature.bonds.data.remote.BondsApi
import com.parental.shared.feature.thirdparty.data.remote.ThirdPartiesApi
import com.parental.shared.feature.thirdparty.domain.model.ThirdPartyGrant
import com.parental.shared.feature.thirdparty.domain.model.relationshipLabel
import com.parental.shared.platform.Platform
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * La hoja de detalle de una actividad del terciario.
 *
 * Es un `sealed interface` keyed por id, como `DetailUiState` de los progenitores, porque
 * "cargando" y "sin detalle" son estados distintos y colapsarlos en `detalle != null`
 * haría la carga irrepresentable.
 */
sealed interface ThirdPartyDetail {
    data object Closed : ThirdPartyDetail
    data class Loading(val id: String) : ThirdPartyDetail
    data class Content(
        val detalle: ActividadDetalle,
        val attachments: List<ActivityAttachmentDto> = emptyList(),
    ) : ThirdPartyDetail
    data class Failed(val id: String, val message: String) : ThirdPartyDetail
}

/**
 * La tarjeta de permiso, ya resuelta para pintar (nada de ISO-8601 en la UI).
 *
 * `endDate == null` significa "el servidor no nos dio la vigencia": la tarjeta se pinta
 * sin barra y sin "Vence el", jamás con una fecha inventada.
 */
data class ThirdPartyPermitUi(
    val authorizedByName: String,
    val relationship: String?,
    val endDate: LocalDate?,
    val daysLeft: Int?,
    val progress: Float?,
)

data class ThirdPartyUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    /** `null` = no hay datos de permiso: la tarjeta no se pinta. */
    val permit: ThirdPartyPermitUi? = null,
    /** Sólo las actividades asignadas al visitante (S6). Ya filtradas y ordenadas. */
    val activities: List<Actividad> = emptyList(),
    val categories: Map<String, CategoryInfo> = emptyMap(),
    val filter: ThirdPartyFilter = ThirdPartyFilter.EN_PROGRESO,
    val detail: ThirdPartyDetail = ThirdPartyDetail.Closed,
    val isCompleting: Boolean = false,
    val message: String? = null,
    val showPermitDialog: Boolean = false,
    val userName: String = "",
) {
    val counts: Map<ThirdPartyFilter, Int> get() = thirdPartyFilterCounts(activities)
    val homeTasks: List<Actividad> get() = thirdPartyHomeTasks(activities)
    val activeCount: Int get() = thirdPartyActiveCount(activities)
    val filtered: List<Actividad> get() = thirdPartyActivitiesFor(activities, filter)
}

/**
 * El estado de la vista del terciario temporal (artboard `03-permiso-explicito`).
 *
 * **Una sola fuente para las dos pantallas**, porque comparten datos: la misma lista
 * asignada alimenta el Inicio (lo abierto) y Actividades (los cuatro chips), y la hoja de
 * detalle se abre desde las dos.
 *
 * Lo que este ViewModel NO hace, a propósito:
 * - No deriva `canComplete` ni ningún otro flag: los manda el servidor (S13). Si el
 *   servidor no los manda, la fila dice "Esperando" en vez de ofrecer un botón que va a
 *   fallar.
 * - No pide compliance, gastos, hijos ni la lista de miembros: S5. El único dato del
 *   vínculo es el nombre de quien autorizó (S12).
 */
class ThirdPartyViewModel(
    private val thirdPartiesApi: ThirdPartiesApi,
    private val activitiesApi: ActivitiesApi,
    private val categoriesApi: CategoriesApi,
    private val bondsApi: BondsApi,
    private val token: String,
    private val bondId: String,
    private val currentUserId: String,
    private val userName: String,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    /**
     * Persistencia del "ya vio el permiso". Inyectable como en `ThemeController` para que
     * los tests no necesiten `Platform` (que en Android necesita Context).
     */
    private val loadFlag: (String) -> String? = { key -> Platform.loadString(key) },
    private val saveFlag: (String, String) -> Unit = { key, value -> Platform.saveString(key, value) },
    private val today: () -> LocalDate = { currentDateIn(TimeZone.currentSystemDefault()) },
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _state = MutableStateFlow(ThirdPartyUiState(userName = userName))
    val state: StateFlow<ThirdPartyUiState> = _state.asStateFlow()

    /**
     * Carga inicial: permiso, actividades y categorías en paralelo.
     *
     * El permiso y las categorías degradan sin romper: sin permiso no hay tarjeta (y el
     * onboarding no se muestra, porque no hay fecha que anunciar); sin categorías la fila
     * pierde el chip de categoría pero conserva título, hora y estado.
     */
    fun load() {
        scope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)

            val activitiesResult = activitiesApi.listActividades(token, bondId)
            val categoriesResult = categoriesApi.getCategories()
            val grantResult = thirdPartiesApi.getMyGrant(token)

            val categories = when (categoriesResult) {
                is ApiResult.Success -> categoriesResult.data.associateBy { it.id }.mapValues { (_, dto) ->
                    CategoryInfo(
                        name = dto.name,
                        colorHex = dto.color,
                        emoji = dto.icon,
                        criticality = dto.criticality,
                        isActive = dto.isActive,
                    )
                }
                is ApiResult.Error -> emptyMap()
            }

            when (activitiesResult) {
                is ApiResult.Success -> {
                    val mine = thirdPartySorted(
                        thirdPartyVisibleActivities(activitiesResult.data, currentUserId),
                    )
                    val permit = grantResult.toPermit() ?: ownerFallbackPermit()
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = null,
                        activities = mine,
                        categories = categories,
                        permit = permit,
                        showPermitDialog = permit != null && !hasSeenPermit(),
                    )
                }
                is ApiResult.Error -> {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = activitiesResult.message,
                        categories = categories,
                        permit = grantResult.toPermit() ?: ownerFallbackPermit(),
                    )
                }
            }
        }
    }

    fun selectFilter(filter: ThirdPartyFilter) {
        _state.value = _state.value.copy(filter = filter)
    }

    fun openDetail(id: String) {
        if (_state.value.detail is ThirdPartyDetail.Loading) return
        _state.value = _state.value.copy(detail = ThirdPartyDetail.Loading(id))
        scope.launch {
            when (val res = activitiesApi.getActividad(token, id)) {
                is ApiResult.Success -> {
                    val attachments = when (val list = activitiesApi.listAttachments(token, id)) {
                        is ApiResult.Success -> list.data
                        is ApiResult.Error -> emptyList()
                    }
                    _state.value = _state.value.copy(
                        detail = ThirdPartyDetail.Content(res.data, attachments),
                    )
                }
                is ApiResult.Error ->
                    _state.value = _state.value.copy(
                        detail = ThirdPartyDetail.Failed(id, res.message),
                    )
            }
        }
    }

    fun closeDetail() {
        _state.value = _state.value.copy(detail = ThirdPartyDetail.Closed)
    }

    fun dismissMessage() {
        _state.value = _state.value.copy(message = null)
    }

    fun showPermitDialog() {
        if (_state.value.permit == null) return
        _state.value = _state.value.copy(showPermitDialog = true)
    }

    /** "Entendido": el popup de permiso no vuelve a aparecer solo (sí desde el icono). */
    fun acknowledgePermit() {
        saveFlag(permitSeenKey(currentUserId), "true")
        _state.value = _state.value.copy(showPermitDialog = false)
    }

    /** El único cambio de estado que el terciario puede provocar (S8/S11). */
    fun complete(id: String) {
        if (_state.value.isCompleting) return
        _state.value = _state.value.copy(isCompleting = true)
        scope.launch {
            when (val res = activitiesApi.completeActivity(token, id)) {
                is ApiResult.Success -> {
                    _state.value = _state.value.copy(
                        isCompleting = false,
                        detail = ThirdPartyDetail.Closed,
                        message = "Listo. Quedó por verificar.",
                    )
                    refreshActivities()
                }
                is ApiResult.Error -> _state.value = _state.value.copy(
                    isCompleting = false,
                    message = res.message,
                )
            }
        }
    }

    /**
     * Adjuntar un archivo a una actividad propia (S10). Mismo camino que el resto de la
     * app: comprimir → presign → PUT a la URL firmada → confirmar. Sin `rename` ni
     * `delete`: adjuntar es la única operación de archivos del terciario (S9).
     */
    fun addAttachment(activityId: String, fileName: String, mimeType: String, bytes: ByteArray) {
        scope.launch {
            try {
                val compressed = withContext(dispatcher) { ImageCompressor().compress(bytes, mimeType) }
                val presign = when (
                    val res = activitiesApi.presignAttachment(
                        token,
                        activityId,
                        PresignAttachmentReq(fileName, compressed.mimeType, compressed.bytes.size.toLong()),
                    )
                ) {
                    is ApiResult.Success -> res.data
                    is ApiResult.Error -> {
                        _state.value = _state.value.copy(message = res.message)
                        return@launch
                    }
                }
                val upload = uploadBytes(presign, compressed.bytes, compressed.mimeType)
                if (upload == null) {
                    _state.value = _state.value.copy(message = "No se pudo subir el archivo.")
                    return@launch
                }
                when (
                    val confirm = activitiesApi.confirmAttachment(
                        token,
                        activityId,
                        ConfirmAttachmentReq(
                            presign.path,
                            fileName,
                            compressed.mimeType,
                            compressed.bytes.size.toLong(),
                        ),
                    )
                ) {
                    is ApiResult.Success -> {
                        val current = _state.value.detail
                        if (current is ThirdPartyDetail.Content) {
                            _state.value = _state.value.copy(
                                detail = current.copy(attachments = current.attachments + confirm.data),
                                message = "Archivo adjuntado.",
                            )
                        }
                    }
                    is ApiResult.Error -> _state.value = _state.value.copy(message = confirm.message)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(message = "No se pudo adjuntar el archivo.")
            }
        }
    }

    private suspend fun uploadBytes(
        presign: PresignAttachmentRes,
        bytes: ByteArray,
        mimeType: String,
    ): PresignAttachmentRes? {
        val res = ApiClient.httpClient.put(presign.uploadUrl) {
            contentType(ContentType.parse(mimeType))
            setBody(ByteArrayContent(bytes))
        }
        return if (res.status.isSuccess()) presign else null
    }

    private suspend fun refreshActivities() {
        when (val res = activitiesApi.listActividades(token, bondId)) {
            is ApiResult.Success -> _state.value = _state.value.copy(
                activities = thirdPartySorted(
                    thirdPartyVisibleActivities(res.data, currentUserId),
                ),
            )
            is ApiResult.Error -> _state.value = _state.value.copy(message = res.message)
        }
    }

    private fun hasSeenPermit(): Boolean = loadFlag(permitSeenKey(currentUserId)) == "true"

    /**
     * El grant del servidor, o `null` si no vino o no trae fecha.
     *
     * Un grant sin `endDate` no es un permiso temporal (D3): sin vencimiento no hay
     * tarjeta, porque mostrar "acceso temporal" sin fecha sería mentir sobre la vigencia.
     */
    private fun ApiResult<ThirdPartyGrant>.toPermit(): ThirdPartyPermitUi? {
        val grant = (this as? ApiResult.Success)?.data ?: return null
        val end = grant.endDate.toLocalDateOrNull() ?: return null
        val start = grant.startDate.toLocalDateOrNull()
        val now = today()
        val owner = grant.authorizedByName?.trim()?.takeIf { it.isNotEmpty() } ?: "tu progenitor"
        return ThirdPartyPermitUi(
            authorizedByName = owner,
            relationship = relationshipLabel(grant.relationship),
            endDate = end,
            daysLeft = thirdPartyDaysLeft(end, now),
            progress = thirdPartyAccessProgress(start, end, now),
        )
    }

    /**
     * Plan B mientras `GET /third-parties/me` no exista (T2 pendiente): el nombre de quien
     * autorizó se puede derivar de `GET /bonds`, que ya trae los miembros del vínculo.
     *
     * Se toma el progenitor del vínculo SOLO si es uno: con dos progenitores no hay forma
     * de saber cuál otorgó el acceso sin el grant, y decir el nombre equivocado sería peor
     * que decir "tu progenitor". La fecha de vencimiento NO se inventa: sin grant la
     * tarjeta se pinta sin vigencia.
     */
    private suspend fun ownerFallbackPermit(): ThirdPartyPermitUi? {
        return when (val res = bondsApi.getMyBonds(token)) {
            is ApiResult.Success -> {
                val bond = res.data.firstOrNull { it.id == bondId }
                val progenitors = bond?.members
                    ?.filter { it.role == "progenitor" }
                    ?.mapNotNull { member ->
                        listOfNotNull(member.user?.firstName, member.user?.lastName)
                            .joinToString(" ")
                            .takeIf { it.isNotBlank() }
                    }
                    .orEmpty()
                val owner = progenitors.singleOrNull() ?: "tu progenitor"
                ThirdPartyPermitUi(
                    authorizedByName = owner,
                    relationship = null,
                    endDate = null,
                    daysLeft = null,
                    progress = null,
                )
            }
            is ApiResult.Error -> null
        }
    }

    fun destroy() {
        scope.cancel()
    }

    internal companion object {
        /** La marca es POR USUARIO: dos cuentas en el mismo dispositivo no se pisan. */
        internal fun permitSeenKey(userId: String): String = "ui.third_party_permit_seen.$userId"
    }
}

/** ISO-8601 (`2026-12-12` o `2026-12-12T00:00:00.000Z`) → `LocalDate`, tolerante. */
internal fun String?.toLocalDateOrNull(): LocalDate? {
    val raw = this?.trim()?.takeIf { it.length >= 10 } ?: return null
    return runCatching { LocalDate.parse(raw.take(10)) }.getOrNull()
}
