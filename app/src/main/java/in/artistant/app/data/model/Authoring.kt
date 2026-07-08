package `in`.artistant.app.data.model

/**
 * Artist-authoring (M5a write-side) domain models — the shapes the EPK editor /
 * wizard build to WRITE, distinct from the read-only browse models (`Sample`,
 * `ArtistPackage`). Ports of the iOS `SampleRow` / `SampleInput` / `ArtistLink`.
 */

/** Read-side row from `public.samples` — everything the EPK "Music" tab renders. */
data class SampleRow(
    val id: String,
    val artistId: String,
    val position: Int,
    val title: String,
    val durationLabel: String,
    val audioUrl: String?,
    val spotifyTrackUrl: String?,
    val coverArtUrl: String?,
)

/**
 * Input shape for the atomic samples replace (EPK editor re-order/remove). The
 * wizard's first publish uses the per-sample upload path instead, so it never
 * builds these.
 */
data class SampleInput(
    val title: String,
    val durationLabel: String,
    val audioUrl: String? = null,
    val spotifyTrackUrl: String? = null,
    val coverArtUrl: String? = null,
)

/** One row of `public.artist_links` — an external URL surfaced on the EPK. */
data class ArtistLink(
    val id: String,
    val artistId: String,
    val label: String,
    val url: String,
    val position: Int,
)
