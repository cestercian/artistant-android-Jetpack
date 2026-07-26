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

    /** Which client tab should be selected (push may flip this before a pending id lands). */
    private val _clientTab = MutableStateFlow(ClientDeepTab.Discover)
    val clientTab: StateFlow<ClientDeepTab> = _clientTab.asStateFlow()

    private val _artistTab = MutableStateFlow(ArtistDeepTab.Home)
    val artistTab: StateFlow<ArtistDeepTab> = _artistTab.asStateFlow()

    fun clearTransients() {
        _pendingBookingDetail.value = null
        _pendingReviewSheet.value = null
        _pendingThreadId.value = null
        _pendingGigRequestId.value = null
    }

    fun apply(action: PushDeepLinkAction) {
        clearTransients()
        when (action) {
            is PushDeepLinkAction.OpenThread -> {
                if (action.artistSide) _artistTab.value = ArtistDeepTab.Messages
                else _clientTab.value = ClientDeepTab.Messages
                _pendingThreadId.value = action.threadId
            }
            is PushDeepLinkAction.OpenGigRequest -> {
                _artistTab.value = ArtistDeepTab.Home
                _pendingGigRequestId.value = action.requestId
            }
            is PushDeepLinkAction.OpenBookingDetail -> {
                _clientTab.value = ClientDeepTab.Bookings
                _pendingBookingDetail.value = action.bookingId
                if (action.autoReview) _pendingReviewSheet.value = action.bookingId
            }
            PushDeepLinkAction.ArtistGigs -> _artistTab.value = ArtistDeepTab.Gigs
            PushDeepLinkAction.ArtistHome -> _artistTab.value = ArtistDeepTab.Home
            PushDeepLinkAction.Ignore -> Unit
        }
    }

    fun consumePendingThread(): String? = _pendingThreadId.value.also { _pendingThreadId.value = null }
    fun consumePendingGigRequest(): String? =
        _pendingGigRequestId.value.also { _pendingGigRequestId.value = null }
    fun consumePendingBookingDetail(): String? =
        _pendingBookingDetail.value.also { _pendingBookingDetail.value = null }
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
