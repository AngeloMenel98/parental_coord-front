package com.parental.shared

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.parental.shared.core.error.ApiResult
import com.parental.shared.core.network.ApiClient
import com.parental.shared.core.ui.components.BottomNavBar
import com.parental.shared.core.ui.theme.ParentalCoordinationTheme
import com.parental.shared.core.ui.theme.ThemeController
import com.parental.shared.feature.activities.ui.ActividadesScreen
import com.parental.shared.feature.activities.ui.ActividadesViewModel
import com.parental.shared.feature.expenses.ui.ExpensesScreen
import com.parental.shared.feature.expenses.ui.ExpensesViewModel
import com.parental.shared.feature.auth.data.repository.AuthRepository
import com.parental.shared.feature.auth.ui.ChangePasswordScreen
import com.parental.shared.feature.auth.ui.ChangePasswordViewModel
import com.parental.shared.feature.auth.ui.LoginScreen
import com.parental.shared.feature.auth.ui.LoginViewModel
import com.parental.shared.feature.admin.ui.AdminScreen
import com.parental.shared.feature.admin.ui.AdminViewModel
import com.parental.shared.feature.home.ui.HomeScreen
import com.parental.shared.feature.home.ui.HomeViewModel
import com.parental.shared.feature.notifications.ui.NotificationsScreen
import com.parental.shared.feature.notifications.ui.NotificationsViewModel
import com.parental.shared.feature.notifications.ui.UnreadBadge
import com.parental.shared.feature.notifications.ui.UnreadCountPoller
import com.parental.shared.feature.session.data.SessionManager
import com.parental.shared.feature.session.domain.ViewerRole
import com.parental.shared.feature.session.domain.resolveViewerRole
import com.parental.shared.feature.thirdparty.ui.ThirdPartiesScreen
import com.parental.shared.feature.thirdparty.ui.ThirdPartiesViewModel
import com.parental.shared.feature.thirdparty.ui.ThirdPartyRoot
import com.parental.shared.core.network.realtime.RealtimeConnectionManager
import com.parental.shared.platform.Platform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private sealed class Screen(val route: String) {
    data object Login : Screen("login")

    /**
     * La hoja de cambio obligatorio de contraseña (S2). No es una pantalla del recorrido:
     * mientras `mustChangePassword` esté encendido es la ÚNICA, y por eso se resuelve antes
     * que el rol.
     */
    data object ChangePassword : Screen("change_password")
    data object Home : Screen("home")
    data object Admin : Screen("admin")
    data object ThirdParty : Screen("third_party")
}

@Composable
fun App() {
    val authRepository = remember { AuthRepository() }
    val sessionManager = remember { SessionManager() }

    LaunchedEffect(Unit) {
        sessionManager.restoreFromStorage()
        // Legacy sessions (pre userId) — backfill via GET /auth/me so the
        // activities confirm-affordance visibility rule can evaluate.
        val token = sessionManager.token
        if (token != null && sessionManager.userId == null) {
            when (val me = ApiClient.authApi().me(token)) {
                is ApiResult.Success -> sessionManager.updateUserId(me.data.id)
                is ApiResult.Error -> Unit // degrade safely: no confirm affordance until re-login
            }
        }
        // El rol en el vínculo se deriva al loguear, pero una sesión restaurada de una
        // versión anterior no lo tiene; sin él la app caería en el Home de los
        // progenitores con los datos de todo el vínculo (S5). Se deriva del vínculo
        // guardado y, si no hay ninguno, queda `null`: el `else` ya es el Home de siempre,
        // que es la degradación segura.
        val restoredToken = sessionManager.token
        if (restoredToken != null && sessionManager.bondRole == null && sessionManager.userId != null) {
            when (val bonds = ApiClient.bondsApi().getMyBonds(restoredToken)) {
                is ApiResult.Success -> {
                    val bond = bonds.data.firstOrNull { it.id == sessionManager.bondId }
                        ?: bonds.data.firstOrNull()
                    sessionManager.updateBondId(bond?.id)
                    if (bond != null) {
                        val memberRole = bond.members
                            .firstOrNull { it.user?.id == sessionManager.userId }
                            ?.role
                        if (memberRole != null) {
                            sessionManager.updateBondRole(memberRole)
                        } else {
                            // S5: al tercero le redactan `members`, así que su fila no viaja;
                            // el grant propio es lo único que lo identifica al restaurar.
                            val grant = try {
                                ApiClient.thirdPartiesApi().getMyGrant(restoredToken)
                            } catch (e: Exception) {
                                null
                            }
                            if (sessionManager.token == restoredToken) {
                                when (
                                    val resolved = resolveViewerRole(
                                        bond.members,
                                        sessionManager.userId,
                                        grant,
                                    )
                                ) {
                                    is ViewerRole.Known -> resolved.role?.let(sessionManager::updateBondRole)
                                    ViewerRole.Unknown -> Unit
                                }
                            }
                        }
                    }
                }
                is ApiResult.Error -> Unit
            }
        }
    }

    // El interruptor sol/luna del Inicio elige el tema; sin decisión explícita manda el
    // sistema. El controlador guarda la DECISIÓN y se resuelve en cada composición, así
    // que un cambio de tema del sistema se refleja sin recrear nada.
    val themeController = remember { ThemeController() }
    val systemDark = isSystemInDarkTheme()
    val darkTheme = themeController.resolve(systemDark)

    // ADR-10: un único wrap del tema en commonMain — Android hoy, iOS hereda.
    ParentalCoordinationTheme(darkTheme = darkTheme) {
        AppContent(
            authRepository = authRepository,
            sessionManager = sessionManager,
            isDarkTheme = darkTheme,
            onToggleTheme = { themeController.toggle(systemDark) },
        )
    }
}

