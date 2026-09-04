package `in`.artistant.app.feature.messages

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.data.model.Message
import `in`.artistant.app.data.model.MessageDelivery
import `in`.artistant.app.data.model.Thread
import `in`.artistant.app.data.repository.ArtistsRepository
import `in`.artistant.app.data.repository.BookingsRepository
import `in`.artistant.app.data.repository.MessagesRepository
import `in`.artistant.app.data.repository.MessagesSubscription
import `in`.artistant.app.data.repository.PendingReport
import `in`.artistant.app.data.repository.ReportOutcome
import `in`.artistant.app.data.repository.ReportsRepository
import `in`.artistant.app.data.repository.RequestsRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * One-shot side effects. Today: a send that didn't land.
 *
 * iOS watches a derived `failedCount` and buzzes when it rises. We fire from the
 * write itself, which says the same thing more directly and also covers the case
 * a count can't: a RETRY that fails again leaves the count exactly where it was.
 */
sealed interface ChatEvent {
    /** A message flipped to `Failed` — first attempt or retry. */
    data object SendFailed : ChatEvent

    /**
     * The in-thread quote was accepted and its terms are frozen.
     *
     * **It carries no booking id, because accepting a quote creates no
     * booking.** Design 94 draws a "Match confirmed" landing for this path, but
     * this backend does not produce one: accepting a `gig_requests` row is a
     * status PATCH, and the only server reaction (migration 0047, rewritten by
     * 0076) is to open the bookingless chat thread the quote is already sitting
     * in — 0047's own header says it deliberately creates no booking, because
     * the client has to consent to the final amount. RLS agrees: the accepting
     * seat on an `open` request is the ARTIST, and `bookings_insert_client`
     * gates inserts on `auth.uid() = client_id`, so the write that would create
     * the booking is not the artist's to make.
     *
     * So the record IS the thread, which is what design 08's note means by "the
     * record of what was agreed lives in the thread, not in a screenshot" — and
     * the card says what has and has not happened rather than implying a booking
     * exists. This event is the buzz and nothing more.
     */
    data object QuoteAccepted : ChatEvent
}

/**
 * The three named phases of accepting a quote (design 70 — "narrated, not a
 * spinner").
 *
 * Each one advances on real work finishing, never on a timer: [Locking] is done
 * the moment the amount is captured, [Saving] while the status PATCH is in
 * flight, [Confirming] while the thread re-reads what it just changed. A phase
 * that lied about progress would be worse than the spinner it replaces.
 */
enum class QuotePhase { Locking, Saving, Confirming }

/** What the quote card is doing. */
sealed interface QuoteAction {
    data object Idle : QuoteAction
    data class Accepting(val phase: QuotePhase) : QuoteAction
    data class Failed(val message: String) : QuoteAction
}

