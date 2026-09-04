package `in`.artistant.app.feature.system

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

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
