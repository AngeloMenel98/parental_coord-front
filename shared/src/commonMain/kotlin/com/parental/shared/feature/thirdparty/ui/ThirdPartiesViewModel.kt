package com.parental.shared.feature.thirdparty.ui

import com.parental.shared.core.error.ApiResult
import com.parental.shared.feature.activities.ui.ES_MONTHS
import com.parental.shared.feature.activities.ui.currentDateIn
import com.parental.shared.feature.thirdparty.data.remote.ThirdPartiesApi
import com.parental.shared.feature.thirdparty.domain.model.CreateThirdPartyAccessRequest
import com.parental.shared.feature.thirdparty.domain.model.ThirdPartyAccess
import com.parental.shared.feature.thirdparty.domain.model.ThirdPartyRelationshipOption
import com.parental.shared.feature.thirdparty.domain.model.UpdateThirdPartyAccessRequest
import com.parental.shared.feature.thirdparty.domain.model.relationshipLabel
import com.parental.shared.feature.thirdparty.domain.model.thirdPartyStatusLabel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus

/*
 * La consola del progenitor que OTORGA el acceso (T15, artboard `design/terceros-ui/
 * 04-vigencia-a-la-vista.html`).
 *
 * No confundir con `ThirdPartyViewModel`, que es la vista del terciario: ése sólo LEE su
 * grant y finaliza tareas; éste lista, crea, edita y revoca los accesos que autorizó, y
 * nunca pide datos de las actividades. Son dos ViewModels porque son dos permisos.
 */

/** Los dos pasos del asistente: quién, y hasta cuándo. */
enum class ThirdPartyWizardStep { QUIEN, HASTA_CUANDO }

/**
 * Las tres duraciones del artboard. Las dos primeras se calculan con `kotlinx-datetime`
 * sobre la fecha de hoy; la tercera abre el campo de texto.
 *
 * No se usa `DatePicker` de Material3: en Compose Multiplatform 1.7.1 el picker arrastra
 * riesgo de plataforma, y el contrato del back es una fecha ISO de calendario —un campo
 * de texto validado dice exactamente lo mismo sin esa dependencia.
 */
enum class ThirdPartyDuration(val label: String) {
    THIRTY_DAYS("30 días"),
    THREE_MONTHS("3 meses"),
    CUSTOM("Elegir fecha"),
}

/**
 * El estado del asistente de alta.
 *
 * `accessEnd` es el texto que se envía y el que se valida: con un preset ya viene
 * resuelto a ISO; con [ThirdPartyDuration.CUSTOM] es lo que la persona escribe. Una sola
 * fuente para el valor evita el clásico "el preset dice una fecha y el campo otra".
 */
data class ThirdPartyWizardState(
    val step: ThirdPartyWizardStep = ThirdPartyWizardStep.QUIEN,
    val email: String = "",
    /** S1 (thirdparty-identity): nombre y apellido obligatorios; tel y doc opcionales. */
    val firstName: String = "",
    val lastName: String = "",
    val phone: String = "",
    val document: String = "",
    /** `family|caregiver|medical|educational`, o `null` si todavía no eligió. */
    val relationship: String? = null,
    val duration: ThirdPartyDuration = ThirdPartyDuration.THREE_MONTHS,
    val accessEnd: String = "",
    val note: String = "",
    /** Error INLINE del formulario: validación local o mensaje del servidor (422/409). */
    val error: String? = null,
)

/**
 * El estado de la edición en curso (hoja inferior abierta desde una tarjeta).
 *
 * Sólo se abre para un acceso **vigente**: el PATCH del back responde 409 sobre uno
 * vencido o revocado, así que ofrecer "editar" ahí sería un botón que siempre falla.
 */
data class ThirdPartyEditState(
    val id: String,
    val name: String,
    /** S9 (thirdparty-identity): identidad precargada; `null` = el grant no la tiene. */
    val firstName: String? = null,
    val lastName: String? = null,
    val phone: String? = null,
    val document: String? = null,
    val relationship: String? = null,
    /** `YYYY-MM-DD`, inicializado con el vencimiento actual. */
    val accessEnd: String = "",
    val note: String = "",
    val error: String? = null,
)

