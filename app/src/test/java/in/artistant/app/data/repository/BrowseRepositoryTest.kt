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
