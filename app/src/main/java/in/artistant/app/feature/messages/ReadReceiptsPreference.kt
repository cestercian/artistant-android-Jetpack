package `in`.artistant.app.feature.messages

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import `in`.artistant.app.feature.signup.PrivacyPreferences
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
 * unreadable file. The one caller wraps it and, on a throw, **does not
 * broadcast**: an absent key means enabled, but an unreadable store means
 * unknown, and among the people whose preference cannot be read are the ones who
 * turned it off. See [ChatViewModel.markReadBestEffort], where the wrap also
 * stops an escaping throw skipping the unread-flag cleanup that follows it.
 *
 * **One store, not two.** This used to read the DataStore key directly and agree
 * with `feature/signup/PrivacyPreferences` — screen 62's own switch — by
 * convention: same key string, same "absent means on" rule, written down in two
 * places. Now that both are on `main` it simply DELEGATES to that class. Two
 * copies of a privacy default is a bug waiting for someone to change one of
 * them, and the direction it would fail in is silent: the switch reads ON on the
 * settings screen while the chat has stopped broadcasting, or the reverse.
 *
 * The seam itself stays. `PrivacyPreferences` lives in `feature/signup` because
 * that section owns the screen, and the chat has no business reaching across
 * into another feature package for a boolean; this `fun interface` is the one
 * question the chat actually asks, and it is what the tests substitute.
 */
fun interface ReadReceiptsPreference {
    /** True when the viewer is willing to broadcast that they have read. */
    suspend fun enabled(): Boolean
}

@Module
@InstallIn(SingletonComponent::class)
object ReadReceiptsModule {
    /**
     * Screen 62's switch, read through the class that owns it.
     *
     * `PrivacyPreferences.readReceipts` is a `Flow` because the settings screen
     * renders it; this takes `first()` because the chat needs the value at the
     * moment it is about to broadcast, not a subscription. It is read per call
     * for that reason — see the interface doc — and the one caller wraps it,
     * because a DataStore read can throw.
     *
     * The key (`privacy.read_receipts`) and the "absent means on" rule now live
     * in exactly one place, `PrivacyPreferences`.
     */
    @Provides
    @Singleton
    fun provideReadReceiptsPreference(privacy: PrivacyPreferences): ReadReceiptsPreference =
        ReadReceiptsPreference { privacy.readReceipts.first() }
}
