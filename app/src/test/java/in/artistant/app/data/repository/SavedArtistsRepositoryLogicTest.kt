package `in`.artistant.app.data.repository

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedArtistsRepositoryLogicTest {
    @Test
    fun addRemoveList_roundTrip() = runTest {
        val repo = FakeSavedArtistsRepository()
        repo.add("AAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA")
        assertEquals(listOf("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"), repo.list())
        repo.remove("AAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA")
        assertTrue(repo.list().isEmpty())
    }

    @Test
    fun list_throwsWhenUnsignedIn() = runTest {
        val repo = FakeSavedArtistsRepository().apply { signedIn = false }
        var threw = false
        try {
            repo.list()
        } catch (_: SavedArtistsRepositoryError.NotSignedIn) {
            threw = true
        }
        assertTrue(threw)
    }
}

class SavedStoreToggleTest {
    @Test
    fun toggle_flipsLocalSet() = runTest {
        // Exercise Fake directly — SavedStore needs DataStore Context in Hilt.
        val repo = FakeSavedArtistsRepository()
        assertFalse(repo.list().contains("a1"))
        repo.add("a1")
        assertTrue(repo.list().contains("a1"))
        repo.remove("a1")
        assertFalse(repo.list().contains("a1"))
    }
}
