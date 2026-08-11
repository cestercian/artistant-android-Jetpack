package `in`.artistant.app.feature.profile

import `in`.artistant.app.data.model.Thread
import `in`.artistant.app.testsupport.ARTIST_ID
import `in`.artistant.app.testsupport.CLIENT_ID
import `in`.artistant.app.testsupport.OTHER_ARTIST_ID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The two decisions the blocked-accounts screen makes, without a Compose tree.
 *
 * Both are the kind that look trivial and are not: one decides whether an empty
 * screen is allowed to say "nobody is blocked", the other decides whose name
 * goes next to an Unblock control.
 */
class BlockedAccountsTest {

    private fun thread(
        id: String = "t-1",
        artistId: String = ARTIST_ID,
        clientId: String = CLIENT_ID,
        clientName: String? = null,
    ) = Thread(id = id, artistId = artistId, clientId = clientId, clientName = clientName)

    // --- status: empty is not the same as unknown ----------------------------

    @Test
    fun beforeTheFirstLoadCompletesTheScreenClaimsNothing() {
        assertEquals(
            BlockedAccountsStatus.Loading,
            BlockedAccounts.status(loadCompleted = false, serverReadOk = false, rowCount = 0),
        )
    }

    @Test
    fun aServerReadThatReturnedNothingMeansNobodyIsBlocked() {
        assertEquals(
            BlockedAccountsStatus.Ready,
            BlockedAccounts.status(loadCompleted = true, serverReadOk = true, rowCount = 0),
        )
    }

    @Test
    fun aFailedReadWithNothingKnownIsUnavailableNotEmpty() {
        // The whole point of the enum. Offline, signed out, and 0087-not-applied
        // all land here, and every one of them would render as "no one is
        // blocked" if this collapsed into Ready — telling someone who blocked a
        // person last week that they hadn't.
        assertEquals(
            BlockedAccountsStatus.Unavailable,
            BlockedAccounts.status(loadCompleted = true, serverReadOk = false, rowCount = 0),
        )
    }

    @Test
    fun aFailedReadOverAKnownSetStillShowsItButFlaggedAsStale() {
        // These rows came off this device's own mirror. Worth showing — they're
        // the only way to unblock while offline — but not worth presenting as
        // the complete list.
        assertEquals(
            BlockedAccountsStatus.Stale,
            BlockedAccounts.status(loadCompleted = true, serverReadOk = false, rowCount = 2),
        )
    }

    // --- rows: names are inferred, never invented ----------------------------

    @Test
    fun aBlockedArtistIsNamedFromTheArtistCache() {
        val rows = BlockedAccounts.rows(
            blockedIds = setOf(ARTIST_ID),
            threads = listOf(thread()),
            artistName = { if (it == ARTIST_ID.lowercase()) "Nova Beats" else null },
        )

        assertEquals(1, rows.size)
        assertEquals("Nova Beats", rows.single().displayName)
        assertEquals("Artist", rows.single().role)
    }

    @Test
    fun aBlockedClientIsNamedFromTheThreadsDenormalizedColumn() {
        // `client_name` (migration 0080) is the only readable source for the
        // client seat — RLS nulls the users embed for the counterparty.
        val rows = BlockedAccounts.rows(
            blockedIds = setOf(CLIENT_ID),
            threads = listOf(thread(clientName = "Meera Iyer")),
            artistName = { "Nova Beats" },
        )

        assertEquals("Meera Iyer", rows.single().displayName)
        assertEquals("Client", rows.single().role)
    }

    @Test
    fun theSeatIsReadOffTheBlockedIdNotTheViewer() {
        // Both seats of the same thread, blocked in two separate lists. The
        // function is never told who is looking, and still resolves each to the
        // right side — you cannot block yourself, so the blocked id IS the seat.
        val threads = listOf(thread(clientName = "Meera Iyer"))

        val artistSide = BlockedAccounts.rows(setOf(ARTIST_ID), threads) { "Nova Beats" }
        val clientSide = BlockedAccounts.rows(setOf(CLIENT_ID), threads) { "Nova Beats" }

        assertEquals("Artist", artistSide.single().role)
        assertEquals("Client", clientSide.single().role)
    }

    @Test
    fun anUnknownIdKeepsItsNameNullRatherThanInventingOne() {
        // No thread and no cached profile. A row that guessed a name here would
        // put the wrong person next to an Unblock control.
        val rows = BlockedAccounts.rows(
            blockedIds = setOf(OTHER_ARTIST_ID),
            threads = listOf(thread()),
            artistName = { null },
        )

        assertNull(rows.single().displayName)
        assertNull(rows.single().role)
        // The id fragment is what's left to tell two unnamed rows apart.
        assertEquals(OTHER_ARTIST_ID.take(8), rows.single().shortId)
    }

    @Test
    fun aBlankClientNameCountsAsNoNameAtAll() {
        // The server writes "" about as often as it writes null.
        val rows = BlockedAccounts.rows(
            blockedIds = setOf(CLIENT_ID),
            threads = listOf(thread(clientName = "  ")),
            artistName = { null },
        )

        assertNull(rows.single().displayName)
        // The seat is still known, so the row can still say which side they sat on.
        assertEquals("Client", rows.single().role)
    }

    @Test
    fun anIdWithNoThreadCanStillBeNamedFromThePublicArtistCache() {
        // The thread list can fail while the artists cache is warm, and an
        // artist row is public — so this is the one recoverable nameless case.
        val rows = BlockedAccounts.rows(
            blockedIds = setOf(OTHER_ARTIST_ID),
            threads = emptyList(),
            artistName = { "Echo Halls" },
        )

        assertEquals("Echo Halls", rows.single().displayName)
        assertEquals("Artist", rows.single().role)
    }

    @Test
    fun idsAreLowercasedSoOneBlockCannotRenderAsTwoRows() {
        val rows = BlockedAccounts.rows(
            blockedIds = setOf(ARTIST_ID.uppercase(), ARTIST_ID.lowercase()),
            threads = listOf(thread()),
            artistName = { "Nova Beats" },
        )

        assertEquals(1, rows.size)
        assertEquals(ARTIST_ID.lowercase(), rows.single().userId)
    }

    @Test
    fun namedRowsSortFirstAndAlphabeticallySoTheListDoesNotReshuffle() {
        val threads = listOf(
            thread(id = "t-1", artistId = ARTIST_ID),
            thread(id = "t-2", artistId = OTHER_ARTIST_ID),
        )
        val rows = BlockedAccounts.rows(
            blockedIds = setOf(ARTIST_ID, OTHER_ARTIST_ID, "99999999-9999-9999-9999-999999999999"),
            threads = threads,
            artistName = { id ->
                when (id) {
                    ARTIST_ID.lowercase() -> "Nova Beats"
                    OTHER_ARTIST_ID.lowercase() -> "Echo Halls"
                    else -> null
                }
            },
        )

        assertEquals(listOf("Echo Halls", "Nova Beats", null), rows.map { it.displayName })
    }
}
