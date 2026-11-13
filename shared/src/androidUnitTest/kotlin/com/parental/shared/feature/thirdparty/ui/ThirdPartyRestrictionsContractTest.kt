package com.parental.shared.feature.thirdparty.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Contrato de la vista del terciario temporal — artboard
 * `design/terciario-ui/03-permiso-explicito.html`, spec
 * `odd/tasks/terciarios-temporales.md` (S1–S13) y decisiones L7.
 *
 * Mismo mecanismo que `HomeRingAndSectionsContractTest`: no hay Compose UI tests en el
 * proyecto, así que lo que se puede verificar de la UI es el FUENTE. Cada aserción nombra
 * la spec que protege; si alguien agrega un botón prohibido, el test cae con el motivo.
 */
class ThirdPartyRestrictionsContractTest {

    private val uiDir: File by lazy { thirdPartyUiDir() }

    private fun thirdPartyUiDir(): File {
        var candidate: File? = File("").absoluteFile
        while (candidate != null) {
            val dir = File(
                candidate,
                "src/commonMain/kotlin/com/parental/shared/feature/thirdparty/ui",
            )
            if (dir.isDirectory) return dir
            candidate = candidate.parentFile
        }
        fail("No se encuentra `feature/thirdparty/ui` subiendo desde ${File("").absolutePath}")
    }

    private fun source(name: String): String = File(uiDir, name).readText()

    private fun allUiSources(): List<Pair<String, String>> =
        uiDir.listFiles { file -> file.extension == "kt" }
            .orEmpty()
            .sortedBy { it.name }
            .map { it.name to it.readText() }

    // ── El permiso es la jerarquía (L7) ─────────────────────────────────

    @Test
    fun `la tarjeta de permiso nombra a quien autorizo y la vigencia`() {
        val home = source("ThirdPartyHomeScreen.kt")
        assertTrue(
            "\"Autorizado por" in home,
            "🔴 S7/S12: la tarjeta dice quién autorizó al terciario.",
        )
        assertTrue(
            "\"Acceso temporal\"" in home,
            "🔴 D3: el permiso se rotula como temporal, no como membresía.",
        )
        assertTrue(
            "\"Vence el \"" in home && "daysLeft" in home,
            "🔴 D3: la vigencia se dice en palabras además de en la barra.",
        )
    }

    @Test
    fun `el popup de permiso existe y se cierra con Entendido`() {
        val dialogs = source("ThirdPartyPermissionDialogs.kt")
        assertTrue(
            "\"Tu permiso, en concreto\"" in dialogs,
            "🔴 L7: el permiso se explica en un popup, no con notas dentro del producto.",
        )
        assertTrue("\"Entendido\"" in dialogs, "🔴 L7: el popup tiene una única salida.")
        assertTrue(
            "crear actividades" in dialogs.lowercase(),
            "🔴 S4: el popup enumera que no puede crear actividades.",
        )
        assertTrue(
            "Aceptar o rechazar" in dialogs,
            "🔴 S8: el popup explica que al asignarle ya cuenta como que asiste.",
        )
    }

    @Test
    fun `el popup de limites vive en el detalle`() {
        val dialogs = source("ThirdPartyPermissionDialogs.kt")
        val detail = source("ThirdPartyActivitySheet.kt")
        assertTrue(
            "\"Lo que no podés hacer acá\"" in dialogs,
            "🔴 L7: las prohibiciones se explican en un popup del detalle.",
        )
        assertTrue(
            "LimitsDialog(" in detail,
            "🔴 L7: el detalle abre ese popup desde el icono de información.",
        )
    }

    @Test
    fun `el onboarding se muestra una sola vez por usuario`() {
        val viewModel = source("ThirdPartyViewModel.kt")
        assertTrue(
            "ui.third_party_permit_seen." in viewModel,
            "🔴 L7: el popup de permiso aparece la primera vez, no en cada ingreso.",
        )
        assertTrue(
            "saveFlag(permitSeenKey(currentUserId)" in viewModel,
            "🔴 L7: al cerrarlo se persiste la marca por usuario.",
        )
    }

    // ── Actividades: chips por estado, sin crear ni deslizar ────────────

    @Test
    fun `los chips son los cuatro del artboard y no los tabs de progenitores`() {
        val policy = source("ThirdPartyPolicy.kt")
        val activities = source("ThirdPartyActivitiesScreen.kt")
        for (label in listOf("Asignadas", "En progreso", "Vencidas", "Finalizadas")) {
            assertTrue("(\"$label\")" in policy, "🔴 L7: el chip \"$label\" existe en la política.")
        }
        assertTrue(
            "ThirdPartyFilter.entries" in activities,
            "🔴 L7: la fila de chips se pinta desde el enum, no de una lista suelta.",
        )
        assertTrue(
            "ActivityTab" !in activities,
            "🔴 L7: el terciario no usa los tabs Todos/Eventos/Obligaciones de los progenitores.",
        )
    }

