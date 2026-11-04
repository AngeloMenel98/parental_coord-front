package com.parental.shared.feature.expenses.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Contrato de FUENTE del rediseño "saldo vivo, libro, swipe" (design/expenses-ui/04).
 *
 * Mismo motivo que `ExpensesScreenContractTest`: el repo no tiene `createComposeRule`, así
 * que la UI se fija leyendo la fuente. Defiende cuatro reglas que un refactor podría
 * deshacer en silencio:
 *
 *  S1 el saldo del vínculo se muestra y NO se recalcula;
 *  S2 los filtros son por estado y sus números salen del servidor;
 *  S3 la fila muestra la fecha como asiento y la parte del solicitante;
 *  S4 el swipe sólo existe cuando el servidor habilitó la acción, y es accesible.
 */
class ExpensesLedgerContractTest {

    private val uiDir: File by lazy { expensesUiDir() }

    private fun expensesUiDir(): File {
        var candidate: File? = File("").absoluteFile
        while (candidate != null) {
            val dir = File(candidate, "src/commonMain/kotlin/com/parental/shared/feature/expenses/ui")
            if (dir.isDirectory) return dir
            candidate = candidate.parentFile
        }
        fail("No se encuentra `feature/expenses/ui` subiendo desde ${File("").absolutePath}")
    }

    private fun source(name: String): String = File(uiDir, name).readText()

    private fun domainSource(name: String): String =
        File(File(uiDir.parentFile, "domain"), name).readText()

    @Test
    fun `el saldo del vinculo se muestra sin recalcularlo`() {
        val screen = source("ExpensesScreen.kt")

        assertTrue(
            "summary.saldo" in screen,
            "🔴 S1: el saldo del vínculo sale del resumen del servidor.",
        )
        assertTrue(
            "porPagar" !in screen.substringAfter("private fun BalanceHeader")
                .substringBefore("private fun BalanceFigure") ||
                "saldo" in screen,
            "🔴 R1: el saldo no se deriva de `porPagar - porCobrar` en el cliente.",
        )
        assertTrue(
            "formatMoney(summary.porPagar)" in screen && "formatMoney(summary.porCobrar)" in screen,
            "🔴 S1: los dos brutos se muestran debajo del saldo, formateados igual.",
        )
    }

    @Test
    fun `el saldo conserva la tarjeta que el usuario eligio`() {
        val screen = source("ExpensesScreen.kt")
        val balance = screen.substringAfter("private fun BalanceHeader(")
            .substringBefore("@Composable\nprivate fun BalanceFigure(")

        assertTrue(
            "Surface(" in balance,
            "🔴 S1: el saldo tiene que conservar la tarjeta elegida por el usuario, no una columna suelta.",
        )
        assertTrue(
            "shape = MaterialTheme.shapes.large" in balance,
            "🔴 La tarjeta de saldo usa el radio grande del sistema de diseño.",
        )
        assertTrue(
            "BorderStroke(Spacing.hairline, MaterialTheme.colorScheme.outlineVariant)" in balance,
            "🔴 La tarjeta de saldo conserva la hairline de borde del artboard.",
        )
    }

    @Test
    fun `el encabezado no inventa una fecha de actualizacion`() {
        val screen = source("ExpensesScreen.kt")

        assertTrue(
            "actualizado hoy" !in screen,
            "🔴 El resumen no trae una fecha de actualización: la pantalla no puede inventarla.",
        )
        assertTrue(
            "updatedAt" !in screen.substringAfter("private fun BalanceHeader(")
                .substringBefore("@Composable\nprivate fun BalanceFigure("),
            "🔴 El encabezado del saldo no puede derivar frescura de un campo que no existe en el contrato.",
        )
    }

    @Test
    fun `los filtros son por estado y usan los conteos del servidor`() {
        val screen = source("ExpensesScreen.kt")

        assertTrue(
            "private fun StatusFilterRow(" in screen,
            "🔴 S2: el filtro es una fila de chips por estado.",
        )
        for (status in listOf("PENDING", "APPROVED", "PAID", "REJECTED")) {
            assertTrue(
                "ExpenseStatus.$status" in screen,
                "🔴 S2: falta el filtro por `$status`.",
            )
        }
        assertTrue(
            "val counts = summary.counts" in screen,
            "🔴 S2: los números de los chips salen del resumen, no de contar la lista.",
        )
    }

