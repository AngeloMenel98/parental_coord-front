package com.parental.shared.core.util

/**
 * Formatea un importe en pesos argentinos (`es-AR`).
 *
 * El backend modela el dinero como `amount: number` con escala 2 (`numeric(12,2)`),
 * por lo que esta función recibe la cantidad ya en unidades decimales — `$632,80`
 * se representa como `632.8`. No hay consumidor todavía: las pantallas de Gastos
 * (T29/T33) la usarán; vive aquí como utilidad compartida del módulo.
 *
 * Regla explícita, sin dependencias de formato:
 * - símbolo `$` al inicio;
 * - signo negativo **antes** del símbolo cuando el monto es menor a cero;
 * - separador de miles: `.` (punto);
 * - separador decimal: `,` (coma);
 * - siempre dos decimales, redondeo por exceso a partir de 5 en el tercer decimal.
 *
 * ⚠ EL REDONDEO ES SOBRE EL NÚMERO ESCRITO, NO SOBRE EL DOUBLE: `632.805` se
 *   redondea a `632,81` (lo que el usuario escribió), no a `632,80` (lo que el
 *   `Double` representa, que en binario es 632.804999…). Un importe de dinero con
 *   tres decimales es un caso límite, pero la regla queda escrita y fijada en los
 *   tests, que es lo que la hace defendible.
 */
internal fun formatMoney(amount: Double): String {
    require(amount.isFinite()) { "El importe debe ser finito: $amount" }
    require(kotlin.math.abs(amount) <= MAX_AMOUNT) { "El importe es absurdo: $amount" }

    val negative = amount < 0
    val plain = plainDecimalString(kotlin.math.abs(amount))
    val dot = plain.indexOf('.')
    val whole = if (dot == -1) plain else plain.substring(0, dot)
    val frac = if (dot == -1) "" else plain.substring(dot + 1)

    val scale = frac.length
    val digits = whole + frac

    val totalCents = when {
        scale == 0 -> digits.toLong() * 100L
        scale <= 2 -> digits.toLong() * pow10(2 - scale)
        else -> {
            val drop = scale - 2
            val kept = digits.dropLast(drop)
            val base = kept.toLong()
            val firstDropped = digits.takeLast(drop).first()
            if (firstDropped >= '5') base + 1 else base
        }
    }

    val intPart = totalCents / 100L
    val decPart = (totalCents % 100L).toString().padStart(2, '0')
    val grouped = intPart.toString()
        .reversed()
        .chunked(3)
        .joinToString(".")
        .reversed()

    return "${if (negative) "-" else ""}$$grouped,$decPart"
}

/**
 * El `toString()` de un `Double` salta a notación científica desde `1E7` — y el
 * backend admite `numeric(12,2)`, o sea hasta `9.999.999.999,99`. Sin expandir el
 * exponente, un importe grande (o muy pequeño) no se parsearía y el formateo
 * reventaría con un `NumberFormatException`.
 *
 * `1.0E7` → `"10000000"` · `1.23E-5` → `"0.0000123"`.
 */
private fun plainDecimalString(value: Double): String {
    val raw = value.toString()
    val exponentAt = raw.indexOfFirst { it == 'E' || it == 'e' }
    if (exponentAt < 0) return raw

    val mantissa = raw.substring(0, exponentAt)
    val exponent = raw.substring(exponentAt + 1).toInt()
    val digits = mantissa.replace(".", "")
    val fractionDigits = mantissa.length - mantissa.indexOf('.') - 1

    // El valor es `digits` con `fractionDigits` decimales, multiplicado por 10^exponent.
    val pointShift = exponent - fractionDigits
    if (pointShift >= 0) return digits + "0".repeat(pointShift)

    val padded = "0".repeat(-pointShift) + digits
    val integerDigits = padded.length + pointShift
    return if (integerDigits <= 0) {
        "0." + padded
    } else {
        padded.substring(0, integerDigits) + "." + padded.substring(integerDigits)
    }
}

private fun pow10(exp: Int): Long {
    var result = 1L
    repeat(exp) { result *= 10L }
    return result
}

/**
 * Tope defensivo: `numeric(12,2)` no pasa de `9.999.999.999,99`. Este límite
 * sólo lo cruza un error de programación, y un `require` que lo dice es mejor que
 * un importe silenciosamente raro.
 */
private const val MAX_AMOUNT = 1e12
