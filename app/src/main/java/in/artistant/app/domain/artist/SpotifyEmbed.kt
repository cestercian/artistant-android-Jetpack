package `in`.artistant.app.domain.artist

/**
 * Turning an artist's pasted Spotify link into the address of Spotify's own
 * embed widget.
 *
 * `artists.spotify_artist_url` is free text an artist typed or pasted into the
 * wizard, and the profile hands the result straight to a WebView — so this is
 * the only thing standing between "whatever was in the clipboard" and a page
 * load inside the app. Kept as a pure function over a String, away from the
 * WebView and the Composable, so every shape it has to survive is covered.
 *
 * The transform itself is trivial and matches iOS `SpotifyEmbedView.embedURL`:
 * insert `/embed` after the host.
 *
 *     https://open.spotify.com/artist/4gzpq5DPGxSnKTe4SA8HAU
 *     https://open.spotify.com/embed/artist/4gzpq5DPGxSnKTe4SA8HAU
 *
 * Two places this is deliberately STRICTER than iOS, which prefixes `/embed` to
 * any path on any `*.spotify.com` host:
 *
 *  - the first path segment must be something Spotify actually embeds. Otherwise
 *    a link to `open.spotify.com/legal` renders Spotify's terms of service
 *    inside the artist's Sound section, chrome-less and un-dismissable, and a
 *    link to anything else on the host is a page nobody chose to show.
 *  - the id must be base62, which is what a Spotify id is. It lands in a URL
 *    this app builds and then loads, so it does not get to carry a `..`, a
 *    query, or a second path segment.
 *
 * Anything that fails either test returns null, and null means the Sound section
 * simply has no Spotify row — never a WebView pointed at a guess.
 *
 * Forgiving where an artist realistically is: a missing scheme (the wizard's own
 * placeholder reads `open.spotify.com/artist/…`, so half of them will paste it
 * that way), a `?si=` share suffix, Spotify's `/intl-xx/` locale prefix, and a
 * link that is already an embed URL.
 */

/** Path kinds Spotify serves an embed player for. */
private val EMBEDDABLE = setOf("artist", "album", "track", "playlist", "episode", "show")

/**
 * The embed URL for [raw], or null when [raw] is not a Spotify link this app is
 * willing to load.
 */
fun spotifyEmbedUrl(raw: String?): String? {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isEmpty()) return null

    // Scheme: only http(s), and only when one is present at all. A `javascript:`
    // or `file:` prefix is not a typo to be repaired, it is a reason to stop.
    val schemeSplit = trimmed.indexOf("://")
    val rest = if (schemeSplit < 0) {
        trimmed
    } else {
        val scheme = trimmed.take(schemeSplit).lowercase()
        if (scheme != "https" && scheme != "http") return null
        trimmed.substring(schemeSplit + 3)
    }
    // A bare `spotify.com/...` with no scheme must not be confused with
    // `mailto:` style opaque forms; anything left carrying a colon before the
    // first slash is not a host.
    val hostAndPath = rest.substringBefore('#').substringBefore('?')
    val host = hostAndPath.substringBefore('/').lowercase()
    if (host != "spotify.com" && !host.endsWith(".spotify.com")) return null

    var segments = hostAndPath.substringAfter('/', "")
        .split('/')
        .filter { it.isNotBlank() }
    // `/intl-de/track/<id>` — Spotify localises shared links, and the locale
    // segment is not part of the embed path.
    if (segments.firstOrNull()?.startsWith("intl-") == true) segments = segments.drop(1)
    // An already-embed URL passes through rather than becoming `/embed/embed/…`.
    if (segments.firstOrNull() == "embed") segments = segments.drop(1)

    val kind = segments.getOrNull(0)?.lowercase() ?: return null
    if (kind !in EMBEDDABLE) return null
    val id = segments.getOrNull(1) ?: return null
    if (id.isEmpty() || !id.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' }) return null

    // The host is rebuilt rather than reused: embeds are only served from
    // open.spotify.com, so a link written against `www.spotify.com` (which
    // redirects for a human, not for an iframe) still resolves to a player.
    return "https://open.spotify.com/embed/$kind/$id"
}
