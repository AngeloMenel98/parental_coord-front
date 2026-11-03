package com.parental.shared.feature.activities.ui.detail

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * T21 — contrato de FUENTE de las acciones "Asignar" y "Reasignar" en la hoja de detalle.
 *
 * ⚠ Es una prueba de fuente y lo es a propósito: este repo no tiene `createComposeRule`
 * ni `androidTest`, así que no se puede componer la hoja para contar nodos. Leer el texto
 * es más honesto que fingir una prueba de UI que no existe.
 */
class AssignActionContractTest {

    private fun activitiesPath(relative: String): File {
        var candidate: File? = File("").absoluteFile
        while (candidate != null) {
            val dir = File(
                candidate,
                "src/commonMain/kotlin/com/parental/shared/feature/activities/$relative",
            )
            if (dir.isDirectory) return dir
            candidate = candidate.parentFile
        }
        fail("No se encuentra `src/commonMain/.../activities/$relative` subiendo desde ${File("").absolutePath}")
    }

    private fun source(relative: String): String =
        File(activitiesPath(relative.substringBeforeLast('/')), relative.substringAfterLast('/')).readText()

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

    private val detailSheet: String by lazy { stripComments(source("ui/detail/ActividadDetailSheet.kt")) }
    private val viewModel: String by lazy { stripComments(source("ui/ActivitiesViewModel.kt")) }
    private val models: String by lazy { stripComments(source("domain/model/ActivitiesModels.kt")) }

    // ── gates por flags del servidor ─────────────────────────────────────

    @Test
    fun `Asignar se gatea por canAssign y no por el estado created`() {
        assertTrue(
            "actividad.canAssign" in detailSheet,
            "🔴 \"Asignar\" no se gatea por `canAssign`: el cliente dejaría de obedecer el flag del servidor.",
        )
        assertFalse(
            "status == \"created\"" in detailSheet || "\"created\" == status" in detailSheet,
            "🔴 El cliente vuelve a derivar \"Asignar\" del estado: eso es justo la segunda copia de la política que T21 prohíbe.",
        )
    }

    @Test
    fun `Reasignar se gatea por canReassign y no por el estado not_assisting`() {
        assertTrue(
            "actividad.canReassign" in detailSheet,
            "🔴 \"Reasignar\" no se gatea por `canReassign`: el cliente dejaría de obedecer el flag del servidor.",
        )
        assertFalse(
            "status == \"not_assisting\"" in detailSheet || "\"not_assisting\" == status" in detailSheet,
            "🔴 El cliente vuelve a derivar \"Reasignar\" del estado: eso es justo la segunda copia de la política que T21 prohíbe.",
        )
    }

    @Test
    fun `los flags existen en los dos modelos con default false`() {
        assertEquals(
            2,
            Regex("val canAssign: Boolean = false").findAll(models).count(),
            "🔴 `canAssign` debe existir en `Actividad` y en `ActividadDetalle`, con default `false` para degradar contra un servidor viejo.",
        )
        assertEquals(
            2,
            Regex("val canReassign: Boolean = false").findAll(models).count(),
            "🔴 `canReassign` debe existir en `Actividad` y en `ActividadDetalle`, con default `false` para degradar contra un servidor viejo.",
        )
    }

    // ── picker reutiliza memberCandidates ────────────────────────────────

    @Test
    fun `el picker recibe el mismo memberCandidates que el alta`() {
        assertTrue(
            "memberCandidates: List<MemberCandidate>" in detailSheet,
            "🔴 La hoja de detalle no recibe `memberCandidates`: no puede reutilizar la lista del vínculo.",
        )
        assertTrue(
            "memberCandidates = memberCandidates" in detailSheet ||
                "memberCandidates = uiState.memberCandidates" in stripComments(source("ui/ActivitiesScreen.kt")),
            "🔴 La pantalla no pasa `uiState.memberCandidates` a la hoja de detalle.",
        )
    }

    @Test
    fun `el picker no hace ninguna peticion propia`() {
        assertFalse(
            "bondsApi" in detailSheet ||
                "activitiesApi" in detailSheet ||
                "loadAll(" in detailSheet ||
                "viewModelScope" in detailSheet,
            "🔴 El picker de la hoja de detalle no debe hacer peticiones: la lista de candidatos la trae el ViewModel.",
        )
    }

    @Test
    fun `el picker lista los nombres de los candidatos`() {
        assertTrue(
            "memberCandidates.forEach" in detailSheet,
            "🔴 El picker no itera `memberCandidates`: no puede mostrar los miembros del vínculo.",
        )
        assertTrue(
            "candidate.name" in detailSheet,
            "🔴 El picker no muestra el nombre del candidato.",
        )
    }

    // ── copy en espanol y conexion con ViewModel ─────────────────────────

    @Test
    fun `los labels son los acordados en espanol`() {
        assertTrue(
            "\"Asignar\"" in detailSheet,
            "🔴 Falta el botón/label \"Asignar\".",
        )
        assertTrue(
            "\"Reasignar\"" in detailSheet,
            "🔴 Falta el botón/label \"Reasignar\".",
        )
        assertTrue(
            "\"Asignar a\"" in detailSheet,
            "🔴 Falta el título del picker \"Asignar a\".",
        )
        assertTrue(
            "\"Reasignar a\"" in detailSheet,
            "🔴 Falta el título del picker \"Reasignar a\".",
        )
    }

    @Test
    fun `ViewModel expone assignActivity y reassignActivity`() {
        assertTrue(
            "fun assignActivity(" in viewModel,
            "🔴 El ViewModel no expone `assignActivity`.",
        )
        assertTrue(
            "fun reassignActivity(" in viewModel,
            "🔴 El ViewModel no expone `reassignActivity`.",
        )
    }

    @Test
    fun `la pantalla cablea assignActivity y reassignActivity`() {
        val screen = stripComments(source("ui/ActivitiesScreen.kt"))

        assertTrue(
            "onAssign = viewModel::assignActivity" in screen,
            "🔴 La pantalla no cablea `assignActivity` a la hoja de detalle.",
        )
        assertTrue(
            "onReassign = viewModel::reassignActivity" in screen,
            "🔴 La pantalla no cablea `reassignActivity` a la hoja de detalle.",
        )
    }
}
