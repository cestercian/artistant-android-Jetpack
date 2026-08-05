package `in`.artistant.app.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which transition a navigation gets.
 *
 * This is the one branch in the nav-motion code that can be wrong without
 * crashing, and it is wrong in a way screenshots cannot show: the app still
 * navigates, it just describes the wrong spatial relationship. A pop that
 * cross-dissolves has thrown away the only cue that told the user they went
 * back.
 */
class NavMotionTest {

    private val clientTabs = setOf("discover", "bookings", "messages", "profile", "search")

    @Test
    fun `moving between two tabs is a tab switch`() {
        assertTrue(isTabSwitch("discover", "bookings", clientTabs))
        assertTrue(isTabSwitch("messages", "profile", clientTabs))
    }

    @Test
    fun `the detached search destination still counts as a tab`() {
        // Search renders outside the pill, but moving to it is lateral, not a
        // push — it must dissolve like any other peer.
        assertTrue(isTabSwitch("discover", "search", clientTabs))
        assertTrue(isTabSwitch("search", "discover", clientTabs))
    }

    @Test
    fun `pushing from a tab into a detail screen is not a tab switch`() {
        assertFalse(isTabSwitch("discover", "artist/{artistId}", clientTabs))
        assertFalse(isTabSwitch("bookings", "bookingDetail/{bookingId}", clientTabs))
    }

    @Test
    fun `popping a detail screen back to its tab is not a tab switch`() {
        // The regression this guards: checking only the DESTINATION would call
        // this a tab switch and dissolve a transition the user experienced as
        // going back.
        assertFalse(isTabSwitch("artist/{artistId}", "discover", clientTabs))
    }

    @Test
    fun `moving between two detail screens is not a tab switch`() {
        assertFalse(isTabSwitch("booking/{artistId}", "checkout", clientTabs))
    }

    @Test
    fun `staying on the same tab is not a switch`() {
        // A re-selection of the live tab has nothing to animate between.
        assertFalse(isTabSwitch("discover", "discover", clientTabs))
    }

    @Test
    fun `a null route falls back to the stack transition`() {
        // Graph nodes and entries mid-teardown report no route. Defaulting to
        // the slide is the safer miss: a slide on a genuine tab switch merely
        // feels off, while a dissolve on a genuine push loses the back cue.
        assertFalse(isTabSwitch(null, "discover", clientTabs))
        assertFalse(isTabSwitch("discover", null, clientTabs))
        assertFalse(isTabSwitch(null, null, clientTabs))
    }

    @Test
    fun `the artist graph resolves its own tab set independently`() {
        // Both scaffolds share these builders; a route that is a tab on one side
        // must not be treated as one on the other.
        val artistTabs = setOf("home", "gigs", "messages", "epk")
        assertTrue(isTabSwitch("home", "gigs", artistTabs))
        assertFalse(isTabSwitch("home", "discover", artistTabs))
    }
}
