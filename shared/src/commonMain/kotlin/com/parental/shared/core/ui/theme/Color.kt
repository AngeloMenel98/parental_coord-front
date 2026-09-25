package com.parental.shared.core.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * Único propietario de literales hex del proyecto (design ui-theme-foundation §6/§7).
 * Nada fuera de este archivo debe definir Color(0x…) — los composables consumen
 * MaterialTheme tokens o los objetos CategoryTokens/StatusTokens.
 *
 * Regla de dos namespaces (proposal): Category y Status NO comparten hex.
 */

// ── Esquemas de marca (design §6 — pares verificados WCAG AA) ─────────────

/** Esquema claro de marca (M3, semilla teal #00696D). Sin roles surfaceContainer* (ADR-1). */
val LightColorScheme = androidx.compose.material3.lightColorScheme(
    primary = Color(0xFF00696D),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF9CF1F6),
    onPrimaryContainer = Color(0xFF002022),
    secondary = Color(0xFF4A6365),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8EA),
    onSecondaryContainer = Color(0xFF051F21),
    tertiary = Color(0xFF00697A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC2E9FF),
    onTertiaryContainer = Color(0xFF001F29),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFAFDFC),
    onBackground = Color(0xFF191C1D),
    surface = Color(0xFFFAFDFC),
    onSurface = Color(0xFF191C1D),
    surfaceVariant = Color(0xFFDAE4E5),
    onSurfaceVariant = Color(0xFF3F4849),
    outline = Color(0xFF6F797A),
    outlineVariant = Color(0xFFBEC8C9),
    scrim = Color(0xFF000000),
    surfaceTint = Color(0xFF00696D),
    inverseSurface = Color(0xFF2D3132),
    inverseOnSurface = Color(0xFFEFF1F1),
    inversePrimary = Color(0xFF80D4D9),
)

/** Esquema oscuro de marca (design §6). */
val DarkColorScheme = androidx.compose.material3.darkColorScheme(
    primary = Color(0xFF80D4D9),
    onPrimary = Color(0xFF00373A),
    primaryContainer = Color(0xFF004F53),
    onPrimaryContainer = Color(0xFF9CF1F6),
    secondary = Color(0xFFB0CCCE),
    onSecondary = Color(0xFF1B3436),
    secondaryContainer = Color(0xFF334D4F),
    onSecondaryContainer = Color(0xFFCCE8EA),
    tertiary = Color(0xFF84D2E6),
    onTertiary = Color(0xFF00363D),
    tertiaryContainer = Color(0xFF004F5D),
    onTertiaryContainer = Color(0xFFB8EBFF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF101415),
    onBackground = Color(0xFFE0E3E3),
    surface = Color(0xFF101415),
    onSurface = Color(0xFFE0E3E3),
    surfaceVariant = Color(0xFF3F4849),
    onSurfaceVariant = Color(0xFFBEC8C9),
    outline = Color(0xFF899294),
    outlineVariant = Color(0xFF404849),
    scrim = Color(0xFF000000),
    surfaceTint = Color(0xFF80D4D9),
    inverseSurface = Color(0xFFE0E3E3),
    inverseOnSurface = Color(0xFF2D3132),
    inversePrimary = Color(0xFF00696D),
)

// ── Tokens de tags (design §7 — tríadas LOCKED, AA verificadas) ───────────

/**
 * Tríada de color de un tag/chip: [dot] es el ancla de identidad (migración §9
 * lee `.dot`), [container]/[content] son el par texto-de-fondo AA-ready para la
 * fase de re-render de chips (fuera de alcance en Phase 0).
 */
data class TagTriple(
    val dot: Color,
    val container: Color,
    val content: Color,
)

// ── Categorías (6) — namespaces disjuntos de Status (design ADR-5) ────────

/** Tokens de categoría: color re-sueltos por nombre canónico ES, modo-aware. */
object CategoryTokens {

    /** Entradas canónicas (6) — el orden coincide con el mapa de visual. */
    enum class Category(val esName: String) {
        Salud("Salud"),
        Educacion("Educación"),
        Familiar("Familiar"),
        Social("Social"),
        Recreacion("Recreación"),
        Otros("Otros"),
    }

