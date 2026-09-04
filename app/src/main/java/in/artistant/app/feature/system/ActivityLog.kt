package `in`.artistant.app.feature.system

import `in`.artistant.app.feature.messages.ViewerIdentity
import `in`.artistant.app.platform.storage.KeyValueStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One push, as it was received on THIS device.
 *
 * Everything on it comes off the FCM payload `send-push` sent — nothing is
 * enriched, looked up or inferred, because the moment a row here claims more
 * than the notification did, the screen stops being a record of what arrived.
 */
@Serializable
data class ActivityEntry(
    /** Stable within the log; the write assigns it. */
    val id: String,
    /**
     * The account the device was signed in as when this landed, lowercase.
     *
     * Nullable only so a blob written by an older build still decodes; nothing
     * stores a null any more. [ActivityLog.record] drops a push that arrives
     * with no session rather than filing it under nobody, and the read shows
     * only rows owned by the account asking — an ownerless row would otherwise
     * surface the previous account's title, body and route to whoever signs in
     * next.
     */
    val userId: String? = null,
    /** `artistant_event`, verbatim. Null for a payload that carried none. */
    val event: String? = null,
    val title: String,
    val body: String,
    val receivedAtMs: Long,
    val bookingId: String? = null,
    val threadId: String? = null,
    val requestId: String? = null,
    val read: Boolean = false,
)

/** The four chips on screen 123, in the order the design draws them. */
enum class ActivityFilter(val label: String) {
    All("All"),
    Bookings("Bookings"),
    Quotes("Quotes"),
    Reviews("Reviews"),
}

/**
 * Which chip an entry answers to.
 *
 * [Other] has no chip of its own, which is the design as drawn: 123 has exactly
 * four chips and none of them is "Messages". So a chat push is in the log, is
 * visible under **All**, and is simply not one of the three narrowing lenses —
 * the inbox is where a conversation is meant to be found, and a fourth lens
 * pointing at a tab the user already has would be a second Messages tab.
 */
enum class ActivityCategory { Booking, Quote, Review, Other }

/**
 * Event name → chip.
 *
 * The names are the shared backend's (`send-push` / `formatPush`), so this list
 * has to stay in step with [in.artistant.app.navigation.PushPayloadRouter]'s.
 * An unknown event — a server event newer than the app — lands in [Other] rather
 * than being guessed into a chip: a mis-filed row is worse than an unfiled one,
 * because a filter the user trusts is a filter that must not lie.
 */
fun activityCategory(event: String?): ActivityCategory = when (event?.trim()) {
    "booking_confirmed_client",
    "booking_confirmed_artist",
    "booking_reminder_24h",
    "booking_request",
    -> ActivityCategory.Booking

    "gig_request" -> ActivityCategory.Quote
    "booking_review_request" -> ActivityCategory.Review
    else -> ActivityCategory.Other
}

/** Does [entry] belong under [filter]? */
fun matchesFilter(entry: ActivityEntry, filter: ActivityFilter): Boolean = when (filter) {
    ActivityFilter.All -> true
    ActivityFilter.Bookings -> activityCategory(entry.event) == ActivityCategory.Booking
    ActivityFilter.Quotes -> activityCategory(entry.event) == ActivityCategory.Quote
    ActivityFilter.Reviews -> activityCategory(entry.event) == ActivityCategory.Review
}

/**
 * How many of [entries] the account has not read.
 *
 * The bell on Discover (screen 02) draws its accent dot off this, and screen
 * 123's own header asks the same question — so the count is one function rather
 * than two `any { !it.read }`s that could drift apart about what "unread" means.
 */
fun unreadActivityCount(entries: List<ActivityEntry>): Int = entries.count { !it.read }

/**
 * Prepend [entry] and cap the log at [limit].
 *
 * Newest first, because that is the order the screen reads in and sorting on
 * every read would be work repeated for the life of the log.
 *
 * The cap is what stops a preference value growing without bound: this is one
 * JSON string in DataStore, which is read whole and written whole, so an
 * uncapped log turns every arriving push into a progressively slower write. The
 * limit is generous enough that the screen's own "TODAY / EARLIER" split never
 * runs out of rows.
 */
fun appendActivity(
    existing: List<ActivityEntry>,
    entry: ActivityEntry,
    limit: Int = ACTIVITY_LOG_LIMIT,
): List<ActivityEntry> = (listOf(entry) + existing).take(limit.coerceAtLeast(1))

/** How many received pushes one device remembers. */
const val ACTIVITY_LOG_LIMIT = 60

/**
 * The relative stamp on the right of a row — "2m", "1h", "2d".
 *
 * Deliberately coarse and deliberately never in the future: a clock that moved
 * backwards (a manual change, a timezone the device caught up on) would
 * otherwise render "-3m", which reads as a bug in the app rather than in the
 * clock. Anything at or before now-ish collapses to "now".
 */
fun relativeStamp(receivedAtMs: Long, nowMs: Long): String {
    val elapsed = (nowMs - receivedAtMs).coerceAtLeast(0)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsed)
    val hours = TimeUnit.MILLISECONDS.toHours(elapsed)
    val days = TimeUnit.MILLISECONDS.toDays(elapsed)
    return when {
        minutes < 1 -> "now"
        minutes < MINUTES_PER_HOUR -> "${minutes}m"
        hours < HOURS_PER_DAY -> "${hours}h"
        days < DAYS_PER_WEEK -> "${days}d"
        else -> "${days / DAYS_PER_WEEK}w"
    }
}

