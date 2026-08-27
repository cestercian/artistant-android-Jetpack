package `in`.artistant.app.feature.search

import `in`.artistant.app.data.model.SearchCursor
import `in`.artistant.app.data.model.SearchFilters
import `in`.artistant.app.data.model.SearchSort
import `in`.artistant.app.data.repository.FakeArtistsRepository
import `in`.artistant.app.data.repository.FakeSearchRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchViewModelLogicTest {

    @Test
    fun `hasActiveQuery when city or categories set`() {
        val empty = SearchUiState()
        assertTrue(!empty.hasActiveQuery)
        assertTrue(SearchUiState(city = "Bangalore").hasActiveQuery)
        assertTrue(SearchUiState(categories = setOf("DJ")).hasActiveQuery)
        assertTrue(SearchUiState(query = "nova").hasActiveQuery)
    }

    @Test
    fun `paged search returns more on loadMore cursor`() = runTest {
        val roster = (1..25).map {
            FakeArtistsRepository.sample(id = "a$it", name = "A$it", score = 100 - it)
        }
        val repo = FakeSearchRepository(roster)
        val page1 = repo.search(SearchFilters(sort = SearchSort.Bookability), SearchCursor.Start)
        assertEquals(20, page1.artists.size)
        // The default sort keysets, like the server does — see FakeSearchRepositoryTest.
        assertTrue(page1.nextCursor is SearchCursor.Keyset)
        val page2 = repo.search(SearchFilters(sort = SearchSort.Bookability), page1.nextCursor)
        assertEquals(5, page2.artists.size)
        assertEquals(SearchCursor.End, page2.nextCursor)

        // Keyset RESUME, not just keyset emission: page2 must be the roster's
        // remaining tail — not a repeat of anything page1 already returned, and
        // not a gap either.
        val page1Ids = page1.artists.map { it.id }.toSet()
        val page2Ids = page2.artists.map { it.id }.toSet()
        assertTrue("page2 repeats a row page1 already returned", page1Ids.intersect(page2Ids).isEmpty())
        assertEquals(roster.map { it.id }.toSet(), page1Ids + page2Ids)
    }
}
