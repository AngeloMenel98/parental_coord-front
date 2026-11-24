package com.parental.shared.feature.auth.ui

import com.parental.shared.core.error.ApiResult
import com.parental.shared.feature.auth.domain.repository.IAuthRepository
import com.parental.shared.feature.bonds.data.remote.BondsApi
import com.parental.shared.feature.session.data.SessionManager
import com.parental.shared.feature.session.domain.ViewerRole
import com.parental.shared.feature.session.domain.resolveViewerRole
import com.parental.shared.feature.thirdparty.data.remote.ThirdPartiesApi
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Estado del cambio obligatorio de contraseña (S2).
 *
 * Tres campos y un solo error: la hoja no tiene otra cosa que mostrar, y un error por campo
 * obligaría a pintar tres carteles debajo de tres inputs que se leen de arriba abajo.
 */
data class ChangePasswordUiState(
    val currentPassword: String = "",
    val newPassword: String = "",
    val repeatPassword: String = "",
    val isSaving: Boolean = false,
    val error: String? = null,
)

/** Falta la provisoria: sin ella no hay nada que autorizar. */
internal const val CHANGE_PASSWORD_CURRENT_MISSING = "Escribí tu contraseña provisoria."

/** Mínimo del back (`@MinLength(8)`), dicho antes de gastar la request. */
internal const val CHANGE_PASSWORD_NEW_TOO_SHORT = "La nueva contraseña necesita 8 caracteres o más."

/** `@Matches(/(?=.*[A-Za-z])/)` del back. */
internal const val CHANGE_PASSWORD_NEW_NEEDS_LETTER = "La nueva contraseña necesita al menos una letra."

/** `@Matches(/(?=.*[0-9])/)` del back. */
internal const val CHANGE_PASSWORD_NEW_NEEDS_DIGIT = "La nueva contraseña necesita al menos un número."

/** Cambiar "provisoria123" por "provisoria123" no es cambiar nada. */
internal const val CHANGE_PASSWORD_NEW_SAME_AS_CURRENT =
    "La nueva contraseña tiene que ser distinta de la provisoria."

/** El typo se detecta acá, no en el siguiente login. */
internal const val CHANGE_PASSWORD_REPEAT_MISMATCH = "Las dos contraseñas no coinciden."

/**
 * El rechazo de la provisoria, en castellano.
 *
 * El back lo devuelve como `401 Current password is incorrect` —el mismo status que un
 * token vencido— porque desde el servidor la contraseña actual es la credencial de la
 * operación. Mostrar ese texto en inglés dentro de una hoja en castellano no explica nada;
 * se traduce sólo este caso y todo lo demás se muestra tal cual vino.
 */
internal const val CHANGE_PASSWORD_CURRENT_INVALID = "La contraseña actual no es correcta."

/**
 * El cambio de contraseña obligatorio del primer ingreso (S2).
 *
 * 🔴 El token lo escribe este VM, no la pantalla: cuando el servidor acepta el cambio el back
 * emite uno nuevo y ninguna actualización de la sesión puede depender de que una composición
 * siga viva. Por eso recibe el [SessionManager] y no callbacks: el estado que se afirma en un
 * test es el mismo objeto que lee `App`.
 *
 * 🔴 Pero el FLAG de bloqueo NO lo apaga este VM, aunque sea el dueño del token. Apagarlo es
 * lo que hace recomponer el routing, y si se apagara acá la app podría montar el destino real
 * —el Home de los progenitores— con el rol del vínculo todavía en `null`, porque esa
 * derivación (`GET /bonds`) ocurre después. Quien apaga el flag es `App`, y sólo cuando la
 * derivación ya terminó (o se rindió): se avisa con [success] y se deja el flag como está.
 *
 * La validación de cliente repite la política del back a propósito. No es desconfianza: es
 * evitar que un typo cueste un viaje de red y, sobre todo, que el bloqueo se sienta como un
 * error del servidor en vez de como un formulario que dice qué falta.
 *
 * Es `ViewModel` (además del plain class que era en develop) para que `App` lo tome con
 * `koinViewModel` dentro del [androidx.lifecycle.ViewModelStoreOwner] de la sesión: la hoja
 * sobrevive a rotaciones y se libera con la sesión (logout), no con la rama.
 */
