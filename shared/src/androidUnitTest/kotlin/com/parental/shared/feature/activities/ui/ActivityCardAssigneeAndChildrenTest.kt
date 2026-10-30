package com.parental.shared.feature.activities.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * S7/S8 · CONTRATO DE LA TARJETA: muestra asignatario e hijos, sin romper la
 * semántica ni el único `clickable` del cuerpo.
 *
 * Prueba de FUENTE (no hay `androidTest` ni `createComposeRule` en este repo).
 */
class ActivityCardAssigneeAndChildrenTest {

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
     * El texto SIN comentarios ni literales: la prosa en castellano rompe el balance,
     * los literales ocultan palabras clave que buscamos en el código.
     */
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

    @Test
    fun `la tarjeta recibe los mapas de nombres y la lista de hijos`() {
        val signature = functionBody(source("ActivityCard.kt"), "ActivityCard")

        assertTrue(
            "memberNames" in signature,
            "🔴 `ActivityCard` no recibe `memberNames`: no puede resolver el asignatario client-side.",
        )
        assertTrue(
            "children" in signature,
            "🔴 `ActivityCard` no recibe `children`: no puede resolver los nombres de los hijos.",
        )
    }

    @Test
    fun `la tarjeta pinta la linea Asignada a`() {
        val card = source("ActivityCard.kt")

        assertTrue(
            "Asignada a:" in card,
            "🔴 La tarjeta no muestra la etiqueta 'Asignada a:' (A-2).",
        )
        assertTrue(
            "AssigneeLine(" in card,
            "🔴 La tarjeta no tiene un composable `AssigneeLine` para pintar el asignatario.",
        )
    }

    @Test
    fun `la tarjeta pinta la linea de hijos`() {
        val card = source("ActivityCard.kt")

        assertTrue(
            "ChildrenLine(" in card,
            "🔴 La tarjeta no tiene un composable `ChildrenLine` para pintar los hijos (A-3).",
        )
    }

    @Test
    fun `la tarjeta sigue teniendo UN SOLO clickable`() {
        val body = functionBody(source("ActivityCard.kt"), "ActivityCardBody")
        val count = Regex("\\.clickable\\(").findAll(body).count()

        assertEquals(
            1, count,
            "🔴 `ActivityCardBody` tiene $count `clickable`. Un segundo clickable rompe el " +
                "contrato de accesibilidad: un toque cerca del botón abriría el detalle.",
        )
    }

    @Test
    fun `AssigneeLine no anade un segundo clickable`() {
        val body = functionBody(source("ActivityCard.kt"), "AssigneeLine")
        val count = Regex("\\.clickable\\(").findAll(body).count()

        assertEquals(
            0, count,
            "🔴 `AssigneeLine` tiene un `clickable`: la línea de asignatario no debe ser pulsable.",
        )
    }

    @Test
    fun `ChildrenLine no anade un segundo clickable`() {
        val body = functionBody(source("ActivityCard.kt"), "ChildrenLine")
        val count = Regex("\\.clickable\\(").findAll(body).count()

        assertEquals(
            0, count,
            "🔴 `ChildrenLine` tiene un `clickable`: la línea de hijos no debe ser pulsable.",
        )
    }

    @Test
    fun `la lista Agenda pasa memberNames y children a la tarjeta`() {
        val list = source("AgendaList.kt")

        assertTrue(
            "memberNames = memberNames" in list || "memberNames =" in list,
            "🔴 `AgendaTimeline` no recibe o no reenvía `memberNames` a `ActivityCard`.",
        )
        assertTrue(
            "children = children" in list || "children =" in list,
            "🔴 `AgendaTimeline` no recibe o no reenvía `children` a `ActivityCard`.",
        )
    }
}
