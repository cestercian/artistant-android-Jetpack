package `in`.artistant.app.feature.system

import `in`.artistant.app.platform.storage.KeyValueStore
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the rating prompt remembers about itself (design screen 138).
 *
 * Three facts, and all three are needed to keep the promise the screen's own
 * footnote makes — *asked once, after a completed booking, never on launch*:
 *
 *  - [reviewSubmitted] is the ONLY thing that arms it. Without it the prompt is
 *    a launch interrupt, which is the pattern the design exists to reject.
 *  - [asked] closes it forever whichever button was pressed, because "Not now"
 *    that comes back next week is "Not now" that gets a one-star review.
 *  - [rated] records that the store was actually opened, so a future in-app
 *    review flow (Play's `com.google.android.play:review`) can skip somebody who
 *    already went.
 */
data class RatePromptRecord(
    val reviewSubmitted: Boolean = false,
    val asked: Boolean = false,
    val rated: Boolean = false,
)

/**
 * The housekeeping flags this DEVICE remembers — What's new and the rating
 * prompt.
 *
 * One interface for two screens because they are one concern: small, local,
 * per-install bookkeeping that decides whether a sheet appears. Splitting them
 * would be two interfaces with one implementation each, which house rule 5 calls
 * what it is.
 *
 * An interface at all (rather than direct [in.artistant.app.platform.storage.AppPreferences]
 * calls) for the reason [in.artistant.app.feature.search.SearchRecents] gives:
 * DataStore needs an Android `Context`, and the decision logic on top of these
 * values is exactly what wants a JVM test.
 *
 * Everything here lives in the main preference store, so
 * `AppPreferences.wipeAll()` clears it on sign-out. That is right for the rating
 * record — "this account reviewed a booking" is that account's fact — and
 * harmless for the seen version, which costs at most one extra sheet.
 */
interface SystemPreferences {
    suspend fun whatsNewSeenVersion(): String?
    suspend fun setWhatsNewSeenVersion(version: String)

    suspend fun ratePrompt(): RatePromptRecord
    suspend fun setRatePrompt(record: RatePromptRecord)
}

@Singleton
class DataStoreSystemPreferences @Inject constructor(
    private val store: KeyValueStore,
) : SystemPreferences {

    override suspend fun whatsNewSeenVersion(): String? =
        store.getString(KEY_WHATS_NEW).first()?.takeIf { it.isNotBlank() }

    override suspend fun setWhatsNewSeenVersion(version: String) {
        store.setString(KEY_WHATS_NEW, version)
    }

    /**
     * The three booleans as one comma-joined string.
     *
     * [KeyValueStore] carries strings only, and three separate keys for three
     * facts that are always read together would be three reads and three chances
     * for them to disagree after a partial write.
     */
    override suspend fun ratePrompt(): RatePromptRecord {
        val raw = store.getString(KEY_RATE).first().orEmpty()
        val parts = raw.split(SEPARATOR)
        return RatePromptRecord(
            reviewSubmitted = parts.getOrNull(0) == TRUE,
            asked = parts.getOrNull(1) == TRUE,
            rated = parts.getOrNull(2) == TRUE,
        )
    }

    override suspend fun setRatePrompt(record: RatePromptRecord) {
        store.setString(
            KEY_RATE,
            listOf(record.reviewSubmitted, record.asked, record.rated)
                .joinToString(SEPARATOR.toString()) { if (it) TRUE else FALSE },
        )
    }

    private companion object {
        const val KEY_WHATS_NEW = "system.whatsNewSeenVersion"
        const val KEY_RATE = "system.ratePrompt"
        const val SEPARATOR = ','
        const val TRUE = "1"
        const val FALSE = "0"
    }
}
