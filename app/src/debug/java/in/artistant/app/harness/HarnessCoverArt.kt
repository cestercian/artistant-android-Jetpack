package `in`.artistant.app.harness

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withRotation
import `in`.artistant.app.data.model.Artist
import timber.log.Timber
import java.io.File
import java.util.Random

/**
 * Cover art for the fixture roster, drawn on device at first harness launch.
 *
 * WHY THIS EXISTS
 * ---------------
 * The harness used to seed every artist with `coverUrl = null`, so every surface
 * that shows a cover — the Discover hero, the rails, the search tiles, the
 * artist profile, the inbox thumbnails, the EPK — rendered its GRADIENT FALLBACK
 * instead. Two things follow from that, and both are bad:
 *
 *  1. Nobody looking at the app was looking at the design. Scrims, crops, the
 *     hero's bottom fade, the legibility of white type over a picture: all of it
 *     is tuned for a photograph and none of it was on screen.
 *  2. The image path itself was never exercised. A broken aspect ratio, a wrong
 *     `ContentScale`, a fetch that silently fails — all invisible, because the
 *     fallback looks intentional.
 *
 * WHY GENERATED, NOT BUNDLED
 * --------------------------
 * The emulator this harness runs on has no working network, so anything fetched
 * over HTTP would fail and land straight back on the fallback. That leaves
 * bundling or generating. These are GENERATED — drawn here, from a fixed palette
 * and a per-artist seed — which means:
 *
 *  - no third-party imagery, no licence to track, and nothing to get wrong when
 *    a public repo ships someone else's photograph;
 *  - no binaries in git and no bytes in ANY artifact, debug or release: the
 *    files are written to `cacheDir` on the device, not packaged;
 *  - deterministic. The same artist gets the same cover on every device and
 *    every launch, so two screenshots of the same screen are comparable.
 *
 * They are deliberately abstract — stage lighting, not a band. Nobody should
 * mistake a fixture for a real photograph, and the point is to exercise the
 * picture path and give the type something to sit on, not to fake a portfolio.
 *
 * COST
 * ----
 * Eight 600×750 bitmaps, drawn and JPEG-encoded once and then reused from disk
 * — a fraction of a second on the FIRST harness launch after an install, zero on
 * every launch after that. Paid synchronously on the main thread during
 * [HarnessInstaller]'s pre-create hook, because the alternative (writing them in
 * the background) races the first image request, and Coil caches a miss.
 */
internal object HarnessCoverArt {

    /**
     * Bumped whenever the drawing below changes, so a device that already cached
     * the old art redraws instead of showing it forever. Cheaper and more
     * obvious than clearing the cache directory on a version mismatch.
     */
    private const val VERSION = 2

    // 4:5, the aspect the artist-media pipeline calls `portrait`. Small enough to
    // draw and encode in a few milliseconds, large enough that upscaling it into
    // a full-bleed hero stays clean — everything drawn here is soft, so there is
    // no fine detail for the upscale to lose.
    private const val WIDTH = 600
    private const val HEIGHT = 750
    private const val QUALITY = 88

    /**
     * Two frames past the end of the roster, belonging to no artist.
     *
     * They fill out the fixture artist's media gallery. Reusing another artist's
     * cover for that would put one image on two different artists' work, which
     * is exactly the sort of thing someone would then spend ten minutes trying
     * to reproduce as a bug.
     */
    private const val EXTRA_GALLERY = 2

    @Volatile
    private var uris: List<String> = emptyList()

    @Volatile
    private var rosterCount: Int = 0

