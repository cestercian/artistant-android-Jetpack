package `in`.artistant.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two decisions on the DPDP §11 path, pinned.
 *
 * Both used to live inline against a `client.functions.invoke` call with private
 * nested response types, so nothing in this suite could reach them — the
 * highest-stakes untested code in the app: one decides whether an erasure
 * actually happened, the other whether the user gets their data or a dead link.
 * `AccountErasureTest` only ever exercised the fake's counters, which is why the
 * gap survived the audit's own test wave.
 */
class AccountResponseDecodeTest {

    // ── requireDeleted ──────────────────────────────────────────────────────

    @Test
    fun `a deleted-true body is accepted`() {
        requireDeleted("""{"deleted":true}""")
    }

    @Test
    fun `unknown keys do not stop a valid answer being read`() {
        // The function is free to grow its envelope; that must not read as a
        // failed erasure.
        requireDeleted("""{"deleted":true,"purged_rows":42,"request_id":"abc"}""")
    }

    @Test
    fun `deleted-false is a refusal, not a success`() {
        // The function answers 200 either way, so the status code is not the
        // answer. Telling someone their data is gone when it is not would be the
        // worst failure this path has.
        val e = assertThrows(AccountRepositoryError.DeleteFailed::class.java) {
            requireDeleted("""{"deleted":false}""")
        }
        assertTrue(e.message!!.contains("deleted=false"))
    }

    @Test
    fun `an unreadable body fails closed rather than passing`() {
        // A proxy error page, an empty body, or a renamed field: none of these
        // are proof of an erasure, so none of them may be read as one.
        listOf("", "not json", "<html>502</html>", """{"ok":true}""").forEach { body ->
            assertThrows(
                "\"$body\" must not read as a completed deletion",
                AccountRepositoryError.DeleteFailed::class.java,
            ) { requireDeleted(body) }
        }
    }

    // ── parseExportResponse ─────────────────────────────────────────────────

    @Test
    fun `a signed-url envelope yields the url and its expiry`() {
        val result = parseExportResponse(
            """{"mode":"signed_url","url":"https://x/exports/a.json","expires_in_seconds":900}""",
        )

        assertEquals(ExportResult.SignedUrl("https://x/exports/a.json", 900), result)
    }

    @Test
    fun `a signed-url envelope without an expiry falls back to an hour`() {
        val result = parseExportResponse("""{"mode":"signed_url","url":"https://x/e.json"}""")

        assertEquals(ExportResult.SignedUrl("https://x/e.json", 3600), result)
    }

    @Test
    fun `a signed-url envelope with a blank url is a server bug, not an export`() {
        // Handing this back would open nothing and still report success.
        assertThrows(AccountRepositoryError.Underlying::class.java) {
            parseExportResponse("""{"mode":"signed_url","url":""}""")
        }
    }

    @Test
    fun `anything that is not a signed-url envelope IS the export`() {
        val body = """{"user":{"id":"u1"},"bookings":[],"messages":[]}"""

        assertEquals(ExportResult.Inline(body), parseExportResponse(body))
    }

    @Test
    fun `an inline export that happens to carry a mode is still inline`() {
        // Shape-guessing would misread this; the envelope's own `mode` decides.
        val body = """{"mode":"full","user":{"id":"u1"}}"""

        assertEquals(ExportResult.Inline(body), parseExportResponse(body))
    }

    @Test
    fun `a body that is not JSON at all is returned inline rather than throwing`() {
        // The export is the user's data; refusing to hand it over because it did
        // not parse as an envelope would lose it for no reason.
        assertEquals(ExportResult.Inline("plain text export"), parseExportResponse("plain text export"))
    }
}
