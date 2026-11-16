package com.parental.shared.feature.thirdparty.data.remote

import com.parental.shared.core.error.ApiResult
import com.parental.shared.feature.thirdparty.domain.model.ThirdPartyGrant
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

/**
 * El único endpoint de lectura del tercero (`odd/tasks/terciarios-temporales.md`, T2/T7).
 *
 * Es `open` con métodos `open`, igual que el resto de las APIs del proyecto, para que los
 * tests puedan sustituirlo sin red ni servidor.
 */
open class ThirdPartiesApi(
    private val client: HttpClient,
    private val baseUrl: String,
) {
    /**
     * GET /third-parties/me — el grant propio: quién lo autorizó, la relación y la
     * vigencia. **Sin `bondId` en la ruta**: el backend resuelve el vínculo del token y
     * un terciario sólo puede tener un grant vivo por vínculo (índice único parcial de
     * T1). Pedirlo por ruta abriría la puerta a consultar el grant de otro.
     *
     * Un error NO es fatal para la pantalla: la tarjeta de permiso desaparece y la lista
     * de tareas se sigue pintando (ver `ThirdPartyViewModel.load`).
     */
    open suspend fun getMyGrant(token: String): ApiResult<ThirdPartyGrant> {
        return try {
            val response = client.get("$baseUrl/third-parties/me") {
                header("Authorization", "Bearer $token")
            }
            if (response.status.isSuccess()) {
                ApiResult.Success(response.body())
            } else {
                ApiResult.Error(response.status.value, response.status.description)
            }
        } catch (e: Exception) {
            ApiResult.Error(0, e.message ?: "Network error")
        }
    }
}
