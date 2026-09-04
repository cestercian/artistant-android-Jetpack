package `in`.artistant.app.feature.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.data.model.Artist
import `in`.artistant.app.data.model.SearchCursor
import `in`.artistant.app.data.model.SearchFilters
import `in`.artistant.app.data.model.SearchSort
import `in`.artistant.app.data.repository.ArtistsRepository
import `in`.artistant.app.data.repository.SearchRepository
import `in`.artistant.app.data.repository.UsersRepository
import `in`.artistant.app.feature.saved.SavedStore
import `in`.artistant.app.feature.system.ActivityLog
import `in`.artistant.app.feature.system.unreadActivityCount
import `in`.artistant.app.feature.search.SearchSeed
import `in`.artistant.app.feature.search.SearchSeedRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * One titled row of the Discover feed.
 *
 * [seed] is what the row's "See all" means, carried on the rail rather than
 * re-derived in the composable: the rail's title and the search it stands for are
 * the same claim, and a screen that computed the second from the first would let
 * them drift the moment a title is reworded.
 */
data class DiscoverRail(
    val id: String,
    val title: String,
    val artists: List<Artist>,
    val seed: SearchSeedRequest,
)

/**
 * Discover home — port of iOS `DiscoverFeedStore`, re-cut for the Sep-2026 light
 * design (screen 02).
 *
 * One hero act over a stack of titled rails, each rail its own bounded
 * `search_artists` page fired concurrently.
 */
data class DiscoverUiState(
    val hero: Artist? = null,
    val rails: List<DiscoverRail> = emptyList(),
    /** Facet labels for the category chip rail. Empty until `search_facets` lands. */
    val categories: List<String> = emptyList(),
    /** null = "For you", the unfiltered feed. */
    val selectedCategory: String? = null,
    val isLoading: Boolean = false,
    val loadError: String? = null,
    /**
     * The user's city, driving the header subtitle. Loaded separately from the
     * rails and never allowed to fail the screen — a missing city downgrades the
     * subtitle to the national fallback, it does not blank Discover.
     */
    val city: String? = null,
    /** The user's name, for a monogram where one is wanted. */
    val displayName: String? = null,
    /**
     * The day the feed is scoped to.
     *
     * Held in state rather than read from the clock in the composable so the
     * header's date, the availability rail's title and the rail's own `p_date`
     * argument are all THE SAME day — recomputing `LocalDate.now()` per reader
     * lets them disagree across midnight, which is precisely when a feed captioned
     * with yesterday's date is most misleading.
     */
    val today: LocalDate = LocalDate.now(),
) {
    /** "Bengaluru · Sat 12 Oct" — see [DiscoverHeroLogic.headerSubtitle]. */
    val headerSubtitle: String get() = DiscoverHeroLogic.headerSubtitle(city, today)

    /** Masthead avatar monogram; empty when the user has no name yet. */
    val avatarInitial: String get() = DiscoverHeroLogic.avatarInitial(displayName)

    /** Nothing came back at all — the empty-roster branch, not the failure one. */
    val isEmpty: Boolean get() = hero == null && rails.isEmpty()
}

