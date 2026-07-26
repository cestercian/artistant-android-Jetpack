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
        assertTrue(page1.nextCursor is SearchCursor.Offset)
        val page2 = repo.search(SearchFilters(sort = SearchSort.Bookability), page1.nextCursor)
        assertEquals(5, page2.artists.size)
        assertEquals(SearchCursor.End, page2.nextCursor)
    }
}
