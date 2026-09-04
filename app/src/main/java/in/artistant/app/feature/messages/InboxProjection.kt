package `in`.artistant.app.feature.messages

import `in`.artistant.app.data.model.Thread

/**
 * Inbox segments.
 *
 * Only splits the data can actually answer. A thread either has a linked booking
 * or it doesn't, which is the whole of [Bookings] / [Inquiries]; inventing
 * categories the model can't resolve would produce chips that lie about their
 * counts. [Support] is the one exception and it is honest about being synthetic:
 * it holds the scripted Artistant assistant, not conversations, so no thread ever
 * matches it.
 */
enum class MessagesFilter(val label: String) {
    All("All"),
    Bookings("Bookings"),
    Inquiries("Inquiries"),
    Support("Support"),
    ;

    /** True when [thread] belongs in this segment. [Support] holds no threads. */
    fun matches(thread: Thread): Boolean = when (this) {
        All -> true
        Bookings -> thread.bookingId != null
        Inquiries -> thread.bookingId == null
        Support -> false
    }
}

/**
 * One inbox row, fully resolved.
 *
 * Built in the ViewModel rather than the composable so every derived fact —
 * whose name to show, which gig it is about, whether it is starred — is decided
 * once per load and can be asserted in a JVM test.
 */
data class ThreadListItem(
    val thread: Thread,
    val counterpartName: String,
    val context: ThreadContext = ThreadContext.INQUIRY,
    /**
     * The gig artist's cover photo, when the by-id cache has one. Carried as a
     * plain URL rather than an `Artist` so this row model stays free of the
     * artist model's Compose types and the projection stays JVM-testable.
     */
    val coverUrl: String? = null,
    /**
     * The offer standing in this conversation, if any (design 19 — "threads
     * carry deal state").
     *
     * It is what turns the inbox from a contact list into a pipeline: the row
     * shows the amount and when it lapses instead of the last thing somebody
     * typed. Null for every thread with no live or accepted `gig_requests` row,
     * which is most of them.
     */
    val quote: ThreadQuote? = null,
    val starred: Boolean = false,
    val archived: Boolean = false,
    /**
     * Set by the details sheet's "Mark as unread". Device-local: it is a reading
     * intention, not a fact about the server's counter, so it ORs with — never
     * overwrites — [Thread.unreadCount].
     */
    val markedUnread: Boolean = false,
) {
    val unread: Boolean get() = thread.unreadCount > 0 || markedUnread
}

/**
 * Filtering, search and chip counts for the inbox — pure, so the rules can be
 * pinned without a Compose or Android runtime.
 *
 * Everything here runs over the already-loaded list. The inbox is a small,
 * fully-in-memory set (one row per conversation), so segmenting and searching it
 * needs no repository call and no server round-trip — which is also why switching
 * a chip must never refetch.
 */
object InboxProjection {

    /**
     * Rows for [filter] that also match [query].
     *
     * Search covers the counterparty's name and the last-message preview: the two
     * things a person actually remembers about a conversation. It deliberately
     * does NOT search the full transcript — bodies aren't loaded until a thread is
     * opened, so a transcript search would silently only cover whatever happened
     * to be cached and would look broken for every other thread.
     */
    fun visible(
        items: List<ThreadListItem>,
        filter: MessagesFilter,
        query: String,
    ): List<ThreadListItem> {
        val needle = query.trim().lowercase()
        return items.filter { filter.matches(it.thread) && it.matchesQuery(needle) }
    }

    /**
     * Per-chip counts, taken over the SEARCH-MATCHED set.
     *
     * Counting the unsearched list would advertise rows the user can't currently
     * see — tap a chip reading "3" while a query is active and land on one result.
     */
    fun counts(items: List<ThreadListItem>, query: String): Map<MessagesFilter, Int> {
        val needle = query.trim().lowercase()
        val matching = items.filter { it.matchesQuery(needle) }
        return MessagesFilter.entries.associateWith { filter ->
            matching.count { filter.matches(it.thread) }
        }
    }

    private fun ThreadListItem.matchesQuery(needle: String): Boolean =
        needle.isEmpty() ||
            counterpartName.lowercase().contains(needle) ||
            thread.lastPreview.lowercase().contains(needle)

    /**
     * "now" / "12m" / "5h" / "3d" — the trailing stamp on a row.
     *
     * Compact on purpose: the timestamp is the least important thing in the row
     * and shares a baseline with the name, so it has to stay narrow enough never
     * to squeeze the name it sits beside. Anything older than a day rounds to
     * days and keeps counting rather than switching to a date, matching the
     * reference inbox.
     *
     * A null stamp (a thread the server has never had a message in) renders as
     * nothing rather than "now", which would be a lie about an empty thread. A
     * future stamp — clock skew between device and server — also renders as
     * "now" rather than a negative age.
     */
    fun timeAgo(thenMs: Long?, nowMs: Long): String {
        val then = thenMs ?: return ""
        val minutes = (nowMs - then) / MILLIS_PER_MINUTE
        return when {
            minutes < 1L -> "now"
            minutes < MINUTES_PER_HOUR -> "${minutes}m"
            minutes < MINUTES_PER_DAY -> "${minutes / MINUTES_PER_HOUR}h"
            else -> "${minutes / MINUTES_PER_DAY}d"
        }
    }

    private const val MILLIS_PER_MINUTE = 60_000L
    private const val MINUTES_PER_HOUR = 60L
    private const val MINUTES_PER_DAY = 60L * 24L
}
