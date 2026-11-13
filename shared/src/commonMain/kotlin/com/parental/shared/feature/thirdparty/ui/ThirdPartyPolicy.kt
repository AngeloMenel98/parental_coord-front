package com.parental.shared.feature.thirdparty.ui

import com.parental.shared.feature.activities.domain.model.Actividad
import com.parental.shared.feature.activities.domain.model.isAttachmentsEditable
import kotlinx.datetime.LocalDate

/**
 * Los cuatro chips del artboard `design/terciario-ui/03-permiso-explicito.html`.
 *
 * **No son los tabs de los progenitores.** `ActivityTab` agrupa por TIPO (Eventos /
 * Obligaciones) porque un progenitor planifica; el terciario no planifica nada: lo único
 * que decide es si una tarea suya ya se puede finalizar. Por eso sus chips agrupan por
 * ESTADO ACCIONABLE y son cuatro, no tres (L7 en `odd/tasks/terciarios-temporales.md`).
 */
enum class ThirdPartyFilter(val label: String) {
    ASIGNADAS("Asignadas"),
    EN_PROGRESO("En progreso"),
    VENCIDAS("Vencidas"),
    FINALIZADAS("Finalizadas"),
}

/** Lo que la fila ofrece al final. La acción NO se deriva del estado, se deriva del flag. */
enum class ThirdPartyTaskAction(val label: String) {
    /** El servidor dijo `canComplete` (S13): sólo el asignado, sólo en `in_progress`/`verify`. */
    FINALIZE("Finalizar"),

    /** Asignada pero todavía no finalizable: el paso del tiempo la va a habilitar. */
    WAITING("Esperando"),

    /** Vencida: no hay acción posible y la UI lo dice en palabras, no en gris. */
    NONE("Sin acción"),

    /** Cerrada (cumplida o dada de baja por quien la asignó). */
    CLOSED("Cerrada"),
}

/**
 * El chip al que pertenece un estado del servidor, o `null` si ningún chip lo reclama.
 *
 * `cancelled` y `not_assisting` caen en [ThirdPartyFilter.FINALIZADAS] porque ya no son
 * accionables para el terciario —no puede cancelar ni rechazar (S8)—, y la fila sigue
 * mostrando su `StatusTag` real ("Cancelada", "No asiste"), así que el bucket no miente
 * sobre lo que pasó. Un estado DESCONOCIDO no entra en ningún chip: inventarle un bucket
 * lo volvería invisible bajo una etiqueta falsa, y es preferible que un estado nuevo del
 * backend no aparezca antes que verlo mal clasificado.
 */
internal fun thirdPartyFilterOf(status: String): ThirdPartyFilter? =
    when (status.lowercase()) {
        "assigned", "assisting", "created" -> ThirdPartyFilter.ASIGNADAS
        "in_progress", "verify" -> ThirdPartyFilter.EN_PROGRESO
        "overdue" -> ThirdPartyFilter.VENCIDAS
        "done", "cancelled", "not_assisting" -> ThirdPartyFilter.FINALIZADAS
        else -> null
    }

/**
 * S6 aplicado en el cliente, como segunda barrera: el back ya filtra por assignee (T5),
 * pero mientras T5 no exista la lista bond-wide no puede llegar entera a esta vista.
 * Una actividad sin asignado no es de nadie y no entra.
 */
internal fun thirdPartyVisibleActivities(all: List<Actividad>, currentUserId: String): List<Actividad> =
    all.filter { it.assignedTo != null && it.assignedTo == currentUserId }

/** Conteo por chip, en el orden de los chips (el patrón `tabCounts` de los progenitores). */
internal fun thirdPartyFilterCounts(activities: List<Actividad>): Map<ThirdPartyFilter, Int> =
    ThirdPartyFilter.entries.associateWith { filter ->
        activities.count { thirdPartyFilterOf(it.status) == filter }
    }

internal fun thirdPartyActivitiesFor(
    activities: List<Actividad>,
    filter: ThirdPartyFilter,
): List<Actividad> = activities.filter { thirdPartyFilterOf(it.status) == filter }

/**
 * Orden de la lista: lo programado primero y cronológico; lo que no tiene fecha al final.
 * El ISO-8601 del servidor ordena bien como string, así que no hace falta parsear para
 * ordenar (y un `scheduledStart` corrupto no rompe el orden, sólo se va al final).
 */
internal fun thirdPartySorted(activities: List<Actividad>): List<Actividad> =
    activities.sortedBy { it.scheduledStart ?: "~" }

