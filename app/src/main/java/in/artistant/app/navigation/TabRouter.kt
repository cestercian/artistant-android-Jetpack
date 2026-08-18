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

    fun apply(action: PushDeepLinkAction) {
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
        if (event.isNullOrBlank()) return PushDeepLinkAction.Ignore
        return when (event) {
            "booking_confirmed_client" ->
                bookingId?.let { PushDeepLinkAction.OpenBookingDetail(it) } ?: PushDeepLinkAction.Ignore
            "booking_confirmed_artist" -> PushDeepLinkAction.ArtistGigs
            "booking_reminder_24h" ->
                if (role == AppRole.Artist) PushDeepLinkAction.ArtistGigs
                else bookingId?.let { PushDeepLinkAction.OpenBookingDetail(it) } ?: PushDeepLinkAction.Ignore
            "booking_review_request" ->
                bookingId?.let { PushDeepLinkAction.OpenBookingDetail(it, autoReview = true) }
                    ?: PushDeepLinkAction.Ignore
            "message" -> PushDeepLinkAction.OpenThread(
                threadId = threadId,
                artistSide = role == AppRole.Artist,
            )
            "gig_request" -> PushDeepLinkAction.OpenGigRequest(requestId)
            "booking_request" -> PushDeepLinkAction.ArtistHome
            else -> PushDeepLinkAction.Ignore
        }
    }
}
