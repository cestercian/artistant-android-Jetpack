package `in`.artistant.app.data.model

import androidx.compose.ui.graphics.Color

/**
 * Domain artist — port of iOS `Models/Artist.swift`. Tile-level projections from
 * `search_artists` leave packages/bio/stats empty; `ArtistsRepository.fetchArtist`
 * upgrades to a full 5-table stitch for the profile screen.
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
    val bio: String = "",
    val followers: String = "",
    val streams: String = "",
    val response: String = "",
    val onTime: Int = 0,
    val gigs: Int = 0,
    val rating: Double = 0.0,
    val packages: List<ArtistPackage> = emptyList(),
    val tech: List<String> = emptyList(),
    val samples: List<Sample> = emptyList(),
    val reviews: List<Review> = emptyList(),
    val spotifyArtistUrl: String? = null,
    val instagramHandle: String? = null,
    val youtubeChannelUrl: String? = null,
    val daysAvailable: List<String> = emptyList(),
    val timeSlots: List<String> = emptyList(),
    /** Public CDN URL for position-0 photo, or null → gradient fallback. */
    val coverUrl: String? = null,
    val newArtistDiscountPct: Int = 0,
)

data class ArtistPackage(
    val id: String,
    val name: String,
    val duration: String,
    val price: Int,
    val includes: List<String>,
    val popular: Boolean = false,
)

data class Sample(
    val id: String,
    val title: String,
    val duration: String,
)

data class Review(
    val id: String,
    val name: String,
    val org: String,
    val rating: Int,
    val body: String,
    val createdAt: String? = null,
    val categories: Map<String, Int>? = null,
)

/**
 * Six brand cover gradients indexed by `artists.cover_gradient_index` (0–5).
 * Port of iOS `ArtistGradient` — the never-empty fallback behind every tile/hero.
 */
object ArtistGradient {
    private val palettes: List<List<Color>> = listOf(
        listOf(Color(0xFFFF6B9D), Color(0xFF7C5CFF), Color(0xFF0F1014)),
        listOf(Color(0xFF22D3EE), Color(0xFF7C5CFF), Color(0xFF0F1014)),
        listOf(Color(0xFFFFB547), Color(0xFFFF5A6E), Color(0xFF0F1014)),
        listOf(Color(0xFF34D399), Color(0xFF5BB7FF), Color(0xFF0F1014)),
        listOf(Color(0xFFFF6FAE), Color(0xFFFFB547), Color(0xFF0F1014)),
        listOf(Color(0xFF7C5CFF), Color(0xFF22D3EE), Color(0xFF0F1014)),
    )

    fun palette(index: Int): List<Color> =
        palettes[index.coerceIn(0, palettes.lastIndex)]
}
