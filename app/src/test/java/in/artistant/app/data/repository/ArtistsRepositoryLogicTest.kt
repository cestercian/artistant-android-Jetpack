package `in`.artistant.app.data.repository

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    private companion object {
        const val ARTIST = "11111111-1111-1111-1111-111111111111"
        const val NOT_AN_ARTIST = "33333333-3333-3333-3333-333333333333"
    }
}
