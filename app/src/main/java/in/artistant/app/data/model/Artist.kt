package `in`.artistant.app.data.model

import androidx.compose.ui.graphics.Color
import java.time.Instant

/**
 * Browse-side domain models — the read shapes Discover / Search / Artist-profile
 * render. Ports of iOS `Models/Artist.swift`. Repositories return these plain
 * data classes; the DTOs in `data/model/dto` decode Postgres and map INTO them.
 */

/** One bookable package (iOS `ArtistPackage`). */
data class ArtistPackage(
    val name: String,
    val duration: String,
    val price: Int,
    val includes: List<String>,
    val popular: Boolean = false,
)

/** A read-only audio sample row (iOS `Sample`). Playback/upload is M5. */
data class Sample(
    val title: String,
    val duration: String,
)

/**
 * A reader-side view of `public.reviews` (iOS `Review`). [id] is the server row
 * UUID so a `LazyColumn` key stays stable across refetches; [createdAt] is null
 * for preview/fake constructors and drives newest-first sort in the real path.
 */
data class Review(
    val id: String,
    val name: String,
    val org: String,
    val rating: Int,
    val body: String,
    val createdAt: Instant? = null,
)

/** Average star rating; 0 for an empty set (iOS `Array<Review>.averageRating`). */
fun List<Review>.averageRating(): Double =
    if (isEmpty()) 0.0 else sumOf { it.rating }.toDouble() / size

/**
 * A performer as shown across Discover/Search/profile. `gradient` is the resolved
 * 3-stop brand palette (from [ArtistGradient.palette]) used as the never-empty
 * fallback behind a tile/hero when [coverUrl] is null or still loading. Tile-level
 * projections (from server search) leave the profile-only fields empty/blank —
 * they're upgraded to a full profile on tap via `ArtistsRepository.fetchArtist`.
 */
data class Artist(
    val id: String,
    val name: String,
    val handle: String,
    val category: String,
    val genre: String,
    val city: String,
    val price: Int,
    val duration: String,
    val score: Int,
    val gradient: List<Color>,
    val bio: String,
    val followers: String,
    val streams: String,
    val response: String,
    val onTime: Int,
    val gigs: Int,
    val rating: Double,
    val packages: List<ArtistPackage>,
    val tech: List<String>,
    val samples: List<Sample>,
    val reviews: List<Review>,
    // Social identifiers — null when the artist hasn't linked that platform.
    val spotifyArtistUrl: String? = null,
    val instagramHandle: String? = null,
    val youtubeChannelUrl: String? = null,
    // Wizard-supplied weekday availability + preferred start times. Empty = "no
    // preference set" → BookingView treats every day/generic slots as open.
    val daysAvailable: List<String> = emptyList(),
    val timeSlots: List<String> = emptyList(),
    // Resolved position-0 cover photo URL; null → the gradient fallback.
    val coverUrl: String? = null,
) {
    // Identity is by id only (iOS `==`/`hash` override) so cache-swaps of a
    // partial→full profile for the same id compare equal.
    override fun equals(other: Any?): Boolean = other is Artist && other.id == id
    override fun hashCode(): Int = id.hashCode()
}

/**
 * The six brand cover gradients, indexed by `artists.cover_gradient_index` (0–5).
 * Port of iOS `ArtistGradient`. Out-of-range indices CLAMP to the nearest valid
 * palette (a forward-compat 7th vibe still renders) rather than crashing.
 * Single source so a search-result gradient never drifts from the full-profile
 * gradient for the same artist.
 */
object ArtistGradient {
    // Each palette is 3 stops landing on the warm-black ground (0F1014) so the
    // bottom name/price strip stays legible regardless of the picked vibe.
    private val palettes: List<List<Color>> = listOf(
        listOf(Color(0xFFFF6B9D), Color(0xFF7C5CFF), Color(0xFF0F1014)),
        listOf(Color(0xFF22D3EE), Color(0xFF7C5CFF), Color(0xFF0F1014)),
        listOf(Color(0xFFFFB547), Color(0xFFFF5A6E), Color(0xFF0F1014)),
        listOf(Color(0xFF34D399), Color(0xFF5BB7FF), Color(0xFF0F1014)),
        listOf(Color(0xFFFF6FAE), Color(0xFFFFB547), Color(0xFF0F1014)),
        listOf(Color(0xFF7C5CFF), Color(0xFF22D3EE), Color(0xFF0F1014)),
    )

    fun palette(index: Int): List<Color> =
        palettes[index.coerceIn(0, palettes.size - 1)]
}
