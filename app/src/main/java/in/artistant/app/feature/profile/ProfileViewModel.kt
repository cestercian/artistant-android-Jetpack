package `in`.artistant.app.feature.profile

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.core.config.AppEnvironment
import `in`.artistant.app.data.model.Booking
import `in`.artistant.app.data.model.BookingStatus
import `in`.artistant.app.data.model.SelfProfile
import `in`.artistant.app.data.repository.ArtistsRepository
import `in`.artistant.app.data.repository.AvailabilityDraft
import `in`.artistant.app.data.repository.BookingsRepository
import `in`.artistant.app.data.repository.ScoreRepository
import `in`.artistant.app.data.repository.UsersRepository
import `in`.artistant.app.designsystem.theme.AppRole
import `in`.artistant.app.feature.booking.BookingDraftStore
import `in`.artistant.app.feature.paywall.EntitlementStore
import `in`.artistant.app.feature.saved.SavedStore
import `in`.artistant.app.platform.auth.SessionManager
import `in`.artistant.app.platform.calendar.CalendarSyncService
import `in`.artistant.app.platform.storage.AppPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class ProfileUiState(
    val profile: SelfProfile? = null,
    val role: AppRole = AppRole.Client,
    val isLoading: Boolean = true,
    val error: String? = null,
    val actionMessage: String? = null,
    val actionError: String? = null,
    val showSignOutConfirm: Boolean = false,
    val showHelp: Boolean = false,
    val calendarSyncEnabled: Boolean = false,
    val calendarHasPermission: Boolean = false,
    val calendarTitle: String = "Artistant",
    val calendars: List<CalendarSyncService.CalendarOption> = emptyList(),
    /**
     * The signed-in account's email, straight off the cached auth session (the
     * `public.users` row does not carry one). Read-only identity, rendered
     * MASKED — see [maskedEmail]. Null for a session without an email address,
     * which is legitimate: OAuth providers can withhold it, and the row is
     * simply omitted then rather than showing a blank value.
     */
    val email: String? = null,
    /**
     * The calendar year this account was created, off the same cached auth
     * session as [email]. Null when there is no session to read — a provider can
     * withhold the date, and a signed-out state has none at all.
     */
    val joinedYear: Int? = null,
    /**
     * Profile stats — Bookings = still live (see [liveBookingsCount]), Completed
     * = finished.
     *
     * Null means "not read yet", and it is NOT zero: the bookings fetch is
     * best-effort, so an offline / RLS / not-signed-in failure used to leave the
     * defaults in place and print "0 BOOKINGS · 0 COMPLETED" — a confident claim
     * about the account's track record that is indistinguishable from a genuinely
     * new user, with no error and no retry beside it. Rendered as an em dash
     * until a read actually answers (see [profileStatValue]). A read that fails
     * AFTER one succeeded keeps the last known counts rather than blanking them.
     *
     * [savedCount] has no such state: it is fed by the local [SavedStore] set,
     * which always has an answer.
     */
    val bookingsCount: Int? = null,
    val savedCount: Int = 0,
    val completedCount: Int? = null,
    /**
     * The artist band on design screens 47 / 69 — "Gigs · Bookability · Completed".
     *
     * Same null-means-unknown rule as the client counters above, and for the same reason: the
     * two reads that feed them (the artist's own bookings, and `score_history`'s latest row)
     * are best-effort, and a score of 0 is a real, publishable value that an unread score must
     * not be confused with.
     */
    val gigsCount: Int? = null,
    val bookabilityScore: Int? = null,
    /**
     * The "Manage availability" row's subtitle — "Thu–Sun evenings". Null until the read
     * answers, and the row simply drops its second line rather than inventing a schedule.
     */
    val availabilitySummary: String? = null,
    /** Whether Play Billing reports an active subscription. Drives the "Subscription" row. */
    val isPro: Boolean = false,
    /** In flight while the "Switch to artist mode" pill (screen 26) writes the new role. */
    val switchingRole: Boolean = false,
    val feedbackSending: Boolean = false,
    val feedbackStatus: String? = null,
    val feedbackOk: Boolean = false,
) {
    val displayName: String
        get() = profile?.fullName?.trim()?.takeIf { it.isNotEmpty() } ?: "You"

    /**
     * The year the identity strip and [subtitle] print. [joinedYear] when the
     * session answered, otherwise today — the least-surprising fallback, and the
     * one iOS uses, since a made-up vintage is worse than a current one.
     */
    val vintageYear: Int
        get() = joinedYear ?: Calendar.getInstance().get(Calendar.YEAR)

    /** "Host · Bengaluru · joined 2024" (screen 26). Drops the city when there isn't one. */
    val subtitle: String
        get() {
            val city = profile?.city?.trim().orEmpty()
            val roleNoun = if (role == AppRole.Client) "Host" else "Artist"
            return listOf(roleNoun, city, "joined $vintageYear")
                .filter { it.isNotBlank() }
                .joinToString(" · ")
        }

    val handleLabel: String?
        get() = profile?.handle?.trim()?.takeIf { it.isNotEmpty() }?.let { "@$it" }

    /** The email row's rendered value, or null when there is no email to show. */
    val maskedEmail: String?
        get() = email?.trim()?.takeIf { it.isNotEmpty() }?.let(::maskEmail)

    val subscriptionsEnabled: Boolean get() = AppEnvironment.subscriptionsEnabled

    /**
     * What the "Subscription" row says on its second line.
     *
     * Three answers, not two. With Play Billing dormant (`subscriptionsEnabled` off) the app
     * has no way to know whether anyone is subscribed to anything, so it says the plan is not
     * on sale rather than asserting "Free plan" — which would be a claim about an entitlement
     * nothing checked.
     */
    val subscriptionSubtitle: String
        get() = when {
            !subscriptionsEnabled -> "Not available yet"
            isPro -> "Artistant Pro"
            else -> "Free plan"
        }
}

