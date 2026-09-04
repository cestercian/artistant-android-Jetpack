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

/**
 * The last list we read, whose it was, and when we read it.
 *
 * [ownerId] is not bookkeeping — it is the only thing standing between two
 * accounts on one phone. `wipeAll()` clears this store on sign-out, but a
 * refresh already in flight for the OUTGOING account can finish afterwards and
 * write its list back into the emptied store; the next person to sign in offline
 * would then be shown a stranger's artist, venue and load-in note. So every
 * snapshot is stamped with the uid it belongs to, every read checks that stamp
 * against the live session, and a mismatch is DELETED rather than merely
 * ignored — leaving it on disk means the same leak waits for the next reader.
 *
 * Defaulted to blank so a snapshot written before this field existed still
 * decodes — and, having no owner to vouch for it, is treated as somebody else's
 * and dropped. See [snapshotBelongsTo].
 */
@Serializable
data class BookingsSnapshot(
    val cachedAtMs: Long,
    val items: List<CachedBooking> = emptyList(),
    val ownerId: String = "",
)

/**
 * May [userId] read this snapshot?
 *
 * Deliberately closed rather than permissive: a signed-out reader, an unstamped
 * snapshot and a stamp belonging to somebody else all answer false. The cost of
 * a false negative is one uncached list; the cost of a false positive is one
 * person's schedule shown to another.
 */
fun snapshotBelongsTo(snapshot: BookingsSnapshot, userId: String?): Boolean {
    val owner = snapshot.ownerId.trim()
    val reader = userId?.trim().orEmpty()
    if (owner.isEmpty() || reader.isEmpty()) return false
    return owner.equals(reader, ignoreCase = true)
}

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

    /**
     * Drop the snapshot outright.
     *
     * Called when a read finds one belonging to a different account, which is
     * the case `wipeAll()` cannot cover on its own: an in-flight refresh can
     * land after the wipe. Ignoring such a snapshot would leave it on disk for
     * the next reader to trip over, so the mismatch deletes it.
     */
    suspend fun clearSnapshot()

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

    // An empty string rather than a key removal: `AppPreferences` exposes only
    // get/set over its generic string slot, and `decodeSnapshot` already answers
    // null for a blank value — so this reads back as "no snapshot" either way.
    override suspend fun clearSnapshot() = prefs.setString(SNAPSHOT_KEY, "")

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
