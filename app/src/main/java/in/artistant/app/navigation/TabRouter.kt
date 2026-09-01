package `in`.artistant.app.navigation

import `in`.artistant.app.designsystem.theme.AppRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-wide deep-link channels — Android port of iOS `TabRouter`.
 * Push taps (and future URI deep links) set `pending*`; tab scaffolds
 * consume + clear. Sign-out MUST call [clearTransients].
 */
@Singleton
class TabRouter @Inject constructor() {
    private val _pendingBookingDetail = MutableStateFlow<String?>(null)
    val pendingBookingDetail: StateFlow<String?> = _pendingBookingDetail.asStateFlow()

    private val _pendingReviewSheet = MutableStateFlow<String?>(null)
    val pendingReviewSheet: StateFlow<String?> = _pendingReviewSheet.asStateFlow()

    private val _pendingThreadId = MutableStateFlow<String?>(null)
    val pendingThreadId: StateFlow<String?> = _pendingThreadId.asStateFlow()

    private val _pendingGigRequestId = MutableStateFlow<String?>(null)
    val pendingGigRequestId: StateFlow<String?> = _pendingGigRequestId.asStateFlow()

    /**
     * Which tab a push wants selected — a **one-shot event**, exactly like the
     * pending ids above it, and nullable for the same reason.
     *
     * It used to be a non-null retained selection (`clientTab = Discover`,
     * `artistTab = Home`), which broke twice over:
     *
     *  - Nothing ever wrote the user's own tab taps back into it, so the value was
     *    stale by construction. The scaffolds re-applied it on every fresh
     *    composition — and a font-scale change, a day/night flip, a fold resize or
     *    a process-death restore is a fresh composition — so `navigateToTab`'s
     *    `popUpTo(start)` collapsed the back stack the nav controller had just
     *    restored, dumping a client out of an artist profile and onto Discover.
     *  - A `StateFlow` drops a write equal to its current value, so re-arming the
     *    tab a push already sat on emitted nothing. An artist who launched (Home),
     *    tapped Messages by hand, then tapped a "New request" push got no
     *    navigation at all: `ArtistHome` wrote `Home` over `Home`. The id-carrying
     *    actions were immune only because their pending-id channel navigated for
     *    them; `ArtistHome`/`ArtistGigs` have no id to fall back on.
     *
     * Null → value → null always emits, and a fresh process (or an already-
     * consumed event) arms nothing, so a recreation navigates nowhere.
     *
     * A deliberate divergence from iOS, which keeps `clientTab`/`artistTab` as
     * persistent selections and excludes them from `clearTransients()`. There they
     * ARE the selection — `TabView(selection: $router.clientTab)` is a two-way
     * binding, so the user's own taps keep the value current and re-reading it
     * moves nothing. Navigation-Compose has no such binding: the selected tab is
     * derived from the back stack, and "applying" one is a `navigate()` that pops.
     * A selection here could only ever be a stale command.
     */
    private val _pendingClientTab = MutableStateFlow<ClientDeepTab?>(null)
    val pendingClientTab: StateFlow<ClientDeepTab?> = _pendingClientTab.asStateFlow()

    private val _pendingArtistTab = MutableStateFlow<ArtistDeepTab?>(null)
    val pendingArtistTab: StateFlow<ArtistDeepTab?> = _pendingArtistTab.asStateFlow()

    fun clearTransients() {
        _pendingBookingDetail.value = null
        _pendingReviewSheet.value = null
        _pendingThreadId.value = null
        _pendingGigRequestId.value = null
        // The tabs are transients now too: an unconsumed one belongs to the push
        // (or the account) that armed it, and must not steer a later session.
        _pendingClientTab.value = null
        _pendingArtistTab.value = null
    }

    /**
     * Apply one push TAP: wipe whatever an earlier tap left unconsumed, then arm only the
     * channels this event needs.
     *
     * The wipe-first order is the iOS `handleNotificationPayload` contract and it is
     * load-bearing: a `booking_review_request` leaves `pendingReviewSheet` set, and a
     * later `booking_confirmed_client` tap overwrites only `pendingBookingDetail` — so
     * without it the old review sheet auto-presents on top of a different booking.
     *
     * [PushDeepLinkAction.Ignore] is the one action that must NOT wipe, and it returns
     * above the clear. It arms nothing, so clearing on its behalf was pure loss: a payload
     * this build can't interpret — a server event newer than the app, or one whose id went
     * missing — silently cancelled a deep link the user HAD asked for by tapping, and left
     * them with nothing for either tap. `pushNotificationPlan` posts a notification for any
     * payload carrying an event, ids included or not, so an Ignore-routed tap is a state
     * the user can actually reach. And [in.artistant.app.platform.push.PushService] routes
     * on an IO coroutine after an async role read, so two taps landing close together (the
     * launcher intent, then `onNewIntent`) are not ordered relative to each other — an
     * Ignore could eat a good link it never even arrived after. "Ignore" now means what it
     * says: nothing is read from the payload and nothing is written.
     *
     * That last part diverges from iOS, which clears ahead of its `switch` and so wipes on
     * an unknown event too. iOS can afford it — `clientTab`/`artistTab` are persistent
     * selections there, excluded from `clearTransients()`, so its unknown-event tap still
     * leaves the user's tab alone. Here the tabs are transients (see [pendingArtistTab])
     * and they are the ONLY channel `ArtistGigs`/`ArtistHome` have, so the same wipe costs
     * strictly more than it does on iOS.
     */
    fun apply(action: PushDeepLinkAction) {
        if (action == PushDeepLinkAction.Ignore) return
        clearTransients()
        when (action) {
            is PushDeepLinkAction.OpenThread -> {
                if (action.artistSide) _pendingArtistTab.value = ArtistDeepTab.Messages
                else _pendingClientTab.value = ClientDeepTab.Messages
                _pendingThreadId.value = action.threadId
            }
            is PushDeepLinkAction.OpenGigRequest -> {
                _pendingArtistTab.value = ArtistDeepTab.Home
                _pendingGigRequestId.value = action.requestId
            }
            is PushDeepLinkAction.OpenBookingDetail -> {
                _pendingClientTab.value = ClientDeepTab.Bookings
                _pendingBookingDetail.value = action.bookingId
                if (action.autoReview) _pendingReviewSheet.value = action.bookingId
            }
            PushDeepLinkAction.ArtistGigs -> _pendingArtistTab.value = ArtistDeepTab.Gigs
            PushDeepLinkAction.ArtistHome -> _pendingArtistTab.value = ArtistDeepTab.Home
            // Returned above, before the clear — this branch only satisfies exhaustiveness.
            PushDeepLinkAction.Ignore -> Unit
        }
    }