class ChangePasswordViewModel(
    private val authRepository: IAuthRepository,
    private val sessionManager: SessionManager,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _uiState = MutableStateFlow(ChangePasswordUiState())
    val uiState: StateFlow<ChangePasswordUiState> = _uiState.asStateFlow()

    /** `true` una vez que la contraseña cambió: la hoja ya no tiene nada que hacer. */
    private val _success = MutableStateFlow(false)
    val success: StateFlow<Boolean> = _success.asStateFlow()

    fun onCurrentPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(currentPassword = value, error = null)
    }

    fun onNewPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(newPassword = value, error = null)
    }

    fun onRepeatPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(repeatPassword = value, error = null)
    }

    fun submit() {
        val state = _uiState.value
        if (state.isSaving) return

        val error = validate(
            current = state.currentPassword,
            new = state.newPassword,
            repeat = state.repeatPassword,
        )
        if (error != null) {
            _uiState.value = state.copy(error = error)
            return
        }

        _uiState.value = state.copy(isSaving = true, error = null)

        scope.launch {
            authRepository.changePassword(state.currentPassword, state.newPassword)
                .onSuccess { response ->
                    // El repositorio ya cambió su token cacheado; la sesión de la app es
                    // otra cosa y recibe SÓLO el token nuevo. El flag queda como está a
                    // propósito: lo apaga `App` después de derivar el vínculo, porque
                    // apagarlo acá dejaría el routing recomponer hacia Home con `bondRole`
                    // en null y un tercero vería el Home de los progenitores (R3-001).
                    sessionManager.updateToken(response.access_token)
                    _uiState.value = ChangePasswordUiState()
                    _success.value = true
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        error = mapServerError(e.message),
                    )
                }
        }
    }

    fun destroy() {
        scope.cancel()
    }

    override fun onCleared() {
        // El clear del [androidx.lifecycle.ViewModelStoreOwner] de la sesión (logout)
        // y `destroy()` comparten un solo destino: no queda scope vivo tras la sesión.
        destroy()
    }

    /**
     * La política, en el orden en que se lee el formulario: primero la provisoria, después
     * la nueva (largo, letra, número, distinta) y al final la repetición. La repetición se
     * chequea última porque decir "no coinciden" cuando la nueva todavía no cumple la
     * política mandaría a corregir el campo equivocado.
     */
    private fun validate(current: String, new: String, repeat: String): String? {
        if (current.isEmpty()) return CHANGE_PASSWORD_CURRENT_MISSING
        if (new.length < MIN_PASSWORD_LENGTH) return CHANGE_PASSWORD_NEW_TOO_SHORT
        if (new.none { it.isLetter() }) return CHANGE_PASSWORD_NEW_NEEDS_LETTER
        if (new.none { it.isDigit() }) return CHANGE_PASSWORD_NEW_NEEDS_DIGIT
        if (new == current) return CHANGE_PASSWORD_NEW_SAME_AS_CURRENT
        if (new != repeat) return CHANGE_PASSWORD_REPEAT_MISMATCH
        return null
    }

    /**
     * Un mensaje que se pueda leer en la hoja.
     *
     * Sólo se traduce el caso conocido —el `401` del back por la provisoria— porque es el
     * único que tiene un texto fijo y en inglés. Un `400` de la política o un `503` caen al
     * mensaje del servidor tal cual: inventar una traducción para un texto que no se
     * conoce sería mostrar algo que nadie dijo.
     */
    private fun mapServerError(message: String?): String {
        val raw = message?.trim().orEmpty()
        if (raw.isEmpty()) return "No se pudo cambiar la contraseña"
        return if (raw.contains("current password", ignoreCase = true)) {
            CHANGE_PASSWORD_CURRENT_INVALID
        } else {
            raw
        }
    }

    private companion object {
        /** `@MinLength(8)` del `ChangePasswordDto` del back. */
        const val MIN_PASSWORD_LENGTH = 8
    }
}

/** Cuántas veces se intenta la derivación antes de rendirse con el rol en null (R4). */
internal const val BOND_DERIVATION_ATTEMPTS = 2

/** Aire entre intentos: un fallo transitorio no se reintenta en el mismo instante. */
internal const val BOND_DERIVATION_RETRY_DELAY_MS = 250L

/**
 * Lo que dejó UN intento de derivación.
 *
 * La distinción es lo que evita el bucle: «no hay vínculo» o «no hay un miembro que sea
 * este usuario» son respuestas CONCLUYENTES —reintentar devuelve lo mismo—, mientras que un
 * error de red o del servidor sí puede cambiar en el intento siguiente (R4, R3-003).
 */
internal enum class BondDerivationOutcome { Settled, Unresolved }

/**
 * Deriva el vínculo de quien está logueado — `bondId` y el rol DENTRO del vínculo — desde
 * `GET /bonds`, en UN intento.
 *
 * Versión token-free del helper de develop (que tiraba de `ApiClient`): las dos APIs llegan
 * por parámetro ya resueltas por Koin, y la autorización la inyecta el interceptor del
 * [HttpClient] único (NetworkModule). Se comparte entre los DOS caminos que derivan:
 * el login (cuando la contraseña no es provisoria, en [LoginViewModel]) y la salida del
 * cambio obligatorio (coordinador de `App`). Con el flag de contraseña encendido el back
 * responde `403 PASSWORD_CHANGE_REQUIRED` en esa ruta, así que el rol del vínculo —el que
 * decide si la persona es un tercero— sólo se puede derivar una vez que la contraseña
 * ya cambió.
 *
 * 🔴 La respuesta tardía se DESCARTA si la sesión ya no es la que la pidió (R1-002): entre
 * el request y la respuesta pudo correr un logout —o el login de otra persona—, y escribir
 * acá repoblaría `bondId`/`bondRole` sobre una sesión que ya no existe.
 */
internal suspend fun SessionManager.deriveBondFromServer(
    bondsApi: BondsApi,
    thirdPartiesApi: ThirdPartiesApi,
): BondDerivationOutcome {
    val sessionToken = token ?: return BondDerivationOutcome.Unresolved
    val sessionUserId = userId
    val result = bondsApi.getMyBonds()

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
                thirdPartiesApi.getMyGrant(sessionToken)
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
internal suspend fun SessionManager.deriveBondWithRetry(
    bondsApi: BondsApi,
    thirdPartiesApi: ThirdPartiesApi,
    attempts: Int = BOND_DERIVATION_ATTEMPTS,
): Boolean {
    repeat(attempts) { attempt ->
        if (deriveBondFromServer(bondsApi, thirdPartiesApi) == BondDerivationOutcome.Settled) return true
        if (attempt < attempts - 1) delay(BOND_DERIVATION_RETRY_DELAY_MS)
    }
    return false
}