/**
 * Una tarjeta de la lista, ya resuelta para pintar: nada de ISO-8601 ni de cálculo de
 * días en la UI.
 *
 * `hasAccess` combina el estado del servidor con la fecha: un grant `active` cuya
 * `endDate` ya pasó (el barrido del back todavía no lo marcó) NO se anuncia como vigente,
 * porque la vigencia real es la fecha. Es la única deducción que se permite el cliente, y
 * va en la dirección segura: nunca dice "tiene acceso" cuando no lo tiene.
 */
data class ThirdPartyAccessUi(
    val id: String,
    val userId: String,
    val name: String,
    val initials: String,
    /** S9: identidad declarada, para precargar la edición; `null` en grants previos. */
    val firstName: String? = null,
    val lastName: String? = null,
    val phone: String? = null,
    val document: String? = null,
    /** `family|caregiver|medical|educational`, tal cual lo manda el servidor. */
    val relationship: String?,
    /** La relación en palabras, para la tarjeta ("Familiar"). */
    val relationshipLabel: String?,
    val note: String?,
    /** `active|expired|revoked|suspended`, en minúsculas. */
    val status: String,
    val statusLabel: String,
    val endDate: LocalDate?,
    /** `YYYY-MM-DD`, para precargar el campo de edición. */
    val endDateIso: String,
    /** `"12 dic 2026"`, o `null` si el servidor no dio fecha. */
    val endDateLabel: String?,
    val daysLeft: Int?,
    /** `"quedan 26 días"`, `"queda 1 día"`, `"vence hoy"`; `null` sin vigencia. */
    val remainingLabel: String?,
    /** Fracción del período ya transcurrida, para la barra; `null` sin ambas fechas. */
    val progress: Float?,
    val hasAccess: Boolean,
)

data class ThirdPartiesUiState(
    val isLoading: Boolean = true,
    val loadError: String? = null,
    val items: List<ThirdPartyAccessUi> = emptyList(),
    val wizard: ThirdPartyWizardState? = null,
    val edit: ThirdPartyEditState? = null,
    /** El acceso que espera confirmación para revocarse (diálogo destructivo). */
    val pendingRevoke: ThirdPartyAccessUi? = null,
    /** La matriz de permisos, que vive detrás del icono ⓘ del topbar. */
    val showPolicySheet: Boolean = false,
    val isSaving: Boolean = false,
    /** Confirmación efímera (snackbar), nunca un error de formulario. */
    val message: String? = null,
) {
    /** "Con acceso": los que hoy tienen vigencia real. */
    val active: List<ThirdPartyAccessUi> get() = items.filter { it.hasAccess }

    /** "Sin acceso vigente": vencidos, revocados y suspendidos. */
    val inactive: List<ThirdPartyAccessUi> get() = items.filterNot { it.hasAccess }

    val isEmpty: Boolean get() = !isLoading && loadError == null && items.isEmpty()
}

/*
 * Copia de la UI. Las tres frases son las del artboard 04 y valen para la tarjeta, el
 * paso 2 del asistente y la hoja de información: una sola fuente para el resumen, la
 * matriz y el aviso destructivo.
 */

/** El resumen compacto que va en la tarjeta y en el paso 2. */
internal const val THIRD_PARTY_PERMISSION_SUMMARY =
    "Puede: ver sus actividades asignadas, finalizarlas y adjuntar archivos. " +
        "No puede: crear, reasignar ni cancelar."

/** El aviso que acompaña a la revocación, en la hoja de información y en el diálogo. */
internal const val THIRD_PARTY_REVOKE_WARNING =
    "Si revocás el acceso, deja de ver sus actividades al instante. No se puede deshacer."

/** Una fila de la matriz de capacidades. */
internal data class ThirdPartyCapability(val text: String, val allowed: Boolean)