/**
 * Render-time PII mask for the account email: `yashafaid@gmail.com` →
 * `y•••d@gmail.com`.
 *
 * Masking happens at RENDER, never in storage — the stored value is untouched,
 * we just refuse to put the whole address on a screen that a shoulder-surfer or
 * a screenshot can read. Keeping the first and last character of the local part
 * is what lets the owner still recognise WHICH of their addresses is signed in,
 * which is the entire point of showing the row.
 *
 * Anything that is not a single-`@` address is returned verbatim rather than
 * mangled: a value we cannot parse is a value we cannot safely shorten, and a
 * half-masked string would be worse than either extreme. A one-character local
 * part has no distinct last character, so it degrades to `x•••@domain`.
 */
internal fun maskEmail(email: String): String {
    val at = email.indexOf('@')
    // Needs a local part, an @, and a domain — reject 0 matches, a leading @,
    // a trailing @, and (via lastIndexOf) any address carrying a second @.
    if (at <= 0 || at == email.length - 1 || at != email.lastIndexOf('@')) return email
    val local = email.substring(0, at)
    val domain = email.substring(at)
    val head = local.first()
    val tail = local.drop(1).takeLast(1)
    return "$head•••$tail$domain"
}

/**
 * Identity + settings state for design screens 26 (client Profile tab) and 47 / 69 (the pushed
 * Account list).
 *
 * Both screens read this same ViewModel because they are two views of one account: the tab root
 * is the identity page, the pushed list is everything you can change about it. Export and
 * delete used to live here too and now have their own screens and their own ViewModels
 * ([DataExportViewModel], [DeleteAccountViewModel]) — each is a multi-state flow with a
 * progress model of its own, and folding three of those into one state class is what made the
 * old single-screen version carry `isExporting`, `isDeleting`, `pendingExport` and two
 * different confirmation flags at once.
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val users: UsersRepository,
    private val session: SessionManager,
    private val prefs: AppPreferences,
    private val calendarSync: CalendarSyncService,
    private val savedStore: SavedStore,
    private val draftStore: BookingDraftStore,
    private val bookingsRepository: BookingsRepository,
    private val artists: ArtistsRepository,
    private val scores: ScoreRepository,
    private val entitlements: EntitlementStore,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            calendarSync.ui.collect { cal ->
                _state.update {
                    it.copy(
                        calendarSyncEnabled = cal.enabled,
                        calendarHasPermission = cal.hasPermission,
                        calendarTitle = cal.calendarTitle,
                        calendars = cal.calendars,
                    )
                }
            }
        }
        viewModelScope.launch {
            savedStore.ids.collect { ids ->
                _state.update { it.copy(savedCount = ids.size) }
            }
        }
        viewModelScope.launch {
            entitlements.isEntitled.collect { pro -> _state.update { it.copy(isPro = pro) } }
        }
    }

    fun refresh() = viewModelScope.launch {
        _state.update { it.copy(isLoading = true, error = null) }
        val role = prefs.role.first()
        // Off the cached session, not a network call — safe to read on every
        // refresh, and it is the only place the account email and the signup
        // date exist (the `public.users` row carries neither). Published BEFORE
        // the profile fetch so the identity row survives a failed refresh: both
        // are already known locally, and blanking them because an unrelated
        // request 500'd would be inventing a gap.
        val user = session.currentUser
        _state.update {
            it.copy(
                email = user?.email,
                joinedYear = user?.createdAt?.toEpochMilliseconds()?.let { ms ->
                    Calendar.getInstance().apply { timeInMillis = ms }.get(Calendar.YEAR)
                },
            )
        }
        runCatching { users.fetchSelfProfile() }
            .onSuccess { profile ->
                _state.update {
                    it.copy(
                        profile = profile,
                        role = profile?.role ?: role,
                        isLoading = false,
                    )
                }
            }
            .onFailure { e ->
                _state.update {
                    it.copy(isLoading = false, error = e.message ?: "Couldn't load profile")
                }
            }
        // Play Billing is queried rather than assumed; the store answers "not entitled" when
        // subscriptions are dormant, so this is inert until the flag flips.
        runCatching { entitlements.refresh() }
        if (_state.value.role == AppRole.Client) refreshClientStats() else refreshArtistStats()
    }

    /**
     * The client band — Upcoming / Saved / Completed (design 26).
     *
     * Resolved role, not the cached pref: the profile fetch above is what settles it. Both
     * reads are best-effort and the counters stay NULL rather than 0 until one answers; see
     * [ProfileUiState.bookingsCount].
     */
    private suspend fun refreshClientStats() {
        runCatching { bookingsRepository.listForClient() }
            .onSuccess { bookings ->
                _state.update {
                    it.copy(
                        bookingsCount = liveBookingsCount(bookings),
                        completedCount = completedBookingsCount(bookings),
                        savedCount = savedStore.ids.value.size,
                    )
                }
            }
        // No runCatching: refreshFromServer swallows every network/RLS failure
        // itself and now re-throws CancellationException, which wrapping it here
        // would put straight back in the bin.
        savedStore.refreshFromServer()
    }

    /**
     * The artist band — Gigs / Bookability / Completed (design 47 / 69) plus the availability
     * summary the "Manage availability" row prints.
     *
     * Three independent best-effort reads rather than one: a score that fails must not blank
     * the gig counts, and an availability read that fails must not blank either. Each writes
     * only its own fields.
     */
    private suspend fun refreshArtistStats() {
        runCatching { bookingsRepository.listForArtist() }
            .onSuccess { bookings ->
                _state.update {
                    it.copy(
                        gigsCount = liveBookingsCount(bookings),
                        completedCount = completedBookingsCount(bookings),
                    )
                }
            }
        runCatching { scores.breakdownForSelf() }
            .onSuccess { breakdown -> _state.update { it.copy(bookabilityScore = breakdown.score) } }
        runCatching { artists.fetchSelfAvailability() }
            .onSuccess { availability ->
                _state.update { it.copy(availabilitySummary = availabilitySummary(availability)) }
            }
    }

    fun showSignOutConfirm() = _state.update { it.copy(showSignOutConfirm = true) }
    fun dismissSignOutConfirm() = _state.update { it.copy(showSignOutConfirm = false) }

    fun showHelp() = _state.update {
        it.copy(showHelp = true, feedbackStatus = null, feedbackOk = false, feedbackSending = false)
    }
    fun dismissHelp() = _state.update {
        it.copy(showHelp = false, feedbackStatus = null, feedbackOk = false, feedbackSending = false)
    }

    fun submitFeedback(body: String, isBug: Boolean) = viewModelScope.launch {
        if (_state.value.feedbackSending) return@launch
        _state.update { it.copy(feedbackSending = true, feedbackStatus = null) }
        val ok = bookingsRepository.submitFeedback(body, isBug)
        _state.update {
            it.copy(
                feedbackSending = false,
                feedbackOk = ok,
                feedbackStatus = if (ok) {
                    "Thanks — we got it."
                } else {
                    "Couldn't send — check your connection and try again."
                },
            )
        }
    }

    /**
     * "Switch to artist mode" (design screen 26 — "A host who starts performing switches here
     * instead of signing up again").
     *
     * One write: `public.users.role`, which `users_update_self` (mig 0002) lets the account
     * change for itself. Everything downstream follows from it — the root gate re-reads the
     * profile, sees an artist with no `setup_complete`, and routes into the setup wizard, which
     * is the same place a fresh artist signup lands. So this does NOT create an artists row or
     * publish anything: it changes what the account IS and hands the rest to the flow that
     * already knows how to build an act.
     *
     * The upsert re-sends the handle, name and city unchanged because
     * [UsersRepository.upsertSelfProfile] is the only write we have and it takes the whole row;
     * an incomplete profile (no handle) is refused here rather than sent, since the upsert
     * would write a blank handle over the user's own. `termsAccepted = false` is an assertion
     * of nothing, not a revocation — see that method's contract.
     *
     * [onSwitched] is the caller's re-route (the root gate's `retryRouting`). It runs only on a
     * successful write, so a failed switch leaves the user where they are with the reason on
     * screen rather than half-moved.
     */
    fun switchToArtistMode(onSwitched: () -> Unit) = viewModelScope.launch {
        val current = _state.value
        if (current.switchingRole) return@launch
        // A profile we never loaded and a profile with no username are the same refusal: the
        // upsert takes the whole row, so sending it without a handle would write a blank one
        // over the user's own. Reported rather than dropped — a pill that does nothing on tap
        // is the failure mode the design's notes rule out.
        val profile = current.profile
        val handle = profile?.handle?.trim().orEmpty()
        if (profile == null || handle.isEmpty()) {
            _state.update {
                it.copy(actionError = "Finish your profile first — an artist needs a username.")
            }
            return@launch
        }
        _state.update { it.copy(switchingRole = true, actionError = null) }
        runCatching {
            users.upsertSelfProfile(
                handle = handle,
                fullName = profile.fullName.orEmpty(),
                city = profile.city.orEmpty(),
                role = AppRole.Artist,
                termsAccepted = false,
            )
            prefs.setRole(AppRole.Artist)
        }.onSuccess {
            _state.update { it.copy(switchingRole = false, role = AppRole.Artist) }
            onSwitched()
        }.onFailure { e ->
            _state.update {
                it.copy(
                    switchingRole = false,
                    actionError = e.message ?: "Couldn't switch to artist mode.",
                )
            }
        }
    }

    fun signOut() = viewModelScope.launch {
        _state.update { it.copy(showSignOutConfirm = false) }
        // Account-scoped state held in memory, which the next session must not
        // inherit. The booking draft is a @Singleton that outlives the session:
        // the artist, venue, guest count and the free-text directions of a
        // half-composed booking — plus the package tier tapped on a profile,
        // which is what the next account's booking screen would silently open
        // on — all stayed resident without this.
        calendarSync.clearSessionState()
        draftStore.clear()
        val message = cleanUpAfterSignOut(
            signOut = { session.signOut() },
            stillSignedIn = { session.currentUserId != null },
            wipeLocalState = { prefs.wipeAll(); savedStore.reset() },
        )
        if (message != null) _state.update { it.copy(actionError = message) }
    }

    /** Tap-to-dismiss for the two transient lines under the settings list. */
    fun clearActionFeedback() = _state.update { it.copy(actionMessage = null, actionError = null) }

    /**
     * A system handoff the screen could not complete — no browser, no
     * notification-settings activity, no share target for the export. Reported
     * on the same line as every other action failure instead of throwing
     * ActivityNotFoundException out of a click handler and taking the tab with it.
     */
    fun reportActionError(message: String) = _state.update { it.copy(actionError = message) }

    fun manageAvailabilityMissingNav() {
        _state.update {
            it.copy(actionMessage = "Open Account from the artist Profile tab to manage availability.")
        }
    }

    fun setCalendarSyncEnabled(on: Boolean) = viewModelScope.launch {
        val ok = calendarSync.setEnabled(on)
        if (!ok) {
            _state.update {
                it.copy(actionMessage = "Calendar permission is required to sync gigs.")
            }
        }
    }

    fun selectCalendar(id: Long) = viewModelScope.launch {
        calendarSync.selectCalendar(id)
    }

    fun onCalendarPermissionResult(granted: Boolean) = viewModelScope.launch {
        if (granted) {
            calendarSync.setEnabled(true)
        } else {
            _state.update {
                it.copy(actionMessage = "Calendar permission denied — enable it in system Settings.")
            }
        }
    }
}