@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
    private val artistsRepository: ArtistsRepository,
    private val usersRepository: UsersRepository,
    private val savedStore: SavedStore,
    activityLog: ActivityLog,
    private val searchSeed: SearchSeed = SearchSeed(),
) : ViewModel() {

    private val _state = MutableStateFlow(DiscoverUiState())
    val state: StateFlow<DiscoverUiState> = _state.asStateFlow()

    /**
     * The rails load in flight, so a new one can retire it.
     *
     * Without this every category tap started a load that nobody stopped, and
     * five concurrent RPCs do not finish in the order they were issued: the
     * loser landing last wrote ITS artists into the current state, while the
     * rail titles and "See all" seeds — which [applyRails] derives from
     * `selectedCategory` — described the category the user had actually picked.
     * A feed of DJs under headings about comedy, with no error and nothing to
     * retry.
     */
    private var railsJob: Job? = null

    /** Saved-heart ids, straight off the shared optimistic store. */
    val savedIds: StateFlow<Set<String>> = savedStore.ids

    /**
     * Is there an unread notification behind the header's bell (screen 02)?
     *
     * The log is this device's own record of arriving pushes — there is no
     * server-side read state and screen 123 says so — which is exactly why the
     * dot is derived from it rather than from a count the feed would have to be
     * told. Cold-started `false`, so a bell never claims news before the store
     * has been read.
     */
    val hasUnreadActivity: StateFlow<Boolean> = activityLog.entries
        .map { unreadActivityCount(it) > 0 }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(UNREAD_SUBSCRIPTION_GRACE_MS),
            initialValue = false,
        )

    init {
        loadCategories()
        refresh()
    }

    /** Optimistic save/unsave for the hero's heart. */
    fun toggleSaved(artistId: String) = savedStore.toggle(artistId)

    /**
     * Narrow the whole feed to one category, or back to "For you" with null.
     *
     * The chips filter Discover IN PLACE rather than jumping to Search: the design
     * puts them between the search bar and the hero, above content they are
     * plainly meant to be scoping, and a chip that navigated away would leave the
     * feed it appears to control untouched.
     */
    fun selectCategory(category: String?) {
        if (_state.value.selectedCategory == category) return
        _state.update { it.copy(selectedCategory = category) }
        refresh()
    }

    /** Hand a rail's filters to the Search tab. See [SearchSeed]. */
    fun seedSearch(seed: SearchSeedRequest) = searchSeed.request(seed)

    /**
     * The category facet list behind the chip rail.
     *
     * Its own coroutine, run once, and deliberately NOT part of [refresh]: the
     * facets describe the whole roster, not the current filter, so re-reading them
     * on every pull would cost a round trip to learn what we already know — and a
     * failure must leave the rail as it is rather than emptying the one control
     * that can undo a filter.
     */
    private fun loadCategories() {
        viewModelScope.launch {
            val facets = runCatching { searchRepository.facets() }.getOrNull() ?: return@launch
            _state.update { it.copy(categories = facets.categories.map { f -> f.label }) }
        }
    }

    /**
     * Masthead personalisation. Deliberately a separate coroutine from the rails
     * below: the subtitle is cosmetic, so a failed profile read must not surface
     * as a roster error or retry the rails. On failure we simply keep whatever
     * the header already had — the national fallback on a cold start.
     */
    private fun loadIdentity() {
        viewModelScope.launch {
            val profile = runCatching { usersRepository.fetchSelfProfile() }.getOrNull()
                ?: return@launch
            _state.update { it.copy(city = profile.city, displayName = profile.fullName) }
        }
    }

    /**
     * Reloads the rails AND the header.
     *
     * The identity read used to run once, from `init`. Discover is the NavHost
     * start destination and its entry is never popped, so its ViewModel lives for
     * the whole session: a profile read that blipped at cold start left the
     * national fallback pinned there for good, with pull-to-refresh visibly doing
     * nothing about it. The retry the user reaches for has to cover both halves.
     */
    fun refresh() {
        loadIdentity()
        // Retire whatever is in the air BEFORE launching the replacement.
        railsJob?.cancel()
        railsJob = viewModelScope.launch {
            // The day is re-read here and nowhere else, so a session left open
            // overnight picks up the new date on the next pull instead of
            // captioning today's roster with yesterday.
            _state.update { it.copy(isLoading = true, loadError = null, today = LocalDate.now()) }
            // The category this load speaks for. Cancellation alone is not
            // enough: `viewModelScope` is Main.immediate and a coroutine already
            // past its last suspension point runs to completion regardless, so
            // the answer is stamped and checked on arrival as well.
            val issuedFor = _state.value.selectedCategory
            try {
                loadRails(issuedFor)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                // A failure belonging to a category nobody is looking at any more
                // must not blank the feed that replaced it.
                if (_state.value.selectedCategory != issuedFor) return@launch
                _state.update {
                    it.copy(isLoading = false, loadError = messageFor(t))
                }
            }
        }
    }

    private suspend fun loadRails(category: String?) = coroutineScope {
        val today = _state.value.today
        val todayIso = today.toString()
        val scope = listOfNotNull(category)

        val topDeferred = async {
            searchRepository.search(
                SearchFilters(categories = scope, sort = SearchSort.Bookability),
                SearchCursor.Start,
            )
        }
        val availableDeferred = async {
            searchRepository.search(
                filters = SearchFilters(categories = scope, sort = SearchSort.Bookability),
                cursor = SearchCursor.Start,
                services = null,
                date = todayIso,
                flexDays = 0,
            )
        }
        val cityDeferred = async {
            searchRepository.search(
                SearchFilters(city = TOP_CITY, categories = scope, sort = SearchSort.Bookability),
                SearchCursor.Start,
            )
        }
        val newDeferred = async {
            searchRepository.search(
                SearchFilters(categories = scope, sort = SearchSort.New),
                SearchCursor.Start,
            )
        }
        val comedyDeferred = async {
            // Skipped entirely while a category is selected — under "DJs" a
            // comedy rail is either empty or contradicts the chip above it.
            if (category != null) null else searchRepository.search(
                SearchFilters(categories = COMEDY_CATEGORIES, sort = SearchSort.Bookability),
                SearchCursor.Start,
            )
        }

        val top = topDeferred.await().artists
        val available = availableDeferred.await().artists
        val city = cityDeferred.await().artists
        val fresh = newDeferred.await().artists
        val comedy = comedyDeferred.await()?.artists.orEmpty()

        // Cache every fetched artist so profile taps resolve via ensureFull.
        artistsRepository.cache(top + available + city + fresh + comedy)

        _state.update { applyRails(it, category, top, available, city, fresh, comedy) }
    }

    companion object {
        /** Keeps the bell's flow alive across a tab switch rather than re-reading DataStore. */
        private const val UNREAD_SUBSCRIPTION_GRACE_MS = 5_000L

        private const val TOP_CITY = "Bangalore"
        private val COMEDY_CATEGORIES = listOf("Stand-up")

        /** Rows kept per rail — two visible, the rest a horizontal scroll. */
        private const val RAIL_LIMIT = 10

        /**
         * The rail projection [loadRails] applies once the queries land.
         *
         * Pure and `internal` (not `private`) so the mapping — which artist page
         * feeds which rail, how each is titled and what its "See all" means — can
         * be pinned by a JVM test without a ViewModel runtime.
         *
         * A rail with nothing in it is DROPPED rather than rendered empty: a
         * titled row over a blank strip claims a section exists and then fails to
         * show it, which reads as a loading bug rather than as a young roster.
         */
        internal fun applyRails(
            state: DiscoverUiState,
            issuedFor: String?,
            top: List<Artist>,
            available: List<Artist>,
            city: List<Artist>,
            fresh: List<Artist>,
            comedy: List<Artist>,
        ): DiscoverUiState {
            // A page fetched for a category the user has since left describes
            // somebody else's feed. Dropping it here — rather than trusting the
            // job cancellation alone — is what keeps the artists on screen and
            // the headings above them talking about the same thing.
            if (state.selectedCategory != issuedFor) return state
            val category = state.selectedCategory
            val today = state.today
            // Only artists whose OWN published week names this day. The server's
            // `p_date` is not evidence on its own — see
            // [DiscoverHeroLogic.evidencesAvailability].
            val free = available.filter {
                DiscoverHeroLogic.evidencesAvailability(it.daysAvailable, today)
            }
            val rails = listOfNotNull(
                rail(
                    id = "available",
                    title = DiscoverHeroLogic.availableRailTitle(today),
                    artists = free,
                    seed = SearchSeedRequest(category = category, dateIso = today.toString()),
                ),
                rail(
                    id = "city",
                    title = "Top in $TOP_CITY",
                    artists = city,
                    seed = SearchSeedRequest(city = TOP_CITY, category = category),
                ),
                rail(
                    id = "new",
                    title = "New on Artistant",
                    artists = fresh,
                    seed = SearchSeedRequest(category = category),
                ),
                rail(
                    id = "comedy",
                    title = "Comedy",
                    artists = comedy,
                    seed = SearchSeedRequest(category = COMEDY_CATEGORIES.first()),
                ),
            )
            return state.copy(
                hero = top.firstOrNull(),
                rails = rails,
                isLoading = false,
                loadError = null,
            )
        }

        private fun rail(
            id: String,
            title: String,
            artists: List<Artist>,
            seed: SearchSeedRequest,
        ): DiscoverRail? =
            artists.take(RAIL_LIMIT)
                .takeIf { it.isNotEmpty() }
                ?.let { DiscoverRail(id = id, title = title, artists = it, seed = seed) }

        /**
         * The detail line under both failure surfaces — the full-screen empty
         * state (no rails at all) and the inline banner over a roster that is
         * already up.
         *
         * It used to end "Pull to refresh in a moment." Wrong on the branch it
         * shows on most: with no rails the screen is a plain `EmptyState`, which
         * has nothing scrollable in it, so `PullToRefreshBox`'s nested-scroll
         * connection never sees a delta and the gesture the sentence asks for
         * does nothing. Both surfaces carry a button instead, so the copy points
         * at the button.
         */
        fun messageFor(error: Throwable): String {
            val m = error.message.orEmpty().lowercase()
            if (m.contains("could not find the function") ||
                m.contains("search_artists") ||
                m.contains("42883") ||
                m.contains("does not exist")
            ) {
                return "We couldn't load the roster right now. Try again in a moment."
            }
            return "Something went wrong loading the roster."
        }
    }
}
