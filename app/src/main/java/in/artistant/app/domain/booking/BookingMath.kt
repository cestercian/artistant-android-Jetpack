package `in`.artistant.app.domain.booking

import kotlin.math.roundToInt

/**
 * Port of iOS `Booking.compute` (Models/Booking.swift). Money math is
 * client-side and India-statutory:
 *   platform = round(fee * 0.05)                 5% platform fee
 *   gst      = round((fee + platform) * 0.18)    18% GST on fee+platform
 *   total    = fee + platform + gst
 * All figures rounded to the nearest rupee (Int). Kept pure + unit-tested
 * because it's a money path.
 */
data class BookingCharges(val platform: Int, val gst: Int, val total: Int)

object BookingMath {
    fun compute(fee: Int): BookingCharges {
        val platform = (fee * 0.05).roundToInt()
        val gst = ((fee + platform) * 0.18).roundToInt()
        return BookingCharges(platform = platform, gst = gst, total = fee + platform + gst)
    }
}