/**
 * The local cleanup that follows a SUCCESSFUL server-side account delete.
 *
 * Non-throwing by construction, because the call site sits inside
 * `runCatching { … }.onSuccess { }` — an inline lambda that catches nothing. A
 * throw there escapes `viewModelScope`, which installs no
 * CoroutineExceptionHandler, so it reaches the thread's uncaught handler and
 * kills the app moments AFTER the account was erased, with the local wipe
 * half-done. Neither step is hypothetical: [wipeCalendar] deletes through the
 * calendar provider with no permission check of its own, so a calendar
 * permission revoked since the toggle was enabled throws SecurityException, and
 * [signOut] posts a logout carrying a JWT whose user has just been deleted, over
 * a link that may already be gone.
 *
 * [wipeLocalState] is the DPDP §11 backstop. `SessionManager.signOut()` does the
 * network logout FIRST and clears prefs / saved ids after it returns, so a failed
 * logout skips the local wipe entirely and the deleted account's role, saved ids
 * and thread flags survive on the device. That half is done here when sign-out
 * fails, and the user is told to restart — the one part of this they can act on.
 *
 * @return the message to surface, or null when the cleanup finished cleanly.
 */
internal suspend fun cleanUpAfterAccountDelete(
    wipeCalendar: suspend () -> Unit,
    signOut: suspend () -> Unit,
    wipeLocalState: suspend () -> Unit,
): String? {
    // The mirrored gigs are the device owner's own calendar events. Failing to
    // remove them cannot un-delete the account, and on the ordinary path the
    // sign-out below replaces this screen before any message could be read, so
    // this failure is swallowed rather than reported.
    runCatching { wipeCalendar() }
    if (runCatching { signOut() }.isSuccess) return null
    runCatching { wipeLocalState() }
    return "Account deleted. Restart the app to finish signing out."
}

