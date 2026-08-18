package `in`.artistant.app.platform.observability

import `in`.artistant.app.core.logging.PiiScrub
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The privacy invariants of the observability layer: the analytics event surface
 * can't silently grow (allowlist), and no email/phone PII reaches the crash sink
 * (scrub). These hold regardless of whether the SDKs are ever linked.
 */
class ObservabilityTest {

    @Test
    fun `analytics allowlist admits only the four owner-approved funnel events`() {
        // app_open is the launch emit (ArtistantApplication.onCreate) — if it ever fell off
        // the allowlist, capture() would silently drop the funnel denominator.
        assertTrue(PostHogAnalytics.isAllowed("app_open"))
        assertTrue(PostHogAnalytics.isAllowed("signup_complete"))
        assertTrue(PostHogAnalytics.isAllowed("message_sent"))
        assertTrue(PostHogAnalytics.isAllowed("booking_created"))
        assertFalse(PostHogAnalytics.isAllowed("random_event"))
        assertFalse(PostHogAnalytics.isAllowed(""))
        assertEquals(4, PostHogAnalytics.ALLOWED_EVENTS.size)
    }

    @Test
    fun `the payments-era booking_paid event is not part of the no-payments v1 surface`() {
        // v1 is the matchmaker (request → accept); nothing can be paid, so an emitter for
        // this could only be a copy-paste from the payments era. iOS deleted it too.
        assertFalse(PostHogAnalytics.isAllowed("booking_paid"))
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

    /**
     * The leak this pins: `record` used to scrub only `throwable.message` and hand the RAW
     * throwable on, and the PII in a wrapped repository failure lives in the CAUSE — so the
     * `captureException` wiring the class prescribes would have uploaded the address verbatim.
     */
    @Test
    fun `record scrubs every message in the cause chain, not just the top one`() {
        val sent = mutableListOf<Throwable>()
        val crash = object : SentryCrash() {
            override fun forward(scrubbed: Throwable) {
                sent += scrubbed
            }
        }
        val cause = IllegalStateException("PostgREST 409: key (email)=(alice@example.com) exists")
        val original = RuntimeException("booking save failed for +919876543210", cause)

        crash.record(original)

        val chain = generateSequence(sent.single()) { it.cause }.toList()
        assertEquals("both links have to reach the send point", 2, chain.size)
        val text = chain.joinToString("\n") { it.message.orEmpty() }
        assertFalse("cause email must be redacted: $text", text.contains("alice@example.com"))
        assertFalse("top-level phone must be redacted: $text", text.contains("9876543210"))
        assertTrue(text.contains("[REDACTED:EMAIL]"))
        assertTrue(text.contains("[REDACTED:PHONE]"))
    }

    @Test
    fun `record hands the send point a twin, keeping the type and frames for grouping`() {
        val sent = mutableListOf<Throwable>()
        val crash = object : SentryCrash() {
            override fun forward(scrubbed: Throwable) {
                sent += scrubbed
            }
        }
        val cause = IllegalStateException("inner")
        val original = RuntimeException("outer", cause)

        crash.record(original)

        val chain = generateSequence(sent.single()) { it.cause }.toList()
        // Nothing the SDK could serialise un-scrubbed off the original ever reaches it.
        assertTrue(
            "the original objects must not be forwarded",
            chain.none { it === original || it === cause },
        )
        // What a crash dashboard needs — exception type + top frame — survives the copy.
        assertTrue(chain[0].message!!.startsWith("java.lang.RuntimeException"))
        assertTrue(chain[1].message!!.startsWith("java.lang.IllegalStateException"))
        assertEquals(original.stackTrace.first(), chain[0].stackTrace.first())
    }

    @Test
    fun `a cyclic cause chain is walked once instead of forever`() {
        val outer = IllegalStateException("outer")
        val inner = IllegalStateException("inner")
        outer.initCause(inner)
        inner.initCause(outer) // legal to build, fatal to a naive walk

        val sent = mutableListOf<Throwable>()
        val crash = object : SentryCrash() {
            override fun forward(scrubbed: Throwable) {
                sent += scrubbed
            }
        }
        crash.record(outer)

        assertEquals(2, generateSequence(sent.single()) { it.cause }.count())
    }

    /** DPDP §11: sign-out has to drop the crash-report user association, not just stop sending. */
    @Test
    fun `setUser attaches the opaque id and null clears it on sign-out`() {
        val crash = SentryCrash()
        crash.setUser("3f6b1c8e-0a2d-4f7b-9c1e-5d8a2b4c6e10")
        assertEquals("3f6b1c8e-0a2d-4f7b-9c1e-5d8a2b4c6e10", crash.attachedUserId)
        crash.setUser(null)
        assertNull(crash.attachedUserId)
    }
}
