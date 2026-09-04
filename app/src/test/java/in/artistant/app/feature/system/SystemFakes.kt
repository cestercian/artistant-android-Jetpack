package `in`.artistant.app.feature.system

import `in`.artistant.app.platform.storage.KeyValueStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.yield

/**
 * In-memory twins of section SH's three persistence seams.
 *
 * They exist for the same reason every other `Fake*` in this tree does: the real
 * implementations wrap DataStore, which needs an Android `Context`, and the
 * decisions built on top of them are exactly what wants a JVM test.
 */
class FakeSystemPreferences(
    private var seenVersion: String? = null,
    private var rate: RatePromptRecord = RatePromptRecord(),
) : SystemPreferences {

    /** Every version this store was ever told about, in order. */
    val recorded = mutableListOf<String>()

    override suspend fun whatsNewSeenVersion(): String? = seenVersion

    override suspend fun setWhatsNewSeenVersion(version: String) {
        seenVersion = version
        recorded += version
    }

    override suspend fun ratePrompt(): RatePromptRecord = rate

    override suspend fun setRatePrompt(record: RatePromptRecord) {
        rate = record
    }
}

/**
 * [FakeSystemPreferences] with a suspension point on either side of the value.
 *
 * A transition that regresses another one needs a window between its own read
 * and its own write — which a store that answers instantly never opens. This is
 * the fake that makes "the rating prompt serializes its transitions" a claim a
 * test can fail.
 */
class SlowSystemPreferences(private var rate: RatePromptRecord = RatePromptRecord()) :
    SystemPreferences {

    override suspend fun whatsNewSeenVersion(): String? = null

    override suspend fun setWhatsNewSeenVersion(version: String) = Unit

    override suspend fun ratePrompt(): RatePromptRecord {
        yield()
        return rate
    }

    override suspend fun setRatePrompt(record: RatePromptRecord) {
        yield()
        rate = record
    }

    /** The value as it stands, read without the suspension — for assertions. */
    val snapshot: RatePromptRecord get() = rate
}

/**
 * A [KeyValueStore] that suspends between a caller's read and its write.
 *
 * The `yield()` is the whole point: a lost update needs a window in which two
 * read-modify-write callers have both read and neither has written, and on a
 * single test dispatcher nothing else creates one. A store that writes eagerly
 * would make an unserialized implementation pass by accident.
 */
class SlowKeyValueStore(initial: Map<String, String> = emptyMap()) : KeyValueStore {
    private val values = MutableStateFlow(initial)

    override fun getString(key: String): Flow<String?> = values.map { it[key] }

    override suspend fun setString(key: String, value: String) {
        yield()
        values.value = values.value + (key to value)
    }
}

/** An activity log that never touches disk. */
class FakeActivityLog(seed: List<ActivityEntry> = emptyList()) : ActivityLog {
    private val rows = MutableStateFlow(seed)
    override val entries: Flow<List<ActivityEntry>> = rows

    override suspend fun record(entry: ActivityEntry) {
        rows.value = appendActivity(rows.value, entry)
    }

    override suspend fun markAllRead() {
        rows.value = rows.value.map { it.copy(read = true) }
    }

    override suspend fun clear() {
        rows.value = emptyList()
    }
}

/**
 * A feedback outbox that queues but never sends, unless [drainSucceeds] says so.
 *
 * Both halves matter to the screen: a queued note is the design's "it sends on
 * your next live session", and a drain that fails has to leave the note where it
 * was rather than reporting it gone.
 */
class FakeFeedbackOutbox(var drainSucceeds: Boolean = true) : FeedbackOutbox {
    val queued = mutableListOf<PendingFeedback>()
    var drains = 0
        private set

    override suspend fun pending(): List<PendingFeedback> = queued.toList()

    override suspend fun enqueue(note: PendingFeedback) {
        queued += note
    }

    override suspend fun drain(): Boolean {
        drains += 1
        if (drainSucceeds) queued.clear()
        return queued.isEmpty()
    }
}
