package `in`.artistant.app.feature.search

import `in`.artistant.app.platform.storage.AppPreferences
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistence seam for the Search "recent queries" rail (iOS `Persistence` +
 * `SearchStore.recents`). An interface — not a direct [AppPreferences] call —
 * purely so [SearchViewModel] stays unit-testable without an Android `Context`
 * (DataStore needs one). The DataStore impl stores the list newline-joined under
 * one key; a fake in tests holds it in memory.
 */
interface SearchRecents {
    suspend fun load(): List<String>
    suspend fun save(terms: List<String>)
}

@Singleton
class DataStoreSearchRecents @Inject constructor(
    private val prefs: AppPreferences,
) : SearchRecents {
    override suspend fun load(): List<String> =
        prefs.getString(KEY).first()?.split("\n")?.filter { it.isNotBlank() }.orEmpty()

    override suspend fun save(terms: List<String>) =
        prefs.setString(KEY, terms.joinToString("\n"))

    private companion object {
        const val KEY = "search.recents"
    }
}
