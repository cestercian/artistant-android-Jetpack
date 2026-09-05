package `in`.artistant.app.feature.profile

import `in`.artistant.app.data.model.Booking
import `in`.artistant.app.data.model.BookingStatus
import `in`.artistant.app.data.model.SelfProfile
import `in`.artistant.app.designsystem.theme.AppRole
import `in`.artistant.app.testsupport.booking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Which bookings list the delete screen reads, and what it says when it could not read one.
 *
 * The shipped version assumed a client whenever `fetchSelfProfile()` failed: `isArtist` starts
 * false, so an ARTIST was sent down `listForClient()` — a query that does not fail, it just
 * answers an empty list about the wrong question. Stage 2 then told an artist with gigs on the
 * books that nothing was upcoming, on the last screen before an irreversible action. These are
 * red→green against that.
 */
class DeleteConsequencesLoadTest {

    /** The role the app has cached on the device — `AppPreferences.role`. */
    private val artistProfile = SelfProfile(
        role = AppRole.Artist,
        fullName = "Tilt Collective",
        city = "Bengaluru",
        handle = "tiltcollective",
        artistSetupComplete = true,
    )

    /** Collects the transforms the loader publishes, exactly as the ViewModel applies them. */
    private class Facts {
        var value = DeleteConsequences()
            private set

        val publish: ((DeleteConsequences) -> DeleteConsequences) -> Unit = { transform ->
            value = transform(value)
        }
    }

    @Test
    fun `an artist's own gigs are what an artist is shown`() = runTest {
        val facts = Facts()
        var asked: AppRole? = null
        loadDeleteConsequences(
            fetchProfile = { artistProfile },
            cachedRole = { AppRole.Client },
            listBookings = { role ->
                asked = role
                listOf(booking(status = BookingStatus.Confirmed))
            },
            publish = facts.publish,
        )
        assertEquals(AppRole.Artist, asked)
        assertTrue(facts.value.isArtist)
        assertEquals("tiltcollective", facts.value.handle)
        assertEquals(1, facts.value.bookings)
        assertEquals(1, facts.value.upcoming)
    }

    @Test
    fun `a failed fetch falls back to the device's cached role instead of assuming a client`() =
        runTest {
            val facts = Facts()
            var asked: AppRole? = null
            loadDeleteConsequences(
                fetchProfile = { throw IOException("offline") },
                cachedRole = { AppRole.Artist },
                listBookings = { role ->
                    asked = role
                    listOf(booking(status = BookingStatus.Confirmed))
                },
                publish = facts.publish,
            )
            // The bug: this used to be Client, and the artist's gigs were never counted.
            assertEquals(AppRole.Artist, asked)
            assertTrue(facts.value.isArtist)
            assertEquals(1, facts.value.upcoming)
        }

    @Test
    fun `a fetch that answers a row with no role falls back too`() = runTest {
        val facts = Facts()
        var asked: AppRole? = null
        loadDeleteConsequences(
            fetchProfile = { artistProfile.copy(role = null) },
            cachedRole = { AppRole.Artist },
            listBookings = { role ->
                asked = role
                emptyList()
            },
            publish = facts.publish,
        )
        assertEquals(AppRole.Artist, asked)
    }

    @Test
    fun `neither source answering leaves the counts unknown rather than recording zeros`() =
        runTest {
            val facts = Facts()
            var listed = false
            loadDeleteConsequences(
                fetchProfile = { throw IOException("offline") },
                cachedRole = { throw IOException("datastore gone") },
                listBookings = {
                    listed = true
                    emptyList()
                },
                publish = facts.publish,
            )
            // A list read against a guessed role is worse than no list: `listForClient()` for an
            // artist SUCCEEDS, and its honest empty answer becomes "nothing is upcoming, so
            // nothing gets cancelled" on the screen that erases the account.
            assertFalse("no role means no query", listed)
            assertNull(facts.value.bookings)
            assertNull(facts.value.upcoming)
            assertFalse(facts.value.isArtist)
            // …and null is exactly what the copy is built to survive.
            val sentence = deleteConsequences(facts.value).map { it.detail }
            assertTrue(sentence.any { it.contains("including anything still upcoming") })
        }

    @Test
    fun `a failed bookings read keeps the handle the fetch already gave us`() = runTest {
        val facts = Facts()
        loadDeleteConsequences(
            fetchProfile = { artistProfile },
            cachedRole = { AppRole.Artist },
            listBookings = { throw IllegalStateException("RLS") },
            publish = facts.publish,
        )
        assertEquals("tiltcollective", facts.value.handle)
        assertNull(facts.value.bookings)
        assertNull(facts.value.upcoming)
    }

    @Test
    fun `a blank handle is dropped rather than printed as an at-sign on its own`() = runTest {
        val facts = Facts()
        loadDeleteConsequences(
            fetchProfile = { artistProfile.copy(handle = "   ") },
            cachedRole = { AppRole.Artist },
            listBookings = { emptyList<Booking>() },
            publish = facts.publish,
        )
        assertNull(facts.value.handle)
    }

    @Test
    fun `nothing here throws, whatever every source does`() = runTest {
        // The call site is a bare `viewModelScope.launch` — a throw kills the app, and no stat
        // query may stop a DPDP §11 erasure.
        val facts = Facts()
        loadDeleteConsequences(
            fetchProfile = { throw IOException("offline") },
            cachedRole = { throw IOException("datastore gone") },
            listBookings = { throw IllegalStateException("never reached") },
            publish = facts.publish,
        )
        assertEquals(DeleteConsequences(), facts.value)
    }
}
