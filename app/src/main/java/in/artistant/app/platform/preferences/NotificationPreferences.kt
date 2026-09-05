package `in`.artistant.app.platform.preferences

import `in`.artistant.app.platform.storage.KeyValueStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The seven switches and the quiet-hours toggle on design screen 124.
 *
 * **Why DataStore and not a column.** The canonical schema
 * (`~/Desktop/ios-swift/supabase/migrations`, 105 files) has no notification-preference table
 * and no preference columns on `users`: `device_tokens` (mig 0001) stores a token per device
 * and nothing else, and the three push triggers (mig 0016) fan out unconditionally. Adding a
 * preference row is a schema change, and schema changes originate in the iOS repo. So these
 * are device preferences — and the screen SAYS so rather than implying the setting follows the
 * account, the same rule `PrivacyPreferences` follows for read receipts.
 *
 * **What "off" means today, precisely.** The server still sends: nothing on this phone can
 * stop a trigger firing. What a switch off means is that this client will not RAISE the
 * notification when the payload arrives — which is a real, honest effect for a payload the app
 * receives while running, and no effect at all for one the system posts on its own. The screen
 * states that boundary; it does not claim the server was told.
 *
 * **Defaults.** Everything transactional is ON, which is what the design draws and what the
 * app already does. [newActs] and [tipsAndOffers] — the two marketing categories — default OFF
 * and stay off unless someone turns them on: the design's whole note on this screen. A
 * marketing default of ON would also be a consent the signup flow never collected.
 */
@Singleton
class NotificationPreferences @Inject constructor(
    private val prefs: KeyValueStore,
) {
    /** "Quotes and replies" — a gig request, a counter, a reply in a thread. */
    val quotesAndReplies: Flow<Boolean> = prefs.bool(KEY_QUOTES, default = true)

    /** "Booking confirmed or declined". */
    val bookingUpdates: Flow<Boolean> = prefs.bool(KEY_BOOKING_UPDATES, default = true)

    /** "Show-day reminder" — the evening before. */
    val showDayReminder: Flow<Boolean> = prefs.bool(KEY_SHOW_DAY, default = true)

    /** "Load-in reminder" — three hours before. */
    val loadInReminder: Flow<Boolean> = prefs.bool(KEY_LOAD_IN, default = true)

    /** "New acts in your city". MARKETING — off by default. */
    val newActs: Flow<Boolean> = prefs.bool(KEY_NEW_ACTS, default = false)

    /** "Tips and offers". MARKETING — off by default. */
    val tipsAndOffers: Flow<Boolean> = prefs.bool(KEY_TIPS, default = false)

    /** "Review reminders" — once, 24h after a show. */
    val reviewReminders: Flow<Boolean> = prefs.bool(KEY_REVIEWS, default = true)

    /** Quiet hours, 10pm–8am. Urgent booking changes are the stated exception. */
    val quietHours: Flow<Boolean> = prefs.bool(KEY_QUIET_HOURS, default = true)

    /**
     * All eight as one snapshot.
     *
     * `combine` over eight flows rather than eight collectors in the ViewModel: the screen
     * renders one list and a partial snapshot would flicker a switch into its default on every
     * process start. Note DataStore emits the whole preferences object per write, so this is
     * one upstream read either way.
     *
     * `combine` tops out at five flows, hence the nesting — but each half now carries a NAMED
     * type rather than a `List<Boolean>` indexed at the join. Positional lists here are two
     * places to make the same mistake: swapping `loadInReminder` and `showDayReminder` in either
     * the producer or the `rest[2]`-style reader compiles, type-checks, and silently posts a
     * load-in reminder to somebody who asked for a show-day one. The compiler cannot see it;
     * with fields it cannot miss it.
     */
    val all: Flow<NotificationSettings> = combine(
        combine(quotesAndReplies, bookingUpdates, showDayReminder, loadInReminder) { a, b, c, d ->
            TransactionalSwitches(
                quotesAndReplies = a,
                bookingUpdates = b,
                showDayReminder = c,
                loadInReminder = d,
            )
        },
        combine(newActs, tipsAndOffers, reviewReminders, quietHours) { a, b, c, d ->
            RemainingSwitches(
                newActs = a,
                tipsAndOffers = b,
                reviewReminders = c,
                quietHours = d,
            )
        },
    ) { transactional, rest ->
        NotificationSettings(
            quotesAndReplies = transactional.quotesAndReplies,
            bookingUpdates = transactional.bookingUpdates,
            showDayReminder = transactional.showDayReminder,
            loadInReminder = transactional.loadInReminder,
            newActs = rest.newActs,
            tipsAndOffers = rest.tipsAndOffers,
            reviewReminders = rest.reviewReminders,
            quietHours = rest.quietHours,
        )
    }

    suspend fun set(toggle: NotificationToggle, enabled: Boolean) =
        prefs.setString(toggle.key, enabled.toString())

    companion object {
        const val KEY_QUOTES = "notify.quotes"
        const val KEY_BOOKING_UPDATES = "notify.booking_updates"
        const val KEY_SHOW_DAY = "notify.show_day"
        const val KEY_LOAD_IN = "notify.load_in"
        const val KEY_NEW_ACTS = "notify.new_acts"
        const val KEY_TIPS = "notify.tips"
        const val KEY_REVIEWS = "notify.review_reminders"
        const val KEY_QUIET_HOURS = "notify.quiet_hours"
    }
}

