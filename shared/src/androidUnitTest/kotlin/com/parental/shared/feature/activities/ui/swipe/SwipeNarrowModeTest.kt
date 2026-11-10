package com.parental.shared.feature.activities.ui.swipe

import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * T9 (L12) · **MODO ANGOSTO de la barra de acciones**: el defecto que encontró T7.
 *
 * `ActivitySwipeBox` revela la barra al 55% del ancho del bloque. En un bloque de MEDIA CALLE
 * (~146dp) esa barra son ~80dp y los tres botones CON ETIQUETA necesitan ~286dp: el tercero
 * queda recortado y no se alcanza con el dedo — TalkBack sí lo alcanza por `customActions`, que
 * es exactamente el síntoma de una superficie que se lee bien y no se toca.
 *
 * El arreglo es un MODO ANGOSTO dentro del mismo componente: bajo un umbral medido sobre la
 * barra REVELADA, cada acción se dibuja con ÍCONO y su nombre accesible, en vez de con texto.
 *
 * Estas pruebas son de dos clases, a propósito:
 *  1. ARITMÉTICA PURA — `isNarrowSwipeBar` / `swipeBarWidthPx` / `SwipeNarrowBarMinWidth`.
 *     Es donde se puede comprobar de verdad que la calle completa conserva la etiqueta y que
 *     media y un tercio cambian a íconos, con los números exactos.
 *  2. CONTRATO DE FUENTE — el repo no tiene `androidTest` ni `createComposeRule`, así que lo
 *     que no es aritmética se pinea leyendo el texto, igual que `SwipeMountingTest`: que el
 *     modo cuelgue del ANCHO medido (no de una bandera del llamante) y que ninguna acción se
 *     quede sin nombre accesible en ninguno de los dos modos.
 *
 * ⚠ Las funciones puras trabajan en UNA sola unidad (px o dp, la que se les pase): el umbral se
 * convierte a px en el sitio de composición (`LocalDensity`), y aquí se pasa en dp para que los
 * números del contrato se lean como los del riel.
 */
class SwipeNarrowModeTest {

    // ── Utilidades de lectura de fuente ───────────────────────────────────────────────

    private val uiDir: File by lazy { activitiesUiDir() }

    private fun activitiesUiDir(): File {
        var candidate: File? = File("").absoluteFile
        while (candidate != null) {
            val dir = File(candidate, "src/commonMain/kotlin/com/parental/shared/feature/activities/ui")
            if (dir.isDirectory) return dir
            candidate = candidate.parentFile
        }
        fail(
            "No se encuentra `src/commonMain/kotlin/.../activities/ui` subiendo desde " +
                "${File("").absolutePath}. El guard del modo angosto no se puede evaluar en silencio.",
        )
    }

    private fun source(name: String): String = File(uiDir, name).readText()

