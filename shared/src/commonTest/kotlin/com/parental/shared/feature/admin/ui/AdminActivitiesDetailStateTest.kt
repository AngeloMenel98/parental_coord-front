package com.parental.shared.feature.admin.ui

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * S2/S3 (odd/tasks/admin-report-ui.md) — el detalle de actividades tiene que distinguir
 * cuatro estados reales: cargando, carga fallida (con reintento), período sin actividades,
 * y contenido. Antes mostraba el mismo texto de "no disponible" tanto para un fetch que
 * falló como para un período vacío, y sin ninguna forma de reintentar.
 */
class AdminActivitiesDetailStateTest {

    @Test
    fun loading_shows_loading() {
        assertEquals(
            ActivitiesDetailState.LOADING,
            activitiesDetailState(isLoading = true, hasLoadError = false, hasActivities = false),
        )
    }

    @Test
    fun fetch_error_shows_load_error_even_if_report_is_loaded() {
        // El reporte del período está cargado; lo que falló es la lista de actividades.
        assertEquals(
            ActivitiesDetailState.LOAD_ERROR,
            activitiesDetailState(isLoading = false, hasLoadError = true, hasActivities = false),
        )
    }

    @Test
    fun error_wins_over_empty_but_not_over_loading() {
        assertEquals(
            ActivitiesDetailState.LOADING,
            activitiesDetailState(isLoading = true, hasLoadError = true, hasActivities = false),
        )
    }

    @Test
    fun successful_load_with_empty_list_is_empty_period_not_unavailable() {
        assertEquals(
            ActivitiesDetailState.EMPTY,
            activitiesDetailState(isLoading = false, hasLoadError = false, hasActivities = false),
        )
    }

    @Test
    fun activities_present_is_content() {
        assertEquals(
            ActivitiesDetailState.CONTENT,
            activitiesDetailState(isLoading = false, hasLoadError = false, hasActivities = true),
        )
        // Con contenido, un error viejo no tapa las actividades (una carga posterior exitosa lo limpia).
        assertEquals(
            ActivitiesDetailState.CONTENT,
            activitiesDetailState(isLoading = false, hasLoadError = true, hasActivities = true),
        )
    }
}
