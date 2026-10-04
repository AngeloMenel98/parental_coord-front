package com.parental.shared.feature.activities.ui.swipe

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * R2-T2-7 · **DÓNDE ESTÁ MONTADO EL GESTO**, no cómo se comporta.
 *
 * El cambio llegó a `sdd-verify` con 46 pruebas de swipe en verde y el gesto montado en la
 * fila equivocada: `ActivitySwipeBox` era el parámetro `swipe` de `DayAgendaRow`, así que
 * sólo las filas CON hora de un único día se deslizaban. La lista (`TimelineMode.AGENDA`) y
 * las filas SIN fecha de inicio no lo montaban, y nadie se enteró porque:
 *
 *  1. Las pruebas de `ActivitySwipeBox` la ejercitaban en AISLAMIENTO — nunca preguntaban
 *     quién la monta, que es la única pregunta que tenía respuesta incorrecta.
 *  2. El hueco llevaba VALOR POR DEFECTO (`= { c -> c() }`). Una superficie que no lo monta
 *     no es un error de compilación: es un `c()` callado.
 *
 * Aquí se vigila la segunda causa, y la primera se vigila en el grafo de llamadas de
 * verdad: se leen los `.kt` de producción y se comprueba que TODA superficie de fila que
 * debería deslizar pasa el hueco, y que la fila de día ya NO lo declara.
 *
 * ⚠ Es una prueba de FUENTE, y lo es a propósito: este repo no tiene `androidTest` ni
 * `createComposeRule`, así que no hay forma de contar los montajes en el árbol de
 * composición. Ante eso, leer el texto es más honesto que fingir una prueba de UI.
 * Mismo criterio que los guards de Batch E, que también se comprueban por estructura.
 *
 * Si algún día hay infraestructura de Compose UI, esta prueba debe SUPERSEDERSE por una que
 * componga `TimelineMode.AGENDA` y `TimelineMode.SEMANA` y cuente los nodos con
 * `customActions`. No coexistir: dos fuentes de verdad sobre el mismo guard es como vuelve
 * a romperse.
 */
class SwipeMountingTest {

    // ── Utilidades de lectura de fuente ───────────────────────────────────────────────

    private val uiDir: File by lazy { activitiesUiDir() }

    private fun activitiesUiDir(): File {
        // El `workingDir` por defecto de una tarea Gradle `Test` es el proyecto (aquí
        // `shared/`), pero se sube de directorio hasta encontrar el árbol de `commonMain`
        // para que la prueba no dependa de dónde se invoque.
        var candidate: File? = File("").absoluteFile
        while (candidate != null) {
            val dir = File(candidate, "src/commonMain/kotlin/com/parental/shared/feature/activities/ui")
            if (dir.isDirectory) return dir
            candidate = candidate.parentFile
        }
        fail(
            "No se encuentra `src/commonMain/kotlin/.../activities/ui` subiendo desde " +
                "${File("").absolutePath}. El guard de montaje no se puede evaluar en silencio.",
        )
    }

    private fun source(name: String): String = File(uiDir, name).readText()

    /** Toda la UI de actividades, para el grafo de llamadas (ficheros planos y subcarpetas). */
    private fun uiSources(): List<File> =
        uiDir.walkTopDown().filter { it.isFile && it.name.endsWith(".kt") }.toList()

    /**
     * Sustituye comentarios y literales por ESPACIOS, conservando longitudes y saltos de
     * línea. Sin esto, el equilibrio de paréntesis se rompe con la prosa en castellano — que está
     * llena de `(R2-T2-7)`, `(*)` y `"""` — y la extracción de argumentos devuelve basura.
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
                            repeat(close.length) { out.append(' '); i++ }
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

    private data class CallSite(val file: String, val callee: String, val arguments: String)

    /** Todas las invocaciones de [callee], con su bloque de argumentos, sin la declaración. */
    private fun callSites(callee: String, files: List<File> = uiSources()): List<CallSite> {
        val sites = mutableListOf<CallSite>()
        for (file in files) {
            val code = stripCommentsAndStrings(file.readText())
            var i = 0
            while (true) {
                val at = code.indexOf("$callee(", i)
                if (at < 0) break
                val isDeclaration = code.substring(0, at).trimEnd().endsWith("fun")
                // `open` es el índice del `(`; el bloque empieza DESPUÉS de él, con depth 1.
                val open = at + callee.length
                var j = open + 1
                var depth = 1
                while (j < code.length && depth > 0) {
                    if (code[j] == '(') depth++
                    if (code[j] == ')') depth--
                    j++
                }
                if (!isDeclaration) {
                    sites += CallSite(file.name, callee, code.substring(open + 1, j - 1))
                }
                i = j
            }
        }
        return sites
    }

    /** Los parámetros declarados por una función, tal cual aparecen (con su default). */
    private fun declaredParameters(text: String, function: String): String {
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
        return code.substring(at, j)
    }

    // ── 1 · La fila de día NO declara el hueco ──────────────────────────────────────────

