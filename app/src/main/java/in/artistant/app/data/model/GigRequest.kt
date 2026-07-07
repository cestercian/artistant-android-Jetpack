package `in`.artistant.app.data.model

/**
 * Gig-request (negotiation) models — ports of iOS `Models/Artist.swift` GigRequest
 * + `State/RequestStore.swift` StoredRequest/GigRequestStatus. A client proposes a
 * quote; the artist answers accept / decline / counter.
 */

/** Negotiation state. Rawvalues match `gig_requests.status`. */
enum class GigRequestStatus(val dbValue: String, val label: String) {
    Open("open", "Open"),
    Countered("countered", "Countered"),
    Accepted("accepted", "Accepted"),
    Declined("declined", "Declined"),
    Expired("expired", "Expired");

    companion object {
        fun fromDb(raw: String?): GigRequestStatus =
            entries.firstOrNull { it.dbValue == raw } ?: Open
    }
}

/**
 * The immutable proposal shown in the artist's inbox / client's outbox (iOS
 * `GigRequest`). [client] is the resolved display name from the embed; [package]
 * is a free-text label ("Custom" until a richer projection lands).
 */
data class GigRequest(
    val id: String,
    val client: String,
    val message: String,
    val date: String,
    val amount: Int,
    val `package`: String,
    val timeAgo: String,
)

/**
 * A [GigRequest] plus its mutable negotiation state (iOS `StoredRequest`).
 * [counterAmount] is set only when the artist countered.
 */
data class StoredRequest(
    val raw: GigRequest,
    val status: GigRequestStatus,
    val counterAmount: Int? = null,
) {
    val id: String get() = raw.id
}
