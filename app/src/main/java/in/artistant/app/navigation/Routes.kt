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

    /** Every review for one artist, with search and lenses (design screen 102). */
    const val ARTIST_REVIEWS = "artist_reviews/{artistId}"

    /**
     * The client-facing audit of one artist's Bookability Score (design 16).
     *
     * Its own destination rather than a second tab on the profile: it is a page
     * a client can be linked to, sit on, and read — and the sheet that opens
     * first from the profile's stat strip is the summary, not the audit.
     */
    const val BOOKABILITY = "bookability/{artistId}"

    /**
     * Screen 94 — the landing for a match reached by NEGOTIATION rather than
     * through checkout. Messaging navigates here when an in-thread quote is
     * accepted; [CONFIRMED] stays the funnel's own page, and the two say
     * different things because they are reached from different places.
     */
    const val MATCH_CONFIRMED = "match_confirmed/{bookingId}"

    /**
     * Screen 132 — the booking record. Reachable from Confirmed today and, once
     * the bookings section is rewritten, from Booking detail: it takes the same
     * booking id both would hand it.
     */
    const val INVOICE = "invoice/{bookingId}"

    fun bookingCompose(artistId: String) = "booking/$artistId"
    fun chat(threadId: String) = "chat/$threadId"
    fun confirmed(bookingId: String) = "confirmed/$bookingId"
    fun bookingDetail(bookingId: String) = "booking_detail/$bookingId"
    fun requestQuote(artistId: String) = "request_quote/$artistId"
    fun artistList(kind: String) = "artist_list/$kind"
    fun artistReviews(artistId: String) = "artist_reviews/$artistId"
    fun bookability(artistId: String) = "bookability/$artistId"
    fun matchConfirmed(bookingId: String) = "match_confirmed/$bookingId"
    fun invoice(bookingId: String) = "invoice/$bookingId"
    const val PAYWALL = "paywall"

    /**
     * Account settings → blocked accounts. Same literal on both roles because it
     * is the same screen in two graphs — blocking is not role-specific, and a
     * client who blocks an artist and an artist who blocks a client need the
     * identical way back.
     */
    const val BLOCKED_ACCOUNTS = "blocked_accounts"

    /**
     * Account settings → privacy (design screen 62) and the legal viewer
     * (31 / 114).
     *
     * Same literals on both roles because they are the same screens in two
     * graphs: neither the two privacy switches nor the terms/privacy pair is
     * role-specific, and a client and an artist need the identical way back.
     * Same reasoning as [BLOCKED_ACCOUNTS].
     */
    const val PRIVACY = "privacy"
    const val LEGAL = "legal/{doc}"

    /** [doc] is a [in.artistant.app.feature.signup.LegalDoc] name — the viewer's
     *  opening segment, not a lock: it is segmented, so the other document is one
     *  tap away whichever one you arrive on. */
    fun legal(doc: String) = "legal/$doc"
}

/** String routes for the artist NavHost. */
object ArtistNavRoutes {
    const val BOOKING_DETAIL = "booking_detail/{bookingId}"
    const val GIG_REQUEST = "gig_request/{requestId}"
    const val CHAT = "chat/{threadId}"

    /**
     * Screen 61 — the counter offer, on the artist graph and only there.
     *
     * `gig_requests` has exactly one UPDATE policy (`gig_requests_update_artist`,
     * mig 0002), so the artist is the only party the server lets counter. A
     * client-side route would be a form RLS refuses at submit.
     */
    const val COUNTER_OFFER = "counter_offer/{requestId}"

    fun bookingDetail(bookingId: String) = "booking_detail/$bookingId"
    fun gigRequest(requestId: String) = "gig_request/$requestId"
    fun counterOffer(requestId: String) = "counter_offer/$requestId"
    fun chat(threadId: String) = "chat/$threadId"
    const val PROFILE = "profile"
    const val PAYWALL = "paywall"
    const val WIZARD = "wizard"
    const val MANAGE_AVAILABILITY = "manage_availability"
    const val SCORE_EXPLAINER = "score_explainer"

    /**
     * The score ledger (design screen 51).
     *
     * A pushed screen rather than the sheet it used to be: it is a scrolling
     * audit with its own header, its own failure state and its own retry, and a
     * sheet that fills the display is a screen wearing a grabber.
     */
    const val SCORE_HISTORY = "score_history"

    /** See [ClientNavRoutes.BLOCKED_ACCOUNTS] — same screen, artist graph. */
    const val BLOCKED_ACCOUNTS = "blocked_accounts"

    /** See [ClientNavRoutes.PRIVACY] / [ClientNavRoutes.LEGAL] — same screens, artist graph. */
    const val PRIVACY = "privacy"
    const val LEGAL = "legal/{doc}"

    fun legal(doc: String) = "legal/$doc"
}