/**
 * La matriz completa, que NO es un bloque permanente: se consulta desde el icono ⓘ
 * (artboard 04, frame C). El orden es el del artboard: primero lo que sí, después lo que no.
 */
internal val THIRD_PARTY_CAPABILITIES: List<ThirdPartyCapability> = listOf(
    ThirdPartyCapability("Ver las actividades que le asignes", true),
    ThirdPartyCapability("Finalizarlas y adjuntar archivos", true),
    ThirdPartyCapability("Crear, reasignar o cancelar", false),
    ThirdPartyCapability("Ver el cumplimiento o los gastos", false),
    ThirdPartyCapability("Ver a los otros miembros o a los hijos", false),
)

// Validación del formulario. Son constantes para que el test afirme el texto exacto que
// ve la persona y para que el mensaje del servidor no se confunda con uno propio.
internal const val THIRD_PARTY_FIRST_NAME_MISSING = "Escribí el nombre de la persona."
internal const val THIRD_PARTY_LAST_NAME_MISSING = "Escribí el apellido de la persona."
internal const val THIRD_PARTY_EMAIL_MISSING = "Escribí el email de la persona."
internal const val THIRD_PARTY_EMAIL_INVALID = "Ese email no parece válido."
internal const val THIRD_PARTY_RELATIONSHIP_MISSING = "Elegí la relación con la persona."
internal const val THIRD_PARTY_ACCESS_END_MISSING = "Elegí hasta cuándo va a tener acceso."
internal const val THIRD_PARTY_ACCESS_END_INVALID =
    "La fecha tiene que ser un día real, con formato AAAA-MM-DD."
internal const val THIRD_PARTY_ACCESS_END_PAST = "La fecha no puede ser anterior a hoy."

/**
 * La consola del progenitor dueño del acceso.
 *
 * Una sola fuente para la lista, el asistente, la edición y la revocación: las cuatro
 * acciones escriben sobre el mismo grant, y separarlas en dos ViewModels dejaría la lista
 * desactualizada respecto de lo que el asistente acaba de crear.
 *
 * Lo que este ViewModel NO hace, a propósito:
 * - No inventa estados: `status` y la vigencia salen del servidor; lo único que deduce es
 *   `hasAccess` (estado `active` **y** fecha no vencida), y siempre en la dirección segura.
 * - No decide la política: `Editar`/`Revocar` se ofrecen sólo sobre un acceso vigente
 *   porque el back devuelve 409 en el resto, no porque la UI esconda un botón.
 * - No toca las actividades del tercero: esta pantalla administra el permiso, no el trabajo.
 */