    @Test
    fun `DayAgendaRow vuelve a su firma de 67a11a7 — el hueco no es suyo`() {
        val firma = declaredParameters(source("DayAgenda.kt"), "DayAgendaRow")
        assertFalse(
            "swipe" in firma,
            "`DayAgendaRow` vuelve a declarar el hueco `swipe`. Era la fila equivocada: sólo " +
                "pinta las filas con hora de un día, y su hueco con valor por defecto dejaba " +
                "sin gesto, en silencio, a las otras dos superficies. Si el gesto se monta " +
                "ahora sobre su llamada, el envoltorio va en `WeekAgenda.kt`.",
        )
    }

    @Test
    fun `el fichero DayAgenda no menciona el swipe en absoluto`() {
        assertFalse(
            "swipe" in source("DayAgenda.kt"),
            "`DayAgenda.kt` es fichero de Batch E (RR-4) y no debe oír hablar de gestos: " +
                "diseño S7 lo quiere libre de eso.",
        )
    }

    // ── 2 · La fila de timeline lo declara, y SIN valor por defecto ─────────────────────

    @Test
    fun `AgendaTimelineRow declara el hueco swipe`() {
        val firma = declaredParameters(source("AgendaTimelineRow.kt"), "AgendaTimelineRow")
        assertTrue(
            "swipe: @Composable (content: @Composable () -> Unit) -> Unit" in firma,
            "`AgendaTimelineRow` debe declarar el hueco con el tipo exacto de ranura. " +
                "Firma real:\n$firma",
        )
    }

    @Test
    fun `el hueco NO lleva valor por defecto — omitirlo es error de compilacion`() {
        val firma = declaredParameters(source("AgendaTimelineRow.kt"), "AgendaTimelineRow")
        val linea = firma.lines().firstOrNull { "swipe:" in it }
        assertTrue(linea != null, "No se encuentra el parámetro `swipe` en:\n$firma")
        assertFalse(
            "=" in linea,
            "🔴 `swipe` vuelve a llevar valor por defecto. ESO ES EL DEFECTO: un hueco con " +
                "default convierte «no monté el gesto» en un `c()` silencioso, que es " +
                "justo como pasaron 46 pruebas de verde con dos superficies sin swipe.\n" +
                "Firma real:\n$firma",
        )
    }

    // ── 3 · TODA superficie de fila deslizable pasa el hueco ────────────────────────────

    @Test
    fun `toda llamada a AgendaTimelineRow monta el gesto`() {
        val sites = callSites("AgendaTimelineRow")
        assertTrue(sites.isNotEmpty(), "No se encontró ninguna llamada a `AgendaTimelineRow`")
        sites.forEach { site ->
            assertTrue(
                "swipe =" in site.arguments,
                "🔴 `${site.callee}()` en ${site.file} NO monta el gesto (falta `swipe =`). " +
                    "Esa superficie se queda sin swipe aunque todo lo demás compile.",
            )
        }
    }

    @Test
    fun `toda llamada a DayAgendaRow queda ENVUELTA por ActivitySwipeBox`() {
        val sites = callSites("DayAgendaRow")
        assertTrue(sites.isNotEmpty(), "No se encontró ninguna llamada a `DayAgendaRow`")
        sites.forEach { site ->
            assertFalse(
                "swipe =" in site.arguments,
                "🔴 `DayAgendaRow()` en ${site.file} vuelve a recibir el hueco. El envoltorio " +
                    "va POR FUERA de la llamada, en el punto de render.",
            )
        }
    }

    // ── 4 · Las TRES superficies, una por una ───────────────────────────────────────────

    @Test
    fun `superficie 1 — agenda de dia con hora — el envoltorio envuelve la llamada a DayAgendaRow`() {
        // Se comprueba la FORMA, no la cuenta: la llamada tiene que quedar dentro del
        // bloque `content` de un `ActivitySwipeBox(`. Con un `count` sólo se probaría que
        // hay un `ActivitySwipeBox` en el fichero.
        val week = stripCommentsAndStrings(source("WeekAgenda.kt"))
        val wrapper = week.indexOf("ActivitySwipeBox(")
        assertTrue(wrapper >= 0, "`WeekAgenda.kt` ya no monta `ActivitySwipeBox`")
        val dayRow = week.indexOf("DayAgendaRow(")
        assertTrue(dayRow >= 0, "`WeekAgenda.kt` ya no llama a `DayAgendaRow`")
        assertTrue(
            dayRow > wrapper,
            "🔴 `DayAgendaRow()` se pinta FUERA del envoltorio de swipe: el gesto se " +
                "desplazaría sólo sobre una parte de la fila, o directamente no se montaría.",
        )
        assertTrue(
            week.indexOf("onSwipeAction(", wrapper).let { it in wrapper..dayRow },
            "El envoltorio de la fila con hora no recibe `onSwipeAction`: no podría disparar " +
                "ninguna acción.",
        )
    }

