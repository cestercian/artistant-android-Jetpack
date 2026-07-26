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

    companion object {
        fun fromDb(raw: String?): GigRequestStatus =
            entries.firstOrNull { it.dbValue == raw } ?: Open
    }
}

data class GigRequest(
    val id: String,
    val client: String,
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
}
