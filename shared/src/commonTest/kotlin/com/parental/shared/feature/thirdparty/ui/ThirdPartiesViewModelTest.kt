package com.parental.shared.feature.thirdparty.ui

import com.parental.shared.core.error.ApiResult
import com.parental.shared.feature.thirdparty.data.remote.ThirdPartiesApi
import com.parental.shared.feature.thirdparty.domain.model.CreateThirdPartyAccessRequest
import com.parental.shared.feature.thirdparty.domain.model.ThirdPartyAccess
import com.parental.shared.feature.thirdparty.domain.model.ThirdPartyGrant
import com.parental.shared.feature.thirdparty.domain.model.UpdateThirdPartyAccessRequest
import com.parental.shared.feature.thirdparty.domain.model.relationshipLabel
import com.parental.shared.feature.thirdparty.domain.model.thirdPartyStatusLabel
import io.ktor.client.HttpClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * La consola del progenitor que otorga accesos (T15, artboard
 * `design/terceros-ui/04-vigencia-a-la-vista.html`).
 *
 * Una prueba por regla observable, con la API sustituida y el día fijo —el mismo patrón que
 * `ThirdPartyViewModelTest`—: lo que se afirma es `state`, nunca un campo interno. El día
 * se inyecta porque "3 meses" y "quedan 26 días" sólo son verificables contra una fecha
 * conocida.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ThirdPartiesViewModelTest {

    private val token = "t-1"
    private val bondId = "bond-1"
    private val viewModels = mutableListOf<ThirdPartiesViewModel>()

    private companion object {
        val UNUSED_CLIENT = HttpClient { }
        val TODAY: LocalDate = LocalDate(2026, 11, 3)
        const val UNAUTHORIZED_EMAIL =
            "Ese email todavía no tiene una cuenta en la app. El alta con credenciales está diferida (D4)."
    }

    @AfterTest
    fun tearDown() {
        viewModels.forEach { it.destroy() }
        viewModels.clear()
    }

    // ── Lista ───────────────────────────────────────────────────────────

    @Test
    fun `la lista traduce los cuatro estados y la vigencia`() {
        val api = FakeThirdPartiesApi().apply {
            grants += grant("g-active", "Marta Gómez", "FAMILY", "2026-12-12", "active")
            grants += grant("g-expired", "Dr. Pérez", "MEDICAL", "2026-11-02", "expired")
            grants += grant("g-revoked", "Sofía Ruiz", "CAREGIVER", "2026-12-30", "revoked")
            grants += grant("g-suspended", "Julio Díaz", "EDUCATIONAL", "2026-12-15", "suspended")
        }
        val viewModel = viewModel(api)

        viewModel.load()

        val state = viewModel.state.value
        assertEquals(4, state.items.size, "🔴 T15: se listan los cuatro accesos que otorgó el dueño.")

        val byId = state.items.associateBy { it.id }
        assertEquals("Activo", byId.getValue("g-active").statusLabel, "🔴 T15: active → Activo.")
        assertEquals("Vencido", byId.getValue("g-expired").statusLabel, "🔴 T15: expired → Vencido.")
        assertEquals("Revocado", byId.getValue("g-revoked").statusLabel, "🔴 T15: revoked → Revocado.")
        assertEquals("Suspendido", byId.getValue("g-suspended").statusLabel, "🔴 T15: suspended → Suspendido.")

        assertEquals(
            listOf("g-active"),
            state.active.map { it.id },
            "🔴 T15: sólo el grant vigente entra en 'Con acceso'.",
        )
        assertEquals(3, state.inactive.size, "🔴 T15: vencidos, revocados y suspendidos van a 'Sin acceso vigente'.")

        val activo = byId.getValue("g-active")
        assertEquals(39, activo.daysLeft, "🔴 T15: del 3 de noviembre al 12 de diciembre quedan 39 días.")
        assertEquals("quedan 39 días", activo.remainingLabel, "🔴 T15: los días se dicen en palabras.")
        assertEquals("12 dic 2026", activo.endDateLabel, "🔴 T15: la fecha se muestra, no se deja en ISO.")
        assertEquals("Familiar", activo.relationshipLabel, "🔴 T15: la relación se muestra en la tarjeta.")
        assertEquals("MG", activo.initials, "🔴 T15: el avatar usa las iniciales del nombre.")

        assertEquals(
            "g-expired",
            state.items.last().id,
            "🔴 T15: dentro de 'sin acceso', el vencimiento más viejo va al final.",
        )
    }

    @Test
    fun `un grant activo con la fecha pasada no se anuncia como vigente`() {
        val api = FakeThirdPartiesApi().apply {
            grants += grant("g-stale", "Marta Gómez", "FAMILY", "2026-10-01", "active")
        }
        val viewModel = viewModel(api)

        viewModel.load()

        val item = viewModel.state.value.items.single()
        assertFalse(
            item.hasAccess,
            "🔴 T15: la vigencia real es la fecha; un grant que el back no barrió no se pinta como activo.",
        )
        assertNull(item.remainingLabel, "🔴 T15: sin vigencia no hay 'quedan N días'.")
        assertNull(item.progress, "🔴 T15: sin vigencia no hay barra de período.")
    }

    @Test
    fun `un fallo de carga se muestra y el reintento recupera la lista`() {
        val api = FakeThirdPartiesApi().apply { listError = ApiResult.Error(500, "boom") }
        val viewModel = viewModel(api)

        viewModel.load()
        assertEquals("boom", viewModel.state.value.loadError, "🔴 T15: el error de carga se muestra con su motivo.")

        api.listError = null
        api.grants += grant("g-active", "Marta Gómez", "FAMILY", "2026-12-12", "active")
        viewModel.load()

        val state = viewModel.state.value
        assertNull(state.loadError, "🔴 T15: reintentar limpia el error.")
        assertEquals(1, state.items.size, "🔴 T15: y la lista aparece.")
        assertEquals(2, api.listCalls, "🔴 T15: el reintento vuelve a pedir la lista.")
    }

    // ── Alta ────────────────────────────────────────────────────────────

    @Test
    fun `el paso 1 exige email y relación antes de hablar de fechas`() {
        val viewModel = viewModel(FakeThirdPartiesApi())
        viewModel.openCreate()

        viewModel.next()
        assertEquals(
            THIRD_PARTY_EMAIL_MISSING,
            viewModel.state.value.wizard?.error,
            "🔴 T15: sin email no se avanza.",
        )
        assertEquals(
            ThirdPartyWizardStep.QUIEN,
            viewModel.state.value.wizard?.step,
            "🔴 T15: el error deja a la persona en el paso 1.",
        )

        viewModel.setEmail("no-es-un-email")
        viewModel.next()
        assertEquals(THIRD_PARTY_EMAIL_INVALID, viewModel.state.value.wizard?.error, "🔴 T15: un email sin forma no pasa.")

        viewModel.setEmail("abuela@example.com")
        viewModel.next()
        assertEquals(
            THIRD_PARTY_RELATIONSHIP_MISSING,
            viewModel.state.value.wizard?.error,
            "🔴 T15: la relación es obligatoria en el alta.",
        )

        viewModel.setRelationship("family")
        viewModel.next()
        assertEquals(
            ThirdPartyWizardStep.HASTA_CUANDO,
            viewModel.state.value.wizard?.step,
            "🔴 T15: con email y relación se pasa al paso 2.",
        )
        assertNull(viewModel.state.value.wizard?.error, "🔴 T15: y el error anterior se limpia.")
    }

    @Test
    fun `el alta exitosa cierra el asistente y entra en la lista`() {
        val api = FakeThirdPartiesApi()
        val viewModel = viewModel(api)
        viewModel.load()

        viewModel.openCreate()
        viewModel.setEmail("abuela@example.com")
        viewModel.setRelationship("family")
        viewModel.next()
        assertEquals(
            "2027-02-03",
            viewModel.state.value.wizard?.accessEnd,
            "🔴 T15: '3 meses' se calcula sobre hoy (3 nov 2026 → 3 feb 2027).",
        )
        viewModel.submit()

        assertEquals(1, api.createCalls, "🔴 T15: otorgar llama al alta del back.")
        assertEquals("2027-02-03", api.lastCreate?.accessEnd, "🔴 T15: viaja la fecha resuelta por el preset.")
        assertEquals("family", api.lastCreate?.relationship, "🔴 T15: viaja el valor del enum del back.")

        val state = viewModel.state.value
        assertNull(state.wizard, "🔴 T15: el asistente se cierra al otorgar.")
        assertFalse(state.isSaving, "🔴 T15: se apaga el flag de guardado.")
        assertNotNull(state.message, "🔴 T15: la persona recibe confirmación.")
        assertEquals(
            listOf("g-new"),
            state.items.map { it.id },
            "🔴 T15: el acceso nuevo aparece en la lista.",
        )
        assertEquals("Activo", state.items.single().statusLabel, "🔴 T15: y aparece como vigente.")
    }

    @Test
    fun `el 422 del email sin cuenta se muestra inline y no borra el formulario`() {
        val api = FakeThirdPartiesApi().apply { createError = ApiResult.Error(422, UNAUTHORIZED_EMAIL) }
        val viewModel = viewModel(api)
        viewModel.load()

        viewModel.openCreate()
        viewModel.setEmail("abuela@example.com")
        viewModel.setRelationship("caregiver")
        viewModel.next()
        viewModel.setNote("Llevarla a la escuela")
        viewModel.submit()

        val state = viewModel.state.value
        val wizard = assertNotNull(state.wizard, "🔴 T15: un error del servidor no cierra el asistente.")
        assertEquals(UNAUTHORIZED_EMAIL, wizard.error, "🔴 T15: se muestra el mensaje del servidor, no uno propio.")
        assertEquals("abuela@example.com", wizard.email, "🔴 T15: el email escrito se conserva.")
        assertEquals("caregiver", wizard.relationship, "🔴 T15: la relación elegida se conserva.")
        assertEquals("2027-02-03", wizard.accessEnd, "🔴 T15: la fecha resuelta se conserva.")
        assertEquals("Llevarla a la escuela", wizard.note, "🔴 T15: la nota escrita se conserva.")
        assertFalse(state.isSaving, "🔴 T15: se puede reintentar.")
        assertTrue(state.items.isEmpty(), "🔴 T15: nada entró en la lista.")
        assertEquals(1, api.createCalls, "🔴 T15: se llamó una vez y no en bucle.")
    }

    @Test
    fun `sin fecha o con una fecha imposible el alta no se envía`() {
        val api = FakeThirdPartiesApi()
        val viewModel = viewModel(api)
        viewModel.load()

        viewModel.openCreate()
        viewModel.setEmail("abuela@example.com")
        viewModel.setRelationship("family")
        viewModel.next()
        viewModel.setDuration(ThirdPartyDuration.CUSTOM)

        viewModel.submit()
        assertEquals(
            THIRD_PARTY_ACCESS_END_MISSING,
            viewModel.state.value.wizard?.error,
            "🔴 T15: accessEnd es obligatorio (D3).",
        )

        viewModel.setAccessEnd("2026-02-30")
        viewModel.submit()
        assertEquals(
            THIRD_PARTY_ACCESS_END_INVALID,
            viewModel.state.value.wizard?.error,
            "🔴 T15: una fecha que no existe no se manda al back.",
        )

        viewModel.setAccessEnd("2026-10-01")
        viewModel.submit()
        assertEquals(
            THIRD_PARTY_ACCESS_END_PAST,
            viewModel.state.value.wizard?.error,
            "🔴 T15: una fecha pasada no se manda al back.",
        )

        assertEquals(0, api.createCalls, "🔴 T15: ninguna de las tres intentonas gastó una request.")
        assertNotNull(viewModel.state.value.wizard, "🔴 T15: el asistente sigue abierto.")
    }

    @Test
    fun `elegir fecha acepta un dia real y lo manda tal cual`() {
        val api = FakeThirdPartiesApi()
        val viewModel = viewModel(api)
        viewModel.openCreate()
        viewModel.setEmail("medico@example.com")
        viewModel.setRelationship("medical")
        viewModel.next()
        viewModel.setAccessEnd("2027-01-31")
        viewModel.submit()

        assertEquals(1, api.createCalls, "🔴 T15: una fecha válida sí se envía.")
        assertEquals("2027-01-31", api.lastCreate?.accessEnd, "🔴 T15: el campo se manda como YYYY-MM-DD.")
        assertNull(viewModel.state.value.wizard, "🔴 T15: y el asistente se cierra.")
    }

    // ── Edición ─────────────────────────────────────────────────────────

    @Test
    fun `editar guarda relación vencimiento y nota`() {
        val api = FakeThirdPartiesApi().apply {
            grants += grant("g-active", "Marta Gómez", "FAMILY", "2026-12-12", "active")
        }
        val viewModel = viewModel(api)
        viewModel.load()

        viewModel.openEdit(viewModel.state.value.items.single())
        assertNotNull(viewModel.state.value.edit, "🔴 T15: la tarjeta vigente abre la edición.")

        viewModel.setEditRelationship("medical")
        viewModel.setEditAccessEnd("2027-01-31")
        viewModel.setEditNote("Sólo retiros")
        viewModel.saveEdit()

        assertEquals(1, api.updateCalls, "🔴 T15: guardar llama al PATCH.")
        assertEquals("2027-01-31", api.lastUpdate?.accessEnd, "🔴 T15: el vencimiento extendido viaja.")
        assertEquals("medical", api.lastUpdate?.relationship, "🔴 T15: la relación editada viaja.")
        assertEquals("Sólo retiros", api.lastUpdate?.note, "🔴 T15: la nota editada viaja.")

        val state = viewModel.state.value
        assertNull(state.edit, "🔴 T15: la hoja se cierra al guardar.")
        assertEquals("2027-01-31", state.items.single().endDateIso, "🔴 T15: la tarjeta refleja la fecha nueva.")
        assertNotNull(state.message, "🔴 T15: y se confirma el cambio.")
    }

    @Test
    fun `un acceso sin vigencia no ofrece edicion`() {
        val api = FakeThirdPartiesApi().apply {
            grants += grant("g-expired", "Dr. Pérez", "MEDICAL", "2026-11-02", "expired")
        }
        val viewModel = viewModel(api)
        viewModel.load()

        viewModel.openEdit(viewModel.state.value.items.single())

        assertNull(
            viewModel.state.value.edit,
            "🔴 T15: el PATCH del back responde 409 sobre un grant no vigente, así que no se ofrece editar.",
        )
        assertEquals(0, api.updateCalls, "🔴 T15: y no se llamó al back.")
    }

    // ── Revocación ──────────────────────────────────────────────────────

    @Test
    fun `revocar pide confirmacion y deja el acceso marcado`() {
        val api = FakeThirdPartiesApi().apply {
            grants += grant("g-active", "Marta Gómez", "FAMILY", "2026-12-12", "active")
        }
        val viewModel = viewModel(api)
        viewModel.load()

        val item = viewModel.state.value.items.single()
        viewModel.askRevoke(item)

        assertEquals(item.id, viewModel.state.value.pendingRevoke?.id, "🔴 T15: revocar abre una confirmación.")
        assertEquals(0, api.revokeCalls, "🔴 T15: pedir NO revoca.")

        viewModel.revoke()

        assertEquals(1, api.revokeCalls, "🔴 T15: confirmar llama al DELETE.")
        val state = viewModel.state.value
        assertNull(state.pendingRevoke, "🔴 T15: el diálogo se cierra.")
        val revoked = state.items.single()
        assertEquals("Revocado", revoked.statusLabel, "🔴 T15: la tarjeta queda marcada como revocada.")
        assertFalse(revoked.hasAccess, "🔴 T15: y deja de contar como acceso vigente.")
        assertTrue(state.active.isEmpty(), "🔴 T15: sale del grupo 'Con acceso'.")
        assertNotNull(state.message, "🔴 T15: se confirma la revocación.")
    }

    @Test
    fun `descartar la confirmacion no revoca nada`() {
        val api = FakeThirdPartiesApi().apply {
            grants += grant("g-active", "Marta Gómez", "FAMILY", "2026-12-12", "active")
        }
        val viewModel = viewModel(api)
        viewModel.load()

        viewModel.askRevoke(viewModel.state.value.items.single())
        viewModel.dismissRevoke()

        assertNull(viewModel.state.value.pendingRevoke, "🔴 T15: el diálogo se puede descartar.")
        assertEquals(0, api.revokeCalls, "🔴 T15: descartar no revoca.")
        assertTrue(viewModel.state.value.items.single().hasAccess, "🔴 T15: el acceso sigue vigente.")
    }

    // ── Preservación de lo existente ────────────────────────────────────

    @Test
    fun `las etiquetas de la vista del terciario no cambian`() {
        assertEquals("Familiar", relationshipLabel("FAMILY"), "🔴 PRESERVE: la salida de FAMILY no cambia.")
        assertEquals("Cuidador", relationshipLabel("CAREGIVER"), "🔴 PRESERVE: la salida de CAREGIVER no cambia.")
        assertEquals("Médico", relationshipLabel("MEDICAL"), "🔴 PRESERVE: la salida de MEDICAL no cambia.")
        assertEquals("Educativo", relationshipLabel("EDUCATIONAL"), "🔴 PRESERVE: la salida de EDUCATIONAL no cambia.")
        assertNull(relationshipLabel(null), "🔴 PRESERVE: sin relación no hay etiqueta.")
        assertEquals("Otra", relationshipLabel("Otra"), "🔴 PRESERVE: un valor desconocido se muestra tal cual.")

        assertEquals("Activo", thirdPartyStatusLabel("active"), "🔴 T15: el estado se traduce.")
        assertEquals("Vencido", thirdPartyStatusLabel("EXPIRED"), "🔴 T15: la traducción no depende del caso.")
        assertNull(thirdPartyStatusLabel("  "), "🔴 T15: sin estado no hay etiqueta.")

        // El grant del terciario (`/third-parties/me`) sigue siendo un tipo distinto y con
        // sus mismos campos: la consola del dueño no lo reemplaza.
        val mine = ThirdPartyGrant(bondId = bondId, userId = "u-1", endDate = "2026-12-12")
        assertEquals("2026-12-12", mine.endDate, "🔴 PRESERVE: `ThirdPartyGrant` no cambia de forma.")
    }

    // ── Fixtures ────────────────────────────────────────────────────────

    private fun viewModel(api: FakeThirdPartiesApi): ThirdPartiesViewModel =
        ThirdPartiesViewModel(
            api = api,
            token = token,
            bondId = bondId,
            dispatcher = UnconfinedTestDispatcher(),
            today = { TODAY },
        ).also { viewModels += it }

    private fun grant(
        id: String,
        name: String,
        relationship: String,
        endDate: String,
        status: String,
    ) = ThirdPartyAccess(
        id = id,
        bondId = bondId,
        userId = "user-$id",
        displayName = name,
        relationship = relationship,
        startDate = "2026-10-01",
        endDate = endDate,
        status = status,
        authorizedBy = "p-1",
        authorizedByName = "Ángelo",
    )

    /**
     * El back sustituto: guarda los grants en memoria y aplica las mismas transiciones que
     * el servicio real (crear agrega, editar pisa los campos enviados, revocar cambia el
     * estado). Así el refresco posterior a cada escritura devuelve la verdad del "servidor"
     * y no la que el test quiere oír.
     */
    private class FakeThirdPartiesApi : ThirdPartiesApi(UNUSED_CLIENT, "http://unused") {
        val grants = mutableListOf<ThirdPartyAccess>()
        var listCalls = 0
        var createCalls = 0
        var updateCalls = 0
        var revokeCalls = 0
        var listError: ApiResult.Error? = null
        var createError: ApiResult.Error? = null
        var updateError: ApiResult.Error? = null
        var revokeError: ApiResult.Error? = null
        var lastCreate: CreateThirdPartyAccessRequest? = null
        var lastUpdate: UpdateThirdPartyAccessRequest? = null

        override suspend fun listForBond(
            token: String,
            bondId: String,
        ): ApiResult<List<ThirdPartyAccess>> {
            listCalls++
            listError?.let { return it }
            return ApiResult.Success(grants.toList())
        }

        override suspend fun create(
            token: String,
            bondId: String,
            request: CreateThirdPartyAccessRequest,
        ): ApiResult<ThirdPartyAccess> {
            createCalls++
            lastCreate = request
            createError?.let { return it }
            val created = ThirdPartyAccess(
                id = "g-new",
                bondId = bondId,
                userId = "user-new",
                displayName = "Marta Gómez",
                relationship = request.relationship,
                startDate = "2026-11-03",
                endDate = request.accessEnd,
                status = "active",
                authorizedBy = "p-1",
                authorizedByName = "Ángelo",
                note = request.note,
            )
            grants += created
            return ApiResult.Success(created)
        }

        override suspend fun update(
            token: String,
            id: String,
            request: UpdateThirdPartyAccessRequest,
        ): ApiResult<ThirdPartyAccess> {
            updateCalls++
            lastUpdate = request
            updateError?.let { return it }
            val current = grants.first { it.id == id }
            val updated = current.copy(
                relationship = request.relationship ?: current.relationship,
                endDate = request.accessEnd ?: current.endDate,
                note = request.note ?: current.note,
            )
            grants[grants.indexOf(current)] = updated
            return ApiResult.Success(updated)
        }

        override suspend fun revoke(token: String, id: String): ApiResult<ThirdPartyAccess> {
            revokeCalls++
            revokeError?.let { return it }
            val current = grants.first { it.id == id }
            val revoked = current.copy(status = "revoked", revokedAt = "2026-11-03T10:00:00.000Z")
            grants[grants.indexOf(current)] = revoked
            return ApiResult.Success(revoked)
        }
    }
}
