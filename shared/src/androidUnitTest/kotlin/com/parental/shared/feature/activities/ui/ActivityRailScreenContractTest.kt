package com.parental.shared.feature.activities.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * T6 de `odd/tasks/activity-rail.md` (S3, S4, S5, S6, S7, S8, S11) — **la PANTALLA**, contrato de
 * FUENTE.
 *
 * ⚠ Es una prueba de fuente y lo es a propósito: este repo no tiene `createComposeRule`, así
 * que componer la pantalla y contar nodos no es una opción. Leer el texto es más honesto que
 * fingir una prueba de UI. Mismo criterio que `SwipeMountingTest` y `VerifyWindowContractTest`.
 *
 * LO QUE DEFIENDE, y por qué cada cosa puede romperse en silencio:
 *
 *  1. **El riel es la única vista** (L6: "Reemplaza Agenda y Semana"). Los modos, el toggle de
 *     vista, su `Crossfade` y la hoja modal de calendario YA NO están. Si volvieran, la pantalla
 *     tendría dos feeds y sólo uno tendría los arreglos de las otras tareas.
 *  2. **Las secciones salen del agrupador que ya existe** (`buildAgendaGroups`) y cada día se
 *     deriva con `buildRailDay`: si alguien agrupara por su cuenta, el orden o la zona podrían
 *     separarse de los del riel `T1`–`T4`.
 *  3. **El salto a un día usa `railItemIndexOf`** (S6). Es la MISMA cuenta que monta el riel
 *     (`RailIndex.kt`); un índice calculado aquí volvería a aterrizar en el día equivocado.
 *  4. **El mapa de densidad va montado y se deriva del filtro activo** (S4). Un mapa que leyera
 *     `uiState.actividades` ignoraría el filtro de pestaña/hijo y mentiría con el número.
 *  5. **El alta es UN solo FAB** (S7) y la barra ya no tiene "+": dos entradas de alta es
 *     exactamente lo que S7 prohíbe.
 *  6. **El reloj no se lee en composición** (S3/S11): se lee una vez por minuto desde un
 *     `LaunchedEffect`, que se cancela solo al salir de composición.
 *  7. **El flujo de completado no cambia** (S8): sigue siendo `Confirmar` → *Por verificar* →
 *     `Cumplida` desde la hoja de detalle, sin checkbox que saltee la verificación del otro
 *     progenitor.
 *  8. **T7 (S11) · Los tres estados del riel se excluyen entre sí** y el de carga tiene forma de
 *     riel (canaleta + bloques proporcionales), no la de fila: un esqueleto de filas promete
 *     una lista que la pantalla ya no es. Y **cada bloque registra su `FocusRequester`**, que la
 *     pantalla poda por conjunto de datos: sin registro, el retorno de foco desde la hoja cae
 *     siempre en el contenedor.
 */
class ActivityRailScreenContractTest {

    private val screen: String by lazy { stripComments(source("ActivitiesScreen.kt")) }

    /** La pantalla SIN recortar: el KDoc de `densityExpanded` también es contrato (S14). */
    private val rawScreen: String by lazy { source("ActivitiesScreen.kt") }