/**
 * The local cleanup that follows a PLAIN sign-out attempt.
 *
 * `SessionManager.signOut()` does the network logout first and clears prefs /
 * saved ids only after it returns, so anything that throws in between skips the
 * local wipe entirely. Which of the two failures happened decides what may be
 * done about it — and unlike the delete path, the answer is not always "wipe".
 *
 * - **The logout never landed** (offline, timeout, an unexpected status).
 *   supabase-kt rethrows without touching its stored session — it only reaches
 *   `clearSession()` on a logout it swallowed — so the account is still signed
 *   in and still using its role, saved ids and thread flags. Nothing was
 *   cleared, and nothing may be: wiping here would strip a live session's own
 *   state out from under it over a failure the user can simply retry. Report it
 *   and leave the device exactly as it was.
 * - **The logout landed and a step after it threw.** [AppPreferences.wipeAll] is
 *   a DataStore edit, which throws IOException, and the analytics/crash resets
 *   ahead of it are no more guaranteed. The session is gone, so the device is
 *   holding the departed account's local state with no session left to reach it
 *   — the DPDP §11 backstop, the same one [cleanUpAfterAccountDelete] applies
 *   when its own sign-out fails. Finish the wipe here, and say nothing: the user
 *   got exactly what they asked for, and the auth screen is already replacing
 *   this one as the cleared session propagates.
 *
 * [stillSignedIn] is the whole discriminator, and it is read AFTER the attempt.
 *
 * Non-throwing by construction, like its sibling: the call site is a bare
 * `viewModelScope.launch`, which installs no CoroutineExceptionHandler.
 *
 * @return the message to surface, or null when there is nothing to tell.
 */
