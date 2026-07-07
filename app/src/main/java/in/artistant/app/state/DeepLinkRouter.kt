package `in`.artistant.app.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The push deep-link channel (port of the iOS `TabRouter.pendingThreadId`). A
 * `message` push tap parks a thread id here; [MessagesScreen] observes it, pushes
 * the chat, then clears it so re-appearing doesn't re-push.
 *
 * ponytail: only the CONSUMER (MessagesScreen) exists today — there is no producer
 * yet because M4 ships no push/FCM. This is the seam so wiring a `message` push
 * later is a one-line `deepLinkTo(threadId)` call, not a new plumbing pass.
 */
@Singleton
class DeepLinkRouter @Inject constructor() {
    private val _pendingThreadId = MutableStateFlow<String?>(null)
    val pendingThreadId: StateFlow<String?> = _pendingThreadId.asStateFlow()

    /** Park a thread id for the Messages screen to consume (future push entry point). */
    fun deepLinkTo(threadId: String) { _pendingThreadId.value = threadId }

    /** Clear after the screen has navigated, so re-composition doesn't re-navigate. */
    fun consumePendingThread() { _pendingThreadId.value = null }
}