    @Test
    fun `superficie 2 — filas SIN fecha de inicio en la agenda de semana — montan el gesto`() {
        val sites = callSites("AgendaTimelineRow", listOf(File(uiDir, "WeekAgenda.kt")))
        assertEquals(1, sites.size, "Se esperaba exactamente un sitio de fila sin fecha")
        assertTrue("swipe =" in sites.single().arguments, "La fila sin fecha no monta el gesto")
        assertTrue("ActivitySwipeBox(" in sites.single().arguments, "El hueco no crea la capa de gesto")
    }

    @Test
    fun `superficie 3 — modo LISTA — AgendaTimeline monta el gesto y la pantalla se lo pasa`() {
        val list = callSites("AgendaTimelineRow", listOf(File(uiDir, "AgendaList.kt")))
        assertEquals(1, list.size, "Se esperaba exactamente un sitio de fila en la lista")
        assertTrue("swipe =" in list.single().arguments, "La fila de la lista no monta el gesto")
        assertTrue("ActivitySwipeBox(" in list.single().arguments, "El hueco no crea la capa de gesto")

        // Y el cable de arriba: sin esto el gesto se pintaría pero no dispararía nada.
        val agendaTimeline = callSites("AgendaTimeline")
        assertEquals(
            1, agendaTimeline.size,
            "Se esperaba un único llamante de `AgendaTimeline` en toda la UI (la pantalla). " +
                "Varios significa que alguna superficie se dibujaría sin el callback.",
        )
        assertEquals("ActivitiesScreen.kt", agendaTimeline.single().file)
        val firma = declaredParameters(source("AgendaList.kt"), "AgendaTimeline")
        val linea = firma.lines().firstOrNull { "onSwipeAction:" in it }
        assertTrue(linea != null, "`AgendaTimeline` no declara `onSwipeAction`:\n$firma")
        assertFalse(
            "=" in linea,
            "🔴 `AgendaTimeline.onSwipeAction` lleva valor por defecto. En una SUPERFICIE eso " +
                "repite el defecto del hueco: si un llamante nuevo se olvida de pasarlo, la " +
                "lista se dibuja sin gesto y compila.",
        )
        val screen = callSites("AgendaTimeline", listOf(File(uiDir, "ActivitiesScreen.kt")))
        assertEquals(1, screen.size, "Se esperaba un único llamante de `AgendaTimeline` en la pantalla")
        assertTrue(
            "onSwipeAction = onSwipeAction" in screen.single().arguments,
            "`ActivitiesScreen.kt` no le pasa su `onSwipeAction` a `AgendaTimeline`: el modo " +
                "LISTA se desliza y no hace nada.",
        )
    }

    // ── 5 · La capa de swipe se monta, no se dibuja ─────────────────────────────────────

    @Test
    fun `ActivitySwipeBox sigue siendo un ENVOLTORIO DE RANURA y no un painter`() {
        val text = source("swipe/ActivitySwipeBox.kt")
        val code = stripCommentsAndStrings(text)
        assertTrue("content: @Composable () -> Unit" in code, "Ya no recibe `content`: entonces dibujaría la fila.")
        // Si dibujara la tarjeta, el `content` se ejecutaría una sola vez pero habría que
        // volverle a pasar los datos de la fila; la forma buena es la ranura.
        assertTrue(
            "content()" in code,
            "`content()` no se invoca: la ranura está declarada pero no se ejecuta.",
        )
    }

    // ── 6 · El motivo de no asistencia tiene superficie ────────────────────────────────

    @Test
    fun `la hoja de detalle pinta el motivo de no asistir`() {
        val sheet = source("detail/ActividadDetailSheet.kt")
        assertTrue(
            "visibleDeclinedReason()" in stripCommentsAndStrings(sheet),
            "🔴 `declinedReason` vuelve a no pintarse en ninguna parte: el backend lo expone " +
                "a toda la actividad del vínculo (A3) y la UI lo esconde. Falta la mitad " +
                "visible del ruling.",
        )
        assertFalse(
            "createdBy" in stripCommentsAndStrings(sheet),
            "🔴 La hoja empieza a mirar `createdBy`. A3 lo SUPERSEDÓ: el motivo es para " +
                "toda la actividad del vínculo, no sólo para quien la creó.",
        )
    }

    @Test
    fun `declinedReason se lee de la actividad CARGADA — ni remember ni cache`() {
        val code = stripCommentsAndStrings(source("detail/ActividadDetailSheet.kt"))
        val usos = Regex("visibleDeclinedReason\\(").findAll(code).count()
        assertEquals(
            1, usos,
            "`visibleDeclinedReason()` debería leerse en la composición, sin memorizar. " +
                "Un `remember { }` alrededor del motivo lo sobreviviría a la actividad que " +
                "la trajo, que es exactamente lo que A3/A4 no quieren.",
        )
        assertFalse(
            "remember { visibleDeclinedReason" in code ||
                "remember(actividad" in code || "remember(a." in code,
            "El motivo de no asistir no debe memorizarse: vive y muere con el detalle cargado.",
        )
    }
}