/**
 * Contenido de la app (ADR-10): el bloque `when` existente se movió verbatim
 * desde App() — los `return` tempranos son legales dentro de una composable
 * función (los lambdas no-inline no los permitirían). Cero delta de layout.
 */
@Composable
private fun AppContent(
    authRepository: AuthRepository,
    sessionManager: SessionManager,
    isDarkTheme: Boolean = false,
    onToggleTheme: (() -> Unit)? = null,
) {
    // S12 · G-3 — la salida vive en la barra global de todas las vistas, así que el
    // efecto es UNO solo: no hay dos lambdas que puedan divergir.
    val logout: () -> Unit = {
        authRepository.logout()
        sessionManager.clear()
    }

    /**
     * Scope de la SESIÓN, no de una rama: el trabajo que sigue después de cambiar la
     * contraseña obligatoria tiene que sobrevivir a que esa rama se desmonte — es
     * exactamente lo que pasa cuando el flag se apaga.
     */
    val sessionScope = rememberCoroutineScope()

    /**
     * Dueño único de la derivación del vínculo al salir del cambio obligatorio (S2/R3-001).
     * Se recuerda a nivel de sesión —y no en la hoja— justamente porque la hoja se desmonta
     * cuando el flag se apaga, que es el último paso de la derivación.
     */
    val bondDerivation = remember(sessionManager, sessionScope) {
        BondDerivationCoordinator(sessionManager, sessionScope)
    }

    val currentScreen = if (sessionManager.loginPending) {
        Screen.Login
    } else if (sessionManager.isLoggedIn) {
        when {
            // S2 · el bloqueo gana sobre el rol: da igual si es admin, progenitor o
            // tercero, con una contraseña provisoria el back responde 403 en todas las
            // rutas autenticadas. Se resuelve ANTES de `role`/`bondRole` para que ninguna
            // vista con datos llegue a montarse. Cuando el flag se apaga, este mismo
            // `when` recalcula y cae en el destino real sin ninguna navegación explícita.
            sessionManager.mustChangePassword -> Screen.ChangePassword
            sessionManager.role == "admin" -> Screen.Admin
            // S1/S5: el tercero temporal tiene su propio árbol. El `systemRole` sigue
            // siendo `user`; lo que decide acá es el rol DENTRO del vínculo.
            sessionManager.bondRole == "third_party" -> Screen.ThirdParty
            else -> Screen.Home
        }
    } else {
        Screen.Login
    }

    when (currentScreen) {
        Screen.Login -> {
            val viewModel = remember { LoginViewModel(authRepository) }
            DisposableEffect(Unit) {
                onDispose { viewModel.destroy() }
            }
            LoginScreen(
                viewModel = viewModel,
                onLoginSuccess = { response ->
                    val name = response.user.firstName ?: response.user.email

                    sessionManager.beginLogin()

                    sessionManager.setSession(
                        token = response.access_token,
                        userName = name,
                        role = response.user.systemRole,
                        userId = response.user.id,
                        // S2/D3: es el servidor el que dice si la contraseña con la que se
                        // entró la generó el sistema. Si el flag se pierde acá, la app
                        // abriría el Inicio de alguien que debe cambiarla.
                        mustChangePassword = response.user.mustChangePassword,
                    )

                    // D2/F7: el rol vive en el miembro del vínculo, no en el JWT ni en
                    // `system_role`. 🔴 Con una contraseña provisoria NO se deriva: el
                    // guard del back responde `403 PASSWORD_CHANGE_REQUIRED` en `GET
                    // /bonds` mientras el flag esté encendido, así que sería trabajo
                    // perdido a sabiendas (R2-001). Esa derivación la hace
                    // [BondDerivationCoordinator] al salir del cambio.
                    try {
                        if (!sessionManager.mustChangePassword) {
                            sessionManager.deriveBondWithRetry()
                        }
                    } finally {
                        // Complete login — now safe to navigate to Home/Admin
                        sessionManager.completeLogin()
                    }
                },
            )
        }

        Screen.ChangePassword -> {
            // El VM escribe el token nuevo y avisa que el cambio salió; el FLAG lo apaga
            // esta capa, y sólo después de derivar el vínculo (R3-001). Al revés, este
            // mismo `when` recompondría hacia el destino real con `bondRole` todavía en
            // null: un tercero montaría el Home de los progenitores.
            val changePasswordViewModel = remember {
                ChangePasswordViewModel(
                    authRepository = authRepository,
                    sessionManager = sessionManager,
                )
            }
            DisposableEffect(Unit) {
                onDispose {
                    changePasswordViewModel.destroy()
                    // S2: mientras el flag estaba encendido `GET /bonds` respondía 403, así
                    // que el login no pudo derivar el vínculo. Además del camino de éxito
                    // se pide al desmontarse la hoja —una red de seguridad para el caso en
                    // que el efecto de éxito no llegue a correr—; el coordinador comparte
                    // el guard entre los dos, así que una sola request queda en vuelo
                    // (R3-003) y ninguna se repite sobre un resultado ya concluyente.
                    bondDerivation.deriveOnDispose()
                }
            }
            ChangePasswordScreen(
                viewModel = changePasswordViewModel,
                onLogout = logout,
                onSuccess = { bondDerivation.deriveThenRelease() },
            )
        }

        Screen.Admin -> {
            val adminApi = remember { ApiClient.adminApi() }
            val token = sessionManager.token
            if (token == null) {
                sessionManager.clear()
                return
            }
            val viewModel = remember {
                AdminViewModel(
                    adminApi = adminApi,
                    token = token,
                )
            }
            DisposableEffect(Unit) {
                onDispose { viewModel.destroy() }
            }
            AdminScreen(
                viewModel = viewModel,
                onLogout = logout,
            )
        }

        Screen.ThirdParty -> {
            val thirdPartyToken = sessionManager.token
            if (thirdPartyToken == null) {
                sessionManager.clear()
                return
            }
            ThirdPartyRoot(
                token = thirdPartyToken,
                userName = sessionManager.userName.orEmpty(),
                bondId = sessionManager.bondId,
                currentUserId = sessionManager.userId,
                onLogout = logout,
            )
        }

        Screen.Home -> {
            var bottomRoute by remember { mutableStateOf("home") }
            var showNotifications by remember { mutableStateOf(false) }
            var expenseAttachmentDraft by remember { mutableStateOf<Pair<String, String>?>(null) }
            val token = sessionManager.token
            val userName = sessionManager.userName.orEmpty()

            if (token == null) {
                sessionManager.clear()
                return
            }

            // Terceros ya navega (T15): lista, alta, edición y revocación de los accesos
            // que este progenitor otorgó.
            val onNavigateBottom: (String) -> Unit = { route ->
                when (route) {
                    "home", "activities", "expenses", "thirdparty" -> bottomRoute = route
                    else -> Unit
                }
            }

            // Realtime (slice 5): UN manager por composición de Home, ya no dentro de la
            // pestaña Activities. El badge de Inicio necesita la señal
            // `notification.created` mientras el usuario está en Home, y la pestaña se
            // desmonta al navegar. Sin vínculo no hay frames posibles (el handshake
            // cerraría 4403 en bucle de reconexión), así que sólo corre con bondId.
            val bondId = sessionManager.bondId
            val currentUserId = sessionManager.userId
            val realtimeManager = remember(token) {
                RealtimeConnectionManager(
                    http = ApiClient.httpClient,
                    url = Platform.wsUrl,
                    tokenProvider = { sessionManager.token },
                )
            }
            // Único dueño del ciclo de vida (antes lo era la rama Activities). start() y
            // stop() son idempotentes ⇒ exactamente UN stop() por manager, y un bondId que
            // aparece tarde (backfill) arranca el loop sin recrear el manager.
            DisposableEffect(realtimeManager, bondId) {
                if (bondId != null) realtimeManager.start() else realtimeManager.stop()
                onDispose { realtimeManager.stop() }
            }

            // Notificaciones (slice 4): el contador vive acá porque lo leen DOS
            // superficies (la campana de Home y la lista) y lo escriben TRES
            // (el poller, markRead, markAllRead). Una sola fuente de verdad.
            val notificationsApi = remember { ApiClient.notificationsApi() }
            val unreadBadge = remember(token) { UnreadBadge() }
            val unreadPoller = remember(token) {
                UnreadCountPoller(
                    api = notificationsApi,
                    token = token,
                    badge = unreadBadge,
                )
            }
            val unreadCount by unreadBadge.count.collectAsState()

            DisposableEffect(unreadPoller) {
                onDispose { unreadPoller.destroy() }
            }
            // Poll-only (S8): el bucle corre mientras haya una campana a la vista. Desde
            // S12 la campana vive también en Actividades (barra global), así que ya no
            // alcanza con "sólo Home": el contador tiene que estar fresco en las dos. En
            // la lista el VM ya sincroniza el badge, por eso ahí se detiene. Al volver de
            // la lista este efecto rearranca y fetcha de inmediato.
            LaunchedEffect(unreadPoller, showNotifications) {
                if (!showNotifications) {
                    unreadPoller.start()
                } else {
                    unreadPoller.stop()
                }
            }

            // Puente frame → badge (slice 5): un `notification.created` refresca el
            // contador sin esperar los 30 s del poll. El coalescer del manager ya acota
            // la ráfaga a ≤1 señal por ventana, así que esto son pocos GET extra, y
            // `refreshNow` funciona con el bucle parado (usuario en la lista o en
            // Actividades) ⇒ el badge no queda viejo mientras el poller duerme.
            LaunchedEffect(realtimeManager, unreadPoller) {
                realtimeManager.notificationSignals.collect {
                    unreadPoller.refreshNow()
                }
            }

            if (showNotifications) {
                // El VM se reconstruye en cada apertura ⇒ la lista siempre se carga
                // al abrirse, sin caché vieja entre visitas.
                val notificationsViewModel = remember(token) {
                    NotificationsViewModel(
                        api = notificationsApi,
                        token = token,
                        badge = unreadBadge,
                    )
                }
                DisposableEffect(notificationsViewModel) {
                    onDispose { notificationsViewModel.destroy() }
                }
                NotificationsScreen(
                    viewModel = notificationsViewModel,
                    onBack = { showNotifications = false },
                    onLogout = logout,
                )
            } else if (bottomRoute == "activities") {
                when {
                    bondId == null -> SinVinculoScreen(onGoHome = { bottomRoute = "home" })
                    else -> {
                        // userId: login lo provee; sesiones legacy → backfill vía /auth/me.
                        // keyed on userId para que el VM se reconstruya si el backfill llega tarde.
                        val activitiesViewModel = remember(currentUserId) {
                            ActividadesViewModel(
                                activitiesApi = ApiClient.activitiesApi(),
                                categoriesApi = ApiClient.categoriesApi(),
                                bondsApi = ApiClient.bondsApi(),
                                token = token,
                                bondId = bondId,
                                currentUserId = currentUserId ?: "",
                                realtime = realtimeManager,
                            )
                        }
                        // W2: keyed en currentUserId — al cambiar el userId (backfill tardío)
                        // o salir de la pantalla, el dispose corre contra el VM DE ESA
                        // composición ⇒ cada VM recibe exactamente UN destroy().
                        // S1: un único efecto (antes eran dos ⇒ destroy() dos veces).
                        // N4 (slice 5): el manager ya NO se detiene acá — su dueño es la
                        // pantalla Home, que sigue viva mientras el usuario navega.
                        DisposableEffect(currentUserId) {
                            onDispose { activitiesViewModel.destroy() }
                        }
                        ActividadesScreen(
                            viewModel = activitiesViewModel,
                            onNavigateBottom = onNavigateBottom,
                            onCreateExpenseFromAttachment = { _, attachmentId, fileName ->
                                expenseAttachmentDraft = attachmentId to fileName
                                bottomRoute = "expenses"
                            },
                            // S12 · la campana de la barra global abre la lista que ya
                            // vive en esta rama de Home; el contador es el mismo.
                            unreadCount = unreadCount,
                            onOpenNotifications = { showNotifications = true },
                            onLogout = logout,
                        )
                    }
                }
            } else if (bottomRoute == "expenses") {
                when {
                    bondId == null -> SinVinculoScreen(onGoHome = { bottomRoute = "home" })
                    else -> {
                        val expensesViewModel = remember(currentUserId) {
                            ExpensesViewModel(
                                api = ApiClient.expensesApi(),
                                token = token,
                                bondId = bondId,
                                realtime = realtimeManager,
                            )
                        }
                        DisposableEffect(currentUserId) {
                            onDispose { expensesViewModel.destroy() }
                        }
                        ExpensesScreen(
                            viewModel = expensesViewModel,
                            currentUserId = currentUserId,
                            onLogout = logout,
                            onNavigateBottom = onNavigateBottom,
                            unreadCount = unreadCount,
                            onOpenNotifications = { showNotifications = true },
                            pendingActivityAttachmentId = expenseAttachmentDraft?.first,
                            pendingActivityAttachmentFileName = expenseAttachmentDraft?.second,
                            onPendingAttachmentConsumed = { expenseAttachmentDraft = null },
                        )
                    }
                }
            } else if (bottomRoute == "thirdparty") {
                when {
                    bondId == null -> SinVinculoScreen(
                        onGoHome = { bottomRoute = "home" },
                        selectedRoute = "thirdparty",
                    )
                    else -> {
                        val thirdPartiesViewModel = remember(currentUserId, bondId) {
                            ThirdPartiesViewModel(
                                api = ApiClient.thirdPartiesApi(),
                                token = token,
                                bondId = bondId,
                            )
                        }
                        DisposableEffect(thirdPartiesViewModel) {
                            onDispose { thirdPartiesViewModel.destroy() }
                        }
                        LaunchedEffect(thirdPartiesViewModel) {
                            thirdPartiesViewModel.load()
                        }
                        ThirdPartiesScreen(
                            viewModel = thirdPartiesViewModel,
                            onLogout = logout,
                            onNavigateBottom = onNavigateBottom,
                            unreadCount = unreadCount,
                            onOpenNotifications = { showNotifications = true },
                        )
                    }
                }
            } else {
                val homeApi = remember { ApiClient.homeApi() }
                val viewModel = remember {
                    HomeViewModel(
                        homeApi = homeApi,
                        token = token,
                        userName = userName,
                        currentUserId = sessionManager.userId,
                    )
                }
                DisposableEffect(Unit) {
                    onDispose { viewModel.destroy() }
                }
                HomeScreen(
                    viewModel = viewModel,
                    onLogout = logout,
                    onNavigateBottom = onNavigateBottom,
                    unreadCount = unreadCount,
                    onOpenNotifications = { showNotifications = true },
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme,
                )
            }
        }
    }
}

