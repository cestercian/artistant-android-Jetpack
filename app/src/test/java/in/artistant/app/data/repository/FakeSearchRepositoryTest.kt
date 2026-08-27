package `in`.artistant.app.data.repository

import `in`.artistant.app.data.model.SearchCursor
import `in`.artistant.app.data.model.SearchFilters
import `in`.artistant.app.data.model.SearchSort
import `in`.artistant.app.feature.discover.DiscoverViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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

    // ─────────────────────────────────────────────────────────────────────────
    // The two missing-RPC classifiers, side by side.
    //
    // [SupabaseSearchRepository.isMissingFunction] decides whether a pre-0073
    // server degrades through the retry-without-0073-dims path or throws;
    // [DiscoverViewModel.messageFor] decides what a failed Discover says. Both
    // are substring matches over the SAME PostgREST message, and their lists
    // overlap without being equal — so one table drives both, a row per
    // substring either keys on (each isolated, so no message trips two branches
    // of the same function) plus an error that is neither.
    //
    // Where they disagree TODAY, said out loud rather than left to be
    // discovered: "pgrst202" and "no function matches" reach the retry but not
    // the friendly copy, and a bare "search_artists" reads friendly without
    // reaching the retry. Costs a blunter sentence, never a broken screen — but
    // a row that moves is now a test failure, not a silent drift.
    // ─────────────────────────────────────────────────────────────────────────

    private data class ClassifierCase(
        val message: String,
        /** Expected [SupabaseSearchRepository.isMissingFunction]. */
        val retries: Boolean,
        /** Expected [DiscoverViewModel.messageFor]. */
        val copy: String,
    )

    @Test
    fun `isMissingFunction and messageFor stay pinned across every substring they key on`() {
        val friendly = "We couldn't load the roster right now. Try again in a moment."
        val generic = "Something went wrong loading the roster."
        val cases = listOf(
            ClassifierCase("Could not find the function public.some_rpc in the schema cache", true, friendly),
            ClassifierCase("PGRST202: schema cache reload required", true, generic),
            ClassifierCase("SQLSTATE 42883 encountered", true, friendly),
            ClassifierCase("relation \"artists_view\" does not exist", true, friendly),
            ClassifierCase("No function matches the given name and argument types", true, generic),
            // messageFor's own extra substring: naming the RPC is enough for the copy.
            ClassifierCase("search_artists timed out", false, friendly),
            // Neither classifier: the roster failed for an ordinary reason.
            ClassifierCase("socket closed", false, generic),
        )

        for (case in cases) {
            val error = IllegalStateException(case.message)
            assertEquals(
                "isMissingFunction(\"${case.message}\")",
                case.retries,
                SupabaseSearchRepository.isMissingFunction(error),
            )
            assertEquals(
                "messageFor(\"${case.message}\")",
                case.copy,
                DiscoverViewModel.messageFor(error),
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // artistsRepository caching — the real search feeds every returned partial
    // into the shared by-id cache (SearchRepository.kt's artistsRepository
    // .cache(artists)); a fake that skipped it made a tapped search result
    // unresolvable through find()/ensureFull() alone.
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `search caches every returned artist into the configured ArtistsRepository`() = runTest {
        val roster = listOf(
            FakeArtistsRepository.sample(id = "1", name = "Nova Beats"),
            FakeArtistsRepository.sample(id = "2", name = "Comedy Night"),
        )
        val artistsCache = FakeArtistsRepository()
        val repo = FakeSearchRepository(roster, artistsRepository = artistsCache)

        repo.search(SearchFilters(), SearchCursor.Start)

        assertEquals("Nova Beats", artistsCache.find("1")?.name)
        assertEquals("Comedy Night", artistsCache.find("2")?.name)
    }
}
