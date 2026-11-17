package com.parental.shared.feature.activities.ui.swipe

import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * T9 (L12/L13) · **LOS TRES ESTADOS de la capa de swipe**, y el defecto que los obligó.
 *
 * `ActivitySwipeBox` revela la barra al 55% del ancho del bloque. En un bloque de MEDIA CALLE
 * (~146dp) esa barra son ~80dp y los tres botones CON ETIQUETA necesitan ~286dp: el tercero
 * quedaba recortado y no se alcanzaba con el dedo — TalkBack sí, por `customActions`, que es
 * exactamente el síntoma de una superficie que se lee bien y no se toca.
 *
 * Decisión del humano (L13): **nunca un objetivo táctil por debajo de 44dp** (S11). Por eso el
 * componente decide en TRES estados, todos desde su ancho YA MEDIDO y sin bandera de quien llama:
 *
 *  1. `LABELLED` — la barra revelada llega a 160dp: botones con ícono + texto, como siempre.
 *  2. `ICON_ONLY` — no llega, pero cada acción conserva ≥ 44dp: botones de sólo ícono con la
 *     etiqueta como `contentDescription`.
 *  3. `NO_GESTURE` — ni eso alcanza: no se monta el gesto. Bloque entero, toque abre el detalle,
 *     acciones en el detalle y en `customActions`.
 *
 * Estas pruebas son de dos clases, a propósito:
 *  1. ARITMÉTICA PURA — `swipeBarMode` / `swipeBarWidthPx` / `swipeActionWidthPx` y los dos
 *     umbrales. Es donde se comprueban los números exactos de cada estado.
 *  2. CONTRATO DE FUENTE — el repo no tiene `androidTest` ni `createComposeRule`, así que lo que no
 *     es aritmética se pinea leyendo el texto, igual que `SwipeMountingTest`: que el estado cuelgue
 *     del ANCHO medido (no de una bandera), que el estado sin gesto exista y NO monte el detector,
 *     y que ninguna acción se quede sin nombre accesible en ninguno de los tres.
 *
 * ⚠ Las funciones puras trabajan en UNA sola unidad (px o dp, la que se les pase): el sitio de
 * composición convierte los umbrales a px con `LocalDensity`, y aquí se pasan en dp para que los
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
                "${File("").absolutePath}. El guard de los estados de la barra no se puede evaluar " +
                "en silencio.",
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

    /** Los dos umbrales en la MISMA unidad que las funciones puras: dp. */
    private fun threshold(): Float = SwipeNarrowBarMinWidth.value

    private fun minAction(): Float = SwipeMinActionWidth.value

    /** Cuerpo de `ActivitySwipeBox`, desde su declaración hasta la siguiente función privada. */
    private fun swipeBoxBody(): String {
        val code = codeOf(source("swipe/ActivitySwipeBox.kt"))
        val from = code.indexOf("fun ActivitySwipeBox(")
        assertTrue(from >= 0, "No se encuentra `ActivitySwipeBox`")
        return code.substring(from)
    }

    /**
     * Índice del `}` que CIERRA el bloque que empieza en [desde] — el cierre de la rama, no un
     * largo fijo de caracteres: un recorte a ojo incluiría la rama siguiente y comprobaría lo que
     * no toca.
     */
    private fun finDeLaRama(body: String, desde: Int): Int {
        val cierre = body.indexOf("\n    }", desde)
        assertTrue(cierre > desde, "La rama que empieza en $desde no cierra con una llave indentada")
        return cierre
    }

    // ── 1 · Los umbrales, y por qué valen lo que valen ────────────────────────────────

    @Test
    fun `el umbral con etiqueta son tres iconos de 48dp con la separacion de siempre`() {
        // 3 × 48dp de objetivo táctil (R2/S11) + 2 × 8dp de separación = 160dp. No es un número
        // redondo elegido a ojo: es la aritmética de lo que la barra necesita para que NINGUNA
        // acción quede recortada cuando se dibujan como íconos.
        assertEquals(48.dp, SwipeTouchTargetWidth, "El objetivo táctil de una acción es 48dp (R2/S11)")
        assertEquals(
            160.dp,
            SwipeNarrowBarMinWidth,
            "El umbral con etiqueta tiene que ser 3 × 48dp + 2 × 8dp = 160dp.",
        )
    }

    @Test
    fun `el suelo del modo de iconos es 44dp — S11 y no un numero elegido`() {
        // Decisión del humano (L13): "we do NOT ship a touch target below 44dp. S11 says so".
        assertEquals(
            44.dp,
            SwipeMinActionWidth,
            "🔴 El suelo por acción tiene que ser 44dp (S11). Si baja, se envían objetivos táctiles " +
                "que la spec prohíbe; si sube, se retira el gesto en bloques donde SÍ cabía.",
        )
    }

    // ── 2 · Los tres estados, con los números del riel ────────────────────────────────

    @Test
    fun `calle completa 296dp — estado con etiqueta y barra intacta`() {
        assertEquals(
            SwipeBarMode.LABELLED,
            swipeBarMode(296f, 3, threshold(), minAction()),
            "🔴 Un bloque de calle completa (~296dp) perdió el modo con etiqueta: su barra revelada " +
                "(~163dp) YA pasa el umbral de 160dp.",
        )
        assertEquals(
            162.8f,
            swipeBarWidthPx(296f, threshold()),
            0.01f,
            "🔴 La fracción del modo con etiqueta cambió. `BAR_WIDTH_FRACTION` es 0.55 y no se toca: " +
                "296dp × 0.55 = 162.8dp.",
        )
    }

    @Test
    fun `media calle 146dp — iconos, y CADA accion conserva al menos 44dp`() {
        assertEquals(
            SwipeBarMode.ICON_ONLY,
            swipeBarMode(146f, 3, threshold(), minAction()),
            "🔴 Media calle (~146dp) no entra en el modo de íconos: su barra al 55% son 80dp, donde " +
                "no cabe ningún botón de 44dp.",
        )
        assertEquals(
            146f,
            swipeBarWidthPx(146f, threshold()),
            0.01f,
            "En modo de íconos la barra revelada es el bloque ENTERO (146dp < 160dp de umbral).",
        )
        val porAccion = swipeActionWidthPx(146f, 3, threshold())
        assertEquals(48.67f, porAccion, 0.01f, "146dp repartidos entre tres acciones son 48,6dp")
        assertTrue(
            porAccion >= minAction(),
            "🔴 Una acción de la barra de íconos quedó por debajo de 44dp ($porAccion): S11 lo " +
                "prohíbe y en ese caso el estado tendría que ser `NO_GESTURE`, no íconos chicos.",
        )
    }

    @Test
    fun `dos calles en pantalla de 360dp 130dp — SIN gesto montado`() {
        // Es el caso que la decisión del humano nombra: 130dp / 3 = 43,3dp, por debajo de 44dp.
        assertEquals(
            SwipeBarMode.NO_GESTURE,
            swipeBarMode(130f, 3, threshold(), minAction()),
            "🔴 Un bloque de 130dp con tres acciones se quedó con barra: 130/3 = 43,3dp por acción, " +
                "por debajo del suelo de 44dp de S11. Ese bloque no admite gesto.",
        )
        assertTrue(
            swipeActionWidthPx(130f, 3, threshold()) < minAction(),
            "La cuenta del caso: 130dp repartidos entre tres son 43,3dp, menos que el suelo.",
        )
    }

    @Test
    fun `tres calles 96dp — SIN gesto montado`() {
        assertEquals(
            SwipeBarMode.NO_GESTURE,
            swipeBarMode(96f, 3, threshold(), minAction()),
            "🔴 Un bloque de un tercio (~96dp) se quedó con barra: 96/3 = 32dp por acción es " +
                "exactamente el objetivo táctil que S11 no permite enviar.",
        )
    }

    @Test
    fun `bloques anchos — el estado con etiqueta NO ensancha la barra`() {
        // PRESERVE · el comportamiento de hoy para los bloques anchos: la barra es la fracción y
        // nada más. Se comprueba lejos del umbral (400dp → 220dp) para no depender del borde.
        assertEquals(SwipeBarMode.LABELLED, swipeBarMode(400f, 3, threshold(), minAction()))
        assertEquals(220f, swipeBarWidthPx(400f, threshold()), 0.01f)
    }

    @Test
    fun `el borde del umbral cae con etiqueta — 291dp es el ultimo bloque con texto`() {
        // 291dp × 0.55 = 160,05dp, el primer ancho que alcanza el umbral. Sirve para fijar que la
        // comparación es "≥" y que el estado se decide por la BARRA, no por el bloque.
        assertEquals(SwipeBarMode.LABELLED, swipeBarMode(291f, 3, threshold(), minAction()))
        assertEquals(
            SwipeBarMode.ICON_ONLY,
            swipeBarMode(290f, 3, threshold(), minAction()),
            "290dp × 0.55 = 159,5dp no llega al umbral: pasa a íconos (y 290/3 = 96,7dp ≥ 44dp).",
        )
    }

    @Test
    fun `el suelo de 44dp es lo unico que separa iconos de sin gesto`() {
        // Con dos acciones, 100dp da 50dp por acción: íconos. Con tres, 100dp da 33,3dp: sin gesto.
        // Si esta pareja dejara de diferir, la regla de 44dp habría dejado de decidir nada.
        assertEquals(SwipeBarMode.ICON_ONLY, swipeBarMode(100f, 2, threshold(), minAction()))
        assertEquals(SwipeBarMode.NO_GESTURE, swipeBarMode(100f, 3, threshold(), minAction()))
    }

    @Test
    fun `antes del primer layout el estado es el de siempre`() {
        // `onSizeChanged` entrega 0px hasta el primer layout. El primer frame tiene que ser el de
        // siempre (barra de ancho 0, nada revelado): un `NO_GESTURE` inicial montaría y desmontaría
        // el detector en cada bloque al aparecer.
        assertEquals(SwipeBarMode.LABELLED, swipeBarMode(0f, 3, threshold(), minAction()))
        assertEquals(0f, swipeBarWidthPx(0f, threshold()), 0.01f)
    }

    // ── 3 · El estado cuelga del ANCHO MEDIDO, no de una bandera del llamante ─────────

    @Test
    fun `los tres estados los decide el ancho medido — ninguna bandera nueva en la firma`() {
        val text = source("swipe/ActivitySwipeBox.kt")
        val code = codeOf(text)
        val firma = declaredParameters(text, "ActivitySwipeBox")

        assertFalse(
            "narrow" in firma.lowercase() || "icononly" in firma.lowercase().replace(" ", "") ||
                "gesture" in firma.lowercase(),
            "🔴 `ActivitySwipeBox` acepta una bandera de estado. ESO ES EL DEFECTO que el humano " +
                "cerró (L12/L13): quien llama no sabe cuánto mide su bloque, y una bandera deja al " +
                "llamante la decisión que ya toma la medición.\nFirma real:\n$firma",
        )
        val booleanos = firma.lines().filter { ": Boolean" in it }.map { it.trim() }
        assertEquals(
            listOf("fillCrossAxis: Boolean = true,"),
            booleanos,
            "🔴 La firma ganó un `Boolean` nuevo. La geometría del eje cruzado (`fillCrossAxis`) es " +
                "la única decisión de quien llama.\nFirma real:\n$firma",
        )

        // La decisión sale del ancho que se observa con `onSizeChanged` y de los dos umbrales en dp
        // convertidos con `LocalDensity`: no de un `if` sobre el enum ni de una constante de pantalla.
        assertTrue(
            "onSizeChanged" in code,
            "`ActivitySwipeBox` dejó de observar su propio ancho: sin medición no hay umbral.",
        )
        assertTrue(
            "swipeBarMode(" in code && "widthPx = fullWidthPx" in code,
            "La decisión de estado no se toma sobre el ancho MEDIDO (`fullWidthPx`).",
        )
        assertTrue(
            "SwipeNarrowBarMinWidth.toPx()" in code && "SwipeMinActionWidth.toPx()" in code,
            "Alguno de los dos umbrales no se convierte a px con la densidad del dispositivo: " +
                "comparar dp contra px daría un estado distinto en cada pantalla.",
        )
    }

    // ── 4 · El estado SIN GESTO existe y NO monta el detector ────────────────────────

    @Test
    fun `el estado sin gesto existe y retorna ANTES de montar el detector`() {
        val body = swipeBoxBody()

        val rama = body.indexOf("if (mode == SwipeBarMode.NO_GESTURE)")
        assertTrue(
            rama >= 0,
            "🔴 Desapareció la rama `NO_GESTURE`. Sin ella, un bloque estrecho vuelve a dibujar " +
                "acciones por debajo de 44dp — exactamente lo que la decisión del humano prohíbe.",
        )
        val detector = body.indexOf(".pointerInput(")
        assertTrue(detector >= 0, "No se encuentra el detector del gesto en `ActivitySwipeBox`")
        assertTrue(
            rama < detector,
            "🔴 La rama `NO_GESTURE` ya no corta ANTES del `pointerInput`: el detector se monta " +
                "igual y el bloque se traga el desliz. La regla es «no se adjunta el gesto», no " +
                "«se adjunta y se ignora».",
        )

        val ventana = body.substring(rama, finDeLaRama(body, rama))
        assertTrue(
            "if (mode == SwipeBarMode.NO_GESTURE) {" in ventana,
            "🔴 La rama del estado sin gesto no se abre con la condición exacta " +
                "`if (mode == SwipeBarMode.NO_GESTURE)` — un `&& false`, un `if (true)` o cualquier " +
                "cortocircuito la deja escrita pero INALCANZABLE, que es la forma silenciosa de que " +
                "vuelvan los botones de 32dp.\n$ventana",
        )
        assertTrue(
            "return" in ventana,
            "🔴 La rama `NO_GESTURE` no retorna: el gesto se montaría igual.\n$ventana",
        )
        assertTrue(
            "content()" in ventana,
            "🔴 La rama `NO_GESTURE` no dibuja el contenido: el bloque quedaría vacío.\n$ventana",
        )
        assertTrue(
            "Box(modifier = modifier" in ventana,
            "🔴 La rama `NO_GESTURE` no se apoya en el mismo envoltorio que el caso «sin acciones» " +
                "(`Box(modifier = modifier) { content() }`): el contenido tiene que medirse igual " +
                "que sin capa de swipe.\n$ventana",
        )
        // Sin barra, sin desplazamiento y sin clip: nada de la capa de swipe en esta rama.
        listOf(".clip(", ".offset {", ".background(", "customActions", "Arrangement").forEach { resto ->
            assertFalse(
                resto in ventana,
                "🔴 La rama `NO_GESTURE` sigue montando parte de la capa de swipe (`$resto`). Ahí no " +
                    "hay barra ni desplazamiento: sólo el contenido.\n$ventana",
            )
        }
    }

    @Test
    fun `el gesto se monta UNA sola vez y en la rama que si lo tiene`() {
        val body = swipeBoxBody()
        assertEquals(
            1,
            Regex("""\.pointerInput\(""").findAll(body).count(),
            "🔴 Hay más de un `pointerInput` en `ActivitySwipeBox`: el detector tendría dos copias o " +
                "se montaría también en el estado sin gesto.",
        )
        assertEquals(
            1,
            Regex("detectHorizontalDragGestures").findAll(body).count(),
            "🔴 Hay más de un `detectHorizontalDragGestures`: el detector del gesto está duplicado.",
        )
    }

    // ── 5 · Ninguna acción sin nombre accesible, en NINGUNO de los tres estados ──────

    @Test
    fun `toda accion tiene etiqueta propia y no vacia`() {
        val entries = Regex("""^\s{4}([A-Z_]+)\("([^"]*)"""", RegexOption.MULTILINE)
            .findAll(source("swipe/SwipeAction.kt"))
            .map { it.groupValues[1] to it.groupValues[2] }
            .toList()

        assertEquals(
            listOf("DELETE", "CANCEL", "DECLINE"),
            entries.map { it.first },
            "El enum de acciones cambió: los tres estados mapean ícono y nombre por acción.",
        )
        entries.forEach { (id, label) ->
            assertTrue(
                label.isNotBlank(),
                "🔴 `SwipeAction.$id` no tiene etiqueta. En el estado de íconos el ícono NO se dibuja " +
                    "sin nombre: la etiqueta es su `contentDescription`; y en el estado sin gesto es " +
                    "la que TalkBack anuncia por `customActions` (`confirmLabel`).",
            )
        }
        // El nombre accesible de `customActions` (estado sin gesto) SALE de la etiqueta: si esa
        // derivación se rompiera, las acciones quedarían sin nombre justo donde no hay barra.
        assertTrue(
            "\"\$label con motivo\"" in source("swipe/SwipeAction.kt"),
            "🔴 `confirmLabel` dejó de derivarse de `label`: en el estado sin gesto las acciones " +
                "perderían el nombre que anuncia TalkBack.",
        )
    }

    @Test
    fun `el estado de iconos usa la etiqueta como nombre accesible del icono`() {
        val code = codeOf(source("swipe/ActivitySwipeBox.kt"))
        assertTrue(
            "contentDescription = action.label" in code,
            "🔴 El ícono del estado de íconos no lleva `contentDescription = action.label`: sería un " +
                "ícono sin nombre para TalkBack.",
        )
        // El estado con etiqueta conserva el suyo: el TEXTO es el nombre y el ícono es decorativo.
        assertTrue(
            "Text(action.label" in code,
            "🔴 El estado con etiqueta perdió el texto de la acción: es su nombre accesible ahí.",
        )
        assertEquals(
            1,
            Regex("contentDescription = null").findAll(code).count(),
            "Hay más de un ícono con `contentDescription = null`: sólo el del estado CON ETIQUETA es " +
                "decorativo, porque su nombre lo pone el texto del botón.",
        )
        // Y ninguno se queda sin el argumento, en ninguno de los dos botones.
        callArguments(code, "Icon").forEach { args ->
            assertTrue(
                "contentDescription" in args,
                "🔴 Un `Icon(` se declara sin `contentDescription`: el repo no dibuja íconos sin " +
                    "nombre. Argumentos:\n$args",
            )
        }
    }

    @Test
    fun `sin gesto las acciones siguen alcanzables por customActions`() {
        // El estado sin gesto NO puede dejar las acciones sin NINGUNA entrada: la vía accesible
        // (`customActions`, R4-T2-4) tiene que seguir declarada y ser la misma para los tres
        // estados, porque es la única que queda cuando no hay barra.
        val code = codeOf(source("swipe/ActivitySwipeBox.kt"))
        assertTrue(
            "customActions = actions.map" in code,
            "🔴 Se perdió `customActions`: en el estado sin gesto las acciones quedarían sin ninguna " +
                "vía, ni táctil (no hay barra) ni accesible.",
        )
        assertTrue(
            "action.confirmLabel" in code,
            "🔴 `customActions` dejó de anunciar `confirmLabel`: la acción se leería sin su motivo.",
        )
    }

    @Test
    fun `cada accion sigue teniendo su icono y su color`() {
        // PRESERVE · el mapeo de íconos y los roles de color no cambian: el estado de íconos dibuja
        // los MISMOS vectores para las MISMAS acciones, no unos nuevos.
        val code = codeOf(source("swipe/ActivitySwipeBox.kt"))
        val icono = code.substringAfter("private fun SwipeAction.icon()")
        listOf("SwipeAction.DELETE", "SwipeAction.CANCEL", "SwipeAction.DECLINE").forEach { action ->
            assertTrue(
                action in icono.substringBefore("\n}"),
                "🔴 `$action` se quedó sin ícono en `SwipeAction.icon()`: en el estado de íconos la " +
                    "acción no se podría dibujar.",
            )
        }
    }

    // ── 6 · Lo que NO cambia: gating del servidor, umbrales del gesto, motivo ────────

    @Test
    fun `los tres estados no tocan el gating del servidor ni los umbrales del gesto`() {
        val code = codeOf(source("swipe/ActivitySwipeBox.kt"))
        // R1 · las acciones siguen derivándose de los `can_*`; el estado no las filtra.
        assertTrue(
            "availableSwipeActions(actividad)" in code,
            "🔴 El envoltorio dejó de derivar las acciones del servidor (R1).",
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
        // El alto mínimo de toque de los botones con etiqueta sigue siendo el mismo.
        assertTrue(
            "internal val SwipeTouchTargetHeight = 48.dp" in code,
            "🔴 El objetivo táctil del botón con etiqueta dejó de ser 48dp.",
        )
    }
}
