package `in`.artistant.app.feature.bookings

/**
 * In-memory [BookingsLocalStore] — the seam's whole reason for existing.
 *
 * [failLoad] models the case a DataStore read can genuinely hit (a corrupt or
 * unreadable preferences file): the ViewModel must degrade to "nothing cached"
 * rather than let it out of `refresh()`, because an unreadable cache on the
 * failure path would turn a network blip into a crash on the Bookings tab.
 */
class FakeBookingsLocalStore(
    var snapshot: BookingsSnapshot? = null,
    var dismissed: Boolean = true,
) : BookingsLocalStore {

    var saved: BookingsSnapshot? = null
        private set
    var failLoad: Boolean = false

    /** Whether a foreign snapshot was actually deleted, not merely ignored. */
    var cleared: Boolean = false
        private set

    override suspend fun loadSnapshot(): BookingsSnapshot? {
        if (failLoad) error("cache unreadable")
        return snapshot
    }

    override suspend fun saveSnapshot(snapshot: BookingsSnapshot) {
        saved = snapshot
        this.snapshot = snapshot
    }

    override suspend fun clearSnapshot() {
        cleared = true
        snapshot = null
    }

    override suspend fun nudgeDismissed(): Boolean = dismissed

    override suspend fun setNudgeDismissed(dismissed: Boolean) {
        this.dismissed = dismissed
    }
}
