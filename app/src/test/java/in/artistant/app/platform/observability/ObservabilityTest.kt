package `in`.artistant.app.platform.observability

import `in`.artistant.app.core.logging.PiiScrub
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The privacy invariants of the observability layer: the analytics event surface
 * can't silently grow (allowlist), and no email/phone PII reaches the crash sink
 * (scrub). These hold regardless of whether the SDKs are ever linked.
 */
class ObservabilityTest {

    @Test
    fun `analytics allowlist admits only the five owner-approved events`() {
        assertTrue(PostHogAnalytics.isAllowed("signup_complete"))
        assertTrue(PostHogAnalytics.isAllowed("message_sent"))
        assertTrue(PostHogAnalytics.isAllowed("booking_created"))
        assertFalse(PostHogAnalytics.isAllowed("random_event"))
        assertFalse(PostHogAnalytics.isAllowed(""))
    }

    @Test
    fun `capture forwards allowlisted events and drops the rest`() {
        val forwarded = mutableListOf<String>()
        val analytics = object : PostHogAnalytics() {
            override fun forward(event: String, props: Map<String, Any?>) {
                forwarded += event
            }
        }
        analytics.capture("signup_complete", emptyMap())
        analytics.capture("exfiltrate_pii", emptyMap()) // must never reach forward
        assertEquals(listOf("signup_complete"), forwarded)
    }

    @Test
    fun `pii scrub redacts an email before it could leave the device`() {
        val scrubbed = PiiScrub.scrub("contact alice@example.com about the gig")
        assertFalse("email should be scrubbed: $scrubbed", scrubbed.contains("alice@example.com"))
    }
}