internal suspend fun cleanUpAfterSignOut(
    signOut: suspend () -> Unit,
    stillSignedIn: () -> Boolean,
    wipeLocalState: suspend () -> Unit,
): String? {
    val failure = runCatching { signOut() }.exceptionOrNull() ?: return null
    if (stillSignedIn()) return failure.message ?: "Sign out failed"
    runCatching { wipeLocalState() }
    return null
}

/**
 * How many of [bookings] are still LIVE — the number under the profile header's
 * "Upcoming" column, read beside "Completed".
 *
 * The shipped rule was `status != Completed`, i.e. "everything that hasn't
 * finished". That put cancelled and disputed bookings — and `Unknown`, the
 * decode fallback for a status this build can't interpret — in the same column a
 * user reads as "work I have on". Cancel all three of your bookings and the
 * header still said 3, next to a Completed column reading 0, which describes a
 * user with three live bookings rather than one with none.
 *
 * So the stat means *in flight*: awaiting the artist's answer, or accepted and
 * not yet played. Cancelled and disputed are terminal — the booking is over,
 * it just didn't happen — and `Unknown` is by construction uninterpretable, so
 * none of them can be asserted to be live.
 *
 * Written as an exhaustive `when` over an allow-list rather than a `!=` chain,
 * for two reasons. The compiler fails the build when a case is added to
 * [BookingStatus], so a new server status has to be classified here instead of
 * being silently swept into "live" — which is exactly how `Unknown` ended up
 * counted. And the safe default when someone does classify it is the one this
 * shape encourages: not-live.
 *
 * A confirmed booking whose date has already passed still counts as live. The
 * server flips it to `completed` on its own schedule, and the Completed column
 * is keyed strictly on that status — so excluding it here would drop the booking
 * out of BOTH columns until the server caught up. Better slightly stale than
 * briefly invisible.
 *
 * Note this is deliberately narrower than the drill-down list the stat opens,
 * which still shows cancelled bookings behind a red pill: the column answers
 * "how much do I have going", the list is where a cancelled booking stays
 * visible at all (the Bookings tab filters it out entirely).
 */