    /**
     * Draw (or find) the covers and remember their `file://` URIs.
     *
     * Safe to call more than once and safe never to call: with no install the
     * URI list stays empty, [withCovers] hands every artist back untouched, and
     * the harness behaves exactly as it did before this file existed.
     */
    fun install(context: Context, count: Int) {
        if (uris.isNotEmpty()) return
        val started = System.currentTimeMillis()
        val dir = File(context.cacheDir, "harness-covers")
        val built = buildList {
            for (index in 0 until count + EXTRA_GALLERY) {
                val file = File(dir, "cover-$index-v$VERSION.jpg")
                val ok = file.exists() || runCatching { write(file, index) }.onFailure {
                    Timber.e(it, "[harness] couldn't draw cover $index")
                }.isSuccess
                // A cover that failed to write is dropped rather than pointed at:
                // a URI to a missing file renders as a broken image, whereas a
                // null coverUrl renders as the gradient the app already ships.
                if (ok) add(android.net.Uri.fromFile(file).toString())
            }
        }
        rosterCount = count
        uris = built
        Timber.i("[harness] ${built.size} covers ready in ${System.currentTimeMillis() - started}ms")
    }

    /**
     * Attach covers to a roster, positionally.
     *
     * The LAST artist is left bare on purpose. A roster where every card has a
     * picture makes the no-photo state — which every real artist occupies until
     * they upload one, and which the gradient fallback exists for — unreachable
     * from inside the harness. One uncovered entry keeps both paths on screen.
     */
    fun withCovers(roster: List<Artist>): List<Artist> {
        if (uris.isEmpty()) return roster
        return roster.mapIndexed { index, artist ->
            val url = uris.getOrNull(index)?.takeIf { index < roster.lastIndex }
            if (url == null) artist else artist.copy(coverUrl = url)
        }
    }

    /**
     * A media gallery for the nth roster artist: their own cover first — the
     * server's rule is that the cover IS the photo at position 0, and a fixture
     * that disagreed with it would show one image on Discover and another on the
     * same artist's press kit — then the unassigned extras.
     *
     * Empty when nothing was drawn, which leaves the gallery in the empty state
     * it was in before this existed.
     */
    fun galleryFor(index: Int): List<String> {
        if (uris.isEmpty()) return emptyList()
        return listOfNotNull(uris.getOrNull(index)) + uris.drop(rosterCount)
    }

    // --- Drawing -----------------------------------------------------------------------

    /**
     * One cover: a duotone ground, a light bloom, a few large soft shapes, two
     * beams, a horizon, and a light touch at the top.
     *
     * NO BAKED-IN BOTTOM SCRIM, deliberately. The first version of this drew a
     * heavy dark ramp across the bottom half on the theory that white type has
     * to sit on something — and it made every cover look like it stopped
     * halfway down the hero, because the hero ALREADY fades its own bottom into
     * the page and the two ramps compounded. The surfaces that show a cover each
     * draw the scrim they need; a fixture that pre-darkens itself hides whether
     * those scrims work, which is the main thing having real cover art is for.
     * The top gets a light wash only, because the masthead sits nearly at the
     * image's edge with nothing between them.
     */
    private fun write(file: File, index: Int) {
        file.parentFile?.mkdirs()
        val bitmap = createBitmap(WIDTH, HEIGHT)
        val canvas = Canvas(bitmap)
        val rng = Random(SEED_BASE + index.toLong())
        val palette = PALETTES[index % PALETTES.size]
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Ground: a vertical duotone. Dark at both ends by construction, so the
        // shapes above it are what carries the colour.
        paint.shader = LinearGradient(
            0f, 0f, WIDTH * 0.35f, HEIGHT.toFloat(),
            palette.top, palette.bottom, Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), paint)

