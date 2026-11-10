package com.parental.shared.feature.expenses.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * S4/S5 · CONTRATO DE FUENTE de la pantalla de gastos (T29 + T19).
 *
 * ⚠ Es una prueba de FUENTE, y lo es a propósito: este repo no tiene `androidTest` ni
 * `createComposeRule` (ver la cabecera de `ActivityCardAccessibilityTest`), así que no
 * hay forma de componer la pantalla y contar nodos. Leer el texto es más honesto que
 * fingir una prueba de UI que no existe.
 *
 * LO QUE DEFENDE, y por qué cada cosa importa:
 *
 *  1. Las CUATRO tarjetas de PRD_expense §5.1 + R1: los dos saldos ("Por pagar",
 *     "Por cobrar") y los dos conteos ("Pendientes", "Pagados"). Faltaba "Pagados":
 *     sin él no había forma de filtrar la lista por los gastos saldados.
 *  2. Las tarjetas de SALDO no filtran: un saldo no es un status. La versión
 *     anterior mapeaba "Por cobrar" al status `paid` — o sea, tocar la tarjeta de
 *     "lo que me deben" mostraba los gastos YA saldados. El signo ya dice quién
 *     le debe a quién.
 *  3. Los botones del detalle se pintan a partir de los flags del SERVIDOR
 *     (`canApprove`, `canReject`, `canMarkPaid`, `canEdit`, `canCancel`): el
 *     cliente no reimplementa FR-11.
 *  4. El PATCH se arma CAMPO POR CAMPO. El cliente Ktor serializa los `null`
 *     (tiene `explicitNulls` activo), y el backend los lee como "poner en null"
 *     contra columnas NOT NULL — y rechaza con 400 cualquier propiedad que no
 *     exista en su DTO (`forbidNonWhitelisted`), como `splitRatioOther`, que
 *     además DERIVA el servidor. Serializar la data class rompía la edición
 *     siempre.
 */
class ExpensesScreenContractTest {

    private val uiDir: File by lazy { expensesUiDir() }

    private fun expensesUiDir(): File {
        var candidate: File? = File("").absoluteFile
        while (candidate != null) {
            val dir = File(candidate, "src/commonMain/kotlin/com/parental/shared/feature/expenses/ui")
            if (dir.isDirectory) return dir
            candidate = candidate.parentFile
        }
        fail("No se encuentra `src/commonMain/kotlin/.../feature/expenses/ui` subiendo desde ${File("").absolutePath}")
    }

    private fun source(name: String): String = File(uiDir, name).readText()

    private fun moduleFile(relative: String): String =
        requireNotNull(uiDir.parentFile?.parentFile?.parentFile)
            .resolve(relative)
            .readText()

    // ── 1 · Las cuatro tarjetas ─────────────────────────────────────────────────────

    @Test
    fun `la pantalla declara las cuatro tarjetas de resumen`() {
        val screen = source("ExpensesScreen.kt")

        for (label in listOf("Por pagar", "Por cobrar", "Pendientes", "Pagados")) {
            assertTrue(
                "\"$label\"" in screen,
                "🔴 Falta la tarjeta \"$label\": PRD_expense §5.1 pide los dos saldos y " +
                    "los dos conteos (y R1 agrega \"Por cobrar\").",
            )
        }
    }

    @Test
    fun `los conteos de la cabecera salen del resumen del servidor`() {
        val screen = source("ExpensesScreen.kt")

        assertTrue(
            "summary.counts.pendientes" in screen,
            "El conteo de pendientes sale del endpoint de resumen: el cliente no recalcula " +
                "números de dinero ni de conteos (R1).",
        )
        assertTrue(
            "summary.counts.pagados" in screen,
            "El conteo de pagados sale del endpoint de resumen, igual que el de pendientes.",
        )
        assertTrue(
            "formatMoney(summary.porPagar)" in screen && "formatMoney(summary.porCobrar)" in screen,
            "Los dos saldos se formatean con `formatMoney` (FR-6): un solo formato de " +
                "moneda en toda la app.",
        )
    }

    // ── 2 · Las tarjetas de saldo no son filtros ───────────────────────────────────