fun liveBookingsCount(bookings: List<Booking>): Int = bookings.count { it.status.isLive() }

/**
 * The profile header's "Completed" column — [liveBookingsCount]'s counterpart.
 *
 * A plain equality on [BookingStatus.Completed], but pulled out beside it
 * rather than inlined at the call site: the two counts are asserted never to
 * double-count a single booking (`no status is counted in both columns`, in
 * the test suite), and that assertion is only worth anything if it calls the
 * SAME expression that ships — duplicating it in-test would let the two drift
 * apart silently.
 */
fun completedBookingsCount(bookings: List<Booking>): Int =
    bookings.count { it.status == BookingStatus.Completed }

/**
 * What one column of the profile stat band prints.
 *
 * A count we HAVE is printed. A count we could not read prints an em dash,
 * because "you have 0 bookings" and "we couldn't reach the server" are opposite
 * claims and the band is the only thing on the screen making either — the same
 * loaded-and-empty vs couldn't-load conflation `BlockedUsersStore.refresh()` was
 * changed to stop making, and the same em dash the artist dashboard already
 * prints for a score it couldn't read.
 *
 * The column stays TAPPABLE while unknown: the drill-down list does its own read
 * and surfaces the failure properly, so "—" plus a tap is the honest route to the
 * error the header used to hide behind a zero.
 */
