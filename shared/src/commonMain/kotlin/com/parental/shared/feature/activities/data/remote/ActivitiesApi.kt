package com.parental.shared.feature.activities.data.remote

import com.parental.shared.core.error.ApiResult
import com.parental.shared.core.network.safeApiCall
import com.parental.shared.feature.activities.domain.model.Actividad
import com.parental.shared.feature.activities.domain.model.ActividadDetalle
import com.parental.shared.feature.activities.domain.model.ConfirmAssignmentResponse
import io.ktor.client.*
import io.ktor.client.request.*

class ActivitiesApi(
    private val client: HttpClient,
) {
    /**
     * GET /activities/bond/:bondId — lista de actividades del vínculo activo,
     * ordenadas scheduledStart → deadline → createdAt (nulls last).
     */
    suspend fun listActividades(bondId: String): ApiResult<List<Actividad>> = safeApiCall {
        client.get("activities/bond/$bondId")
    }

    /**
     * GET /activities/:id — detalle completo de una actividad.
     */
    suspend fun getActividad(id: String): ApiResult<ActividadDetalle> = safeApiCall {
        client.get("activities/$id")
    }

    /**
     * POST /activities/:id/confirm — confirma la asignación (flag-only,
     * idempotente). Sin cuerpo. Errores: mensaje del backend (message ?? error)
     * cuando responde JSON; si no, status.description.
     */
    suspend fun confirmAssignment(id: String): ApiResult<ConfirmAssignmentResponse> = safeApiCall {
        client.post("activities/$id/confirm")
    }
}
