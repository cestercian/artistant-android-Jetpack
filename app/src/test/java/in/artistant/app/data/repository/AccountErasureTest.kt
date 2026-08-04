package `in`.artistant.app.data.repository

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DPDP §11 / Apple 5.1.1(v) erasure seam.
 *
 * The ordering rule lives in ProfileViewModel: call the server FIRST, wipe
 * local state ONLY on success. That orchestration isn't reachable from a JVM
 * test (it needs SessionManager + a DataStore-backed AppPreferences, both of
 * which need a real Android Context), so what's pinned here is the seam it
 * depends on: a failed delete must THROW — a repository that swallowed the
 * failure would make the "only on success" branch fire on a live account.
 */
class AccountErasureTest {

    @Test
    fun aSuccessfulDeleteReturnsNormallyAndIsRecorded() = runTest {
        val account = FakeAccountRepository()

        account.deleteAccount()

        assertEquals(1, account.deleteCallCount)
    }

    @Test
    fun aFailedDeleteThrows_soCallersCannotWipeLocalStateByAccident() = runTest {
        val account = FakeAccountRepository(failDelete = true)

        val error = runCatching { account.deleteAccount() }.exceptionOrNull()

        assertTrue(
            "delete failure must be observable, not swallowed",
            error is AccountRepositoryError.DeleteFailed,
        )
        assertEquals(1, account.deleteCallCount)
    }

    @Test
    fun aDeleteFailureCarriesAUserFacingMessage() = runTest {
        val account = FakeAccountRepository(failDelete = true)

        val error = runCatching { account.deleteAccount() }.exceptionOrNull()

        assertNotNull(error?.message)
        assertTrue(error!!.message!!.contains("Account deletion failed"))
    }

    @Test
    fun retryingAfterAFailureStillReachesTheServer() = runTest {
        val account = FakeAccountRepository(failDelete = true)
        runCatching { account.deleteAccount() }

        account.failDelete = false
        account.deleteAccount()

        assertEquals(2, account.deleteCallCount)
    }

    // --- export -------------------------------------------------------------

    @Test
    fun aSmallExportComesBackInline() = runTest {
        val account = FakeAccountRepository(exportResult = ExportResult.Inline("""{"user":"x"}"""))

        val result = account.requestDataExport()

        assertTrue(result is ExportResult.Inline)
        assertEquals("""{"user":"x"}""", (result as ExportResult.Inline).json)
        assertEquals(1, account.exportCallCount)
    }

    @Test
    fun aLargeExportComesBackAsAnExpiringSignedUrl() = runTest {
        val account = FakeAccountRepository(
            exportResult = ExportResult.SignedUrl("https://example.test/dump.json", expiresInSeconds = 3_600),
        )

        val result = account.requestDataExport()

        assertTrue(result is ExportResult.SignedUrl)
        assertEquals(3_600, (result as ExportResult.SignedUrl).expiresInSeconds)
    }

    @Test
    fun aFailedExportThrowsRatherThanReturningAnEmptyDump() = runTest {
        val account = FakeAccountRepository(failExport = true)

        val error = runCatching { account.requestDataExport() }.exceptionOrNull()

        assertTrue(error is AccountRepositoryError)
    }
}
