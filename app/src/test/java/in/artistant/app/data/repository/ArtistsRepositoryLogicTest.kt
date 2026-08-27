package `in`.artistant.app.data.repository

import `in`.artistant.app.core.result.AppError
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The by-id artist cache has to remember MISSES, not only hits.
 *
 * `fetchArtist` short-circuits a hit on the hydrated set, but a lookup that
 * legitimately resolves to no row used to record nothing — so it re-ran the
 * five-table profile fan-out on every single call. That is not a hypothetical
 * path: Blocked accounts asks for exactly those ids on purpose (a blocked CLIENT
 * id is never in `artists`, and an artist row is the one public place a block can
 * be given a name), and it re-asks on every pull-to-refresh. Five round trips per
 * blocked client, per refresh, each guaranteed to come back empty.
 *
 * The counter these assert through ([FakeArtistsRepository.fetchedIds]) is the
 * only way to see it: a miss answers null either way.
 */
class ArtistsRepositoryLogicTest {

    @Test
    fun aConfirmedMissIsAskedForOnce_notOncePerCall() = runTest {
        val repo = FakeArtistsRepository()

        assertNull(repo.ensureFull(NOT_AN_ARTIST))
        assertNull(repo.ensureFull(NOT_AN_ARTIST))
        assertNull(repo.ensureFull(NOT_AN_ARTIST))

        assertEquals(listOf(NOT_AN_ARTIST), repo.fetchedIds)
    }

    @Test
    fun aHydratedArtistIsAlsoAskedForOnce() = runTest {
        val repo = FakeArtistsRepository(remote = listOf(FakeArtistsRepository.sample(id = ARTIST)))

        assertEquals(ARTIST, repo.ensureFull(ARTIST)?.id)
        assertEquals(ARTIST, repo.ensureFull(ARTIST)?.id)

        assertEquals(listOf(ARTIST), repo.fetchedIds)
    }

    /**
     * A memoized miss must not outlive the fact behind it. A tile projection
     * carrying the id is proof the row exists now — an artist published between
     * the two asks — so the next `ensureFull` has to go and get them.
     */
    @Test
    fun cachingATileForAMissedIdRetiresTheMiss() = runTest {
        val repo = FakeArtistsRepository()
        assertNull(repo.ensureFull(ARTIST))

        // A Discover/Search page lands the artist as a tile projection — proof
        // the row exists now, whatever the earlier ask found.
        repo.cache(listOf(FakeArtistsRepository.sample(id = ARTIST)))

        assertEquals(ARTIST, repo.ensureFull(ARTIST)?.id)
    }

    @Test
    fun cache_neverDowngradesAHydratedArtist() = runTest {
        val full = FakeArtistsRepository.sample(id = "a1", name = "Full Name").copy(bio = "full bio")
        val repo = FakeArtistsRepository(seed = listOf(full))
        repo.cache(listOf(FakeArtistsRepository.sample(id = "a1", name = "Partial").copy(bio = "")))
        assertEquals("Full Name", repo.find("a1")?.name)
        assertEquals("full bio", repo.find("a1")?.bio)
    }

    @Test
    fun ensureFull_returnsNullOnFailure() = runTest {
        val repo = FakeArtistsRepository(seed = listOf(FakeArtistsRepository.sample()))
        repo.failFetch = true
        assertNull(repo.ensureFull("a1"))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Self-row edits — mutateSelf/updateAvailability/setPublished used to
    // resolve their target as `byId.keys.firstOrNull()`, insertion order
    // rather than identity, so a multi-artist fake could have a press-kit
    // save land on the WRONG artist while "my edit is visible on the next
    // read" still passed.
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun aSelfRowEditOnAMultiArtistFakeWithNoSelfIdRefusesRatherThanGuess() = runTest {
        val repo = FakeArtistsRepository(
            seed = listOf(
                FakeArtistsRepository.sample(id = ARTIST),
                FakeArtistsRepository.sample(id = OTHER_ARTIST),
            ),
        )

        val err = runCatching { repo.updateBio("new bio") }.exceptionOrNull()

        assertTrue("expected NotFoundOrUnauthorized, got $err", err is AppError.NotFoundOrUnauthorized)
        // Neither row moved — the old bug wrote silently to whichever key came first.
        assertEquals("Live sets for rooftops and weddings.", repo.find(ARTIST)?.bio)
        assertEquals("Live sets for rooftops and weddings.", repo.find(OTHER_ARTIST)?.bio)
    }

    @Test
    fun aSelfRowEditTargetsTheNamedSelfId_notWhicheverArtistWasSeededFirst() = runTest {
        val repo = FakeArtistsRepository(
            seed = listOf(
                FakeArtistsRepository.sample(id = ARTIST),
                FakeArtistsRepository.sample(id = OTHER_ARTIST),
            ),
            selfId = OTHER_ARTIST,
        )

        repo.updateBio("new bio")

        assertEquals("Live sets for rooftops and weddings.", repo.find(ARTIST)?.bio)
        assertEquals("new bio", repo.find(OTHER_ARTIST)?.bio)
    }

    @Test
    fun setPublished_refusesAnArtistIdThatIsNotSelf() = runTest {
        val repo = FakeArtistsRepository(seed = listOf(FakeArtistsRepository.sample(id = ARTIST)))

        val err = runCatching { repo.setPublished(OTHER_ARTIST, published = true) }.exceptionOrNull()

        assertTrue("expected IllegalArgumentException, got $err", err is IllegalArgumentException)
        assertFalse(repo.published)
    }

    private companion object {
        const val ARTIST = "11111111-1111-1111-1111-111111111111"
        const val OTHER_ARTIST = "22222222-2222-2222-2222-222222222222"
        const val NOT_AN_ARTIST = "33333333-3333-3333-3333-333333333333"
    }
}
