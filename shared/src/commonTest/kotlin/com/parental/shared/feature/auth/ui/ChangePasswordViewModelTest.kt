package com.parental.shared.feature.auth.ui

import com.parental.shared.feature.auth.domain.model.AuthResponse
import com.parental.shared.feature.auth.domain.model.User
import com.parental.shared.feature.auth.domain.repository.IAuthRepository
import com.parental.shared.feature.session.data.SessionManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * El cambio obligatorio de contraseña: qué valida el cliente ANTES de gastar una request,
 * qué hace con el error del servidor y qué deja escrito en la sesión cuando sale bien.
 *
 * Igual que `ThirdPartyViewModelTest`: `UnconfinedTestDispatcher` inyectado, repositorio
 * sustituido (nunca hay red) y `destroy()` al final de cada test.
 *
 * 🔴 La sesión se afirma sobre el [SessionManager] REAL, no sobre un espía: el contrato es
 * que el token nuevo queda en el objeto que lee el routing, y que el FLAG de bloqueo NO se
 * apaga acá — porque apagarlo antes de derivar el vínculo haría montar el Home de los
 * progenitores a un tercero (R3-001); lo apaga `App`, después de derivar. La
 * persistencia (`Platform.saveString`) es un no-op acá — sin `init()` no hay prefs — y por
 * eso este test no prueba la escritura en disco, sólo el estado en memoria.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChangePasswordViewModelTest {

    private val oldToken = "token-provisorio"
    private val newToken = "token-nuevo"
    private val viewModels = mutableListOf<ChangePasswordViewModel>()

    private companion object {
        val USER = User(
            id = "user-1",
            email = "tercero@example.com",
            systemRole = "user",
            firstName = "Marta",
            lastName = "Gómez",
            mustChangePassword = true,
        )
        val CHANGED_USER = USER.copy(mustChangePassword = false)
        const val CURRENT = "provisoria123"
        const val NEW = "elegida456"
    }

    @AfterTest
    fun tearDown() {
        viewModels.forEach { it.destroy() }
        viewModels.clear()
    }

    @Test
    fun `el cambio exitoso reemplaza el token y no toca el flag`() {
        val repository = FakeAuthRepository()
        val session = session()
        val viewModel = viewModel(repository = repository, session = session)

        fill(viewModel)
        viewModel.submit()

        assertEquals(
            listOf(CURRENT to NEW),
            repository.changeCalls,
            "🔴 S2: el cambio manda la provisoria y la nueva al endpoint.",
        )
        assertEquals(
            newToken,
            session.token,
            "🔴 S2: el token que sigue contando es el que devolvió el cambio, no el viejo.",
        )
        assertTrue(
            session.mustChangePassword,
            "🔴 R3-001: el VM ya NO apaga el bloqueo; lo apaga `App` recién cuando el vínculo " +
                "está derivado. Si el flag cayera acá, el routing recompondría hacia el Home " +
                "de los progenitores con `bondRole` todavía en null.",
        )
        assertTrue(
            viewModel.success.value,
            "🔴 S2: la pantalla se entera de que el cambio salió y `App` deriva el vínculo.",
        )
        assertNull(
            viewModel.uiState.value.error,
            "🔴 S2: un éxito no deja cartel de error.",
        )
    }

    @Test
    fun `un cambio exitoso no inventa el rol del vínculo`() {
        val repository = FakeAuthRepository()
        val session = session()
        val viewModel = viewModel(repository = repository, session = session)

        fill(viewModel)
        viewModel.submit()

        assertNull(
            session.bondRole,
            "🔴 R3-001: el rol lo deriva `App` con `GET /bonds`; el VM no puede fabricarlo " +
                "ni dejarlo como estaba.",
        )
    }

    @Test
    fun `si la provisoria está mal se muestra el mensaje en español y no se toca la sesión`() {
        val repository = FakeAuthRepository().apply {
            changeResult = Result.failure(Exception("Current password is incorrect"))
        }
        val session = session()
        val viewModel = viewModel(repository = repository, session = session)

        fill(viewModel)
        viewModel.submit()

        assertEquals(
            CHANGE_PASSWORD_CURRENT_INVALID,
            viewModel.uiState.value.error,
            "🔴 S2: el rechazo del servidor por la provisoria se explica en castellano.",
        )
        assertEquals(
            oldToken,
            session.token,
            "🔴 S2: un cambio fallido no puede dejar la sesión con un token que nadie emitió.",
        )
        assertTrue(
            session.mustChangePassword,
            "🔴 S2: el bloqueo sigue puesto hasta que el cambio sea real.",
        )
        assertFalse(
            viewModel.success.value,
            "🔴 S2: un fallo nunca habilita el resto de la app.",
        )
    }

    @Test
    fun `un error de servidor desconocido se muestra tal cual`() {
        val repository = FakeAuthRepository().apply {
            changeResult = Result.failure(Exception("Service Unavailable"))
        }
        val viewModel = viewModel(repository = repository)

        fill(viewModel)
        viewModel.submit()

        assertEquals(
            "Service Unavailable",
            viewModel.uiState.value.error,
            "🔴 S2: lo que el cliente no sabe traducir se muestra sin inventar nada.",
        )
    }

    @Test
    fun `la nueva igual a la provisoria no gasta una request`() {
        val repository = FakeAuthRepository()
        val viewModel = viewModel(repository = repository)

        viewModel.onCurrentPasswordChange(CURRENT)
        viewModel.onNewPasswordChange(CURRENT)
        viewModel.onRepeatPasswordChange(CURRENT)
        viewModel.submit()

        assertEquals(
            CHANGE_PASSWORD_NEW_SAME_AS_CURRENT,
            viewModel.uiState.value.error,
            "🔴 S2: repetir la provisoria no es elegir una contraseña propia.",
        )
        assertEquals(
            emptyList(),
            repository.changeCalls,
            "🔴 S2: una validación de cliente no gasta una request.",
        )
    }

    @Test
    fun `la nueva corta no pasa`() {
        val viewModel = viewModel(repository = FakeAuthRepository())

        viewModel.onCurrentPasswordChange(CURRENT)
        viewModel.onNewPasswordChange("corta1")
        viewModel.onRepeatPasswordChange("corta1")
        viewModel.submit()

        assertEquals(
            CHANGE_PASSWORD_NEW_TOO_SHORT,
            viewModel.uiState.value.error,
            "🔴 S2: el mínimo son 8 caracteres y el cliente lo dice sin pedir permiso.",
        )
    }

    @Test
    fun `la nueva sin número no pasa`() {
        val viewModel = viewModel(repository = FakeAuthRepository())

        viewModel.onCurrentPasswordChange(CURRENT)
        viewModel.onNewPasswordChange("sinnumeros")
        viewModel.onRepeatPasswordChange("sinnumeros")
        viewModel.submit()

        assertEquals(
            CHANGE_PASSWORD_NEW_NEEDS_DIGIT,
            viewModel.uiState.value.error,
            "🔴 S2: la política del back exige un número; el cliente no deja que rebote.",
        )
    }

    @Test
    fun `la nueva sin letra no pasa`() {
        val viewModel = viewModel(repository = FakeAuthRepository())

        viewModel.onCurrentPasswordChange(CURRENT)
        viewModel.onNewPasswordChange("12345678")
        viewModel.onRepeatPasswordChange("12345678")
        viewModel.submit()

        assertEquals(
            CHANGE_PASSWORD_NEW_NEEDS_LETTER,
            viewModel.uiState.value.error,
            "🔴 S2: la política del back exige una letra; el cliente no deja que rebote.",
        )
    }

    @Test
    fun `la repetición distinta no pasa`() {
        val repository = FakeAuthRepository()
        val viewModel = viewModel(repository = repository)

        viewModel.onCurrentPasswordChange(CURRENT)
        viewModel.onNewPasswordChange(NEW)
        viewModel.onRepeatPasswordChange("elegida457")
        viewModel.submit()

        assertEquals(
            CHANGE_PASSWORD_REPEAT_MISMATCH,
            viewModel.uiState.value.error,
            "🔴 S2: el typo se detecta acá, no al siguiente login.",
        )
        assertEquals(
            emptyList(),
            repository.changeCalls,
            "🔴 S2: con las dos contraseñas distintas no se llama al servidor.",
        )
    }

    @Test
    fun `la provisoria vacía no pasa`() {
        val repository = FakeAuthRepository()
        val viewModel = viewModel(repository = repository)

        viewModel.onNewPasswordChange(NEW)
        viewModel.onRepeatPasswordChange(NEW)
        viewModel.submit()

        assertEquals(
            CHANGE_PASSWORD_CURRENT_MISSING,
            viewModel.uiState.value.error,
            "🔴 S2: sin la provisoria no hay forma de autorizar el cambio.",
        )
        assertEquals(
            emptyList(),
            repository.changeCalls,
            "🔴 S2: un campo vacío no gasta una request.",
        )
    }

    @Test
    fun `escribir de nuevo borra el error anterior`() {
        val viewModel = viewModel(repository = FakeAuthRepository())

        viewModel.onCurrentPasswordChange(CURRENT)
        viewModel.onNewPasswordChange(NEW)
        viewModel.onRepeatPasswordChange("otra-cosa")
        viewModel.submit()
        assertNotNull(viewModel.uiState.value.error, "precondición: hay un error en pantalla.")

        viewModel.onRepeatPasswordChange(NEW)

        assertNull(
            viewModel.uiState.value.error,
            "🔴 S2: el error se va cuando la persona corrige, no queda pegado.",
        )
    }

    @Test
    fun `una sesión sin el flag arranca sin bloqueo`() {
        val session = SessionManager()

        session.setSession(token = oldToken, userName = "Ana", role = "user")

        assertFalse(
            session.mustChangePassword,
            "PRESERVE S2: el parámetro nuevo tiene default `false`; una sesión de siempre " +
                "no puede quedar encerrada en la pantalla de cambio.",
        )
    }

    private fun fill(viewModel: ChangePasswordViewModel) {
        viewModel.onCurrentPasswordChange(CURRENT)
        viewModel.onNewPasswordChange(NEW)
        viewModel.onRepeatPasswordChange(NEW)
    }

    private fun session(): SessionManager = SessionManager().apply {
        setSession(
            token = oldToken,
            userName = "Marta",
            role = "user",
            mustChangePassword = true,
        )
    }

    private fun viewModel(
        repository: FakeAuthRepository,
        session: SessionManager = session(),
    ): ChangePasswordViewModel = ChangePasswordViewModel(
        authRepository = repository,
        sessionManager = session,
        dispatcher = UnconfinedTestDispatcher(),
    ).also { viewModels += it }

    /**
     * Doble del repositorio: registra los pares que se pidieron cambiar y devuelve lo que
     * el test diga. El éxito por defecto es el del back real: token nuevo y el usuario con
     * `mustChangePassword = false`.
     */
    private class FakeAuthRepository : IAuthRepository {
        var changeResult: Result<AuthResponse> = Result.success(
            AuthResponse(access_token = "token-nuevo", user = CHANGED_USER),
        )
        val changeCalls = mutableListOf<Pair<String, String>>()

        override val isLoggedIn: Boolean = true
        override val token: String? = "token-provisorio"
        override val currentUser: User? = USER

        override suspend fun login(email: String, password: String): Result<AuthResponse> =
            Result.failure(IllegalStateException("no usado"))

        override suspend fun register(
            email: String,
            password: String,
            firstName: String,
            lastName: String,
        ): Result<AuthResponse> = Result.failure(IllegalStateException("no usado"))

        override suspend fun getProfile(): Result<User> = Result.success(USER)

        override suspend fun changePassword(
            currentPassword: String,
            newPassword: String,
        ): Result<AuthResponse> {
            changeCalls += currentPassword to newPassword
            return changeResult
        }

        override fun logout() = Unit
    }
}
