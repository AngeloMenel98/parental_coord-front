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
 * sólo las filas CON hora de un único día se deslizaban. La lista y las filas SIN fecha de
 * inicio no lo montaban, y nadie se enteró porque:
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
 * componga el riel del tiempo y cuente los nodos con `customActions`. No coexistir: dos fuentes
 * de verdad sobre el mismo guard es como vuelve a romperse.
 *
 * T6 · La pantalla cambió de forma (L6: el riel reemplaza Agenda y Semana). El guard NO cambió de
 * propósito: donde antes vigilaba el cable `pantalla → AgendaTimeline`, hoy vigila que la agenda
 * no vuelva a montarse y que las acciones del gesto sigan teniendo entrada mientras el riel no
 * monte su propia capa.
 *
 * T7 (L11) · El riel YA monta su capa de swipe, así que la sección `superficie 3` dejó de vigilar
 * "la agenda no vuelve" a secas y ahora PINEA la superficie nueva con la misma vara: el bloque
 * del riel declara el hueco sin default y el punto de render lo envuelve con `ActivitySwipeBox`.
 * El agujero temporal que abrió `c817fd0` (riel sin capa de gesto) queda cerrado y guardado.
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

    @Test
    fun `la tarjeta de la lista declara el hueco swipe`() {
        val firma = declaredParameters(source("ActivityCard.kt"), "ActivityCard")
        assertTrue(
            "swipe: @Composable (content: @Composable () -> Unit) -> Unit" in firma,
            "`ActivityCard` debe declarar el hueco con el tipo exacto de ranura. " +
                "Firma real:\n$firma",
        )
    }

    @Test
    fun `el hueco de la tarjeta NO lleva valor por defecto`() {
        val firma = declaredParameters(source("ActivityCard.kt"), "ActivityCard")
        val linea = firma.lines().firstOrNull { "swipe:" in it }
        assertTrue(linea != null, "No se encuentra el parámetro `swipe` en:\n$firma")
        assertFalse(
            "=" in linea,
            "🔴 `ActivityCard.swipe` lleva valor por defecto. ESO ES EL DEFECTO: un hueco con " +
                "default convierte «no monté el gesto» en un `c()` silencioso.\n" +
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
    fun `toda llamada a ActivityCard monta el gesto`() {
        val sites = callSites("ActivityCard")
        assertTrue(sites.isNotEmpty(), "No se encontró ninguna llamada a `ActivityCard`")
        sites.forEach { site ->
            assertTrue(
                "swipe =" in site.arguments,
                "🔴 `${site.callee}()` en ${site.file} NO monta el gesto (falta `swipe =`). " +
                    "La superficie de la lista se quedaría sin gesto aunque compile.",
            )
            // T7 · La tarjeta de `AgendaList` sobrevive en disco hasta T8: su capa de gesto se
            // sigue exigiendo aquí para no perder la asserción que se movió de sitio al
            // reescribir la sección `superficie 3` (el riel es ahora la superficie viva).
            assertTrue(
                "ActivitySwipeBox(" in site.arguments,
                "🔴 El hueco `swipe` de `ActivityCard()` en ${site.file} no crea la capa de gesto.",
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
    fun `superficie 3 — el riel — todo bloque declara el hueco y lo monta en el punto de render`() {
        // La superficie de lista es el RIEL, y su unidad deslizable es el BLOQUE. El guard es el
        // MISMO de siempre —"toda superficie deslizable declara el hueco sin default y su punto
        // de render lo envuelve"—, cambia la pieza que vigila: antes `ActivityCard` en
        // `AgendaList.kt`, hoy el bloque del día y la fila sin fecha del riel (T7, L11).
        listOf(
            "RailBlockPill" to "RailCanvas.kt",
            "RailUndatedRow" to "RailTimeline.kt",
        ).forEach { (row, file) ->
            val firma = declaredParameters(source(file), row)
            assertTrue(
                "swipe: @Composable (content: @Composable () -> Unit) -> Unit" in firma,
                "`$row` debe declarar el hueco con el tipo exacto de ranura. Firma real:\n$firma",
            )
            val linea = firma.lines().firstOrNull { "swipe:" in it }
            assertTrue(linea != null, "No se encuentra el parámetro `swipe` de `$row` en:\n$firma")
            assertFalse(
                "=" in linea,
                "🔴 `$row.swipe` lleva valor por defecto. ESO ES EL DEFECTO del que salieron 46 " +
                    "pruebas en verde con dos superficies sin gesto: un hueco con default convierte " +
                    "«no monté el gesto» en un `c()` silencioso.\nFirma real:\n$firma",
            )
        }

        // Una llamada por superficie y con la capa de gesto DENTRO del hueco: la fila sigue
        // siendo función pura de sus datos y quién crea el gesto es el punto de render.
        listOf(
            "RailBlockPill" to 1,
            "RailUndatedRow" to 1,
        ).forEach { (row, expected) ->
            val sites = callSites(row)
            assertEquals(expected, sites.size, "Se esperaba exactamente $expected sitio de `$row`")
            sites.forEach { site ->
                assertTrue(
                    "swipe =" in site.arguments,
                    "🔴 `$row()` en ${site.file} NO monta el gesto (falta `swipe =`). " +
                        "Esa superficie se queda sin swipe aunque todo lo demás compile.",
                )
                assertTrue(
                    "ActivitySwipeBox(" in site.arguments,
                    "🔴 El hueco `swipe` de `$row()` en ${site.file} no crea la capa de gesto: " +
                        "la actividad se dibuja sin acciones y compila igual.",
                )
            }
        }

        // Y la acción se dispara para la actividad de ESA fila, no para otra: el id sale del
        // bloque y no del estado de la pantalla.
        assertTrue(
            "block.row.actividad.id" in callSites("RailBlockPill").single().arguments,
            "🔴 El bloque no reenvía el id de SU actividad: la acción caería en otra fila.",
        )
        assertTrue(
            "undatedRow.actividad.id" in callSites("RailUndatedRow").single().arguments,
            "🔴 La fila sin fecha no reenvía el id de SU actividad.",
        )
    }

    @Test
    fun `el callback del gesto se EXIGE en toda la cadena del riel`() {
        // El hueco sin default sólo sirve si el callback llega hasta el bloque. Estos tres
        // eslabones son la cadena entera: si uno lo declara con default, el gesto se puede
        // quedar sin consumidor desde arriba y nadie se entera.
        listOf(
            "RailTimeline" to "RailTimeline.kt",
            "RailDayCanvas" to "RailCanvas.kt",
        ).forEach { (callee, file) ->
            val firma = declaredParameters(source(file), callee)
            val linea = firma.lines().firstOrNull { "onSwipeAction:" in it }
            assertTrue(linea != null, "`$callee` no declara `onSwipeAction`:\n$firma")
            assertTrue(
                "(String, SwipeAction) -> Unit" in linea,
                "`$callee` no recibe el par (id, acción): la política de acciones se perdería.\n$firma",
            )
            assertFalse(
                "=" in linea,
                "🔴 `$callee.onSwipeAction` lleva valor por defecto: un llamante nuevo se olvida de " +
                    "pasarlo y la lista se dibuja sin gesto, compilando.\n$firma",
            )
        }
    }

    @Test
    fun `la pantalla monta el riel como unica superficie y el motivo sigue siendo obligatorio`() {
        // T6 · LA PANTALLA CAMBIÓ DE FORMA, el guard no se borra: se reescribe para la forma
        // nueva. `TimelineMode` desapareció (L6: el riel REEMPLAZA Agenda y Semana), así que
        // `ActivitiesScreen.kt` ya no monta `AgendaTimeline` ni le pasa su `onSwipeAction`: el
        // feed de la lista es el riel. Dos feeds a la vez es justo lo que el guard no puede
        // dejar pasar.
        val agendaTimeline = callSites("AgendaTimeline")
        assertTrue(
            agendaTimeline.isEmpty(),
            "🔴 La pantalla vuelve a montar `AgendaTimeline` en ${agendaTimeline.map { it.file }}. " +
                "L6 decidió que el riel es la única vista; la agenda se retira en T8 y hasta " +
                "entonces sólo se sostiene con sus propias pruebas.",
        )
        // La firma sigue declarada en `AgendaList.kt` (T8 borra el fichero): mientras exista,
        // el parámetro no puede recuperar un valor por defecto.
        val firma = declaredParameters(source("AgendaList.kt"), "AgendaTimeline")
        val linea = firma.lines().firstOrNull { "onSwipeAction:" in it }
        assertTrue(linea != null, "`AgendaTimeline` no declara `onSwipeAction`:\n$firma")
        assertFalse(
            "=" in linea,
            "🔴 `AgendaTimeline.onSwipeAction` lleva valor por defecto. En una SUPERFICIE eso " +
                "repite el defecto del hueco: si un llamante nuevo se olvida de pasarlo, la " +
                "lista se dibuja sin gesto y compila.",
        )

        // El cable de la pantalla al riel: sin él, los bloques tendrían gesto y nada que hacer.
        val screen = stripCommentsAndStrings(source("ActivitiesScreen.kt"))
        assertTrue(
            "RailTimeline(" in screen,
            "La pantalla no monta el riel: sin feed de lista, el gesto se quedaría sin superficie.",
        )
        assertTrue(
            "onSwipeAction = viewModel::startSwipeAction" in screen,
            "🔴 La pantalla no cablea el gesto del riel: `ActivitySwipeBox` dispararía acciones " +
                "hacia la nada.",
        )
        assertTrue(
            "SwipeReasonDialog(" in screen && "viewModel::submitSwipeReason" in screen,
            "🔴 La pantalla dejó de montar el diálogo de motivo: el gesto de " +
                "\"No asistir\" se quedaría sin su mitad obligatoria.",
        )
        assertTrue(
            "SwipeAction.DECLINE" in screen,
            "🔴 La pantalla dejó de abrir el gesto de no asistir desde el detalle: esa acción se " +
                "quedaría sin NINGUNA entrada.",
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
