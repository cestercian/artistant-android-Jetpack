package `in`.artistant.app.feature.bookings

import `in`.artistant.app.platform.storage.AppPreferences
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One booking as it survives without a network — the essentials of the night.
 *
 * Deliberately NOT the whole [in.artistant.app.data.model.Booking]. Screen 122's
 * note is the spec: "On a show night the venue is on a basement Wi-Fi — the
 * essentials are cached deliberately." What is essential at that moment is where
 * to go, when to be there, what the load-in note said and who the act is. The
 * fee, the guest count, the payment method and the package index are not, and
 * caching them would mean holding a stale number about money on the device for
 * nothing.
 *
 * [status] is stored as its `dbValue` rather than as the enum, so a snapshot
 * written by a build that knew a status this one does not still decodes — into
 * `Unknown`, which is exactly how the offline screen renders anything it cannot
 * vouch for.
 */
@Serializable
data class CachedBooking(
    val id: String,
    val artistName: String,
    val status: String,
    val date: String,
    val time: String,
    val venue: String,
    val venueNotes: String? = null,
)

/** The last list we read, and when we read it. */
@Serializable
data class BookingsSnapshot(
    val cachedAtMs: Long,
    val items: List<CachedBooking> = emptyList(),
)

/**
 * The Bookings tab's own persisted state: the offline snapshot, and whether the
 * profile nudge has been sent away.
 *
 * An interface rather than an [AppPreferences] call in the ViewModel, for the
 * same reason `SearchRecents` is one: DataStore needs an Android `Context`, and
 * `BookingsViewModel` is unit-tested on the JVM. Both values live behind one
 * seam because they are the same kind of thing — small local state belonging to
 * one screen — and two interfaces over the same DataStore would be two fakes to
 * keep in step for no gain.
 *
 * Everything here is written under the main `artistant.state` store, so a
 * sign-out's `wipeAll()` takes it: a snapshot is somebody's schedule and a
 * dismissal is somebody's preference, and neither may be inherited by the next
 * account on a shared device.
 */
interface BookingsLocalStore {
    suspend fun loadSnapshot(): BookingsSnapshot?
    suspend fun saveSnapshot(snapshot: BookingsSnapshot)

    /** Screen 89's nudge is dismissible, and the dismissal has to outlive the process. */
    suspend fun nudgeDismissed(): Boolean
    suspend fun setNudgeDismissed(dismissed: Boolean)
}

@Singleton
class DataStoreBookingsLocalStore @Inject constructor(
    private val prefs: AppPreferences,
) : BookingsLocalStore {

    override suspend fun loadSnapshot(): BookingsSnapshot? =
        decodeSnapshot(prefs.getString(SNAPSHOT_KEY).first())

    override suspend fun saveSnapshot(snapshot: BookingsSnapshot) =
        prefs.setString(SNAPSHOT_KEY, encodeSnapshot(snapshot))

    override suspend fun nudgeDismissed(): Boolean =
        prefs.getString(NUDGE_KEY).first() == DISMISSED

    override suspend fun setNudgeDismissed(dismissed: Boolean) =
        prefs.setString(NUDGE_KEY, if (dismissed) DISMISSED else "")

    private companion object {
        const val SNAPSHOT_KEY = "bookings.snapshot"
        const val NUDGE_KEY = "bookings.nameNudge.dismissed"
        const val DISMISSED = "1"
    }
}

/**
 * The codec, apart from the store so it can be asserted without a `Context`.
 *
 * [decodeSnapshot] answers null for anything it cannot read — absent, empty, or
 * written by a shape that no longer exists — rather than throwing. A snapshot is
 * a convenience; a corrupt one must degrade to "we have nothing cached", which
 * is a state the screen already draws, and never to a crash on the Bookings tab.
 */
private val snapshotJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun encodeSnapshot(snapshot: BookingsSnapshot): String =
    snapshotJson.encodeToString(BookingsSnapshot.serializer(), snapshot)

fun decodeSnapshot(raw: String?): BookingsSnapshot? {
    if (raw.isNullOrBlank()) return null
    return runCatching { snapshotJson.decodeFromString(BookingsSnapshot.serializer(), raw) }
        .getOrNull()
}