/** Cuántas veces se intenta la derivación antes de rendirse con el rol en null (R4). */
private const val BOND_DERIVATION_ATTEMPTS = 2

/** Aire entre intentos: un fallo transitorio no se reintenta en el mismo instante. */
private const val BOND_DERIVATION_RETRY_DELAY_MS = 250L

/**
 * Lo que dejó UN intento de derivación.
 *
 * La distinción es lo que evita el bucle: «no hay vínculo» o «no hay un miembro que sea
 * este usuario» son respuestas CONCLUYENTES —reintentar devuelve lo mismo—, mientras que un
 * error de red o del servidor sí puede cambiar en el intento siguiente (R4, R3-003).
 */
private enum class BondDerivationOutcome { Settled, Unresolved }

/**
 * Deriva el vínculo de quien está logueado — `bondId` y el rol DENTRO del vínculo — desde
 * `GET /bonds`, en UN intento.
 *
 * Vive acá porque la llaman dos caminos con la misma necesidad: el login (cuando la
 * contraseña no es provisoria) y la salida del cambio obligatorio (S2). Con el flag de
 * contraseña encendido el back responde `403 PASSWORD_CHANGE_REQUIRED` en esa ruta, así que
 * el rol del vínculo —el que decide si la persona es un tercero— sólo se puede derivar una
 * vez que la contraseña ya cambió.
 *
 * 🔴 La respuesta tardía se DESCARTA si la sesión ya no es la que la pidió (R1-002): entre
 * el request y la respuesta pudo correr un logout —o el login de otra persona—, y escribir
 * acá repoblaría `bondId`/`bondRole` sobre una sesión que ya no existe.
 */
