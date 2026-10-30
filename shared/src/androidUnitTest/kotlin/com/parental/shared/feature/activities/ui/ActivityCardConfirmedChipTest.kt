package com.parental.shared.feature.activities.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * S1 (T01) — la tarjeta de actividad NO pinta el chip "Confirmada".
 *
 * ⚠ Es una prueba de FUENTE, y lo es a propósito: este repo no tiene `androidTest` ni
 * `createComposeRule` (ver la cabecera de `ActivityCardAccessibilityTest`), así que no
 * hay forma de componer la tarjeta y contar sus nodos. Leer el texto es más honesto que
 * fingir una prueba de UI que no existe.
 *
 * LO QUE DEFENDE, y por qué cada cosa importa:
 *
 *  1. La tarjeta no pinta el chip "Confirmada" (A-1). Duplica la insignia de estado y
 *     es un dato POR PERSONA en una superficie de actividad.
 *  2. La tarjeta ni siquiera menciona `assignedConfirmed`: si el chip se va, la condición
 *     que lo pintaba se va con él — si no, queda un `assignedConfirmed` muerto que
 *     promete un dato que ya no se muestra.
 *  3. La hoja de detalle SIGUE anunciando la asignación confirmada: ese es el sitio
 *     donde el dato sí significa algo (R4). Quitar el chip no puede borrar el dato.
 */
class ActivityCardConfirmedChipTest {

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
     * El texto SIN comentarios: la prosa en castellano rompe el balance. Los literales
     * se quedan — es justo lo que hay que buscar ("la etiqueta pintada").
     */
    private fun stripComments(text: String): String {
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
                else -> { out.append(c); i++ }
            }
        }
        return out.toString()
    }

    @Test
    fun `la tarjeta no pinta la etiqueta Confirmada`() {
        val card = stripComments(source("ActivityCard.kt"))

        assertFalse(
            "\"Confirmada\"" in card,
            "🔴 La tarjeta sigue pintando el chip \"Confirmada\" (A-1): duplica la insignia " +
                "de estado y es un dato por persona en una superficie de actividad.",
        )
    }

    @Test
    fun `la tarjeta no tiene el composable ConfirmedChip`() {
        val card = stripComments(source("ActivityCard.kt"))

        assertFalse(
            "ConfirmedChip" in card,
            "🔴 El composable `ConfirmedChip` sigue vivo en la tarjeta: sin chip no hay " +
                "por qué lo tenga.",
        )
    }

    @Test
    fun `la tarjeta no decide por assignedConfirmed`() {
        val card = stripComments(source("ActivityCard.kt"))

        assertFalse(
            "assignedConfirmed" in card,
            "🔴 La tarjeta todavía mira `assignedConfirmed`: si el chip ya no se pinta, la " +
                "condición que lo pintaba tiene que irse con él.",
        )
    }

    @Test
    fun `la hoja de detalle sigue anunciando la asignacion confirmada`() {
        val detail = stripComments(source("detail/ActividadDetailSheet.kt"))

        assertTrue(
            "assignedConfirmed" in detail,
            "🔴 La hoja de detalle ha perdido `assignedConfirmed`: ese es el sitio donde el " +
                "dato SÍ significa algo (R4) — quitarlo del chip no puede borrarlo.",
        )
        assertTrue(
            "\"Asignación confirmada\"" in detail,
            "La hoja de detalle debe seguir diciendo \"Asignación confirmada\": es la línea " +
                "por persona que reemplaza al chip de la tarjeta.",
        )
    }
}
