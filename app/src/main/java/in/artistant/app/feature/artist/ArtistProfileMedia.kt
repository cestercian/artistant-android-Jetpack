package `in`.artistant.app.feature.artist

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil3.compose.AsyncImage
import `in`.artistant.app.data.model.GalleryPhoto
import `in`.artistant.app.data.repository.ArtistMediaAspect
import `in`.artistant.app.designsystem.theme.AppTheme
import timber.log.Timber

/**
 * The profile's two picture-and-sound surfaces: the About gallery strip and the
 * Spotify player behind the Sound section's disclosure.
 *
 * Their own file rather than more of `ArtistProfileScreen.kt` because both are
 * self-contained renderings of one field — a photo list and a URL — with no
 * knowledge of the page around them, and because one of them embeds a WebView,
 * which is the sort of thing that should be findable.
 */

// ── Gallery strip ────────────────────────────────────────────────────────────

/**
 * Tile geometry, ported from iOS `ArtistView.galleryCellWidth` — a fixed height
 * with three widths, so a portrait shot reads portrait and a landscape one gets
 * the room it needs instead of every photo being cropped to the same square.
 *
 * Local constants rather than `Size` tokens because `designsystem/theme` was
 * held by a concurrent branch while this landed; they belong beside the widths
 * they pair with and should move onto the ramp in a follow-up.
 */
private val GALLERY_TILE_HEIGHT: Dp = 150.dp
private val GALLERY_TILE_SQUARE: Dp = 150.dp
private val GALLERY_TILE_PORTRAIT: Dp = 120.dp
private val GALLERY_TILE_LANDSCAPE: Dp = 200.dp

private fun galleryTileWidth(aspect: ArtistMediaAspect): Dp = when (aspect) {
    ArtistMediaAspect.square -> GALLERY_TILE_SQUARE
    ArtistMediaAspect.portrait -> GALLERY_TILE_PORTRAIT
    ArtistMediaAspect.landscape -> GALLERY_TILE_LANDSCAPE
}

/**
 * The artist's other photos, as a horizontal strip under the bio.
 *
 * A plain scrolling `Row`, not a `LazyRow`: the server caps an artist at six
 * photos, and this sits inside the page's own vertical scroll where a lazy row
 * buys nothing but a second scroll container to get wrong.
 *
 * No card chrome, no captions, no page dots — the strip is the artist's work and
 * anything drawn around it is the app talking over them. Tapping a tile does
 * nothing yet; the full-screen pager is iOS PROF-10 and is not this change.
 */
@Composable
internal fun GalleryStrip(photos: List<GalleryPhoto>, modifier: Modifier = Modifier) {
    val dimens = AppTheme.dimens
    val colors = AppTheme.colors
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(dimens.space.sm),
    ) {
        photos.forEachIndexed { index, photo ->
            AsyncImage(
                model = photo.url,
                // Numbered rather than "Gallery photo" ×5: with no alt text to
                // read, position in the set is the only thing that distinguishes
                // one tile from the next when swiping through by ear.
                contentDescription = "Photo ${index + 1} of ${photos.size}",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = galleryTileWidth(photo.aspect), height = GALLERY_TILE_HEIGHT)
                    .clip(RoundedCornerShape(dimens.radii.sm))
                    // Behind the image, so a slow load is a quiet placeholder
                    // rather than a hole in the page.
                    .background(colors.bgCard),
            )
        }
    }
}

// ── Spotify ──────────────────────────────────────────────────────────────────

/**
 * Spotify's recommended minimum for the artist/album embed form (iOS asks for
 * the same 380 under `minHeight`). Shorter and the widget scrolls its own track
 * list inside a letterbox.
 */
private val SPOTIFY_EMBED_HEIGHT: Dp = 380.dp

/**
 * A hairline disclosure row — icon, title, muted detail, rotating chevron, the
 * whole row a hit target. Mirrors iOS `ArtistView.disclosureRow`, which is how
 * the Sound section's heavier content stays folded away until it is asked for.
 */
@Composable
internal fun DisclosureRow(
    title: String,
    detail: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val space = dimens.space
    val chevron by animateFloatAsState(if (expanded) 90f else 0f, label = "disclosureChevron")
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = space.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space.md),
    ) {
        Icon(
            Icons.Filled.MusicNote,
            contentDescription = null,
            tint = colors.ink2,
            modifier = Modifier.size(dimens.size.iconMd),
        )
        Text(title, style = AppTheme.type.callout, color = colors.ink)
        Spacer(Modifier.weight(1f))
        Text(detail, style = AppTheme.type.footnote, color = colors.ink3)
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            // Decorative: the row's own title labels the control, and the
            // expanded state is announced by the content appearing under it.
            contentDescription = null,
            tint = colors.ink3,
            modifier = Modifier.size(dimens.size.iconLg).rotate(chevron),
        )
    }
}

