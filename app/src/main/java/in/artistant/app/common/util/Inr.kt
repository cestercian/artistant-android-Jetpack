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
    // Chunk the remaining digits into groups of 2, from the RIGHT. Reversing the
    // string is what makes chunked() group from the right; the chunks must then
    // be joined AS-IS and the whole result reversed once.
    //
    // The bug this replaced reversed each chunk as well as the joined string, so
    // every group came back swapped: a ₹60,000 tier rendered "₹06,000". It went
    // unnoticed because the original tests only used 5_000 (single-digit head)
    // and 100_000 (head survives by coincidence) — both no-ops under a reversal.
    val head = rest.reversed().chunked(2).joinToString(",").reversed()
    return (if (neg) "-" else "") + "$head,$last3"
}

/**
 * Compact form: ₹1.5L / ₹12K / ₹500. Lakh at ≥1,00,000, thousand at ≥1,000.
 * Drops a trailing ".0" so ₹2L reads clean, not ₹2.0L.
 *
 * The band is picked from the ROUNDED figure, not the raw amount: ₹99,999 rounds to
 * 100 thousands, and "₹100K" *is* one lakh written in the band the next rung up
 * exists to express, so it promotes to "₹1L". Only the rounding promotes — ₹95,000
 * still reads "₹95K" — and the K band still keys off the raw amount so exact rupees
 * below ₹1,000 stay exact ("₹999", not a rounded "₹1K").
 *
 * A negative delegates to [formatInr]. The compact bands exist for prices, which are
 * never negative, and the bare `else` used to emit an ungrouped "₹-150000" where
 * every other path in this file groups.
 */
fun formatInrShort(amount: Int): String {
    if (amount < 0) return formatInr(amount)
    val thousands = round1(amount / 1_000.0)
    return when {
        thousands >= 100.0 -> "₹${trimDot(round1(amount / 100_000.0))}L"
        amount >= 1_000 -> "₹${trimDot(thousands)}K"
        else -> "₹$amount"
    }
}

/** One decimal place, half-up — the precision both compact bands render at. */
private fun round1(v: Double): Double = Math.round(v * 10) / 10.0

/** Drops a trailing ".0" from an ALREADY-rounded value (see [round1]). */
private fun trimDot(v: Double): String =
    if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()
