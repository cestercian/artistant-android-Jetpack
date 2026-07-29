package `in`.artistant.app.feature.messages

import `in`.artistant.app.data.model.Thread
import org.junit.Assert.assertEquals
import org.junit.Test

class MessagesFilterLogicTest {

    private fun item(id: String, bookingId: String?) = ThreadListItem(
        thread = Thread(id = id, artistId = "a1", bookingId = bookingId),
        counterpartName = "Name",
    )

    @Test
    fun visibleThreads_partitionsAllBookingsInquiries() {
        val threads = listOf(
            item("t1", bookingId = "b1"),
            item("t2", bookingId = null),
            item("t3", bookingId = "b2"),
        )
        assertEquals(3, MessagesUiState(threads = threads, filter = MessagesFilter.All).visibleThreads.size)
        assertEquals(
            listOf("t1", "t3"),
            MessagesUiState(threads = threads, filter = MessagesFilter.Bookings).visibleThreads.map { it.thread.id },
        )
        assertEquals(
            listOf("t2"),
            MessagesUiState(threads = threads, filter = MessagesFilter.Inquiries).visibleThreads.map { it.thread.id },
        )
    }
}
