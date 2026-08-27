package `in`.artistant.app.data.repository

import `in`.artistant.app.data.model.Thread
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pins the Fake seam used by message and chat ViewModel tests. */
class MessagesRepositoryLogicTest {
    @Test
    fun send_updatesThreadPreview_andListsMessage() = runTest {
        val repo = FakeMessagesRepository()
        val threadId = repo.findOrCreateThread("11111111-1111-1111-1111-111111111111")

        val sent = repo.send(threadId, "Meet at 8?")

        assertEquals("Meet at 8?", repo.listMessages(threadId).single().body)
        assertEquals("Meet at 8?", repo.listThreadsForUser().single().lastPreview)
        assertEquals(true, sent.isMine)
    }

    @Test
    fun findOrCreate_reusesSameArtistAndBookingPair() = runTest {
        val repo = FakeMessagesRepository()

        val first = repo.findOrCreateThread("11111111-1111-1111-1111-111111111111", "booking-a")
        val same = repo.findOrCreateThread("11111111-1111-1111-1111-111111111111", "booking-a")
        val otherBooking = repo.findOrCreateThread("11111111-1111-1111-1111-111111111111", "booking-b")

        assertEquals(first, same)
        assertNotEquals(first, otherBooking)
    }

    /**
     * The artist seat cannot MINT a thread — that would be `client_id ==
     * artist_id`, which 0081's CHECK rejects — but it must still reach the one
     * 0015 minted when the booking confirmed. So the refusal is typed, and it
     * sits BELOW the lookup: swapping those two would lock artists out of every
     * booking conversation they have.
     */
    @Test
    fun theArtistSeatReachesAnExistingBookingThreadButCannotMintOne() = runTest {
        val existing = Thread(id = THREAD, artistId = ARTIST, clientId = CLIENT, bookingId = "b-1")
        val repo = FakeMessagesRepository(userId = ARTIST, seedThreads = listOf(existing))

        assertEquals(THREAD, repo.findOrCreateThread(ARTIST, bookingId = "b-1"))

        val err = runCatching { repo.findOrCreateThread(ARTIST, bookingId = "b-2") }.exceptionOrNull()
        assertTrue("expected SelfThread, got $err", err is MessagesRepositoryError.SelfThread)
        assertEquals("The conversation opens once the booking is confirmed.", err?.message)
    }

    /**
     * `threads` keeps one unread counter per side, so a mark-read is filtered on
     * the seat it names and a write aimed at the counterparty's column clears
     * NOTHING — which is why the seat is a required argument rather than a
     * defaulted guess, and why the seam no longer writes both sides and lets the
     * server discard the miss.
     */
    @Test
    fun markingReadClearsOnlyTheSeatItNames() = runTest {
        val seed = listOf(Thread(id = THREAD, artistId = ARTIST, clientId = CLIENT, unreadCount = 3))
        val repo = FakeMessagesRepository(userId = CLIENT, seedThreads = seed)

        repo.markThreadRead(THREAD, viewerIsArtist = true)
        assertEquals(3, repo.listThreadsForUser().single().unreadCount)

        repo.markThreadRead(THREAD, viewerIsArtist = false)
        assertEquals(0, repo.listThreadsForUser().single().unreadCount)

        assertEquals(listOf(THREAD to true, THREAD to false), repo.readSeats)
    }

    // ── per-seat mute (migration 0091) ───────────────────────────────────────
    //
    // 0091 puts TWO booleans on `threads` — `client_muted` and `artist_muted` —
    // because a mute is a fact about a READER, not about the conversation. The
    // whole point is that a client silencing a thread must not silence the
    // artist, and vice-versa. The server enforces it (`tg_threads_guard_mute`
    // raises `insufficient_privilege` on a cross-side write), so a client that
    // writes the wrong column doesn't quietly mis-mute — it hard-fails.
    //
    // These two tests are written so that an implementation writing BOTH columns
    // PASSES NOTHING: each asserts the counterparty's column was never written
    // at all (`isEmpty()`), not merely that it isn't `true`. A "set them both"
    // implementation makes the other map non-empty and fails here.
    //
    // They run against `FakeMessagesRepository` rather than a per-test stub
    // precisely because it models the two columns separately, the way the server
    // does — the domain `Thread` collapses them to one viewer-relative flag, so
    // asserting through that would read the same field for both seats and could
    // never catch a cross-side write.

    @Test
    fun mutingAsTheClientWritesTheClientColumnAndLeavesTheArtistsUntouched() = runTest {
        val repo = FakeMessagesRepository(
            userId = CLIENT,
            seedThreads = listOf(Thread(id = THREAD, artistId = ARTIST, clientId = CLIENT)),
        )

        repo.setMuted(THREAD, muted = true)

        assertEquals(mapOf(THREAD to true), repo.clientMuted)
        assertTrue(
            "a client muting must not touch threads.artist_muted — it would silence the artist",
            repo.artistMuted.isEmpty(),
        )
        // And the viewer-relative projection agrees with the column that was set.
        assertTrue(repo.listThreadsForUser().single().muted)
    }

    @Test
    fun mutingAsTheArtistWritesTheArtistColumnAndLeavesTheClientsUntouched() = runTest {
        // Same seeded row, viewed from the other seat: `artistId` IS the artist's
        // user id (0091 relies on that too), so the viewer is the artist here.
        val repo = FakeMessagesRepository(
            userId = ARTIST,
            seedThreads = listOf(Thread(id = THREAD, artistId = ARTIST, clientId = CLIENT)),
        )

        repo.setMuted(THREAD, muted = true)

        assertEquals(mapOf(THREAD to true), repo.artistMuted)
        assertTrue(
            "an artist muting must not touch threads.client_muted — it would silence the client",
            repo.clientMuted.isEmpty(),
        )
        assertTrue(repo.listThreadsForUser().single().muted)
    }

    @Test
    fun oneSidesMuteIsInvisibleToTheOtherSide() = runTest {
        // The counterparty's mute state must never reach the viewer's UI: the
        // artist has muted, and the client's row must still read unmuted.
        val seed = listOf(Thread(id = THREAD, artistId = ARTIST, clientId = CLIENT))
        val artistSeat = FakeMessagesRepository(userId = ARTIST, seedThreads = seed)
        artistSeat.setMuted(THREAD, muted = true)

        val clientSeat = FakeMessagesRepository(userId = CLIENT, seedThreads = seed)

        assertFalse(clientSeat.listThreadsForUser().single().muted)
    }

    @Test
    fun unmutingClearsOnlyTheViewersOwnColumn() = runTest {
        val repo = FakeMessagesRepository(
            userId = CLIENT,
            seedThreads = listOf(Thread(id = THREAD, artistId = ARTIST, clientId = CLIENT)),
        )

        repo.setMuted(THREAD, muted = true)
        repo.setMuted(THREAD, muted = false)

        assertEquals(mapOf(THREAD to false), repo.clientMuted)
        assertTrue(repo.artistMuted.isEmpty())
        assertFalse(repo.listThreadsForUser().single().muted)
    }

    private companion object {
        const val THREAD = "44444444-4444-4444-4444-444444444444"
        const val ARTIST = "11111111-1111-1111-1111-111111111111"
        const val CLIENT = "33333333-3333-3333-3333-333333333333"
    }
}
