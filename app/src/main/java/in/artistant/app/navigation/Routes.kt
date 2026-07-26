package `in`.artistant.app.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe Navigation-Compose routes (the two iOS `Route` enums' analogue).
 * M0 ships the top-level graph markers + stubbed per-role routes; screen args
 * fill in as those screens land (M1+).
 */

// Top-level graph destinations gated by ArtistantRoot.
@Serializable data object SignupGraph
@Serializable data object ClientGraph
@Serializable data object ArtistGraph

/** Client-side pushed routes. */
sealed interface ClientRoute {
    @Serializable data class ArtistProfile(val artistId: String) : ClientRoute
    @Serializable data class Chat(val threadId: String) : ClientRoute
    @Serializable data object Search : ClientRoute
    /** M3 — booking funnel (request → accept). */
    @Serializable data class BookingCompose(val artistId: String) : ClientRoute
    @Serializable data object Checkout : ClientRoute
    @Serializable data class Confirmed(val bookingId: String) : ClientRoute
    @Serializable data class BookingDetail(val bookingId: String) : ClientRoute
    @Serializable data class RequestQuote(val artistId: String) : ClientRoute
}

/** String routes for the client NavHost (mirrors [ClientRoute] until typed nav lands). */
object ClientNavRoutes {
    const val BOOKING = "booking/{artistId}"
    const val CHECKOUT = "checkout"
    const val CONFIRMED = "confirmed/{bookingId}"
    const val BOOKING_DETAIL = "booking_detail/{bookingId}"
    const val REQUEST_QUOTE = "request_quote/{artistId}"

    fun bookingCompose(artistId: String) = "booking/$artistId"
    fun confirmed(bookingId: String) = "confirmed/$bookingId"
    fun bookingDetail(bookingId: String) = "booking_detail/$bookingId"
    fun requestQuote(artistId: String) = "request_quote/$artistId"
}

/** Artist-side pushed routes. */
sealed interface ArtistRoute {
    @Serializable data class GigRequest(val id: String) : ArtistRoute
    @Serializable data object ScoreExplainer : ArtistRoute
}
