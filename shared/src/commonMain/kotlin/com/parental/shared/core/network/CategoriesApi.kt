package com.parental.shared.core.network

import com.parental.shared.core.error.ApiResult
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable

@Serializable
data class CategoryDto(
    val id: String,
    val name: String,
    val color: String? = null,
    val icon: String? = null,
)

class CategoriesApi(
    private val client: HttpClient,
    private val baseUrl: String,
) {
    /**
     * GET /categories — público (sin auth). Lista de categorías del sistema,
     * usada para resolver categoryId → (nombre, color, icono).
     */
    suspend fun getCategories(): ApiResult<List<CategoryDto>> {
        return try {
            val response = client.get("$baseUrl/categories")
            if (response.status.isSuccess()) {
                ApiResult.Success(response.body())
            } else {
                ApiResult.Error(response.status.value, response.status.description)
            }
        } catch (e: Exception) {
            ApiResult.Error(0, "GET $baseUrl/categories failed: ${e.message}")
        }
    }
}