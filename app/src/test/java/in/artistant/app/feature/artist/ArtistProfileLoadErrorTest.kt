package `in`.artistant.app.feature.artist

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What the artist profile admits when hydration didn't produce a full artist.
 *
 * The regression: `ArtistsRepository.ensureFull` returns null for BOTH "no such
 * artist" and "the read threw", and the screen treated that null as nothing to
 * report the moment a cached artist existed. One always does exist for a client
 * arriving from Discover or Search — both `cache()` the tile projection first —
 * and the tile carries no packages, bio, samples or reviews. So a dropped request
 * left a fully-drawn profile asserting "Pricing on request", an empty About and
 * "No reviews yet.", with no banner and no retry: the Retry on this screen lives
 * only in the nothing-loaded branch.
 *
 * The ViewModel cannot be constructed in a JVM test (`SavedStore` needs a
 * DataStore-backed `AppPreferences`, i.e. a Context), so the decision it feeds
 * `loadError` from is pinned here.
 */
class ArtistProfileLoadErrorTest {

    /** The bug, in one row: a failed read must speak even with a tile in hand. */
    @Test
    fun aFailedFetchIsAnError_evenWhenACachedTileIsAlreadyOnScreen() {
        assertEquals(
            PROFILE_LOAD_FAILED,
            artistProfileLoadError(fetchFailed = true, hasCachedArtist = true),
        )
    }

    @Test
    fun aFailedFetchWithNothingCached_saysTheReadFailed_notThatTheArtistIsMissing() {
        assertEquals(
            PROFILE_LOAD_FAILED,
            artistProfileLoadError(fetchFailed = true, hasCachedArtist = false),
        )
    }

    @Test
    fun anEmptyServerAnswerWithNothingCached_isTheOnlyNotFound() {
        assertEquals(
            ARTIST_NOT_FOUND,
            artistProfileLoadError(fetchFailed = false, hasCachedArtist = false),
        )
    }

    /**
     * Server answered, row invisible (unpublished / deleted / RLS), stale tile on
     * screen: still the load-failed copy. "Artist not found." printed beside the
     * artist's own name reads as a contradiction rather than a warning, and from
     * this client's seat a row it cannot read is a row it cannot show.
     */
    @Test
    fun anEmptyServerAnswerOverAStaleTile_stillAdmitsTheProfileIsIncomplete() {
        assertEquals(
            PROFILE_LOAD_FAILED,
            artistProfileLoadError(fetchFailed = false, hasCachedArtist = true),
        )
    }
}
