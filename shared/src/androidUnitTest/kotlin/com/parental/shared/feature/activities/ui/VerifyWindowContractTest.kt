package com.parental.shared.feature.activities.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * S3 (T18) — la ventana de verificación y los flags del servidor, contrato de FUENTE.
 *
 * ⚠ Igual que `ActivityCardConfirmedChipTest`, esto es una prueba de fuente y lo es a
 * propósito: este repo no tiene `createComposeRule`, así que componer la hoja para contar
 * el nodo no es una opción. Leer el texto es más honesto que fingir una prueba de UI.
 *
 * LO QUE DEFIENDE:
 *
 *  1. El detalle PINTA la ventana cuando `status == "verify"`: sin ella el usuario está en
 *     el único estado en el que tiene que actuar y no ve ni el plazo ni la consecuencia.
 *  2. El plazo sale de `deadline`, que manda el SERVIDOR (lo reancla a `now + 36 h` al
 *     entrar en `verify`). El cliente NO calcula vencimientos: si lo hiciera, el plazo
 *     del aviso y el que decide el estado `overdue` podrían discrepar.
 *  3. Se reutiliza el formato que ya existe (`formatDeadline` + `formatTime`), no un
 *     segundo formateador que envejezca por su cuenta.
 *  4. "Finalizar" se gatea por `canComplete` (flag del servidor) y NUNCA por
 *     `status != "done"`: el gate local pintaba el botón en estados donde el POST daba 409.
 *  5. La regla local `canConfirmAssignment` no vuelve: el permiso viaja en el DTO.
 */
class VerifyWindowContractTest {

    private fun activitiesPath(relative: String): File {
        var candidate: File? = File("").absoluteFile
        while (candidate != null) {
            val dir = File(
                candidate,
                "src/commonMain/kotlin/com/parental/shared/feature/activities/$relative",
            )
            if (dir.isDirectory) return dir
            candidate = candidate.parentFile
        }
        fail("No se encuentra `src/commonMain/.../activities/$relative` subiendo desde ${File("").absolutePath}")
    }

    private fun source(relative: String): String =
        File(activitiesPath(relative.substringBeforeLast('/')), relative.substringAfterLast('/')).readText()

    /** Mismo recorte que `ActivityCardConfirmedChipTest`: los comentarios traen prosa y literales. */
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

    private fun detailSheet(): String = stripComments(source("ui/detail/ActividadDetailSheet.kt"))

    // ── la ventana ───────────────────────────────────────────────────────

    @Test
    fun `el detalle pinta la ventana de verificacion cuando el estado es verify`() {
        val detail = detailSheet()

        assertTrue(
            "\"verify\"" in detail,
            "🔴 El detalle no mira el estado `verify`: sin ventana, el usuario está en el " +
                "único estado en el que tiene que actuar y no ve el plazo.",
        )
        assertTrue(
            "VerifyWindowCard" in detail,
            "🔴 La ventana de verificación desapareció del detalle (S3/T18).",
        )
    }

    @Test
    fun `el plazo sale del deadline del servidor y no de un reloj del cliente`() {
        val detail = detailSheet()

        assertTrue(
            "parseInstant(a.deadline)" in detail.replace(" ", ""),
            "🔴 La ventana no lee `a.deadline`: el plazo lo reancla el servidor al entrar en " +
                "`verify`, y el aviso tiene que decir EL MISMO instante que decide `overdue`.",
        )
        assertFalse(
            "Clock.System.now" in detail,
            "🔴 El detalle calcula el vencimiento con el reloj del cliente: el plazo del aviso " +
                "y el que aplica el servidor se separarían en cuanto los relojes discrepen.",
        )
    }

    @Test
    fun `la ventana reutiliza los formateadores existentes`() {
        val detail = detailSheet()

        assertTrue(
            "formatDeadline(" in detail,
            "La ventana debe usar `formatDeadline`, el mismo formateador que la tarjeta.",
        )
        assertTrue(
            "formatTime(" in detail,
            "La ventana debe usar `formatTime`: la hora del vencimiento importa, no sólo el día.",
        )
    }

    @Test
    fun `el plazo es inclusivo del dia del vencimiento`() {
        val detail = detailSheet()

        // Lenguaje del usuario: "antes del <día> a las <hora>". Cambiar el ancla ("hasta el")
        // cambiaría si el propio instante del deadline todavía se puede finalizar.
        assertTrue(
            "\"Por verificar\"" in detail,
            "La tarjeta de la ventana debe titularse \"Por verificar\".",
        )
        assertTrue(
            "Finaliza antes del" in detail,
            "⚠ El texto debe seguir anclado en \"Finaliza antes del\": es el wording aprobado " +
                "para la ventana.",
        )
    }

    // ── los flags del servidor ───────────────────────────────────────────

    @Test
    fun `finalizar se gatea por canComplete y no por status distinto de done`() {
        val detail = detailSheet()

        assertTrue(
            "actividad.canComplete" in detail,
            "🔴 \"Finalizar\" no se gatea por `canComplete`: el servidor decide qué " +
                "transiciones existen (S2) y el cliente las pinta.",
        )
        assertFalse(
            "status != \"done\"" in detail,
            "🔴 Volvió el gate local `status != \"done\"`: pintaba \"Finalizar\" en estados " +
                "donde el POST responde 409.",
        )
    }

    @Test
    fun `confirmar se pinta desde el flag del DTO`() {
        val detail = detailSheet()

        assertTrue(
            "actividad.canConfirm" in detail,
            "🔴 El botón \"Confirmar\" no se pinta desde `canConfirm` del DTO.",
        )
        assertFalse(
            "canConfirm(" in detail,
            "🔴 Volvió una llamada `canConfirm(...)`: la regla de asignación vive en " +
                "`ActivityPolicyService.evaluate`, no en el cliente.",
        )
    }

    @Test
    fun `la regla local canConfirmAssignment no vuelve`() {
        val models = stripComments(source("domain/model/ActivitiesModels.kt"))

        assertFalse(
            "canConfirmAssignment" in models,
            "🔴 La regla local `canConfirmAssignment` reapareció en los modelos: sería una " +
                "segunda copia de la política del servidor, y la copia del cliente es justo " +
                "la que se equivoca.",
        )
        assertEquals(
            2,
            Regex("val canConfirm: Boolean = false").findAll(models).count(),
            "🔴 `canConfirm` debe existir en `Actividad` y en `ActividadDetalle`, con default " +
                "`false` para degradar contra un servidor viejo.",
        )
        assertEquals(
            2,
            Regex("val canComplete: Boolean = false").findAll(models).count(),
            "🔴 `canComplete` debe existir en `Actividad` y en `ActividadDetalle`, con default " +
                "`false` para degradar contra un servidor viejo.",
        )
    }
}