class ThirdPartiesViewModel(
    private val api: ThirdPartiesApi,
    private val token: String,
    private val bondId: String,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val today: () -> LocalDate = { currentDateIn(TimeZone.currentSystemDefault()) },
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _state = MutableStateFlow(ThirdPartiesUiState())
    val state: StateFlow<ThirdPartiesUiState> = _state.asStateFlow()

    /** Carga inicial y reintento: una sola lista, la del dueño (el back filtra por S7). */
    fun load() {
        scope.launch {
            _state.value = _state.value.copy(isLoading = true, loadError = null)
            when (val res = api.listForBond(token, bondId)) {
                is ApiResult.Success -> _state.value = _state.value.copy(
                    isLoading = false,
                    loadError = null,
                    items = res.data.map { it.toUi() }.sortedForOwner(),
                )
                is ApiResult.Error -> _state.value = _state.value.copy(
                    isLoading = false,
                    loadError = res.message,
                )
            }
        }
    }

    // ── Asistente de alta ───────────────────────────────────────────────

    /** Abre el paso 1 con la duración por defecto ya resuelta (3 meses). */
    fun openCreate() {
        _state.value = _state.value.copy(
            wizard = ThirdPartyWizardState(accessEnd = resolveDuration(ThirdPartyDuration.THREE_MONTHS)),
            edit = null,
            pendingRevoke = null,
        )
    }

    fun setEmail(value: String) {
        val wizard = _state.value.wizard ?: return
        _state.value = _state.value.copy(wizard = wizard.copy(email = value, error = null))
    }

    fun setRelationship(value: String) {
        val wizard = _state.value.wizard ?: return
        _state.value = _state.value.copy(wizard = wizard.copy(relationship = value, error = null))
    }

    fun setFirstName(value: String) {
        val wizard = _state.value.wizard ?: return
        _state.value = _state.value.copy(wizard = wizard.copy(firstName = value, error = null))
    }

    fun setLastName(value: String) {
        val wizard = _state.value.wizard ?: return
        _state.value = _state.value.copy(wizard = wizard.copy(lastName = value, error = null))
    }

    fun setPhone(value: String) {
        val wizard = _state.value.wizard ?: return
        _state.value = _state.value.copy(wizard = wizard.copy(phone = value, error = null))
    }

    fun setDocument(value: String) {
        val wizard = _state.value.wizard ?: return
        _state.value = _state.value.copy(wizard = wizard.copy(document = value, error = null))
    }

    /** Un preset recalcula la fecha; "elegir fecha" deja el campo vacío para escribir. */
    fun setDuration(duration: ThirdPartyDuration) {
        val wizard = _state.value.wizard ?: return
        val accessEnd = when (duration) {
            ThirdPartyDuration.THIRTY_DAYS,
            ThirdPartyDuration.THREE_MONTHS,
            -> resolveDuration(duration)
            ThirdPartyDuration.CUSTOM -> ""
        }
        _state.value = _state.value.copy(
            wizard = wizard.copy(duration = duration, accessEnd = accessEnd, error = null),
        )
    }

    /** El texto de "elegir fecha"; se valida recién al enviar, no en cada tecla. */
    fun setAccessEnd(value: String) {
        val wizard = _state.value.wizard ?: return
        _state.value = _state.value.copy(
            wizard = wizard.copy(
                duration = ThirdPartyDuration.CUSTOM,
                accessEnd = value,
                error = null,
            ),
        )
    }

    fun setNote(value: String) {
        val wizard = _state.value.wizard ?: return
        _state.value = _state.value.copy(wizard = wizard.copy(note = value, error = null))
    }

    /** Paso 1 → 2. El email y la relación se validan acá, antes de hablar del alcance. */
    fun next() {
        val wizard = _state.value.wizard ?: return
        val email = wizard.email.trim()
        val firstName = wizard.firstName.trim()
        val lastName = wizard.lastName.trim()
        val error = when {
            firstName.length < 2 -> THIRD_PARTY_FIRST_NAME_MISSING
            lastName.length < 2 -> THIRD_PARTY_LAST_NAME_MISSING
            email.isEmpty() -> THIRD_PARTY_EMAIL_MISSING
            !looksLikeEmail(email) -> THIRD_PARTY_EMAIL_INVALID
            wizard.relationship == null -> THIRD_PARTY_RELATIONSHIP_MISSING
            else -> null
        }
        _state.value = _state.value.copy(
            wizard = wizard.copy(
                email = email,
                firstName = firstName,
                lastName = lastName,
                step = if (error == null) ThirdPartyWizardStep.HASTA_CUANDO else wizard.step,
                error = error,
            ),
        )
    }

    fun back() {
        val wizard = _state.value.wizard ?: return
        _state.value = _state.value.copy(
            wizard = wizard.copy(step = ThirdPartyWizardStep.QUIEN, error = null),
        )
    }

    fun closeWizard() {
        _state.value = _state.value.copy(wizard = null)
    }

    /**
     * Otorgar el acceso.
     *
     * Valida la fecha ANTES de llamar: `accessEnd` es obligatorio (D3) y el back responde
     * 400 tanto por una fecha imposible (`2026-02-30`) como por una pasada, así que el
     * formulario no gasta una request para decirlo. Un error del servidor —el 409 del
     * acceso duplicado, el 422 de un email que no se puede dar de alta— se muestra
     * **inline** y el formulario queda intacto: perder lo que la persona escribió la
     * castigaría por un dato que el cliente no podía conocer.
     *
     * 🔴 El email SIN cuenta ya no es un error (D2): el back crea la cuenta con una
     * contraseña generada y le manda las credenciales por mail. El 422 que quede es para
     * lo que de verdad no se puede completar, y su mensaje es el que se muestra.
     */
    fun submit() {
        val wizard = _state.value.wizard ?: return
        if (_state.value.isSaving || wizard.step != ThirdPartyWizardStep.HASTA_CUANDO) return

        val relationship = wizard.relationship
        val error = validateAccessEnd(wizard.accessEnd)
            ?: if (relationship == null) THIRD_PARTY_RELATIONSHIP_MISSING else null
        if (error != null) {
            _state.value = _state.value.copy(wizard = wizard.copy(error = error))
            return
        }

        val request = CreateThirdPartyAccessRequest(
            email = wizard.email.trim(),
            firstName = wizard.firstName.trim(),
            lastName = wizard.lastName.trim(),
            phone = wizard.phone.trim().takeIf { it.isNotEmpty() },
            document = wizard.document.trim().takeIf { it.isNotEmpty() },
            relationship = relationship.orEmpty(),
            accessEnd = wizard.accessEnd.trim(),
            note = wizard.note.trim().takeIf { it.isNotEmpty() },
        )
        _state.value = _state.value.copy(wizard = wizard.copy(error = null), isSaving = true)

        scope.launch {
            when (val res = api.create(token, bondId, request)) {
                is ApiResult.Success -> {
                    val grant = res.data
                    _state.value = _state.value
                        .withGrant(grant)
                        .copy(
                            isSaving = false,
                            wizard = null,
                            message = "Acceso otorgado. ${grant.toUi().name} ya puede entrar.",
                        )
                    refresh()
                }
                is ApiResult.Error -> {
                    val current = _state.value.wizard ?: wizard
                    _state.value = _state.value.copy(
                        isSaving = false,
                        wizard = current.copy(error = res.message),
                    )
                }
            }
        }
    }

    // ── Edición ─────────────────────────────────────────────────────────

    /** Sólo un acceso vigente: el PATCH del back responde 409 sobre el resto. */
    fun openEdit(item: ThirdPartyAccessUi) {
        if (!item.hasAccess) return
        _state.value = _state.value.copy(
            edit = ThirdPartyEditState(
                id = item.id,
                name = item.name,
                firstName = item.firstName,
                lastName = item.lastName,
                phone = item.phone,
                document = item.document,
                relationship = item.relationship,
                accessEnd = item.endDateIso,
                note = item.note.orEmpty(),
            ),
            pendingRevoke = null,
        )
    }

    fun setEditRelationship(value: String) {
        val edit = _state.value.edit ?: return
        _state.value = _state.value.copy(edit = edit.copy(relationship = value, error = null))
    }

    fun setEditAccessEnd(value: String) {
        val edit = _state.value.edit ?: return
        _state.value = _state.value.copy(edit = edit.copy(accessEnd = value, error = null))
    }

    fun setEditNote(value: String) {
        val edit = _state.value.edit ?: return
        _state.value = _state.value.copy(edit = edit.copy(note = value, error = null))
    }

    /** Edita la identidad declarada; escribir habilita mandar el campo (S9). */
    fun setEditFirstName(value: String) {
        val edit = _state.value.edit ?: return
        _state.value = _state.value.copy(edit = edit.copy(firstName = value, error = null))
    }

    fun setEditLastName(value: String) {
        val edit = _state.value.edit ?: return
        _state.value = _state.value.copy(edit = edit.copy(lastName = value, error = null))
    }

    fun setEditPhone(value: String) {
        val edit = _state.value.edit ?: return
        _state.value = _state.value.copy(edit = edit.copy(phone = value, error = null))
    }

    fun setEditDocument(value: String) {
        val edit = _state.value.edit ?: return
        _state.value = _state.value.copy(edit = edit.copy(document = value, error = null))
    }

    fun closeEdit() {
        _state.value = _state.value.copy(edit = null)
    }

    /**
     * Guardar la edición.
     *
     * `accessEnd` viaja siempre: el campo ya viene precargado, y no mandarlo dejaría la
     * fecha vieja si la persona vino a extenderla —el único motivo por el que se edita—.
     * La nota viaja tal cual, incluso vacía: `""` es la forma que tiene el back de
     * borrarla.
     */
    fun saveEdit() {
        val edit = _state.value.edit ?: return
        if (_state.value.isSaving) return

        val error = validateAccessEnd(edit.accessEnd)
            ?: if (edit.relationship == null) THIRD_PARTY_RELATIONSHIP_MISSING else null
        if (error != null) {
            _state.value = _state.value.copy(edit = edit.copy(error = error))
            return
        }

        val request = UpdateThirdPartyAccessRequest(
            relationship = edit.relationship,
            accessEnd = edit.accessEnd.trim(),
            note = edit.note.trim(),
            // S9: el nombre exige 2..80 en el back, así que un campo en blanco no viaja
            // (no hay forma de "borrar" el nombre); tel/doc viajan siempre — "" limpia.
            firstName = edit.firstName?.trim()?.takeIf { it.length >= 2 },
            lastName = edit.lastName?.trim()?.takeIf { it.length >= 2 },
            phone = edit.phone?.trim().orEmpty(),
            document = edit.document?.trim().orEmpty(),
        )
        _state.value = _state.value.copy(edit = edit.copy(error = null), isSaving = true)

        scope.launch {
            when (val res = api.update(token, edit.id, request)) {
                is ApiResult.Success -> {
                    _state.value = _state.value
                        .withGrant(res.data)
                        .copy(isSaving = false, edit = null, message = "Acceso actualizado.")
                    refresh()
                }
                is ApiResult.Error -> {
                    val current = _state.value.edit ?: edit
                    _state.value = _state.value.copy(
                        isSaving = false,
                        edit = current.copy(error = res.message),
                    )
                }
            }
        }
    }

    // ── Revocación ──────────────────────────────────────────────────────

    /** Abre el diálogo destructivo. Pedir NO revoca: confirmar sí. */
    fun askRevoke(item: ThirdPartyAccessUi) {
        if (!item.hasAccess) return
        _state.value = _state.value.copy(pendingRevoke = item, edit = null)
    }

    fun dismissRevoke() {
        _state.value = _state.value.copy(pendingRevoke = null)
    }

    /** Confirmado: el back revoca en el acto (y cierra la membresía del vínculo). */
    fun revoke() {
        val target = _state.value.pendingRevoke ?: return
        if (_state.value.isSaving) return
        _state.value = _state.value.copy(isSaving = true)

        scope.launch {
            when (val res = api.revoke(token, target.id)) {
                is ApiResult.Success -> {
                    _state.value = _state.value
                        .withGrant(res.data)
                        .copy(
                            isSaving = false,
                            pendingRevoke = null,
                            message = "Acceso revocado. ${target.name} ya no puede entrar.",
                        )
                    refresh()
                }
                is ApiResult.Error -> {
                    _state.value = _state.value.copy(
                        isSaving = false,
                        pendingRevoke = null,
                        message = res.message,
                    )
                }
            }
        }
    }

    // ── Hoja de información y mensajes ──────────────────────────────────

    fun openPolicySheet() {
        _state.value = _state.value.copy(showPolicySheet = true)
    }

    fun dismissPolicySheet() {
        _state.value = _state.value.copy(showPolicySheet = false)
    }

    fun dismissMessage() {
        _state.value = _state.value.copy(message = null)
    }

    fun destroy() {
        scope.cancel()
    }

    // ── Interno ─────────────────────────────────────────────────────────

    /**
     * Refresca la lista después de una escritura. Un fallo acá NO pisa lo que la persona
     * acaba de hacer: la tarjeta ya quedó actualizada con la respuesta del servidor, así
     * que el error se informa como mensaje efímero en vez de reemplazar la lista.
     */
    private suspend fun refresh() {
        when (val res = api.listForBond(token, bondId)) {
            is ApiResult.Success -> _state.value = _state.value.copy(
                items = res.data.map { it.toUi() }.sortedForOwner(),
                loadError = null,
            )
            is ApiResult.Error -> _state.value = _state.value.copy(message = res.message)
        }
    }

    private fun ThirdPartiesUiState.withGrant(grant: ThirdPartyAccess): ThirdPartiesUiState {
        val ui = grant.toUi()
        return copy(items = (items.filterNot { it.id == ui.id } + ui).sortedForOwner())
    }

    /**
     * La fecha que corresponde a un preset, calculada sobre HOY.
     *
     * `today()` se inyecta (igual que en `ThirdPartyViewModel`) para que un test pueda
     * fijar el día: "3 meses" desde una fecha fija es una afirmación verificable, y desde
     * `LocalDate.now()` no lo es. `kotlinx-datetime` recorta el fin de mes cuando el día
     * no existe en el mes destino, que es el comportamiento correcto para un vencimiento.
     */
    private fun resolveDuration(duration: ThirdPartyDuration): String = when (duration) {
        ThirdPartyDuration.THIRTY_DAYS -> today().plus(30, DateTimeUnit.DAY).toString()
        ThirdPartyDuration.THREE_MONTHS -> today().plus(3, DateTimeUnit.MONTH).toString()
        ThirdPartyDuration.CUSTOM -> ""
    }

    /** `AAAA-MM-DD` de un día que existe y que no está en el pasado, o el motivo del rechazo. */
    private fun validateAccessEnd(raw: String): String? {
        val value = raw.trim()
        if (value.isEmpty()) return THIRD_PARTY_ACCESS_END_MISSING
        val date = parseCalendarDate(value) ?: return THIRD_PARTY_ACCESS_END_INVALID
        if (date < today()) return THIRD_PARTY_ACCESS_END_PAST
        return null
    }

    private fun ThirdPartyAccess.toUi(): ThirdPartyAccessUi = toAccessUi(today())
}

