package `in`.artistant.app.common.util

/**
 * ₹1,00,000 — full Indian lakh/crore grouping (last group of 3 digits, then
 * groups of 2). Hand-rolled rather than via `NumberFormat(en_IN)` because some
 * JDK ICU versions omit the lakh separator (emitting ₹100,000), which is the
 * wrong, non-iOS grouping. The rule is simple and fixed, so we own it.
 */
fun formatInr(amount: Int): String = "₹${groupIndian(amount)}"

private fun groupIndian(amount: Int): String {
    val neg = amount < 0
    val digits = kotlin.math.abs(amount).toString()
    if (digits.length <= 3) return (if (neg) "-" else "") + digits
    val last3 = digits.takeLast(3)
    val rest = digits.dropLast(3)
    // Chunk the remaining digits into groups of 2, from the right.
    val head = rest.reversed().chunked(2).joinToString(",") { it.reversed() }.reversed()
    return (if (neg) "-" else "") + "$head,$last3"
}

/**
 * Compact form: ₹1.5L / ₹12K / ₹500. Lakh at ≥1,00,000, thousand at ≥1,000.
 * Drops a trailing ".0" so ₹2L reads clean, not ₹2.0L.
 */
fun formatInrShort(amount: Int): String = when {
    amount >= 100_000 -> "₹${trimDot(amount / 100_000.0)}L"
    amount >= 1_000 -> "₹${trimDot(amount / 1_000.0)}K"
    else -> "₹$amount"
}

private fun trimDot(v: Double): String {
    val oneDp = (Math.round(v * 10) / 10.0)
    return if (oneDp % 1.0 == 0.0) oneDp.toInt().toString() else oneDp.toString()
}