        // The bloom — one off-centre light source, upper half, which is what
        // makes the frame read as lit rather than as a gradient.
        val bloomX = WIDTH * (0.25f + rng.nextFloat() * 0.5f)
        val bloomY = HEIGHT * (0.18f + rng.nextFloat() * 0.22f)
        paint.shader = RadialGradient(
            bloomX, bloomY, WIDTH * 0.75f,
            withAlpha(palette.accent, 0x9C), Color.TRANSPARENT, Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), paint)

        // Three soft masses. Big and low-alpha: several small shapes read as a
        // pattern, and a pattern reads as a placeholder.
        repeat(3) { i ->
            val radius = WIDTH * (0.28f + rng.nextFloat() * 0.34f)
            val cx = WIDTH * (rng.nextFloat() * 1.1f - 0.05f)
            val cy = HEIGHT * (0.15f + rng.nextFloat() * 0.6f)
            val tint = if (i % 2 == 0) palette.accent else palette.accentAlt
            paint.shader = RadialGradient(
                cx, cy, radius,
                withAlpha(tint, 0x6E), Color.TRANSPARENT, Shader.TileMode.CLAMP,
            )
            canvas.drawCircle(cx, cy, radius, paint)
        }

        // Two beams, struck from the bloom. Rotated rather than axis-aligned so
        // the frame has a diagonal in it — the one thing the app's own gradient
        // fallback has none of, and therefore the quickest way to tell at a
        // glance whether a screen is showing a picture or the fallback.
        paint.shader = null
        repeat(2) {
            canvas.withRotation(-38f + rng.nextFloat() * 26f, bloomX, bloomY) {
                paint.color = withAlpha(Color.WHITE, 0x12)
                val beamW = WIDTH * (0.06f + rng.nextFloat() * 0.1f)
                val offset = WIDTH * (rng.nextFloat() * 0.9f - 0.45f)
                drawRect(
                    RectF(bloomX + offset, -HEIGHT.toFloat(), bloomX + offset + beamW, HEIGHT * 2f),
                    paint,
                )
            }
        }

        // Horizon: a soft ramp into the ground colour across the lower third.
        // Gives the frame a subject/ground split, which is what stops it reading
        // as wallpaper. A ramp rather than the flat fill this started as — a
        // flat one turned the bottom 40% into a single dark block.
        val horizon = HEIGHT * (0.58f + rng.nextFloat() * 0.12f)
        paint.shader = LinearGradient(
            0f, horizon, 0f, HEIGHT.toFloat(),
            Color.TRANSPARENT, withAlpha(palette.bottom, 0x96), Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, horizon, WIDTH.toFloat(), HEIGHT.toFloat(), paint)

        // The one wash — see the note above the function.
        paint.shader = LinearGradient(
            0f, 0f, 0f, HEIGHT * 0.22f,
            withAlpha(Color.BLACK, 0x5E), Color.TRANSPARENT, Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT * 0.22f, paint)

        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
        bitmap.recycle()
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    private const val SEED_BASE = 8_1_2026L

    private data class Palette(val top: Int, val bottom: Int, val accent: Int, val accentAlt: Int)

    /**
     * Six grounds, one per roster slot.
     *
     * All dark and all differently HUED — a set that varied only in brightness
     * would make the six cards look like six crops of one image, which defeats
     * the point of a roster. Deliberately NOT the brand lime: lime is this
     * system's single "do the positive thing" signal, and a cover that carries
     * it competes with the Book button sitting on top of it.
     */
    private val PALETTES = listOf(
        Palette(0xFF1B2A4A.toInt(), 0xFF07090F.toInt(), 0xFF3E6FD6.toInt(), 0xFF7A4FD0.toInt()),
        Palette(0xFF3A1730.toInt(), 0xFF0B060A.toInt(), 0xFFD1477F.toInt(), 0xFFE0764A.toInt()),
        Palette(0xFF10322E.toInt(), 0xFF050B0A.toInt(), 0xFF2FA88C.toInt(), 0xFF4FC3D9.toInt()),
        Palette(0xFF3B2410.toInt(), 0xFF0C0704.toInt(), 0xFFE0A046.toInt(), 0xFFCB5B2E.toInt()),
        Palette(0xFF241B3D.toInt(), 0xFF08060D.toInt(), 0xFF8C6BE8.toInt(), 0xFF4A83E8.toInt()),
        Palette(0xFF2C1414.toInt(), 0xFF0A0505.toInt(), 0xFFCF5252.toInt(), 0xFFE09A5E.toInt()),
    )
}