/** Half of [NotificationPreferences.all]'s join — see there for why it is not a list. */
private data class TransactionalSwitches(
    val quotesAndReplies: Boolean,
    val bookingUpdates: Boolean,
    val showDayReminder: Boolean,
    val loadInReminder: Boolean,
)

/** The other half. @see TransactionalSwitches */
private data class RemainingSwitches(
    val newActs: Boolean,
    val tipsAndOffers: Boolean,
    val reviewReminders: Boolean,
    val quietHours: Boolean,
)

/** One switch on screen 124, named so a ViewModel can pass it around without a string. */
enum class NotificationToggle(val key: String) {
    QuotesAndReplies(NotificationPreferences.KEY_QUOTES),
    BookingUpdates(NotificationPreferences.KEY_BOOKING_UPDATES),
    ShowDayReminder(NotificationPreferences.KEY_SHOW_DAY),
    LoadInReminder(NotificationPreferences.KEY_LOAD_IN),
    NewActs(NotificationPreferences.KEY_NEW_ACTS),
    TipsAndOffers(NotificationPreferences.KEY_TIPS),
    ReviewReminders(NotificationPreferences.KEY_REVIEWS),
    QuietHours(NotificationPreferences.KEY_QUIET_HOURS),
}

/** A snapshot of all eight, as the screen renders them. */
data class NotificationSettings(
    val quotesAndReplies: Boolean = true,
    val bookingUpdates: Boolean = true,
    val showDayReminder: Boolean = true,
    val loadInReminder: Boolean = true,
    val newActs: Boolean = false,
    val tipsAndOffers: Boolean = false,
    val reviewReminders: Boolean = true,
    val quietHours: Boolean = true,
) {
    operator fun get(toggle: NotificationToggle): Boolean = when (toggle) {
        NotificationToggle.QuotesAndReplies -> quotesAndReplies
        NotificationToggle.BookingUpdates -> bookingUpdates
        NotificationToggle.ShowDayReminder -> showDayReminder
        NotificationToggle.LoadInReminder -> loadInReminder
        NotificationToggle.NewActs -> newActs
        NotificationToggle.TipsAndOffers -> tipsAndOffers
        NotificationToggle.ReviewReminders -> reviewReminders
        NotificationToggle.QuietHours -> quietHours
    }

    fun with(toggle: NotificationToggle, enabled: Boolean): NotificationSettings = when (toggle) {
        NotificationToggle.QuotesAndReplies -> copy(quotesAndReplies = enabled)
        NotificationToggle.BookingUpdates -> copy(bookingUpdates = enabled)
        NotificationToggle.ShowDayReminder -> copy(showDayReminder = enabled)
        NotificationToggle.LoadInReminder -> copy(loadInReminder = enabled)
        NotificationToggle.NewActs -> copy(newActs = enabled)
        NotificationToggle.TipsAndOffers -> copy(tipsAndOffers = enabled)
        NotificationToggle.ReviewReminders -> copy(reviewReminders = enabled)
        NotificationToggle.QuietHours -> copy(quietHours = enabled)
    }
}
