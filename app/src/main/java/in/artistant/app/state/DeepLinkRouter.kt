package `in`.artistant.app.state

import android.os.Bundle
import `in`.artistant.app.designsystem.theme.AppRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Push deep-link channels (port of iOS `TabRouter` + `PushService.handleNotificationPayload`).
 *
 * A notification tap parks a destination here; the tab scaffolds + tab screens observe the
 * relevant channel, navigate, then clear it so a re-composition can't re-navigate. Two layers:
 *
 *  1. [pendingTab] — a role-aware TAB selection the OUTER scaffold consumes (switches the bottom
 *     tab). This is what gets the right tab in front BEFORE the inner screen consumes its id
 *     (e.g. Bookings isn't the client start tab, so without the tab switch the id-consumer never
 *     mounts).
 *  2. [pendingThreadId] / [pendingRequestId] / [pendingBookingId] — a transient id the INNER
 *     screen (Messages / ArtistHome / Bookings) consumes to push a detail within that tab.
 *
 * SEAM STATUS (issue #38, phase P2a): this is INERT until P1 (backend push triggers) + P2b
 * (Firebase FCM receiver) land. The only PRODUCER today is [routeFromExtras], called from
 * MainActivity for a notification-launch intent — which carries no `artistant_*` extras until
 * P2b sends real pushes, so it's a no-op. No Firebase dependency is needed to build/test this.
 *
 * KNOWN LIMITATION — deferred to P2b/P4 (issue #39), where a real push lets us device-verify it:
 * the id-consumers ([pendingBookingId] etc.) live on each tab's ROOT screen, but the scaffold's
 * `navigateToTab` uses `restoreState = true` (the standard multi-back-stack pattern). If the target
 * tab already holds a SAVED DEEP stack (the user drilled into a detail, then switched tabs), a push
 * into it restores that deep destination instead of the root — so the root's `LaunchedEffect`
 * doesn't run and the parked id isn't consumed (the user lands on the stale detail, not the pushed
 * one). Cold launch + shallow-tab both work. The fix (consume the id at the tab-CONTAINER level, or
 * force-root the deep-link tab switch) touches `restoreState` semantics that only a device can
 * verify, so it's intentionally NOT done blind here — it's an explicit acceptance item on #39.
 */
@Singleton
class DeepLinkRouter @Inject constructor() {

    private val _pendingThreadId = MutableStateFlow<String?>(null)
    /** A parked `message` thread id — [in.artistant.app.feature.messages.MessagesScreen] consumes it. */
    val pendingThreadId: StateFlow<String?> = _pendingThreadId.asStateFlow()

    private val _pendingRequestId = MutableStateFlow<String?>(null)
    /** A parked `gig_request` id — ArtistHomeScreen consumes it (pushes the request detail). */
    val pendingRequestId: StateFlow<String?> = _pendingRequestId.asStateFlow()

    private val _pendingBookingId = MutableStateFlow<String?>(null)
    /** A parked booking id — BookingsScreen consumes it (pushes the booking detail). */
    val pendingBookingId: StateFlow<String?> = _pendingBookingId.asStateFlow()

    private val _pendingTab = MutableStateFlow<DeepLinkTab?>(null)
    /** A parked role-aware tab selection — the OUTER scaffold whose role matches consumes it. */
    val pendingTab: StateFlow<DeepLinkTab?> = _pendingTab.asStateFlow()

    /** Park a thread id for the Messages screen to consume (future push entry point). */
    fun deepLinkTo(threadId: String) { _pendingThreadId.value = threadId }

    fun consumePendingThread() { _pendingThreadId.value = null }
    fun consumePendingRequest() { _pendingRequestId.value = null }
    fun consumePendingBooking() { _pendingBookingId.value = null }
    fun consumePendingTab() { _pendingTab.value = null }

    /**
     * Read the `artistant_*` extras a notification-launch intent carries, resolve the destination
     * via [routeFor], and park the tab + the one id channel this event needs. Unknown / missing
     * event ⇒ no-op. [role] is the tapping device's persisted role (mirrors iOS reading
     * `Persistence.load(AppRole.self)`), needed because `message` / `booking_reminder_24h` route
     * differently per side.
     *
     * Android `Bundle` here (not unit-tested); the pure decision lives in [routeFor], which is.
     */
    fun routeFromExtras(extras: Bundle?, role: AppRole) {
        val event = extras?.getString(KEY_EVENT) ?: return
        val target = routeFor(
            event = event,
            role = role,
            threadId = extras.getString(KEY_THREAD),
            requestId = extras.getString(KEY_REQUEST),
            bookingId = extras.getString(KEY_BOOKING),
        ) ?: return
        // Clear every transient id channel first, THEN set only the one this event carries — a
        // stale id from an earlier tap must not survive into this navigation (iOS clears all
        // pending* up front before populating). Setting all three from `target` does exactly that:
        // the two the event doesn't use are null on the target and so get cleared here.
        _pendingThreadId.value = target.threadId
        _pendingRequestId.value = target.requestId
        _pendingBookingId.value = target.bookingId
        _pendingTab.value = target.tab
    }

    companion object {
        // The custom keys the send-push payload attaches (identical to the iOS contract).
        const val KEY_EVENT = "artistant_event"
        const val KEY_THREAD = "artistant_thread_id"
        const val KEY_REQUEST = "artistant_request_id"
        const val KEY_BOOKING = "artistant_booking_id"

        /**
         * PURE routing table — mirrors iOS `PushService.handleNotificationPayload` exactly. Maps a
         * push `event` (+ the tapping device's [role] and the payload ids) to a [DeepLinkTarget],
         * or null for "don't navigate" (unknown event, or a booking event whose id is missing —
         * iOS no-ops those rather than landing on a detail-less screen). This is the unit-tested
         * core; [routeFromExtras] is the thin Bundle-reading + state-setting wrapper around it.
         */
        fun routeFor(
            event: String,
            role: AppRole,
            threadId: String?,
            requestId: String?,
            bookingId: String?,
        ): DeepLinkTarget? = when (event) {
            // Shared screen between both tab bars — land on whichever role's Messages tab and hand
            // it the thread id. Routes even without a thread id (thread list beats a last-tab no-op).
            "message" -> DeepLinkTarget(
                tab = if (role == AppRole.Artist) DeepLinkTab.ArtistMessages else DeepLinkTab.ClientMessages,
                threadId = threadId,
            )
            // Artist-only event (the DB trigger targets the artist's user id) — surfaces on Home.
            "gig_request" -> DeepLinkTarget(DeepLinkTab.ArtistHome, requestId = requestId)
            // Always client-side by event name; needs the booking id to open its detail.
            "booking_confirmed_client" ->
                bookingId?.let { DeepLinkTarget(DeepLinkTab.ClientBookings, bookingId = it) }
            // Always artist-side; the new gig surfaces via the Gigs list refresh (no detail id yet).
            "booking_confirmed_artist" -> DeepLinkTarget(DeepLinkTab.ArtistGigs)
            // Fan-out event — the tapping device's role decides the side. Artist → Gigs (no id);
            // client → the booking detail.
            "booking_reminder_24h" ->
                if (role == AppRole.Artist) DeepLinkTarget(DeepLinkTab.ArtistGigs)
                else bookingId?.let { DeepLinkTarget(DeepLinkTab.ClientBookings, bookingId = it) }
            // Client-side; lands on the booking detail. iOS also auto-presents a review sheet here.
            // TODO(P2b/P4): wire a review-sheet auto-present channel — not built on Android yet, so
            // for now this is identical to booking_confirmed_client (detail only, no sheet).
            "booking_review_request" ->
                bookingId?.let { DeepLinkTarget(DeepLinkTab.ClientBookings, bookingId = it) }
            else -> null
        }
    }
}

/**
 * A bottom-tab destination a push can select, carrying the [role] whose scaffold owns it + the
 * scaffold's [route] string. The scaffold only acts on a [pendingTab] whose [role] matches its own
 * (never composed for the other role, so this is defensive), then [DeepLinkRouter.consumePendingTab].
 * `messages` exists for both roles because the Messages screen is shared between the two tab bars.
 */
enum class DeepLinkTab(val role: AppRole, val route: String) {
    ClientMessages(AppRole.Client, "messages"),
    ArtistMessages(AppRole.Artist, "messages"),
    ClientBookings(AppRole.Client, "bookings"),
    ArtistHome(AppRole.Artist, "home"),
    ArtistGigs(AppRole.Artist, "gigs"),
}

/**
 * The resolved push destination: which [tab] to select + at most one id channel to set. Exactly one
 * of the id fields is non-null per event (the others clear the corresponding pending channel).
 */
data class DeepLinkTarget(
    val tab: DeepLinkTab,
    val threadId: String? = null,
    val requestId: String? = null,
    val bookingId: String? = null,
)
