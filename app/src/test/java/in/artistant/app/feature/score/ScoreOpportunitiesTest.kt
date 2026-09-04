package `in`.artistant.app.feature.score

import androidx.compose.ui.graphics.Color
import `in`.artistant.app.data.model.Artist
import `in`.artistant.app.data.model.ArtistPackage
import `in`.artistant.app.data.model.GalleryPhoto
import `in`.artistant.app.data.model.Sample
import `in`.artistant.app.data.repository.ArtistMediaAspect
import `in`.artistant.app.data.repository.ScoreBreakdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Screen 50's "Small wins".
 *
 * The design draws a "+N" pill on every row. We draw it only where the number is
 * real — points still unearned on a published factor — because a plausible
 * figure attached to "add a photo" is the marketplace inventing a score effect
 * that no `metric_*` column backs. These tests are what stops that pill drifting
 * onto rows that have not earned it.
 */
class ScoreOpportunitiesTest {

    private fun breakdown(
        showUp: Int = 100,
        reviews: Int = 100,
        reply: Int = 100,
        cancellations: Int = 0,
        social: Int = 100,
        gigs: Int = 40,
    ) = ScoreBreakdown(
        score = 90,
        showUpRate = showUp,
        reviewScore = reviews,
        replySpeed = reply,
        cancellationRate = cancellations,
        socialProof = social,
        totalGigs = gigs,
    )

    private fun artist(
        samples: List<Sample> = listOf(Sample(id = "s", title = "Demo", duration = "3:00")),
        gallery: List<GalleryPhoto> = listOf(
            GalleryPhoto(id = "g", url = "u", aspect = ArtistMediaAspect.square),
        ),
        packages: List<ArtistPackage> = listOf(
            ArtistPackage(id = "p", name = "Trio", duration = "60 min", price = 26000, includes = emptyList()),
        ),
        bio: String = "Warm four-part harmonies.",
        tech: List<String> = listOf("PA"),
        spotify: String? = "https://open.spotify.com/artist/x",
    ) = Artist(
        id = "a1",
        name = "The Tilt Collective",
        handle = "tilt",
        category = "Band",
        genre = "Indie folk",
        city = "Bengaluru",
        price = 26000,
        duration = "60 min",
        score = 90,
        gradient = listOf(Color.Black, Color.White),
        bio = bio,
        packages = packages,
        tech = tech,
        samples = samples,
        spotifyArtistUrl = spotify,
        gallery = gallery,
    )

    @Test
    fun `a complete artist at full marks is offered nothing`() {
        // "Nothing outstanding" is a real answer, and better than padding the
        // list with advice that does not apply.
        assertTrue(ScoreOpportunities.of(breakdown(), artist()).isEmpty())
    }

    @Test
    fun `only score-moving rows carry a points pill`() {
        val wins = ScoreOpportunities.of(
            breakdown(reply = 50, social = 0),
            artist(samples = emptyList(), tech = emptyList()),
        )
        val scoring = wins.filter { it.points != null }
        val profile = wins.filter { it.points == null }

        assertTrue("some rows must move the score", scoring.isNotEmpty())
        assertTrue("some rows must be profile-only", profile.isNotEmpty())
        // Every pill is the real remaining points on a published factor.
        assertTrue(scoring.all { it.points!! in 1..ScoreFactors.SHOW_UP_WEIGHT })
    }

    @Test
    fun `the points offered are exactly the points left on that factor`() {
        val reply = ScoreOpportunities.of(breakdown(reply = 50), artist())
            .single { it.title == "Reply faster" }
        // 50 of 100 on a 20-point factor leaves 10.
        assertEquals(10, reply.points)
    }

    @Test
    fun `show-up and cancellation history are never offered as advice`() {
        // Both have points left here, and neither is something the artist can
        // act on today — "don't cancel" is the lecture the design forbids.
        val wins = ScoreOpportunities.of(
            breakdown(showUp = 40, cancellations = 60),
            artist(),
        )
        assertTrue(wins.none { it.title.contains("cancel", ignoreCase = true) })
        assertTrue(wins.none { it.title.contains("on time", ignoreCase = true) })
    }

    @Test
    fun `a missing Spotify link changes the social win from refresh to connect`() {
        val without = ScoreOpportunities.of(breakdown(social = 0), artist(spotify = null))
            .single { it.editor == ScoreEditor.Wizard }
        val with = ScoreOpportunities.of(breakdown(social = 0), artist(spotify = "https://x"))
            .single { it.editor == ScoreEditor.Wizard }

        assertEquals("Connect Spotify", without.title)
        assertEquals("Refresh your socials", with.title)
    }

    @Test
    fun `profile gaps are offered only when the record actually has them`() {
        val complete = ScoreOpportunities.of(breakdown(), artist())
        assertTrue(complete.none { it.title.contains("sample", ignoreCase = true) })

        val bare = ScoreOpportunities.of(
            breakdown(),
            artist(samples = emptyList(), gallery = emptyList(), packages = emptyList(), bio = "", tech = emptyList()),
        )
        assertEquals(
            listOf(
                "Add an audio sample",
                "Add photos from a real show",
                "Publish a package",
                "Write your bio",
                "Fill your tech rider",
            ),
            bare.map { it.title },
        )
        assertTrue("profile rows carry no invented number", bare.all { it.points == null })
    }

    @Test
    fun `every row opens something, including the two that edit no field`() {
        // Screen 50's note is that each win opens the thing it edits. The type
        // enforces it now — `editor` is non-null — so what is worth pinning is
        // that the two score-moving rows with no editable field still send the
        // reader somewhere useful rather than being quietly dropped.
        val wins = ScoreOpportunities.of(
            breakdown(reply = 50, reviews = 60, social = 0),
            artist(samples = emptyList(), gallery = emptyList(), packages = emptyList(), bio = "", tech = emptyList()),
        )
        assertTrue("advice must never dead-end", wins.isNotEmpty())

        assertEquals(
            ScoreEditor.Messages,
            wins.single { it.title == "Reply faster" }.editor,
        )
        assertEquals(
            ScoreEditor.Gigs,
            wins.single { it.title == "Ask your hosts for a review" }.editor,
        )
        // The profile rows split between the two editors that own the fields:
        // the press kit for the listing, the wizard for the tech rider.
        assertEquals(
            setOf(ScoreEditor.PressKit, ScoreEditor.Wizard),
            wins.filter { it.points == null }.map { it.editor }.toSet(),
        )
    }

    @Test
    fun `an unreadable profile still offers the score-moving half and nothing else`() {
        // `artist` null is "we couldn't read your own row". The score half is
        // still knowable; the profile half is not, so it is simply not offered.
        val wins = ScoreOpportunities.of(breakdown(reply = 50, reviews = 60), artist = null)
        assertTrue(wins.isNotEmpty())
        assertTrue(wins.all { it.points != null })
        assertNull(wins.firstOrNull { it.title.contains("bio", ignoreCase = true) })
    }
}
