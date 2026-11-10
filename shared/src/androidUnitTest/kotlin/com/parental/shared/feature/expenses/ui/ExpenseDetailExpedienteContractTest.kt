package com.parental.shared.feature.expenses.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Contrato de FUENTE del detalle "Expediente" (design/expense-detail-ui/01).
 *
 * El repo no tiene `createComposeRule`, así que la UI se fija leyendo la fuente, igual que el
 * resto de los contratos de gastos. Defiende lo que el artboard pide y lo que ya se corrigió:
 *
 *  S1 encabezado con chips (categoría SIN ícono), monto serif, `Tu parte` del viewer y barra de
 *     división con sus dos leyendas;
 *  S2 datos en grilla con `Hijos` y `—` cuando el contrato no trae el dato;
 *  S4 documento con `Adjuntar` cuando falta;
 *  S5 un SOLO control plegable para la trazabilidad, sin el texto que el usuario pidió sacar.
 */
class ExpenseDetailExpedienteContractTest {

    private val uiDir: File by lazy {
        var candidate: File? = File("").absoluteFile
        while (candidate != null) {
            val dir = File(candidate, "src/commonMain/kotlin/com/parental/shared/feature/expenses/ui")
            if (dir.isDirectory) return@lazy dir
            candidate = candidate.parentFile
        }
        fail("No se encuentra `feature/expenses/ui` subiendo desde ${File("").absolutePath}")
    }

    private fun source(name: String): String = File(uiDir, name).readText()

    @Test
    fun `el encabezado muestra estado, categoria y el monto con la parte del viewer`() {
        val detail = source("ExpenseDetailScreen.kt")

        assertTrue(
            "StatusChip(status = expense.status)" in detail,
            "🔴 S1: el encabezado muestra el chip de estado.",
        )
        assertTrue(
            "CategoryChip(name = expense.categoryName)" in detail,
            "🔴 S1: el encabezado muestra la categoría.",
        )
        assertTrue(
            "viewerShare(expense, currentUserId)" in detail,
            "🔴 S1: `Tu parte` es la del viewer, no siempre la del solicitante.",
        )
        assertTrue(
            "Pagador \${expense.splitRatioPayer}%" in detail &&
                "Otro \${expense.splitRatioOther}%" in detail,
            "🔴 S1: la barra de división lleva sus dos leyendas.",
        )
    }

    @Test
    fun `la categoria del encabezado no lleva icono`() {
        val detail = source("ExpenseDetailScreen.kt")
        val chip = detail.substringAfter("private fun CategoryChip(")

        assertTrue(
            "emoji" !in chip && "drawCircle(" !in chip,
            "🔴 S1: la categoría va sólo con su nombre y color, sin ícono.",
        )
    }

    @Test
    fun `los datos muestran hijos con guion cuando el contrato no lo trae`() {
        val detail = source("ExpenseDetailScreen.kt")

        for (label in listOf("Fecha", "Solicitó", "Contraparte", "Hijos")) {
            assertTrue(
                "\"$label\"" in detail,
                "🔴 S2: falta el dato \"$label\" en la grilla.",
            )
        }
        assertTrue(
            "label = \"Hijos\",\n                value = \"—\"" in detail,
            "🔴 S2: sin hijos en el contrato se muestra `—`, nunca un nombre inventado.",
        )
    }

    @Test
    fun `el documento ofrece adjuntar con el icono de clip`() {
        val detail = source("ExpenseDetailScreen.kt")

        assertTrue(
            "Sin comprobante adjunto" in detail,
            "🔴 S4: el bloque de documento avisa cuando falta.",
        )
        assertTrue(
            "Icons.Default.AttachFile" in detail && "\"Adjuntar\"" in detail,
            "🔴 S4: el botón Adjuntar lleva el clip.",
        )
        assertTrue(
            "onAttach" in detail && "attachDocument" in File(uiDir, "ExpensesViewModel.kt").readText(),
            "🔴 S4: adjuntar está conectado al ViewModel.",
        )
    }

    @Test
    fun `la trazabilidad es un solo bloque plegable`() {
        val detail = source("ExpenseDetailScreen.kt")
        val timeline = detail.substringAfter("private fun TimelineCard(")

        assertTrue(
            "\"Trazabilidad\"" in timeline,
            "🔴 S5: el bloque se llama Trazabilidad.",
        )
        assertTrue(
            "expanded = !expanded" in timeline,
            "🔴 S5: el bloque entero se despliega, no cada paso.",
        )
        assertTrue(
            "expenseTimeline(expense)" in timeline,
            "🔴 S5: la línea temporal sale del dominio testeable.",
        )
        assertTrue(
            "Los próximos pasos aparecen" !in detail,
            "🔴 El texto que el usuario pidió sacar no se implementa.",
        )
    }

    @Test
    fun `las acciones salen de los flags del servidor`() {
        val detail = source("ExpenseDetailScreen.kt")

        for (flag in listOf("canApprove", "canReject", "canMarkPaid", "canEdit", "canCancel", "isTerminal")) {
            assertTrue(
                "expense.$flag" in detail,
                "🔴 S6: la acción de `$flag` no se pinta desde el flag del servidor.",
            )
        }
        assertTrue(
            "\"Solo la contraparte puede aprobar o rechazar" in detail,
            "🔴 S6: la ayuda del pie explica quién puede decidir.",
        )
    }
}
