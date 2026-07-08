package `in`.artistant.app.data.repository

import `in`.artistant.app.common.util.lowercaseUuid
import `in`.artistant.app.data.model.Artist
import `in`.artistant.app.data.model.SelfArtistRow
import `in`.artistant.app.data.model.SelfAvailability

/**
 * In-memory [ArtistsRepository] for tests / previews (iOS `ArtistsRepository`'s
 * fixture path). Seed [full] with complete artists; [refresh] returns them and the
 * cache behaves like the real one — [cache] never downgrades a hydrated entry.
 */
class FakeArtistsRepository(
    full: List<Artist> = emptyList(),
) : ArtistsRepository {

    private val byId = full.associateBy { it.id.lowercaseUuid() }.toMutableMap()
    private val hydratedIds = full.map { it.id.lowercaseUuid() }.toMutableSet()
    private val roster = full.toMutableList()

    override fun find(id: String): Artist? = byId[id.lowercaseUuid()]

    override fun cachedArtists(ids: List<String>): List<Artist> =
        ids.mapNotNull { byId[it.lowercaseUuid()] }

    override fun cache(partials: List<Artist>) {
        for (a in partials) {
            val key = a.id.lowercaseUuid()
            if (key !in hydratedIds) byId[key] = a
        }
    }

    override suspend fun fetchArtist(id: String): Artist? = byId[id.lowercaseUuid()]

    override suspend fun ensureFull(id: String): Artist? = byId[id.lowercaseUuid()]

    override suspend fun fetchArtists(ids: List<String>) { /* already in memory */ }

    override suspend fun refresh(): List<Artist> = roster.toList()

    // --- Writes (in-memory; enough for wizard/EPK view-model tests) ---------

    var selfRow: SelfArtistRow? = null
    var availability: SelfAvailability = SelfAvailability(emptyList(), emptyList())
    val published = mutableSetOf<String>()

    override suspend fun upsertSelfArtist(
        handle: String,
        stageName: String,
        category: String,
        baseCity: String,
        genre: String?,
        bio: String?,
        coverGradientIndex: Int,
    ): String = "self".also {
        selfRow = SelfArtistRow(
            stageName = stageName, handle = handle, category = category, baseCity = baseCity,
            genre = genre, bio = bio, coverGradientIndex = coverGradientIndex,
            published = false, setupComplete = false,
            instagramHandle = null, spotifyArtistUrl = null, youtubeChannelUrl = null,
        )
    }

    override suspend fun savePublishedProfile(
        handle: String,
        stageName: String,
        category: String,
        baseCity: String,
        genre: String?,
        bio: String?,
        coverGradientIndex: Int,
        daysAvailable: List<String>,
        timeSlots: List<String>,
        eventTypes: List<String>,
        instagramHandle: String?,
        spotifyArtistUrl: String?,
        youtubeChannelUrl: String?,
    ): String = "self".also {
        availability = SelfAvailability(daysAvailable, timeSlots)
        selfRow = SelfArtistRow(
            stageName = stageName, handle = handle, category = category, baseCity = baseCity,
            genre = genre, bio = bio, coverGradientIndex = coverGradientIndex,
            published = false, setupComplete = true,
            instagramHandle = instagramHandle, spotifyArtistUrl = spotifyArtistUrl,
            youtubeChannelUrl = youtubeChannelUrl,
        )
    }

    override suspend fun fetchSelfArtistRow(): SelfArtistRow? = selfRow

    override suspend fun fetchSelfAvailability(): SelfAvailability = availability

    override suspend fun updateAvailability(days: List<String>, times: List<String>) {
        availability = SelfAvailability(days, times)
    }

    override suspend fun updateCoverGradient(index: Int) {
        selfRow = selfRow?.copy(coverGradientIndex = index)
    }

    override suspend fun publish(artistId: String) {
        published.add(artistId.lowercaseUuid())
        selfRow = selfRow?.copy(published = true)
    }

    override fun invalidate(id: String) {
        byId.remove(id.lowercaseUuid())
        hydratedIds.remove(id.lowercaseUuid())
    }
}
