package `in`.artistant.app.data.repository

import `in`.artistant.app.data.model.Artist
import `in`.artistant.app.data.model.ArtistGradient
import `in`.artistant.app.data.model.Review
import `in`.artistant.app.data.model.SearchCursor
import `in`.artistant.app.data.model.SearchFilters
import `in`.artistant.app.data.model.SearchSort
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Confirms the Browse fakes honour the same contracts the real repos do. */
class BrowseFakesTest {

    private fun artist(id: String, name: String, score: Int, price: Int, full: Boolean) = Artist(
        id = id, name = name, handle = name.lowercase(), category = "DJ", genre = "House",
        city = "Mumbai", price = price, duration = if (full) "2 hr" else "", score = score,
        gradient = ArtistGradient.palette(0), bio = if (full) "real bio" else "",
        followers = "", streams = "", response = "", onTime = 0, gigs = 0, rating = 0.0,
        packages = emptyList(), tech = emptyList(), samples = emptyList(), reviews = emptyList(),
    )

    @Test
    fun `cache never downgrades a hydrated full entry to a partial`() = runTest {
        val full = artist("A1", "Neon", 90, 25000, full = true)
        val repo = FakeArtistsRepository(full = listOf(full))

        // A search projection for the same id must NOT overwrite the full profile.
        val partial = artist("a1", "Neon", 90, 0, full = false)  // note lowercase id
        repo.cache(listOf(partial))

        val resolved = repo.find("a1")!!
        assertEquals("real bio", resolved.bio)   // still the full entry
        assertEquals(25000, resolved.price)
    }

    @Test
    fun `cache adds a brand-new partial that has no full entry`() = runTest {
        val repo = FakeArtistsRepository()
        repo.cache(listOf(artist("b2", "Fresh", 50, 0, full = false)))
        assertEquals("Fresh", repo.find("B2")?.name)   // case-insensitive lookup
    }

    @Test
    fun `fake search filters, sorts by score, and paginates`() = runTest {
        val roster = (1..25).map { artist("id$it", "Act$it", score = it, price = it * 100, full = true) }
        val repo = FakeSearchRepository(roster)

        val page1 = repo.search(SearchFilters(sort = SearchSort.Bookability), SearchCursor.Start)
        assertEquals(20, page1.artists.size)
        assertEquals(25, page1.artists.first().score)          // score desc
        assertTrue(page1.nextCursor is SearchCursor.Keyset)

        val page2 = repo.search(SearchFilters(sort = SearchSort.Bookability), page1.nextCursor)
        assertEquals(5, page2.artists.size)
        assertEquals(SearchCursor.End, page2.nextCursor)
    }

    @Test
    fun `fake search honours a min-score filter`() = runTest {
        val roster = (1..10).map { artist("id$it", "Act$it", score = it * 10, price = 0, full = true) }
        val repo = FakeSearchRepository(roster)
        val page = repo.search(SearchFilters(minScore = 60), SearchCursor.Start)
        assertTrue(page.artists.all { it.score >= 60 })
    }

    @Test
    fun `fake saved-artists add and remove are idempotent`() = runTest {
        val repo = FakeSavedArtistsRepository()
        repo.add("X1")
        repo.add("x1")                       // same id, different case
        assertEquals(1, repo.list().size)
        repo.remove("X1")
        assertTrue(repo.list().isEmpty())
        repo.remove("nope")                  // no-op, no throw
    }

    @Test
    fun `fake reviews returns the seeded list or empty`() = runTest {
        val repo = FakeReviewsRepository(
            mapOf("a1" to listOf(Review(id = "r1", name = "Asha", org = "", rating = 5, body = "great"))),
        )
        assertEquals(1, repo.listForArtist("a1").size)
        assertTrue(repo.listForArtist("unknown").isEmpty())
        assertFalse(repo.listForArtist("a1").first().body.isEmpty())
    }
}
