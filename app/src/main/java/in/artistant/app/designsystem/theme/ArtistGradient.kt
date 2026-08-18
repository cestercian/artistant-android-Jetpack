package `in`.artistant.app.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * Six brand cover gradients indexed by `artists.cover_gradient_index` (0–5).
 * Port of iOS `ArtistGradient` — the never-empty fallback behind every tile/hero
 * when no cover photo has loaded (no upload yet, or a load still in flight/failed).
 *
 * Lives here, with the rest of the visual language, rather than beside the `Artist`
 * model it decorates: it is brand colour data, and the mappers that stamp a palette
 * onto a row are its callers, not its owners.
 *
 * RAW HEXES ON PURPOSE — do not retarget these at [AppColors]/[AppRole] tokens. The
 * index is a persisted column every client reads, so the palette it names has to
 * resolve to the SAME colours on iOS, web and here. The violet stop equals the artist
 * accent by coincidence of one brand, not by reference: binding it to the token would
 * drift Android's covers away from iOS's (`Models/ArtistGradient.swift`) the first time
 * the role accent is retuned. Likewise the shared terminal stop is the warm-black
 * *cover* ground iOS documents — chosen so the tile's name/price strip stays legible
 * over any vibe — deliberately not `colors.bg`, which is a page colour.
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
