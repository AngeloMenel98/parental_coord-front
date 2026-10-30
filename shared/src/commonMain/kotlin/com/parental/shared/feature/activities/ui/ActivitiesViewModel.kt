package com.parental.shared.feature.activities.ui

import com.parental.shared.core.error.ApiResult
import com.parental.shared.core.files.CompressedImage
import com.parental.shared.core.files.ImageCompressor
import com.parental.shared.core.network.CategoriesApi
import com.parental.shared.core.ui.components.CategoryInfo
import com.parental.shared.core.ui.components.CategoryResolver
import com.parental.shared.feature.activities.data.remote.ActivitiesApi
import com.parental.shared.feature.activities.domain.model.Actividad
import com.parental.shared.feature.activities.domain.model.ActividadDetalle
import com.parental.shared.feature.activities.domain.model.ActivityReasonRequest
import com.parental.shared.feature.activities.ui.create.CreateForm
import com.parental.shared.feature.activities.ui.create.TimeOfDay
import com.parental.shared.feature.activities.ui.create.toRequest
import com.parental.shared.feature.activities.ui.create.validateCreateForm
import com.parental.shared.feature.activities.ui.swipe.REASON_MAX_LENGTH
import com.parental.shared.feature.activities.ui.swipe.ReasonValidation
import com.parental.shared.feature.activities.ui.swipe.SWIPE_UNDO_WINDOW_MS
import com.parental.shared.feature.activities.ui.swipe.SwipeAction
import com.parental.shared.feature.activities.ui.swipe.UndoOffer
import com.parental.shared.feature.activities.ui.swipe.successMessage
import com.parental.shared.feature.activities.ui.swipe.validateSwipeReason
import com.parental.shared.feature.bonds.data.remote.BondsApi
import com.parental.shared.feature.bonds.domain.model.VinculoChild
import com.parental.shared.core.network.realtime.RealtimeConnectionManager
import com.parental.shared.feature.activities.domain.model.attachments.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.ByteArrayContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate

enum class ActivityTab(val label: String) {
    TODOS("Todos"),
    EVENTOS("Eventos"),
    OBLIGACIONES("Obligaciones"),
}

/** Estado del detalle de UNA actividad — R1. El **id es la única clave**: sustituye a
 *  `selectedActividad`/`isDetailLoading`/`detailError`/`lastSelectedId`, y clavear por
 *  *payload* haría el estado de carga irrepresentable (mientras `Loading` no hay payload
 *  que no sea `null`). Por eso la hoja se compone sobre `!= Closed`, no sobre "hay algo
 *  seleccionado". [Content.id] es siempre igual a `actividad.id`. */
sealed interface DetailUiState {
    /** Ninguna hoja abierta. Es el estado inicial y el único tras un descarte. */
    data object Closed : DetailUiState

    /** Petición en vuelo para [id] — la hoja pinta su esqueleto (R5). */
    data class Loading(val id: String) : DetailUiState

    /** Falló `GET /activities/:id` para [id] — la hoja pinta el error inline (R6). */
    data class Error(val id: String, val message: String) : DetailUiState

    /** Detalle cargado de [id]. */
    data class Content(val id: String, val actividad: ActividadDetalle) : DetailUiState
}

/** Id de la actividad abierta, o `null` si no hay ninguna — R1/R2. Existe porque
 *  [DetailUiState.Closed] no lleva `id` y una interfaz sellada no admite un smart cast
 *  "todo menos `Closed`": tras `if (current is Closed) return` el tipo sigue siendo
 *  `DetailUiState` y `current.id` no resuelve. ⚠ La misma regla aplica en la UI: `uiState`
 *  es una `val` delegada (`by collectAsState()`) y no admite smart cast, de modo que el `as`
 *  explícito de la rama `Content` en `ActivitiesScreen` es necesario, no redundante. */
internal val DetailUiState.openedId: String?
    get() = when (this) {
        DetailUiState.Closed -> null
        is DetailUiState.Loading -> id
        is DetailUiState.Error -> id
        is DetailUiState.Content -> id
    }

data class ActivitiesUiState(
    val isLoading: Boolean = true,
    val listError: String? = null,
    val actividades: List<Actividad> = emptyList(),
    val selectedTab: ActivityTab = ActivityTab.TODOS,
    /** Detalle por id — R1. `Closed` = no hay hoja; el resto lleva el id de la actividad. */
    val detail: DetailUiState = DetailUiState.Closed,
    /** uuid → info resuelta de GET /categories + preferencias por nombre. */
    val categories: Map<String, CategoryInfo> = emptyMap(),
    /** userId → "Nombre Apellido" (del vínculo activo). */
    val memberNames: Map<String, String> = emptyMap(),
    val snackbarMessage: String? = null,
    /** True SOLO mientras la confirmación está en vuelo (single-flight global). */
    val isConfirming: Boolean = false,
    // ── create-activity: alta (T-FE-14) ────────────────────────────────
    /** Estado del modal de alta. `Closed` = no hay modal abierto. */
    val create: CreateUiState = CreateUiState.Closed,
    /** ⚠ D19: el formulario vive FUERA de [create] para que un error de red no lo borre. */
    val createForm: CreateForm = CreateForm(),
    /** Derivado, nunca un flag a mano: ¿el formulario difiere del que se abrió? */
    val createDirty: Boolean = false,
    /** Hijos del vínculo activo — derivados en el MISMO bloque de `loadAll()` que memberNames. */
    val children: List<VinculoChild> = emptyList(),
    /** Candidatos a "Asignar a" — degradación declarada de AS-9 (sin estado `pending`). */
    val memberCandidates: List<MemberCandidate> = emptyList(),
    /** True SOLO mientras `POST /:id/complete` está en vuelo (single-flight global). */
    val isCompleting: Boolean = false,
    // ── activity-swipe-actions: mutaciones de swipe ────────────────────────
    /** Diálogo de motivo de Cancelar / No asistir — A1. `Closed` = no hay diálogo. */
    val swipeReason: SwipeReasonUiState = SwipeReasonUiState.Closed,
    /**
     * R2 · Oferta de "Deshacer" viva, o `null`. ⚠ El campo NO guarda "hay snackbar":
     * guarda una ventana con plazo. `null` significa que no hay nada que deshacer —
     * tanto porque no se hizo una acción reversible como porque la ventana se venció.
     */
    val undoOffer: UndoOffer? = null,
)

