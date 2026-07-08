package `in`.artistant.app.data.model

import `in`.artistant.app.common.util.artistMediaPublicUrl
import java.time.Instant

/** `public.artist_media.kind`. */
enum class MediaKind(val db: String) {
    Photo("photo"),
    Video("video");

    companion object {
        fun fromDb(raw: String): MediaKind? = entries.firstOrNull { it.db == raw }
    }
}

/** `public.artist_media.aspect`. */
enum class MediaAspect(val db: String) {
    Square("square"),
    Portrait("portrait"),
    Landscape("landscape");

    companion object {
        fun fromDb(raw: String): MediaAspect? = entries.firstOrNull { it.db == raw }

        /**
         * Auto-classify a WxH into one of the three buckets (iOS
         * `ArtistMediaAspect.classify`): >1.2 landscape, <0.85 portrait, else
         * square (so a 4:5 feed photo is portrait, 16:9 is landscape). Zero/short
         * height falls back to square.
         */
        fun classify(width: Int, height: Int): MediaAspect {
            if (height <= 0) return Square
            val ratio = width.toDouble() / height.toDouble()
            return when {
                ratio > 1.2 -> Landscape
                ratio < 0.85 -> Portrait
                else -> Square
            }
        }
    }
}

/**
 * One row of `public.artist_media` (iOS `ArtistMediaItem`). Read-side only in
 * M2a — the gallery render path. Upload/delete is M5. [publicUrl] builds a stable
 * URL against the public `artist-media` bucket so views render directly via Coil.
 */
data class ArtistMediaItem(
    val id: String,
    val artistId: String,
    val kind: MediaKind,
    val aspect: MediaAspect,
    val position: Int,
    val storagePath: String,
    val mimeType: String,
    val width: Int?,
    val height: Int?,
    val durationSeconds: Double?,
    val createdAt: Instant,
) {
    val publicUrl: String? get() = artistMediaPublicUrl(storagePath)
}
