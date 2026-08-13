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
    /**
     * Which palette [gradient] was resolved from.
     *
     * Carried alongside the resolved colours rather than instead of them: every
     * reader wants the colours, and only the editor wants to know which entry is
     * currently picked so it can ring that swatch. Deriving the index back out of
     * a colour list would be fragile the moment two palettes share a stop.
     */
    val coverGradientIndex: Int = 0,
    val newArtistDiscountPct: Int = 0,
    /**
     * Fri–Sun surcharge %, 0–100 (`artists.weekend_premium_pct`).
     *
     * Sits beside [newArtistDiscountPct] rather than in a nested "pricing"
     * object because they are the same kind of fact — a percentage the artist
     * publishes and honours in their own quote — and every reader that wants one
     * wants the other.
     */
    val weekendPremiumPct: Int = 0,
    /**
     * The curated "what you offer" slugs (`artists.service_tags`).
     *
     * Slugs, not labels: Discover's server-side services filter overlaps this
     * column with the same slug vocabulary, so storing display text here would
     * publish an artist who can never be found by the filter that exists to find
     * them. See `ServiceTags` for the taxonomy and the label lookup.
     */
    val serviceTags: List<String> = emptyList(),
    /** Answered personality prompts (`artists.prompts` jsonb). */
    val prompts: List<ArtistPrompt> = emptyList(),
)

/**
 * One personality prompt and its answer.
 *
 * Round-trips 1:1 to a `{"q":…,"a":…}` object in the `artists.prompts` jsonb
 * array — the field names here are the readable ones, and the wire keys live in
 * `ArtistPrompts` so exactly one place knows the encoding.
 */
data class ArtistPrompt(
    val question: String,
    val answer: String,
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
    /**
     * Public CDN URL of the clip in the `artist-samples` bucket, or null.
     *
     * Nullable because `samples.audio_url` is: rows written before the upload
     * path existed, and rows whose upload never drained, carry a title with no
     * file. Playback treats those as unplayable rather than offering a control
     * that does nothing.
     */
    val audioUrl: String? = null,
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

    /** How many palettes the picker may offer. */
    val count: Int get() = palettes.size

    fun palette(index: Int): List<Color> =
        palettes[index.coerceIn(0, palettes.lastIndex)]

    /**
     * Clamp an index onto the palette range.
     *
     * The read path coerces already, so this exists for the WRITE path: an index
     * that is out of range here would persist a number the app cannot render, and
     * every later read would silently show palette 0 while the column said
     * otherwise. Refusing at the boundary keeps the stored value and the rendered
     * cover the same fact.
     */
    fun clampIndex(index: Int): Int = index.coerceIn(0, palettes.lastIndex)
}