data class ChatUiState(
    val thread: Thread? = null,
    val title: String = "Chat",
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = true,
    /**
     * The transcript couldn't be loaded, or a send didn't land. Owns the two
     * surfaces that offer to reload the conversation — the empty-state Retry and
     * the strip above the composer — so nothing else may write it.
     */
    val error: String? = null,
    /**
     * A mute or block toggle that didn't take. Kept apart from [error] because
     * the two mean opposite things to the reader: routing a failed mute into
     * `error` told them "couldn't refresh this conversation" over a transcript
     * that was fine, and on a thread with no messages yet it replaced the whole
     * transcript with a load-failure screen. This renders in the details sheet,
     * where the tap happened, and clears on the next toggle or on dismiss.
     */
    val actionError: String? = null,
    val counterpartLastReadAt: Long? = null,
    val showDetails: Boolean = false,
    /**
     * Which side of the thread the viewer sits on. Drives the participant role
     * labels in the details sheet and the "Name · Role · time" caption on
     * incoming runs — both of which are the opposite of the viewer's own role.
     */
    val viewerIsArtist: Boolean = false,
    /**
     * The gig this conversation is about. Without it the header carried only a
     * name, so someone deep in a negotiation had no way to tell which booking
     * they were negotiating — the most common reason to bounce out mid-thread.
     */
    val context: ThreadContext = ThreadContext.INQUIRY,
    /** Per-thread, persisted: dismissing the notice in one chat must not hide it in all. */
    val safetyBannerVisible: Boolean = true,
    val starred: Boolean = false,
    val archived: Boolean = false,
    /**
     * The viewer's own mute state for this thread (mig 0091). Unlike starred /
     * archived — which are device-local reading preferences — this is a server
     * column that `send-push` honours, so it survives a reinstall and applies on
     * every device the person signs in on.
     */
    val muted: Boolean = false,
    /**
     * The gig's artist, for the details sheet. Only the client seat can act on
     * it — an artist's counterpart is a client with no public profile — so the
     * id is null for the artist seat rather than pointing at the viewer.
     */
    val artistId: String? = null,
    val artistSubtitle: String = "",
    val artistScore: Int? = null,
    /**
     * A report that reached SOMEWHERE — `public.reports` or this install's local
     * log. Null until one has.
     *
     * The distinction is the whole point: the sheet used to flip one boolean
     * after every call and print "the report is with our safety team" over all
     * three outcomes, including the one where nothing had been stored at all.
     * [ReportOutcome.Queued] is not a failure and the copy must not call it one,
     * but it is not a delivery either. [ReportOutcome.Failed] never lands here —
     * it is durable state with an action attached, so it rides [failedReport].
     */
    val reportOutcome: ReportOutcome? = null,
    /**
     * A report nothing is holding — the insert failed AND so did the local log.
     *
     * Keeps the reader's own reason and note so the retry doesn't ask them to
     * write it twice, and deliberately SURVIVES closing the sheet: the receipt
     * is a momentary fact, but "your safety report was lost" is a state, and it
     * goes away when it is fixed or explicitly discarded, not when a sheet is
     * dismissed.
     */
    val failedReport: PendingReport? = null,
    /**
     * A report is in flight.
     *
     * The form does not close on submit — it stays up and only becomes a receipt
     * when the outcome lands — so without this the CTA is live for the whole
     * round trip and a second tap files the SAME report again. Duplicates are
     * not free here: they are two rows in `public.reports` against one person for
     * one thing, which is noise the moderation team reads as a pattern.
     */
    val isSubmittingReport: Boolean = false,
    /**
     * The other person's user id, for blocking (mig 0087). Unlike [artistId] this
     * is populated on BOTH seats — an artist blocks a client just as a client
     * blocks an artist — and is null only when the seat can't be resolved, which
     * is what hides the action rather than letting it guess.
     */
    val counterpartId: String? = null,
    /** Whether the viewer has blocked [counterpartId]. */
    val blocked: Boolean = false,
    /**
     * The offer standing in this conversation, as an object (design 08).
     *
     * Null when there is no live or accepted `gig_requests` row between these
     * two people — which is most threads. Never invented: no request, no card.
     */
    val quote: ThreadQuote? = null,
    val quoteAction: QuoteAction = QuoteAction.Idle,
    /** True while the counter-amount sheet is open. */
    val countering: Boolean = false,
) {
    /**
     * The last of the viewer's own messages the counterparty has read. Only
     * `Sent` rows qualify — an in-flight or failed bubble has no server row that
     * could have been read.
     */
    val lastReadOwnMessageId: String? =
        ChatTimestamps.lastReadOwnMessageId(messages, counterpartLastReadAt)
}

