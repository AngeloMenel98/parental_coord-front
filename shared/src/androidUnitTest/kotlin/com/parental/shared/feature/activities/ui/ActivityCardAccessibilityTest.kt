package com.parental.shared.feature.activities.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * S8 · CONTRATO DE ACCESIBILIDAD de la tarjeta y de la hoja de calendario — T5 de
 * `odd/tasks/activities-cards-ui.md`.
 *
 * ⚠ Es una prueba de FUENTE, y lo es a propósito: este repo no tiene `androidTest` ni
 * `createComposeRule` (ver la cabecera de `SwipeMountingTest`), así que no hay forma de
 * componer y contar nodos de semántica. Leer el texto es más honesto que fingir una
 * prueba de UI que no existe.
 *
 * LO QUE DEFENDE, y por qué cada cosa importa:
 *
 *  1. La tarjeta es UN nodo de semántica (título + hora + límite + estado + categoría) y
 *     el botón "Confirmar" es OTRO. Anidarlos fue el defecto que `sdd-verify` encontró en
 *     `activity-swipe-actions`: un toque cerca del botón abría el DETALLE en vez de
 *     confirmar (`R2-T2-8`).
 *  2. El cuerpo de la tarjeta tiene UN SOLO `clickable`. Un segundo (en la insignia, en
 *     el pie) devolvería el bug anidado.
 *  3. La celda del calendario fusiona sus descendientes: sin eso, TalkBack lee la celda
 *     y ADEMÁS el número del día suelto.
 *  4. Los puntos y los iconos son decorativos: el color nunca es la única señal.
 *  5. Los objetivos táctiles nuevos usan el token de 48dp (`touchTargetLegacy`, que
 *    despite su nombre vale 48 — así lo fijó T3-8).
 */
class ActivityCardAccessibilityTest {

    private val uiDir: File by lazy { activitiesUiDir() }

    private fun activitiesUiDir(): File {
        var candidate: File? = File("").absoluteFile
        while (candidate != null) {
            val dir = File(candidate, "src/commonMain/kotlin/com/parental/shared/feature/activities/ui")
            if (dir.isDirectory) return dir
            candidate = candidate.parentFile
        }
        fail("No se encuentra `src/commonMain/kotlin/.../activities/ui` subiendo desde ${File("").absolutePath}")
    }

    private fun source(name: String): String = File(uiDir, name).readText()

    /**
     * La raíz del módulo `shared`: tres niveles arriba de `…/feature/activities/ui`.
     * De ahí cuelgan `core/ui/theme/` y `core/ui/components/`.
     */
    private fun sharedRoot(): File =
        uiDir.parentFile.parentFile.parentFile