    /** Tríadas LIGHT por categoría (design §7). */
    val Light: Map<Category, TagTriple> = mapOf(
        Category.Salud to TagTriple(Color(0xFFD81B60), Color(0xFFFCE4EC), Color(0xFFAD1457)),
        Category.Educacion to TagTriple(Color(0xFF3F51B5), Color(0xFFE8EAF6), Color(0xFF303F9F)),
        Category.Familiar to TagTriple(Color(0xFF8E24AA), Color(0xFFF3E5F5), Color(0xFF6A1B9A)),
        Category.Social to TagTriple(Color(0xFF00897B), Color(0xFFE0F2F1), Color(0xFF00695C)),
        Category.Recreacion to TagTriple(Color(0xFFF4511E), Color(0xFFFBE9E7), Color(0xFFB42C0E)),
        Category.Otros to TagTriple(Color(0xFF8D6E63), Color(0xFFEFEBE9), Color(0xFF5D4037)),
    )

    /** Tríadas DARK por categoría (design §7). */
    val Dark: Map<Category, TagTriple> = mapOf(
        Category.Salud to TagTriple(Color(0xFFD81B60), Color(0xFF880E4F), Color(0xFFF8BBD0)),
        Category.Educacion to TagTriple(Color(0xFF5C6BC0), Color(0xFF1A237E), Color(0xFFC5CAE9)),
        Category.Familiar to TagTriple(Color(0xFFAB47BC), Color(0xFF4A148C), Color(0xFFE1BEE7)),
        Category.Social to TagTriple(Color(0xFF00897B), Color(0xFF004D40), Color(0xFFB2DFDB)),
        Category.Recreacion to TagTriple(Color(0xFFF4511E), Color(0xFF8F2600), Color(0xFFFFCCBC)),
        Category.Otros to TagTriple(Color(0xFF8D6E63), Color(0xFF3E2723), Color(0xFFD7CCC8)),
    )

    /**
     * Resuelve el nombre (canónico ES o normalizado sin acentos, ej. "EDUCACION")
     * a su entrada canónica. Devuelve null si no es una categoría conocida.
     */
    fun resolve(name: String): Category? =
        Category.entries.firstOrNull { normalize(it.esName) == normalize(name) }

    /**
     * Tríada base LIGHT por nombre conocido (contrato design §5). Para resolución
     * modo-aware usa [resolve] + [triple].
     */
    fun byName(name: String): TagTriple? = resolve(name)?.let { Light.getValue(it) }

    /** Tríada de [category] según [darkTheme] (ADR-7: los componentes resuelven modo). */
    fun triple(category: Category, darkTheme: Boolean): TagTriple =
        (if (darkTheme) Dark else Light).getValue(category)

    private fun normalize(value: String): String =
        value.uppercase()
            .replace("Á", "A")
            .replace("É", "E")
            .replace("Í", "I")
            .replace("Ó", "O")
            .replace("Ú", "U")
            .replace("Ü", "U")
}

// ── Status (11) — alfabeto semántico FIXED (design ADR-4/§9) ───────────────

/** Tokens de status: actividad (6) + ok/ko (bond) + gasto (3). Modo-aware. */
object StatusTokens {

    /** Entradas canónicas (11). */
    enum class Status {
        Created, Assigned, InProgress, Verify, Done, Overdue,
        Ok, Ko, Paid, Rejected, Dispute,
    }

    /**
     * 17 claves en MAYÚSCULAS (strings del backend/UI) → entrada canónica.
     * DESIGN §9: CANCELLED→Created (normalización gris), PENDING_VERIFICATION→Verify,
     * PENDIENTE/PENDING→InProgress, COMPLETED/APROBADO→Done, ACTIVE→Ok, INACTIVE→Ko.
     */
    val aliasMap: Map<String, Status> = mapOf(
        "CREATED" to Status.Created,
        "ASSIGNED" to Status.Assigned,
        "IN_PROGRESS" to Status.InProgress,
        "VERIFY" to Status.Verify,
        "DONE" to Status.Done,
        "PENDING_VERIFICATION" to Status.Verify,
        "COMPLETED" to Status.Done,
        "OVERDUE" to Status.Overdue,
        "CANCELLED" to Status.Created,
        "PENDIENTE" to Status.InProgress,
        "APROBADO" to Status.Done,
        "RECHAZADO" to Status.Rejected,
        "PAGADO" to Status.Paid,
        "DISPUTA" to Status.Dispute,
        "ACTIVE" to Status.Ok,
        "INACTIVE" to Status.Ko,
        "PENDING" to Status.InProgress,
    )

