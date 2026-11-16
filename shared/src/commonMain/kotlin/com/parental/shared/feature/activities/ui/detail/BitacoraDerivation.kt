package com.parental.shared.feature.activities.ui.detail

import com.parental.shared.feature.activities.domain.model.ActividadDetalle
import com.parental.shared.feature.activities.domain.model.resolveChildNames
import com.parental.shared.feature.activities.ui.formatDeadline
import com.parental.shared.feature.activities.ui.formatDetailDateTime
import com.parental.shared.feature.activities.ui.formatTime
import com.parental.shared.feature.activities.ui.parseInstant
import com.parental.shared.feature.bonds.domain.model.VinculoChild
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/*
 * `activity-detail-bitacora` T1 — las reglas de las dos tarjetas nuevas del detalle:
 * el riel de etapas, el bloque "Qué falta" y la bitácora de eventos.
 *
 * NO importa `androidx.compose.*` a propósito: es la MISMA regla que gobierna
 * `DetailPairs.kt`. Una regla que sólo existe dentro de un `buildList` de un composable
 * privado no se puede testear sin levantar la UI entera, y este repo no tiene
 * infraestructura de UI (`createComposeRule`).
 *
 * 🔴 S6 · NINGÚN HITO SE INVENTA. Todo lo de este archivo se deriva de marcas que el
 * servidor ya manda (`createdAt`, `confirmedAt`, `completedAt`, `declinedReason`, `isPast`)
 * o de la lista de adjuntos que la pantalla ya cargó. Falta `assignedAt` en el DTO, y por
 * eso el evento "se asignó a X" NO existe: no hay fecha con la que ubicarlo en la bitácora.
 */

/**
 * Los cinco pasos del camino feliz (PRD §2), en el orden en que se pintan.
 *
 * `Asiste` es un paso PROPIO: el PRD lo separa de "Confirmada" porque es la acción con la que
 * el asignado acepta. `En progreso` llega por tiempo y `Verificar` no es un paso: el PRD lo
 * deja en el 4 con una indicación aparte.
 */
internal enum class ActivityStage(val label: String) {
    Created("Creada"),
    Assigned("Asignada"),
    Confirmed("Asiste"),
    InProgress("En progreso"),
    Finished("Finalizada"),
}

/**
 * El desenlace negativo del riel (PRD §3).
 *
 * `Expired` lo DECLARA el servidor con `overdue`; `Declined` y `Cancelled` son decisiones de
 * usuario. Ninguno inventa una fecha: el riel sólo dice DÓNDE se cortó la línea, y la fecha
 * (cuando existe) va al registro de trazabilidad, no al stepper.
 */
internal enum class TerminalOutcome(val label: String) {
    Expired("Vencida"),
    Declined("No asiste"),
    Cancelled("Cancelada"),
}

/**
 * Un paso del riel.
 *
 * `current` es único y NO coexiste con un terminal: cuando la línea se corta, el punto de
 * corte reemplaza al actual. `reached` sigue siendo "la línea llegó hasta acá".
 */
internal data class StageNode(
    val stage: ActivityStage,
    val reached: Boolean,
    val current: Boolean,
    val terminal: TerminalOutcome? = null,
)

/** El riel completo: los cinco nodos más los dos hechos que no son un nodo. */
internal data class StageRail(
    val nodes: List<StageNode>,
    val verificationPending: Boolean = false,
) {
    /** El desenlace negativo, o `null` si la actividad sigue en el camino feliz. */
    val terminal: TerminalOutcome? get() = nodes.firstNotNullOfOrNull { it.terminal }

    /** Índice del nodo que cierra la línea, o -1. */
    val terminalIndex: Int get() = nodes.indexOfFirst { it.terminal != null }
}

/** Estados en los que la actividad ya está cerrada. Los mismos alias que usa
 *  [statusSemanticLabel] (`DONE`/`COMPLETED`), repetidos como constante para no importar
 *  una tabla de textos sólo para comparar estados. */
private val FINISHED_STATUSES = setOf("DONE", "COMPLETED")

/** `verify` y su alias viejo: la ventana de verificación, que NO es un paso del riel. */
private val VERIFY_STATUSES = setOf("VERIFY", "PENDING_VERIFICATION")

/** Estados que prueban que el horario ya llegó: "En progreso" es un paso POR TIEMPO. */
private val STARTED_STATUSES =
    setOf("IN_PROGRESS", "VERIFY", "PENDING_VERIFICATION", "DONE", "COMPLETED")

