package `in`.artistant.app.domain.artist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `artists.spotify_artist_url` is free text and its output is loaded in a
 * WebView, so these cover both halves: the shapes an artist realistically pastes
 * have to resolve, and everything else has to resolve to null rather than to a
 * page nobody chose.
 */
class SpotifyEmbedTest {

    // ── The shapes that must work ────────────────────────────────────────────

    @Test
    fun artistLinkBecomesAnArtistEmbed() {
        assertEquals(
            "https://open.spotify.com/embed/artist/4gzpq5DPGxSnKTe4SA8HAU",
            spotifyEmbedUrl("https://open.spotify.com/artist/4gzpq5DPGxSnKTe4SA8HAU"),
        )
    }

    @Test
    fun albumTrackAndPlaylistAllEmbed() {
        assertEquals(
            "https://open.spotify.com/embed/album/1DFixOWyIaqjHRPWvGwsSp",
            spotifyEmbedUrl("https://open.spotify.com/album/1DFixOWyIaqjHRPWvGwsSp"),
        )
        assertEquals(
            "https://open.spotify.com/embed/track/6rqhFgbbKwnb9MLmUQDhG6",
            spotifyEmbedUrl("https://open.spotify.com/track/6rqhFgbbKwnb9MLmUQDhG6"),
        )
        assertEquals(
            "https://open.spotify.com/embed/playlist/37i9dQZF1DXcBWIGoYBM5M",
            spotifyEmbedUrl("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M"),
        )
    }

    /**
     * The wizard's own placeholder reads `open.spotify.com/artist/…`, so a good
     * share of artists will type exactly that. Rejecting it would mean a Sound
     * section that silently has no Spotify row for a link that looks right in
     * the editor.
     */
    @Test
    fun aMissingSchemeIsNotAMalformedLink() {
        assertEquals(
            "https://open.spotify.com/embed/artist/4gzpq5DPGxSnKTe4SA8HAU",
            spotifyEmbedUrl("open.spotify.com/artist/4gzpq5DPGxSnKTe4SA8HAU"),
        )
    }

    /** Spotify's own share sheet appends `?si=…`; it is not part of the embed. */
    @Test
    fun theShareSuffixIsDropped() {
        assertEquals(
            "https://open.spotify.com/embed/track/6rqhFgbbKwnb9MLmUQDhG6",
            spotifyEmbedUrl("https://open.spotify.com/track/6rqhFgbbKwnb9MLmUQDhG6?si=abc123&nd=1"),
        )
    }

    /** Spotify localises shared links; the locale segment is not a path kind. */
    @Test
    fun theLocalePrefixIsDropped() {
        assertEquals(
            "https://open.spotify.com/embed/track/6rqhFgbbKwnb9MLmUQDhG6",
            spotifyEmbedUrl("https://open.spotify.com/intl-de/track/6rqhFgbbKwnb9MLmUQDhG6"),
        )
    }

    /** An artist who pasted the embed URL already gets a player, not `/embed/embed`. */
    @Test
    fun anEmbedUrlPassesThroughUnchanged() {
        assertEquals(
            "https://open.spotify.com/embed/artist/4gzpq5DPGxSnKTe4SA8HAU",
            spotifyEmbedUrl("https://open.spotify.com/embed/artist/4gzpq5DPGxSnKTe4SA8HAU"),
        )
    }

    /**
     * Embeds are only served from open.spotify.com, so the host is rebuilt
     * rather than reused — a link written against the marketing host still
     * resolves to a player instead of to an iframe that never loads.
     */
    @Test
    fun anotherSpotifyHostIsRewrittenToTheEmbedHost() {
        assertEquals(
            "https://open.spotify.com/embed/artist/4gzpq5DPGxSnKTe4SA8HAU",
            spotifyEmbedUrl("https://www.spotify.com/artist/4gzpq5DPGxSnKTe4SA8HAU"),
        )
    }

    @Test
    fun surroundingWhitespaceIsNotAnError() {
        assertEquals(
            "https://open.spotify.com/embed/artist/4gzpq5DPGxSnKTe4SA8HAU",
            spotifyEmbedUrl("  https://open.spotify.com/artist/4gzpq5DPGxSnKTe4SA8HAU  "),
        )
    }

    // ── The shapes that must NOT ─────────────────────────────────────────────

    @Test
    fun nullAndBlankAreNotLinks() {
        assertNull(spotifyEmbedUrl(null))
        assertNull(spotifyEmbedUrl(""))
        assertNull(spotifyEmbedUrl("   "))
    }

    @Test
    fun anotherServiceIsNeverEmbedded() {
        assertNull(spotifyEmbedUrl("https://soundcloud.com/artist/track"))
        assertNull(spotifyEmbedUrl("https://music.apple.com/in/artist/x/123"))
        // The suffix check is on the HOST, not on the string: an attacker's
        // domain that merely ends in the right letters is a different host.
        assertNull(spotifyEmbedUrl("https://notspotify.com/artist/4gzpq5DPGxSnKTe4SA8HAU"))
    }

    /**
     * The reason this function is strict at all. Prefixing `/embed` to any path,
     * as iOS does, turns a link to Spotify's terms of service into a full web
     * page rendered inside the artist's Sound section.
     */
    @Test
    fun aSpotifyPageThatIsNotAnEmbeddableThingIsRefused() {
        assertNull(spotifyEmbedUrl("https://open.spotify.com/legal"))
        assertNull(spotifyEmbedUrl("https://open.spotify.com/search/queries"))
        assertNull(spotifyEmbedUrl("https://open.spotify.com/"))
        assertNull(spotifyEmbedUrl("https://open.spotify.com"))
    }

    @Test
    fun aKindWithNoIdBehindItIsRefused() {
        assertNull(spotifyEmbedUrl("https://open.spotify.com/artist"))
        assertNull(spotifyEmbedUrl("https://open.spotify.com/artist/"))
    }

    /**
     * The id is interpolated into a URL this app then loads, so it does not get
     * to carry path segments or traversal of its own.
     */
    @Test
    fun anIdThatIsNotBase62IsRefused() {
        assertNull(spotifyEmbedUrl("https://open.spotify.com/artist/../../legal"))
        assertNull(spotifyEmbedUrl("https://open.spotify.com/artist/id with spaces"))
        assertNull(spotifyEmbedUrl("https://open.spotify.com/artist/id-with-dashes"))
    }

    @Test
    fun aNonHttpSchemeIsRefused() {
        assertNull(spotifyEmbedUrl("javascript://open.spotify.com/artist/4gzpq5DPGxSnKTe4SA8HAU"))
        assertNull(spotifyEmbedUrl("file://open.spotify.com/artist/4gzpq5DPGxSnKTe4SA8HAU"))
    }

    /** Plain http is upgraded rather than refused — the embed host is https-only. */
    @Test
    fun httpIsUpgradedToHttps() {
        assertEquals(
            "https://open.spotify.com/embed/artist/4gzpq5DPGxSnKTe4SA8HAU",
            spotifyEmbedUrl("http://open.spotify.com/artist/4gzpq5DPGxSnKTe4SA8HAU"),
        )
    }
}