    /** Tríadas LIGHT por status (design §7). */
    val Light: Map<Status, TagTriple> = mapOf(
        Status.Created to TagTriple(Color(0xFF757575), Color(0xFFFAFAFA), Color(0xFF616161)),
        Status.Assigned to TagTriple(Color(0xFF1E88E5), Color(0xFFE3F2FD), Color(0xFF1565C0)),
        Status.InProgress to TagTriple(Color(0xFFE65100), Color(0xFFFFF3E0), Color(0xFFBF360C)),
        Status.Verify to TagTriple(Color(0xFFB26A00), Color(0xFFFFF8E1), Color(0xFF9E5F00)),
        Status.Done to TagTriple(Color(0xFF43A047), Color(0xFFE8F5E9), Color(0xFF2E7D32)),
        Status.Overdue to TagTriple(Color(0xFFE53935), Color(0xFFFFEBEE), Color(0xFFC62828)),
        Status.Ok to TagTriple(Color(0xFF43A047), Color(0xFFE8F5E9), Color(0xFF2E7D32)),
        Status.Ko to TagTriple(Color(0xFFF44336), Color(0xFFFFEBEE), Color(0xFFC62828)),
        Status.Paid to TagTriple(Color(0xFF1E88E5), Color(0xFFE3F2FD), Color(0xFF1565C0)),
        Status.Rejected to TagTriple(Color(0xFFE53935), Color(0xFFFFEBEE), Color(0xFFC62828)),
        Status.Dispute to TagTriple(Color(0xFF8E24AA), Color(0xFFF3E5F5), Color(0xFF6A1B9A)),
    )

    /** Tríadas DARK por status (design §7). */
    val Dark: Map<Status, TagTriple> = mapOf(
        Status.Created to TagTriple(Color(0xFF9E9E9E), Color(0xFF3A3A3A), Color(0xFFE0E0E0)),
        Status.Assigned to TagTriple(Color(0xFF1E88E5), Color(0xFF0D47A1), Color(0xFFBBDEFB)),
        Status.InProgress to TagTriple(Color(0xFFFB8C00), Color(0xFFBF360C), Color(0xFFFFE3B8)),
        Status.Verify to TagTriple(Color(0xFFFFB300), Color(0xFF4A3300), Color(0xFFFFECB3)),
        Status.Done to TagTriple(Color(0xFF43A047), Color(0xFF1B5E20), Color(0xFFC8E6C9)),
        Status.Overdue to TagTriple(Color(0xFFE53935), Color(0xFF7F1D1D), Color(0xFFFFCDD2)),
        Status.Ok to TagTriple(Color(0xFF4CAF50), Color(0xFF1B5E20), Color(0xFFC8E6C9)),
        Status.Ko to TagTriple(Color(0xFFF44336), Color(0xFF7F1D1D), Color(0xFFFFCDD2)),
        Status.Paid to TagTriple(Color(0xFF1E88E5), Color(0xFF0D47A1), Color(0xFFBBDEFB)),
        Status.Rejected to TagTriple(Color(0xFFE53935), Color(0xFF7F1D1D), Color(0xFFFFCDD2)),
        Status.Dispute to TagTriple(Color(0xFFAB47BC), Color(0xFF4A148C), Color(0xFFE1BEE7)),
    )

    /** Tríada LIGHT de Ok (chip de bond activo) — anclas de fase chip. */
    val Ok: TagTriple get() = Light.getValue(Status.Ok)

    /** Tríada LIGHT de Ko (chip de bond inactivo) — anclas de fase chip. */
    val Ko: TagTriple get() = Light.getValue(Status.Ko)

    /** Tríada de [status] según [darkTheme] (ADR-7: los componentes resuelven modo). */
    fun triple(status: Status, darkTheme: Boolean): TagTriple =
        (if (darkTheme) Dark else Light).getValue(status)
}

// ── Parser de hex del servidor (ADR-6: cat.color ganador; NUNCA literal) ──

/**
 * Convierte un hex "#RRGGBB" del servidor a [Color] (ARGB opaco) o null si no
 * es válido. Es el único lugar de parseo runtime — no es un literal de código,
 * por lo que queda exento del grep-audit (design §10).
 */
fun parseHexColor(hex: String): Color? =
    hex.removePrefix("#")
        .takeIf { it.length == 6 }
        ?.let { runCatching { Color(("FF$it").toLong(16)) }.getOrNull() }