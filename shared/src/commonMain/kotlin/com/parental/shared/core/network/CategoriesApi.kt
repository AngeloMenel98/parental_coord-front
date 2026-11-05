package com.parental.shared.core.network

import com.parental.shared.core.error.ApiResult
import io.ktor.client.*
import io.ktor.client.request.*
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
) {
    /**
     * GET /categories — público (sin auth). Lista de categorías del sistema,
     * usada para resolver categoryId → (nombre, color, icono).
     */
    suspend fun getCategories(): ApiResult<List<CategoryDto>> = safeApiCall {
        client.get("categories")
    }
}
