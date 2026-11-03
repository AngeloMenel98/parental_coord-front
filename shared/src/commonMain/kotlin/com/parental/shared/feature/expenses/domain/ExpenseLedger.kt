package com.parental.shared.feature.expenses.domain

import kotlinx.datetime.LocalDate

/**
 * La parte que le toca a quien pide el gasto (S3).
 *
 * El contrato guarda el porcentaje del solicitante (`splitRatioPayer`) y el servidor deriva
 * la contraparte, así que acá sólo se aplica ese porcentaje: no hay una segunda regla de
 * reparto en el cliente. Un ratio fuera de 0..100 se recorta — el backend no lo manda así,
 * pero una fila con "tu parte" mayor que el total sería peor que un recorte silencioso.
 */
internal fun payerShare(amount: Double, splitRatioPayer: Int): Double {
    if (!amount.isFinite()) return 0.0
    val ratio = splitRatioPayer.coerceIn(0, 100)
    return amount * ratio / 100.0
}

/** El día y el mes abreviado de un asiento del libro (S3). */
internal data class LedgerDate(
    val day: String,
    /** Tres letras en minúscula: `ene`, `feb`, … `dic`. */
    val month: String,
    /** `false` cuando la fecha no se pudo leer y se muestra el texto crudo. */
    val parsed: Boolean,
)

private val MONTH_ABBREVIATIONS = listOf(
    "ene",
    "feb",
    "mar",
    "abr",
    "may",
    "jun",
    "jul",
    "ago",
    "sep",
    "oct",
    "nov",
    "dic",
)

/**
 * `"2026-11-03"` → día `"3"` y mes `"nov"`.
 *
 * Se acepta también un ISO con hora (`2026-11-03T00:00:00Z`), que es lo que puede devolver el
 * backend en un campo de fecha: se toma la parte de fecha. Si no se puede leer, el texto
 * original se muestra tal cual en [LedgerDate.day] y [LedgerDate.parsed] queda en `false` —
 * nunca una fecha inventada.
 */
internal fun ledgerDate(raw: String): LedgerDate {
    val dateOnly = raw.trim().take(10)
    val parsed = runCatching { LocalDate.parse(dateOnly) }.getOrNull()
        ?: return LedgerDate(day = raw.trim(), month = "", parsed = false)
    return LedgerDate(
        day = parsed.dayOfMonth.toString(),
        month = MONTH_ABBREVIATIONS[parsed.monthNumber - 1],
        parsed = true,
    )
}
