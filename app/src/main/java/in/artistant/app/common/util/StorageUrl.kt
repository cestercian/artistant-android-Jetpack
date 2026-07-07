package `in`.artistant.app.common.util

import `in`.artistant.app.core.config.AppEnvironment

/**
 * Builds the public CDN URL for an `artist-media` storage object from its stored
 * relative path (e.g. `a1/photo/p.jpg`). Port of the iOS
 * `ArtistsRepository.coverURL(forStoragePath:)` + `ArtistMediaItem.publicURL` —
 * both shared one URL-shaping rule, so it lives in one place here too.
 *
 * The `artist-media` bucket is public with no SELECT policy (API_MAPPING §6), so
 * views hit this URL directly (via Coil) instead of round-tripping the Storage
 * SDK. Returns null for an empty path so callers fall back to the gradient
 * placeholder.
 */
fun artistMediaPublicUrl(storagePath: String): String? {
    if (storagePath.isEmpty()) return null
    val base = AppEnvironment.supabaseUrl.trimEnd('/')
    return "$base/storage/v1/object/public/artist-media/$storagePath"
}
