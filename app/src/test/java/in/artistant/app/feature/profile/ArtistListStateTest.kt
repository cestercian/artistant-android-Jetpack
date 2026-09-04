package `in`.artistant.app.feature.profile

import `in`.artistant.app.data.model.Artist
import `in`.artistant.app.data.repository.FakeArtistsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The saved / bookings / completed list's own decisions (screens 32 and 112).
 *
 * All pure: the category rail and the rows it hides are derived from the loaded
 * rows, and the copy is per kind. The interesting cases are the ones where a
 * filter and a reload disagree — a category chip that survives a refresh into a
 * list that no longer contains it hides every row with nothing lit to explain it.
 */
class ArtistListStateTest {

    private fun row(id: String, artist: Artist?) = ArtistListRow(
        id = id,
        artistId = artist?.id,
        bookingId = null,
        artist = artist,
        fallbackTitle = artist?.name ?: "Artist",
        pills = emptyList(),
    )

    private fun state(vararg artists: Artist) = ArtistListUiState(
        kind = ArtistListKind.Saved,
        rows = artists.map { row(it.id, it) },
        isLoading = false,
    )

    // ── the category rail ────────────────────────────────────────────────────

    /**
     * Every chip comes from a category some row actually has. A chip for an act
     * type nobody in your list plays can only ever empty the screen.
     */
    @Test
    fun `the rail lists each act type present, once, in order`() {
        val s = state(
            FakeArtistsRepository.sample(id = "a", category = "DJ"),
            FakeArtistsRepository.sample(id = "b", category = "Band"),
            FakeArtistsRepository.sample(id = "c", category = "DJ"),
        )
        assertEquals(listOf("Band", "DJ"), s.categories)
    }

    @Test
    fun `a row whose artist never hydrated contributes no chip`() {
        val s = ArtistListUiState(
            rows = listOf(row("a", null), row("b", FakeArtistsRepository.sample(category = "DJ"))),
            isLoading = false,
        )
        assertEquals(listOf("DJ"), s.categories)
    }

    @Test
    fun `a blank category is not an act type`() {
        val s = state(
            FakeArtistsRepository.sample(id = "a", category = "  "),
            FakeArtistsRepository.sample(id = "b", category = "DJ"),
        )
        assertEquals(listOf("DJ"), s.categories)
    }

    // ── what the list actually draws ─────────────────────────────────────────

    @Test
    fun `no category selected shows every row`() {
        val s = state(
            FakeArtistsRepository.sample(id = "a", category = "DJ"),
            FakeArtistsRepository.sample(id = "b", category = "Band"),
        )
        assertEquals(s.rows, s.visibleRows)
    }

    @Test
    fun `a selected category keeps only its own rows`() {
        val s = state(
            FakeArtistsRepository.sample(id = "a", category = "DJ"),
            FakeArtistsRepository.sample(id = "b", category = "Band"),
        ).copy(selectedCategory = "DJ")
        assertEquals(listOf("a"), s.visibleRows.map { it.id })
    }

    /** The rail's labels and the rows' values come from the same column. */
    @Test
    fun `every chip in the rail selects at least one row`() {
        val s = state(
            FakeArtistsRepository.sample(id = "a", category = "DJ"),
            FakeArtistsRepository.sample(id = "b", category = "Band"),
        )
        s.categories.forEach { category ->
            assertTrue(
                "\"$category\" is a chip that hides everything",
                s.copy(selectedCategory = category).visibleRows.isNotEmpty(),
            )
        }
    }

    // ── copy ─────────────────────────────────────────────────────────────────

    /**
     * A booking is not an act and a past show is neither, so the subtitle is
     * named after what the rows are — and singular where it has to be.
     */
    @Test
    fun `the count line names the rows and knows its singular`() {
        assertEquals("1 act", ArtistListKind.Saved.countLabel(1))
        assertEquals("12 acts", ArtistListKind.Saved.countLabel(12))
        assertEquals("1 booking", ArtistListKind.Bookings.countLabel(1))
        assertEquals("3 bookings", ArtistListKind.Bookings.countLabel(3))
        assertEquals("1 past show", ArtistListKind.Completed.countLabel(1))
        assertEquals("5 past shows", ArtistListKind.Completed.countLabel(5))
    }

    /** The header says the long name; the switcher chip says the short one. */
    @Test
    fun `saved is titled in full and chipped in short`() {
        assertEquals("Saved artists", ArtistListKind.Saved.title)
        assertEquals("Saved", ArtistListKind.Saved.chipLabel)
    }

    /**
     * The design's note for screen 112: the badge is the hook, and it is worth
     * stating before there is anything in the list.
     */
    @Test
    fun `the empty saved copy states what saving buys you`() {
        assertTrue(ArtistListKind.Saved.emptyBody.contains("free up your date"))
    }

    /** Failure and emptiness are different screens, and say different things. */
    @Test
    fun `every kind has a failure headline distinct from its empty one`() {
        ArtistListKind.entries.forEach { kind ->
            assertTrue(kind.failedTitle.startsWith("Couldn't load"))
            assertTrue(kind.failedTitle != kind.emptyTitle)
        }
    }

    @Test
    fun `an unknown nav argument lands on Saved rather than throwing`() {
        assertEquals(ArtistListKind.Saved, ArtistListKind.fromRaw("nonsense"))
        assertEquals(ArtistListKind.Completed, ArtistListKind.fromRaw("COMPLETED"))
    }
}
