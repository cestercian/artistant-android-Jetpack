package `in`.artistant.app.data.repository

import `in`.artistant.app.core.result.AppError
import `in`.artistant.app.data.model.HandleAvailability
import `in`.artistant.app.data.model.SelfProfile
import `in`.artistant.app.designsystem.theme.AppRole
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Confirms the fake honours the same contracts the real repo does (taken/failed/upsert). */
class FakeUsersRepositoryTest {

    @Test
    fun `handle availability reflects the taken set and format`() = runTest {
        val repo = FakeUsersRepository(taken = setOf("taken"))
        assertEquals(HandleAvailability.Available, repo.handleIsAvailable("free_one"))
        assertEquals(HandleAvailability.Unavailable, repo.handleIsAvailable("taken"))
        assertEquals(HandleAvailability.Unavailable, repo.handleIsAvailable("no")) // bad format
    }

    @Test
    fun `fetch returns the seeded profile, or throws when failFetch is set`() = runTest {
        val profile = SelfProfile(AppRole.Artist, "Yash", "Delhi", "yash", true)
        assertEquals(profile, FakeUsersRepository(selfProfile = profile).fetchSelfProfile())

        val failing = FakeUsersRepository(failFetch = true)
        try {
            failing.fetchSelfProfile()
            fail("expected fetchSelfProfile to throw")
        } catch (e: AppError.Unknown) { /* expected */ }
    }

    @Test
    fun `upsert records the write and rejects a taken handle`() = runTest {
        val repo = FakeUsersRepository(taken = setOf("dupe"))
        repo.upsertSelfProfile("Yash_01", "Yash", "Mumbai", AppRole.Client, termsAccepted = true)
        assertEquals("yash_01", repo.lastUpsert?.handle)
        assertTrue(repo.lastUpsert?.role == AppRole.Client)

        try {
            repo.upsertSelfProfile("dupe", "X", "Y", AppRole.Client, true)
            fail("expected upsert to reject a taken handle")
        } catch (e: AppError.UniqueViolation) { /* expected */ }
    }
}
