package com.parental.shared.feature.activities.ui.detail

import com.parental.shared.feature.activities.domain.model.ActividadDetalle
import com.parental.shared.feature.bonds.domain.model.VinculoChild
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `activity-detail-bitacora` T1 · S3/S6 — las reglas del bloque "Estado" y de la bitácora.
 *
 * Va contra las funciones puras y no contra el composable: el repo no tiene infraestructura
 * de UI (`createComposeRule`), y lo que hay que defender es la REGLA —qué etapa se alcanzó,
 * qué paso falta y qué evento existe—, no su pintado. Mismo criterio que [DetailPairsTest].
 *
 * 🔴 S6 · NINGÚN HITO SE INVENTA. Cada `assertFalse` de "no se inventa" defiende la misma
 * cosa: si el DTO no trae la marca (`confirmedAt`, `completedAt`, `isPast`), el bloque no
 * puede afirmar que ese paso ocurrió.
 */
class BitacoraDerivationTest {

    private fun detalle(
        status: String = "assigned",
        assignedConfirmed: Boolean = false,
        confirmedAt: String? = null,
        completedAt: String? = null,
        scheduledStart: String? = "2026-08-05T16:30:00Z",
        deadline: String? = null,
        isPast: Boolean = false,
        declinedReason: String? = null,
        childrenIds: List<String> = emptyList(),
        createdBy: String = "u-carlos",
        createdAt: String = "2026-08-01T10:00:00Z",
        assignedTo: String? = "u-maria",
    ) = ActividadDetalle(
        id = "a1",
        title = "Clases de natación",
        type = "event",
        status = status,
        categoryId = "c1",
        criticality = "medium",
        createdBy = createdBy,
        createdAt = createdAt,
        scheduledStart = scheduledStart,
        deadline = deadline,
        assignedTo = assignedTo,
        assignedConfirmed = assignedConfirmed,
        confirmedAt = confirmedAt,
        completedAt = completedAt,
        isPast = isPast,
        declinedReason = declinedReason,
        childrenIds = childrenIds,
    )

    private val ana = VinculoChild(id = "c1", firstName = "Ana", lastName = "García")
    private val luis = VinculoChild(id = "c2", firstName = "Luis", lastName = "García")
    private val zone = TimeZone.UTC

    private fun nodos(actividad: ActividadDetalle) = stageRail(actividad).nodes

    private fun etapaActual(actividad: ActividadDetalle) =
        nodos(actividad).single { it.current }

    // ── Riel de etapas ────────────────────────────────────────────────

    @Test
    fun `S3 el riel siempre tiene las cinco etapas en orden`() {
        val labels = nodos(detalle()).map { it.stage.label }
        assertEquals(
            listOf("Creada", "Asignada", "Asiste", "En progreso", "Finalizada"),
            labels,
        )
    }

    @Test
    fun `S3 created deja la etapa actual en Creada`() {
        val stages = nodos(detalle(status = "created", assignedTo = null))
        assertEquals("Creada", etapaActual(detalle(status = "created", assignedTo = null)).stage.label)
        assertFalse(stages.single { it.stage.label == "Asignada" }.reached)
    }

    @Test
    fun `S3 assigned sin confirmar deja Asiste sin alcanzar`() {
        val stages = nodos(detalle(status = "assigned"))
        assertTrue(stages.single { it.stage.label == "Asignada" }.reached)
        assertFalse(stages.single { it.stage.label == "Asiste" }.reached)
    }

    @Test
    fun `S3 assignedConfirmed alcanza la etapa Asiste`() {
        val stages = nodos(detalle(status = "assigned", assignedConfirmed = true))
        assertTrue(stages.single { it.stage.label == "Asiste" }.reached)
        assertEquals("Asiste", etapaActual(detalle(status = "assigned", assignedConfirmed = true)).stage.label)
    }

    @Test
    fun `S3 in_progress confirmado deja En progreso como etapa actual`() {
        val actividad = detalle(status = "in_progress", assignedConfirmed = true)
        assertEquals("En progreso", etapaActual(actividad).stage.label)
        assertFalse(nodos(actividad).single { it.stage.label == "Finalizada" }.reached)
    }

    @Test
    fun `S6 in_progress sin confirmacion no afirma que la asignacion se confirmo`() {
        // A4: `in_progress` sin `confirmedAt` es un estado que el servidor puede mandar, y el
        // riel no puede pintar "Asiste" como alcanzada: sería inventar un hito. La etapa
        // actual sigue siendo la del ESTADO —el hueco queda visible en "Asiste"— en vez
        // de retroceder el riel entero a "Asignada".
        val actividad = detalle(status = "in_progress", assignedConfirmed = false)
        val stages = nodos(actividad)
        assertFalse(stages.single { it.stage.label == "Asiste" }.reached)
        assertEquals("En progreso", etapaActual(actividad).stage.label)
    }

