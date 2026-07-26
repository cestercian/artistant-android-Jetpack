package `in`.artistant.app.data.repository

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-logic coverage for packages / tech replace drafts used by wizard publish. */
class WizardPublishReposLogicTest {
    @Test
    fun packageDraft_mapsToReplacePayloadFields() = runTest {
        val fake = FakePackagesRepository()
        fake.replaceAll(
            "artist-1",
            listOf(
                PackageDraft(
                    name = "Evening set",
                    durationLabel = "2h",
                    priceInr = 25_000,
                    popular = true,
                ),
            ),
        )
        val listed = fake.list("artist-1")
        assertEquals(1, listed.size)
        assertEquals("Evening set", listed[0].name)
        assertEquals("2h", listed[0].duration)
        assertEquals(25_000, listed[0].price)
        assertTrue(listed[0].popular)
    }

    @Test
    fun techReplace_trimsAndDropsEmpty() = runTest {
        val fake = FakeTechRiderRepository()
        fake.replaceAll("artist-1", listOf("  Mic  ", "", "DI box"))
        assertEquals(listOf("Mic", "DI box"), fake.list("artist-1"))
    }

    @Test
    fun samplesFormatDuration_padsSeconds() {
        assertEquals("0:05", SupabaseSamplesRepository.formatDuration(5.0))
        assertEquals("1:30", SupabaseSamplesRepository.formatDuration(90.0))
    }
}