/** Estado del MODAL DE ALTA — D19. ⚠ El estado en vuelo se llama `Loading`, no `Saving`, a
 *  propósito: es el mismo nombre que ya usa [DetailUiState.Loading], así ambos se leen igual. */
sealed interface CreateUiState {
    /** Ningún modal abierto. Estado inicial y el único tras un descarte. */
    data object Closed : CreateUiState

    /** `POST /create` en vuelo — el pie muestra spinner y el doble envío es imposible. */
    data object Loading : CreateUiState

    /** Formulario listo para rellenarse. */
    data object Content : CreateUiState

    /** Falló el alta. ⇦ el formulario sigue íntegro en [ActivitiesUiState.createForm] (spec). */
    data class Error(val message: String) : CreateUiState
}

/** Un miembro del vínculo que puede recibir la actividad — el desplegable "Asignar a". */
data class MemberCandidate(val id: String, val name: String)

// ═══════════════════════════════════════════════════════════════════════════
// Acciones de swipe (R1–R8)
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Diálogo de motivo — A1 · obligatorio en Cancelar y No asistir, inexistente en Eliminar.
 *
 * Como en [DetailUiState], la clave es el **id** de la actividad y no el payload: el
 * diálogo se compone sobre `!= Closed`, así que `Loading` no necesita llevar una
 * actividad que aún no existe. `Content` sí lleva el título, que es lo que la pantalla
 * pinta en el subtítulo y que evita tener que buscar la fila en la lista —que ya no
 * está, porque se quitó de forma optimista.
 */
sealed interface SwipeReasonUiState {
    data object Closed : SwipeReasonUiState

    /** Diálogo abierto, esperando texto. */
    data class Content(
        val id: String,
        val action: SwipeAction,
        val title: String,
        val reason: String = "",
        /** @param validation error de cliente (`TooShort`, `TooLong`…); `null` = aún no se ha enviado. */
        val validation: ReasonValidation? = null,
        val isSubmitting: Boolean = false,
        /** @param serverError 422 `VALIDATION_FAILED` del backend: manda sobre [validation]. */
        val serverError: String? = null,
    ) : SwipeReasonUiState
}

