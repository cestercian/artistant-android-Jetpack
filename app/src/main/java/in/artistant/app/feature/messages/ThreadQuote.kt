package `in`.artistant.app.feature.messages

import `in`.artistant.app.data.model.GigRequestStatus
import `in`.artistant.app.data.model.StoredRequest

/**
 * The offer standing in a conversation — design 08, "quotes are objects, not text".
 *
 * A quote is a `gig_requests` row, not a message. That is the whole point of the
 * screen: an amount typed into the transcript is a screenshot waiting to be
 * argued about, whereas this one has an id, a status, an expiry, and two buttons
 * that change the row rather than the chat.
 *
 * **How a thread finds its quote.** `threads` carries `client_id`, `artist_id`
 * and `booking_id` — there is no `request_id`, and adding one is a schema change
 * this redesign does not get to make. What a thread and a request DO share is the
 * pair of people in them, so the match is on the artist: the viewer's own request
 * list (RLS already restricts it to their side) filtered to this thread's artist,
 * newest first. That is exact for the app's actual shape — a client negotiates one
 * live quote with an artist at a time — and it degrades to "no card" rather than
 * to a wrong card if it ever stops being.
 *
 * Pure, so the seat rules below can be pinned in a JVM test.
 */
data class ThreadQuote(
    val requestId: String,
    /** What is on the table now — the counter if there is one, else the proposal. */
    val amountInr: Int,
    /** True when [amountInr] is the artist's counter rather than the original ask. */
    val countered: Boolean,
    val packageLabel: String,
    /** The gig's own day, as the request stored it. Blank when it wasn't given. */
    val dateLabel: String,
    /** When the offer lapses. Null when the row carried no parseable deadline. */
    val expiresAtEpochMs: Long? = null,
    val status: GigRequestStatus,
    /**
     * Whether the NEXT move belongs to the viewer.
     *
     * Seat-aware, exactly like [ThreadContext.awaitingViewer], and for the same
     * reason: an `open` request is the client's ask and the ARTIST answers it; a
     * `countered` one is the artist's reply and the CLIENT answers that. Drawing
     * Accept/Counter on the other seat would offer someone a decision that is
     * not theirs, and the write would be rejected by RLS after they had already
     * been told it was theirs to make.
     */
    val viewerDecides: Boolean,
    /** Past its own deadline. Rendered, but never actionable. */
    val expired: Boolean,
) {
    /** Terms line: "Custom · Sat 12 Oct", dropping whichever half is missing. */
    val terms: String
        get() = listOf(packageLabel, dateLabel)
            .filter { it.isNotBlank() }
            .joinToString(ThreadContext.SEPARATOR)

    /** Accept/Counter are live only while the offer is the viewer's to answer. */
    val actionable: Boolean get() = viewerDecides && !expired

    /** The terms are settled and the card is now a record, not a decision. */
    val frozen: Boolean get() = status == GigRequestStatus.Accepted

    companion object {
        /**
         * The quote a thread is about, or null.
         *
         * Statuses that are neither live nor a record — `declined`, `expired`,
         * and the decode-only `unknown` — produce no card. A declined quote is
         * not the state of this conversation, it is the end of a previous one,
         * and `unknown` means this build cannot read the row at all, which is
         * exactly when it must not draw buttons against it.
         */
        fun pick(
            requests: List<StoredRequest>,
            artistId: String?,
            viewerIsArtist: Boolean,
            nowMs: Long,
        ): ThreadQuote? {
            val key = artistId?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
            // The repositories order by `created_at` descending, so the first
            // match is the newest — no timestamp is needed on the model to say
            // which of two live quotes is the current one.
            val match = requests.firstOrNull { stored ->
                stored.raw.artistId.equals(key, ignoreCase = true) && stored.status.rendersInThread
            } ?: return null
            return from(match, viewerIsArtist, nowMs)
        }

        fun from(stored: StoredRequest, viewerIsArtist: Boolean, nowMs: Long): ThreadQuote {
            val expiry = stored.raw.expiresAtEpochMs
            val expired = expiry != null && expiry <= nowMs
            val countered = stored.counterAmount != null
            return ThreadQuote(
                requestId = stored.id,
                amountInr = stored.counterAmount ?: stored.raw.amount,
                countered = countered,
                packageLabel = stored.raw.packageLabel,
                dateLabel = stored.raw.date,
                expiresAtEpochMs = expiry,
                status = stored.status,
                viewerDecides = decidesNext(stored.status, viewerIsArtist),
                expired = expired,
            )
        }

        /**
         * Who answers a quote in this state.
         *
         * `open` — the client proposed, the artist answers.
         * `countered` — the artist replied, the client answers.
         * anything else — nobody; the row is a record or unreadable.
         */
        fun decidesNext(status: GigRequestStatus, viewerIsArtist: Boolean): Boolean =
            when (status) {
                GigRequestStatus.Open -> viewerIsArtist
                GigRequestStatus.Countered -> !viewerIsArtist
                else -> false
            }

        /** Statuses a thread draws a card for at all. */
        private val GigRequestStatus.rendersInThread: Boolean
            get() = this == GigRequestStatus.Open ||
                this == GigRequestStatus.Countered ||
                this == GigRequestStatus.Accepted
    }
}