/**
 * Índice de la etapa que el ESTADO declara, sin mirar marcas.
 *
 * `ASSISTING` es el paso 3: el servidor sólo transiciona a `assisting` cuando el asignado
 * confirma, así que el estado ES la prueba de "Asiste". `IN_PROGRESS` en adelante declaran el
 * paso 4; `VERIFY` también, porque la verificación no es un paso del PRD.
 *
 * 🔴 Los terminales (`overdue`, `not_assisting`, `cancelled`) y cualquier estado desconocido
 * NO ascienden el riel: su posición se calcula con las MARCAS (`confirmedAt`, `isPast`,
 * `completedAt`), porque un terminal dice cómo termina, no cuánto avanzó.
 */
private fun stageIndexOf(status: String): Int = when (status.uppercase()) {
    "CREATED" -> ActivityStage.Created.ordinal
    "ASSIGNED" -> ActivityStage.Assigned.ordinal
    "ASSISTING" -> ActivityStage.Confirmed.ordinal
    "IN_PROGRESS", "VERIFY", "PENDING_VERIFICATION", "DONE", "COMPLETED" -> ActivityStage.InProgress.ordinal
    else -> ActivityStage.Created.ordinal
}

/** El desenlace negativo que declara el estado, o `null` si sigue en el camino feliz. */
private fun terminalOutcomeOf(status: String): TerminalOutcome? = when (status.uppercase()) {
    "OVERDUE" -> TerminalOutcome.Expired
    "NOT_ASSISTING" -> TerminalOutcome.Declined
    "CANCELLED" -> TerminalOutcome.Cancelled
    else -> null
}

/**
 * El riel de 5 pasos de una actividad (PRD §5).
 *
 * 🔴 NO se calcula con un índice único: se acumula lo que CADA marca prueba. Un índice
 * obligaba a elegir entre mentir arriba (pintar "Asiste" en una actividad cerrada sin
 * `confirmedAt`) o mentir abajo (retroceder una `in_progress` a "Asignada"). Con el conjunto
 * alcanzado, una actividad puede estar terminada y aun así dejar el hueco visible en
 * "Asiste" — que es exactamente lo que pasó.
 *
 * Con un TERMINAL (`overdue`, `not_assisting`, `cancelled`) la regla cambia una sola cosa: el
 * riel es un PREFIJO hasta el paso donde la línea se cortó, porque el PRD pide que el trazo
 * "avance hasta el paso donde ocurrió y termine ahí". Ese corte es el `current` de la card:
 * no hay etapa actual cuando la actividad ya terminó mal.
 */
internal fun stageRail(actividad: ActividadDetalle): StageRail {
    val status = actividad.status.uppercase()
    val confirmed = actividad.assignedConfirmed ||
        parseInstant(actividad.confirmedAt) != null ||
        status == "ASSISTING"
    val started = status in STARTED_STATUSES ||
        parseInstant(actividad.completedAt) != null ||
        (confirmed && actividad.isPast)
    val finished = parseInstant(actividad.completedAt) != null || status in FINISHED_STATUSES
    val declared = stageIndexOf(status)
    val reached = buildSet {
        add(ActivityStage.Created)
        if (declared >= ActivityStage.Assigned.ordinal || actividad.assignedTo != null) {
            add(ActivityStage.Assigned)
        }
        if (confirmed) add(ActivityStage.Confirmed)
        if (started) add(ActivityStage.InProgress)
        if (finished) add(ActivityStage.Finished)
    }

    val terminal = terminalOutcomeOf(status)
    if (terminal != null) {
        val index = reached.maxOf { it.ordinal }
        return StageRail(
            nodes = ActivityStage.values().map { stage ->
                StageNode(
                    stage = stage,
                    reached = stage.ordinal <= index,
                    current = false,
                    terminal = terminal.takeIf { stage.ordinal == index },
                )
            },
            verificationPending = false,
        )
    }

    val current = reached.maxByOrNull { it.ordinal } ?: ActivityStage.Created
    return StageRail(
        nodes = ActivityStage.values().map { stage ->
            StageNode(stage = stage, reached = stage in reached, current = stage == current)
        },
        verificationPending = status in VERIFY_STATUSES,
    )
}

/** Un paso del bloque "Qué falta". */
internal data class CheckStep(
    val label: String,
    val detail: String,
    val done: Boolean,
)

/**
 * Los pasos pendientes que el usuario puede ver hoy, con sus datos al lado.
 *
 * Sólo dos, y cada uno con una fuente que existe:
 * - **Turno** — la fecha programada, y `isPast` del servidor (`now >= coalesce(end, start)`)
 *   como marca de que ocurrió. El cliente NO calcula el paso del tiempo.
 * - **Comprobantes** — la cuenta de adjuntos ya cargada y el `deadline` del servidor.
 *
 * El paso "Verificación" del artboard no se implementa: el DTO no dice quién debe verificar
 * ni cuándo se verificó (sólo existe el estado `VERIFY`, cuya ventana ya pinta
 * `VerifyWindowCard` con su propio texto). Inventarlo sería prometer un paso que la app no
 * puede observar.
 */
