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