    @Test
    fun `S2 assisting se ubica en Asiste y no en Creada`() {
        // PRD §2: "Asiste" es su propio paso, y el servidor sólo pasa a `assisting` cuando el
        // asignado confirma. Antes `ASSISTING` saltaba a "En progreso" y se comía el paso 3.
        val stages = nodos(detalle(status = "assisting", assignedConfirmed = true))
        assertEquals("Asiste", stages.single { it.current }.stage.label)
    }

    @Test
    fun `S6 done sin marca de confirmacion no alcanza Asiste`() {
        // Cerrar no prueba que se haya confirmado: el hueco tiene que quedar a la vista aunque
        // la actividad esté terminada.
        val stages = nodos(detalle(status = "done", assignedConfirmed = false, confirmedAt = null))
        assertFalse(stages.single { it.stage.label == "Asiste" }.reached)
        assertTrue(stages.single { it.stage.label == "Finalizada" }.reached)
        assertEquals("Finalizada", stages.single { it.current }.stage.label)
    }

    // La invariante "a lo sumo una etapa actual / el terminal la reemplaza" vive ahora en
    // `BitacoraStagesMatrixTest`, junto con la matriz completa de estados terminales.

    @Test
    fun `S3 done alcanza Finalizada`() {
        val actividad = detalle(status = "done", assignedConfirmed = true, completedAt = "2026-08-05T18:00:00Z")
        val stages = nodos(actividad)
        assertTrue(stages.all { it.reached })
        assertEquals("Finalizada", etapaActual(actividad).stage.label)
    }

    @Test
    fun `S3 con completedAt aunque el estado venga atrasado la etapa es Finalizada`() {
        // El estado es la fuente principal, pero un `completedAt` presente es prueba de que
        // el cierre ocurrió aunque el estado venga atrasado por un desfase de sincronización.
        val actividad = detalle(status = "in_progress", assignedConfirmed = true, completedAt = "2026-08-05T18:00:00Z")
        assertEquals("Finalizada", etapaActual(actividad).stage.label)
    }

    // ── "Qué falta" ──────────────────────────────────────────────────

    @Test
    fun `S6 el turno cuenta como hecho cuando el servidor dice isPast`() {
        val paso = detailChecklist(detalle(isPast = true), attachmentCount = 0, zone = zone)
            .single { it.label == "Turno" }
        assertTrue(paso.done)
    }

    @Test
    fun `S6 con la fecha futura el turno queda pendiente`() {
        val paso = detailChecklist(detalle(isPast = false), attachmentCount = 0, zone = zone)
            .single { it.label == "Turno" }
        assertFalse(paso.done)
    }

    @Test
    fun `S6 sin fecha de inicio no hay paso Turno`() {
        val labels = detailChecklist(detalle(scheduledStart = null), attachmentCount = 0, zone = zone)
            .map { it.label }
        assertEquals(listOf("Comprobantes"), labels)
    }

    @Test
    fun `S6 sin adjuntos el paso Comprobantes queda pendiente con su limite`() {
        val paso = detailChecklist(
            detalle(deadline = "2026-08-06T00:00:00Z"),
            attachmentCount = 0,
            zone = zone,
        ).single { it.label == "Comprobantes" }
        assertFalse(paso.done)
        // El año NO se fija acá: [formatDeadline] aplica la regla del proyecto de omitirlo
        // cuando coincide con el año en curso, así que un literal con año volvería este test
        // dependiente del reloj de la máquina. Se fija el formato y el día, que es la regla.
        assertTrue(paso.detail.startsWith("sin cargar · límite "), "fue: ${paso.detail}")
        assertTrue(paso.detail.contains("6 ago"), "fue: ${paso.detail}")
    }

    @Test
    fun `S6 con un adjunto el paso Comprobantes queda hecho`() {
        val paso = detailChecklist(detalle(), attachmentCount = 1, zone = zone)
            .single { it.label == "Comprobantes" }
        assertTrue(paso.done)
        assertEquals("1 archivo", paso.detail)
    }

    @Test
    fun `S6 con varios adjuntos el detalle pluraliza`() {
        val paso = detailChecklist(detalle(), attachmentCount = 3, zone = zone)
            .single { it.label == "Comprobantes" }
        assertEquals("3 archivos", paso.detail)
    }

    @Test
    fun `S6 el medidor cuenta los pasos hechos sobre los pasos visibles`() {
        val pasos = detailChecklist(detalle(isPast = true), attachmentCount = 0, zone = zone)
        assertEquals(1, pasos.count { it.done })
        assertEquals(2, pasos.size)
    }

    // ── Bitácora ─────────────────────────────────────────────────────

    @Test
    fun `S3 la bitacora arranca con la creacion`() {
        val evento = bitacoraEvents(detalle(), memberNames = emptyMap(), children = emptyList(), zone = zone)
            .single()
        assertEquals(BitacoraKind.Created, evento.kind)
        assertEquals("Actividad creada", evento.title)
    }

    @Test
    fun `S3 la creacion lleva fecha y hora del servidor`() {
        val evento = bitacoraEvents(detalle(), emptyMap(), emptyList(), zone).single()
        assertEquals(1, evento.date?.dayOfMonth)
        assertEquals(8, evento.date?.monthNumber)
        assertEquals(2026, evento.date?.year)
        assertEquals("10:00", evento.time)
    }

