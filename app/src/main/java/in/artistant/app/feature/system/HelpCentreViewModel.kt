package `in`.artistant.app.feature.system

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.data.repository.UsersRepository
import `in`.artistant.app.designsystem.theme.AppRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class HelpCentreUiState(
    val audience: HelpAudience = HelpAudience.Clients,
    val query: String = "",
    /** The user's own first name, for the greeting. Null until the profile lands. */
    val firstName: String? = null,
    /** The promoted blocking item, or null when there is nothing in the way. */
    val outstanding: HelpAction? = null,
    val expanded: Set<String> = emptySet(),
) {
    val articles: List<HelpArticle> get() = helpArticles(audience, query)

    /**
     * "Hi Rhea, how can we help?" degrades to the plain question rather than to
     * "Hi , how can we help?" — the profile read can fail, and a greeting with a
     * hole in it is worse than no greeting.
     */
    val greeting: String
        get() = firstName?.takeIf { it.isNotBlank() }
            ?.let { "Hi $it, how can we help?" }
            ?: "How can we help?"
}

/**
 * Screen 63.
 *
 * Reads the signed-in profile for two things and no more: the greeting, and
 * whether anything is blocking the user ([outstandingHelpItem]). A failed read
 * silently costs the greeting and the promoted card — neither is worth an error
 * state on a screen somebody opened because something else was already wrong.
 */
@HiltViewModel
class HelpCentreViewModel @Inject constructor(
    private val users: UsersRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HelpCentreUiState())
    val state: StateFlow<HelpCentreUiState> = _state.asStateFlow()

    private var role: AppRole = AppRole.Client

    /**
     * Told, not fetched: the tab scaffold that hosts this screen already knows
     * which graph it is, and re-reading the role here would be a second source
     * of truth for a fact the navigation is built on.
     */
    fun start(role: AppRole) {
        if (this.role == role && _state.value.firstName != null) return
        this.role = role
        _state.update { it.copy(audience = HelpAudience.forRole(role)) }
        viewModelScope.launch {
            val profile = runCatching { users.fetchSelfProfile() }
                .onFailure { Timber.w(it, "Help centre couldn't read the profile") }
                .getOrNull()
            _state.update {
                it.copy(
                    firstName = profile?.fullName?.trim()?.substringBefore(' ')
                        ?.takeIf { name -> name.isNotBlank() },
                    outstanding = outstandingHelpItem(profile, role),
                )
            }
        }
    }

    fun setAudience(audience: HelpAudience) = _state.update { it.copy(audience = audience) }

    fun setQuery(query: String) = _state.update { it.copy(query = query) }

    /**
     * Answers open in place.
     *
     * The design draws a chevron, which on iOS means a push to an article — and
     * there is no article store to push to, on the server or in the build. An
     * accordion keeps the row shape the design specifies and shows the answer
     * that actually exists, rather than pushing an empty screen with a title.
     */
    fun toggle(question: String) = _state.update {
        it.copy(
            expanded = if (question in it.expanded) it.expanded - question else it.expanded + question,
        )
    }
}
