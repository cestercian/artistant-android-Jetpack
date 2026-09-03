package `in`.artistant.app.data.repository

import `in`.artistant.app.domain.artist.PackagePricing
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What `Artist.price` means, and who has to agree about it.
 *
 * The field is a **"from" price — the minimum over the artist's tiers**. That is
 * not an opinion held by one screen: the tile renders it directly under a "from"
 * framing, the profile dock uses it as the fallback when no package list has
 * loaded, and the search projection fills it from the server's `min_price`
 * column. Two code paths populate the same field, and they must mean the same
 * thing by it or the app quotes two different prices for one artist.
 *
 * The full-stitch path did not. It took the price of the *popular* package —
 * falling back to the *first* — which is an ordering fact, not a pricing one.
 * An artist whose headline tier is their most expensive (an ordinary way to sell:
 * lead with the full band, keep a cheap acoustic set below) had every surface
 * backed by that field advertising their dearest tier as their "from" price.
 * These tests pin the contract at the seam that builds it.
 */
class ArtistHeadlinePriceTest {

    private fun dbArtist(id: String = ARTIST) = DbArtist(
        id = id,
        handle = "vdfdfdsf",
        stageName = "Vdfdfdsf",
        category = "Indie Band",
        baseCity = "Hyderabad",
    )

    private fun dbPackage(
        id: String,
        name: String,
        priceInr: Int,
        durationLabel: String,
        popular: Boolean = false,
        artistId: String = ARTIST,
    ) = DbPackage(
        id = id,
        artistId = artistId,
        name = name,
        durationLabel = durationLabel,
        priceInr = priceInr,
        popular = popular,
    )

    private fun stitchOne(packages: List<DbPackage>) =
        SupabaseArtistsRepository.stitch(
            artists = listOf(dbArtist()),
            packages = packages,
            tech = emptyList(),
            samples = emptyList(),
            photos = emptyList(),
        ).single()

    /**
     * The exact shape that surfaced this: the dearest tier is both first in
     * position order and the one flagged popular, so "popular", "first" and
     * "cheapest" all point at different rows.
     */
    private fun dearestTierIsTheHeadline() = listOf(
        dbPackage("p0", "Acoustic Trio", 51_000, "45 min", popular = true),
        dbPackage("p1", "Full Band", 22_000, "90 min"),
    )

    @Test
    fun headlinePriceIsTheCheapestTier_notThePopularOne() {
        val artist = stitchOne(dearestTierIsTheHeadline())

        assertEquals(22_000, artist.price)
    }

    @Test
    fun headlinePriceIsTheCheapestTier_notTheFirstOne() {
        // Nothing flagged popular, so the old rule fell through to `first()`.
        val artist = stitchOne(
            listOf(
                dbPackage("p0", "Acoustic Trio", 51_000, "45 min"),
                dbPackage("p1", "Full Band", 22_000, "90 min"),
            ),
        )

        assertEquals(22_000, artist.price)
    }

    /**
     * The headline duration has to describe the same tier as the headline price.
     * Quoting one package's price beside another's duration is a subtler lie than
     * quoting the wrong number, because nothing on screen looks wrong.
     */
    @Test
    fun headlineDurationDescribesTheSameTierAsTheHeadlinePrice() {
        val artist = stitchOne(dearestTierIsTheHeadline())

        assertEquals("90 min", artist.duration)
    }

    /**
     * The whole point of the contract: the value baked into the artist row and
     * the value computed live from the loaded packages must be the same number,
     * or the dock and the page disagree the moment one of them falls back.
     */
    @Test
    fun theBakedInPriceAgreesWithTheLivePackageMinimum() {
        val artist = stitchOne(dearestTierIsTheHeadline())

        assertEquals(
            PackagePricing.fromPrice(artist.packages, fallback = artist.price),
            artist.price,
        )
    }

    @Test
    fun aSingleTierIsItsOwnMinimum() {
        val artist = stitchOne(listOf(dbPackage("p0", "Full Band", 22_000, "90 min", popular = true)))

        assertEquals(22_000, artist.price)
        assertEquals("90 min", artist.duration)
    }

    @Test
    fun anArtistWithNoPublishedTiersHasNoHeadlinePrice() {
        val artist = stitchOne(emptyList())

        assertEquals(0, artist.price)
        assertEquals("set", artist.duration)
    }

    /**
     * `stitch` fans several artists out of one query, so the minimum has to be
     * taken per artist — a global min would price everyone at the cheapest tier
     * on the page.
     */
    @Test
    fun eachArtistIsPricedFromItsOwnTiers() {
        val other = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
        val stitched = SupabaseArtistsRepository.stitch(
            artists = listOf(dbArtist(), dbArtist(id = other)),
            packages = dearestTierIsTheHeadline() +
                dbPackage("q0", "Duo", 80_000, "60 min", artistId = other),
            tech = emptyList(),
            samples = emptyList(),
            photos = emptyList(),
        )

        assertEquals(22_000, stitched.first { it.id == ARTIST }.price)
        assertEquals(80_000, stitched.first { it.id == other }.price)
    }

    private companion object {
        const val ARTIST = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
    }
}
