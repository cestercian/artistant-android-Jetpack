package `in`.artistant.app.feature.discover

import `in`.artistant.app.data.model.SearchCursor
import `in`.artistant.app.data.model.SearchFilters
import `in`.artistant.app.data.repository.FakeArtistsRepository
import `in`.artistant.app.data.repository.FakeSearchRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Discover feed logic tests: [DiscoverViewModel.applyRails] for the rail slice
 * `loadRails()` applies, [DiscoverViewModel.messageFor] for the failure-copy
 * mapping, and a paging smoke test against FakeSearchRepository (no Android
 * ViewModel runtime needed for any of it).
 */
class DiscoverFeedLogicTest {

    private val saturday = LocalDate.of(2026, 10, 10) // a Saturday

    private fun state() = DiscoverUiState(today = saturday)

    /**
     * An artist who has published [saturday] as a working day.
     *
     * The availability rail admits nobody else — see
     * `DiscoverHeroLogic.evidencesAvailability` — so a fixture destined for that
     * page has to carry its own evidence, exactly as a real row from
     * `search_artists` does.
     */
    private fun freeOnSaturday(id: String, name: String = id) =
        FakeArtistsRepository.sample(id = id, name = name).copy(daysAvailable = listOf("Sat"))

    /**
     * Drives [DiscoverViewModel.applyRails] itself — the function `loadRails()`
     * calls once the queries land — rather than reimplementing the slice in-test.
     * Each source page uses a disjoint id prefix, so a swapped source (the city
     * rail fed from `fresh`) fails this test, not just a wrong rail length.
     */
    @Test
    fun `applyRails maps each query page onto its own named rail`() {
        val top = (1..12).map { FakeArtistsRepository.sample(id = "top$it", name = "Top $it") }
        val free = (1..12).map { freeOnSaturday(id = "free$it", name = "Free $it") }
        val city = (1..12).map { FakeArtistsRepository.sample(id = "city$it", name = "City $it") }
        val fresh = (1..12).map { FakeArtistsRepository.sample(id = "fresh$it", name = "Fresh $it") }
        val comedy = (1..12).map { FakeArtistsRepository.sample(id = "comedy$it", name = "Comedy $it") }

        val after = DiscoverViewModel.applyRails(state(), null, top, free, city, fresh, comedy)

        assertEquals(top.first(), after.hero)
        assertEquals(listOf("available", "city", "new", "comedy"), after.rails.map { it.id })
        assertEquals(free.take(10), after.rails.first { it.id == "available" }.artists)
        assertEquals(city.take(10), after.rails.first { it.id == "city" }.artists)
        assertEquals(fresh.take(10), after.rails.first { it.id == "new" }.artists)
        assertEquals(comedy.take(10), after.rails.first { it.id == "comedy" }.artists)
        assertFalse(after.isLoading)
    }

    /**
     * A rail with nothing in it is dropped, not rendered as a title over a blank
     * strip. A young roster returns empty pages for the narrower queries all the
     * time, and a heading that promises a section then shows none of it reads as
     * a loading bug.
     */
    @Test
    fun `an empty page produces no rail at all`() {
        val top = listOf(FakeArtistsRepository.sample(id = "top1"))
        val after = DiscoverViewModel.applyRails(
            state(),
            issuedFor = null,
            top = top,
            available = emptyList(),
            city = emptyList(),
            fresh = top,
            comedy = emptyList(),
        )
        assertEquals(listOf("new"), after.rails.map { it.id })
    }

    /** No hero when the top page is empty — the card is not drawn over nothing. */
    @Test
    fun `no hero when the roster is empty`() {
        val after = DiscoverViewModel.applyRails(
            state(),
            issuedFor = null,
            top = emptyList(),
            available = emptyList(),
            city = emptyList(),
            fresh = emptyList(),
            comedy = emptyList(),
        )
        assertNull(after.hero)
        assertTrue(after.isEmpty)
    }