    @Test
    fun `las tarjetas de saldo no filtran la lista`() {
        val screen = source("ExpensesScreen.kt")
        val balanceCards = screen.split("label = \"Por ").drop(1)

        assertTrue(
            balanceCards.size >= 2,
            "Se esperaban las dos tarjetas de saldo (Por pagar / Por cobrar).",
        )
        for (card in balanceCards) {
            val block = card.substringBefore("SummaryCard(").ifEmpty {
                card.substringAfter("SummaryCard(")
            }
            assertTrue(
                "onClick = null" in block,
                "🔴 Una tarjeta de saldo está mapeada a un status. Un saldo no es un " +
                    "status: la versión anterior mapeaba \"Por cobrar\" a `paid`, o sea " +
                    "que tocar la tarjeta de \"lo que me deben\" mostraba los gastos YA " +
                    "saldados.",
            )
        }
    }

    @Test
    fun `las tarjetas de conteo filtran por su status`() {
        val screen = source("ExpensesScreen.kt")

        assertTrue(
            "ExpenseStatus.PENDING" in screen,
            "La tarjeta \"Pendientes\" filtra por el status `pending` (FR-2).",
        )
        assertTrue(
            "ExpenseStatus.PAID" in screen,
            "La tarjeta \"Pagados\" filtra por el status `paid` (FR-2).",
        )
    }

    // ── 3 · El detalle se pinta con los flags del servidor ─────────────────────────

    @Test
    fun `los botones del detalle dependen de los flags del servidor`() {
        val detail = source("ExpenseDetailScreen.kt")

        for (flag in listOf("canApprove", "canReject", "canMarkPaid", "canEdit", "canCancel")) {
            assertTrue(
                "expense.$flag" in detail,
                "🔴 El botón de `$flag` no se pinta desde el flag del servidor: el cliente " +
                    "no puede reimplementar FR-11 (y el servidor devuelve 403 cuando se " +
                    "equivoca).",
            )
        }
    }

    @Test
    fun `el detalle muestra los NOMBRES de las personas, no sus ids`() {
        val detail = source("ExpenseDetailScreen.kt")

        // Un UUID en pantalla no le dice nada a nadie: el nombre viene del servidor
        // (que ya resuelve el de la categoría) y el id es sólo el plan B.
        assertTrue(
            "expense.requestedByName" in detail,
            "El detalle tiene que pintar el NOMBRE de quien pidió el gasto, con el id " +
                "como plan B cuando no hay perfil.",
        )
        assertTrue(
            "expense.counterpartName" in detail,
            "El detalle tiene que pintar el NOMBRE de la contraparte.",
        )
        assertTrue(
            "expense.approvedByName" in detail,
            "El detalle tiene que pintar el NOMBRE de quien aprobó.",
        )
        assertTrue(
            "expense.requestedBy\n" !in detail && "value = expense.requestedBy)" !in detail,
            "El detalle NO puede pintar el id crudo como si fuera un nombre.",
        )
    }

    @Test
    fun `la fila avisa cuando el gasto no tiene documento`() {
        val row = source("ExpenseRow.kt")

        assertTrue(
            "hasDocument" in row,
            "La fila tiene que mirar `hasDocument`: es el flag DERIVADO (no hay fila en " +
                "`expense_attachment`) que PRD_expense FR-9 pide pintar como \"sin doc.\".",
        )
        assertTrue(
            "sin doc." in row,
            "El aviso de documento faltante dice \"sin doc.\": es el texto del PRD.",
        )
    }

    // ── 4 · El PATCH se arma campo por campo ───────────────────────────────────────

    @Test
    fun `el PATCH manda solo los campos editados y ninguno desconocido`() {
        val api = moduleFile(
            "feature/expenses/data/remote/ExpensesApi.kt",
        )

        assertTrue(
            "buildJsonObject" in api,
            "🔴 El PATCH se arma con `buildJsonObject`, campo por campo. Serializar la " +
                "data class manda `null` en todo lo que el usuario no tocó (el cliente " +
                "Ktor tiene `explicitNulls` activo) y el backend lo lee como \"poner en " +
                "null\" contra columnas NOT NULL.",
        )
        assertTrue(
            "splitRatioOther" !in api,
            "🔴 El PATCH manda `splitRatioOther`. El backend lo DERIVA de " +
                "`splitRatioPayer` y rechaza con 400 cualquier propiedad que no exista " +
                "en su DTO (`forbidNonWhitelisted`): la edición fallaría siempre.",
        )
        assertTrue(
            "setBody(body)" in api,
            "El cuerpo del PATCH es el JSON armado a mano, no la data class.",
        )
    }

