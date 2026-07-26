package `in`.artistant.app.feature.booking

import `in`.artistant.app.data.model.BookingDraft
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory draft holder — port of iOS `BookingStore.draft`.
 * Survives Booking → Checkout within a session; cleared after confirm.
 */
@Singleton
class BookingDraftStore @Inject constructor() {
    private val _draft = MutableStateFlow<BookingDraft?>(null)
    val draft: StateFlow<BookingDraft?> = _draft.asStateFlow()

    fun setDraft(draft: BookingDraft) {
        _draft.value = draft
    }

    fun clear() {
        _draft.value = null
    }
}