    /** El texto SIN comentarios ni literales: la prosa en castellano rompe el balance. */
    private fun stripCommentsAndStrings(text: String): String {
        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '/' && i + 1 < text.length && text[i + 1] == '/' -> {
                    while (i < text.length && text[i] != '\n') { out.append(' '); i++ }
                }
                c == '/' && i + 1 < text.length && text[i + 1] == '*' -> {
                    val end = text.indexOf("*/", i + 2).let { if (it < 0) text.length else it + 2 }
                    repeat(end - i) { k -> out.append(if (text[i + k] == '\n') '\n' else ' ') }
                    i = end
                }
                c == '"' -> {
                    val triple = text.startsWith("\"\"\"", i)
                    val close = if (triple) "\"\"\"" else "\""
                    out.append(' '); i++
                    while (i < text.length) {
                        if (text.startsWith(close, i)) {
                            repeat(close.length) { out.append(' ') }
                            i += close.length
                            break
                        }
                        if (!triple && text[i] == '\\' && i + 1 < text.length) {
                            out.append(' '); i++
                        }
                        out.append(if (text[i] == '\n') '\n' else ' '); i++
                    }
                }
                else -> { out.append(c); i++ }
            }
        }
        return out.toString()
    }

    /** El bloque de un composable: desde su declaración hasta el final de su cuerpo. */
    private fun functionBody(text: String, function: String): String {
        val code = stripCommentsAndStrings(text)
        val at = code.indexOf("fun $function(")
        assertTrue(at >= 0, "No se encuentra la declaración de `$function`")
        var j = at + "fun $function(".length
        var depth = 1
        while (j < code.length && depth > 0) {
            if (code[j] == '(') depth++
            if (code[j] == ')') depth--
            j++
        }
        val open = code.indexOf('{', j)
        if (open < 0) return code.substring(at, j)
        var k = open
        depth = 0
        while (k < code.length) {
            when (code[k]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return code.substring(at, k + 1)
                }
            }
            k++
        }
        return code.substring(at)
    }

    // ── 1 · La tarjeta es UN nodo de semántica ─────────────────────────────────────────

    @Test
    fun `el cuerpo de la tarjeta fusiona sus descendientes con una descripcion`() {
        val body = functionBody(source("ActivityCard.kt"), "ActivityCardBody")

        assertTrue(
            "mergeDescendants = true" in body,
            "🔴 El cuerpo de la tarjeta no fusiona sus descendientes: el título, la hora y " +
                "el límite serían nodos sueltos en TalkBack en vez de UNA frase.\n$body",
        )
        assertTrue(
            "contentDescription = semanticLabel" in body,
            "🔴 El cuerpo de la tarjeta no pone `contentDescription`: la tarjeta se " +
                "anunciaría como texto suelto, sin fecha, categoría ni estado.",
        )
    }

    @Test
    fun `la tarjeta usa la etiqueta combinada que ya existe`() {
        val list = source("AgendaList.kt")

        assertTrue(
            "activitySemanticLabel(" in list,
            "La tarjeta debe usar `activitySemanticLabel`: es la ÚNICA frase que anuncia " +
                "fecha, hora, categoría, tipo, estado y límite.",
        )
    }

    // ── 2 · UN solo clickable, y el botón es hermano ────────────────────────────────

    @Test
    fun `el cuerpo de la tarjeta tiene UN SOLO clickable`() {
        val body = functionBody(source("ActivityCard.kt"), "ActivityCardBody")
        val count = Regex("\\.clickable\\(").findAll(body).count()

        assertEquals(
            1, count,
            "🔴 El cuerpo de la tarjeta tiene $count `clickable`. El segundo sería un " +
                "clickable anidado: un toque cerca de él abriría el detalle en vez de " +
                "hacer lo suyo (`R2-T2-8`).",
        )
    }

    @Test
    fun `el boton Confirmar se pinta FUERA del cuerpo clickeable`() {
        val card = stripCommentsAndStrings(source("ActivityCard.kt"))
        val clickableAt = card.indexOf(".clickable(")
        val confirmAt = card.indexOf("ConfirmButton(")

        assertTrue(clickableAt >= 0, "El cuerpo de la tarjeta no tiene `clickable`")
        assertTrue(confirmAt >= 0, "No se encuentra la llamada a `ConfirmButton`")
        assertTrue(
            confirmAt > clickableAt,
            "🔴 `ConfirmButton()` se pinta ANTES del cuerpo clickeable. El botón tiene que " +
                "ser HERMANO de ese cuerpo, no una parte suya: si queda dentro, un toque " +
                "cerca del botón abre el detalle en vez de confirmar.",
        )
    }

    @Test
    fun `el boton Confirmar es un boton de Material con su propia semantica`() {
        val card = stripCommentsAndStrings(source("ActivityCard.kt"))

        assertTrue(
            "OutlinedButton(" in card,
            "El botón Confirmar debe ser un `OutlinedButton`: Material le da el objetivo " +
                "táctil de 48dp (`minimumInteractiveComponentSize`) y su semántica propia.",
        )
        assertTrue(
            "Confirmar" in source("ActivityCard.kt"),
            "El botón tiene que decir `Confirmar` (el copy de la app).",
        )
    }

    // ── 3 · La celda del calendario fusiona y anuncia ───────────────────────────────

    @Test
    fun `la celda del calendario fusiona sus descendientes`() {
        val sheet = functionBody(source("CalendarJumpSheet.kt"), "CalendarDayCell")

        assertTrue(
            "mergeDescendants = true" in sheet,
            "🔴 La celda no fusiona: TalkBack anunciaría la celda y ADEMÁS el número del " +
                "día suelto, en dos nodos.",
        )
        assertTrue(
            "dayCellSemanticLabel(" in sheet,
            "La celda debe anunciar la fecha y las actividades con `dayCellSemanticLabel`.",
        )
    }

    @Test
    fun `las celdas de otro mes son inertes y no se anuncian`() {
        val sheet = functionBody(source("CalendarJumpSheet.kt"), "CalendarDayCell")

        assertTrue(
            "clearAndSetSemantics" in sheet,
            "🔴 Las celdas de otro mes deben quedar fuera del árbol accesible: son relleno " +
                "de la rejilla, no destinos.",
        )
    }

    // ── 4 · Los puntos son decorativos ───────────────────────────────────────────────

    @Test
    fun `los puntos de categoria y de estado son decorativos`() {
        val dot = stripCommentsAndStrings(
            File(sharedRoot(), "core/ui/components/TagDot.kt").readText(),
        )

        assertTrue(
            "clearAndSetSemantics" in dot,
            "🔴 `TagDot` dejó de limpiar su semántica: el punto volvería a anunciarse " +
                "y duplicaría la categoría o el estado, que ya van en el rótulo.",
        )
    }

    // ── 5 · Objetivos táctiles de 48dp ───────────────────────────────────────────────

    @Test
    fun `los objetivos tactiles nuevos usan el token de 48dp`() {
        val card = source("ActivityCard.kt")
        val sheet = source("CalendarJumpSheet.kt")
        val filters = source("ActivityFilters.kt")

        assertTrue(
            "AgendaGeometry.touchTargetLegacy" in card,
            "El cuerpo de la tarjeta debe medir al menos el objetivo táctil (48dp).",
        )
        assertTrue(
            "AgendaGeometry.touchTargetLegacy" in sheet,
            "La celda del calendario debe medir al menos el objetivo táctil (48dp).",
        )
        assertTrue(
            "AgendaGeometry.touchTargetLegacy" in filters,
            "Los chips de filtro deben medir al menos el objetivo táctil (48dp).",
        )
    }

    @Test
    fun `el token de objetivo tactil vale 48dp`() {
        val spacing = File(sharedRoot(), "core/ui/theme/Spacing.kt").readText()

        assertTrue(
            "val touchTargetLegacy: Dp = 48.dp" in spacing,
            "🔴 `touchTargetLegacy` ya no vale 48dp. El nombre es histórico (T3-8 lo dejó " +
                "así), pero el VALOR es el que garantiza el objetivo táctil de la app.",
        )
    }
}
