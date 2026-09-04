package `in`.artistant.app.feature.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The one thing the toast host cannot get wrong (design 77). */
class ToastControllerTest {

    @Test
    fun `show publishes the message`() {
        val controller = ToastController()
        controller.show("Venue address copied")
        assertEquals("Venue address copied", controller.current.value?.text)
    }

    @Test
    fun `a blank toast is not a toast`() {
        val controller = ToastController()
        controller.show("   ")
        assertNull(controller.current.value)
    }

    @Test
    fun `a repeat of the same string is a new message`() {
        // The identity is what the host keys its display timer on: keyed on the
        // TEXT, a second identical toast restarts nothing and is cut short by the
        // first one's already-running delay.
        val controller = ToastController()
        controller.show("Request sent.")
        val first = controller.current.value!!.id
        controller.show("Request sent.")
        assertNotEquals(first, controller.current.value!!.id)
    }

    @Test
    fun `dismissing a superseded toast leaves the current one alone`() {
        val controller = ToastController()
        controller.show("first")
        val stale = controller.current.value!!.id
        controller.show("second")
        controller.dismiss(stale)
        assertEquals("second", controller.current.value?.text)
    }

    @Test
    fun `dismissing the current toast clears it`() {
        val controller = ToastController()
        controller.show("Feedback sent")
        controller.dismiss(controller.current.value!!.id)
        assertNull(controller.current.value)
    }

    @Test
    fun `a null id clears whatever is showing`() {
        // The host passes `toast?.id`, which is null exactly when nothing is up —
        // and after an exit animation, when the state has already been cleared.
        val controller = ToastController()
        controller.show("anything")
        controller.dismiss(null)
        assertNull(controller.current.value)
    }

    @Test
    fun `the newest toast replaces rather than queues`() {
        val controller = ToastController()
        controller.show("first")
        controller.show("second")
        assertEquals("second", controller.current.value?.text)
    }
}
