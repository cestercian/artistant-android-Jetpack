package `in`.artistant.app.ui.auth

import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Pins the contract behind the friendly "couldn't reach the server" message: the four
 * connectivity failure types map to a network error, a plain app error does not, and a
 * network cause buried under a wrapper is still detected (supabase-kt / our own wrapping).
 * A regression here would put a raw Ktor timeout string back in the signup UI.
 */
class NetworkErrorTest {

    @Test
    fun `the four connectivity exception types are network errors`() {
        assertTrue(isNetworkError(HttpRequestTimeoutException("https://x.supabase.co/auth/v1/signup", 10_000L)))
        assertTrue(isNetworkError(ConnectTimeoutException("connect timed out")))
        assertTrue(isNetworkError(SocketTimeoutException("read timed out")))
        assertTrue(isNetworkError(IOException("network is unreachable")))
    }

    @Test
    fun `a generic error is not a network error`() {
        assertFalse(isNetworkError(IllegalStateException("something else broke")))
        assertFalse(isNetworkError(RuntimeException("boom")))
    }

    @Test
    fun `a network cause wrapped in a non-IO exception is still detected`() {
        val wrapped = RuntimeException("wrapper", HttpRequestTimeoutException("https://x", 10_000L))
        assertTrue(isNetworkError(wrapped))
    }
}
