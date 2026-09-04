package `in`.artistant.app.navigation

/**
 * The route literals both NavHosts navigate by (the two iOS `Route` enums' analogue).
 *
 * Strings rather than Navigation-Compose's type-safe destinations. This file used to
 * carry a second, `@Serializable` copy of the same table — graph markers plus a
 * `ClientRoute`/`ArtistRoute` sealed interface per role — written in M0 against a typed-nav
 * migration that never happened. Nothing referenced any of it through M0–M7 while both
 * NavHosts routed through the constants below, so all it did was drift: `ClientRoute.Search`
 * existed while the string layer never had a SEARCH constant at all. Deleted. If typed nav
 * lands it lands as one migration of the live table, not as scaffolding kept warm beside it.
 */

/** String routes for the client NavHost. */
object ClientNavRoutes {
    const val CHAT = "chat/{threadId}"
    const val BOOKING = "booking/{artistId}"
    const val CHECKOUT = "checkout"
    const val CONFIRMED = "confirmed/{bookingId}"
    const val BOOKING_DETAIL = "booking_detail/{bookingId}"
    const val REQUEST_QUOTE = "request_quote/{artistId}"
    const val ARTIST_LIST = "artist_list/{kind}"

    /**
     * Where an accepted in-thread quote lands — design 94, "Match confirmed".
     *
     * The constant lives here and the CHAT navigates to it, but the destination
     * itself belongs to the booking section: one screen owns what a confirmed
     * match looks like, and the thread's job ends at handing over the id.
     */
    const val MATCH_CONFIRMED = "match_confirmed/{bookingId}"

    /** The archive (design 60 / 111) — reachable from the inbox header. */
    const val ARCHIVED = "archived"

    /** Trust & safety (design 131) — from account settings and from the archive. */
    const val SAFETY_CENTRE = "safety_centre"

    /** The scripted support assistant (design 34) — the inbox's permanent row. */
    const val SUPPORT = "support"

    fun bookingCompose(artistId: String) = "booking/$artistId"
    fun chat(threadId: String) = "chat/$threadId"
    fun confirmed(bookingId: String) = "confirmed/$bookingId"
    fun bookingDetail(bookingId: String) = "booking_detail/$bookingId"
    fun requestQuote(artistId: String) = "request_quote/$artistId"
    fun artistList(kind: String) = "artist_list/$kind"
    fun matchConfirmed(bookingId: String) = "match_confirmed/$bookingId"
    const val PAYWALL = "paywall"

    /**
     * Account settings → blocked accounts. Same literal on both roles because it
     * is the same screen in two graphs — blocking is not role-specific, and a
     * client who blocks an artist and an artist who blocks a client need the
     * identical way back.
     */
    const val BLOCKED_ACCOUNTS = "blocked_accounts"
}

/** String routes for the artist NavHost. */
object ArtistNavRoutes {
    const val BOOKING_DETAIL = "booking_detail/{bookingId}"
    const val GIG_REQUEST = "gig_request/{requestId}"
    const val CHAT = "chat/{threadId}"

    fun bookingDetail(bookingId: String) = "booking_detail/$bookingId"
    fun gigRequest(requestId: String) = "gig_request/$requestId"
    fun chat(threadId: String) = "chat/$threadId"
    fun matchConfirmed(bookingId: String) = "match_confirmed/$bookingId"
    const val PROFILE = "profile"
    const val PAYWALL = "paywall"
    const val WIZARD = "wizard"
    const val MANAGE_AVAILABILITY = "manage_availability"
    const val SCORE_EXPLAINER = "score_explainer"

    /** See [ClientNavRoutes.BLOCKED_ACCOUNTS] — same screen, artist graph. */
    const val BLOCKED_ACCOUNTS = "blocked_accounts"

    /** See [ClientNavRoutes.MATCH_CONFIRMED] — same destination, artist graph. */
    const val MATCH_CONFIRMED = "match_confirmed/{bookingId}"

    /** See [ClientNavRoutes.ARCHIVED]. */
    const val ARCHIVED = "archived"

    /** See [ClientNavRoutes.SAFETY_CENTRE]. */
    const val SAFETY_CENTRE = "safety_centre"

    /** See [ClientNavRoutes.SUPPORT]. */
    const val SUPPORT = "support"
}