    /**
     * Cuerpo de la llamada `callee(…)` con los paréntesis balanceados.
     *
     * Necesario para probar «los filtros están ADENTRO del panel» sin componer: buscar
     * `ActivityFiltersRow(` en todo el archivo no dice si sigue debajo del mapa o pasó a ser su
     * ranura, que es justo la diferencia de S14.
     */
    private fun callBody(source: String, callee: String): String {
        val start = source.indexOf("$callee(")
        assertTrue(start >= 0, "🔴 No existe la llamada `$callee(`.")
        var depth = 0
        var i = start + callee.length
        while (i < source.length) {
            when (source[i]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return source.substring(start, i + 1)
                }
            }
            i++
        }
        fail("🔴 La llamada `$callee(` no cierra sus paréntesis.")
    }

    /**
     * Cuerpo de una función privada, desde su firma hasta el `}` que cierra en la columna 0.
     *
     * El corte por `\n}\n` es seguro con el formato del repo (llave de cierre sin sangría) y
     * acota el texto SIN comentarios que estas guardas leen.
     */
    private fun functionBody(source: String, signature: String): String {
        val body = source.substringAfter(signature).substringBefore("\n}\n")
        assertTrue(
            signature in source && body.isNotBlank(),
            "🔴 No existe `$signature` o su cuerpo quedó vacío.",
        )
        return body
    }

    private fun uiDir(): File {
        var candidate: File? = File("").absoluteFile
        while (candidate != null) {
            val dir = File(
                candidate,
                "src/commonMain/kotlin/com/parental/shared/feature/activities/ui",
            )
            if (dir.isDirectory) return dir
            candidate = candidate.parentFile
        }
        fail("No se encuentra `src/commonMain/.../activities/ui` desde ${File("").absolutePath}")
    }

    private fun source(name: String): String = File(uiDir(), name).readText()

    /** Comentarios borrados, literales CONSERVADOS: hay copy que también es contrato. */
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

    // ── 1 · El riel es la única vista (L6) ─────────────────────────────────────────────

    @Test
    fun `la pantalla ya no conserva los modos ni su crossfade`() {
        assertFalse(
            "TimelineMode" in screen,
            "🔴 Volvió `TimelineMode`: L6 decidió que el riel REEMPLAZA Agenda y Semana, y el " +
                "enum es lo único que sostiene las dos vistas a la vez.",
        )
        assertFalse(
            "targetState = mode" in screen,
            "🔴 Volvió el `Crossfade` entre modos: dos feeds montados a la vez significa que " +
                "sólo uno recibe los arreglos del riel.",
        )
    }

    @Test
    fun `la pantalla ya no monta la agenda, la semana ni la hoja de calendario`() {
        listOf("AgendaTimeline(", "WeekTimeline(", "CalendarJumpSheet(").forEach { callee ->
            assertFalse(
                callee in screen,
                "🔴 `ActivitiesScreen.kt` vuelve a montar `$callee`. El riel es la única vista " +
                    "(S3/L6) y el salto de día vive en el mapa de densidad y en el pill `Hoy`.",
            )
        }
    }

    // ── 2 · Las secciones se derivan de las piezas que ya existen ──────────────────────

    @Test
    fun `la pantalla monta el riel con secciones derivadas por buildRailDay`() {
        assertTrue("RailTimeline(" in screen, "🔴 La pantalla no monta `RailTimeline` (S3).")
        assertTrue(
            "buildAgendaGroups(" in screen,
            "🔴 La pantalla agrupa por su cuenta: el agrupador existente es el que respeta el " +
                "orden de días y el grupo `Sin fecha de inicio` al final.",
        )
        assertTrue(
            "RailSection(" in screen,
            "🔴 La pantalla no construye `RailSection`: el riel no tiene qué pintar.",
        )
        assertTrue(
            "buildRailDay(" in screen,
            "🔴 La pantalla no deriva el día con `buildRailDay`: el eje, los carriles y los " +
                "ticks de T1 se perderían.",
        )
        assertTrue(
            "undatedRows = " in screen,
            "🔴 La sección `Sin fecha de inicio` se queda sin filas: el grupo sin fecha tiene " +
                "que llegar al riel o esas actividades desaparecen de la pantalla.",
        )
    }

    // ── 3 · S6 · el salto a hoy usa el índice del riel ─────────────────────────────────

    @Test
    fun `el salto a un dia usa railItemIndexOf`() {
        assertTrue(
            "railItemIndexOf(" in screen,
            "🔴 La pantalla calcula el índice del salto por su cuenta. `railItemIndexOf` es la " +
                "ÚNICA fuente de la forma de la lista; un índice local aterriza en otro día en " +
                "cuanto una sección se pliega.",
        )
        assertTrue(
            "listState.scrollToItem(" in screen,
            "🔴 El salto no mueve el `LazyListState` del riel: el control diría `Hoy` y no iría.",
        )
        assertTrue(
            "onToggleDay = " in screen,
            "🔴 El riel no recibe el plegado por día (S5): el chevron del encabezado no tendría " +
                "consumidor.",
        )
    }

    @Test
    fun `el plegado por dia y el mapa se recuerdan al recrear la pantalla`() {
        assertTrue(
            "listSaver" in screen && "collapsedDaysIso" in screen,
            "🔴 El estado de plegado no se guarda como lista de cadenas ISO: al rotar, los días " +
                "plegados volverían a estar abiertos.",
        )
        val saveable = Regex("(var|val) (densityExpanded|visibleMonthIso) by rememberSaveable")
            .findAll(screen)
            .map { it.groupValues[2] }
            .toSet()
        assertEquals(
            setOf("densityExpanded", "visibleMonthIso"),
            saveable,
            "🔴 El plegado del mapa y el mes visible tienen que sobrevivir a la recreación: son " +
                "estado de presentación que el usuario eligió.",
        )
    }

    // ── 4 · S4 · el mapa de densidad va montado y sale del filtro ──────────────────────

    @Test
    fun `el mapa de densidad se monta y se deriva de las filas filtradas`() {
        assertTrue("MonthDensityMap(" in screen, "🔴 El mapa de densidad no está montado (S4).")
        assertTrue(
            "buildMonthDensity(" in screen,
            "🔴 La pantalla no deriva la densidad: el mapa no tendría qué pintar.",
        )
        val densityCall = Regex("buildMonthDensity\\(([\\s\\S]*?)\\)").find(screen)
            ?.groupValues?.get(1) ?: ""
        assertTrue(
            "filteredRows" in densityCall,
            "🔴 La densidad no sale de `filteredRows`: un mapa sobre `uiState.actividades` " +
                "ignoraría la pestaña y el filtro de hijos. Llamada real:\n$densityCall",
        )
    }

    // ── 5 · S7 · un único alta, circular ───────────────────────────────────────────────

    @Test
    fun `el alta es un unico FAB y la barra superior ya no tiene el mas`() {
        assertTrue(
            "floatingActionButton = {" in screen,
            "🔴 El alta no vive en el slot `floatingActionButton` del `Scaffold` (S7).",
        )
        assertTrue("FloatingActionButton(" in screen, "🔴 No hay FAB de alta (S7).")
        assertEquals(
            1,
            Regex("Icons\\.Default\\.Add").findAll(screen).count(),
            "🔴 Hay más de un ícono de alta en la pantalla. S7 pide UN solo camino de alta: el " +
                "FAB circular de abajo.",
        )
        assertEquals(
            1,
            Regex("contentDescription = \"Nueva actividad\"").findAll(screen).count(),
            "🔴 El alta se anuncia más de una vez (o ninguna): el FAB es el ÚNICO control de " +
                "alta de la pantalla (S7).",
        )
        assertTrue(
            "shape = CircleShape" in screen,
            "🔴 El FAB de alta dejó de ser circular (S7: «en un circulo abajo»).",
        )
    }

    // ── 6 · El reloj se lee fuera de composición ──────────────────────────────────────

    @Test
    fun `el reloj del riel se lee una vez por minuto y nunca en composicion`() {
        assertEquals(
            1,
            Regex("Clock\\.System\\.now\\(\\)").findAll(screen).count(),
            "🔴 La pantalla lee el reloj en más de un sitio (o en composición): una lectura por " +
                "composición obliga a recomponer en cada frame para mantenerla fresca.",
        )
        assertTrue(
            "delay(" in screen,
            "🔴 El reloj no se refresca con un `delay`: la línea de «ahora» se congelaría en el " +
                "minuto en que se abrió la pantalla.",
        )
        assertTrue(
            "LaunchedEffect(zone)" in screen,
            "🔴 La lectura del reloj no vive en un `LaunchedEffect`: no se cancelaría al salir " +
                "de composición.",
        )
        assertTrue(
            "nowMinute = " in screen,
            "🔴 La pantalla no le pasa el minuto actual al riel: la línea de «ahora» no se pinta.",
        )
    }

    // ── 7 · T7 (S11) · Los estados del riel y la retención de foco ─────────────────────

    @Test
    fun `el esqueleto del riel tiene forma de riel y no la de una fila`() {
        assertTrue(
            "RailSkeleton(" in screen,
            "🔴 El estado de carga no monta un esqueleto del riel.",
        )
        assertFalse(
            "ListSkeleton(" in screen,
            "🔴 El estado de carga volvió a montar `ListSkeleton`: su forma es la de una fila de " +
                "agenda (bloque de fecha + dos líneas) y promete una lista que la pantalla ya no es.",
        )
        val chrome = stripComments(source("CalendarChrome.kt"))
        assertTrue(
            "internal fun RailSkeleton(" in chrome,
            "🔴 El esqueleto del riel no existe en `CalendarChrome.kt`, junto al de filas.",
        )
        val skeleton = chrome.substringAfter("internal fun RailSkeleton(")
        assertTrue(
            "timeColumn" in skeleton,
            "🔴 El esqueleto no tiene canaleta: el riel sin gutter no es el riel.",
        )
        assertTrue(
            "RailGeometry.DpPerHour" in skeleton && "RailGeometry.MinBlockHeight" in skeleton,
            "🔴 Los bloques del esqueleto no son proporcionales: la altura sale de la escala del " +
                "riel (`DpPerHour`) con el piso de toque (`MinBlockHeight`), no de un dp inventado.",
        )
        assertEquals(
            1,
            Regex("clearAndSetSemantics").findAll(skeleton).count(),
            "🔴 El esqueleto habla más de una vez (o ninguna): es UN nodo semántico con su rótulo.",
        )
    }

    @Test
    fun `carga, error y vacio se excluyen entre si`() {
        // Una `when` con cuatro ramas: cargando, error, vacío y contenido. Si dos de las tres
        // primeras se pintan como hermanas, el riel aparece debajo con el estado de encima (o al
        // revés) y el usuario ve una lista que todavía no existe.
        val inicio = screen.indexOf("Box(modifier = Modifier.fillMaxWidth().weight(1f))")
        assertTrue(
            inicio >= 0,
            "🔴 El contenido de la pantalla ya no vive en el `Box` con `weight(1f)`: la región de los " +
                "estados no se puede acotar.",
        )
        val region = screen.substring(inicio)
        val carga = region.indexOf("uiState.isLoading && uiState.actividades.isEmpty() -> RailSkeleton()")
        val error = region.indexOf("uiState.listError != null -> EmptyState(")
        val vacio = region.indexOf("\"No hay actividades\"")
        val riel = region.indexOf("RailTimeline(")
        assertTrue(carga >= 0, "🔴 La rama de carga no es `isLoading && actividades.isEmpty()` → `RailSkeleton()`.")
        assertTrue(error > carga, "🔴 El error no es la rama siguiente a la carga.")
        assertTrue(vacio > error, "🔴 El vacío no es la rama siguiente al error.")
        assertTrue(riel > vacio, "🔴 El riel no es la última rama: se pintaría debajo de los estados.")
        assertEquals(1, Regex("RailSkeleton\\(").findAll(screen).count(), "🔴 El esqueleto se monta más de una vez.")
        assertEquals(1, Regex("RailTimeline\\(").findAll(screen).count(), "🔴 El riel se monta más de una vez.")

        // El error conserva su acción y el vacío usa el MISMO alta que el FAB (S7: un solo camino).
        assertTrue(
            "actionLabel = \"Reintentar\"" in region && "onAction = onRetryList" in region,
            "🔴 El estado de error perdió su reintento.",
        )
        assertTrue(
            "actionLabel = \"Crear actividad\"" in region && "onAction = onOpenCreate" in region,
            "🔴 El vacío no ofrece el alta: la única entrada quedaría en el FAB, fuera de la vista.",
        )
        assertEquals(
            1,
            Regex("onClick = onOpenCreate").findAll(screen).count(),
            "🔴 El alta del vacío y el FAB no comparten callback: dos caminos de alta distintos.",
        )
    }

    @Test
    fun `cada bloque del riel registra su requester y la pantalla lo poda`() {
        assertTrue(
            "focusRequesters = focusRequesters" in screen,
            "🔴 La pantalla no entrega el mapa de foco al riel: el retorno desde la hoja cae siempre " +
                "en el contenedor (el agujero que dejó `c817fd0`).",
        )
        assertTrue(
            "retainAll(" in screen,
            "🔴 La pantalla dejó de podar el mapa de foco: un id que ya no existe se quedaría con un " +
                "requester de un nodo muerto.",
        )
        val canvas = stripComments(source("RailCanvas.kt"))
        assertTrue(
            "SideEffect { focusRequesters[" in canvas,
            "🔴 El bloque del riel no registra su `FocusRequester` con el `SideEffect` de " +
                "`ActivityCard`: es el MISMO mecanismo, no uno paralelo.",
        )
        assertTrue(
            "focusRequester(focusRequester)" in canvas,
            "🔴 El bloque registra el requester pero no lo monta: el nodo no existe.",
        )
        val timeline = stripComments(source("RailTimeline.kt"))
        assertTrue(
            "focusRequesters: MutableMap<String, FocusRequester>" in timeline,
            "🔴 `RailTimeline` no recibe el mapa: el riel no puede registrar sus filas.",
        )
        assertEquals(
            3,
            Regex("focusRequesters = focusRequesters").findAll(timeline).count(),
            "🔴 El riel no reenvía el mapa por sus tres escalones (fila sin fecha, cuerpo del día " +
                "y lienzo del día): alguna superficie se queda sin registrar y su retorno de foco " +
                "cae al contenedor.",
        )
        assertTrue(
            "focusRequesters = focusRequesters" in canvas,
            "🔴 El lienzo no entrega el mapa al bloque: el registro del `SideEffect` no llega nunca " +
                "a la pantalla.",
        )
    }

    // ── 8 · PRESERVE · lo que T6/T7 NO tocan ──────────────────────────────────────────

    @Test
    fun `los filtros, la barra y el snackbar siguen montados como antes`() {
        assertTrue("ActivityFiltersRow(" in screen, "🔴 Se perdió la fila de filtros de pestaña.")
        assertTrue("ChildrenFiltersRow(" in screen, "🔴 Se perdió la fila de filtros de hijos.")
        assertTrue(
            "onOpenNotifications = onOpenNotifications" in screen,
            "🔴 La campana dejó de recibir su callback: se perdió el wiring de notificaciones.",
        )
        assertTrue(
            "SnackbarHost(snackbarHostState)" in screen,
            "🔴 Se perdió el `SnackbarHost` único de la pantalla (snackbar + Deshacer).",
        )
    }

    // ── 9 · T10 (S13, S14) · el mapa: chevron solo y filtros adentro ──────────────────

    @Test
    fun `mostrar y ocultar el mapa es un chevron sin texto con nombre y estado`() {
        val map = stripComments(source("MonthDensityMap.kt"))
        val toggle = functionBody(map, "private fun DensityToggle(")

        assertFalse(
            "Text(" in toggle,
            "🔴 El control del mapa volvió a tener texto visible. S13: «con solo los chevron es " +
                "suficiente» — el nombre accesible no se pinta.",
        )
        assertFalse(
            "\"Ocultar\"" in toggle || "\"Ver el mes\"" in toggle,
            "🔴 Los rótulos visibles del pill volvieron: el control es SÓLO el chevron (S13).",
        )
        assertTrue(
            "contentDescription = " in toggle,
            "🔴 El chevron perdió su nombre accesible: queda un botón anónimo (S11).",
        )
        assertTrue(
            "stateDescription = " in toggle,
            "🔴 El chevron no anuncia si el mapa está expandido o plegado (S11/S13).",
        )
        assertTrue(
            "Icons.Filled.ExpandLess" in toggle && "Icons.Filled.ExpandMore" in toggle,
            "🔴 El chevron dejó de decir el sentido: arriba = plegar, abajo = desplegar (S13).",
        )
        assertTrue(
            "toggleable(" in toggle,
            "🔴 El control dejó de ser `toggleable`: sin valor de estado, TalkBack no sabe qué " +
                "está mostrando.",
        )
        assertTrue(
            "AgendaGeometry.touchTargetLegacy" in toggle,
            "🔴 El chevron bajó del mínimo de 44dp (S11): `touchTargetLegacy` (48dp) es su piso.",
        )
        assertEquals(
            1,
            Regex("toggleable\\s*\\(").findAll(map).count(),
            "🔴 Hay más de un control de mostrar/ocultar en el mapa: S13 deja UN chevron.",
        )
        assertEquals(
            1,
            Regex("onToggleExpanded").findAll(screen).count(),
            "🔴 La pantalla cablea el plegado del mapa en más de un sitio: el chevron del panel " +
                "es el ÚNICO control.",
        )
    }

    @Test
    fun `los dos filtros se montan dentro del panel y no se pliegan con el mapa`() {
        val panel = callBody(screen, "MonthDensityMap")

        assertTrue(
            "ActivityFiltersRow(" in panel,
            "🔴 La fila de tipos (Eventos / Obligaciones) no está DENTRO de la llamada al panel " +
                "(S14): sigue en una fila aparte debajo del calendario.",
        )
        assertTrue(
            "ChildrenFiltersRow(" in panel,
            "🔴 La fila de hijos no está DENTRO de la llamada al panel (S14).",
        )
        listOf("ActivityFiltersRow", "ChildrenFiltersRow").forEach { row ->
            assertEquals(
                1,
                Regex("$row\\s*\\(").findAll(screen).count(),
                "🔴 `$row` se monta más de una vez: la fila vieja debajo del panel no se retiró.",
            )
        }

        val map = stripComments(source("MonthDensityMap.kt"))
        val panelSource = functionBody(map, "internal fun MonthDensityMap(")
        assertEquals(
            1,
            Regex("if \\(expanded\\) \\{").findAll(panelSource).count(),
            "🔴 El plegado del panel dejó de tener UN bloque: si la grilla y los filtros cuelgan " +
                "de condiciones distintas, se pliegan por separado.",
        )
        val collapse = panelSource.substringAfter("if (expanded) {")
        listOf("DensityWeekdayCaps()", "DensityGrid(", "DensityLegend()").forEach { part ->
            assertTrue(
                part in collapse,
                "🔴 `$part` quedó FUERA de la zona plegable: el chevron dejaría de plegar el mes.",
            )
        }
        assertFalse(
            "filters()" in collapse,
            "🔴 Los filtros volvieron DENTRO del plegado: al cerrar el mapa desaparecen, y son " +
                "permanentes (S14 + decisión de producto de T10).",
        )
        val beforeCollapse = panelSource.substringBefore("if (expanded) {")
        assertTrue(
            "filters()" in beforeCollapse,
            "🔴 Los filtros dejaron de montarse SIEMPRE dentro del panel: plegar el mapa los " +
                "escondería.",
        )
        assertTrue(
            beforeCollapse.indexOf("filters()") > beforeCollapse.indexOf("DensityHeader("),
            "🔴 Los filtros quedaron ENCIMA del encabezado del calendario.",
        )
    }

    @Test
    fun `el mapa de densidad abre plegado por defecto`() {
        assertTrue(
            Regex("var densityExpanded by rememberSaveable \\{ mutableStateOf\\(false\\) \\}")
                .containsMatchIn(screen),
            "🔴 El mapa volvió a abrir desplegado: a 384dp el panel mide ~513dp y el riel queda " +
                "en ~130dp, que no es usable. El mapa es una lente ocasional, no el estado de " +
                "reposo.",
        )
        assertTrue(
            "PLEGADO por defecto" in rawScreen,
            "🔴 El KDoc no dice que el mapa abre plegado: el default responde a una medida y tiene " +
                "que quedar escrito donde se lee.",
        )
    }

    // ── 10 · T10 (S12) · «Hoy» sin nada hoy avisa, no salta en silencio ───────────────

    @Test
    fun `hoy sin actividades deja un aviso en el riel en vez de saltar en silencio`() {
        val rail = stripComments(source("RailTimeline.kt"))
        assertTrue(
            "RailTodayEmptyNotice(" in rail,
            "🔴 El riel no monta el aviso de «hoy sin actividades» (S12).",
        )
        val notice = functionBody(rail, "private fun RailTodayEmptyNotice(")
        assertTrue(
            "liveRegion = LiveRegionMode.Polite" in notice,
            "🔴 El aviso no se anuncia a tecnología asistiva: aparece y TalkBack no dice nada.",
        )
        assertTrue(
            "RAIL_TODAY_EMPTY_TITLE" in notice && "\"Hoy no tiene actividades\"" in rail,
            "🔴 El aviso perdió su copy: S12 pide decir que hoy no hay actividades.",
        )
        assertTrue(
            "onCreate" in notice,
            "🔴 El aviso no ofrece el alta: informa y deja al usuario sin salida (S12 + S7).",
        )

        val jump = screen.substringAfter("val goToToday").substringBefore("val shiftMonth")
        assertTrue(
            "todayEmptyNotice = " in jump,
            "🔴 El toque de `Hoy` sigue saltando en silencio: el control no decide nada cuando el " +
                "día de hoy no tiene actividades (S12).",
        )
        assertTrue(
            "pendingJumpDate = today" in jump,
            "🔴 El toque de `Hoy` perdió el salto: cuando hoy SÍ tiene actividades tiene que " +
                "desplegar y llevar el riel a su encabezado.",
        )
        assertTrue(
            "todayEmptyNotice = false" in screen.substringAfter("onSelectDay = {").substringBefore("onPrevMonth"),
            "🔴 El aviso no se reemplaza al elegir otro día en el mapa: quedaría pegado al riel.",
        )
        assertTrue(
            "todayEmptyNotice = showTodayEmptyNotice" in screen,
            "🔴 El riel no recibe el aviso: la pantalla lo decidiría y nadie lo pintaría (S12).",
        )
        assertTrue(
            "onCreateFromEmptyToday = onOpenCreate" in screen,
            "🔴 La acción del aviso no es el MISMO alta que el FAB (S7: un solo camino de alta).",
        )
    }

    // ── 11 · T10 (S15) · el anclaje inicial es una regla pura ─────────────────────────

    @Test
    fun `el anclaje inicial del riel sale de una funcion pura`() {
        assertTrue(
            "railInitialAnchor(" in screen,
            "🔴 La pantalla no usa `railInitialAnchor`: el anclaje volvió a ser una condición " +
                "inline en composición, que no se puede probar (S15).",
        )
        assertFalse(
            "startMinute <= nowMinute" in screen,
            "🔴 La pantalla decide «en curso» por su cuenta: la regla vive en `railInitialAnchor`.",
        )
        // El anclaje tiene que ser la posición de CREACIÓN del `LazyListState`: un `scrollToItem`
        // desde un efecto llega un frame tarde y se ve el salto (el defecto que encontró el
        // humano en el dispositivo).
        assertTrue(
            "rememberLazyListState(" in screen && "initialFirstVisibleItemIndex" in screen &&
                "initialFirstVisibleItemScrollOffset" in screen,
            "🔴 El riel no se crea anclado: si el estado arranca en el tope y se corrige después, " +
                "el usuario ve la lista arriba y un salto.",
        )
        assertEquals(
            1,
            Regex("initialFirstVisibleItemIndex").findAll(screen).count(),
            "🔴 Hay más de un estado de lista anclado: el riel tiene UNO solo.",
        )
        val index = stripComments(source("RailIndex.kt"))
        val anchor = functionBody(index, "internal fun railInitialAnchor(")
        assertTrue(
            "RailAnchorKind.InProgress" in anchor,
            "🔴 La regla perdió la rama «hay algo en curso»: S15 ancla en la línea de ahora sólo " +
                "si hay una actividad en curso.",
        )
        assertTrue(
            "RailAnchorKind.FirstBlock" in anchor,
            "🔴 La regla perdió la rama de respaldo «primer bloque del día».",
        )
    }

    // ── 12 · Hallazgos del dispositivo (384dp) que entraron en T10 ────────────────────

    @Test
    fun `el pill de hoy va abajo a la izquierda y no dentro del slot del FAB`() {
        val fabSlot = screen.substringAfter("floatingActionButton =").substringBefore("snackbarHost =")
        assertFalse(
            "RailTodayButton(" in fabSlot,
            "🔴 El pill «Hoy» volvió al slot `floatingActionButton`: ese slot alinea a la derecha y " +
                "el pill queda pegado al FAB. En el artboard va abajo a la IZQUIERDA.",
        )
        assertTrue(
            "FloatingActionButton(" in fabSlot,
            "🔴 El FAB dejó de montarse en su slot.",
        )
        val pill = callBody(screen, "RailTodayButton")
        assertTrue(
            ".align(Alignment.BottomStart)" in pill,
            "🔴 El pill «Hoy» no está anclado a `BottomStart`: volvería a quedar alineado a la " +
                "derecha por la `Column` centrada.",
        )
        assertTrue(
            "RAIL_FLOATING_CLEARANCE" in screen,
            "🔴 El pill y el FAB dejaron de tener su franja reservada: taparían el último bloque.",
        )
    }

    @Test
    fun `la canaleta horaria usa el ancho del sistema con aire a la izquierda`() {
        val rail = stripComments(source("RailTimeline.kt"))
        val body = functionBody(rail, "private fun RailDayBody(")
        assertTrue(
            "padding(start = Spacing.lg, end = Spacing.md)" in body,
            "🔴 La canaleta volvió a arrancar en x=0: en 384dp los `07:00` quedan pegados al " +
                "borde izquierdo, sin aire.",
        )
        assertTrue(
            "RailHourGutter(" in body && "RailDayCanvas(" in body,
            "🔴 El cuerpo del día dejó de ser [canaleta | lienzo]: la línea tiene que arrancar " +
                "donde arranca el carril 0 de los bloques.",
        )
        val canvas = stripComments(source("RailCanvas.kt"))
        val gutter = functionBody(canvas, "internal fun RailHourGutter(")
        assertTrue(
            "width(AgendaGeometry.timeColumn)" in gutter,
            "🔴 La canaleta dejó de usar el ancho del sistema (`AgendaGeometry.timeColumn`).",
        )
        assertTrue(
            "textAlign = TextAlign.End" in gutter,
            "🔴 El rótulo de hora dejó de estar alineado a la derecha contra la grilla.",
        )
    }

    @Test
    fun `el encabezado del panel pone densidad al lado del mes y no debajo`() {
        val map = stripComments(source("MonthDensityMap.kt"))
        val header = functionBody(map, "private fun DensityHeader(")
        assertFalse(
            "Column(modifier = Modifier.weight(1f))" in header,
            "🔴 Volvió la columna que apila «Densidad» debajo del mes: el artboard los pone en " +
                "la misma línea.",
        )
        assertTrue(
            header.indexOf("DENSITY_CAPTION") > 0 &&
                header.indexOf("DENSITY_CAPTION") < header.indexOf("Icons.AutoMirrored.Filled.KeyboardArrowRight"),
            "🔴 «Densidad» dejó de ser el caption de la misma fila que el mes (antes de la flecha " +
                "siguiente).",
        )
    }

    @Test
    fun `los overlays de detalle, alta y motivo siguen montados`() {
        assertTrue("ActividadDetailSheet(" in screen, "🔴 Se perdió la hoja de detalle.")
        assertTrue("CreateActivitySheet(" in screen, "🔴 Se perdió la hoja de alta.")
        assertTrue("SwipeReasonDialog(" in screen, "🔴 Se perdió el diálogo del motivo.")
    }

    @Test
    fun `el completado sigue pasando por la verificacion del otro progenitor`() {
        assertTrue(
            "onComplete = viewModel::completeActivity" in screen,
            "🔴 La pantalla dejó de cablear el finalizado: el flujo `Confirmar` → *Por " +
                "verificar* → `Cumplida` (S8) es el único camino de completado.",
        )
        assertTrue(
            "onConfirm = viewModel::confirmAssignment" in screen,
            "🔴 La pantalla dejó de cablear la confirmación, primer paso del flujo de S8.",
        )
        assertFalse(
            "Checkbox(" in screen,
            "🔴 Apareció un checkbox en la pantalla: S8 prohíbe un camino de completado que " +
                "saltee la verificación del otro progenitor.",
        )
    }
}
