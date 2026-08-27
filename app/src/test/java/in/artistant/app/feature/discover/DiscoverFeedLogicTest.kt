package `in`.artistant.app.feature.discover

import `in`.artistant.app.data.model.SearchCursor
import `in`.artistant.app.data.model.SearchFilters
import `in`.artistant.app.data.repository.FakeArtistsRepository
import `in`.artistant.app.data.repository.FakeSearchRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Discover feed logic tests: [DiscoverViewModel.applyRails] for the six-rail
 * slice `loadRails()` applies, [DiscoverViewModel.messageFor] for the
 * failure-copy mapping, and a paging smoke test against FakeSearchRepository
 * (no Android ViewModel runtime needed for any of it).
 */
class DiscoverFeedLogicTest {

    /**
     * Drives [DiscoverViewModel.applyRails] itself — the function `loadRails()`
     * calls once all four queries land — rather than reimplementing the slice
     * in-test. Each source page uses a disjoint id prefix, so a swapped source
     * (`topBangalore` fed from `fresh` instead of `city`) fails this test, not
     * just a wrong rail length.
     */
    @Test
    fun `applyRails maps the four query pages onto their six named rails`() {
        val top = (1..12).map { FakeArtistsRepository.sample(id = "top$it", name = "Top $it") }
        val city = (1..12).map { FakeArtistsRepository.sample(id = "city$it", name = "City $it") }
        val fresh = (1..12).map { FakeArtistsRepository.sample(id = "fresh$it", name = "Fresh $it") }
        val comedy = (1..12).map { FakeArtistsRepository.sample(id = "comedy$it", name = "Comedy $it") }

        val state = DiscoverViewModel.applyRails(DiscoverUiState(), top, city, fresh, comedy)

        assertEquals(top.take(5), state.hero)
        assertEquals(top.take(8), state.featured)
        assertEquals(top.take(10), state.topIndia)
        assertEquals(city.take(10), state.topBangalore)
        assertEquals(fresh.take(10), state.newOnArtistant)
        assertEquals(comedy.take(10), state.comedy)
        assertFalse(state.isLoading)
    }

    @Test
    fun `messageFor maps missing RPC to friendly copy`() {
        val msg = DiscoverViewModel.messageFor(
            IllegalStateException("Could not find the function search_artists"),
        )
        assertTrue(msg.contains("couldn't load", ignoreCase = true))
    }

    /**
     * One message per branch, each carrying only its own substring.
     *
     * The case above says "Could not find the function search_artists", which
     * trips TWO of the four at once — so it would still pass if either had been
     * deleted, and the `42883` and `does not exist` branches were asserted by
     * nothing at all. A Postgres 42883 is what PostgREST reports when the RPC is
     * absent, which is the whole reason the branch exists.
     */
    @Test
    fun `messageFor recognises every missing-RPC dialect on its own`() {
        val perBranch = listOf(
            "could not find the function",
            "search_artists",
            "42883",
            "does not exist",
        )
        perBranch.forEach { phrase ->
            assertTrue(
                "\"$phrase\" alone must read as a missing RPC",
                DiscoverViewModel.messageFor(IllegalStateException(phrase))
                    .contains("couldn't load", ignoreCase = true),
            )
        }
    }

    /** Anything that is not a missing RPC gets the generic line, asserted by its copy. */
    @Test
    fun `messageFor falls back to the generic line for an unrelated failure`() {
        assertEquals(
            "Something went wrong loading the roster.",
            DiscoverViewModel.messageFor(IllegalStateException("socket closed")),
        )
    }

    /**
     * The failure line lands on a plain `EmptyState` whenever there are no rails
     * to show, and an `EmptyState` has nothing scrollable in it — so
     * `PullToRefreshBox`'s nested-scroll connection never sees the gesture. The
     * copy must not send the user after it; both surfaces carry a button.
     */
    @Test
    fun `messageFor never instructs a pull-to-refresh the empty screen cannot receive`() {
        val errors = listOf(
            IllegalStateException("Could not find the function search_artists"),
            IllegalStateException("socket closed"),
        )
        errors.forEach { e ->
            assertFalse(
                "failure copy points at the Retry button, not at a dead gesture",
                DiscoverViewModel.messageFor(e).contains("pull to refresh", ignoreCase = true),
            )
        }
    }

    @Test
    fun `empty roster returns empty page`() = runTest {
        val repo = FakeSearchRepository(emptyList())
        val page = repo.search(SearchFilters(), SearchCursor.Start)
        assertTrue(page.artists.isEmpty())
    }
}
