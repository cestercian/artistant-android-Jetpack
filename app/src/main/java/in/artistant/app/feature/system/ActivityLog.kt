package `in`.artistant.app.feature.system

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import `in`.artistant.app.platform.storage.KeyValueStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
    /** The account the device was signed in as when this landed, lowercase. */
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
    /** Newest first, already filtered to the signed-in account. */
    val entries: Flow<List<ActivityEntry>>

    /** Record an arriving push. Never throws — a failed log must not drop a notification. */
    suspend fun record(entry: ActivityEntry)

    suspend fun markAllRead()

    /** Sign-out and delete-account both go through `AppPreferences.wipeAll`; this is for tests
     *  and for a caller that wants the log gone without wiping everything else. */
    suspend fun clear()
}

@Singleton
class DataStoreActivityLog @Inject constructor(
    private val store: KeyValueStore,
    private val client: SupabaseClient,
) : ActivityLog {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override val entries: Flow<List<ActivityEntry>> =
        store.getString(KEY).map { raw ->
            val all = decode(raw)
            val account = currentUserId()
            // A row with no account recorded is shown to whoever is signed in:
            // it was received on this device, the store is wiped on sign-out, and
            // hiding it would mean the log is silently incomplete.
            all.filter { it.userId == null || account == null || it.userId == account }
        }

    override suspend fun record(entry: ActivityEntry) {
        runCatching {
            val existing = decode(store.getString(KEY).first())
            val stamped = entry.copy(userId = entry.userId ?: currentUserId())
            store.setString(KEY, json.encodeToString(appendActivity(existing, stamped)))
        }.onFailure { Timber.w(it, "Couldn't record activity entry") }
    }

    override suspend fun markAllRead() {
        runCatching {
            val updated = decode(store.getString(KEY).first()).map { it.copy(read = true) }
            store.setString(KEY, json.encodeToString(updated))
        }.onFailure { Timber.w(it, "Couldn't mark activity read") }
    }

    override suspend fun clear() {
        runCatching { store.setString(KEY, "") }
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

    private fun currentUserId(): String? =
        client.auth.currentSessionOrNull()?.user?.id?.lowercase()

    private companion object {
        const val KEY = "system.activityLog"
    }
}