/**
 * El Inicio muestra todo lo ABIERTO —asignadas, en progreso y vencidas—, que es lo que el
 * terciario tiene entre manos hoy; lo cerrado vive en el chip "Finalizadas" de Actividades.
 */
internal fun thirdPartyHomeTasks(activities: List<Actividad>): List<Actividad> =
    thirdPartySorted(activities.filter { thirdPartyFilterOf(it.status) != ThirdPartyFilter.FINALIZADAS })

/**
 * Cuántas tareas están realmente activas: las vencidas NO cuentan (el artboard muestra
 * "3 activas" con cuatro filas, una vencida). Una vencida no es trabajo en curso y
 * contarla inflaría la única cifra de la tarjeta.
 */
internal fun thirdPartyActiveCount(activities: List<Actividad>): Int =
    activities.count { thirdPartyFilterOf(it.status) == ThirdPartyFilter.ASIGNADAS || thirdPartyFilterOf(it.status) == ThirdPartyFilter.EN_PROGRESO }

/**
 * La acción al final de la fila. **`FINALIZE` exige `canComplete` del servidor**: el
 * cliente no recalcula la política (`ActivitiesModels.kt` es explícito en eso), así que un
 * servidor viejo degrada a "Esperando" en vez de a un botón que va a dar 403.
 */
internal fun thirdPartyTaskAction(actividad: Actividad): ThirdPartyTaskAction =
    when (thirdPartyFilterOf(actividad.status)) {
        ThirdPartyFilter.FINALIZADAS -> ThirdPartyTaskAction.CLOSED
        ThirdPartyFilter.VENCIDAS -> ThirdPartyTaskAction.NONE
        ThirdPartyFilter.ASIGNADAS, ThirdPartyFilter.EN_PROGRESO ->
            if (actividad.canComplete) ThirdPartyTaskAction.FINALIZE else ThirdPartyTaskAction.WAITING
        null -> ThirdPartyTaskAction.NONE
    }

/**
 * S10: adjuntar es del ASIGNADO. Se combina con la regla de la actividad
 * (`isAttachmentsEditable`) en vez de reimplementarla: la actividad cerrada o vencida no
 * acepta archivos para nadie, y el terciario tampoco para tareas ajenas.
 */
internal fun thirdPartyCanAttach(
    status: String,
    assignedTo: String?,
    currentUserId: String,
): Boolean = assignedTo == currentUserId && isAttachmentsEditable(status, assignedTo)

/**
 * El paso del riel que el detalle marca como alcanzado (0 = ninguno).
 *
 * Es una escalera propia y no `stageRail(...)` de la bitácora porque el terciario no
 * necesita la bitácora: necesita saber dónde está de los cuatro pasos que S11/D6
 * conservan (`assisting → in_progress → verify → done`).
 */
internal fun thirdPartyStage(status: String): Int =
    when (status.lowercase()) {
        "assigned", "assisting" -> 1
        "in_progress" -> 2
        "verify" -> 3
        "done" -> 4
        else -> 0
    }

/** Los cuatro rótulos del riel, en el orden del ciclo. */
internal val THIRD_PARTY_STAGES: List<String> = listOf("Asiste", "En progreso", "Verificar", "Finalizada")

/** Días que faltan para el vencimiento; negativo si ya venció. `null` sin fecha. */
internal fun thirdPartyDaysLeft(endDate: LocalDate?, today: LocalDate): Int? =
    endDate?.let { it.toEpochDays() - today.toEpochDays() }

/**
 * La frase de vigencia, sin números inventados: un día es "1 día", el día del vencimiento
 * es "Vence hoy" y el pasado es "Venció". Se dice en palabras porque la barra sola no es
 * una señal accesible.
 */
internal fun thirdPartyDaysLeftLabel(days: Int?): String? =
    when {
        days == null -> null
        days < 0 -> "Venció"
        days == 0 -> "Vence hoy"
        days == 1 -> "1 día"
        else -> "$days días"
    }

/**
 * Fracción del período de acceso ya transcurrida, para la barra de vigencia.
 * `null` si falta alguna de las dos fechas o si el período es inválido: sin denominador
 * no hay barra honesta, y una barra en 0% leería como "no queda nada".
 */
internal fun thirdPartyAccessProgress(
    startDate: LocalDate?,
    endDate: LocalDate?,
    today: LocalDate,
): Float? {
    if (startDate == null || endDate == null) return null
    val total = endDate.toEpochDays() - startDate.toEpochDays()
    if (total <= 0) return if (today >= endDate) 0f else 1f
    val elapsed = today.toEpochDays() - startDate.toEpochDays()
    return (elapsed.toFloat() / total.toFloat()).coerceIn(0f, 1f)
}
