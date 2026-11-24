package com.parental.shared.feature.auth.ui

import com.parental.shared.feature.auth.domain.model.AuthResponse
import com.parental.shared.feature.auth.domain.model.User
import com.parental.shared.feature.auth.domain.repository.IAuthRepository
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
 * Lo que el login tiene que entregarle al routing.
 *
 * El flag `mustChangePassword` viaja en la respuesta del servidor y lo escribe UN solo dueño
 * (`App`, en `SessionManager.setSession`), así que lo que este VM debe garantizar es que no
 * lo pierda ni lo reinterprete en el camino: si se cayera acá, la app abriría el Inicio de
 * un usuario que todavía tiene una contraseña provisoria.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val viewModels = mutableListOf<LoginViewModel>()

    private companion object {
        val TEMPORARY_USER = User(
            id = "user-1",
            email = "tercero@example.com",
            systemRole = "user",
            firstName = null,
            lastName = null,
            mustChangePassword = true,
        )
    }

    @AfterTest
    fun tearDown() {
        viewModels.forEach { it.destroy() }
        viewModels.clear()
    }

    @Test
    fun `el flag de primer ingreso llega intacto al exito del login`() {
        val response = AuthResponse(access_token = "t-1", user = TEMPORARY_USER)
        val repository = FakeAuthRepository(result = Result.success(response))
        val viewModel = viewModel(repository)

        viewModel.onEmailChange(TEMPORARY_USER.email)
        viewModel.onPasswordChange("provisoria123")
        viewModel.login()

        val emitted = assertNotNull(
            viewModel.loginSuccess.value,
            "🔴 S2: un login exitoso tiene que anunciarse para que la sesión se escriba.",
        )
        assertTrue(
            emitted.user.mustChangePassword,
            "🔴 S2: si el flag se pierde acá, la app abre el Inicio con una contraseña provisoria.",
        )
        assertEquals(
            "t-1",
            emitted.access_token,
            "🔴 S2: el token que viaja es el que el servidor emitió.",
        )
    }

    @Test
    fun `un usuario comun loguea sin bloqueo`() {
        val response = AuthResponse(
            access_token = "t-2",
            user = TEMPORARY_USER.copy(mustChangePassword = false),
        )
        val repository = FakeAuthRepository(result = Result.success(response))
        val viewModel = viewModel(repository)

        viewModel.onEmailChange(TEMPORARY_USER.email)
        viewModel.onPasswordChange("elegida456")
        viewModel.login()

        val emitted = assertNotNull(
            viewModel.loginSuccess.value,
            "precondición: el login común sigue funcionando.",
        )
        assertFalse(
            emitted.user.mustChangePassword,
            "🔴 S2: quien no tiene contraseña provisoria no ve la pantalla de cambio.",
        )
        assertNull(
            viewModel.uiState.value.error,
            "🔴 S2: un login exitoso no deja error en pantalla.",
        )
    }

    private fun viewModel(repository: FakeAuthRepository): LoginViewModel =
        LoginViewModel(
            authRepository = repository,
            dispatcher = UnconfinedTestDispatcher(),
        ).also { viewModels += it }

    private class FakeAuthRepository(
        private val result: Result<AuthResponse>,
    ) : IAuthRepository {
        override val isLoggedIn: Boolean = false
        override val token: String? = null
        override val currentUser: User? = null

        override suspend fun login(email: String, password: String): Result<AuthResponse> = result

        override suspend fun register(
            email: String,
            password: String,
            firstName: String,
            lastName: String,
        ): Result<AuthResponse> = Result.failure(IllegalStateException("no usado"))

        override suspend fun getProfile(): Result<User> =
            Result.failure(IllegalStateException("no usado"))

        override suspend fun changePassword(
            currentPassword: String,
            newPassword: String,
        ): Result<AuthResponse> = Result.failure(IllegalStateException("no usado"))

        override fun logout() = Unit
    }
}
