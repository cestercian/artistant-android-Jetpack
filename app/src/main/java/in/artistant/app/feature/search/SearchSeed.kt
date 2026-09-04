package `in`.artistant.app.feature.search

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What a rail on Discover means by "See all".
 *
 * Every field is a filter [SearchViewModel] already owns, so a seed can only ever
 * ask for a search the user could have built by hand. Nothing here is a new query
 * shape.
 */
data class SearchSeedRequest(
    val text: String = "",
    val city: String? = null,
    val category: String? = null,
    val dateIso: String? = null,
)

/**
 * The one-way seam between Discover's "See all" and the Search tab.
 *
 * Discover and Search are two destinations in the same NavHost with two
 * ViewModels; the Search entry is never popped, so its ViewModel outlives every
 * visit and cannot be re-created with arguments. Passing the rail's filters as
 * route arguments would therefore work exactly once — the second "See all" would
 * navigate to a live entry and change nothing.
 *
 * So the request is handed over as state instead: Discover [request]s, the
 * scaffold switches tabs, and [SearchViewModel] collects and [consume]s it.
 * A singleton because both sides need the same instance and neither owns the
 * other; a `StateFlow` rather than a channel because a seed that arrives before
 * Search has ever been opened must still be waiting when it is.
 *
 * One slot, last-write-wins: two rapid taps mean the second rail is the one the
 * user wants, not a queue of two searches to run in order.
 */
@Singleton
class SearchSeed @Inject constructor() {

    private val _pending = MutableStateFlow<SearchSeedRequest?>(null)

    /** The seed waiting to be applied, or null. */
    val pending: StateFlow<SearchSeedRequest?> = _pending.asStateFlow()

    fun request(seed: SearchSeedRequest) {
        _pending.value = seed
    }

    /**
     * Take the pending seed, leaving the slot empty.
     *
     * Consumed rather than merely observed because a seed is an EVENT: leaving it
     * in place would re-apply the rail's filters on the next configuration change
     * or process-death restore, silently undoing whatever the user had narrowed
     * to since. Same reason the tab router consumes its pending deep links.
     */
    fun consume(): SearchSeedRequest? {
        val seed = _pending.value
        _pending.value = null
        return seed
    }
}
