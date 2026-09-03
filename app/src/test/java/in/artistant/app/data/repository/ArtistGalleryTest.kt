package `in`.artistant.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The cover/gallery split, which is one decision two surfaces depend on: the
 * hero draws the cover, the About strip draws everything else, and the one thing
 * neither may do is show the same photograph twice on one page.
 *
 * The URL resolver is injected so these run without a configured Supabase
 * project — `coverUrl` reads `BuildConfig.SUPABASE_URL` and answers null for the
 * blank value a test JVM carries, which would make every assertion below pass
 * for the wrong reason.
 */
class ArtistGalleryTest {

    private val resolve: (String) -> String? = { path -> "https://cdn.test/$path" }

    private fun photo(
        id: String,
        position: Int,
        aspect: String = "square",
        kind: String = "photo",
        path: String = "$ARTIST/photo/$id.jpg",
    ) = DbArtistPhoto(
        id = id,
        artistId = ARTIST,
        kind = kind,
        aspect = aspect,
        storagePath = path,
        position = position,
    )

    private fun split(rows: List<DbArtistPhoto>) =
        SupabaseArtistsRepository.artistPhotos(rows, resolve)

    @Test
    fun positionZeroIsTheCoverAndTheRestAreTheGallery() {
        val result = split(
            listOf(photo("p0", 0), photo("p1", 1), photo("p2", 2)),
        )

        assertEquals("https://cdn.test/$ARTIST/photo/p0.jpg", result.coverUrl)
        assertEquals(listOf("p1", "p2"), result.gallery.map { it.id })
    }

    /** The cover is drawn by the hero; drawing it again under the bio is a bug. */
    @Test
    fun theCoverIsNeverAlsoInTheStrip() {
        val result = split(listOf(photo("p0", 0), photo("p1", 1)))

        assertTrue(result.gallery.none { it.url == result.coverUrl })
    }

    /**
     * The reason the split is "first in position order" rather than iOS's exact
     * `position == 0`.
     *
     * Positions go sparse the moment an artist deletes a photo — the press-kit
     * editor deletes rows and does not renumber the survivors — and a rule keyed
     * on the literal 0 then finds no cover at all while putting EVERY remaining
     * photo, including the one the hero is showing, into the strip below it.
     * Cutting both halves out of one sorted list makes that impossible to
     * express.
     */
    @Test
    fun aDeletedFirstPhotoStillLeavesACoverAndNoDuplicate() {
        val result = split(listOf(photo("p1", 1), photo("p2", 2), photo("p3", 3)))

        assertEquals("https://cdn.test/$ARTIST/photo/p1.jpg", result.coverUrl)
        assertEquals(listOf("p2", "p3"), result.gallery.map { it.id })
    }

    /** The artist's order is the strip's order, whatever order the rows arrive in. */
    @Test
    fun theStripIsInPositionOrder() {
        val result = split(
            listOf(photo("p3", 3), photo("p0", 0), photo("p2", 2), photo("p1", 1)),
        )

        assertEquals("https://cdn.test/$ARTIST/photo/p0.jpg", result.coverUrl)
        assertEquals(listOf("p1", "p2", "p3"), result.gallery.map { it.id })
    }

    /**
     * `kind` is filtered server-side as well, but the rule lives here: an
     * artist's showreel frame is not one of their photographs, and a caller that
     * forgets the filter must not get it rendered as one.
     */
    @Test
    fun aVideoIsNeitherCoverNorGallery() {
        val result = split(
            listOf(
                photo("v0", 0, kind = "video", path = "$ARTIST/video/v0.mp4"),
                photo("p1", 1),
                photo("p2", 2),
            ),
        )

        assertEquals("https://cdn.test/$ARTIST/photo/p1.jpg", result.coverUrl)
        assertEquals(listOf("p2"), result.gallery.map { it.id })
    }

    @Test
    fun aVideoOnlyArtistHasNoCoverAndNoStrip() {
        val result = split(
            listOf(photo("v0", 0, kind = "video", path = "$ARTIST/video/v0.mp4")),
        )

        assertNull(result.coverUrl)
        assertTrue(result.gallery.isEmpty())
    }

    @Test
    fun aSingleCoverLeavesAnEmptyStrip() {
        val result = split(listOf(photo("p0", 0)))

        assertEquals("https://cdn.test/$ARTIST/photo/p0.jpg", result.coverUrl)
        assertTrue(result.gallery.isEmpty())
    }

    @Test
    fun noPhotosIsNotAnError() {
        val result = split(emptyList())

        assertNull(result.coverUrl)
        assertTrue(result.gallery.isEmpty())
    }

    /**
     * A tile with no address is a grey box that never fills in, so a row whose
     * path cannot be resolved is dropped rather than carried with a null URL the
     * strip would have to defend against.
     */
    @Test
    fun aPhotoWithNoResolvableUrlIsDropped() {
        val result = SupabaseArtistsRepository.artistPhotos(
            listOf(photo("p0", 0), photo("p1", 1), photo("p2", 2)),
        ) { path -> if (path.endsWith("p1.jpg")) null else "https://cdn.test/$path" }

        assertEquals(listOf("p2"), result.gallery.map { it.id })
    }

    // ── Aspect ──────────────────────────────────────────────────────────────

    /** The strip sizes each tile from this, which is the point of the column. */
    @Test
    fun theStoredAspectReachesTheStrip() {
        val result = split(
            listOf(
                photo("p0", 0),
                photo("p1", 1, aspect = "portrait"),
                photo("p2", 2, aspect = "landscape"),
                photo("p3", 3, aspect = "square"),
            ),
        )

        assertEquals(
            listOf(
                ArtistMediaAspect.portrait,
                ArtistMediaAspect.landscape,
                ArtistMediaAspect.square,
            ),
            result.gallery.map { it.aspect },
        )
    }

    /**
     * An aspect this build has never heard of — a value another client wrote, or
     * one a later migration adds — draws a square tile. It does not throw, and
     * this DTO backs the profile's only read: a decode failure here is an artist
     * page that refuses to open over a photo's shape.
     */
    @Test
    fun anUnknownAspectFallsBackToSquare() {
        val result = split(listOf(photo("p0", 0), photo("p1", 1, aspect = "panorama")))

        assertEquals(listOf(ArtistMediaAspect.square), result.gallery.map { it.aspect })
    }

    @Test
    fun galleryIdsAreLowercased() {
        val result = split(listOf(photo("p0", 0), photo("A1B2", 1)))

        assertEquals(listOf("a1b2"), result.gallery.map { it.id })
    }

    private companion object {
        const val ARTIST = "11111111-1111-1111-1111-111111111111"
    }
}
