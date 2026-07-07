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
    // M3 booking funnel. Chat (M4) + ScoreExplainer stay stubs.
    @Serializable data class Booking(val artistId: String) : ClientRoute
    @Serializable data class RequestQuote(val artistId: String) : ClientRoute
    // The draft lives in BookingStore, so Checkout takes no args; Confirmed +
    // BookingDetail carry the persisted booking id.
    @Serializable data object Checkout : ClientRoute
    @Serializable data class Confirmed(val bookingId: String) : ClientRoute
    @Serializable data class BookingDetail(val bookingId: String) : ClientRoute
    @Serializable data object ScoreExplainer : ClientRoute
}

/** Artist-side pushed routes. */
sealed interface ArtistRoute {
    @Serializable data class GigRequest(val id: String) : ArtistRoute
    @Serializable data object ScoreExplainer : ArtistRoute
}
