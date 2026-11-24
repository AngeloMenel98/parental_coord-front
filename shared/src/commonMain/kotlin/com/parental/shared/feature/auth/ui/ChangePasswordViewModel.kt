package com.parental.shared.feature.auth.ui

import com.parental.shared.feature.auth.domain.repository.IAuthRepository
import com.parental.shared.feature.session.data.SessionManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
 */
class ChangePasswordViewModel(
    private val authRepository: IAuthRepository,
    private val sessionManager: SessionManager,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
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
