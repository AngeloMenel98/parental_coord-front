package com.parental.shared.feature.home.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Contrato de FUENTE del rediseño "Hoy, con anillo" (S2, S3, S4, S5).
 *
 * Mismo motivo que [HomeMetricCardsContractTest]: el repo no tiene
 * `createComposeRule`, así que la UI se fija leyendo la fuente. Son las reglas que un
 * refactor no puede romper sin que nos enteremos:
 *
 *  - la tarjeta "Hoy" es UN objetivo táctil (sin chevrons ni botones anidados);
 *  - las tres celdas de la franja navegan a su pestaña;
 *  - el anillo usa las 12 constantes y la banda de status, no un color suelto;
 *  - la sección se llama "Hijos".
 */
class HomeRingAndSectionsContractTest {

    private val uiDir: File by lazy { homeUiDir() }

    private fun homeUiDir(): File {
        var candidate: File? = File("").absoluteFile
        while (candidate != null) {
            val dir = File(candidate, "src/commonMain/kotlin/com/parental/shared/feature/home/ui")
            if (dir.isDirectory) return dir
            candidate = candidate.parentFile
        }
        fail("No se encuentra `feature/home/ui` subiendo desde ${File("").absolutePath}")
    }

    private fun source(name: String): String = File(uiDir, name).readText()

    private fun domainSource(name: String): String =
        File(File(uiDir.parentFile, "domain"), name).readText()

    @Test
    fun `la seccion de hijos se llama Hijos`() {
        val screen = source("HomeScreen.kt")

        assertTrue(
            "\"Hijos\"" in screen,
            "🔴 S5: la sección se titula \"Hijos\".",
        )
        assertTrue(
            "Familiares" !in screen,
            "🔴 S5: \"Familiares\" ya no existe en el Home.",
        )
    }

    @Test
    fun `la tarjeta Hoy es un solo objetivo tactil`() {
        val screen = source("HomeScreen.kt")

        assertTrue(
            "Card(\n        onClick = onOpen" in screen || "onClick = onOpen" in screen,
            "🔴 S2: la tarjeta de hoy entera es el objetivo: `Card(onClick = …)`.",
        )
        assertTrue(
            "\"Hoy\"" in screen,
            "🔴 S2: la tarjeta se rotula \"Hoy\".",
        )
        assertTrue(
            "No se pudo cargar la agenda de hoy" in screen,
            "🔴 S2: sin agenda se dice, no se finge una lista vacía.",
        )
        assertTrue(
            "actividades más hoy" in screen,
            "🔴 S2: el resto de las actividades de hoy se anuncia como conteo.",
        )
    }

    @Test
    fun `las celdas de la franja navegan a su pestana`() {
        val screen = source("HomeScreen.kt")

        assertTrue(
            "onNavigateBottom(\"activities\")" in screen,
            "🔴 S3: Asignadas y Vencidas abren Actividades.",
        )
        assertTrue(
            "onNavigateBottom(\"expenses\")" in screen,
            "🔴 S3: el saldo abre Gastos.",
        )
        assertTrue(
            "onClick = onOpenActivities" in screen && "onClick = onOpenExpenses" in screen,
            "🔴 S3: cada celda es un botón, no una cifra decorativa.",
        )
    }

    @Test
    fun `el anillo usa los doce segmentos y la banda de status`() {
        val screen = source("HomeScreen.kt")
        val ring = domainSource("ComplianceRing.kt")

        assertTrue(
            "COMPLIANCE_RING_SEGMENTS" in screen,
            "🔴 S4: el anillo dibuja los 12 segmentos desde la constante.",
        )
        assertTrue(
            "complianceBand" in screen,
            "🔴 S4: el color del anillo sale de la banda, no de un literal.",
        )
        assertTrue(
            "StatusTokens.triple(band" in screen,
            "🔴 S4: la banda se resuelve a la tríada de status (dot + content).",
        )
        assertTrue(
            "const val COMPLIANCE_RING_SEGMENTS = 12" in ring,
            "🔴 S4: son 12 segmentos.",
        )
    }

    @Test
    fun `el interruptor sol luna vive en la barra del Inicio`() {
        val screen = source("HomeScreen.kt")
        val app = File(uiDir, "../../../App.kt").normalize().readText()

        assertTrue(
            "ThemeToggleButton(" in screen && "Icons.Filled.LightMode" in screen &&
                "Icons.Filled.DarkMode" in screen,
            "🔴 S8: la barra del Inicio expone el interruptor sol/luna.",
        )
        assertTrue(
            "onToggleTheme = onToggleTheme" in app && "ThemeController" in app,
            "🔴 S8: App resuelve el tema elegido y se lo pasa al Inicio.",
        )
        assertTrue(
            "Activar tema oscuro" in screen && "Activar tema claro" in screen,
            "🔴 S8: el botón anuncia la ACCIÓN, no el estado.",
        )
    }

    @Test
    fun `el encabezado del cumplimiento no repite el conteo del anillo`() {
        val screen = source("HomeScreen.kt")

        assertTrue(
            "compliancePeriodLabel(period = compliance.period)" in screen,
            "🔴 S4: el encabezado recibe sólo el período.",
        )
        // El formato que producía el duplicado en el encabezado: `"$name · $completed de
        // $total tareas"`. La descripción accesible del anillo puede (y debe) decir el
        // conteo; lo que no vuelve es el texto visible repetido.
        assertTrue(
            "\$name · \$completed de \$total tareas" !in screen,
            "🔴 S4: el conteo aparece una sola vez, dentro del anillo.",
        )
    }

    @Test
    fun `la agenda de hoy no pide un request propio`() {
        val api = source("../data/remote/HomeApi.kt")

        assertTrue(
            "selectTodayAgenda" in api && "selectChildStatuses" in api,
            "🔴 S2/S5: la agenda y el estado por hijo se derivan de la lista del vínculo.",
        )
        assertTrue(
            "activitiesApi.listActividades" in api,
            "🔴 S2: la lista se reusa; no hay una segunda llamada de actividades.",
        )
    }
}
