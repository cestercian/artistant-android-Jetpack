package `in`.artistant.app.data.repository

import `in`.artistant.app.core.result.AppError
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The by-id artist cache has to remember MISSES, not only hits.
 *
 * `fetchArtist` short-circuits a hit on the hydrated set, but a lookup that
 * legitimately resolves to no row used to record nothing — so it re-ran the
 * five-table profile fan-out on every single call. That is not a hypothetical
 * path: Blocked accounts asks for exactly those ids on purpose (a blocked CLIENT
 * id is never in `artists`, and an artist row is the one public place a block can
 * be given a name), and it re-asks on every pull-to-refresh. Five round trips per
 * blocked client, per refresh, each guaranteed to come back empty.
 *
 * The counter these assert through ([FakeArtistsRepository.fetchedIds]) is the
 * only way to see it: a miss answers null either way.
 */
class ArtistsRepositoryLogicTest {

    @Test
    fun aConfirmedMissIsAskedForOnce_notOncePerCall() = runTest {
        val repo = FakeArtistsRepository()

        assertNull(repo.ensureFull(NOT_AN_ARTIST))
        assertNull(repo.ensureFull(NOT_AN_ARTIST))
        assertNull(repo.ensureFull(NOT_AN_ARTIST))

        assertEquals(listOf(NOT_AN_ARTIST), repo.fetchedIds)
    }

    @Test
    fun aHydratedArtistIsAlsoAskedForOnce() = runTest {
        val repo = FakeArtistsRepository(remote = listOf(FakeArtistsRepository.sample(id = ARTIST)))

        assertEquals(ARTIST, repo.ensureFull(ARTIST)?.id)
        assertEquals(ARTIST, repo.ensureFull(ARTIST)?.id)

        assertEquals(listOf(ARTIST), repo.fetchedIds)
    }

    /**
     * A memoized miss must not outlive the fact behind it. A tile projection
     * carrying the id is proof the row exists now — an artist published between
     * the two asks — so the next `ensureFull` has to go and get them.
     */
    @Test
    fun cachingATileForAMissedIdRetiresTheMiss() = runTest {
        val repo = FakeArtistsRepository()
        assertNull(repo.ensureFull(ARTIST))
        assertEquals(listOf(ARTIST), repo.fetchedIds)

        // A Discover/Search page lands the artist as a tile projection — proof
        // the row exists now, whatever the earlier ask found. What that buys is
        // the RE-ASK: the miss no longer short-circuits, so the next fetch
        // reaches the server again.
        //
        // It does not itself answer the fetch. This test used to assert the tile
        // came back, and that is precisely the drift the profile screen rendered
        // as a hydrated half-artist — empty pricing, samples and rider presented
        // as fact, unrecoverable because hydration short-circuits. The real
        // repository stitches five tables here and never returns a tile.
        repo.cache(listOf(FakeArtistsRepository.sample(id = ARTIST)))
        repo.ensureFull(ARTIST)

        assertEquals(listOf(ARTIST, ARTIST), repo.fetchedIds)
    }

    @Test
    fun cache_neverDowngradesAHydratedArtist() = runTest {
        val full = FakeArtistsRepository.sample(id = "a1", name = "Full Name").copy(bio = "full bio")
        val repo = FakeArtistsRepository(seed = listOf(full))
        repo.cache(listOf(FakeArtistsRepository.sample(id = "a1", name = "Partial").copy(bio = "")))
        assertEquals("Full Name", repo.find("a1")?.name)
        assertEquals("full bio", repo.find("a1")?.bio)
    }