/**
 * `AAAA-MM-DD` → fecha, exigiendo el formato con guiones.
 *
 * `LocalDate.parse` acepta formas ISO que el back NO acepta (el DTO valida con
 * `/^\d{4}-\d{2}-\d{2}$/`), y `2026-02-30` es un día que no existe: se rechaza acá en vez
 * de mandarlo y esperar un 400. Es la misma regla que el back aplica con `isCalendarDate`.
 */
internal fun parseCalendarDate(raw: String): LocalDate? {
    val value = raw.trim()
    if (value.length != 10) return null
    if (!CALENDAR_DATE_SHAPE.matches(value)) return null
    return runCatching { LocalDate.parse(value) }.getOrNull()
}

private val CALENDAR_DATE_SHAPE = Regex("^\\d{4}-\\d{2}-\\d{2}$")

private fun looksLikeEmail(value: String): Boolean =
    EMAIL_SHAPE.matches(value)

private val EMAIL_SHAPE = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

/**
 * El grant del servidor → la tarjeta.
 *
 * `hasAccess` es la única deducción del cliente y es conservadora: exige `status` `active`
 * **y** que la fecha no haya pasado, porque un grant vencido que el barrido del back
 * todavía no marcó no puede anunciarse como vigente. `progress` y `daysLeft` reutilizan
 * los helpers de la vista del terciario (`thirdPartyAccessProgress`, `thirdPartyDaysLeft`):
 * "cuánto queda" no puede calcularse distinto según quién mire.
 */