    @Test
    fun `no hay boton de alta ni gestos de swipe`() {
        for ((name, text) in allUiSources()) {
            assertTrue(
                "FloatingActionButton" !in text,
                "🔴 S4: `$name` no puede ofrecer el alta de actividades.",
            )
            assertTrue(
                "ActivitySwipeBox" !in text && "SwipeAction" !in text,
                "🔴 S8/S9: `$name` no puede montar swipe de cancelar/rechazar.",
            )
        }
    }

    @Test
    fun `no hay acciones de rechazar cancelar reasignar ni borrar`() {
        for ((name, text) in allUiSources()) {
            for (verb in listOf("\"Reasignar\"", "\"No asistir\"", "\"Eliminar\"", "\"Cancelar\"")) {
                assertTrue(
                    verb !in text,
                    "🔴 S8/S9: `$name` no puede ofrecer la acción $verb.",
                )
            }
        }
    }

    @Test
    fun `la unica accion sobre una tarea es finalizar`() {
        val detail = source("ThirdPartyActivitySheet.kt")
        assertTrue(
            "\"Marcar como finalizada\"" in detail,
            "🔴 S8/S11: el detalle ofrece finalizar la tarea.",
        )
        assertTrue(
            "al verificar" in detail,
            "🔴 S11: el pie aclara que el cierre completo no es del terciario.",
        )
        assertTrue(
            "\"Adjuntar archivo\"" in detail,
            "🔴 S10: el asignado puede adjuntar archivos a su tarea.",
        )
    }

    // ── Nada del vínculo (S5) ───────────────────────────────────────────

    @Test
    fun `la vista no pide ni pinta datos del vinculo`() {
        for ((name, text) in allUiSources()) {
            for (forbidden in listOf("ComplianceRingCard", "HomeMetricStrip", "ChildrenSection", "getHomeSummary")) {
                assertTrue(
                    forbidden !in text,
                    "🔴 S5: `$name` no puede mostrar $forbidden (datos de los progenitores).",
                )
            }
        }
    }

    @Test
    fun `el nav del terciario tiene solo inicio y actividades`() {
        val root = source("ThirdPartyRoot.kt")
        assertTrue(
            "listOf(BottomNavItem.Home, BottomNavItem.Actividades)" in root,
            "🔴 S9: el terciario navega sólo a Inicio y Actividades.",
        )
        for (name in listOf("ThirdPartyHomeScreen.kt", "ThirdPartyActivitiesScreen.kt")) {
            val text = source(name)
            assertTrue(
                "THIRD_PARTY_NAV_ITEMS" in text,
                "🔴 S9: `$name` usa el juego de ítems reducido.",
            )
            assertTrue(
                "BottomNavItem.Gastos" !in text && "BottomNavItem.Terceros" !in text,
                "🔴 S9: `$name` no expone Gastos ni Terceros.",
            )
        }
    }

    // ── La política la manda el servidor (S13) ──────────────────────────

    @Test
    fun `el cliente no recalcula la politica de la actividad`() {
        val policy = source("ThirdPartyPolicy.kt")
        assertTrue(
            "actividad.canComplete" in policy,
            "🔴 S13: finalizar se decide con el flag del servidor, no con el estado.",
        )
        for ((name, text) in allUiSources()) {
            assertTrue(
                "canComplete =" !in text,
                "🔴 S13: `$name` no puede asignar el flag que calcula el servidor.",
            )
        }
    }

    @Test
    fun `la lista se filtra por asignado aunque el back no lo haga todavia`() {
        val viewModel = source("ThirdPartyViewModel.kt")
        val policy = source("ThirdPartyPolicy.kt")
        assertTrue(
            "thirdPartyVisibleActivities(" in policy,
            "🔴 S6: existe el filtro por assignee como segunda barrera.",
        )
        assertTrue(
            "thirdPartyVisibleActivities(" in viewModel,
            "🔴 S6: el ViewModel aplica ese filtro antes de publicar la lista.",
        )
    }

    @Test
    fun `adjuntar es del asignado y no habilita renombrar ni borrar`() {
        val policy = source("ThirdPartyPolicy.kt")
        assertTrue(
            "isAttachmentsEditable(status, assignedTo)" in policy,
            "🔴 S10: la editabilidad de archivos reutiliza la regla de la actividad.",
        )
        for ((name, text) in allUiSources()) {
            assertTrue(
                "renameAttachment" !in text && "deleteAttachment" !in text,
                "🔴 S9: `$name` no expone renombrar/borrar archivos.",
            )
        }
    }
}
