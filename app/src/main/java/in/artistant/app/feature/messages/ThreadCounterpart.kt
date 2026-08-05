package `in`.artistant.app.feature.messages

import `in`.artistant.app.data.model.Thread

/**
 * "Who am I talking to" — resolved from the VIEWER's side of the thread.
 *
 * A thread has exactly two seats: `client_id` and `artist_id`. The counterparty
 * is whichever one the viewer is NOT sitting in, so the same row resolves to two
 * different names depending on who is looking. Getting this wrong is not cosmetic:
 * the previous ladder preferred `thread.clientName` unconditionally, and
 * `client_name` is by definition the CLIENT's name — so every client read their
 * own name as the person they were messaging.
 *
 * Why `client_name` exists at all: RLS nulls the `users` embed for the
 * counterparty, so the artist seat cannot join to the client's row. Migration
 * 0080 denormalizes the name onto `threads` for exactly that seat. There is no
 * mirror column for the artist name because the artist's profile is public —
 * the client seat resolves it through the artist cache instead.
 *
 * Pure and side-effect free so all three surfaces (inbox row, chat title,
 * details sheet) share one rule and can be tested without a session.
 */
object ThreadCounterpart {
    /** Shown when the artist's profile hasn't hydrated into the cache yet. */
    const val ARTIST_PLACEHOLDER = "Artist"

    /** Shown when the server hasn't stamped `client_name` on the thread. */
    const val CLIENT_PLACEHOLDER = "Client"

    /**
     * True when [viewerId] occupies the artist seat.
     *
     * A null [viewerId] (signed out, or the session dropped mid-render) is
     * deliberately NOT the artist seat: we can't prove which side they're on, and
     * the client seat is the safe default because it resolves to the artist's
     * public name — a name that is never the viewer's own. Guessing "artist"
     * instead would reintroduce the original bug for anonymous readers.
     */
    fun viewerIsArtist(thread: Thread, viewerId: String?): Boolean =
        viewerId != null && thread.artistId.equals(viewerId, ignoreCase = true)

    /**
     * The counterparty's display name. [artistName] is the caller's cache lookup
     * for `thread.artistId` (passed in rather than fetched here to keep this pure).
     *
     * Blank is treated as missing on both seats — the server writes "" as often as
     * null — and the fallback is always a neutral placeholder, never a raw uuid.
     */
    fun name(thread: Thread, viewerId: String?, artistName: String?): String =
        if (viewerIsArtist(thread, viewerId)) {
            thread.clientName?.takeIf { it.isNotBlank() } ?: CLIENT_PLACEHOLDER
        } else {
            artistName?.takeIf { it.isNotBlank() } ?: ARTIST_PLACEHOLDER
        }

    /**
     * The counterparty's ROLE label, for the details-sheet participant rows and
     * the incoming-run caption. Always the opposite of the viewer's own seat.
     */
    fun counterpartRole(viewerIsArtist: Boolean): String =
        if (viewerIsArtist) CLIENT_PLACEHOLDER else ARTIST_PLACEHOLDER

    /** The viewer's own role label ("You" row in the details sheet). */
    fun viewerRole(viewerIsArtist: Boolean): String =
        if (viewerIsArtist) ARTIST_PLACEHOLDER else CLIENT_PLACEHOLDER
}
