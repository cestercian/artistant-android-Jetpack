package `in`.artistant.app.feature.booking

import `in`.artistant.app.data.model.BookingDraft
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory draft holder — port of iOS `BookingStore.draft`.
 *
 * Survives Booking → Checkout within a session; cleared after confirm — and on
 * sign-out / account delete (`ProfileViewModel`), because a `@Singleton` in a
 * process that outlives the session otherwise hands the next account the
 * previous one's half-composed booking: their venue, guest count and free-text
 * directions, plus the package tier that decides which ticket the booking
 * screen opens on.
 */
@Singleton
class BookingDraftStore @Inject constructor() {
    private val _draft = MutableStateFlow<BookingDraft?>(null)
    val draft: StateFlow<BookingDraft?> = _draft.asStateFlow()

    /**
     * The tier the client tapped on the artist profile, waiting for the booking
     * screen to open on it.
     *
     * This is the Android shape of the iOS handoff: `ArtistView`'s dock CTA runs
     * `bookingStore.startDraft(for:)` + `draft?.packageIndex = pkg` in a
     * `simultaneousGesture` *before* the NavigationLink pushes, and `BookingView`
     * re-seeds only when the draft belongs to a different artist — so the tapped
     * ticket survives the navigation. The route carries only the artist id on
     * both platforms, which is why the selection travels through the shared
     * store instead of through nav arguments.
     *
     * It is kept separate from [draft] rather than folded into it: a `BookingDraft`
     * cannot be built on the profile (it requires a date and time the client has
     * not picked yet), and [CheckoutViewModel] reads [draft] directly, so parking
     * a half-built one there would hand checkout a draft with no date.
     */
    private val _pendingPackage = MutableStateFlow<PendingPackage?>(null)

    fun setDraft(draft: BookingDraft) {
        _draft.value = draft
    }

    /** Called from the profile as the client leaves for the booking screen. */
    fun seedPackageIndex(artistId: String, index: Int) {
        _pendingPackage.value = PendingPackage(artistId, index)
    }

    /**
     * Scoped by artist id on purpose: the seed outlives a single navigation (the
     * client can go back, change nothing, and come in again — iOS keeps its draft
     * for the same reason), so a leftover from artist A must never decide which
     * tier artist B opens on.
     */
    fun pendingPackageIndex(artistId: String): Int? =
        _pendingPackage.value?.takeIf { it.artistId == artistId }?.index

    /** Clears the whole handoff — the post-confirm reset (as on iOS) and sign-out. */
    fun clear() {
        _draft.value = null
        _pendingPackage.value = null
    }

    private data class PendingPackage(val artistId: String, val index: Int)
}
