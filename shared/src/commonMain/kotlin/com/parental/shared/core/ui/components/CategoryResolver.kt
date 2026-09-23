package com.parental.shared.core.ui.components

import com.parental.shared.core.network.CategoryDto

/**
 * Info de categoría resuelta para la UI: nombre + color hex + emoji.
 */
data class CategoryInfo(
    val name: String,
    val colorHex: String?,
    val emoji: String?,
)

/**
 * Resuelve categoryId (UUID) → CategoryInfo.
 * Construye el mapa uuid → info combinando las categorías fetcheadas de
 * GET /categories (público) con preferencias hardcodeadas de color/emoji
 * keyed por NOMBRE (los UUIDs no se conocen en build-time). Cualquier uuid
 * fuera del mapa → null → el caller renderiza chip neutral.
 */
object CategoryResolver {
    private val namePrefs: Map<String, CategoryInfo> = mapOf(
        "Salud" to CategoryInfo("Salud", "#E53935", "\u2764\uFE0F"),
        "Educación" to CategoryInfo("Educación", "#1E88E5", "\uD83D\uDCDA"),
        "Familiar" to CategoryInfo("Familiar", "#8E24AA", "\uD83D\uDC6A"),
        "Social" to CategoryInfo("Social", "#43A047", "\uD83E\uDD1D"),
        "Recreación" to CategoryInfo("Recreación", "#FB8C00", "\uD83C\uDF89"),
        "Otros" to CategoryInfo("Otros", "#757575", "\u2699\uFE0F"),
    )

    /**
     * Construye el mapa uuid → CategoryInfo. El color fetcheado (si existe)
     * gana sobre la preferencia; el emoji siempre viene de la preferencia por
     * nombre (la API solo expone nombre de icono, no emoji).
     */
    fun build(categories: List<CategoryDto>): Map<String, CategoryInfo> =
        categories.associate { cat ->
            val pref = namePrefs[cat.name]
            cat.id to CategoryInfo(
                name = cat.name,
                colorHex = cat.color ?: pref?.colorHex,
                emoji = pref?.emoji,
            )
        }
}