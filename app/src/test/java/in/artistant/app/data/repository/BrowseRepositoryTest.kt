package `in`.artistant.app.data.repository

import `in`.artistant.app.data.model.SearchCursor
import `in`.artistant.app.data.model.SearchFilters
import `in`.artistant.app.data.model.SearchSort
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeArtistsRepositoryTest {

    @Test
    fun `cache never downgrades a hydrated artist`() = runTest {
        val full = FakeArtistsRepository.sample(id = "a1", name = "Full Name").copy(
            bio = "full bio",
        )
        val repo = FakeArtistsRepository(seed = listOf(full))
        repo.cache(
            listOf(
                FakeArtistsRepository.sample(id = "a1", name = "Partial").copy(bio = ""),
            ),
        )
        assertEquals("Full Name", repo.find("a1")?.name)
        assertEquals("full bio", repo.find("a1")?.bio)
    }

    @Test
    fun `ensureFull returns null on failure`() = runTest {
        val repo = FakeArtistsRepository(seed = listOf(FakeArtistsRepository.sample()))
        repo.failFetch = true
        assertNull(repo.ensureFull("a1"))
    }
}

class FakeSearchRepositoryTest {

    @Test
    fun `filters by city and category and sorts by score`() = runTest {
        val roster = listOf(
            FakeArtistsRepository.sample(id = "1", name = "A", city = "Bangalore", category = "DJ", score = 70),
            FakeArtistsRepository.sample(id = "2", name = "B", city = "Bangalore", category = "Stand-up", score = 90),
            FakeArtistsRepository.sample(id = "3", name = "C", city = "Mumbai", category = "DJ", score = 95),
        )
        val repo = FakeSearchRepository(roster)
        val page = repo.search(
            SearchFilters(city = "Bangalore", sort = SearchSort.Bookability),
            SearchCursor.Start,
        )
        assertEquals(listOf("2", "1"), page.artists.map { it.id })
    }

    @Test
    fun `text query filters haystack`() = runTest {
        val roster = listOf(
            FakeArtistsRepository.sample(id = "1", name = "Nova Beats"),
            FakeArtistsRepository.sample(id = "2", name = "Comedy Night"),
        )
        val repo = FakeSearchRepository(roster)
        val page = repo.search(SearchFilters(text = "nova"), SearchCursor.Start)
        assertEquals(listOf("1"), page.artists.map { it.id })
    }

    /**
     * The fake has to page the way the server pages, or a keyset regression is
     * uncatchable through the seam: the default (no-query) bookability sort
     * keysets on `(score, id)`, everything else offsets.
     */
    @Test
    fun `default sort keysets, and the keyset is honoured on the next page`() = runTest {
        val roster = (1..25).map {
            FakeArtistsRepository.sample(id = "a%02d".format(it), name = "A$it", score = 100 - it)
        }
        val repo = FakeSearchRepository(roster)

        val page1 = repo.search(SearchFilters(sort = SearchSort.Bookability), SearchCursor.Start)
        assertEquals(20, page1.artists.size)
        val cursor = page1.nextCursor
        assertTrue(cursor is SearchCursor.Keyset)
        assertEquals(80, (cursor as SearchCursor.Keyset).afterScore)
        assertEquals("a20", cursor.afterId)

        val page2 = repo.search(SearchFilters(sort = SearchSort.Bookability), cursor)
        assertEquals(5, page2.artists.size)
        assertEquals(SearchCursor.End, page2.nextCursor)
        // No overlap: the second page starts strictly after the cursor row.
        assertTrue(page1.artists.map { it.id }.intersect(page2.artists.map { it.id }.toSet()).isEmpty())
        assertEquals("a21", page2.artists.first().id)
    }

    @Test
    fun `ties break on id descending, like the server order`() = runTest {
        val roster = listOf(
            FakeArtistsRepository.sample(id = "a1", score = 90),
            FakeArtistsRepository.sample(id = "a3", score = 90),
            FakeArtistsRepository.sample(id = "a2", score = 90),
        )
        val repo = FakeSearchRepository(roster)
        val page = repo.search(SearchFilters(sort = SearchSort.Bookability), SearchCursor.Start)
        assertEquals(listOf("a3", "a2", "a1"), page.artists.map { it.id })

        // Same score as the cursor row, so only the id can separate them.
        val after = repo.search(
            SearchFilters(sort = SearchSort.Bookability),
            SearchCursor.Keyset(afterScore = 90, afterId = "a3"),
        )
        assertEquals(listOf("a2", "a1"), after.artists.map { it.id })
    }

    @Test
    fun `the price sort still pages by offset`() = runTest {
        val roster = (1..25).map {
            FakeArtistsRepository.sample(id = "a$it", name = "A$it", price = 10_000 + it)
        }
        val repo = FakeSearchRepository(roster)
        val page1 = repo.search(SearchFilters(sort = SearchSort.Price), SearchCursor.Start)
        assertEquals(SearchCursor.Offset(20), page1.nextCursor)
        val page2 = repo.search(SearchFilters(sort = SearchSort.Price), page1.nextCursor)
        assertEquals(5, page2.artists.size)
        assertEquals(SearchCursor.End, page2.nextCursor)
    }

    @Test
    fun `nextCursor ends on short page`() {
        val rows = listOf(
            SearchArtistRow(
                id = "a",
                stageName = "A",
                handle = "a",
                category = "DJ",
                baseCity = "Bangalore",
                score = 80,
            ),
        )
        assertEquals(
            SearchCursor.End,
            SupabaseSearchRepository.nextCursor(rows, limit = 20, hasQuery = false, sort = SearchSort.Bookability, offset = 0),
        )
    }

    @Test
    fun `nextCursor keysets on full bookability page`() {
        val rows = (1..20).map {
            SearchArtistRow(
                id = "id$it",
                stageName = "A$it",
                handle = "a$it",
                category = "DJ",
                baseCity = "Bangalore",
                score = 100 - it,
            )
        }
        val next = SupabaseSearchRepository.nextCursor(
            rows, limit = 20, hasQuery = false, sort = SearchSort.Bookability, offset = 0,
        )
        assertTrue(next is SearchCursor.Keyset)
        val keyset = next as SearchCursor.Keyset
        assertEquals(80, keyset.afterScore)
        assertEquals("id20", keyset.afterId)
    }
}
