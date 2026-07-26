package `in`.artistant.app.data.repository

import `in`.artistant.app.data.model.Artist
import `in`.artistant.app.data.model.ArtistGradient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory [ArtistsRepository] for unit tests / previews (iOS has no Fake twin
 * for Artists — Android adds one so ViewModels stay offline-testable).
 */
class FakeArtistsRepository(
    seed: List<Artist> = emptyList(),
) : ArtistsRepository {

    private val byId = seed.associateBy { it.id.lowercase() }.toMutableMap()
    private val hydratedIds = seed.map { it.id.lowercase() }.toMutableSet()
    private val _cacheGeneration = MutableStateFlow(0)
    override val cacheGeneration: StateFlow<Int> = _cacheGeneration.asStateFlow()

    /** When true, [fetchArtist] throws so callers can exercise degrade paths. */
    var failFetch: Boolean = false

    override fun find(id: String): Artist? = byId[id.lowercase()]

    override fun cachedArtists(ids: List<String>): List<Artist> =
        ids.mapNotNull { byId[it.lowercase()] }

    override fun cache(partials: List<Artist>) {
        var changed = false
        for (a in partials) {
            val id = a.id.lowercase()
            if (id in hydratedIds) continue
            byId[id] = a.copy(id = id)
            changed = true
        }
        if (changed) _cacheGeneration.value = _cacheGeneration.value + 1
    }

    override suspend fun fetchArtist(id: String): Artist? {
        if (failFetch) throw IllegalStateException("fake fetch failure")
        val key = id.lowercase()
        val artist = byId[key] ?: return null
        hydratedIds.add(key)
        return artist
    }

    override suspend fun ensureFull(id: String): Artist? =
        try {
            fetchArtist(id)
        } catch (_: Throwable) {
            null
        }

    fun seedFull(artists: List<Artist>) {
        for (a in artists) {
            val id = a.id.lowercase()
            byId[id] = a.copy(id = id)
            hydratedIds.add(id)
        }
        if (artists.isNotEmpty()) _cacheGeneration.value = _cacheGeneration.value + 1
    }

    companion object {
        fun sample(
            id: String = "a1",
            name: String = "Nova Beats",
            city: String = "Bangalore",
            category: String = "DJ",
            score: Int = 82,
            price: Int = 25000,
            gigs: Int = 12,
        ): Artist = Artist(
            id = id,
            name = name,
            handle = name.lowercase().replace(" ", ""),
            category = category,
            genre = "Electronic",
            city = city,
            price = price,
            duration = "2h",
            score = score,
            gradient = ArtistGradient.palette(0),
            gigs = gigs,
            bio = "Live sets for rooftops and weddings.",
        )
    }
}