    /**
     * The availability rail re-checks the artist's own `days_available` on top of
     * the server's `p_date` filter, because `SupabaseSearchRepository` silently
     * retries a date-filtered search WITHOUT the 0073 dimensions when the RPC
     * signature is missing (and the Fake twins never apply the date at all).
     * Without this second gate that fallback captions an unfiltered page
     * "Available Sat night".
     *
     * An artist who has published NO week is dropped too: the rail's caption is a
     * claim, and silence is not evidence for it.
     */
    @Test
    fun `the availability rail keeps only artists whose own week names the day`() {
        val free = FakeArtistsRepository.sample(id = "free").copy(daysAvailable = listOf("Sat"))
        val busy = FakeArtistsRepository.sample(id = "busy").copy(daysAvailable = listOf("Mon"))
        val unknown = FakeArtistsRepository.sample(id = "unknown")

        val after = DiscoverViewModel.applyRails(
            state(),
            issuedFor = null,
            top = listOf(free),
            available = listOf(free, busy, unknown),
            city = emptyList(),
            fresh = emptyList(),
            comedy = emptyList(),
        )
        val rail = after.rails.first { it.id == "available" }
        assertEquals(listOf("free"), rail.artists.map { it.id })
    }

    /**
     * The whole page arriving without a single published week is what an
     * un-applied `p_date` looks like from here. There is then no evidence anyone
     * is free, so the rail is dropped rather than captioned over strangers.
     */
    @Test
    fun `no published week anywhere means no availability rail`() {
        val artists = (1..3).map { FakeArtistsRepository.sample(id = "a$it") }
        val after = DiscoverViewModel.applyRails(
            state(),
            issuedFor = null,
            top = artists,
            available = artists,
            city = emptyList(),
            fresh = emptyList(),
            comedy = emptyList(),
        )
        assertTrue(after.rails.none { it.id == "available" })
    }

    /**
     * Out-of-order completion.
     *
     * Five concurrent RPCs per load, and every category tap starts another load.
     * The loser landing last used to write ITS artists into the current state
     * while the titles and seeds — derived from `selectedCategory` — described
     * the category the user had actually picked: a feed of DJs under headings
     * about comedy, with no error and nothing to retry. The stamp is checked on
     * arrival, so a page issued for a category nobody is on is dropped whole.
     */
    @Test
    fun `a page issued for an abandoned category is dropped, not applied`() {
        val djs = listOf(FakeArtistsRepository.sample(id = "dj1", category = "DJ"))
        val comics = listOf(FakeArtistsRepository.sample(id = "comic1", category = "Stand-up"))

        // The user is on "Stand-up"; its load already landed.
        val current = DiscoverViewModel.applyRails(
            state().copy(selectedCategory = "Stand-up"),
            issuedFor = "Stand-up",
            top = comics,
            available = emptyList(),
            city = comics,
            fresh = comics,
            comedy = emptyList(),
        )
        // The older "DJ" request finishes afterwards.
        val after = DiscoverViewModel.applyRails(
            current,
            issuedFor = "DJ",
            top = djs,
            available = emptyList(),
            city = djs,
            fresh = djs,
            comedy = emptyList(),
        )

        assertEquals(current, after)
        assertEquals("comic1", after.hero?.id)
        assertTrue(after.rails.all { rail -> rail.artists.all { it.id.startsWith("comic") } })
    }

    /** The rail title names the day the query was actually scoped to. */
    @Test
    fun `the availability rail is titled with its own date`() {
        val artist = freeOnSaturday("a1")
        val after = DiscoverViewModel.applyRails(
            state(),
            issuedFor = null,
            top = listOf(artist),
            available = listOf(artist),
            city = emptyList(),
            fresh = emptyList(),
            comedy = emptyList(),
        )
        assertEquals("Available Sat night", after.rails.first { it.id == "available" }.title)
        assertEquals(saturday.toString(), after.rails.first { it.id == "available" }.seed.dateIso)
    }

    /**
     * Under a selected category a comedy rail either duplicates the chip or
     * contradicts it, so the query is skipped and the rail cannot appear. Every
     * other rail carries the category into its "See all".
     */
    @Test
    fun `a selected category scopes every rail seed and hides comedy`() {
        val artist = freeOnSaturday("a1")
        val after = DiscoverViewModel.applyRails(
            state().copy(selectedCategory = "DJ"),
            issuedFor = "DJ",
            top = listOf(artist),
            available = listOf(artist),
            city = listOf(artist),
            fresh = listOf(artist),
            comedy = emptyList(),
        )
        assertEquals(listOf("available", "city", "new"), after.rails.map { it.id })
        assertTrue(after.rails.all { it.seed.category == "DJ" })
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
