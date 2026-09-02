package `in`.artistant.app.data.repository

import `in`.artistant.app.core.result.AppError
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-logic coverage for packages / tech replace drafts used by wizard publish. */
class WizardPublishReposLogicTest {
    @Test
    fun packageDraft_mapsToReplacePayloadFields() = runTest {
        // selfId names the account these drafts were composed for; replaceAll
        // refuses a write aimed anywhere else (see the guard tests below).
        val fake = FakePackagesRepository(selfId = SELF)
        fake.replaceAll(
            SELF,
            listOf(
                PackageDraft(
                    name = "Evening set",
                    durationLabel = "2h",
                    priceInr = 25_000,
                    popular = true,
                ),
            ),
        )
        val listed = fake.list(SELF)
        assertEquals(1, listed.size)
        assertEquals("Evening set", listed[0].name)
        assertEquals("2h", listed[0].duration)
        assertEquals(25_000, listed[0].price)
        assertTrue(listed[0].popular)
    }

    @Test
    fun techReplace_trimsAndDropsEmpty() = runTest {
        val fake = FakeTechRiderRepository(selfId = SELF)
        fake.replaceAll(SELF, listOf("  Mic  ", "", "DI box"))
        assertEquals(listOf("Mic", "DI box"), fake.list(SELF))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // The replace seams carry the same composed-for guard `patchSelf` does.
    //
    // Both persist by wiping and re-inserting the whole set for one artist, and
    // the EPK editor flushes owed saves from a scope that outlives the screen
    // (`EpkViewModel.onCleared`). Without the guard a replace composed under one
    // artist could run after another signed in on the same device and land this
    // artist's pricing or rider on the new user's public row — a write the server
    // accepts, since the JWT is theirs and the row is theirs. The fakes model the
    // session as `selfId` and refuse the mismatch in the same
    // `require`/IllegalArgumentException family.
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun packagesReplace_composedForSelf_lands() = runTest {
        val fake = FakePackagesRepository(selfId = SELF)
        fake.replaceAll(SELF, listOf(PackageDraft(name = "Set", durationLabel = "2h", priceInr = 10_000)))
        assertEquals(1, fake.list(SELF).size)
    }

    @Test
    fun packagesReplace_composedForAnotherAccount_refused() = runTest {
        val fake = FakePackagesRepository(selfId = SELF)

        val err = runCatching {
            fake.replaceAll(OTHER, listOf(PackageDraft(name = "Set", durationLabel = "2h", priceInr = 10_000)))
        }.exceptionOrNull()

        assertTrue("expected IllegalArgumentException, got $err", err is IllegalArgumentException)
        // Neither the victim's row nor the composer's was written.
        assertTrue(fake.list(OTHER).isEmpty())
        assertTrue(fake.list(SELF).isEmpty())
    }

    @Test
    fun packagesReplace_withNoSession_isUnauthorized() = runTest {
        // selfId unset models "no session" — the read the real repo does before
        // its require finds nothing to check against.
        val fake = FakePackagesRepository()

        val err = runCatching {
            fake.replaceAll(SELF, listOf(PackageDraft(name = "Set", durationLabel = "2h", priceInr = 10_000)))
        }.exceptionOrNull()

        assertTrue("expected NotFoundOrUnauthorized, got $err", err is AppError.NotFoundOrUnauthorized)
    }

    @Test
    fun techReplace_composedForSelf_lands() = runTest {
        val fake = FakeTechRiderRepository(selfId = SELF)
        fake.replaceAll(SELF, listOf("Mic"))
        assertEquals(listOf("Mic"), fake.list(SELF))
    }

    @Test
    fun techReplace_composedForAnotherAccount_refused() = runTest {
        val fake = FakeTechRiderRepository(selfId = SELF)

        val err = runCatching { fake.replaceAll(OTHER, listOf("Mic")) }.exceptionOrNull()

        assertTrue("expected IllegalArgumentException, got $err", err is IllegalArgumentException)
        assertTrue(fake.list(OTHER).isEmpty())
        assertTrue(fake.list(SELF).isEmpty())
    }

    @Test
    fun samplesFormatDuration_padsSeconds() {
        assertEquals("0:05", SupabaseSamplesRepository.formatDuration(5.0))
        assertEquals("1:30", SupabaseSamplesRepository.formatDuration(90.0))
    }

    private companion object {
        const val SELF = "artist-1"
        const val OTHER = "artist-2"
    }
}
