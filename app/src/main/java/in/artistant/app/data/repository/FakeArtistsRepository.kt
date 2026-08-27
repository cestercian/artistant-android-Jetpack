package `in`.artistant.app.data.repository

import `in`.artistant.app.core.result.AppError
import `in`.artistant.app.data.model.Artist
import `in`.artistant.app.data.model.ArtistPrompt
import `in`.artistant.app.designsystem.theme.ArtistGradient
import `in`.artistant.app.domain.artist.ArtistPrompts
import `in`.artistant.app.domain.artist.ServiceTags
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory [ArtistsRepository] for unit tests / previews (iOS has no Fake twin
 * for Artists — Android adds one so ViewModels stay offline-testable).
 */
class FakeArtistsRepository(
    seed: List<Artist> = emptyList(),
    /**
     * Artists the SERVER can answer for but the cache has NOT seen — what [find]
     * must miss and [fetchArtist]/[ensureFull] must hydrate.
     *
     * The real [find] is a pure map read with no fetch behind it, so a fake whose
     * every artist is already cached cannot tell a screen that hydrates its misses
     * from one that renders the "Artist" placeholder forever.
     */
    remote: List<Artist> = emptyList(),
    /**
     * The signed-in artist — the one row [mutateSelf], [updateAvailability] and
     * [setPublished] are allowed to touch. Leave it unset when at most one
     * artist is ever seeded, where "self" is unambiguous; name it explicitly
     * for a multi-artist fake. Before this parameter existed, every self-row
     * edit resolved its target as `byId.keys.firstOrNull()` — insertion order,
     * not identity — so a multi-artist fixture could have a press-kit save
     * land on the WRONG artist while "my edit is visible on the next read"
     * still passed.
     */
    private val selfId: String? = null,
) : ArtistsRepository {

    private val byId = seed.associateBy { it.id.lowercase() }.toMutableMap()
    private val remoteById = remote.associateBy { it.id.lowercase() }
    private val hydratedIds = seed.map { it.id.lowercase() }.toMutableSet()

    /** Confirmed misses, memoized exactly as the real repository memoizes them. */
    private val missingIds = mutableSetOf<String>()
    private val _cacheGeneration = MutableStateFlow(0)
    override val cacheGeneration: StateFlow<Int> = _cacheGeneration.asStateFlow()

    /**
     * Every [fetchArtist] ask the caches did NOT short-circuit, in call order —
     * i.e. the round trips the real repository would have paid for.
     *
     * Exposed because a miss returns null whether or not it was remembered, so
     * the only way to pin "a confirmed miss costs one fan-out, not one per call"
     * is to count the asks that got through.
     */
    val fetchedIds = mutableListOf<String>()

    /** When true, [fetchArtist] throws so callers can exercise degrade paths. */
    var failFetch: Boolean = false

    /**
     * Set by [setPublished], NOT by [publishWizardProfile] — mirroring the real
     * repository, where `setup_complete` rides the same PATCH as `published` so a
     * publish that dies between the two calls cannot strand the artist past the
     * wizard gate but invisible in Discover.
     */
    var setupComplete: Boolean = false
        private set

    var lastPublishedDraft: WizardProfileDraft? = null
        private set

    override fun find(id: String): Artist? = byId[id.lowercase()]

    override fun cachedArtists(ids: List<String>): List<Artist> =
        ids.mapNotNull { byId[it.lowercase()] }

    override fun cache(partials: List<Artist>) {
        var changed = false
        for (a in partials) {
            val id = a.id.lowercase()
            if (id in hydratedIds) continue
            missingIds.remove(id)
            byId[id] = a.copy(id = id)
            changed = true
        }
        if (changed) _cacheGeneration.value = _cacheGeneration.value + 1
    }

    override suspend fun fetchArtist(id: String): Artist? {
        if (failFetch) throw IllegalStateException("fake fetch failure")
        val key = id.lowercase()
        // Both short-circuits the real one has, in the same order: a hydrated
        // hit costs nothing, and so does a remembered miss — otherwise an id
        // that is not an artist (a blocked client, say) re-runs the whole
        // five-table fan-out on every call.
        if (key in hydratedIds) return byId[key]
        if (key in missingIds) return null
        fetchedIds += key
        // A fetch that lands CACHES, like the real one — that's what makes the
        // next `find` hit for an artist this fake only held remotely.
        val artist = byId[key] ?: remoteById[key]
        if (artist == null) {
            missingIds.add(key)
            return null
        }
        byId[key] = artist
        hydratedIds.add(key)
        _cacheGeneration.value = _cacheGeneration.value + 1
        return artist
    }

    override suspend fun ensureFull(id: String): Artist? =
        try {
            fetchArtist(id)
        } catch (_: Throwable) {
            null
        }

    override suspend fun publishWizardProfile(draft: WizardProfileDraft) {
        lastPublishedDraft = draft
        val id = draft.artistId.lowercase()
        byId[id] = FakeArtistsRepository.sample(
            id = id,
            name = draft.stageName,
            city = draft.baseCity,
            category = draft.category,
        ).copy(
            handle = draft.handle.lowercase(),
            genre = draft.genre,
            bio = draft.bio,
            daysAvailable = draft.daysAvailable,
            timeSlots = draft.timeSlots,
            // The palette rides the row like every other column: the real upsert
            // now always sends `cover_gradient_index`, so a fake that dropped it
            // would let a "the artist's palette choice landed" test pass on a
            // write that never carried one.
            coverGradientIndex = ArtistGradient.clampIndex(draft.coverGradientIndex),
            gradient = ArtistGradient.palette(draft.coverGradientIndex),
            instagramHandle = draft.instagramHandle,
            spotifyArtistUrl = draft.spotifyArtistUrl,
            youtubeChannelUrl = draft.youtubeChannelUrl,
        )
        hydratedIds.add(id)
        // The real repository invalidates this id after the upsert, misses
        // included — a first publish INSERTS a row an earlier lookup missed.
        missingIds.remove(id)
        _cacheGeneration.value = _cacheGeneration.value + 1
    }

    var published: Boolean = false
        private set
    var lastAvailability: AvailabilityDraft? = null
        private set

    override suspend fun setPublished(artistId: String, published: Boolean) {
        // Mirrors the real repository's `require(userId == artistId.lowercase())`
        // self-only guard — resolveSelfId() throws the same NotFoundOrUnauthorized
        // signal the real one throws when there's no session to check against.
        require(resolveSelfId() == artistId.lowercase()) { "Can only publish self." }
        this.published = published
        setupComplete = true
    }

    override suspend fun fetchSelfAvailability(): AvailabilityDraft? = lastAvailability

    override suspend fun updateAvailability(daysAvailable: List<String>, timeSlots: List<String>) {
        lastAvailability = AvailabilityDraft(daysAvailable, timeSlots)
        val id = resolveSelfId()
        byId[id] = byId.getValue(id).copy(daysAvailable = daysAvailable, timeSlots = timeSlots)
        _cacheGeneration.value = _cacheGeneration.value + 1
    }

    /**
     * The narrow self-row edits, applied to [resolveSelfId]'s target only.
     *
     * They mutate the cached row rather than only recording the call, because the
     * thing worth testing about them is that a save is VISIBLE on the next read —
     * the real repository's bug class here is a write that lands and then gets
     * masked by a stale cache entry, and a fake that only records would pass
     * whether or not the production code invalidated.
     */
    override suspend fun updateBio(bio: String) = mutateSelf { it.copy(bio = bio.trim()) }

    override suspend fun updateCoverGradient(index: Int) = mutateSelf {
        val clamped = ArtistGradient.clampIndex(index)
        it.copy(coverGradientIndex = clamped, gradient = ArtistGradient.palette(clamped))
    }

    override suspend fun updateNewArtistDiscount(pct: Int) = mutateSelf {
        it.copy(newArtistDiscountPct = pct.coerceIn(0, 100))
    }

    override suspend fun updateServiceTags(tags: List<String>) = mutateSelf {
        // Normalized here too, because the production repository normalizes on
        // the way out — a fake that stored the raw list would let a test pass on
        // a set the server would never have received.
        it.copy(serviceTags = ServiceTags.normalize(tags))
    }

    override suspend fun updateWeekendPremium(pct: Int) = mutateSelf {
        it.copy(weekendPremiumPct = pct.coerceIn(0, 100))
    }

    override suspend fun updatePrompts(prompts: List<ArtistPrompt>) = mutateSelf {
        // Round-tripped through encode/decode rather than stored as handed in, so
        // the fake holds what the SERVER would hold — blank answers dropped,
        // answers clamped. A fake that stored the draft verbatim would hide
        // exactly the encoding bugs these helpers exist to prevent.
        it.copy(prompts = ArtistPrompts.decode(ArtistPrompts.encode(prompts)))
    }

    override suspend fun updateSocialLinks(instagram: String?, spotify: String?, youtube: String?) =
        mutateSelf {
            it.copy(
                instagramHandle = instagram?.trim()?.ifBlank { null },
                spotifyArtistUrl = spotify?.trim()?.ifBlank { null },
                youtubeChannelUrl = youtube?.trim()?.ifBlank { null },
            )
        }

    private fun mutateSelf(transform: (Artist) -> Artist) {
        val id = resolveSelfId()
        byId[id] = transform(byId.getValue(id))
        _cacheGeneration.value = _cacheGeneration.value + 1
    }

    /**
     * [selfId] when named, else the one artist a single-artist fake holds.
     * Throws the same signal the real repository throws when there's no
     * session, rather than silently guessing a target the way
     * `byId.keys.firstOrNull()` used to (see [selfId]'s doc).
     */
    private fun resolveSelfId(): String {
        val id = (selfId ?: byId.keys.singleOrNull())?.lowercase()
        if (id == null || id !in byId) throw AppError.NotFoundOrUnauthorized
        return id
    }

    fun seedFull(artists: List<Artist>) {
        for (a in artists) {
            val id = a.id.lowercase()
            byId[id] = a.copy(id = id)
            hydratedIds.add(id)
            missingIds.remove(id)
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
