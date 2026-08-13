package `in`.artistant.app.feature.wizard

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The wizard's saved draft.
 *
 * ## Why this has its own DataStore file
 *
 * `AppPreferences.wipeAll()` runs on sign-out, which is exactly the moment the
 * draft has to survive: "Save & exit" ends the session and the artist expects to
 * come back to the step they left. Writing the draft into the shared store would
 * mean the one action that saves it is also the one that erases it. The
 * calendar-sync blob already carries the same requirement and solved it the same
 * way — a separate store file that only its own owner clears.
 *
 * ## Why the draft carries an owner
 *
 * A draft with no owner is the previous artist's stage name, handle, bio and
 * pricing waiting to be inherited by whoever signs in next on a shared device.
 * [WizardDraft.ownerId] is checked on read and a mismatch discards rather than
 * merges — there is no honest way to partially adopt someone else's identity.
 */
@Serializable
data class WizardDraft(
    /** Lowercased auth uid this draft belongs to. A mismatch on read discards it. */
    val ownerId: String,
    val step: String = WizardStep.Identity.name,
    val stageName: String = "",
    val handle: String = "",
    val category: String = "",
    val genre: String = "",
    val baseCity: String = "",
    val packages: List<DraftPackage> = emptyList(),
    val techItems: List<String> = emptyList(),
    val daysAvailable: List<String> = emptyList(),
    val timeSlots: List<String> = emptyList(),
    val coverGradientIndex: Int = 0,
    /**
     * Filename of the staged cover inside `WizardMediaCache`, or blank for none.
     *
     * Only the name, never a path: the cache directory is resolved from a
     * `Context` at read time, and an absolute path baked into a draft would go
     * stale the moment the app is reinstalled or moved between users.
     */
    val coverFileName: String = "",
    val samples: List<DraftSample> = emptyList(),
    val instagramHandle: String = "",
    val spotifyArtistUrl: String = "",
    val youtubeChannelUrl: String = "",
    val bio: String = "",
)

/**
 * A staged audio sample inside the draft.
 *
 * The title has to be written down because it cannot be recovered: the file is
 * named with a UUID, and the title is either derived from the picked document's
 * display name or typed by the artist afterwards. Without this the sample comes
 * back from a restart called "Sample" and the artist's edit is silently undone.
 * Duration is here for the same reason — it is read from the media at pick time,
 * not from the filename.
 */
@Serializable
data class DraftSample(
    val fileName: String,
    val title: String = "",
    val durationSeconds: Double = 0.0,
)

/**
 * A pricing tier inside the draft.
 *
 * Price stays a String, matching `PackageRow` — the draft has to be able to
 * represent "the artist cleared the field and hasn't typed the new number yet",
 * and an Int cannot. Round-tripping through a number would also quietly turn a
 * half-typed tier into a ₹0 one.
 */
@Serializable
data class DraftPackage(
    val key: String,
    val name: String = "",
    val duration: String = "",
    val price: String = "",
    val popular: Boolean = false,
)

// Top-level delegate — DataStore's one-instance-per-file rule. Deliberately NOT
// the `artistant.state` store: see the class KDoc above.
private val Context.wizardDraftStore by preferencesDataStore(name = "artistant.wizard")

/**
 * The outcome of reading the draft slot, as three distinct answers.
 *
 * This used to be a nullable `WizardDraft`, which collapsed three situations
 * with opposite meanings into the same `null`: the slot is empty, the slot holds
 * a draft that cannot be decoded, and the slot holds *somebody else's* draft.
 * Every one of those reads as "you have no draft", which is harmless for filling
 * in a form and actively destructive for the resume sweep — an empty reference
 * set is indistinguishable from "delete every staged file", including files that
 * are still the other artist's only copy.
 *
 * So the caller gets to see which it was. [Empty] is the only "nothing staged"
 * answer safe to act destructively on; [Unclaimable] means *someone* has media
 * here and we cannot enumerate it, so leave the disk alone.
 */
sealed interface WizardDraftRead {
    /** A decodable draft owned by the caller. */
    data class Mine(val draft: WizardDraft) : WizardDraftRead

    /** The slot is genuinely empty — nothing is staged for anybody. */
    data object Empty : WizardDraftRead

    /** A draft is there but this caller can't claim it: another owner's, or corrupt. */
    data object Unclaimable : WizardDraftRead
}

/**
 * Classify a draft-slot read. Pure so the distinction that the sweep depends on
 * is covered without a DataStore.
 *
 * [rawPresent] is deliberately separate from [decoded]: "no bytes" and "bytes
 * that would not parse" are the two cases the old nullable return could not tell
 * apart, and they need opposite answers.
 */
fun classifyWizardDraftRead(
    rawPresent: Boolean,
    decoded: WizardDraft?,
    ownerId: String,
): WizardDraftRead = when {
    !rawPresent -> WizardDraftRead.Empty
    // Present but unreadable — an older/newer schema, or a truncated write. The
    // owner is unknown, so the media on disk may be anyone's.
    decoded == null -> WizardDraftRead.Unclaimable
    !decoded.ownerId.equals(ownerId, ignoreCase = true) -> WizardDraftRead.Unclaimable
    else -> WizardDraftRead.Mine(decoded)
}

@Singleton
class WizardDraftStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val draftKey = stringPreferencesKey("draft")

    // Tolerant reader: a draft written by an older build must not crash the
    // wizard. It reads as Unclaimable rather than Empty — see WizardDraftRead.
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Read the draft slot from the perspective of [ownerId]. */
    suspend fun read(ownerId: String): WizardDraftRead {
        val raw = context.wizardDraftStore.data.first()[draftKey]
        val decoded = raw?.let { runCatching { json.decodeFromString<WizardDraft>(it) }.getOrNull() }
        return classifyWizardDraftRead(rawPresent = raw != null, decoded = decoded, ownerId = ownerId)
    }

    suspend fun save(draft: WizardDraft) {
        val raw = runCatching { json.encodeToString(WizardDraft.serializer(), draft) }.getOrNull() ?: return
        context.wizardDraftStore.edit { it[draftKey] = raw }
    }

    /** Called once the profile is published — the draft has served its purpose. */
    suspend fun clear() {
        context.wizardDraftStore.edit { it.remove(draftKey) }
    }
}