/**
 * Chat: poll-on-open + Realtime INSERT + optimistic send with 3-way reconcile
 * (local optimistic ↔ send RETURNING ↔ Realtime echo), matching iOS MessageStore.
 *
 * Beyond the transcript it resolves the gig behind the thread, so the header
 * strip, the caption above the composer and the funnel CTA all read from one
 * [ThreadContext] and can never disagree about the booking.
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val messagesRepository: MessagesRepository,
    private val artistsRepository: ArtistsRepository,
    private val bookingsRepository: BookingsRepository,
    private val reports: ReportsRepository,
    private val requests: RequestsRepository,
    private val flagsStore: ThreadFlagsStore,
    private val blockedUsers: BlockedUsersStore,
    private val readReceipts: ReadReceiptsPreference,
    private val viewer: ViewerIdentity,
) : ViewModel() {
    private val threadId: String = checkNotNull(savedStateHandle["threadId"])
    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private val _events = Channel<ChatEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var subscription: MessagesSubscription? = null
    /**
     * Bumped on each report attempt, and in [onCleared], so a completion that is
     * no longer the current one cannot write state. Same idiom, and the same
     * reason, as [subscribeGeneration] below it.
     */
    private var reportGeneration = 0

    /** Bumped on each subscribe attempt so a superseded join is discarded. */
    private var subscribeGeneration = 0

    /** One scroll-back page in flight at a time — see [loadOlder]. */
    private var loadingOlder = false

    /** Whether the screen's first ON_RESUME has been seen — see [onResumed]. */
    private var resumedOnce = false

    /** A page came back short: the thread has no history behind what's loaded. */
    private var reachedStartOfHistory = false

    init {
        viewModelScope.launch {
            flagsStore.flags.collect { flags ->
                _state.update {
                    it.copy(
                        starred = threadId in flags.starred,
                        archived = threadId in flags.archived,
                        safetyBannerVisible = threadId !in flags.safetyDismissed,
                    )
                }
            }
        }
        // Hydrated here as well as in the inbox because a chat can be opened
        // straight from a push notification, never having passed through the
        // inbox this session. Separate launch from the collect below, which
        // never returns.
        viewModelScope.launch { blockedUsers.refresh() }
        // The blocked set is the single source of truth for the Block/Unblock
        // label, so the toggle needs no optimistic state of its own here — the
        // store flips its set immediately and reverts if the write fails, and
        // this collect just follows it.
        viewModelScope.launch {
            blockedUsers.blocked.collect { ids ->
                _state.update { it.copy(blocked = it.counterpartId in ids) }
            }
        }
        refresh()
        subscribeRealtime()
    }

    fun refresh() = viewModelScope.launch {
        _state.update { it.copy(isLoading = true, error = null) }
        runCatching {
            val thread = messagesRepository.listThreadsForUser().firstOrNull { it.id == threadId }
            // The newest page only. Anything older is reached through [loadOlder],
            // and `mergePreservingOptimistic` keeps whatever it already pulled in.
            val messages = messagesRepository.listMessages(threadId, PAGE_SIZE)
            thread to messages
        }.onSuccess { (thread, serverMessages) ->
            // Same seat-aware rule as the inbox row — see ThreadCounterpart. The
            // title also feeds the details sheet's `counterpartLabel`, so fixing
            // it here fixes the Participants list that used to print the viewer's
            // own name directly above "You".
            val viewerId = viewer.currentUserId()
            val viewerIsArtist = thread != null && ThreadCounterpart.viewerIsArtist(thread, viewerId)
            val artist = thread?.let { artistsRepository.find(it.artistId) }
            // No thread row (load failed / not a participant) means no seat to
            // resolve from, so stay on the neutral header rather than guessing.
            val title = thread?.let {
                ThreadCounterpart.name(thread = it, viewerId = viewerId, artistName = artist?.name)
            } ?: FALLBACK_TITLE
            // Resolved on the same pass as the title so the sheet can never offer
            // to block one person while naming another.
            val counterpartId = thread?.let { ThreadCounterpart.counterpartId(it, viewerId) }
            _state.update { state ->
                state.copy(
                    thread = thread,
                    title = title,
                    viewerIsArtist = viewerIsArtist,
                    counterpartId = counterpartId,
                    blocked = counterpartId != null && counterpartId in blockedUsers.blocked.value,
                    // Server-owned and already resolved to the viewer's own side
                    // by the decoder, so a refresh is the only thing that can
                    // correct an optimistic toggle that failed.
                    muted = thread?.muted ?: false,
                    // Only the client seat's counterpart has a profile to open.
                    artistId = thread?.artistId?.takeUnless { viewerIsArtist },
                    artistSubtitle = artist
                        ?.let { listOf(it.category, it.genre).filter(String::isNotBlank) }
                        ?.joinToString(ThreadContext.SEPARATOR)
                        .orEmpty(),
                    artistScore = artist?.score?.takeIf { it > 0 },
                    messages = ChatRealtimeLogic.mergePreservingOptimistic(
                        server = serverMessages,
                        existing = state.messages,
                    ),
                    isLoading = false,
                )
            }
            hydrateArtist(thread, viewerIsArtist)
            loadGigContext(thread, viewerIsArtist)
            loadQuote(thread, viewerIsArtist)
            markReadBestEffort()
        }.onFailure { e -> _state.update { it.copy(isLoading = false, error = e.message) } }
    }

    /**
     * Scroll-back: the page of history immediately older than what's on screen.
     *
     * [refresh] only ever fetches the newest page, so without this everything
     * before the 50th-newest message is unreachable — scrolling to the top of a
     * long conversation simply stopped. The cursor is the oldest message in
     * memory and the merge is the same one refresh uses, so a re-fetched
     * boundary row collapses by id and in-flight bubbles survive.
     *
     * Two guards, and the difference between them matters: a page SHORTER than
     * the one asked for means the thread has no more history, so paging retires
     * for good; a FAILED page means nothing about history, so it only unlocks
     * and the next scroll to the top tries again. Reading a dropped request as
     * "you've reached the beginning" would kill scroll-back for the session.
     */
    fun loadOlder() {
        if (loadingOlder || reachedStartOfHistory) return
        // Nothing on screen yet: the first page is [refresh]'s job, and there is
        // no cursor to page before.
        val oldest = _state.value.messages.minOfOrNull { it.sentAtEpochMs } ?: return
        loadingOlder = true
        viewModelScope.launch {
            runCatching { messagesRepository.listMessages(threadId, PAGE_SIZE, before = oldest) }
                .onSuccess { older ->
                    reachedStartOfHistory = older.size < PAGE_SIZE
                    _state.update {
                        it.copy(
                            messages = ChatRealtimeLogic.mergePreservingOptimistic(
                                server = older,
                                existing = it.messages,
                            ),
                        )
                    }
                }
            loadingOlder = false
        }
    }

    /**
     * Back on screen — from the background, or from a destination pushed on top.
     *
     * The socket is suspended while the app is away, so a thread left open goes
     * silent. Refresh first — that fills the gap even if the channel is slow to
     * rejoin — then re-establish; the generation token makes the re-subscribe
     * idempotent against the one already in flight.
     *
     * The FIRST call is swallowed: that is the resume that arrives with the
     * screen's first paint, and `init` has already loaded and subscribed for it.
     * The latch lives here rather than in [ResumeEffect] because navigation
     * disposes a destination's composition behind a pushed screen while this
     * ViewModel survives — a latch over there would reset and skip the resume on
     * the way back. Same rule, same reason, in [MessagesViewModel.onResumed].
     */
    fun onResumed() {
        if (!resumedOnce) {
            resumedOnce = true
            return
        }
        refresh()
        subscribeRealtime()
    }

    /**
     * Optimistic insert (`.sending`) then background write. Cap at 4000 chars —
     * same choke point as the composer, for both first send and retry.
     *
     * The bubble carries the TRIMMED text, not the raw draft, because the seam
     * trims before it inserts (`SupabaseMessagesRepository.send`) — so the row
     * Postgres broadcasts back carries the trimmed body too. The Realtime echo
     * collapses into an in-flight bubble only when the bodies match
     * ([ChatRealtimeLogic.receiveRealtimeMessage]), so a soft keyboard's trailing
     * space would otherwise leave the echo beside the placeholder: two bubbles
     * for the whole round trip, and a permanent phantom "Not sent · Tap to retry"
     * next to the delivered message if the insert lands but its response is lost.
     * Cap first, then trim, so the 4000-char ceiling still bounds the write.
     */
    fun send(body: String) {
        val text = body.take(MAX_MESSAGE_CHARS).trim()
        if (text.isEmpty()) return
        // Random, not the clock: the transcript is keyed by id in a LazyColumn,
        // which throws on a duplicate key rather than degrading. The prefix is
        // the part everything else matches on ([retryFailedMessage], the tests).
        val optimisticId = "optimistic-${UUID.randomUUID()}"
        val optimistic = Message(
            id = optimisticId,
            threadId = threadId,
            body = text,
            sentAtEpochMs = System.currentTimeMillis(),
            isMine = true,
            delivery = MessageDelivery.Sending,
        )
        _state.update { it.copy(messages = it.messages + optimistic, error = null) }
        deliver(optimisticId, text)
    }

    fun retryFailedMessage(messageId: String) {
        val message = _state.value.messages.firstOrNull { it.id == messageId } ?: return
        if (message.delivery != MessageDelivery.Failed) return
        _state.update {
            it.copy(messages = ChatRealtimeLogic.markDelivery(it.messages, messageId, MessageDelivery.Sending))
        }
        deliver(messageId, message.body)
    }

    private fun deliver(optimisticId: String, body: String) = viewModelScope.launch {
        runCatching { messagesRepository.send(threadId, body) }
            .onSuccess { server ->
                _state.update {
                    it.copy(
                        messages = ChatRealtimeLogic.reconcileSendSuccess(
                            existing = it.messages,
                            optimisticId = optimisticId,
                            server = server,
                        ),
                        // A retry that lands has to retire the message the failed
                        // attempt left behind: `send` clears it on its way in,
                        // `retryFailedMessage` reuses the existing bubble and so
                        // never passes through there, and once the failed bubble
                        // is gone the strip above the composer stops suppressing
                        // itself and would report a refresh that never failed.
                        error = null,
                    )
                }
                markReadBestEffort()
            }
            .onFailure { e ->
                _state.update {
                    it.copy(
                        messages = ChatRealtimeLogic.markDelivery(
                            it.messages,
                            optimisticId,
                            MessageDelivery.Failed,
                        ),
                        error = e.message,
                    )
                }
                // The bubble may well be off-screen by now — the buzz is how the
                // user learns the send failed without scrolling back to find it.
                _events.send(ChatEvent.SendFailed)
            }
    }

    private fun subscribeRealtime() = viewModelScope.launch {
        subscribeGeneration += 1
        val myGeneration = subscribeGeneration
        // Tear down any prior channel before joining (foreground re-subscribe).
        subscription?.cancel()
        subscription = null
        val sub = messagesRepository.subscribeMessages(threadId) { incoming ->
            // Realtime callback may arrive off Main; ViewModel updates must be serialised.
            viewModelScope.launch {
                if (myGeneration != subscribeGeneration) return@launch
                _state.update {
                    it.copy(messages = ChatRealtimeLogic.receiveRealtimeMessage(it.messages, incoming))
                }
                // An inbound message seen while the thread is open must not
                // resurrect the unread badge on the next inbox refresh. Our own
                // echo needs no write.
                if (!incoming.isMine) markReadBestEffort()
            }
        }
        if (myGeneration != subscribeGeneration) {
            sub.cancel()
            return@launch
        }
        subscription = sub
    }

    /**
     * The gig behind this thread.
     *
     * One row by id rather than the seat's whole list: the chat needs exactly one
     * booking, and `listForArtist()` would pull every gig the artist has ever had
     * (plus its calendar side effect) to render a status line. Failure is silent
     * — the strip degrades to the inquiry shape, which is also what a genuinely
     * bookingless thread shows.
     */
    private fun loadGigContext(thread: Thread?, viewerIsArtist: Boolean) = viewModelScope.launch {
        val bookingId = thread?.bookingId
        if (bookingId == null) {
            _state.update { it.copy(context = ThreadContext.INQUIRY) }
            return@launch
        }
        val booking = runCatching { bookingsRepository.fetchOne(bookingId) }.getOrNull()
        _state.update {
            it.copy(
                context = booking?.let { row -> ThreadContext.from(row, viewerIsArtist) }
                    // Keep the id even when the row can't be read, so the details
                    // sheet can still route to the booking.
                    ?: ThreadContext(bookingId = bookingId),
            )
        }
    }

    /**
     * Pull the thread's artist into the by-id cache when it is cold, then
     * re-project the header. Reaching the chat from a push deep link skips the
     * inbox entirely, so this is often the first time the artist is fetched.
     *
     * Only on the client seat. On the artist seat `thread.artistId` IS the
     * viewer, and nothing renders it: the title keeps the client's name, and
     * `artistId` is nulled in [refresh] so the details sheet never offers the
     * profile row these two fields feed. Fetching it would be a round trip for
     * three assignments no surface reads.
     */
    private fun hydrateArtist(thread: Thread?, viewerIsArtist: Boolean) = viewModelScope.launch {
        if (viewerIsArtist) return@launch
        val artistId = thread?.artistId ?: return@launch
        if (artistsRepository.find(artistId) != null) return@launch
        val artist = artistsRepository.ensureFull(artistId) ?: return@launch
        _state.update { state ->
            state.copy(
                title = if (state.viewerIsArtist) state.title else artist.name,
                artistSubtitle = listOf(artist.category, artist.genre)
                    .filter(String::isNotBlank)
                    .joinToString(ThreadContext.SEPARATOR),
                artistScore = artist.score.takeIf { it > 0 },
            )
        }
    }

    private fun markReadBestEffort() = viewModelScope.launch {
        // `threads` keeps one unread counter per side, so the write has to name
        // the viewer's seat — and the loaded row is what decides it. No row means
        // no seat AND nothing to clear: either the viewer isn't a participant, or
        // [refresh] is still in flight and marks read again the moment it lands.
        val loaded = _state.value
        if (loaded.thread != null) {
            runCatching { messagesRepository.markThreadRead(threadId, loaded.viewerIsArtist) }
        }
        // The BROADCAST, gated on the Privacy screen's "Show when I've read
        // messages" switch (design 62). `markThreadReadReceipt` is the
        // `mark_thread_read` RPC, which writes `thread_reads` — the row the
        // COUNTERPARTY reads to render "Read by …". The badge PATCH above is
        // deliberately NOT gated: that counter is the viewer's own and nobody
        // else can see it, so hiding receipts must not leave someone staring at
        // an unread count for a conversation they have read.
        //
        // Read per call rather than cached: the switch is on another screen and
        // can be flipped while this thread is open, which is exactly the moment
        // someone is watching for it to take effect.
        //
        // Wrapped for two different reasons, and they resolve to opposite
        // answers — which is why this is not one decision.
        //
        // It has to be wrapped at all because the read goes to DataStore, which
        // throws on a corrupt or unreadable file, and an escape from HERE is
        // expensive out of all proportion to the preference: it aborts the rest
        // of this coroutine, so the "mark as unread" flag below is never retired
        // and the receipt below that is never re-read. Everything outside this
        // `if` therefore runs regardless of what the preference says or whether
        // it can be read at all.
        //
        // It **fails closed** — `false`, not `true` — because this one call is
        // the only thing here the COUNTERPARTY can see. An absent key means
        // enabled (nobody has opted out yet; that default lives in
        // `PrivacyPreferences` and is unchanged), but an unreadable store means
        // we do not know, and the two must not collapse into the same answer:
        // among the people whose preference we cannot read are the ones who
        // turned it off. Broadcasting their read status is an unrecoverable
        // privacy failure; not broadcasting costs the other side a "Read · 9:14
        // am" caption until the store is readable again. Those are not the same
        // size of mistake.
        if (runCatching { readReceipts.enabled() }.getOrDefault(false)) {
            runCatching { messagesRepository.markThreadReadReceipt(threadId) }
        }
        // Opening the thread also retires an explicit "mark as unread" — the
        // reader has now, demonstrably, read it.
        runCatching { flagsStore.clearMarkedUnread(threadId) }
        // Only ever overwritten with a VALUE. Null means three different things
        // here — no receipts row, nothing read yet, and a read that failed (the
        // seam swallows its own throw) — and this runs on every inbound message,
        // so assigning null unconditionally made one dropped request wipe a
        // receipt the counterparty had genuinely left: the "Read · 9:14 am"
        // caption blinked out mid-conversation. Receipts only ever move forward,
        // so the last known one is never wrong for longer than the next call.
        val readAt = runCatching { messagesRepository.counterpartLastRead(threadId) }.getOrNull()
        if (readAt != null) _state.update { it.copy(counterpartLastReadAt = readAt) }
    }

    /**
     * The quote standing between these two people, if any.
     *
     * Reads the viewer's OWN request list — RLS restricts it to their side, so
     * this is the same query the requests screens make and needs no seat
     * argument beyond choosing which list to ask for. Failure is silent and
     * leaves the card absent: a quote that cannot be read is not a quote that
     * can be accepted, and a card with dead buttons is worse than no card.
     */
    private fun loadQuote(thread: Thread?, viewerIsArtist: Boolean) = viewModelScope.launch {
        // Nothing a bookingless thread can be matched against, or a thread that
        // is about a booking rather than a negotiation: no request list is worth
        // fetching for either. [ThreadQuote.pick] applies the same two rules, so
        // this is a round trip saved, not a second opinion.
        if (thread == null || !thread.bookingId.isNullOrBlank() || thread.artistId.isBlank()) {
            _state.update { it.copy(quote = null) }
            return@launch
        }
        val rows = runCatching {
            if (viewerIsArtist) requests.listForArtist() else requests.listForClient()
        }.getOrNull() ?: return@launch
        val quote = ThreadQuote.pick(
            requests = rows,
            thread = thread,
            viewerIsArtist = viewerIsArtist,
            nowMs = System.currentTimeMillis(),
        )
        _state.update { it.copy(quote = quote) }
    }

    /**
     * Accept the standing quote: freeze the terms, in this conversation.
     *
     * Narrated in three phases (design 70) that each wait on real work, and the
     * event fires only after the write returned — nobody is told the terms are
     * agreed for a row the server never took.
     *
     * The third phase re-reads the row rather than patching the card locally.
     * That is the whole of what accepting does: there is no booking at the end
     * of it and no destination to land on — see [ChatEvent.QuoteAccepted] for
     * why, and [ChatQuoteCard]'s frozen copy for what the reader is told
     * instead.
     */
    fun acceptQuote() {
        val quote = _state.value.quote ?: return
        if (!quote.actionable) return
        if (_state.value.quoteAction is QuoteAction.Accepting) return
        // Phase 1 is genuinely complete at this point: the amount is captured
        // and cannot change under the write.
        _state.update { it.copy(quoteAction = QuoteAction.Accepting(QuotePhase.Locking)) }
        viewModelScope.launch {
            _state.update { it.copy(quoteAction = QuoteAction.Accepting(QuotePhase.Saving)) }
            val saved = runCatching { requests.accept(quote.requestId) }
            if (saved.isFailure) {
                _state.update { it.copy(quoteAction = QuoteAction.Failed(ACCEPT_FAILED)) }
                return@launch
            }
            _state.update { it.copy(quoteAction = QuoteAction.Accepting(QuotePhase.Confirming)) }
            // Re-read rather than patching the card locally: the same row is what
            // the bookings surfaces read, and a card that says "accepted" off a
            // local guess would disagree with them if the write raced anything.
            loadQuote(_state.value.thread, _state.value.viewerIsArtist).join()
            _state.update { it.copy(quoteAction = QuoteAction.Idle) }
            _events.send(ChatEvent.QuoteAccepted)
        }
    }

    fun openCounter() = _state.update { it.copy(countering = true, quoteAction = QuoteAction.Idle) }

    fun dismissCounter() = _state.update { it.copy(countering = false) }

    fun dismissQuoteError() = _state.update { it.copy(quoteAction = QuoteAction.Idle) }

    /**
     * Counter the standing quote with a different amount.
     *
     * A counter flips the row to `countered` and, by [ThreadQuote.decidesNext],
     * hands the decision to the OTHER seat — so the card the counter-er sees
     * afterwards has no buttons on it. That is the point: you don't get to
     * answer your own offer.
     */
    fun counterQuote(amountInr: Int) {
        val quote = _state.value.quote ?: return
        if (!quote.actionable || amountInr <= 0) return
        _state.update { it.copy(countering = false) }
        viewModelScope.launch {
            runCatching { requests.counter(quote.requestId, amountInr) }
                .onFailure {
                    _state.update { s -> s.copy(quoteAction = QuoteAction.Failed(COUNTER_FAILED)) }
                    return@launch
                }
            loadQuote(_state.value.thread, _state.value.viewerIsArtist)
        }
    }

    fun openDetails() = _state.update { it.copy(showDetails = true) }

    /** Closing takes the sheet's own failure line with it — see [ChatUiState.actionError]. */
    fun dismissDetails() = _state.update {
        // `failedReport` deliberately survives: see [ChatUiState.failedReport].
        it.copy(showDetails = false, reportOutcome = null, actionError = null)
    }

    fun dismissSafetyBanner() = viewModelScope.launch { flagsStore.dismissSafetyBanner(threadId) }

    fun toggleStarred() = viewModelScope.launch { flagsStore.toggleStarred(threadId) }

    fun toggleArchived() = viewModelScope.launch { flagsStore.toggleArchived(threadId) }

    fun markUnread() = viewModelScope.launch { flagsStore.markUnread(threadId) }

    /**
     * Mute or unmute this conversation for the viewer only.
     *
     * Optimistic, then reverted on failure. The revert matters more here than it
     * does for the device-local flags: this control claims to stop notifications
     * arriving on the lock screen, so a toggle that flipped in the UI but never
     * reached the server would leave someone expecting silence and getting
     * pushes. `thread.muted` is also patched so a details sheet reopened before
     * the next refresh doesn't read the stale row.
     */
    fun toggleMuted() {
        val next = !_state.value.muted
        _state.update {
            it.copy(muted = next, thread = it.thread?.copy(muted = next), actionError = null)
        }
        viewModelScope.launch {
            runCatching { messagesRepository.setMuted(threadId, next) }
                .onFailure { _ ->
                    // Deliberately not `e.message`: this row is one tap inside a
                    // sheet, and a Postgres/transport string there says nothing
                    // the reader can act on.
                    _state.update {
                        it.copy(
                            muted = !next,
                            thread = it.thread?.copy(muted = !next),
                            actionError = MUTE_FAILED,
                        )
                    }
                }
        }
    }

    /**
     * Block or unblock the other person (migration 0087).
     *
     * Keyed on the counterparty's USER id, not this thread: blocking is about a
     * person, so every conversation with them leaves the inbox, not just this
     * one. No-ops when the seat is unresolved rather than guessing an id — the
     * UI hides the action in that case, and this is the second guard.
     *
     * What it does NOT do, deliberately: 0087's v1 scope is client-side
     * filtering only. The blocked person is not told, is not prevented from
     * sending, and their pushes are not suppressed (mute is the control for
     * that). The copy in the details sheet says exactly this — see
     * [ThreadDetailsSheet] — because a block that quietly under-delivers is
     * worse for someone's safety than one that is honest about its limits.
     */
    fun toggleBlocked() {
        val target = _state.value.counterpartId ?: return
        _state.update { it.copy(actionError = null) }
        viewModelScope.launch {
            // The store owns the optimistic flip and its revert; all that is
            // left here is saying so when the write didn't land.
            if (!blockedUsers.toggle(target)) {
                _state.update { it.copy(actionError = BLOCK_FAILED) }
            }
        }
    }

    /**
     * File a report against this conversation.
     *
     * `reportConversation` soft-fails rather than throwing — a moderation outage
     * must not block a chat — but it SAYS which of the three things happened,
     * and this used to throw that answer away and claim delivery for all of
     * them. Telling someone their safety report is with the team when nothing
     * anywhere is holding it is the worst sentence on this surface.
     *
     * So the three outcomes are three different states, exactly as the artist
     * profile's report does it (screen 56):
     *  - [ReportOutcome.Sent] — it reached `public.reports`. Receipt, success buzz.
     *  - [ReportOutcome.Queued] — it is in this install's log and nowhere else.
     *    Receipt, different words, and no success claim.
     *  - [ReportOutcome.Failed] — nothing holds it. Not a receipt at all: a
     *    failure with the reader's own words kept for a retry.
     */
    fun reportConversation(reason: String, details: String? = null) {
        // One report per tap. The sheet keeps the form up for the whole round
        // trip — it has no outcome to render yet — so the CTA stays live, and a
        // double tap used to file the row twice.
        if (_state.value.isSubmittingReport) return
        reportGeneration += 1
        val myGeneration = reportGeneration
        _state.update {
            it.copy(reportOutcome = null, failedReport = null, isSubmittingReport = true)
        }
        viewModelScope.launch {
            val outcome = runCatching { reports.reportConversation(threadId, reason, details) }
                // A throw is a contract violation (the interface promises not
                // to), so it is the WORST of the three claims and not the middle
                // one: we know nothing about where the report went.
                .getOrDefault(ReportOutcome.Failed)
            _state.update { state ->
                // The flag belongs to the attempt that is finishing, so it is
                // always released — a superseded completion that returned early
                // without clearing it would wedge the form shut for good.
                val settled = state.copy(isSubmittingReport = false)
                when {
                    // Superseded or retired (see [onCleared]): the write is not
                    // ours to make, and claiming an outcome for a report the
                    // reader has moved on from is how a dismissed banner comes
                    // back from the dead.
                    myGeneration != reportGeneration -> settled
                    outcome == ReportOutcome.Failed ->
                        settled.copy(failedReport = PendingReport(reason, details), reportOutcome = null)
                    else -> settled.copy(reportOutcome = outcome, failedReport = null)
                }
            }
        }
    }

    /** Re-file the report the reader already wrote, from the failure banner. */
    fun retryReport() {
        val pending = _state.value.failedReport ?: return
        reportConversation(pending.reason, pending.details)
    }

    /**
     * Give up on a lost report.
     *
     * A separate control from [retryReport] and never a timeout: the banner says
     * nothing is holding the report, so it must not vanish on its own while that
     * is still true.
     */
    fun discardFailedReport() = _state.update { it.copy(failedReport = null) }

    override fun onCleared() {
        reportGeneration += 1
        subscribeGeneration += 1
        subscription?.cancel()
        subscription = null
        super.onCleared()
    }

    private companion object {
        const val FALLBACK_TITLE = "Chat"
        const val MUTE_FAILED = "Couldn't update notifications for this conversation."
        const val ACCEPT_FAILED = "Couldn't accept this quote. Nothing changed — try again."
        const val COUNTER_FAILED = "Couldn't send your counter. Nothing changed — try again."
        const val BLOCK_FAILED = "Couldn't update your block list. Nothing changed."

        /**
         * Rows per fetch, for the newest page and every scroll-back page alike —
         * they have to agree, because "shorter than a page" is what tells
         * [loadOlder] it has reached the start of the thread.
         */
        const val PAGE_SIZE = 50
    }
}