    fun consumePendingThread(): String? = _pendingThreadId.value.also { _pendingThreadId.value = null }
    fun consumePendingGigRequest(): String? =
        _pendingGigRequestId.value.also { _pendingGigRequestId.value = null }
    fun consumePendingBookingDetail(): String? =
        _pendingBookingDetail.value.also { _pendingBookingDetail.value = null }
    fun consumePendingClientTab(): ClientDeepTab? =
        _pendingClientTab.value.also { _pendingClientTab.value = null }
    fun consumePendingArtistTab(): ArtistDeepTab? =
        _pendingArtistTab.value.also { _pendingArtistTab.value = null }
}

enum class ClientDeepTab { Discover, Bookings, Messages, Profile, Search }
enum class ArtistDeepTab { Home, Gigs, Messages, Epk }

/** Pure result of mapping a push payload — unit-tested without Firebase. */
sealed class PushDeepLinkAction {
    data class OpenThread(val threadId: String?, val artistSide: Boolean) : PushDeepLinkAction()
    data class OpenGigRequest(val requestId: String?) : PushDeepLinkAction()
    data class OpenBookingDetail(val bookingId: String, val autoReview: Boolean = false) : PushDeepLinkAction()
    data object ArtistGigs : PushDeepLinkAction()
    data object ArtistHome : PushDeepLinkAction()
    data object Ignore : PushDeepLinkAction()
}

/**
 * Maps send-push `artistant_*` data keys → [PushDeepLinkAction].
 * Same event contract as iOS `PushService.handleNotificationPayload`.
 */
object PushPayloadRouter {
    fun route(
        event: String?,
        bookingId: String?,
        threadId: String?,
        requestId: String?,
        role: AppRole,
    ): PushDeepLinkAction {
        // Every field arrives as an FCM data string, so "absent" and "arrived empty" are
        // the same fact and have to be read as one. `pushNotificationPlan` already
        // normalizes this same payload when it picks a channel and a collapse key; the two
        // halves of a push disagreeing about whether an id is present is how a tap ends up
        // somewhere the notification never promised. Untrimmed, a blank
        // `artistant_booking_id` sailed through the `?.let` below as a real id:
        // `OpenBookingDetail("")` reached the scaffold, which navigated to
        // `booking_detail/` — a string no destination in either graph matches — so a
        // malformed push CRASHED on tap instead of being ignored. The event name is
        // normalized by the same rule, because the plan half trims it before choosing a
        // channel: " message " was shown on the messages channel and then routed nowhere.
        val name = event.pushValue() ?: return PushDeepLinkAction.Ignore
        val booking = bookingId.pushValue()
        val thread = threadId.pushValue()
        val request = requestId.pushValue()
        return when (name) {
            "booking_confirmed_client" ->
                booking?.let { PushDeepLinkAction.OpenBookingDetail(it) } ?: PushDeepLinkAction.Ignore
            "booking_confirmed_artist" -> PushDeepLinkAction.ArtistGigs
            "booking_reminder_24h" ->
                if (role == AppRole.Artist) PushDeepLinkAction.ArtistGigs
                else booking?.let { PushDeepLinkAction.OpenBookingDetail(it) } ?: PushDeepLinkAction.Ignore
            "booking_review_request" ->
                booking?.let { PushDeepLinkAction.OpenBookingDetail(it, autoReview = true) }
                    ?: PushDeepLinkAction.Ignore
            // A `message` with no usable thread id still lands on the INBOX rather than on
            // nowhere: [TabRouter.apply] arms the Messages tab above the id, and the
            // scaffolds' id effect returns early on null. Same call iOS makes — "a payload
            // without a thread id still lands on the thread list, which beats the old
            // last-tab no-op". Not `Ignore`: the user was shown a message notification and
            // tapping it has to reach their messages.
            "message" -> PushDeepLinkAction.OpenThread(
                threadId = thread,
                artistSide = role == AppRole.Artist,
            )
            "gig_request" -> PushDeepLinkAction.OpenGigRequest(request)
            "booking_request" -> PushDeepLinkAction.ArtistHome
            else -> PushDeepLinkAction.Ignore
        }
    }
}

/**
 * One payload string, or null when it says nothing — the [PushPayloadRouter] copy of
 * `PushNotificationPlan`'s private helper of the same name. Two one-liners in two packages
 * beats a shared util nobody else would ever call, but they must stay the SAME rule: the
 * plan decides what the user is shown and the router decides where the tap goes, and those
 * two answering differently about one payload is a bug by construction.
 */
private fun String?.pushValue(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