    @Test
    fun `S3 el autor se resuelve contra memberNames`() {
        val evento = bitacoraEvents(
            detalle(),
            mapOf("u-carlos" to "Carlos P."),
            emptyList(),
            zone,
        ).single()
        assertEquals("Carlos P.", evento.author)
    }

    @Test
    fun `S6 un autor desconocido no se pinta como uuid`() {
        val evento = bitacoraEvents(detalle(), emptyMap(), emptyList(), zone).single()
        assertNull(evento.author)
    }

    @Test
    fun `S5 cada evento lleva el nombre del hijo al que refiere`() {
        val evento = bitacoraEvents(
            detalle(childrenIds = listOf("c1", "c2")),
            emptyMap(),
            listOf(ana, luis),
            zone,
        ).single()
        assertEquals("Ana García, Luis García", evento.context)
    }

    @Test
    fun `S5 sin hijos resueltos el contexto queda vacio`() {
        val evento = bitacoraEvents(detalle(childrenIds = listOf("c1")), emptyMap(), emptyList(), zone).single()
        assertNull(evento.context)
    }

    @Test
    fun `S3 la confirmacion entra cuando hay confirmedAt`() {
        val eventos = bitacoraEvents(
            detalle(status = "in_progress", assignedConfirmed = true, confirmedAt = "2026-08-02T11:02:00Z"),
            mapOf("u-maria" to "María G."),
            emptyList(),
            zone,
        )
        assertEquals(listOf(BitacoraKind.Created, BitacoraKind.Confirmed), eventos.map { it.kind })
        assertEquals("María G.", eventos[1].author)
    }

    @Test
    fun `S6 sin confirmedAt no se inventa la confirmacion`() {
        val eventos = bitacoraEvents(
            detalle(status = "in_progress", assignedConfirmed = true, confirmedAt = null),
            emptyMap(),
            emptyList(),
            zone,
        )
        assertTrue(eventos.none { it.kind == BitacoraKind.Confirmed })
    }

    @Test
    fun `S3 la finalizacion entra con completedAt`() {
        val eventos = bitacoraEvents(
            detalle(status = "done", completedAt = "2026-08-05T18:20:00Z"),
            emptyMap(),
            emptyList(),
            zone,
        )
        assertEquals(BitacoraKind.Completed, eventos.last().kind)
        assertEquals("Actividad finalizada", eventos.last().title)
    }

    @Test
    fun `S3 el motivo de no asistir entra como evento sin hora`() {
        val evento = bitacoraEvents(
            detalle(status = "not_assisting", declinedReason = "Viaje familiar"),
            emptyMap(),
            emptyList(),
            zone,
        ).last()
        assertEquals(BitacoraKind.Declined, evento.kind)
        assertEquals("Viaje familiar", evento.context)
        assertNull(evento.time)
    }

    @Test
    fun `S3 los eventos salen en orden de ciclo de vida`() {
        val eventos = bitacoraEvents(
            detalle(
                status = "done",
                assignedConfirmed = true,
                confirmedAt = "2026-08-02T11:02:00Z",
                completedAt = "2026-08-05T18:20:00Z",
            ),
            emptyMap(),
            emptyList(),
            zone,
        )
        assertEquals(
            listOf(BitacoraKind.Created, BitacoraKind.Confirmed, BitacoraKind.Completed),
            eventos.map { it.kind },
        )
    }

    @Test
    fun `S6 createdAt ilegible no inventa el evento de creacion`() {
        assertTrue(bitacoraEvents(detalle(createdAt = "basura"), emptyMap(), emptyList(), zone).isEmpty())
    }

    @Test
    fun `S6 completedAt ilegible no inventa la finalizacion`() {
        val eventos = bitacoraEvents(
            detalle(status = "done", completedAt = "5/8/26"),
            emptyMap(),
            emptyList(),
            zone,
        )
        assertTrue(eventos.none { it.kind == BitacoraKind.Completed })
    }

    @Test
    fun `S6 un motivo en blanco no genera evento`() {
        val eventos = bitacoraEvents(
            detalle(status = "not_assisting", declinedReason = "   "),
            emptyMap(),
            emptyList(),
            zone,
        )
        assertTrue(eventos.none { it.kind == BitacoraKind.Declined })
    }

    @Test
    fun `S6 sin deadline el paso Comprobantes no inventa limite`() {
        val paso = detailChecklist(detalle(deadline = null), attachmentCount = 0, zone = zone)
            .single { it.label == "Comprobantes" }
        assertEquals("sin cargar", paso.detail)
        assertFalse(paso.done)
    }

    @Test
    fun `S6 un estado terminal da el turno por hecho aunque isPast sea falso`() {
        val paso = detailChecklist(detalle(status = "completed", isPast = false), 0, zone = zone)
            .single { it.label == "Turno" }
        assertTrue(paso.done)
    }
}