fun profileStatValue(count: Int?): String = count?.toString() ?: "—"

/**
 * The "Manage availability" row's second line — "Thu–Sun evenings", "Fri, Sat", "Not set yet".
 *
 * Null in, null out: an availability read that failed must leave the row with ONE line rather
 * than claim the artist has set nothing. An artist who genuinely has no days open is a
 * different fact and says so.
 *
 * Consecutive weekdays collapse to a range because that is how the design writes it and how
 * people say it — "Thu–Sun" rather than "Thu, Fri, Sat, Sun" — and the run is detected against
 * the canonical week order rather than against the list's own order, which the server does not
 * promise. Times are summarised, not listed: a row is one line, and five start times is a
 * screen's worth of detail that the editor one tap away already shows properly.
 */
fun availabilitySummary(availability: AvailabilityDraft?): String? {
    if (availability == null) return null
    val days = availability.daysAvailable.map { it.trim() }.filter { it.isNotEmpty() }
    if (days.isEmpty()) return "Not set yet"
    val part = weekdayRangeLabel(days)
    val slot = timeOfDayLabel(availability.timeSlots)
    return if (slot == null) part else "$part $slot"
}

/** Canonical week order, so a range is detected against the WEEK, not against the list. */
private val WEEK = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

/** "Thu–Sun" for a consecutive run, else "Fri, Sat", else the raw list. */
internal fun weekdayRangeLabel(days: List<String>): String {
    val indices = days.mapNotNull { day ->
        WEEK.indexOfFirst { it.equals(day.take(3), ignoreCase = true) }.takeIf { it >= 0 }
    }.distinct().sorted()
    if (indices.isEmpty()) return days.joinToString(", ")
    val consecutive = indices.zipWithNext().all { (a, b) -> b == a + 1 }
    return if (consecutive && indices.size > 2) {
        "${WEEK[indices.first()]}–${WEEK[indices.last()]}"
    } else {
        indices.joinToString(", ") { WEEK[it] }
    }
}

/**
 * "evenings" / "afternoons" / "mornings", or null when the times say nothing useful.
 *
 * Keyed on the EARLIEST start time, because that is what decides whether a night is free: an
 * act that can start at 6pm and at 9pm is an evening act.
 */
internal fun timeOfDayLabel(times: List<String>): String? {
    val hours = times.mapNotNull { raw ->
        val digits = raw.trim().takeWhile { it.isDigit() }
        val hour = digits.toIntOrNull() ?: return@mapNotNull null
        val pm = raw.contains("pm", ignoreCase = true)
        val am = raw.contains("am", ignoreCase = true)
        when {
            pm && hour < 12 -> hour + 12
            am && hour == 12 -> 0
            else -> hour
        }
    }
    val earliest = hours.minOrNull() ?: return null
    return when {
        earliest < 12 -> "mornings"
        earliest < 17 -> "afternoons"
        else -> "evenings"
    }
}

private fun BookingStatus.isLive(): Boolean = when (this) {
    BookingStatus.PendingConfirm, BookingStatus.Confirmed -> true
    BookingStatus.Completed,
    BookingStatus.Cancelled,
    BookingStatus.Disputed,
    BookingStatus.Unknown,
    -> false
}

/** Build a share intent for an inline JSON export. */
fun exportShareIntent(json: String): Intent =
    Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_SUBJECT, "Artistant data export")
        putExtra(Intent.EXTRA_TEXT, json)
    }

/** Open a signed export URL in the browser. */
fun exportViewIntent(url: String): Intent =
    Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