private suspend fun SessionManager.deriveBondFromServer(): BondDerivationOutcome {
    val sessionToken = token ?: return BondDerivationOutcome.Unresolved
    val sessionUserId = userId
    val result = try {
        ApiClient.bondsApi().getMyBonds(sessionToken)
    } catch (e: Exception) {
        // Sin red no hay nada que derivar todavía: es un fallo, no una respuesta.
        ApiResult.Error(0, e.message ?: "Network error")
    }

    if (token != sessionToken) return BondDerivationOutcome.Unresolved

    return when (result) {
        is ApiResult.Success -> {
            val bond = result.data.firstOrNull { it.id == bondId } ?: result.data.firstOrNull()
            updateBondId(bond?.id)
            if (bond == null) {
                updateBondRole(null)
                return BondDerivationOutcome.Settled
            }

            val memberRole = bond.members.firstOrNull { it.user?.id == sessionUserId }?.role
            if (memberRole != null) {
                updateBondRole(memberRole)
                return BondDerivationOutcome.Settled
            }

            // S5 le redacta `members` al tercero temporal: su propia fila NO viaja en
            // `GET /bonds`, así que el rol no se puede leer de ahí (el bug que lo dejaba
            // en el Home del progenitor). `GET /third-parties/me` sí lo identifica:
            // 200/403 = tercero (403 es el grant revocado, y su vista muestra el corte),
            // 404 = no tiene grant → no es tercero.
            val ownGrant = try {
                ApiClient.thirdPartiesApi().getMyGrant(sessionToken)
            } catch (e: Exception) {
                null
            }
            if (token != sessionToken) return BondDerivationOutcome.Unresolved

            when (val resolved = resolveViewerRole(bond.members, sessionUserId, ownGrant)) {
                is ViewerRole.Known -> {
                    updateBondRole(resolved.role)
                    BondDerivationOutcome.Settled
                }
                ViewerRole.Unknown -> BondDerivationOutcome.Unresolved
            }
        }
        is ApiResult.Error -> BondDerivationOutcome.Unresolved
    }
}

