package `in`.artistant.app.feature.discover

import `in`.artistant.app.data.model.SearchCursor
import `in`.artistant.app.data.model.SearchFilters
import `in`.artistant.app.data.repository.FakeArtistsRepository
import `in`.artistant.app.data.repository.FakeSearchRepository
import `in`.artistant.app.data.repository.SearchRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Discover feed logic tests — exercise the same rail mapping DiscoverViewModel
 * uses, against FakeSearchRepository (no Android ViewModel runtime needed).
 */
class DiscoverFeedLogicTest {

    @Test
    fun `rails slice top page into hero featured and india`() = runTest {
        val roster = (1..12).map {
            FakeArtistsRepository.sample(
                id = "a$it",
                name = "Artist $it",
                score = 100 - it,
            )
        }
        val search: SearchRepository = FakeSearchRepository(roster)
        val top = search.search(SearchFilters(), SearchCursor.Start)
        val hero = top.artists.take(5)
        val featured = top.artists.take(8)
        val india = top.artists.take(10)
        assertEquals(5, hero.size)
        assertEquals(8, featured.size)
        assertEquals(10, india.size)
        assertEquals("a1", hero.first().id)
    }

    @Test
    fun `messageFor maps missing RPC to friendly copy`() {
        val msg = DiscoverViewModel.messageFor(
            IllegalStateException("Could not find the function search_artists"),
        )
        assertTrue(msg.contains("couldn't load", ignoreCase = true))
    }

    @Test
    fun `empty roster returns empty page`() = runTest {
        val repo = FakeSearchRepository(emptyList())
        val page = repo.search(SearchFilters(), SearchCursor.Start)
        assertTrue(page.artists.isEmpty())
    }
}