class ActividadesViewModel(
    private val activitiesApi: ActivitiesApi,
    private val categoriesApi: CategoriesApi,
    private val bondsApi: BondsApi,
    private val token: String,
    private val bondId: String,
    /** userId del usuario autenticado (non-null: la pantalla está gated por login). */
    val currentUserId: String,
    /**
     * D17 · El hilo en el que se ESCRIBE el estado. Antes vivían dos referencias distintas —
     * el `scope` corría en [Dispatchers.Default] y la escritura del detalle en [Dispatchers.Main]—
     * y los tests no podían redirigir ninguna de las dos. Inyectarlo deja el comportamiento de
     * producción idéntico (el default sigue siendo `Main`) y permite pasar un
     * `UnconfinedTestDispatcher` SIN `Dispatchers.setMain`.
     */
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main,
    /**
     * Reloj de pared inyectado. Lo que R2 miden son 5000 ms REALES contra el reloj del
     * SERVIDOR, así que este reloj no decide nada: sólo hace que el texto del contador y
     * el cierre automático de la oferta sean comprobables.
     *
     * ⇦ Inyectarlo evita el `Thread.sleep` en los tests, y además hace algo más
     * importante: con [scope] en un dispatcher virtual, `delay()` avanza el reloj virtual
     * pero NO el de `System.currentTimeMillis()`. Un test que esperara cinco segundos por
     * `delay()` vería expirar la oferta sin que el tiempo real hubiera pasado, y probaría
     * un reloj que no existe en producción.
     */
    private val clock: () -> Long = { kotlinx.datetime.Clock.System.now().toEpochMilliseconds() },
    private val realtime: RealtimeConnectionManager? = null,
) {
    private val _attachments = MutableStateFlow<List<ActivityAttachmentDto>>(emptyList())
    val attachments: StateFlow<List<ActivityAttachmentDto>> = _attachments.asStateFlow()
    private val _attachmentsLoading = MutableStateFlow(false)
    val attachmentsLoading: StateFlow<Boolean> = _attachmentsLoading.asStateFlow()
    private val httpClient = com.parental.shared.core.network.ApiClient.httpClient
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _uiState = MutableStateFlow(ActivitiesUiState())
    val uiState: StateFlow<ActivitiesUiState> = _uiState.asStateFlow()

    /** Contador de generación del detalle — R1, y la GARANTÍA de que una respuesta obsoleta
     *  no escriba. ⚠ CONFINADO al hilo principal: los `++` salen del toque y del descarte, y
     *  la comparación de la respuesta va también en `Dispatchers.Main` ([selectActividad]);
     *  así la lectura entre hilos desaparece por construcción y no hace falta `@Volatile`.
     *  ⚠ `detailJob?.cancel()` NO basta: `getActividad` envuelve su `client.get` en
     *  `catch (e: Exception)` (`ActivitiesApi.kt:68`) y `CancellationException` ES una
     *  `Exception` ⇒ un job cancelado igual escribe. `cancel()` sólo libera el socket. */
    private var detailGeneration = 0

    /** Petición de detalle en vuelo. Sólo se cancela para liberar el socket. */
    private var detailJob: Job? = null

    /**
     * Contador de generación del ALTA — R9. ⚠ `catch (e: Exception)` de [ActivitiesApi] también
     * se traga `CancellationException` (que ES una `Exception`), así que `cancel()` NO basta: un
     * job cancelado devuelve `Error` y escribe igual. La REGLA es que **todo camino terminal
     * incrementa el contador, incluido el descarte**: si el modal se cierra con el POST en vuelo,
     * la respuesta llega tarde y no debe reabrirlo ni pintar un error en la lista.
     */
    private var createGeneration = 0

    /** `POST /create` en vuelo. Sólo se cancela para liberar el socket (ver [createGeneration]). */
    private var createJob: Job? = null

    /**
     * Formulario con el que se ABRIÓ el modal. `createDirty` se DERIVA comparando contra esto, y
     * no contra `CreateForm()`: al abrir con un solo hijo ese hijo queda preseleccionado, y
     * un `dirty` inicial saltaría antes de que el usuario tocara nada.
     */
    private var createBaseline: CreateForm = CreateForm()

    /** Contador de generación de "Finalizar" — misma garantía que [createGeneration]. */
    private var completeGeneration = 0

    /** `POST /:id/complete` en vuelo. */
    private var completeJob: Job? = null

    private var loadJob: Job? = null

    init {
        loadAll()
        // REQ-5/REQ-6: UNA única ruta de refetch. El RealtimeConnectionManager ya
        // alimenta su coalescer TANTO con cada frame recibido (REQ-2) como con cada
        // reconexión exitosa (REQ-5); aquí sólo se consume la señal coalescida →
        // loadAll() single-flight. (El collector de `changes` que había aquí era un
        // no-op: los frames no llegaban nunca al coalescer — C1 del verify.)
        scope.launch {
            realtime?.resyncSignals?.collect {
                loadAll()
            }
        }
    }

    /**
     * Señal de recarga recibida mientras otra carga está en vuelo (W3 — trailing-edge).
     * `loadAll()` en lugar de descartarla, la REARMA: al terminar la carga en curso se
     * ejecuta UNA (y sólo una, el flag coalescea varias señales) recarga pendiente.
     * ⚠ Escrito y leído en el hilo de [dispatcher] (Main / test), como el resto de
     * estado de esta clase — no hay carreras intra-hilo entre la condición y el bucle.
     */
    private var pendingReload = false

    /** Carga lista + categorías + nombres de miembros en paralelo.
     *  Nunca dos recargas concurrentes (REQ-6); una señal durante la carga en vuelo
     *  deja ANOTADA exactamente una recarga pendiente que se ejecuta al terminar. */
    fun loadAll() {
        if (loadJob?.isActive == true) {
            pendingReload = true
            return
        }
        loadJob = scope.launch {
            do {
                pendingReload = false
                performLoad()
            } while (pendingReload)
        }
    }

    private suspend fun performLoad() {
        _uiState.value = _uiState.value.copy(isLoading = true, listError = null)

        coroutineScope {
            val listDeferred = async { activitiesApi.listActividades(token, bondId) }
            val categoriesDeferred = async { categoriesApi.getCategories() }
            val bondsDeferred = async { bondsApi.getMyBonds(token) }

            val listResult = listDeferred.await()
            val categoriesResult = categoriesDeferred.await()
            val bondsResult = bondsDeferred.await()

            // Fallas de categorías/miembros no bloquean la lista:
            // categorías desconocidas → chip neutral; asignatario → "Sin asignar".
            val categories = when (categoriesResult) {
                is ApiResult.Success -> CategoryResolver.build(categoriesResult.data)
                is ApiResult.Error -> emptyMap()
            }
            // ⚠ `children` y `memberCandidates` se derivan AQUÍ MISMO, del `Vinculo` que ya
            // venía en `GET /bonds`: el modal de alta no hace NINGUNA petición nueva (D: §4.4).
            val bond = (bondsResult as? ApiResult.Success)?.data?.firstOrNull { it.id == bondId }
            val memberNames = bond
                ?.members
                ?.mapNotNull { member ->
                    val user = member.user ?: return@mapNotNull null
                    val name = listOfNotNull(user.firstName, user.lastName)
                        .joinToString(" ")
                        .trim()
                    if (name.isBlank()) null else user.id to name
                }
                ?.toMap()
                ?: emptyMap()
            // ⚠ AS-9 con precisión: `bonds.repository.ts:27-30` YA filtra en el servidor los
            // miembros que se fueron (`leftAt IS NULL`). Lo que NO se puede filtrar es
            // "inactivo/pending" (`VinculoMember` no trae `leftAt` ni `user.isActive`), así que
            // el desplegable ofrece TODOS los miembros, incluido el propio, SIN inventar un
            // estado `pending`. Es la degradación declarada, no un olvido.
            val candidates = bond
                ?.members
                ?.mapNotNull { member ->
                    val user = member.user ?: return@mapNotNull null
                    val name = listOfNotNull(user.firstName, user.lastName)
                        .joinToString(" ")
                        .trim()
                    if (name.isBlank()) null else MemberCandidate(user.id, name)
                }
                .orEmpty()

            val derived = derivedFromBond(
                categories = categories,
                memberNames = memberNames,
                children = bond?.children.orEmpty(),
                memberCandidates = candidates,
            )

            when (listResult) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        actividades = listResult.data,
                        isLoading = false,
                    ).withDerived(derived)
                }
                is ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        listError = listResult.message,
                        isLoading = false,
                    ).withDerived(derived)
                }
            }
        }
    }

    /** Lo que el modal de alta necesita del vínculo, derivado en el bloque de `loadAll()`. */
    private data class DerivedBond(
        val categories: Map<String, CategoryInfo>,
        val memberNames: Map<String, String>,
        val children: List<VinculoChild>,
        val memberCandidates: List<MemberCandidate>,
    )

    private fun derivedFromBond(
        categories: Map<String, CategoryInfo>,
        memberNames: Map<String, String>,
        children: List<VinculoChild>,
        memberCandidates: List<MemberCandidate>,
    ) = DerivedBond(categories, memberNames, children, memberCandidates)

    private fun ActivitiesUiState.withDerived(d: DerivedBond) = copy(
        categories = d.categories,
        memberNames = d.memberNames,
        children = d.children,
        memberCandidates = d.memberCandidates,
    )

    /** Filtro puramente client-side sobre la lista ya cargada (sin refetch). */
    fun selectTab(tab: ActivityTab) {
        _uiState.value = _uiState.value.copy(selectedTab = tab)
    }

    /** Reintentar desde el estado de error de la lista → esqueleto + refetch. */
    fun retry() {
        loadAll()
    }

    /** Abre el detalle de [id] — R1. `Loading(id)` se escribe **fuera** del `launch` para
     *  que la hoja aparezca en el mismo frame del toque; [gen] se captura antes de lanzarlo
     *  y su comparación posterior es la que invalida la respuesta. */
    fun selectActividad(id: String) {
        val gen = ++detailGeneration
        _uiState.value = _uiState.value.copy(detail = DetailUiState.Loading(id))
        detailJob?.cancel()
        detailJob = scope.launch {
            val result = activitiesApi.getActividad(token, id)
            // ⚠ Comparación y escritura en el hilo de ESCRITURA —que es donde vive
            // [detailGeneration]: la carrera entre hilos desaparece por construcción (R-1).
            withContext(dispatcher) {
                // Obsoleta (descarte, o re-tap de otra): no escribe NADA, ni un campo.
                if (gen != detailGeneration) return@withContext
                _uiState.value = _uiState.value.copy(
                    detail = when (result) {
                        is ApiResult.Success -> DetailUiState.Content(id, result.data)
                        is ApiResult.Error -> DetailUiState.Error(id, result.message)
                    },
                )
            }
        }
    }

    /** Cierra el detalle de [id] — R1/R2, el único reseteo. Idempotente Y con guarda de id:
     *  un descarte de una apertura anterior (el `hide()` tardío) no toca la apertura viva, y
     *  el `detailGeneration++` deja obsoleta cualquier respuesta en vuelo para que no reabra
     *  la hoja ni pinte un error en la lista. */
    fun closeDetail(id: String) {
        if (_uiState.value.detail.openedId != id) return
        detailGeneration++
        detailJob?.cancel()
        _uiState.value = _uiState.value.copy(detail = DetailUiState.Closed)
    }

    /** Reintenta el detalle que falló — R6. Lee `Error.id` (ya no hay `lastSelectedId` que
     *  pueda quedar obsoleto) y reentra en `Loading(id)` → `Content`, sin casos especiales. */
    fun retryDetail() {
        val current = _uiState.value.detail
        if (current is DetailUiState.Error) selectActividad(current.id)
    }

    /** Dispara el snackbar para un acción inerte (placeholders). */
    fun onInertAction(message: String) {
        _uiState.value = _uiState.value.copy(snackbarMessage = message)
    }

    /**
     * Las mutaciones de adjuntos fallaban en SILENCIO: cada rama de error hacía
     * `return@launch` sin tocar estado. El usuario elegía el archivo, no veía nada
     * y concluía —con razón— que el botón no funcionaba. Este es el mismo canal que
     * ya usa `confirmAssignment`, así que el aviso sale por donde el usuario mira.
     */
    private fun reportAttachmentFailure(text: String) {
        _uiState.value = _uiState.value.copy(snackbarMessage = text)
    }

    /**
     * POST /activities/:id/confirm (única mutación permitida, R14).
     * Single-flight: mientras isConfirming, los taps siguientes se ignoran.
     * Success → aplicar estado del payload (NO refetch) + snackbar, sin navegar.
     * Error → snackbar con mensaje del backend (403 → texto localizado).
     */
    fun confirmAssignment(activityId: String) {
        if (_uiState.value.isConfirming) return
        scope.launch {
            _uiState.value = _uiState.value.copy(isConfirming = true)
            when (val result = activitiesApi.confirmAssignment(token, activityId)) {
                is ApiResult.Success -> {
                    val r = result.data
                    val detail = _uiState.value.detail
                    _uiState.value = _uiState.value.copy(
                        isConfirming = false,
                        actividades = _uiState.value.actividades.map {
                            if (it.id == activityId) {
                                it.copy(
                                    assignedConfirmed = r.assignedConfirmed,
                                    confirmedAt = r.confirmedAt,
                                )
                            } else {
                                it
                            }
                        },
                        // R1: el `Content` se REESCRIBE EN SITU por id; sin esta rama el chip
                        // "Asignación confirmada" quedaría obsoleto (la hoja vive sobre él).
                        detail = if (detail is DetailUiState.Content && detail.id == activityId) {
                            DetailUiState.Content(
                                id = activityId,
                                actividad = detail.actividad.copy(
                                    assignedConfirmed = r.assignedConfirmed,
                                    confirmedAt = r.confirmedAt,
                                ),
                            )
                        } else {
                            detail
                        },
                        snackbarMessage = "Asignación confirmada",
                    )
                }
                is ApiResult.Error -> {
                    val text = if (result.code == 403) {
                        "Solo el asignado puede confirmar"
                    } else {
                        result.message
                    }
                    _uiState.value = _uiState.value.copy(
                        isConfirming = false,
                        snackbarMessage = text,
                    )
                }
            }
        }
    }

    // ── create-activity · ALTA (T-FE-14) ─────────────────────────────────

    /**
     * Abre el modal de alta. ⚠ `createGeneration++` ANTES de escribir nada: invalida cualquier
     * respuesta en vuelo de un modal anterior, para que una respuesta tardía no lo reabra.
     *
     * Escenario del spec: con **exactamente un** hijo, queda preseleccionado — no se puede
     * inferir "elegir" a un hijo único (el usuario no notaría que puede elegir más), así que se
     * ahorra un toque; con dos o más no se presupone ninguno (R10).
     */
    fun openCreate() {
        createGeneration++
        createJob?.cancel()
        val only = _uiState.value.children.singleOrNull()
        val form = CreateForm(childrenIds = if (only != null) setOf(only.id) else emptySet())
        createBaseline = form
        _uiState.value = _uiState.value.copy(
            create = CreateUiState.Content,
            createForm = form,
            // El baseline ES el formulario recién abierto ⇒ dirty nasce en false por construcción.
            createDirty = false,
        )
    }

    /**
     * Cierra el modal. Idempotente y con `createGeneration++` en TODOS los caminos terminales
     * —incluido este descarte—: si el POST seguía en vuelo, su respuesta llega tarde y no debe
     * reabrir la hoja ni pintar un error en la lista (R9).
     */
    fun closeCreate() {
        if (_uiState.value.create is CreateUiState.Closed) return
        createGeneration++
        createJob?.cancel()
        createBaseline = CreateForm()
        _uiState.value = _uiState.value.copy(
            create = CreateUiState.Closed,
            createForm = CreateForm(),
            createDirty = false,
        )
    }

    /**
     * Único punto de escritura del formulario: recalcula [ActivitiesUiState.createDirty] a partir
     * del baseline, de modo que el flag NO puede desincronizarse de los campos.
     */
    private fun mutateCreateForm(transform: (CreateForm) -> CreateForm) {
        val next = transform(_uiState.value.createForm)
        _uiState.value = _uiState.value.copy(
            createForm = next,
            createDirty = next != createBaseline,
        )
    }

    fun setCreateTitle(value: String) = mutateCreateForm { it.copy(title = value) }

    fun setCreateDescription(value: String) = mutateCreateForm { it.copy(description = value) }

    fun setCreateCategory(id: String?) = mutateCreateForm { it.copy(categoryId = id) }

    /** Toggle de hijo — D20: `Set` +/− `id` ⇒ la UI no puede producir `[a, a]` por construcción. */
    fun toggleCreateChild(id: String) = mutateCreateForm {
        it.copy(
            childrenIds = if (id in it.childrenIds) it.childrenIds - id else it.childrenIds + id,
        )
    }

    /** `null` == "Sin asignar": el backend lo acepta como `assignedTo` ausente (AS-3). */
    fun setCreateAssignee(id: String?) = mutateCreateForm { it.copy(assignedTo = id) }

    fun setCreateDate(date: LocalDate) =
        mutateCreateForm { it.copy(startDate = date) }

    fun setCreateTime(time: TimeOfDay) =
        mutateCreateForm { it.copy(startTime = time) }

    /**
     * Elige la fecha de fin programado. La hora de fin se inicializa a `inicio + 1h`,
     * truncada a 23:59, para que el caso común ya sea válido sin tocar el reloj (A-15).
     */
    fun setCreateEndDate(date: LocalDate) = mutateCreateForm { form ->
        val startMinutes = form.startTime.hour * 60 + form.startTime.minute
        val endMinutes = (startMinutes + 60).coerceAtMost(23 * 60 + 59)
        form.copy(
            endDate = date,
            endTime = TimeOfDay(endMinutes / 60, endMinutes % 60),
        )
    }

    fun setCreateEndTime(time: TimeOfDay) = mutateCreateForm { it.copy(endTime = time) }

    fun clearCreateEnd() = mutateCreateForm { it.copy(endDate = null) }

    /**
     * `POST /activities/:bondId/create`. Corta en DOS puntos:
     * · `create is Loading` ⇒ un solo POST en vuelo (doble envío imposible);
     * · `validateCreateForm(...).isNotEmpty()` ⇒ ni siquiera sale la petición si el formulario
     *   es inválido. [validateCreateForm] es la MISMA función que gobierna el botón "Crear", así
     *   que UI y ViewModel no pueden discrepar.
     *
     * Éxito ⇒ `Closed` + reset del formulario + snackbar + **refetch** (D21: el servidor ordena
     * la lista y la entidad devuelta está incompleta; además el refetch recoge lo que haya hecho
     * el otro progenitor). Fallo ⇒ `Error` y **el formulario NO se toca** (spec).
     */
    fun submitCreate() {
        if (_uiState.value.create is CreateUiState.Loading) return
        if (validateCreateForm(_uiState.value.createForm).isNotEmpty()) return

        val gen = ++createGeneration
        val form = _uiState.value.createForm
        createJob?.cancel()
        _uiState.value = _uiState.value.copy(create = CreateUiState.Loading)
        createJob = scope.launch {
            val result = activitiesApi.createActividad(token, bondId, form.toRequest())
            withContext(dispatcher) {
                // Obsoleta (descarte con el POST en vuelo, o doble submit ya resuelto): no escribe.
                if (gen != createGeneration) return@withContext
                when (result) {
                    is ApiResult.Success -> {
                        createBaseline = CreateForm()
                        _uiState.value = _uiState.value.copy(
                            create = CreateUiState.Closed,
                            createForm = CreateForm(),
                            createDirty = false,
                            snackbarMessage = "Actividad creada",
                        )
                        loadAll()
                    }
                    is ApiResult.Error -> _uiState.value = _uiState.value.copy(
                        create = CreateUiState.Error(result.message),
                    )
                }
            }
        }
    }

    // ── create-activity · FINALIZAR (T-FE-14 / T-FE-18) ────────────────────

    /**
     * `POST /activities/:id/complete`. Parchea el detalle **in situ** con los valores
     * AUTORITATIVOS del servidor (`status`, `completedAt`) ⇒ sin una segunda ida al detalle.
     *
     * ⚠ CÓMO LLEGA EL ESTADO A LA PANTALLA, exactamente: `DetailContent` pinta
     *   `StatusTag(status = a.status)`, y `StatusTag` mapea `"DONE"` → **"Cumplido"**
     *   (`StatusTag.kt:37`). El botón "Finalizar" desaparece por su propia condición
     *   `if (actividad.status != "done")` (`ActividadDetailSheet.kt:257`). Los dos hechos
     *   dependen del MISMO `status` parcheado, así que son coherentes por construcción.
     *   🔴 NO hay ninguna "re-derivación desde `completedAt`" ni una leyenda propia llamada
     *   "Completada": si alguien las busca, no existen — no las añadas en silencio, porque
     *   el parche autoritativo basta.
     *
     * `loadAll()` cubre lista y compliance, que son agregados que el parche no toca.
     */
    fun completeActivity(activityId: String) {
        if (_uiState.value.isCompleting) return
        val gen = ++completeGeneration
        _uiState.value = _uiState.value.copy(isCompleting = true)
        completeJob?.cancel()
        completeJob = scope.launch {
            when (val result = activitiesApi.completeActivity(token, activityId)) {
                is ApiResult.Success -> withContext(dispatcher) {
                    if (gen != completeGeneration) return@withContext
                    val body = result.data
                    // ⚠ `detail` es un `DetailUiState` NO-NULABLE y sellado: `?.let { it.copy(...) }`
                    //   NO compila (una interfaz sellada no tiene `copy`, y el smart cast de un
                    //   `val` delegado tampoco se aplica). El `when` exhaustivo es la única forma
                    //   honesta de parchear, y de paso NO depende de que la hoja siga abierta.
                    val patched = when (val current = _uiState.value.detail) {
                        is DetailUiState.Content ->
                            if (current.id != activityId) {
                                current
                            } else {
                                // Sólo se copian los DOS campos autoritativos del servidor
                                // (`status`, `completedAt`); el resto del detalle sigue siendo el
                                // que ya tenía. Con el `status` parcheado, `StatusTag` pinta
                                // "Cumplido" y el botón "Finalizar" se retira solo (ver el KDoc).
                                current.copy(
                                    actividad = current.actividad.copy(
                                        status = body.status,
                                        completedAt = body.completedAt,
                                    ),
                                )
                            }
                        else -> current
                    }
                    _uiState.value = _uiState.value.copy(
                        isCompleting = false,
                        detail = patched,
                        snackbarMessage = "Actividad finalizada",
                    )
                    loadAll()
                }
                is ApiResult.Error -> withContext(dispatcher) {
                    if (gen != completeGeneration) return@withContext
                    _uiState.value = _uiState.value.copy(
                        isCompleting = false,
                        snackbarMessage = result.message,
                    )
                }
            }
        }
    }

    fun consumeSnackbar() {
        _uiState.value = _uiState.value.copy(snackbarMessage = null)
    }

    // ── activity-swipe-actions · ELIMINAR / CANCELAR / NO ASISTIR ─────────────

    /**
     * Una acción a la vez por actividad. ⇦ NO es un `isActing` global como el de
     * `isConfirming`: dos swipe distintos sobre filas distintas son independientes y
     * bloquearlos juntos los haría sentir lentos. La clave es el **id**, y su único
     * contenido es el texto que se está escribiendo.
     */
    private var swipeJobs = mutableMapOf<String, Job>()

    /** Qué acción está pendiente de UNO de los verbos reversibles, o `null`. */
    private var pendingUndoable: Pair<String, SwipeAction>? = null

    /**
     * Ticker de la oferta de deshacer. Existe porque R2 fija una ventana REAL: si nada
     * la cerrara, el botón "Deshacer" seguiría en pantalla para siempre y acabaría
     * mandar una petición que el servidor rechaza con 409. Compara contra el reloj en
     * cada tic en vez de contar downto porque el proceso puede congelarse en segundo
     * plano, y un contador que se quedó atrás mostraría un botón ya muerto.
     */
    private var undoTicker: Job? = null

    /**
     * Punto de entrada desde el swipe. Para [SwipeAction.DELETE] va directo al servidor;
     * el resto abre [SwipeReasonUiState] porque A1 exige motivo.
     */
    fun startSwipeAction(id: String, action: SwipeAction) {
        // R1 · Los flags del SERVIDOR son la autoridad. Si el cliente ofrece la acción y el
        // servidor la niega, la fila queda en un estado en el que el usuario ve una opción
        // que nunca funcionó. Se avisa en vez de enviar la petición: el 403 igual, pero el
        // snackbar explica por qué en vez de mostrar un error de permisos que no helpa.
        val row = _uiState.value.actividades.firstOrNull { it.id == id } ?: return
        val allowed = when (action) {
            SwipeAction.DELETE -> row.canDelete
            SwipeAction.CANCEL -> row.canCancel
            SwipeAction.DECLINE -> row.canDecline
        }
        if (!allowed) {
            _uiState.value = _uiState.value.copy(
                snackbarMessage = "Esta actividad ya no admite ${action.label.lowercase()}",
            )
            return
        }
        if (action.requiresReason) {
            _uiState.value = _uiState.value.copy(
                swipeReason = SwipeReasonUiState.Content(
                    id = id,
                    action = action,
                    title = row.title,
                ),
            )
        } else {
            submitSwipeAction(id, action, reason = null)
        }
    }

    /** Escribe el motivo — A1. Corta a 200 para que el diálogo no deje construir un 422. */
    fun setSwipeReason(value: String) {
        val current = _uiState.value.swipeReason
        if (current !is SwipeReasonUiState.Content) return
        if (current.isSubmitting) return
        _uiState.value = _uiState.value.copy(
            swipeReason = current.copy(
                reason = value.take(REASON_MAX_LENGTH),
                // Escribir BORRA el error del servidor anterior: si no, un 422 viejo
                // seguiría en pantalla con el texto ya corregido, que es la peor forma
                // de "no se puede enviar" porque no dice qué hacer.
                validation = null,
                serverError = null,
            ),
        )
    }

    /** Cierra el diálogo. Idempotente y sin efectos si ya estaba cerrado. */
    fun dismissSwipeReason() {
        if (_uiState.value.swipeReason is SwipeReasonUiState.Closed) return
        _uiState.value = _uiState.value.copy(swipeReason = SwipeReasonUiState.Closed)
    }

    /** Envía el diálogo abierto. Corta en DOS puntos, como `submitCreate()`:
     *  · `isSubmitting` ⇒ un solo POST en vuelo (doble envío imposible);
     *  · `validateSwipeReason(...) !is Valid` ⇒ ni siquiera sale la petición. */
    fun submitSwipeReason() {
        val current = _uiState.value.swipeReason
        if (current !is SwipeReasonUiState.Content) return
        if (current.isSubmitting) return
        val validation = validateSwipeReason(current.reason)
        if (validation !is ReasonValidation.Valid) {
            _uiState.value = _uiState.value.copy(
                swipeReason = current.copy(validation = validation, serverError = null),
            )
            return
        }
        _uiState.value = _uiState.value.copy(
            swipeReason = current.copy(validation = null, isSubmitting = true),
        )
        submitSwipeAction(current.id, current.action, current.reason.trim())
    }

    /**
     * El verbo real, común a las tres acciones.
     *
     * 🔴 R2 · La fila sale de la lista **de forma optimista** ANTES de la respuesta. Es
     * lo que hace que un swipe se sienta inmediato; la contrapartida es que hay que poder
     * volver atrás, y por eso [previous] viaja entero en [UndoOffer].
     *
     * El error NO es un no-op: se devuelve la fila EXACTAMENTE como estaba y, si venía un
     * [UndoOffer] de una acción anterior, se cierra — si no, "Deshacer" apuntaría a una
     * fila que este fallo acaba de reinsertar y cuyo estado real es el de antes.
     */
    private fun submitSwipeAction(id: String, action: SwipeAction, reason: String?) {
        val row = _uiState.value.actividades.firstOrNull { it.id == id } ?: return
        swipeJobs[id]?.cancel()
        // Optimista: fuera de la lista ya, y fuera del detalle si es la que está abierta.
        _uiState.value = _uiState.value.copy(
            actividades = _uiState.value.actividades.filterNot { it.id == id },
            detail = if (_uiState.value.detail.openedId == id) {
                DetailUiState.Closed
            } else {
                _uiState.value.detail
            },
            swipeReason = if (
                (_uiState.value.swipeReason as? SwipeReasonUiState.Content)?.id == id
            ) {
                SwipeReasonUiState.Closed
            } else {
                _uiState.value.swipeReason
            },
            undoOffer = null,
        )
        swipeJobs[id] = scope.launch {
            val result = when (action) {
                SwipeAction.DELETE -> activitiesApi.deleteActivity(token, id)
                SwipeAction.CANCEL -> activitiesApi.cancelActivity(
                    token, id,
                    ActivityReasonRequest(reason!!),
                )

                SwipeAction.DECLINE -> activitiesApi.declineActivity(
                    token, id,
                    ActivityReasonRequest(reason!!),
                )
            }
            withContext(dispatcher) {
                swipeJobs.remove(id)
                when (result) {
                    is ApiResult.Success -> {
                        // R2 · Sólo DELETE y DECLINE se deshacen. CANCEL no: no hay verbo
                        // de reversión en la API y ofrecerlo sería un botón que miente.
                        val reversible = action == SwipeAction.DELETE || action == SwipeAction.DECLINE
                        if (reversible) {
                            pendingUndoable = id to action
                            val offer = UndoOffer(
                                id = id,
                                action = action,
                                previous = row,
                                expiresAtEpochMillis = clock() + SWIPE_UNDO_WINDOW_MS,
                            )
                            _uiState.value = _uiState.value.copy(
                                undoOffer = offer,
                                snackbarMessage = offer.successMessage(),
                            )
                            startUndoTicker(offer)
                        } else {
                            _uiState.value = _uiState.value.copy(
                                snackbarMessage = "Actividad ${action.pastTense}",
                            )
                        }
                        loadAll()
                    }

                    is ApiResult.Error -> {
                        // Reversión del gesto optimista + por qué NO se reintenta a ciegas.
                        _uiState.value = _uiState.value.copy(
                            actividades = (_uiState.value.actividades + row)
                                .sortedBy { it.scheduledStart },
                            undoOffer = null,
                            snackbarMessage = explainSwipeError(result),
                        )
                        // R2 · Un 409 de ventana vencida significa que la fila YA NO se
                        // puede recuperar: el refetch sustituye al gesto, y sin esto la
                        // lista mentiría mostrando una actividad que el servidor ya borró.
                        if (result.code == 409) loadAll()
                    }
                }
            }
        }
    }

    /**
     * Traduce el fallo a algo accionable usando el CÓDIGO estable (R8), no el status.
     *
     * Un 409 son dos cosas distintas —"ya pasó" o "se venció la ventana"— y el móvil
     * tiene que distinguirlas: la primera se corrige esperando, la segunda no admite
     * reintento. Por eso el backend manda `code` y aquí se lee, en vez de mirar
     * `code == 409`, que sería el status y por tanto justo lo que R8 prohíbe.
     */
    private fun explainSwipeError(result: ApiResult.Error): String =
        when (result.errorCode) {
            "ACTIVITY_ALREADY_PAST" ->
                "Esta actividad ya pasó y no admite cambios"

            "ACTIVITY_UNDO_WINDOW_EXPIRED" ->
                "Ya no se puede deshacer: la ventana de 5 segundos venció"

            "ACTIVITY_FORBIDDEN" ->
                "No tienes permiso para esta acción en esta actividad"

            "ACTIVITY_NOT_FOUND" ->
                "Esta actividad ya no existe"

            "VALIDATION_FAILED" -> result.message
            // Sin código: el endpoint no codifica el error, así que el mensaje del
            // backend es lo mejor que hay. No se adivina una traducción por status.
            else -> result.message
        }

    /**
     * Toca "Deshacer" — R2. Sólo tiene efecto si la ventana sigue viva; un toque
     * posterior es un no-op silencioso en vez de un 409 que el usuario no pidió.
     */
    fun undoLastSwipe() {
        val offer = _uiState.value.undoOffer ?: return
        if (!offer.isLive(clock())) {
            clearUndoOffer()
            return
        }
        val (id, action) = pendingUndoable ?: return
        undoTicker?.cancel()
        _uiState.value = _uiState.value.copy(
            actividades = (_uiState.value.actividades + offer.previous)
                .sortedBy { it.scheduledStart },
            undoOffer = null,
            snackbarMessage = "Deshecho",
        )
        pendingUndoable = null
        swipeJobs[id] = scope.launch {
            val result = when (action) {
                SwipeAction.DELETE -> activitiesApi.restoreActivity(token, id)
                SwipeAction.DECLINE -> activitiesApi.undoDeclineActivity(token, id)
                SwipeAction.CANCEL -> return@launch
            }
            withContext(dispatcher) {
                swipeJobs.remove(id)
                when (result) {
                    is ApiResult.Success -> {
                        _uiState.value = _uiState.value.copy(snackbarMessage = "Deshecho")
                        loadAll()
                    }

                    is ApiResult.Error -> {
                        // El gesto optimista ya reinsertó la fila; si el servidor la
                        // devuelve 404/409 la fila no existe y quedarse con ella mentiría.
                        _uiState.value = _uiState.value.copy(
                            actividades = _uiState.value.actividades
                                .filterNot { it.id == id },
                            snackbarMessage = explainSwipeError(result),
                        )
                        loadAll()
                    }
                }
            }
        }
    }

    /** Cierra la oferta sin deshacer — el snackbar se tragó solo. */
    fun consumeUndoOffer() = clearUndoOffer()

    private fun clearUndoOffer() {
        undoTicker?.cancel()
        undoTicker = null
        pendingUndoable = null
        _uiState.value = _uiState.value.copy(undoOffer = null)
    }

    private fun startUndoTicker(offer: UndoOffer) {
        undoTicker?.cancel()
        undoTicker = scope.launch {
            // Tics de 100 ms: afinado para el texto "Deshecho (Ns)" sin gastar.
            while (clock() <= offer.expiresAtEpochMillis) {
                kotlinx.coroutines.delay(UNDO_TICK_MS)
                if (!offer.isLive(clock())) break
            }
            clearUndoOffer()
        }
    }

    fun loadAttachments(activityId: String) {
        _attachmentsLoading.value = true
        scope.launch {
            val res = activitiesApi.listAttachments(token, activityId)
            withContext(dispatcher) {
                _attachmentsLoading.value = false
                _attachments.value = when (res) {
                    is ApiResult.Success -> res.data
                    else -> emptyList()
                }
            }
        }
    }

    /**
     * Envoltura común de las mutaciones de adjuntos. No es ceremonia: sin el `catch`,
     * una excepción de red (timeout, DNS, conexión rechazada) escapaba del `launch` y
     * volvía a dejar el botón mudo —exactamente el síntoma reportado—. `CancellationException`
     * se re-lanza para no disfrazar una cancelación normal de un fallo.
     */
    private fun launchAttachmentOp(failurePrefix: String, block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reportAttachmentFailure("$failurePrefix: ${e.message ?: e::class.simpleName}")
            }
        }
    }

    /**
     * Presign + PUT los comparten `addAttachment` y `replaceAttachment`, y ninguno
     * de los dos podía fallar en silencio: el usuario acaba de elegir un archivo y
     * necesita saber por qué no entró. Ambas devuelven la señal de "ya avisé".
     */
    private suspend fun presignForUpload(
        activityId: String,
        fileName: String,
        compressed: CompressedImage,
    ): PresignAttachmentRes? =
        when (
            val res = activitiesApi.presignAttachment(
                token,
                activityId,
                PresignAttachmentReq(fileName, compressed.mimeType, compressed.bytes.size.toLong()),
            )
        ) {
            is ApiResult.Success -> res.data
            is ApiResult.Error -> {
                reportAttachmentFailure(res.message)
                null
            }
        }

    private suspend fun uploadOrReport(upload: PresignAttachmentRes, compressed: CompressedImage): Boolean {
        val res = httpClient.put(upload.uploadUrl) {
            contentType(ContentType.parse(compressed.mimeType))
            setBody(ByteArrayContent(compressed.bytes))
        }
        if (res.status.isSuccess()) return true
        reportAttachmentFailure("No se pudo subir el archivo (${res.status.value})")
        return false
    }

    fun addAttachment(activityId: String, fileName: String, mimeType: String, bytes: ByteArray) =
        launchAttachmentOp("No se pudo adjuntar el archivo") {
            val compressed = ImageCompressor().compress(bytes, mimeType)
            val upload = presignForUpload(activityId, fileName, compressed) ?: return@launchAttachmentOp
            if (!uploadOrReport(upload, compressed)) return@launchAttachmentOp
            when (
                val confirm = activitiesApi.confirmAttachment(
                    token,
                    activityId,
                    ConfirmAttachmentReq(upload.path, fileName, compressed.mimeType, compressed.bytes.size.toLong()),
                )
            ) {
                is ApiResult.Success -> _attachments.value = _attachments.value + confirm.data
                is ApiResult.Error -> reportAttachmentFailure(confirm.message)
            }
        }

    fun renameAttachment(activityId: String, attachmentId: String, fileName: String) =
        launchAttachmentOp("No se pudo renombrar el archivo") {
            when (val res = activitiesApi.renameAttachment(token, activityId, attachmentId, RenameAttachmentReq(fileName))) {
                is ApiResult.Success ->
                    _attachments.value = _attachments.value.map { if (it.id == attachmentId) res.data else it }
                is ApiResult.Error -> reportAttachmentFailure(res.message)
            }
        }

    fun replaceAttachment(activityId: String, attachmentId: String, fileName: String, mimeType: String, bytes: ByteArray) =
        launchAttachmentOp("No se pudo reemplazar el archivo") {
            val compressed = ImageCompressor().compress(bytes, mimeType)
            val upload = presignForUpload(activityId, fileName, compressed) ?: return@launchAttachmentOp
            if (!uploadOrReport(upload, compressed)) return@launchAttachmentOp
            when (
                val replace = activitiesApi.replaceAttachment(
                    token,
                    activityId,
                    attachmentId,
                    ReplaceAttachmentReq(upload.path, fileName, compressed.mimeType, compressed.bytes.size.toLong()),
                )
            ) {
                is ApiResult.Success ->
                    _attachments.value = _attachments.value.map { if (it.id == attachmentId) replace.data else it }
                is ApiResult.Error -> reportAttachmentFailure(replace.message)
            }
        }

    fun deleteAttachment(activityId: String, attachmentId: String) =
        launchAttachmentOp("No se pudo eliminar el archivo") {
            when (val res = activitiesApi.deleteAttachment(token, activityId, attachmentId)) {
                is ApiResult.Success ->
                    _attachments.value = _attachments.value.filter { it.id != attachmentId }
                is ApiResult.Error -> reportAttachmentFailure(res.message)
            }
        }

    fun destroy() {
        undoTicker?.cancel()
        loadJob?.cancel()
        // El RealtimeConnectionManager es una dependencia INYECTADA: su ciclo de vida lo
        // posee quien lo crea (App.kt lo construye y lo detiene en su DisposableEffect).
        // stop() aquí era una segunda invocación idempotente redundante (N4 del verify).
        scope.cancel()
    }

    private companion object {
        const val UNDO_TICK_MS = 100L
    }
}