/**
 * [deriveBondFromServer] con hasta [attempts] intentos, para un fallo transitorio (R4).
 *
 * Devuelve `true` cuando la derivación quedó resuelta (con o sin rol) y `false` cuando se
 * agotaron los intentos sin respuesta concluyente.
 */
private suspend fun SessionManager.deriveBondWithRetry(
    attempts: Int = BOND_DERIVATION_ATTEMPTS,
): Boolean {
    repeat(attempts) { attempt ->
        if (deriveBondFromServer() == BondDerivationOutcome.Settled) return true
        if (attempt < attempts - 1) delay(BOND_DERIVATION_RETRY_DELAY_MS)
    }
    return false
}

/**
 * Coordina la derivación del vínculo al salir del cambio obligatorio de contraseña (S2).
 *
 * Dos caminos la piden —el éxito del cambio y el desmontaje de la hoja— y este objeto es lo
 * que los vuelve un solo trabajo:
 *
 * - **Un intento en vuelo**: si ya hay una derivación corriendo, el segundo camino no
 *   arranca otra (R3-003).
 * - **Un presupuesto por token**: [BOND_DERIVATION_ATTEMPTS] intentos en total, compartidos
 *   entre los dos caminos. Agotado el presupuesto no se reintenta: el flag se apaga igual
 *   porque la contraseña YA cambió, y el routing cae en el Home de siempre.
 * - **Nada se repite sobre un resultado concluyente**: si la respuesta fue «hay rol» o «no
 *   hay vínculo/rol», [settledFor] lo recuerda y se deja de pedir. Ese es el caso que antes
 *   no terminaba nunca, porque `bondRole == null` no distingue «todavía no sé» de «no hay».
 * - **El flag lo apaga sólo el camino de éxito**, y recién cuando la derivación terminó
 *   (R3-001): [deriveOnDispose] deriva pero nunca libera el bloqueo.
 */
