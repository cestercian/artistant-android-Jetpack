package `in`.artistant.app.data.repository

import `in`.artistant.app.core.result.AppError
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Position tracking (Fake) + the 23505 collision retry loop used by `upload`. */
class SamplesWriteTest {

    @Test
    fun `fake assigns sequential positions across uploads`() = runTest {
        val repo = FakeSamplesRepository()
        repo.upload(ByteArray(0), "m4a", "audio/m4a", "one", 30.0, "a1")
        repo.upload(ByteArray(0), "m4a", "audio/m4a", "two", 45.0, "a1")
        repo.upload(ByteArray(0), "mp3", "audio/mpeg", "three", 12.0, "a1")

        val positions = repo.list("a1").map { it.position }
        assertEquals(listOf(0, 1, 2), positions)
    }

    @Test
    fun `retry re-fetches position and lands after a 23505 collision`() = runTest {
        // First insert attempt collides on the stale position; the second, with a
        // freshly-fetched position, succeeds — mirroring two devices racing.
        var nextPos = 0
        var attempts = 0
        val landed = insertWithPositionRetry(
            nextPosition = { nextPos },
            insert = { position ->
                attempts++
                if (attempts == 1) {
                    nextPos = 1          // a concurrent uploader took position 0
                    throw AppError.UniqueViolation
                }
                position
            },
        )
        assertEquals(2, attempts)
        assertEquals(1, landed)
    }

    @Test
    fun `retry gives up after exhausting attempts`() = runTest {
        var attempts = 0
        val error = runCatching {
            insertWithPositionRetry(
                maxRetries = 3,
                nextPosition = { 0 },
                insert = { throw AppError.UniqueViolation.also { attempts++ } },
            )
        }.exceptionOrNull()
        assertEquals(3, attempts)
        assertTrue(error is AppError.UniqueViolation)
    }

    @Test
    fun `non-collision errors are not retried`() = runTest {
        var attempts = 0
        val error = runCatching {
            insertWithPositionRetry(
                nextPosition = { 0 },
                insert = { attempts++; throw IllegalStateException("boom") },
            )
        }.exceptionOrNull()
        assertEquals(1, attempts) // propagated immediately, no retry
        assertTrue(error is IllegalStateException)
    }
}