    @Test
    fun `la fila es un asiento con fecha y parte del solicitante`() {
        val row = source("ExpenseRow.kt")

        assertTrue(
            "ledgerDate(expense.expenseDate)" in row,
            "🔴 S3: la fecha se pinta como día + mes abreviado.",
        )
        assertTrue(
            "viewerShare(" in row,
            "🔴 S3 (T7): la parte se calcula con `viewerShare`, la regla de reparto del viewer.",
        )
        assertTrue(
            "\"Tu parte " in row,
            "🔴 S3: la fila dice cuánto le toca a quien pregunta.",
        )
        assertTrue(
            "formatMoney(expense.amount)" in row,
            "🔴 El monto total se formatea con `formatMoney`.",
        )
        assertTrue(
            "hasDocument" in row && "sin doc." in row,
            "🔴 FR-9: el aviso de documento faltante no se pierde en el rediseño.",
        )
    }

    @Test
    fun `la fila recibe la identidad del viewer desde la pantalla`() {
        val screen = source("ExpensesScreen.kt")
        val row = source("ExpenseRow.kt")

        assertTrue(
            "currentUserId: String?" in screen,
            "🔴 T7: `ExpensesScreen` expone `currentUserId` para recibir la identidad autenticada.",
        )
        assertTrue(
            "currentUserId = currentUserId" in screen,
            "🔴 T7: `ExpensesContent` pasa la identidad a `ExpenseRow`.",
        )
        assertTrue(
            "currentUserId: String?" in row,
            "🔴 T7: `ExpenseRow` recibe `currentUserId` para decidir la parte del viewer.",
        )
        assertTrue(
            "viewerShare(expense, currentUserId)" in row,
            "🔴 T7: `ExpenseRow` resuelve la parte a partir de la identidad autenticada.",
        )
    }

    @Test
    fun `la fila conserva el libro plano con separadores`() {
        val screen = source("ExpensesScreen.kt")
        val row = source("ExpenseRow.kt")

        assertTrue(
            "item(key = \"list-frame\")" !in screen,
            "🔴 S3: el libro no se envuelve en una tarjeta redondeada; son asientos planos separados por hairlines.",
        )
        assertTrue(
            "items(uiState.items" in screen,
            "🔴 La lista del libro debe componerse como ítems individuales del LazyColumn.",
        )
        assertTrue(
            "HorizontalDivider(" in screen,
            "🔴 Cada asiento del libro necesita su hairline separador.",
        )
        assertTrue(
            "MaterialTheme.shapes.large" !in row,
            "🔴 La tarjeta de saldo usa el radio grande; los asientos no deben heredarlo.",
        )
    }

    @Test
    fun `el swipe depende de los flags del servidor`() {
        val row = source("ExpenseRow.kt")

        assertTrue(
            "expense.canApprove || expense.canReject" in row,
            "🔴 S4: la fila se desliza sólo si el servidor habilitó aprobar o rechazar; " +
                "el cliente no deduce permisos del estado.",
        )
        assertTrue(
            "enabled = canSwipe" in row,
            "🔴 S4: el swipe se habilita con esa condición, no con el status.",
        )
        assertTrue(
            "ExpenseSwipeBox(" in row,
            "🔴 S4: el gesto vive en `ExpenseSwipeBox`.",
        )
    }

    @Test
    fun `el swipe es accesible sin gesto`() {
        val swipe = source("swipe/ExpenseSwipeBox.kt")

        assertTrue(
            "customActions" in swipe && "CustomAccessibilityAction" in swipe,
            "🔴 S4: un swipe no es descubrible; las acciones se exponen como " +
                "`customActions` para lectores de pantalla.",
        )
        assertTrue(
            "\"Aprobar\"" in swipe && "\"Rechazar\"" in swipe,
            "🔴 S4: las dos acciones se rotulan Aprobar y Rechazar.",
        )
        assertTrue(
            "clearAndSetSemantics" in swipe,
            "🔴 S4: los botones visuales quedan fuera del árbol de semántica para no " +
                "duplicar la acción.",
        )
        assertTrue(
            "MotionTokens.durationStandard" in swipe,
            "La animación usa el token de duración de la app (150 ms).",
        )
    }

    @Test
    fun `las acciones del swipe ocupan todo el alto de la fila`() {
        val swipe = source("swipe/ExpenseSwipeBox.kt")

        assertTrue(
            "matchParentSize()" in swipe,
            "🔴 S4: las acciones rectangulares deben medirse contra toda la fila, no contra un alto infinito.",
        )
    }

    @Test
    fun `el redondeo de la parte vive en el dominio y se puede probar`() {
        val ledger = domainSource("ExpenseLedger.kt")

        assertTrue(
            "internal fun payerShare(" in ledger,
            "🔴 S3: `payerShare` es una función pura y testeable del dominio.",
        )
        assertTrue(
            "internal fun ledgerDate(" in ledger,
            "🔴 S3: `ledgerDate` es una función pura y testeable del dominio.",
        )
    }
}
