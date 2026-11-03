package com.parental.shared.feature.home.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * T20 · CONTRATO DE FUENTE de las tres métricas del Home.
 *
 * ⚠ Es una prueba de FUENTE, y lo es a propósito: este repo no tiene
 * `createComposeRule` (ver la cabecera de `ActivityCardAccessibilityTest`), así que
 * no hay forma de componer la pantalla y leer los nodos. Leer el texto es más
 * honesto que fingir una prueba de UI que no existe.
 *
 * LO QUE DEFENDE:
 *
 *  1. Las TRES tarjetas con sus rótulos exactos: "Asignadas", "Saldo gastos" y
 *     "Vencidas".
 *  2. R1: el saldo se pinta CON signo y con color — verde si es positivo, rojo si es
 *     negativo. Un saldo con el signo escondido es el defecto que R1 vino a
 *     matar.
 *  3. Una métrica que no se pudo calcular se muestra como hueco, NUNCA como `0`:
 *     un 0 de mentira se lee como un dato.
 *  4. R11: los dos conteos se calculan en el cliente y el saldo NO — el saldo llega
 *     del servidor tal cual.
 */
class HomeMetricCardsContractTest {

    private val uiDir: File by lazy { homeUiDir() }

    private fun homeUiDir(): File {
        var candidate: File? = File("").absoluteFile
        while (candidate != null) {
            val dir = File(candidate, "src/commonMain/kotlin/com/parental/shared/feature/home/ui")
            if (dir.isDirectory) return dir
            candidate = candidate.parentFile
        }
        fail("No se encuentra `src/commonMain/kotlin/.../feature/home/ui` subiendo desde ${File("").absolutePath}")
    }

    private fun source(name: String): String = File(uiDir, name).readText()

    @Test
    fun `el Home pinta las tres metricas con sus rotulos`() {
        val screen = source("HomeScreen.kt")

        assertTrue(
            "Asignadas" in screen && "Saldo gastos" in screen && "Vencidas" in screen,
            "🔴 T20: el Home debe mostrar las tres métricas del PRD.",
        )
    }

    @Test
    fun `el saldo se pinta con signo y con color segun el signo`() {
        val screen = source("HomeScreen.kt")

        assertTrue(
            "formatMoney" in screen,
            "🔴 R1: el saldo se formatea con `formatMoney`, que conserva el signo " +
                "delante del símbolo.",
        )
        assertTrue(
            "StatusTokens.Status.Ok" in screen && "StatusTokens.Status.Ko" in screen,
            "🔴 R1: el saldo es verde si me deben y rojo si debo.",
        )
    }

    @Test
    fun `una metrica sin dato muestra un hueco y no un cero`() {
        val screen = source("HomeScreen.kt")

        assertTrue(
            "METRIC_UNAVAILABLE" in screen,
            "🔴 T20: sin dato se muestra un hueco, no un 0 — un 0 de mentira se lee " +
                "como un dato.",
        )
        assertTrue(
            "—" in screen,
            "🔴 T20: el hueco debe ser visible, no un 0.",
        )
    }

    @Test
    fun `el saldo viene del servidor y no se recalcula en el cliente`() {
        val api = source("../data/remote/HomeApi.kt")

        assertTrue(
            "summaryResult.data.saldo" in api,
            "🔴 R1: el saldo se toma del resumen del servidor (`saldo`), nunca se " +
                "recalcula en el cliente.",
        )
        assertTrue(
            "porPagar" !in api && "porCobrar" !in api,
            "🔴 R1: el cliente no puede derivar el saldo de los gastos: no debe " +
                "leer `porPagar`/`porCobrar` para la tarjeta del Home.",
        )
    }

    @Test
    fun `los dos conteos se calculan en el cliente sobre la lista del vinculo`() {
        val counts = source("../domain/HomeCounts.kt")

        assertTrue(
            "computeHomeCounts" in counts && "assignedTo" in counts,
            "🔴 R11: los conteos se derivan de la lista del vínculo en el cliente.",
        )
        assertTrue(
            "\"overdue\"" in counts,
            "🔴 R11: el conteo de vencidas mira el estado `overdue`.",
        )
    }
}
