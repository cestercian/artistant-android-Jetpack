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
fun artistMediaPublicUrl(storagePath: String): String? =
    storagePublicUrl("artist-media", storagePath)

/**
 * Generic public-URL builder for any public bucket (`artist-media`,
 * `artist-samples`). Same shape as the iOS side. Null on an empty path.
 */
fun storagePublicUrl(bucket: String, storagePath: String): String? {
    if (storagePath.isEmpty()) return null
    val base = AppEnvironment.supabaseUrl.trimEnd('/')
    return "$base/storage/v1/object/public/$bucket/$storagePath"
}

/**
 * Inverse of [storagePublicUrl] — pulls the bucket-relative path back out of a
 * public URL so `storage.from(bucket).delete(path)` can target it. Returns null
 * for a URL that doesn't carry the bucket marker (defensive against historical
 * non-Supabase rows, e.g. pasted Spotify embeds). Strips a trailing query string.
 * Port of the iOS `SamplesRepository.storagePath(fromPublicURL:)`.
 */
fun storagePathFromPublicUrl(bucket: String, url: String?): String? {
    if (url.isNullOrEmpty()) return null
    val marker = "/object/public/$bucket/"
    val idx = url.indexOf(marker)
    if (idx < 0) return null
    val tail = url.substring(idx + marker.length)
    return tail.substringBefore('?')
}