private class BondDerivationCoordinator(
    private val sessionManager: SessionManager,
    private val scope: CoroutineScope,
) {
    /** Token cuya derivación ya quedó concluida: reintentar no cambiaría nada. */
    private var settledFor: String? = null

    /** Token al que pertenece el presupuesto de intentos ([attemptsUsed]). */
    private var budgetFor: String? = null
    private var attemptsUsed: Int = 0

    /** El único trabajo de derivación que puede estar corriendo. */
    private var inFlight: Job? = null

    /** Token cuyo éxito espera que se apague el flag cuando la derivación termine. */
    private var releaseRequestedFor: String? = null

    /**
     * Salida exitosa del cambio: deriva con el token NUEVO y DESPUÉS apaga el flag, que es
     * lo que hace recomponer el routing hacia la vista real.
     */
    fun deriveThenRelease() {
        val token = sessionManager.token ?: return
        releaseRequestedFor = token
        start()
    }

    /** Red de seguridad del desmontaje de la hoja: deriva, pero no apaga el flag. */
    fun deriveOnDispose() = start()

    private fun start() {
        val sessionToken = sessionManager.token
        if (sessionToken == null) {
            // Sin sesión no hay nada que derivar ni a quién liberar.
            releaseRequestedFor = null
            return
        }
        if (inFlight?.isActive == true) return
        if (settledFor == sessionToken) {
            releaseNow(sessionToken)
            return
        }

        val remaining = BOND_DERIVATION_ATTEMPTS - attemptsFor(sessionToken)
        val job = scope.launch {
            val settled = remaining > 0 && sessionManager.deriveBondWithRetry(remaining)
            attemptsUsed += remaining.coerceAtLeast(0)
            if (sessionManager.token == sessionToken) {
                if (settled) {
                    settledFor = sessionToken
                } else {
                    // No se esconde: el rol queda en null y conviene poder verlo en el log. El
                    // flag se apaga igual — la contraseña cambió, y dejarlo encendido sería
                    // encerrar a la persona en la hoja por un fallo de red.
                    println(
                        "No se pudo derivar el vínculo tras el cambio de contraseña " +
                            "($BOND_DERIVATION_ATTEMPTS intentos); el bloqueo se apaga igual.",
                    )
                }
                releaseNow(sessionToken)
            } else {
                // La sesión cambió mientras corría: este trabajo es de OTRO usuario. No se
                // toca el flag de la sesión actual (R1/R3/R4-001) y no se reintenta contra
                // el token nuevo.
                if (releaseRequestedFor == sessionToken) releaseRequestedFor = null
            }
        }
        inFlight = job
        job.invokeOnCompletion {
            if (inFlight === job) inFlight = null
            // Una sesión nueva pudo pedir su liberación mientras el job viejo corría.
            if (releaseRequestedFor != null && releaseRequestedFor == sessionManager.token) {
                start()
            }
        }
    }

    /**
     * Apaga el bloqueo si el éxito de [sessionToken] lo pidió y esa sesión sigue viva.
     *
     * Keyeado por token a propósito: un pedido viejo de la sesión A no puede apagar el flag
     * de la sesión B que entró después (R1/R3/R4-001).
     */
    private fun releaseNow(sessionToken: String) {
        if (releaseRequestedFor != sessionToken) return
        releaseRequestedFor = null
        if (sessionManager.token != sessionToken) return
        sessionManager.updateMustChangePassword(false)
    }

    /** Intentos ya gastados para [sessionToken]; un token nuevo arranca el presupuesto. */
    private fun attemptsFor(sessionToken: String): Int {
        if (budgetFor != sessionToken) {
            budgetFor = sessionToken
            attemptsUsed = 0
        }
        return attemptsUsed
    }
}

/** Estado liviano sin VM (A14): sin vínculo activo no se puede listar actividades. */
@Composable
private fun SinVinculoScreen(
    onGoHome: () -> Unit,
    selectedRoute: String = "activities",
) {
    Scaffold(
        bottomBar = {
            BottomNavBar(
                selectedRoute = selectedRoute,
                onNavigate = { route -> if (route == "home") onGoHome() },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Sin vínculo activo",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "No hay un vínculo activo para mostrar actividades.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onGoHome) {
                    Text("Ir al inicio")
                }
            }
        }
    }
}