    @Test
    fun `el modelo de edicion no expone la contraparte del reparto`() {
        val models = moduleFile("feature/expenses/domain/model/ExpenseModels.kt")
        // El MODELO DE RESPUESTA sí lleva `splitRatioOther` (es dato del servidor);
        // lo que no puede existir es el campo en el modelo de EDICIÓN.
        val request = models.substringAfter("class ExpenseUpdateRequest(")
            .substringBefore(")")

        assertTrue(
            "splitRatioOther" !in request,
            "🔴 `ExpenseUpdateRequest` volvió a exponer `splitRatioOther`: la contraparte " +
                "la deriva el servidor y mandarla es un 400.",
        )
    }

    // ── 5 · Alta de gasto: affordance, campos y advertencia sin documento ───────────

    @Test
    fun `la pantalla tiene un affordance para abrir el formulario de alta`() {
        val screen = source("ExpensesScreen.kt")

        assertTrue(
            "CreateExpenseSheet(" in screen,
            "🔴 La pantalla no compone `CreateExpenseSheet`: el formulario de alta no está " +
                "en la superficie.",
        )
        assertTrue(
            "openCreateSheet" in screen,
            "🔴 La pantalla no llama al método que abre la hoja de alta.",
        )
    }

    @Test
    fun `el formulario de alta incluye los campos del PRD`() {
        val sheet = source("CreateExpenseSheet.kt")

        for (label in listOf("Concepto", "Monto", "Categoría", "Fecha del gasto", "Notas")) {
            assertTrue(
                "\"$label\"" in sheet,
                "🔴 Falta el campo \"$label\" del PRD_expense §5.3.",
            )
        }
    }

    @Test
    fun `el formulario de alta muestra la division del gasto`() {
        val sheet = source("CreateExpenseSheet.kt")

        assertTrue(
            "splitRatioPayer" in sheet,
            "🔴 El formulario no expone la división del gasto (`splitRatioPayer`).",
        )
        assertTrue(
            "splitRatioOther" !in sheet,
            "🔴 El formulario no debe enviar la contraparte: el backend la deriva.",
        )
    }

    @Test
    fun `el formulario advierte que el gasto se creara sin documento`() {
        val sheet = source("CreateExpenseSheet.kt")

        assertTrue(
            "sin documento" in sheet || "sin doc" in sheet,
            "🔴 El formulario no advierte que el gasto se creará sin documento de respaldo " +
                "(FR-9).",
        )
    }

    @Test
    fun `el formulario no abre un selector propio para subir archivos`() {
        val sheet = source("CreateExpenseSheet.kt")

        assertTrue(
            "presign" !in sheet && "rememberFilePicker" !in sheet && "picker.launch" !in sheet,
            "🔴 T30 recoge el comprobante en la actividad; el formulario de gastos no debe " +
                "abrir un segundo selector ni inventar otra ruta de subida.",
        )
    }

    @Test
    fun `el POST de alta se arma campo por campo`() {
        val api = moduleFile("feature/expenses/data/remote/ExpensesApi.kt")

        assertTrue(
            "CreateExpenseRequest" in api && "toCreateBody" in api,
            "🔴 El POST de alta debe armar su cuerpo campo por campo, igual que el PATCH.",
        )
        assertTrue(
            "childId" !in api,
            "🔴 El POST de alta no debe mandar `childId`: el backend rechaza propiedades " +
                "desconocidas con 400.",
        )
        assertTrue(
            "activityId" !in api,
            "🔴 El POST de alta no debe mandar `activityId`: el backend rechaza propiedades " +
                "desconocidas con 400.",
        )
    }
}
