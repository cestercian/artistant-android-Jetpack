package `in`.artistant.app.feature.messages

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import `in`.artistant.app.platform.storage.AppPreferences
import kotlinx.coroutines.flow.first
import javax.inject.Singleton

/**
 * "Show when I've read messages" — the Privacy screen's read-receipt switch
 * (design 62), read from the one place that acts on it.
 *
 * **What it gates, precisely.** Two different writes happen when a thread is
 * opened and they mean opposite things to the two people in it:
 *
 *  - `markThreadRead` PATCHes the viewer's OWN `threads.*_unread_count` to zero.
 *    That is the viewer's badge. Nobody else can see it, so the preference has
 *    no business touching it — turning receipts off must not leave someone
 *    staring at an unread count for a conversation they have read.
 *  - `markThreadReadReceipt` calls the `mark_thread_read` RPC, which writes
 *    `thread_reads` — the row the COUNTERPARTY reads to render "Read by Rhea".
 *    That is the broadcast, and that is what this switch turns off.
 *
 * So the gate sits on the second call only, and it covers every path that
 * reaches it: opening a thread, an inbound realtime message, and a send that
 * lands ([ChatViewModel.markReadBestEffort] is the single choke point).
 *
 * **Defaults to true**, matching the Privacy screen's own default: this is an
 * opt-OUT, and a person who has never seen the switch has not asked to be
 * invisible.
 *
 * **Read per call, not cached.** The switch lives on another screen and can be
 * flipped while a thread is open; a value captured at construction would keep
 * broadcasting for the life of the ViewModel after someone had just turned it
 * off, which is the one moment they are watching for it to stop.
 *
 * **[enabled] may throw** — it reads DataStore, which raises on a corrupt or
 * unreadable file. The one caller wraps it and falls back to the same `true` an
 * absent key produces; see [ChatViewModel.markReadBestEffort], where an escaping
 * throw would also skip the unread-flag cleanup that follows it.
 *
 * The key is shared with `feature/signup/PrivacyPreferences.kt` on the
 * getting-started branch — same DataStore, same string — so the two reconcile to
 * one preference when those branches merge.
 */
fun interface ReadReceiptsPreference {
    /** True when the viewer is willing to broadcast that they have read. */
    suspend fun enabled(): Boolean
}

@Module
@InstallIn(SingletonComponent::class)
object ReadReceiptsModule {
    /**
     * The DataStore key the Privacy screen writes.
     *
     * Stored as a string rather than a boolean because [AppPreferences] exposes
     * a string-keyed pair and adding a parallel boolean API for one flag is more
     * surface than the flag is worth. Anything that is not the literal "false"
     * reads as enabled — an absent key, a half-written value, or a future
     * spelling — because the failure direction that matters is silently going
     * quiet on someone who never asked for it.
     */
    const val KEY = "privacy.read_receipts"

    @Provides
    @Singleton
    fun provideReadReceiptsPreference(prefs: AppPreferences): ReadReceiptsPreference =
        ReadReceiptsPreference { prefs.getString(KEY).first() != false.toString() }
}
