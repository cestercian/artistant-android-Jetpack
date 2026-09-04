package `in`.artistant.app.feature.messages

import `in`.artistant.app.data.model.GigRequestStatus
import `in`.artistant.app.data.model.StoredRequest
import `in`.artistant.app.data.model.Thread

/**
 * The offer standing in a conversation — design 08, "quotes are objects, not text".
 *
 * A quote is a `gig_requests` row, not a message. That is the whole point of the
 * screen: an amount typed into the transcript is a screenshot waiting to be
 * argued about, whereas this one has an id, a status, an expiry, and two buttons
 * that change the row rather than the chat.
 *
 * **How a thread finds its quote** — see [Companion.pick], which is where the
 * whole question lives.
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
    /**
     * Which seat is reading the card.
     *
     * Not derivable from [viewerDecides] — both seats read a `frozen` card, and
     * nobody decides that one. It is needed because the copy on an accepted card
     * names who has to move next, and only the CLIENT can: creating the booking
     * is an insert `bookings_insert_client` gates on `auth.uid() = client_id`.
     */
    val viewerIsArtist: Boolean,
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
         * **There is no `request_id` on `threads` and no `thread_id` on
         * `gig_requests`** — checked against the canonical migrations, and adding
         * one is a schema change this redesign does not get to make. So the key
         * has to be built out of what the two rows genuinely share, and it is
         * built to say "I don't know" rather than to guess:
         *
         * 1. **A thread with a `booking_id` draws no card.** That conversation is
         *    about its booking, and its state is the status capsule at the head
         *    of the transcript. The gig-request loop lives somewhere else
         *    entirely: migration 0047/0076's trigger opens a BOOKINGLESS thread
         *    when an artist accepts a request, and deliberately creates no
         *    booking. Matching a request into a booking thread was the actual
         *    bug — two people who have a confirmed booking and a separate open
         *    quote saw the quote's Accept button on the booking's conversation.
         * 2. **Otherwise both halves of the pair must match** — `artist_id` AND
         *    `client_id`. The artist half alone is not a filter on the artist's
         *    own seat: `listForArtist()` returns every client's requests and
         *    `artist_id` is the viewer's own id on all of them, so one client's
         *    quote was rendering on another client's thread (and, through the
         *    same call in the inbox, on every artist row at once).
         * 3. **Exactly one candidate, or none.** The bookingless thread is unique
         *    per pair — `threads_unique_per_pair_booking` collapses every null
         *    `booking_id` onto one sentinel key (0001, restated in 0076) — but
         *    two live requests between the same pair are not, and nothing in
         *    either row says which of them this conversation is. Two candidates
         *    is not a reason to show the newer one; it is the definition of not
         *    knowing. The card returns on its own as soon as the ambiguity does:
         *    a request leaves the rendering set when it is declined, or when
         *    `sweep_expired_gig_requests` (0090) expires it.
         *
         * Statuses that are neither live nor a record — `declined`, `expired`,
         * and the decode-only `unknown` — produce no card and do not count as
         * candidates. A declined quote is not the state of this conversation, it
         * is the end of a previous one, and `unknown` means this build cannot
         * read the row at all, which is exactly when it must not draw buttons
         * against it.
         */
        fun pick(
            requests: List<StoredRequest>,
            thread: Thread?,
            viewerIsArtist: Boolean,
            nowMs: Long,
        ): ThreadQuote? {
            if (thread == null) return null
            if (!thread.bookingId.isNullOrBlank()) return null
            val artistKey = thread.artistId.lowercase().takeIf { it.isNotBlank() } ?: return null
            val clientKey = thread.clientId.lowercase().takeIf { it.isNotBlank() } ?: return null
            // `singleOrNull`, not `firstOrNull`: newest-first ordering answers
            // "which is most recent", which is a different question from "which
            // is this conversation's".
            val match = requests.singleOrNull { stored ->
                stored.raw.artistId.equals(artistKey, ignoreCase = true) &&
                    stored.raw.clientId.equals(clientKey, ignoreCase = true) &&
                    stored.status.rendersInThread
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
                viewerIsArtist = viewerIsArtist,
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
