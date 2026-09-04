package `in`.artistant.app.feature.system

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether the app may be used right now — the state behind design screens 120
 * (update required) and 121 (service outage).
 *
 * Two gates, one type, because they are the same decision made twice: something
 * outside the user's control has made the client unusable, and the app has to
 * say which thing and what happens to their data. [Normal] is every other
 * moment, which is almost all of them.
 */
sealed interface SystemStatus {

    /** Nothing in the way. The app runs. */
    data object Normal : SystemStatus

    /**
     * Screen 120. This build is below the server's floor.
     *
     * Both versions are carried, because the design shows both: a gate that
     * says only "update" is indistinguishable from a bug, and the two numbers
     * side by side are what make it legible.
     */
    data class UpdateRequired(
        /** What is installed — `BuildConfig.VERSION_NAME`. */
        val installed: String,
        /** The lowest version the backend will still talk to. */
        val minimum: String,
    ) : SystemStatus

    /**
     * Screen 121. The backend is down.
     *
     * [impact] is the scoped line the design's note calls load-bearing
     * ("Bookings and messages affected") — an unscoped outage screen sends
     * everyone to support to ask whether their own booking survived.
     * [startedLabel] is null when the source cannot say when it began; the
     * screen then omits the line rather than inventing a time.
     */
    data class Outage(
        val impact: String,
        val startedLabel: String? = null,
    ) : SystemStatus
}

/**
 * Where [SystemStatus] comes from.
 *
 * **Unwired on purpose, and this is the reason.** A hard gate needs a source of
 * truth the client can read before it trusts anything else, and the shared
 * Supabase schema has none: the only key/value table is `app_settings` (mig
 * 0016), which is RLS default-deny with NO policies, and mig 0037 additionally
 * revoked the PUBLIC/anon/authenticated grant on the `app_setting(text)` helper
 * precisely so a client could not read it. There is no min-version column, no
 * status table, and no Edge Function that reports either. So the live
 * implementation reports [SystemStatus.Normal] forever, and the two screens ship
 * behind the debug harness (`force-update` / `service-outage`) so they can be
 * built, reviewed and walked before the server side exists.
 *
 * The seam is an interface rather than a `TODO()` for the usual reason: when the
 * operator adds a readable config row (or a `status` Edge Function), exactly one
 * class changes and neither the gate nor the screens move.
 */
interface SystemStatusSource {
    val status: Flow<SystemStatus>

    /**
     * Re-read the source — screen 121's "Check again".
     *
     * A suspend function even though the live implementation does nothing:
     * whatever replaces it is a network read, and a caller written against a
     * non-suspending signature would have to be rewritten to await one.
     */
    suspend fun refresh()
}

/**
 * The production source: always [SystemStatus.Normal].
 *
 * Not a stub that will be "filled in later" — it is the honest answer to a
 * question the backend cannot currently answer, and it is better than the two
 * alternatives. Guessing (gating on a hard-coded version number baked into the
 * client) gates on a fact the client cannot verify; failing loudly would take
 * the whole app down over a feature nobody has asked the server for yet.
 */
@Singleton
class LiveSystemStatusSource @Inject constructor() : SystemStatusSource {
    override val status: Flow<SystemStatus> = flowOf(SystemStatus.Normal)
    override suspend fun refresh() = Unit
}
