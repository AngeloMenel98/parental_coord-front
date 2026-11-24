package com.parental.shared.feature.auth.ui

import com.parental.shared.core.error.ApiResult
import com.parental.shared.feature.auth.domain.model.AuthResponse
import com.parental.shared.feature.auth.domain.model.User
import com.parental.shared.feature.auth.domain.repository.IAuthRepository
import com.parental.shared.feature.bonds.data.remote.BondsApi
import com.parental.shared.feature.bonds.domain.model.Vinculo
import com.parental.shared.feature.bonds.domain.model.VinculoMember
import com.parental.shared.feature.bonds.domain.model.VinculoMemberUser
import com.parental.shared.feature.session.data.SessionManager
import com.parental.shared.feature.thirdparty.data.remote.ThirdPartiesApi
import com.parental.shared.feature.thirdparty.domain.model.ThirdPartyGrant
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Lo que el login tiene que entregarle al routing.
 *
 * El flag `mustChangePassword` viaja en la respuesta del servidor y el VM lo escribe UN solo
 * dueño (`SessionManager.setSession`, integración del PR #9), así que lo que este VM debe
 * garantizar es que no lo pierda ni lo reinterprete en el camino: si se cayera acá, la app
 * abriría el Inicio de un usuario que todavía tiene una contraseña provisoria.
 *
 * La derivación del vínculo que develop hacía en `App.kt` (`onLoginSuccess` →
 * [SessionManager.deriveBondWithRetry]) vive acá desde la integración con el refactor
 * token-free: mismo comportamiento —se salta con el flag encendido (R2-001), lee el rol del
 * miembro y, si el back le redactó `members` al tercero (S5), consulta el propio grant—.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
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
        val UNUSED_CLIENT = HttpClient { }
    }

    @BeforeTest
    fun setUp() {
        // `viewModelScope` corre sobre Main: sin esto el launch del login no existe.
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        viewModels.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun `el flag de primer ingreso llega intacto al exito del login`() {
        val response = AuthResponse(access_token = "t-1", user = TEMPORARY_USER)
        val repository = FakeAuthRepository(result = Result.success(response))
        val bondsApi = FakeBondsApi()
        val session = SessionManager()
        val viewModel = viewModel(repository, session, bondsApi)

        viewModel.onEmailChange(TEMPORARY_USER.email)
        viewModel.onPasswordChange("provisoria123")
        viewModel.login()

        assertTrue(
            session.mustChangePassword,
            "🔴 S2: si el flag se pierde acá, la app abre el Inicio con una contraseña provisoria.",
        )
        assertTrue(
            session.isLoggedIn,
            "🔴 S2: la sesión misma es del token que el servidor emitió.",
        )
        assertEquals(
            "t-1",
            session.token,
            "🔴 S2: el token que viaja es el que el servidor emitió.",
        )
        assertNull(
            session.bondRole,
            "🔴 R3-001: con una contraseña provisoria el back responde 403 en `GET /bonds`; " +
                "el rol sólo puede derivarse al salir del cambio obligatorio.",
        )
        assertEquals(
            0,
            bondsApi.calls,
            "🔴 R2-001: la derivación SABE que la ruta va a fallar; no la pide.",
        )
        assertFalse(
            session.state.value.loginPending,
            "🔴 flujo de main: el login completo desbloquea la navegación aunque haya que cambiar la contraseña.",
        )
        assertNull(
            viewModel.uiState.value.error,
            "🔴 S2: un login exitoso no deja error en pantalla.",
        )
    }

    @Test
    fun `un usuario comun loguea sin bloqueo`() {
        val response = AuthResponse(
            access_token = "t-2",
            user = TEMPORARY_USER.copy(mustChangePassword = false),
        )
        val repository = FakeAuthRepository(result = Result.success(response))
        val bondsApi = FakeBondsApi(
            ApiResult.Success(
                listOf(
                    Vinculo(
                        id = "bond-1",
                        members = listOf(
                            VinculoMember(
                                id = "m-1",
                                role = "progenitor",
                                user = VinculoMemberUser(id = "user-1"),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val session = SessionManager()
        val viewModel = viewModel(repository, session, bondsApi)

        viewModel.onEmailChange(TEMPORARY_USER.email)
        viewModel.onPasswordChange("elegida456")
        viewModel.login()

        assertFalse(
            session.mustChangePassword,
            "🔴 S2: quien no tiene contraseña provisoria no ve la pantalla de cambio.",
        )
        assertEquals(
            "bond-1",
            session.bondId,
            "D2/F7: el login deja el vínculo activo en la sesión.",
        )
        assertEquals(
            "progenitor",
            session.bondRole,
            "D2/F7: el rol DENTRO del vínculo se deriva del miembro cuyo user.id es el usuario actual.",
        )
        assertEquals(
            1,
            bondsApi.calls,
            "🔴 S2: sin flag hay derivación — un intento exitoso no usa el reintento.",
        )
        assertNull(
            viewModel.uiState.value.error,
            "🔴 S2: un login exitoso no deja error en pantalla.",
        )
    }

    @Test
    fun `al tercero con members redactados lo identifica el propio grant (S5)`() {
        val response = AuthResponse(
            access_token = "t-3",
            user = TEMPORARY_USER.copy(mustChangePassword = false),
        )
        val repository = FakeAuthRepository(result = Result.success(response))
        // S5: la fila del tercero NO viaja en `GET /bonds`; el grant propio sí lo identifica.
        val bondsApi = FakeBondsApi(
            ApiResult.Success(
                listOf(Vinculo(id = "bond-1", members = emptyList())),
            ),
        )
        val thirdPartiesApi = FakeThirdPartiesApi(
            ApiResult.Success(ThirdPartyGrant(bondId = "bond-1", userId = "user-1")),
        )
        val session = SessionManager()
        val viewModel = viewModel(repository, session, bondsApi, thirdPartiesApi)

        viewModel.onEmailChange(TEMPORARY_USER.email)
        viewModel.onPasswordChange("elegida456")
        viewModel.login()

        assertEquals(
            "third_party",
            session.bondRole,
            "🔴 S5: sin su fila en `members`, el tercero se identifica por el grant propio y " +
                "no queda en el Home del progenitor.",
        )
    }

    @Test
    fun `un grant sin vigencia (404) no inventa el rol de tercero`() {
        val response = AuthResponse(
            access_token = "t-4",
            user = TEMPORARY_USER.copy(mustChangePassword = false),
        )
        val repository = FakeAuthRepository(result = Result.success(response))
        val bondsApi = FakeBondsApi(
            ApiResult.Success(
                listOf(Vinculo(id = "bond-1", members = emptyList())),
            ),
        )
        val thirdPartiesApi = FakeThirdPartiesApi(ApiResult.Error(404, "Not found"))
        val session = SessionManager()
        val viewModel = viewModel(repository, session, bondsApi, thirdPartiesApi)

        viewModel.onEmailChange(TEMPORARY_USER.email)
        viewModel.onPasswordChange("elegida456")
        viewModel.login()

        assertNull(
            session.bondRole,
            "🔴 S5: 404 = no tiene grant → la degradación segura es el Home de siempre.",
        )
        assertFalse(
            session.mustChangePassword,
            "precondición: el flag sigue apagado.",
        )
    }

    private fun viewModel(
        repository: FakeAuthRepository,
        session: SessionManager = SessionManager(),
        bondsApi: FakeBondsApi = FakeBondsApi(),
        thirdPartiesApi: FakeThirdPartiesApi = FakeThirdPartiesApi(ApiResult.Error(404, "no usado")),
    ): LoginViewModel =
        LoginViewModel(
            authRepository = repository,
            sessionManager = session,
            bondsApi = bondsApi,
            thirdPartiesApi = thirdPartiesApi,
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

    /** Doble de `BondsApi`: sin red, cuenta las llamadas para afirmar R2-001. */
    private class FakeBondsApi(
        var result: ApiResult<List<Vinculo>> = ApiResult.Success(emptyList()),
    ) : BondsApi(UNUSED_CLIENT) {
        var calls = 0

        override suspend fun getMyBonds(): ApiResult<List<Vinculo>> {
            calls++
            return result
        }
    }

    /** Doble del grant propio: 200 = tercero, 403 = tercero con corte, 404 = no es tercero. */
    private class FakeThirdPartiesApi(
        private val grant: ApiResult<ThirdPartyGrant>,
    ) : ThirdPartiesApi(UNUSED_CLIENT, "http://unused") {
        var calls = 0

        override suspend fun getMyGrant(token: String): ApiResult<ThirdPartyGrant> {
            calls++
            return grant
        }
    }
}
