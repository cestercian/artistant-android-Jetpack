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
    Expired("expired");

    val label: String
        get() = when (this) {
            Open -> "Awaiting response"
            Countered -> "Counter offer"
            Accepted -> "Accepted"
            Declined -> "Declined"
            Expired -> "Expired"
        }

    companion object {
        fun fromDb(raw: String?): GigRequestStatus =
            entries.firstOrNull { it.dbValue == raw } ?: Open
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