/**
 * Spotify's official embed widget, in a WebView.
 *
 * We write zero playback code: the widget is Spotify's own UI — art, title,
 * 30-second preview, and a hand-off to the Spotify app for the full track — and
 * the embed URL is public, so there is no Web API auth here and no token to
 * hold. Same approach as iOS `SpotifyEmbedView`, which wraps `WKWebView`.
 *
 * The WebView is deliberately not a general browser:
 *
 *  - **JavaScript is on** because the widget is a JS player and there is no
 *    other way to render it. Everything else that widens a WebView's reach is
 *    off — no file access, no content-provider access, no local storage — so
 *    what it can do is play one embed.
 *  - **Navigation is pinned.** Spotify's "open in Spotify" control would
 *    otherwise replace the widget with the full Spotify web page *inside the
 *    artist's profile*, chrome-less and with no way back. Anything that is not
 *    this embed leaves for the system instead, which is what hands it to the
 *    Spotify app when it is installed.
 *
 * The view is destroyed on release rather than left to the GC: a WebView that
 * outlives its Composable keeps its audio playing, and leaving a profile has to
 * stop the sound.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun SpotifyEmbed(embedUrl: String, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height(SPOTIFY_EMBED_HEIGHT)
            .clip(RoundedCornerShape(AppTheme.dimens.radii.md)),
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                // The widget honours prefers-color-scheme: dark, so a transparent
                // ground lets it sit on the page instead of on a white card that
                // flashes while it loads.
                setBackgroundColor(AndroidColor.TRANSPARENT)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                isScrollContainer = false
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                // The preview snippet is the point of the embed — requiring a
                // second gesture after the one the user already made on Spotify's
                // own play button means nothing happens when they press it.
                settings.mediaPlaybackRequiresUserGesture = false
                webViewClient = SpotifyEmbedClient
            }
        },
        update = { web ->
            // Guarded, because `update` runs on every recomposition and an
            // unguarded load would restart the widget — and its audio — each
            // time anything else on the profile changed.
            if (web.tag != embedUrl) {
                web.tag = embedUrl
                web.loadUrl(embedUrl)
            }
        },
        onRelease = { it.destroy() },
    )
}

/**
 * Keeps the WebView on the embed and hands everything else to the system.
 *
 * Stateless, so one instance serves every embed.
 */
private object SpotifyEmbedClient : WebViewClient() {
    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest,
    ): Boolean {
        // Only the MAIN frame is policed. The widget loads its own subframes and
        // resources, and treating one of those as "the user is leaving" would
        // throw the browser open behind their back on a page they only scrolled
        // past.
        if (!request.isForMainFrame) return false
        val url = request.url
        if (isEmbed(url)) return false
        // Refusing is the point either way — nothing replaces the embed inside
        // the artist's profile. What a real tap additionally earns is the
        // hand-off, which is what puts "open in Spotify" into the Spotify app.
        // A scripted redirect gets the refusal without it.
        if (request.hasGesture()) {
            // `runCatching` because a device with nothing able to open the link
            // throws, and a profile is not worth crashing over a tap on someone
            // else's widget.
            runCatching {
                view.context.startActivity(
                    Intent(Intent.ACTION_VIEW, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }.exceptionOrNull()?.let { Timber.w(it, "Couldn't open %s", url.host) }
        }
        return true
    }

    private fun isEmbed(url: Uri): Boolean =
        url.scheme.equals("https", ignoreCase = true) &&
            url.host.equals("open.spotify.com", ignoreCase = true) &&
            url.path.orEmpty().startsWith("/embed")
}

/**
 * The Sound section's Spotify half: a disclosure row that opens the player.
 *
 * Folded away by default, exactly as iOS folds it: the embed is a third-party
 * page that fetches its own art and scripts, and loading one on every profile
 * open — for a client who came to read reviews — is bandwidth spent on a thing
 * nobody asked to see. Expanding is the ask.
 */
@Composable
internal fun SpotifyDisclosure(
    embedUrl: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val space = AppTheme.dimens.space
    Column(modifier.fillMaxWidth()) {
        DisclosureRow(
            title = "Spotify",
            detail = if (expanded) "Hide" else "Listen",
            expanded = expanded,
            onToggle = onToggle,
        )
        AnimatedVisibility(visible = expanded) {
            SpotifyEmbed(embedUrl, Modifier.padding(bottom = space.md))
        }
    }
}
