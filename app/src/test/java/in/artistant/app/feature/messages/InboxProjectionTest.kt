package `in`.artistant.app.feature.messages

import `in`.artistant.app.data.model.Thread
import `in`.artistant.app.testsupport.ARTIST_ID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Segmenting, searching and stamping the inbox.
 *
 * These are the rules a reader forms an expectation about within a minute of
 * using the screen — a chip's number should equal the rows behind it, a search
 * should find the thing they half-remember, and a stamp should not claim an old
 * message is new.
 */
class InboxProjectionTest {

    private fun row(
        id: String,
        name: String = "Nova Beats",
        bookingId: String? = null,
        preview: String = "",
    ) = ThreadListItem(
        thread = Thread(id = id, artistId = ARTIST_ID, bookingId = bookingId, lastPreview = preview),
        counterpartName = name,
    )

    private val rows = listOf(
        row("t-1", name = "Nova Beats", bookingId = "b-1", preview = "See you Saturday"),
        row("t-2", name = "Kabir Sen Trio", preview = "What are your rates?"),
        row("t-3", name = "Asha Rao", bookingId = "b-2", preview = "Confirmed, thanks!"),
    )

    // --- segments ------------------------------------------------------------

    @Test
    fun theBookingsAndInquiriesSegmentsPartitionTheInbox() {
        val booked = InboxProjection.visible(rows, MessagesFilter.Bookings, "")
        val inquiries = InboxProjection.visible(rows, MessagesFilter.Inquiries, "")

        assertEquals(listOf("t-1", "t-3"), booked.map { it.thread.id })
        assertEquals(listOf("t-2"), inquiries.map { it.thread.id })
        assertEquals(rows.size, booked.size + inquiries.size)
    }

    /** Support is synthetic: it is an assistant, not a bucket threads fall into. */
    @Test
    fun supportHoldsNoThreads() {
        assertTrue(InboxProjection.visible(rows, MessagesFilter.Support, "").isEmpty())
        assertFalse(MessagesFilter.Support.matches(rows.first().thread))
    }

    // --- search --------------------------------------------------------------

    @Test
    fun searchMatchesNameOrPreviewCaseInsensitively() {
        assertEquals(
            listOf("t-2"),
            InboxProjection.visible(rows, MessagesFilter.All, "KABIR").map { it.thread.id },
        )
        assertEquals(
            listOf("t-1"),
            InboxProjection.visible(rows, MessagesFilter.All, "saturday").map { it.thread.id },
        )
    }

    @Test
    fun aBlankOrWhitespaceQueryMatchesEverything() {
        assertEquals(rows.size, InboxProjection.visible(rows, MessagesFilter.All, "").size)
        assertEquals(rows.size, InboxProjection.visible(rows, MessagesFilter.All, "   ").size)
    }

    @Test
    fun searchAndSegmentCompose() {
        // "Confirmed, thanks!" is on a booked thread, so it survives both filters.
        assertEquals(
            listOf("t-3"),
            InboxProjection.visible(rows, MessagesFilter.Bookings, "confirmed").map { it.thread.id },
        )
        // The same query under Inquiries has nothing to match.
        assertTrue(InboxProjection.visible(rows, MessagesFilter.Inquiries, "confirmed").isEmpty())
    }

    // --- counts --------------------------------------------------------------

    @Test
    fun countsMatchTheRowsEachChipWouldShow() {
        val counts = InboxProjection.counts(rows, "")

        MessagesFilter.entries.forEach { filter ->
            assertEquals(
                "$filter count must equal its rows",
                InboxProjection.visible(rows, filter, "").size,
                counts[filter],
            )
        }
    }

    /**
     * The specific dishonesty this guards: a chip reading "2" while a query is
     * active, that opens onto one row.
     */
    @Test
    fun countsShrinkWithAnActiveQuery() {
        val counts = InboxProjection.counts(rows, "saturday")

        assertEquals(1, counts[MessagesFilter.All])
        assertEquals(1, counts[MessagesFilter.Bookings])
        assertEquals(0, counts[MessagesFilter.Inquiries])
    }

    // --- stamps --------------------------------------------------------------

    @Test
    fun timeAgoStepsThroughMinutesHoursAndDays() {
        val now = 1_000_000_000_000L
        val minute = 60_000L

        assertEquals("now", InboxProjection.timeAgo(now - 30_000L, now))
        assertEquals("12m", InboxProjection.timeAgo(now - 12 * minute, now))
        assertEquals("5h", InboxProjection.timeAgo(now - 5 * 60 * minute, now))
        assertEquals("3d", InboxProjection.timeAgo(now - 3 * 24 * 60 * minute, now))
    }

    /** A thread the server has never had a message in has no age to report. */
    @Test
    fun aMissingStampRendersAsNothingNotAsNow() {
        assertEquals("", InboxProjection.timeAgo(null, 1_000L))
    }

    /** Device/server clock skew must not produce a negative age. */
    @Test
    fun aFutureStampReadsAsNow() {
        assertEquals("now", InboxProjection.timeAgo(2_000L, 1_000L))
    }

    /** The boundaries, because off-by-one here shows up as "60m" and "24h". */
    @Test
    fun theUnitBoundariesRollOverExactly() {
        val now = 1_000_000_000_000L
        val minute = 60_000L

        assertEquals("59m", InboxProjection.timeAgo(now - 59 * minute, now))
        assertEquals("1h", InboxProjection.timeAgo(now - 60 * minute, now))
        assertEquals("23h", InboxProjection.timeAgo(now - 23 * 60 * minute, now))
        assertEquals("1d", InboxProjection.timeAgo(now - 24 * 60 * minute, now))
    }

    // --- unread --------------------------------------------------------------

    /**
     * The explicit mark and the server counter are independent signals. Marking
     * unread must not need the server to agree, and clearing the mark must not
     * silence a genuinely-unread thread.
     */
    @Test
    fun unreadIsTheServerCounterOrTheExplicitMark() {
        val counted = row("t-1").let { it.copy(thread = it.thread.copy(unreadCount = 2)) }
        val marked = row("t-2").copy(markedUnread = true)

        assertTrue(counted.unread)
        assertTrue(marked.unread)
        assertFalse(row("t-3").unread)
    }
}
