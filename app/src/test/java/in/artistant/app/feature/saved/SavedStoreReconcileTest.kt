package `in`.artistant.app.feature.saved

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What a server read is allowed to do to the local saved set.
 *
 * `refreshFromServer()` used to assign the server's answer wholesale
 * (`_ids.value = remote`) and then persist it. Three call sites fire it —
 * SavedStore's own init, ProfileViewModel.refresh() and
 * ArtistListViewModel.loadSaved() — so the read is routinely in flight while the
 * user is browsing. Tap a heart in that window and the answer that comes back
 * PREDATES the tap: it un-filled the heart the user just filled, wrote the wrong
 * set to DataStore, and left the artist reading as unsaved (in the Saved list and
 * in the profile counter) until some later refresh happened to re-read. The class
 * doc promises the opposite — "final desired state wins".
 *
 * Pure over three sets because SavedStore itself needs a DataStore-backed
 * AppPreferences to construct, which this suite (junit + coroutines-test) cannot
 * build. `unsettled` is the store's live `jobs` key set: exactly the ids whose
 * own add/remove has not come back yet.
 */
class SavedStoreReconcileTest {

    @Test
    fun `with nothing in flight the server is simply the truth`() {
        assertEquals(
            setOf("a", "b"),
            reconcileSaved(remote = setOf("a", "b"), local = setOf("a"), unsettled = emptySet()),
        )
    }

    @Test
    fun `a heart tapped while the read was in flight survives the answer`() {
        // Server held {a} when list() was issued; the user then saved b and the
        // add is still in flight. The stale answer must not empty that heart.
        assertEquals(
            setOf("a", "b"),
            reconcileSaved(remote = setOf("a"), local = setOf("a", "b"), unsettled = setOf("b")),
        )
    }

    @Test
    fun `an unheart in flight is not undone by an answer that still lists it`() {
        // The mirror image: the user removed a, the delete has not landed, and
        // the server read still shows it. Re-filling the heart would be just as
        // wrong as emptying one.
        assertEquals(
            setOf("b"),
            reconcileSaved(remote = setOf("a", "b"), local = setOf("b"), unsettled = setOf("a")),
        )
    }

    @Test
    fun `settled ids still take the server's answer even while another id is in flight`() {
        // A pending write on b must not freeze the whole set: c was saved on
        // another device and has to arrive, and a was removed there and has to go.
        assertEquals(
            setOf("b", "c"),
            reconcileSaved(
                remote = setOf("c"),
                local = setOf("a", "b"),
                unsettled = setOf("b"),
            ),
        )
    }

    @Test
    fun `an in-flight id the user did not end up saving stays out`() {
        // Rapid double tap: saved then unsaved, both superseded into one pending
        // write whose desired state is "not saved". Local is the authority for it,
        // so a server answer that still lists it does not resurrect the heart.
        assertEquals(
            emptySet<String>(),
            reconcileSaved(remote = setOf("a"), local = emptySet(), unsettled = setOf("a")),
        )
    }

    /**
     * The window `jobs` alone cannot see.
     *
     * A tap whose write both starts and finishes inside one `list()` round trip
     * is absent from both in-flight snapshots — its entry is added after the
     * first and pruned before the second — so the stale answer reversed a save
     * that had just succeeded. `refreshFromServer` therefore also protects the
     * before/after difference of the local set, which is what these two pin.
     */
    @Test
    fun `a save that completed during the read survives the stale answer`() {
        val before = emptySet<String>()
        val local = setOf("a")
        val changedDuringRead = (local - before) + (before - local)

        assertEquals(
            setOf("a"),
            reconcileSaved(remote = emptySet(), local = local, unsettled = changedDuringRead),
        )
    }

    @Test
    fun `an unsave that completed during the read is not resurrected`() {
        val before = setOf("a")
        val local = emptySet<String>()
        val changedDuringRead = (local - before) + (before - local)

        assertEquals(
            emptySet<String>(),
            reconcileSaved(remote = setOf("a"), local = local, unsettled = changedDuringRead),
        )
    }

    @Test
    fun `a heart tapped and untapped inside one read lets the server win`() {
        // Net zero local change, so there is no user intent for the answer to
        // overwrite — the diff is empty and remote is adopted whole.
        val before = setOf("a")
        val local = setOf("a")
        val changedDuringRead = (local - before) + (before - local)

        assertEquals(
            setOf("a", "b"),
            reconcileSaved(remote = setOf("a", "b"), local = local, unsettled = changedDuringRead),
        )
    }
}