internal fun ThirdPartyAccess.toAccessUi(today: LocalDate): ThirdPartyAccessUi {
    // S9: la identidad DECLARADA en el alta es el nombre de la persona; displayName del
    // perfil queda como fallback (grants previos sin identidad, S6).
    val declaredName = firstName?.trim()?.takeIf { it.isNotEmpty() }?.let { first ->
        listOfNotNull(first, lastName?.trim()?.takeIf { it.isNotEmpty() }).joinToString(" ")
    }
    val name = declaredName
        ?: displayName?.trim()?.takeIf { it.isNotEmpty() }
        ?: "Sin nombre"
    val end = endDate?.trim()?.takeIf { it.isNotEmpty() }?.let(::parseCalendarDate)
    val start = startDate?.trim()?.takeIf { it.isNotEmpty() }?.let(::parseCalendarDate)
    val status = status?.trim()?.lowercase().orEmpty()
    val hasAccess = status == "active" && (end == null || end >= today)
    val daysLeft = if (hasAccess) thirdPartyDaysLeft(end, today) else null
    return ThirdPartyAccessUi(
        id = id,
        userId = userId,
        name = name,
        initials = initialsOf(name),
        firstName = firstName,
        lastName = lastName,
        phone = phone,
        document = document,
        relationship = relationship,
        relationshipLabel = relationshipLabel(relationship),
        note = note?.trim()?.takeIf { it.isNotEmpty() },
        status = status,
        statusLabel = thirdPartyStatusLabel(status) ?: "Sin estado",
        endDate = end,
        endDateIso = endDate?.trim().orEmpty(),
        endDateLabel = end?.let(::fullDateLabel),
        daysLeft = daysLeft,
        remainingLabel = if (hasAccess) remainingLabel(daysLeft) else null,
        progress = if (hasAccess) thirdPartyAccessProgress(start, end, today) else null,
        hasAccess = hasAccess,
    )
}

