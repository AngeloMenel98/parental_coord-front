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
    // namePrefs conserva solo nombre + emoji; los hex DELETED — el color por
    // nombre vive en CategoryTokens (core/ui/theme/Color.kt) — ADR-6.
    private val namePrefs: Map<String, CategoryInfo> = mapOf(
        "Salud" to CategoryInfo("Salud", null, "\u2764\uFE0F"),
        "Educación" to CategoryInfo("Educación", null, "\uD83D\uDCDA"),
        "Familiar" to CategoryInfo("Familiar", null, "\uD83D\uDC6A"),
        "Social" to CategoryInfo("Social", null, "\uD83E\uDD1D"),
        "Recreación" to CategoryInfo("Recreación", null, "\uD83C\uDF89"),
        "Otros" to CategoryInfo("Otros", null, "\u2699\uFE0F"),
    )

    /**
     * Construye el mapa uuid → CategoryInfo. `colorHex` es SOLO del servidor
     * (cat.color gana, sin cambios de contrato); si no trae color, el badge
     * hace fallback a CategoryTokens por nombre canónico (modo-aware).
     */
    fun build(categories: List<CategoryDto>): Map<String, CategoryInfo> =
        categories.associate { cat ->
            val pref = namePrefs[cat.name]
            cat.id to CategoryInfo(
                name = cat.name,
                colorHex = cat.color,
                emoji = pref?.emoji,
            )
        }
}