    @Test
    fun ensureFull_returnsNullOnFailure() = runTest {
        val repo = FakeArtistsRepository(seed = listOf(FakeArtistsRepository.sample()))
        repo.failFetch = true
        assertNull(repo.ensureFull("a1"))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Self-row edits — mutateSelf/updateAvailability/setPublished used to
    // resolve their target as `byId.keys.firstOrNull()`, insertion order
    // rather than identity, so a multi-artist fake could have a press-kit
    // save land on the WRONG artist while "my edit is visible on the next
    // read" still passed.
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun aSelfRowEditOnAMultiArtistFakeWithNoSelfIdRefusesRatherThanGuess() = runTest {
        val repo = FakeArtistsRepository(
            seed = listOf(
                FakeArtistsRepository.sample(id = ARTIST),
                FakeArtistsRepository.sample(id = OTHER_ARTIST),
            ),
        )

        val err = runCatching { repo.updateBio(ARTIST, "new bio") }.exceptionOrNull()

        assertTrue("expected NotFoundOrUnauthorized, got $err", err is AppError.NotFoundOrUnauthorized)
        // Neither row moved — the old bug wrote silently to whichever key came first.
        assertEquals("Live sets for rooftops and weddings.", repo.find(ARTIST)?.bio)
        assertEquals("Live sets for rooftops and weddings.", repo.find(OTHER_ARTIST)?.bio)
    }

    @Test
    fun aSelfRowEditTargetsTheNamedSelfId_notWhicheverArtistWasSeededFirst() = runTest {
        val repo = FakeArtistsRepository(
            seed = listOf(
                FakeArtistsRepository.sample(id = ARTIST),
                FakeArtistsRepository.sample(id = OTHER_ARTIST),
            ),
            selfId = OTHER_ARTIST,
        )

        repo.updateBio(OTHER_ARTIST, "new bio")

        assertEquals("Live sets for rooftops and weddings.", repo.find(ARTIST)?.bio)
        assertEquals("new bio", repo.find(OTHER_ARTIST)?.bio)
    }

    @Test
    fun setPublished_refusesAnArtistIdThatIsNotSelf() = runTest {
        val repo = FakeArtistsRepository(seed = listOf(FakeArtistsRepository.sample(id = ARTIST)))

        val err = runCatching { repo.setPublished(OTHER_ARTIST, published = true) }.exceptionOrNull()

        assertTrue("expected IllegalArgumentException, got $err", err is IllegalArgumentException)
        assertFalse(repo.published)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // …and the same edits must also target the account they were COMPOSED for.
    //
    // "Self" was resolved from the live session at execution time and compared
    // to nothing, which only holds while composing and executing happen under one
    // session. The press-kit editor breaks that on purpose: it flushes its owed
    // saves from a scope that outlives the screen, so a save typed by one artist
    // could run after somebody else signed in on the same device and PATCH THEIR
    // public row — a write the server has no reason to refuse, since the JWT is
    // the new user's and the row is theirs. Every narrow edit now names the
    // account it was built for and the seam refuses the mismatch, in the same
    // `require`/IllegalArgumentException family as the two guards above.
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun aSelfRowEditComposedForTheSignedInOwnerLands() = runTest {
        val repo = FakeArtistsRepository(
            seed = listOf(FakeArtistsRepository.sample(id = HEX_ARTIST)),
            selfId = HEX_ARTIST,
        )

        // Handed in UPPERCASE on purpose (which is why this id carries hex
        // letters and the others do not). Ids reach a caller from the session,
        // from a decoded row and from a deep link, and this repo's standing rule
        // is that a UUID is compared lowercased — a guard that tripped on case
        // would refuse an artist's own edit on whichever path skips the
        // normalisation, which is a worse bug than the one being fixed.
        repo.updateBio(HEX_ARTIST.uppercase(), "new bio")

        assertEquals("new bio", repo.find(HEX_ARTIST)?.bio)
    }

    @Test
    fun aSelfRowEditComposedForAnotherAccountIsRefused_bothDirections() = runTest {
        // Signed in as ARTIST, with a draft composed under OTHER_ARTIST: the
        // sign-out/sign-in case, where the previous artist's owed save arrives
        // holding the new user's session.
        val signedInAsArtist = FakeArtistsRepository(
            seed = listOf(
                FakeArtistsRepository.sample(id = ARTIST),
                FakeArtistsRepository.sample(id = OTHER_ARTIST),
            ),
            selfId = ARTIST,
        )

        val err = runCatching {
            signedInAsArtist.updateBio(OTHER_ARTIST, "composed elsewhere")
        }.exceptionOrNull()

        assertTrue("expected IllegalArgumentException, got $err", err is IllegalArgumentException)
        // Neither row: not the signed-in one (which would be the exploit — the
        // previous artist's words on this account's public profile) and not the
        // one the draft named (which RLS would have refused anyway).
        assertEquals("Live sets for rooftops and weddings.", signedInAsArtist.find(ARTIST)?.bio)
        assertEquals("Live sets for rooftops and weddings.", signedInAsArtist.find(OTHER_ARTIST)?.bio)

        // And the mirror image, which is the same bug seen from the other seat.
        val signedInAsOther = FakeArtistsRepository(
            seed = listOf(
                FakeArtistsRepository.sample(id = ARTIST),
                FakeArtistsRepository.sample(id = OTHER_ARTIST),
            ),
            selfId = OTHER_ARTIST,
        )

        val mirrored = runCatching {
            signedInAsOther.updateBio(ARTIST, "composed elsewhere")
        }.exceptionOrNull()

        assertTrue("expected IllegalArgumentException, got $mirrored", mirrored is IllegalArgumentException)
        assertEquals("Live sets for rooftops and weddings.", signedInAsOther.find(ARTIST)?.bio)
        assertEquals("Live sets for rooftops and weddings.", signedInAsOther.find(OTHER_ARTIST)?.bio)
    }

    /**
     * The guard covers the whole narrow-edit family, not the one method a test
     * happened to pick — it lives in the single write path they all share.
     *
     * Pinned through the socials write because it is the costliest one to get
     * wrong: it replaces all three link columns every time, so a cross-account
     * landing would overwrite the victim's accounts AND publish the composer's
     * under the victim's name.
     */
    @Test
    fun theWholeSetSocialWriteIsGuardedTheSameWay() = runTest {
        val repo = FakeArtistsRepository(
            seed = listOf(
                FakeArtistsRepository.sample(id = ARTIST)
                    .copy(instagramHandle = "theirhandle"),
                FakeArtistsRepository.sample(id = OTHER_ARTIST),
            ),
            selfId = ARTIST,
        )

        val err = runCatching {
            repo.updateSocialLinks(
                expectedOwner = OTHER_ARTIST,
                instagram = "myhandle",
                spotify = null,
                youtube = null,
            )
        }.exceptionOrNull()

        assertTrue("expected IllegalArgumentException, got $err", err is IllegalArgumentException)
        assertEquals("theirhandle", repo.find(ARTIST)?.instagramHandle)
        assertNull(repo.find(OTHER_ARTIST)?.instagramHandle)
    }

    /**
     * `updateAvailability` is in the same family: it resolved "self" from the
     * session at execution and compared it to nothing, so an availability edit
     * composed under one artist could land on another. It now names the account
     * it was composed for, and refuses the mismatch in the same guard.
     */
    @Test
    fun updateAvailability_composedForSelf_lands() = runTest {
        val repo = FakeArtistsRepository(
            seed = listOf(FakeArtistsRepository.sample(id = ARTIST)),
            selfId = ARTIST,
        )

        repo.updateAvailability(ARTIST, daysAvailable = listOf("Mon"), timeSlots = listOf("Evening"))

        assertEquals(listOf("Mon"), repo.find(ARTIST)?.daysAvailable)
        assertEquals(listOf("Evening"), repo.find(ARTIST)?.timeSlots)
    }

    @Test
    fun updateAvailability_composedForAnotherAccount_refused() = runTest {
        val repo = FakeArtistsRepository(
            seed = listOf(
                FakeArtistsRepository.sample(id = ARTIST),
                FakeArtistsRepository.sample(id = OTHER_ARTIST),
            ),
            selfId = ARTIST,
        )

        val err = runCatching {
            repo.updateAvailability(OTHER_ARTIST, daysAvailable = listOf("Mon"), timeSlots = listOf("Evening"))
        }.exceptionOrNull()

        assertTrue("expected IllegalArgumentException, got $err", err is IllegalArgumentException)
        // Neither row's availability moved — not the signed-in one (the exploit)
        // and not the one the draft named (which RLS would refuse anyway).
        assertTrue(repo.find(ARTIST)?.daysAvailable.isNullOrEmpty())
        assertTrue(repo.find(OTHER_ARTIST)?.daysAvailable.isNullOrEmpty())
    }

    private companion object {
        const val ARTIST = "11111111-1111-1111-1111-111111111111"
        const val OTHER_ARTIST = "22222222-2222-2222-2222-222222222222"
        const val NOT_AN_ARTIST = "33333333-3333-3333-3333-333333333333"

        /** All-digit ids `uppercase()` to themselves — this one can actually change case. */
        const val HEX_ARTIST = "aabbccdd-1111-2222-3333-444455556666"
    }

    /**
     * A cached tile is not an answer to a profile fetch.
     *
     * `cache()` stores the compact projection a search or browse row carries. The
     * real repository never returns that from `fetchArtist` — it stitches five
     * tables and produces something strictly richer — so a fake that answered
     * from the tile and then marked it hydrated made every omitted field render
     * as genuine empty data, unrecoverable because hydration short-circuits.
     */
    @Test
    fun `a cached tile does not satisfy a profile fetch`() = runTest {
        val tile = FakeArtistsRepository.sample(id = ARTIST, name = "Kaavya")
        val repo = FakeArtistsRepository()
        repo.cache(listOf(tile))

        // The tile is good enough for the by-id cache the rails read...
        assertEquals("Kaavya", repo.find(ARTIST)?.name)
        // ...but not for the profile, which this fake cannot answer for.
        assertNull(repo.fetchArtist(ARTIST))
    }

    @Test
    fun `a remotely seeded row answers the fetch a tile could not`() = runTest {
        val full = FakeArtistsRepository.sample(id = ARTIST, name = "Kaavya")
        val repo = FakeArtistsRepository(remote = listOf(full))
        repo.cache(listOf(full.copy(name = "stale tile")))

        val fetched = repo.fetchArtist(ARTIST)

        // The server's row wins over the tile, and it is hydrated afterwards.
        assertEquals("Kaavya", fetched?.name)
        assertEquals("Kaavya", repo.find(ARTIST)?.name)
    }
}