/**
 * "12 dic 2026" — la fecha de vencimiento SIEMPRE con año.
 *
 * No se usa `formatAgendaDate`: esa regla omite el año cuando es el año en curso (es la
 * regla de la agenda), y un vencimiento no es una agenda — "hasta el 12 dic" sin año deja
 * la duda de si es este diciembre o el que viene, justo en el dato que decide si el acceso
 * sigue vivo. El artboard 04 muestra el año en las tres tarjetas.
 */
internal fun fullDateLabel(date: LocalDate): String =
    "${date.dayOfMonth} ${ES_MONTHS[date.monthNumber - 1]} ${date.year}"

/**
 * "quedan 26 días" / "queda 1 día" / "vence hoy".
 *
 * Se dice en palabras además de en la barra porque el color no puede ser la única señal
 * (contrato de accesibilidad del design system).
 */
internal fun remainingLabel(days: Int?): String? = when {
    days == null -> null
    days < 0 -> "vencido"
    days == 0 -> "vence hoy"
    days == 1 -> "queda 1 día"
    else -> "quedan $days días"
}

/** "MG" para "Marta Gómez". Dos iniciales como máximo; sin nombre, un marcador honesto. */
internal fun initialsOf(name: String): String {
    val parts = name.trim().split(" ").filter { it.isNotEmpty() }
    val letters = parts.take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }
    return if (letters.isEmpty()) "?" else letters.joinToString("")
}

/**
 * Orden de la lista: primero los que tienen acceso (el que vence antes va arriba, porque
 * es el que hay que mirar), y después los cerrados, del más reciente al más viejo.
 * Dentro de cada grupo, sin fecha al final: un vencimiento desconocido no puede encabezar.
 */
internal fun List<ThirdPartyAccessUi>.sortedForOwner(): List<ThirdPartyAccessUi> {
    val active = filter { it.hasAccess }
        .sortedWith(compareBy({ it.endDate == null }, { it.endDate ?: LocalDate(9999, 12, 31) }))
    val inactive = filterNot { it.hasAccess }
        .sortedWith(compareByDescending<ThirdPartyAccessUi> { it.endDate ?: LocalDate(1, 1, 1) })
    return active + inactive
}

/** El valor etiquetado de una relación para el asistente, o el crudo si no está en el enum. */
internal fun relationshipOptionOf(raw: String?): ThirdPartyRelationshipOption? =
    ThirdPartyRelationshipOption.fromValue(raw)
