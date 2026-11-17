package com.parental.shared.feature.home.domain

import com.parental.shared.core.ui.theme.StatusTokens

/**
 * El anillo de cumplimiento del Home (S4): 12 segmentos, y el color del anillo va de
 * rojo a verde según el avance del mes.
 *
 * ⚠ Las CUATRO bandas no son un degradé continuo: son el escalón más cercano del
 * alfabeto de status (`Overdue` → `InProgress` → `Verify` → `Done`). Un degradé
 * necesitaría colores que no son tokens, y este repo tiene un solo dueño de literales
 * hex (`Color.kt`). Con bandas, cada color del anillo es un token que ya significa algo
 * en el resto de la app.
 */
internal const val COMPLIANCE_RING_SEGMENTS = 12

/** Corte de la banda roja: por debajo de esto el mes está recién arrancado. */
internal const val COMPLIANCE_BAND_RED_MAX = 25.0

/** Corte de la banda naranja. */
internal const val COMPLIANCE_BAND_ORANGE_MAX = 50.0

/** Corte de la banda ámbar: desde acá (y hasta 100) el mes va en verde. */
internal const val COMPLIANCE_BAND_AMBER_MAX = 75.0

/**
 * La banda de status que le corresponde a [percentage].
 *
 * `NaN`/`Infinity` → `Overdue`: el anillo no puede dibujar un avance que no existe y
 * el rojo es la lectura conservadora ("no hay progreso demostrable"). El porcentaje
 * fuera de rango sólo cambia de banda por comparación, no se recorta acá: recortar es
 * trabajo de quien dibuja los segmentos.
 */
internal fun complianceBand(percentage: Double): StatusTokens.Status = when {
    !percentage.isFinite() -> StatusTokens.Status.Overdue
    percentage < COMPLIANCE_BAND_RED_MAX -> StatusTokens.Status.Overdue
    percentage < COMPLIANCE_BAND_ORANGE_MAX -> StatusTokens.Status.InProgress
    percentage < COMPLIANCE_BAND_AMBER_MAX -> StatusTokens.Status.Verify
    else -> StatusTokens.Status.Done
}

/**
 * Cuántos de los [COMPLIANCE_RING_SEGMENTS] segmentos quedan encendidos para
 * [percentage] (0..12), con el redondeo al segmento más cercano.
 */
internal fun complianceSegmentsFilled(percentage: Double): Int {
    val safe = percentage.takeIf { it.isFinite() }?.coerceIn(0.0, 100.0) ?: 0.0
    return ((safe / 100.0) * COMPLIANCE_RING_SEGMENTS).toInt().coerceIn(0, COMPLIANCE_RING_SEGMENTS)
}
