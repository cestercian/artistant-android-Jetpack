package `in`.artistant.app.data.model

/**
 * Gig-request (quote) loop — port of iOS `GigRequest` / `StoredRequest`
 * / `GigRequestStatus`. Separate from package bookings: client proposes
 * amount via RequestQuote; artist Accept/Decline/Counter on detail.
 */

enum class GigRequestStatus(val dbValue: String) {
    Open("open"),
    Countered("countered"),
    Accepted("accepted"),
    Declined("declined"),
    Expired("expired"),

    /**
     * A status this build doesn't recognise — the server has moved ahead of the
     * client (some future `withdrawn` / `converted` / …), or a row was stamped
     * by another client with a value this build predates.
     *
     * Decode-only, exactly like [BookingStatus.Unknown]. [dbValue] is a sentinel
     * that is NOT in the `gig_requests.status` check constraint, and no write
     * path serializes a variable status — `accept`/`decline`/`counter` each send
     * their own literal — so this case can never round-trip to the server.
     *
     * It exists because the decoder used to fall back to [Open], which is the
     * opposite of neutral: [Open] is the state that hangs the artist's
     * Accept/Decline/Counter dock and puts the row in Home's "New requests"
     * bucket. An unrecognised status therefore rendered as a live request, and
     * Accept fired a status PATCH against a row this build cannot reason about.
     */
    Unknown("unknown");

    val label: String
        get() = when (this) {
            Open -> "Awaiting response"
            Countered -> "Counter offer"
            Accepted -> "Accepted"
            Declined -> "Declined"
            Expired -> "Expired"
            // Deliberately NOT "Unknown": [BookingStatus.Unknown] shows
            // "Unavailable" for the same case on both clients, and two words for
            // one fact is a support call.
            Unknown -> "Unavailable"
        }

    companion object {
        /**
         * Null / unrecognised → [Unknown]. Never [Open]: a request the client
         * can't interpret must not be handed an Accept button.
         */
        fun fromDb(raw: String?): GigRequestStatus =
            entries.firstOrNull { it.dbValue == raw } ?: Unknown
    }
}

data class GigRequest(
    val id: String,
    /**
     * Who sent it — **null when nothing we can read carries a name.**
     *
     * `users` is self-only under RLS, so the `client:users!client_id(full_name)`
     * embed comes back null on the artist's side of a request, and
     * `gig_requests` has no denormalized `client_name` the way `bookings` and
     * `threads` do (mig 0080). Nullable rather than defaulted to "Client"
     * because those are opposite meanings: a literal name printed for every
     * requester reads as a fact about that person, and it is the same fact for
     * all of them. The UI says "we don't know" instead — see
     * [StoredRequest.requesterName].
     */
    val client: String?,
    val message: String,
    val date: String,
    val amount: Int,
    val packageLabel: String = "Custom",
    val timeAgo: String = "",
    /**
     * The artist the quote is with, lowercased.
     *
     * Carried so a surface that starts from a CONVERSATION can find the quote
     * belonging to it: `threads` has no `request_id`, and the only thing a
     * thread and a gig request share is the pair of people in them. The chat
     * matches on this (design 08 — "quotes are objects, not text").
     *
     * Empty only for a row minted locally by a fake; the server column is NOT
     * NULL.
     */
    val artistId: String = "",
    /**
     * When the offer lapses (`gig_requests.expires_at`), or null if it could not
     * be parsed.
     *
     * The one fact that turns a number into an offer — the inbox preview says
     * "holds till Fri" and the in-thread card "Valid until Fri 6 pm" (screens 19
     * and 08), and neither line may be drawn without this.
     */
    val expiresAtEpochMs: Long? = null,
)

data class StoredRequest(
    val raw: GigRequest,
    val status: GigRequestStatus,
    val counterAmount: Int? = null,
) {
    val id: String get() = raw.id

    /** The requester's name, or null when there isn't one to show. */
    val requesterName: String? get() = raw.client?.takeIf { it.isNotBlank() }
}
