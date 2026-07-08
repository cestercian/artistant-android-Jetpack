package `in`.artistant.app.platform.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit checks for [fragmentParam], the pure fragment parser behind the implicit-flow
 * `#error=…&error_description=…` OAuth deep-link return. A regression here means a provider
 * denial or failure sent via the URL fragment is misread — either wedging the auth screen
 * silently (the exact bug #12) or showing a garbled/percent-escaped message.
 */
class DeepLinkErrorTest {

    @Test
    fun `extracts error from a multi-key fragment`() {
        assertEquals(
            "access_denied",
            fragmentParam("error=access_denied&error_description=nope", "error"),
        )
    }

    @Test
    fun `percent-decodes an error_description escape to a space`() {
        assertEquals(
            "user cancelled",
            fragmentParam("error=x&error_description=user%20cancelled", "error_description"),
        )
    }

    @Test
    fun `decodes a plus to a space`() {
        assertEquals(
            "user cancelled",
            fragmentParam("error_description=user+cancelled", "error_description"),
        )
    }

    @Test
    fun `absent key returns null`() {
        assertNull(fragmentParam("error=access_denied", "code"))
    }

    @Test
    fun `null fragment returns null`() {
        assertNull(fragmentParam(null, "error"))
    }

    @Test
    fun `empty value returns null`() {
        assertNull(fragmentParam("error=", "error"))
    }

    @Test
    fun `picks the correct key among several`() {
        val fragment = "state=abc&error=server_error&error_description=boom"
        assertEquals("server_error", fragmentParam(fragment, "error"))
        assertEquals("boom", fragmentParam(fragment, "error_description"))
        assertEquals("abc", fragmentParam(fragment, "state"))
    }
}