private const val MINUTES_PER_HOUR = 60L
private const val HOURS_PER_DAY = 24L
private const val DAYS_PER_WEEK = 7L

/**
 * Split the log into the design's two groups.
 *
 * "Today" is the calendar day, not the last 24 hours: a push from 11pm last
 * night is not "today" at 8am however few hours ago it was, and the group header
 * is answering the question "did I already see this today".
 *
 * [startOfTodayMs] is passed in rather than computed, so the split is a pure
 * function a test can pin without owning a clock or a timezone.
 */
fun groupActivity(
    entries: List<ActivityEntry>,
    startOfTodayMs: Long,
): Pair<List<ActivityEntry>, List<ActivityEntry>> =
    entries.partition { it.receivedAtMs >= startOfTodayMs }

/**
 * The device's own record of the pushes it received.
 *
 * **There is no server table behind this and there is not going to be one in
 * v1.** The shared schema has `device_tokens` (where to send) and the push
 * triggers (when to send), and nothing that records what WAS sent — the screen's
 * subtitle says so out loud, because "Activity" that silently omits every
 * notification received on the user's other phone would be worse than no screen.
 *
 * Written from the FCM receive path
 * ([in.artistant.app.platform.push.ArtistantMessagingService]) rather than from
 * the tap, which is the whole point of the design's note: a notification the user
 * never tapped is exactly the one they need to find later.
 */
interface ActivityLog {
    /**
     * Newest first, and only the rows the signed-in account owns.
     *
     * Empty while signed out. "Received on this device" is not "received by
     * whoever holds the device": a push landing between one account signing out
     * and the next signing in used to be stored ownerless and then shown to the
     * new account, which leaks the previous one's notification title, body and
     * deep-link ids.
     */
    val entries: Flow<List<ActivityEntry>>

    /**
     * Record an arriving push. Never throws — a failed log must not drop a notification.
     *
     * A push that arrives with no session is **dropped**. There is nobody to
     * attribute it to, and the only alternatives are filing it under nobody
     * (which is the leak above) or guessing an owner.
     */
    suspend fun record(entry: ActivityEntry)

    /** Marks the signed-in account's own rows read. Inert while signed out. */
    suspend fun markAllRead()

    /** Sign-out and delete-account both go through `AppPreferences.wipeAll`; this is for tests
     *  and for a caller that wants the log gone without wiping everything else. */
    suspend fun clear()
}

@Singleton
class DataStoreActivityLog @Inject constructor(
    private val store: KeyValueStore,
    private val viewer: ViewerIdentity,
) : ActivityLog {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /**
     * The transaction boundary for the whole log.
     *
     * Every mutation here is a read-modify-write of ONE DataStore string, and
     * the writers are genuinely concurrent: FCM delivers pushes on its own
     * worker threads and "Mark all read" runs from the ViewModel at the same
     * time. Unserialized, two pushes arriving together both read the same list
     * and the second write silently discards the first — the entry is simply
     * not in the log, which is the one failure this screen exists to prevent.
     *
     * A `@Singleton` binding is what makes one lock enough: every caller shares
     * this instance, so there is exactly one writer at a time per process.
     */
    private val mutex = Mutex()

    override val entries: Flow<List<ActivityEntry>> =
        store.getString(KEY).map { raw ->
            // Signed out, the log shows nothing. It is not "empty" — it is not
            // this session's to read, and the store is wiped on sign-out anyway.
            val account = viewer.currentUserId() ?: return@map emptyList()
            decode(raw).filter { it.userId == account }
        }

    override suspend fun record(entry: ActivityEntry) {
        // No session, no owner, no row. A push CAN land here between sign-out
        // and the next sign-in (the token survives until `send-push` is told
        // otherwise), and an ownerless row is one the next account would be
        // shown.
        val owner = entry.userId?.lowercase() ?: viewer.currentUserId() ?: run {
            Timber.i("Dropped an arriving push: no signed-in account to file it under")
            return
        }
        runCatching {
            mutex.withLock {
                val existing = decode(store.getString(KEY).first())
                val stamped = entry.copy(userId = owner)
                store.setString(KEY, json.encodeToString(appendActivity(existing, stamped)))
            }
        }.onFailure { Timber.w(it, "Couldn't record activity entry") }
    }

    override suspend fun markAllRead() {
        val account = viewer.currentUserId() ?: return
        runCatching {
            mutex.withLock {
                // Only this account's rows. Anything else in the blob is
                // invisible to this session, and quietly rewriting it would be
                // touching a record that is not ours to touch.
                val updated = decode(store.getString(KEY).first())
                    .map { if (it.userId == account) it.copy(read = true) else it }
                store.setString(KEY, json.encodeToString(updated))
            }
        }.onFailure { Timber.w(it, "Couldn't mark activity read") }
    }

    override suspend fun clear() {
        runCatching { mutex.withLock { store.setString(KEY, "") } }
            .onFailure { Timber.w(it, "Couldn't clear the activity log") }
    }

    /**
     * A stored blob that no longer parses is treated as an empty log rather than
     * as an error: the only way to get one is a shape change between app
     * versions, and taking the Activity screen down over stale local bookkeeping
     * is a bad trade against losing a few rows.
     */
    private fun decode(raw: String?): List<ActivityEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<ActivityEntry>>(raw) }
            .getOrElse {
                Timber.w(it, "Discarding an unreadable activity log")
                emptyList()
            }
    }

    private companion object {
        const val KEY = "system.activityLog"
    }
}