    /**
     * Quita COMENTARIOS y conserva literales (varias asserciones miran el texto de una etiqueta):
     * `//` hasta el fin de línea y `/* … */` completo, conservando los saltos de línea para no
     * desplazar la numeración.
     */
    private fun codeOf(text: String): String {
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

    /** El bloque de argumentos de `fun [function](`, sin comentarios. */
    private fun declaredParameters(text: String, function: String): String {
        val code = codeOf(text)
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

    /** Los tramos de argumentos de cada invocación de `[callee](`, sin la declaración. */
    private fun callArguments(text: String, callee: String): List<String> {
        val code = codeOf(text)
        val blocks = mutableListOf<String>()
        var i = 0
        while (true) {
            val at = code.indexOf("$callee(", i)
            if (at < 0) break
            val open = at + callee.length
            var j = open + 1
            var depth = 1
            while (j < code.length && depth > 0) {
                if (code[j] == '(') depth++
                if (code[j] == ')') depth--
                j++
            }
            if (!code.substring(0, at).trimEnd().endsWith("fun")) {
                blocks += code.substring(open + 1, j - 1)
            }
            i = j
        }
        return blocks
    }

    /** Umbral en la MISMA unidad que las funciones puras: dp. */
    private fun threshold(): Float = SwipeNarrowBarMinWidth.value

    // ── 1 · El umbral, y por qué vale lo que vale ──────────────────────────────────────

    @Test
    fun `el umbral del modo angosto son tres iconos de 48dp con la separacion de siempre`() {
        // 3 × 48dp de objetivo táctil (R2/S11) + 2 × 8dp de separación = 160dp. No es un número
        // redondo elegido a ojo: es la aritmética de lo que la barra necesita para que NINGUNA
        // acción quede recortada cuando se dibujan como íconos.
        assertEquals(48.dp, SwipeTouchTargetWidth, "El objetivo táctil de una acción es 48dp (R2/S11)")
        assertEquals(
            160.dp,
            SwipeNarrowBarMinWidth,
            "El umbral tiene que ser 3 × 48dp + 2 × 8dp = 160dp. Si cambia, o cambia el objetivo " +
                "táctil o la separación, y el modo angosto dejaría de caber.",
        )
    }

    // ── 2 · Los números del riel: calle completa conserva etiqueta, media y un tercio no ─

    @Test
    fun `calle completa — la barra conserva el modo con etiqueta`() {
        // Bloque de calle completa: 296dp de ancho (el riel pierde 4dp por el `LaneGap`).
        val width = 296f
        assertFalse(
            isNarrowSwipeBar(width, threshold()),
            "🔴 Un bloque de calle completa (~296dp) se pasó al modo de íconos: la barra revelada " +
                "(~163dp) YA cabe por encima del umbral de 160dp. El modo angosto es para bloques " +
                "estrechos, no para el ancho entero del riel.",
        )
        assertEquals(
            162.8f,
            swipeBarWidthPx(width, threshold()),
            0.01f,
            "🔴 La fracción del modo ancho cambió. `BAR_WIDTH_FRACTION` es 0.55 y no se toca: " +
                "296dp × 0.55 = 162.8dp.",
        )
    }

    @Test
    fun `dos calles — la barra pasa a iconos y se ensancha hasta el bloque entero`() {
        // Media calle: 146dp. La barra al 55% son 80,3dp y los tres botones con texto necesitan
        // ~286dp: es el defecto que reportó T7.
        val width = 146f
        assertTrue(
            isNarrowSwipeBar(width, threshold()),
            "🔴 Media calle (~146dp) no entra en el modo angosto: su barra al 55% son 80dp, " +
                "donde no cabe ningún botón de 48dp.",
        )
        assertEquals(
            146f,
            swipeBarWidthPx(width, threshold()),
            0.01f,
            "En modo angosto la barra revelada es el bloque ENTERO (146dp < 160dp de umbral): " +
                "revelar sólo una fracción es lo que dejaba acciones fuera del alcance del dedo.",
        )
    }

    @Test
    fun `tres calles — la barra tambien pasa a iconos`() {
        // Un tercio: 96dp. Aquí está el suelo geométrico: tres objetivos de 48dp son 144dp y no
        // caben en 96dp. La barra se revela entera (96dp) para que las tres acciones al menos se
        // vean y se alcancen con el dedo; ver la nota de límite en `ActivitySwipeBox`.
        val width = 96f
        assertTrue(
            isNarrowSwipeBar(width, threshold()),
            "🔴 Un bloque de un tercio (~96dp) no entra en el modo angosto.",
        )
        assertEquals(96f, swipeBarWidthPx(width, threshold()), 0.01f)
    }

    @Test
    fun `bloques anchos — el modo angosto NO ensancha la barra`() {
        // PRESERVE · el comportamiento de hoy para los bloques anchos: la barra es la fracción y
        // nada más. Se comprueba lejos del umbral (400dp → 220dp) para no depender del borde.
        assertEquals(220f, swipeBarWidthPx(400f, threshold()), 0.01f)
        assertFalse(isNarrowSwipeBar(400f, threshold()))
    }

    @Test
    fun `antes del primer layout no se elige modo angosto`() {
        // `onSizeChanged` entrega 0px hasta el primer layout. Con ancho 0 la fracción también es
        // 0, así que sin esta guarda TODA fila empezaría en modo angosto y la barra cambiaría de
        // forma en el primer frame.
        assertFalse(
            isNarrowSwipeBar(0f, threshold()),
            "🔴 Con ancho 0 se declara el modo angosto: la barra cambia de forma en el primer " +
                "frame de cada fila.",
        )
        assertEquals(0f, swipeBarWidthPx(0f, threshold()), 0.01f)
    }

    // ── 3 · El modo cuelga del ANCHO MEDIDO, no de una bandera del llamante ────────────

    @Test
    fun `el modo angosto lo decide el ancho medido — ninguna bandera nueva en la firma`() {
        val text = source("swipe/ActivitySwipeBox.kt")
        val code = codeOf(text)
        val firma = declaredParameters(text, "ActivitySwipeBox")

        assertFalse(
            "narrow" in firma.lowercase() || "icononly" in firma.lowercase().replace(" ", ""),
            "🔴 `ActivitySwipeBox` acepta una bandera de modo angosto. ESO ES EL DEFECTO que el " +
                "humano cerró (L12): quien llama no sabe cuánto mide su bloque, y una bandera deja " +
                "al llamante la decisión que ya toma la medición.\nFirma real:\n$firma",
        )
        val booleanos = firma.lines().filter { ": Boolean" in it }.map { it.trim() }
        assertEquals(
            listOf("fillCrossAxis: Boolean = true,"),
            booleanos,
            "🔴 La firma ganó un `Boolean` nuevo. La geometría del eje cruzado (`fillCrossAxis`) es " +
                "la única decisión de quien llama.\nFirma real:\n$firma",
        )

        // La decisión sale del ancho que se observa con `onSizeChanged` (0dp/0px hasta el primer
        // layout) y de un umbral en dp convertido con `LocalDensity`: no de un `if` sobre el enum
        // ni de una constante de pantalla.
        assertTrue(
            "onSizeChanged" in code,
            "`ActivitySwipeBox` dejó de observar su propio ancho: sin medición no hay umbral.",
        )
        assertTrue(
            "isNarrowSwipeBar(fullWidthPx" in code,
            "La decisión del modo angosto no se toma sobre el ancho MEDIDO (`fullWidthPx`).",
        )
        assertTrue(
            "SwipeNarrowBarMinWidth.toPx()" in code,
            "El umbral no se convierte a px con la densidad del dispositivo: comparar dp contra px " +
                "daría un modo angosto distinto en cada pantalla.",
        )
    }

    // ── 4 · Ninguna acción sin nombre accesible, en NINGUNO de los dos modos ────────────

    @Test
    fun `toda accion tiene etiqueta propia y no vacia`() {
        val entries = Regex("""^\s{4}([A-Z_]+)\("([^"]*)"""", RegexOption.MULTILINE)
            .findAll(source("swipe/SwipeAction.kt"))
            .map { it.groupValues[1] to it.groupValues[2] }
            .toList()

        assertEquals(
            listOf("DELETE", "CANCEL", "DECLINE"),
            entries.map { it.first },
            "El enum de acciones cambió: el modo angosto mapea ícono y nombre por acción.",
        )
        entries.forEach { (id, label) ->
            assertTrue(
                label.isNotBlank(),
                "🔴 `SwipeAction.$id` no tiene etiqueta. En modo angosto el ícono NO se dibuja " +
                    "sin nombre: la etiqueta es su `contentDescription`.",
            )
        }
    }

    @Test
    fun `el modo angosto usa la etiqueta como nombre accesible del icono`() {
        val code = codeOf(source("swipe/ActivitySwipeBox.kt"))
        assertTrue(
            "contentDescription = action.label" in code,
            "🔴 El ícono del modo angosto no lleva `contentDescription = action.label`: sería un " +
                "ícono sin nombre para TalkBack.",
        )
        // El modo ancho conserva el suyo: el TEXTO es el nombre y el ícono es decorativo.
        assertTrue(
            "Text(action.label" in code,
            "🔴 El modo ancho perdió el texto de la acción: es su nombre accesible ahí.",
        )
        assertEquals(
            1,
            Regex("contentDescription = null").findAll(code).count(),
            "Hay más de un ícono con `contentDescription = null`: sólo el del modo ANCHO es " +
                "decorativo, porque su nombre lo pone el texto del botón.",
        )
        // Y ninguno se queda sin el argumento.
        callArguments(code, "Icon").forEach { args ->
            assertTrue(
                "contentDescription" in args,
                "🔴 Un `Icon(` se declara sin `contentDescription`: el repo no dibuja " +
                    "íconos sin nombre. Argumentos:\n$args",
            )
        }
    }

    @Test
    fun `cada accion sigue teniendo su icono y su color`() {
        // PRESERVE · el mapeo de íconos y los roles de color no cambian: el modo angosto dibuja los
        // MISMOS vectores para las MISMAS acciones, no unos nuevos.
        val code = codeOf(source("swipe/ActivitySwipeBox.kt"))
        val icono = code.substringAfter("private fun SwipeAction.icon()")
        listOf("SwipeAction.DELETE", "SwipeAction.CANCEL", "SwipeAction.DECLINE").forEach { action ->
            assertTrue(
                action in icono.substringBefore("\n}"),
                "🔴 `$action` se quedó sin ícono en `SwipeAction.icon()`: en modo angosto la acción " +
                    "no se podría dibujar.",
            )
        }
    }

    // ── 5 · Lo que NO cambia: gating del servidor, umbrales del gesto, motivo ─────────

    @Test
    fun `el modo angosto no toca el gating del servidor ni los umbrales del gesto`() {
        val code = codeOf(source("swipe/ActivitySwipeBox.kt"))
        // R1 · las acciones siguen derivándose de los `can_*`; el modo angosto no las filtra.
        assertTrue(
            "availableSwipeActions(actividad)" in code,
            "🔴 El envoltorio dejó de derivar las acciones del servidor (R1).",
        )
        // R4-T2-4 · la vía accesible sin gesto sigue declarada, y es la misma para los dos modos.
        assertTrue(
            "customActions = actions.map" in code,
            "🔴 Se perdió `customActions`: TalkBack dejaría de ofrecer las acciones sin gesto.",
        )
        // El umbral de compromiso del gesto no se toca (0.35 del ancho, no de la barra).
        assertTrue(
            "private const val COMMIT_THRESHOLD = 0.35f" in code,
            "🔴 `COMMIT_THRESHOLD` cambió: el gesto se comprometería a otro recorrido.",
        )
        assertTrue(
            "private const val BAR_WIDTH_FRACTION = 0.55f" in code,
            "🔴 `BAR_WIDTH_FRACTION` cambió para los bloques anchos.",
        )
        // El alto mínimo de toque del botón con etiqueta sigue siendo el mismo.
        assertTrue(
            "internal val SwipeTouchTargetHeight = 48.dp" in code,
            "🔴 El objetivo táctil del botón con etiqueta dejó de ser 48dp.",
        )
    }
}