internal fun detailChecklist(
    actividad: ActividadDetalle,
    attachmentCount: Int,
    zone: TimeZone = TimeZone.currentSystemDefault(),
): List<CheckStep> = buildList {
    val finished = parseInstant(actividad.completedAt) != null ||
        actividad.status.uppercase() in FINISHED_STATUSES
    // `when` como nombre de parámetro es palabra reservada: el slot programado va en `slot`.
    formatDetailDateTime(actividad.scheduledStart, actividad.scheduledEnd, zone)?.let { slot ->
        add(CheckStep(label = "Turno", detail = slot, done = actividad.isPast || finished))
    }
    val files = when (attachmentCount) {
        0 -> "sin cargar"
        1 -> "1 archivo"
        else -> "$attachmentCount archivos"
    }
    // R-7 (misma regla que `detailPairs`): el límite se omite si no hay `deadline`, nunca se
    // pinta una fecha inventada ni un "sin límite" que ocupe el lugar de un dato.
    val limit = parseInstant(actividad.deadline)?.let { "límite ${formatDeadline(it, zone)}" }
    add(
        CheckStep(
            label = "Comprobantes",
            detail = listOfNotNull(files, limit).joinToString(" · "),
            done = attachmentCount > 0,
        ),
    )
}

/** El tipo de evento de la bitácora. Decide el icono y el color del disco. */
internal enum class BitacoraKind { Created, Confirmed, Completed, Declined }

/**
 * Un evento de la bitácora.
 *
 * `date`/`time` son `null` sólo cuando el DTO no trae la marca (el motivo de no asistir no
 * tiene timestamp). `context` es el dato que acompaña al evento: los hijos a los que refiere
 * (S5) o, en el caso de una no-asistencia, el motivo.
 */
internal data class BitacoraEvent(
    val kind: BitacoraKind,
    val title: String,
    val author: String?,
    val date: LocalDate?,
    val time: String?,
    val context: String?,
)

/**
 * La bitácora de una actividad, en orden de ciclo de vida (creación → confirmación →
 * finalización → no asistencia). El orden es el de la construcción, NO un `sortedBy`: los
 * tres timestamps son jerárquicos por definición, y ordenar por fecha exigiría tratar el
 * `null` del motivo de no asistir como una fecha.
 */
internal fun bitacoraEvents(
    actividad: ActividadDetalle,
    memberNames: Map<String, String>,
    children: List<VinculoChild> = emptyList(),
    zone: TimeZone = TimeZone.currentSystemDefault(),
): List<BitacoraEvent> = buildList {
    // D-5 (misma regla que `detailPairs`): los hijos se resuelven contra `children`, no
    // contra `memberNames`, y un id que no resuelve se omite en vez de pintar un uuid.
    val childNames = resolveChildNames(children, actividad.childrenIds)
        .takeIf { it.isNotEmpty() }
        ?.joinToString(", ")
    fun timed(kind: BitacoraKind, title: String, author: String?, raw: String?) {
        val local = parseInstant(raw)?.toLocalDateTime(zone) ?: return
        add(BitacoraEvent(kind, title, author, local.date, formatTime(local.hour, local.minute), childNames))
    }
    timed(
        kind = BitacoraKind.Created,
        title = "Actividad creada",
        author = memberNames[actividad.createdBy]?.takeIf { it.isNotBlank() },
        raw = actividad.createdAt,
    )
    // La marca es `confirmedAt`; `assignedConfirmed` sólo habilita intentarlo. Sin instante no
    // hay evento: un hito sin fecha no se puede ubicar en una bitácora.
    if (actividad.assignedConfirmed || parseInstant(actividad.confirmedAt) != null) {
        timed(
            kind = BitacoraKind.Confirmed,
            title = "Asignación confirmada",
            author = actividad.assignedTo?.let { id -> memberNames[id]?.takeIf { it.isNotBlank() } },
            raw = actividad.confirmedAt,
        )
    }
    timed(kind = BitacoraKind.Completed, title = "Actividad finalizada", author = null, raw = actividad.completedAt)
    actividad.declinedReason?.takeIf { it.isNotBlank() }?.let { reason ->
        add(BitacoraEvent(BitacoraKind.Declined, "No asiste", null, null, null, reason))
    }
}
