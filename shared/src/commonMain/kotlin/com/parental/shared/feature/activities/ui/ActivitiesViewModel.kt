package com.parental.shared.feature.activities.ui

import com.parental.shared.core.error.ApiResult
import com.parental.shared.core.network.CategoriesApi
import com.parental.shared.core.ui.components.CategoryInfo
import com.parental.shared.core.ui.components.CategoryResolver
import com.parental.shared.feature.activities.data.remote.ActivitiesApi
import com.parental.shared.feature.activities.domain.model.Actividad
import com.parental.shared.feature.activities.domain.model.ActividadDetalle
import com.parental.shared.feature.activities.ui.create.CreateForm
import com.parental.shared.feature.activities.ui.create.TimeOfDay
import com.parental.shared.feature.activities.ui.create.toRequest
import com.parental.shared.feature.activities.ui.create.validateCreateForm
import com.parental.shared.feature.bonds.data.remote.BondsApi
import com.parental.shared.feature.bonds.domain.model.VinculoChild
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
) {
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

    init {
        loadAll()
    }

    /** Carga lista + categorías + nombres de miembros en paralelo. */
    fun loadAll() {
        scope.launch {
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

    fun destroy() {
        scope.cancel()
    }
}
