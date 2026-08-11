package `in`.artistant.app.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import `in`.artistant.app.core.config.AppEnvironment
import `in`.artistant.app.core.result.AppError
import `in`.artistant.app.core.result.mapPostgrest
import `in`.artistant.app.data.model.Artist
import `in`.artistant.app.data.model.ArtistGradient
import `in`.artistant.app.data.model.ArtistPackage
import `in`.artistant.app.data.model.Sample
import `in`.artistant.app.domain.artist.ServiceTags
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Id-keyed hydrating artist cache — port of iOS `ArtistsRepository`.
 *
 * Discover/Search page the server and merge tile projections via [cache];
 * profile taps call [fetchArtist]/[ensureFull] for a full 5-table stitch.
 * Never downgrades a hydrated entry to a partial.
 */
interface ArtistsRepository {
    /** Bumped whenever the by-id cache changes — observers re-resolve by id. */
    val cacheGeneration: StateFlow<Int>

    fun find(id: String): Artist?

    fun cachedArtists(ids: List<String>): List<Artist>

    /** Merge tile projections; never overwrites a fully-hydrated profile. */
    fun cache(partials: List<Artist>)

    /** Full stitch (packages/bio/stats/availability). null = not found / RLS. */
    suspend fun fetchArtist(id: String): Artist?

    /** Non-throwing convenience — swallows transport errors as null. */
    suspend fun ensureFull(id: String): Artist?

    /** Wizard Done — upsert the signed-in artist row and flip `setup_complete`. */
    suspend fun publishWizardProfile(draft: WizardProfileDraft)

    /** Flip `published` so Discover can see the artist (sync go-live; media is async). */
    suspend fun setPublished(artistId: String, published: Boolean = true)

    /** Own availability columns — ManageAvailability + wizard seed. */
    suspend fun fetchSelfAvailability(): AvailabilityDraft?

    suspend fun updateAvailability(daysAvailable: List<String>, timeSlots: List<String>)

    // ── Narrow self-row edits (the press-kit editor) ─────────────────────────
    //
    // Each of these PATCHes exactly the columns it names, filtered to the
    // signed-in artist. That narrowness is the whole point: the only writer these
    // columns had was [publishWizardProfile], which upserts the WHOLE row and
    // therefore blanks every profile column it does not carry. Editing a bio
    // through that path would have silently cleared the artist's social links and
    // cover choice on the way past, so the editor rendered those sections
    // read-only instead. A PATCH per concern is what makes them editable without
    // that collateral, and it is the same shape [updateAvailability] already uses.

    /** `artists.bio`. Blank persists as NULL — an empty bio is absent, not "". */
    suspend fun updateBio(bio: String)

    /** `artists.cover_gradient_index`. Clamped to the palette range before it goes. */
    suspend fun updateCoverGradient(index: Int)

    /**
     * `artists.new_artist_discount_pct`, clamped to 0–100.
     *
     * This column had a reader (the public profile prints "New-artist offer: N%
     * off your booking") and, before this, no writer anywhere in the app — not
     * even the wizard. On a backend shared with another client that DOES set it,
     * that left an artist with a discount advertised on their own profile and no
     * way to withdraw it.
     */
    suspend fun updateNewArtistDiscount(pct: Int)

    /**
     * `artists.service_tags` — the whole set, every time.
     *
     * Whole-set semantics even though it is one column, so it carries the same
     * hazard `updateSocialLinks` does: a caller that has not read the row sends
     * an empty array and silently un-publishes every service the artist offers.
     * Callers gate on the editor's identity-hydrated flag for that reason.
     *
     * Slugs, not labels — see `ServiceTags` for why an exact-match filter makes
     * that the difference between a findable artist and an invisible one.
     */
    suspend fun updateServiceTags(tags: List<String>)

    /**
     * The three social columns, all three every time.
     *
     * Whole-set semantics on purpose: the caller has to pass the current values of
     * the two it is not editing, so an un-hydrated caller cannot send two nulls and
     * silently unlink them. See the editor's save path for the guard that enforces
     * it has read them first.
     */
    suspend fun updateSocialLinks(instagram: String?, spotify: String?, youtube: String?)
}

/** Days + preferred start times on `artists` (not a separate table). */
data class AvailabilityDraft(
    val daysAvailable: List<String>,
    val timeSlots: List<String>,
)

@Singleton
class SupabaseArtistsRepository @Inject constructor(
    private val client: SupabaseClient,
) : ArtistsRepository {

    private val byId = mutableMapOf<String, Artist>()
    private val hydratedIds = mutableSetOf<String>()
    private val _cacheGeneration = MutableStateFlow(0)
    override val cacheGeneration: StateFlow<Int> = _cacheGeneration.asStateFlow()

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
        val key = id.lowercase()
        if (key in hydratedIds) return byId[key]
        val artist = fetchMany(listOf(key)).firstOrNull() ?: return null
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
        val userId = client.auth.currentSessionOrNull()?.user?.id?.lowercase()
            ?: throw AppError.NotFoundOrUnauthorized
        require(userId == draft.artistId.lowercase()) {
            "Wizard publish must target the signed-in artist."
        }
        val row = WizardPublishRow(
            id = userId,
            handle = draft.handle.trim().lowercase(),
            stageName = draft.stageName.trim(),
            category = draft.category,
            baseCity = draft.baseCity.trim(),
            genre = draft.genre.ifBlank { null },
            bio = draft.bio.ifBlank { null },
            coverGradientIndex = draft.coverGradientIndex,
            daysAvailable = draft.daysAvailable,
            defaultTimeSlots = draft.timeSlots,
            instagramHandle = draft.instagramHandle?.ifBlank { null },
            spotifyArtistUrl = draft.spotifyArtistUrl?.ifBlank { null },
            youtubeChannelUrl = draft.youtubeChannelUrl?.ifBlank { null },
            setupComplete = true,
        )
        try {
            client.from("artists").upsert(row) { onConflict = "id" }
        } catch (t: Throwable) {
            throw mapPostgrest(t)
        }
        invalidate(userId)
    }

    override suspend fun setPublished(artistId: String, published: Boolean) {
        val userId = client.auth.currentSessionOrNull()?.user?.id?.lowercase()
            ?: throw AppError.NotFoundOrUnauthorized
        require(userId == artistId.lowercase()) { "Can only publish self." }
        try {
            client.from("artists").update(PublishedPatch(published)) {
                filter { eq("id", userId) }
            }
        } catch (t: Throwable) {
            throw mapPostgrest(t)
        }
        invalidate(userId)
    }

    override suspend fun fetchSelfAvailability(): AvailabilityDraft? {
        val userId = client.auth.currentSessionOrNull()?.user?.id?.lowercase()
            ?: return null
        return try {
            client.from("artists")
                .select(Columns.list("days_available", "default_time_slots")) {
                    filter { eq("id", userId) }
                }
                .decodeList<AvailabilityRow>()
                .firstOrNull()
                ?.let {
                    AvailabilityDraft(
                        daysAvailable = it.daysAvailable.orEmpty(),
                        timeSlots = it.defaultTimeSlots.orEmpty(),
                    )
                }
        } catch (t: Throwable) {
            throw mapPostgrest(t)
        }
    }

    override suspend fun updateAvailability(daysAvailable: List<String>, timeSlots: List<String>) {
        val userId = client.auth.currentSessionOrNull()?.user?.id?.lowercase()
            ?: throw AppError.NotFoundOrUnauthorized
        try {
            client.from("artists").update(
                AvailabilityPatch(daysAvailable = daysAvailable, defaultTimeSlots = timeSlots),
            ) {
                filter { eq("id", userId) }
            }
        } catch (t: Throwable) {
            throw mapPostgrest(t)
        }
        invalidate(userId)
    }

    override suspend fun updateBio(bio: String) =
        patchSelf(BioPatch(bio.trim().ifBlank { null }))

    override suspend fun updateCoverGradient(index: Int) =
        patchSelf(CoverGradientPatch(ArtistGradient.clampIndex(index)))

    override suspend fun updateNewArtistDiscount(pct: Int) =
        patchSelf(NewArtistDiscountPatch(pct.coerceIn(0, MAX_PCT)))

    override suspend fun updateServiceTags(tags: List<String>) =
        patchSelf(ServiceTagsPatch(ServiceTags.normalize(tags)))

    override suspend fun updateSocialLinks(instagram: String?, spotify: String?, youtube: String?) =
        patchSelf(
            SocialLinksPatch(
                instagramHandle = instagram?.trim()?.ifBlank { null },
                spotifyArtistUrl = spotify?.trim()?.ifBlank { null },
                youtubeChannelUrl = youtube?.trim()?.ifBlank { null },
            ),
        )

    /**
     * One PATCH against the signed-in artist's own row, then drop the cache entry.
     *
     * The invalidation is not optional. [fetchArtist] returns a hydrated entry
     * WITHOUT re-reading, so a successful write followed by a refresh would hand
     * back the pre-write row and the edit would appear to revert — the same trap
     * the editor's per-section repositories exist to avoid.
     *
     * Reified so each caller passes its own tiny patch DTO. A shared "everything
     * nullable" patch type would compile, and would also be a loaded gun: one
     * forgotten field and a targeted edit becomes a whole-row overwrite, which is
     * exactly what these methods exist to avoid.
     */
    private suspend inline fun <reified T : Any> patchSelf(patch: T) {
        val userId = client.auth.currentSessionOrNull()?.user?.id?.lowercase()
            ?: throw AppError.NotFoundOrUnauthorized
        try {
            client.from("artists").update(patch) {
                filter { eq("id", userId) }
            }
        } catch (t: Throwable) {
            throw mapPostgrest(t)
        }
        invalidate(userId)
    }

    private fun invalidate(userId: String) {
        byId.remove(userId)
        hydratedIds.remove(userId)
        _cacheGeneration.value = _cacheGeneration.value + 1
    }

    private suspend fun fetchMany(ids: List<String>): List<Artist> {
        if (ids.isEmpty()) return emptyList()
        val artists = client.from("artists")
            .select {
                filter { isIn("id", ids) }
            }
            .decodeList<DbArtist>()
        val packages = client.from("packages")
            .select {
                filter { isIn("artist_id", ids) }
                order("artist_id", Order.ASCENDING)
                order("position", Order.ASCENDING)
            }
            .decodeList<DbPackage>()
        val tech = client.from("tech_rider")
            .select {
                filter { isIn("artist_id", ids) }
                order("artist_id", Order.ASCENDING)
                order("position", Order.ASCENDING)
            }
            .decodeList<DbTechItem>()
        val samples = client.from("samples")
            .select {
                filter { isIn("artist_id", ids) }
                order("artist_id", Order.ASCENDING)
                order("position", Order.ASCENDING)
            }
            .decodeList<DbSample>()
        val covers = client.from("artist_media")
            .select(Columns.list("artist_id", "storage_path", "position")) {
                filter {
                    isIn("artist_id", ids)
                    eq("kind", "photo")
                }
                order("artist_id", Order.ASCENDING)
                order("position", Order.ASCENDING)
            }
            .decodeList<DbArtistCover>()
        return stitch(artists, packages, tech, samples, covers)
    }

    companion object {
        /** Public CDN URL for an `artist-media` storage path. */
        fun coverUrl(storagePath: String): String? {
            if (storagePath.isBlank()) return null
            val base = AppEnvironment.supabaseUrl.trimEnd('/')
            if (base.isBlank()) return null
            return "$base/storage/v1/object/public/artist-media/$storagePath"
        }

        internal fun stitch(
            artists: List<DbArtist>,
            packages: List<DbPackage>,
            tech: List<DbTechItem>,
            samples: List<DbSample>,
            covers: List<DbArtistCover>,
        ): List<Artist> {
            val packagesBy = packages.groupBy { it.artistId.lowercase() }
            val techBy = tech.groupBy { it.artistId.lowercase() }
            val samplesBy = samples.groupBy { it.artistId.lowercase() }
            val coverBy = covers.groupBy { it.artistId.lowercase() }
                .mapValues { (_, rows) ->
                    rows.firstOrNull()?.let { coverUrl(it.storagePath) }
                }
            return artists.map { row ->
                val id = row.id.lowercase()
                val pkgs = packagesBy[id].orEmpty().map { it.toPackage() }
                row.toArtist(
                    packages = pkgs,
                    tech = techBy[id].orEmpty().map { it.item },
                    samples = samplesBy[id].orEmpty().map { it.toSample() },
                    coverUrl = coverBy[id],
                )
            }
        }
    }
}

// --- DB row DTOs (explicit columns where we can; artists uses default select
// matching iOS's full-row decode for the stitch path). ---

@Serializable
internal data class DbArtist(
    val id: String,
    val handle: String,
    @SerialName("stage_name") val stageName: String,
    val category: String,
    val genre: String? = null,
    @SerialName("base_city") val baseCity: String,
    val bio: String? = null,
    @SerialName("cover_gradient_index") val coverGradientIndex: Int = 0,
    @SerialName("followers_label") val followersLabel: String = "",
    @SerialName("streams_label") val streamsLabel: String = "",
    @SerialName("response_label") val responseLabel: String = "",
    @SerialName("on_time_rate") val onTimeRate: Int = 0,
    @SerialName("total_gigs") val totalGigs: Int = 0,
    val rating: Double = 0.0,
    val score: Int = 0,
    @SerialName("spotify_artist_url") val spotifyArtistUrl: String? = null,
    @SerialName("instagram_handle") val instagramHandle: String? = null,
    @SerialName("youtube_channel_url") val youtubeChannelUrl: String? = null,
    @SerialName("days_available") val daysAvailable: List<String>? = null,
    @SerialName("default_time_slots") val defaultTimeSlots: List<String>? = null,
    @SerialName("new_artist_discount_pct") val newArtistDiscountPct: Int? = null,
    // Migration-0073 columns. All nullable-with-default so this DTO still decodes
    // against a server that predates 0073 — the stitch is the profile screen's
    // only read, and a missing column must degrade to an empty section, not to a
    // profile that will not open.
    @SerialName("service_tags") val serviceTags: List<String>? = null,
) {
    fun toArtist(
        packages: List<ArtistPackage>,
        tech: List<String>,
        samples: List<Sample>,
        coverUrl: String?,
    ): Artist {
        // `Artist.price`/`duration` are the artist's **"from" figures — the
        // cheapest tier**, not the headline one. Every consumer reads them that
        // way: the tile renders the price under a "from" framing, the profile
        // dock uses it as the fallback before a package list has loaded, and the
        // search projection fills the same field from the server's `min_price`.
        //
        // This used to take the *popular* package, falling back to the *first* —
        // both ordering facts, neither a pricing one. An artist who leads with
        // their most expensive tier (an ordinary way to sell: full band up top,
        // cheap acoustic set below) had every surface backed by this field
        // advertising their dearest tier as their "from" price, while the profile
        // — which computes the minimum live from the loaded packages — quoted the
        // real one. Two paths filling one field with two different meanings.
        //
        // Duration comes from the SAME package as the price. Quoting one tier's
        // price beside another's duration is the subtler lie, because nothing on
        // screen looks wrong.
        val cheapest = packages.minByOrNull { it.price }
        return Artist(
            id = id.lowercase(),
            name = stageName,
            handle = handle,
            category = category,
            genre = genre.orEmpty(),
            city = baseCity,
            price = cheapest?.price ?: 0,
            duration = cheapest?.duration ?: "set",
            score = score,
            gradient = ArtistGradient.palette(coverGradientIndex),
            bio = bio.orEmpty(),
            followers = followersLabel,
            streams = streamsLabel,
            response = responseLabel,
            onTime = onTimeRate,
            gigs = totalGigs,
            rating = rating,
            packages = packages,
            tech = tech,
            samples = samples,
            spotifyArtistUrl = spotifyArtistUrl,
            instagramHandle = instagramHandle,
            youtubeChannelUrl = youtubeChannelUrl,
            daysAvailable = daysAvailable.orEmpty(),
            timeSlots = defaultTimeSlots.orEmpty(),
            coverUrl = coverUrl,
            newArtistDiscountPct = newArtistDiscountPct ?: 0,
            serviceTags = serviceTags.orEmpty(),
            coverGradientIndex = ArtistGradient.clampIndex(coverGradientIndex),
        )
    }
}

@Serializable
internal data class DbPackage(
    val id: String,
    @SerialName("artist_id") val artistId: String,
    val name: String,
    @SerialName("duration_label") val durationLabel: String,
    @SerialName("price_inr") val priceInr: Int,
    val includes: List<String> = emptyList(),
    val popular: Boolean = false,
) {
    fun toPackage() = ArtistPackage(
        id = id,
        name = name,
        duration = durationLabel,
        price = priceInr,
        includes = includes,
        popular = popular,
    )
}

@Serializable
internal data class DbTechItem(
    @SerialName("artist_id") val artistId: String,
    val item: String,
)

@Serializable
internal data class DbSample(
    val id: String,
    @SerialName("artist_id") val artistId: String,
    val title: String,
    @SerialName("duration_label") val durationLabel: String = "",
) {
    fun toSample() = Sample(id = id, title = title, duration = durationLabel)
}

@Serializable
private data class PublishedPatch(val published: Boolean)

// One DTO per narrow edit. `encodeDefaults` is off by default in kotlinx, but
// these carry no defaults anyway — every field here is one the caller meant to
// send, which is what keeps a PATCH a PATCH.
@Serializable
internal data class BioPatch(val bio: String?)

@Serializable
internal data class CoverGradientPatch(
    @SerialName("cover_gradient_index") val coverGradientIndex: Int,
)

/**
 * A percentage is a percentage; clamped so a caller bug cannot store 900% off.
 * Matches the CHECK constraints migration 0073 puts on both pct columns — the
 * server would reject an out-of-range value, and a rejected PATCH surfaces to the
 * artist as "couldn't save" with no hint that the number was the problem.
 */
private const val MAX_PCT = 100

@Serializable
internal data class NewArtistDiscountPatch(
    @SerialName("new_artist_discount_pct") val newArtistDiscountPct: Int,
)

@Serializable
internal data class ServiceTagsPatch(
    @SerialName("service_tags") val serviceTags: List<String>,
)

@Serializable
internal data class SocialLinksPatch(
    @SerialName("instagram_handle") val instagramHandle: String?,
    @SerialName("spotify_artist_url") val spotifyArtistUrl: String?,
    @SerialName("youtube_channel_url") val youtubeChannelUrl: String?,
)

@Serializable
private data class AvailabilityPatch(
    @SerialName("days_available") val daysAvailable: List<String>,
    @SerialName("default_time_slots") val defaultTimeSlots: List<String>,
)

@Serializable
private data class AvailabilityRow(
    @SerialName("days_available") val daysAvailable: List<String>? = null,
    @SerialName("default_time_slots") val defaultTimeSlots: List<String>? = null,
)

@Serializable
private data class WizardPublishRow(
    val id: String,
    val handle: String,
    @SerialName("stage_name") val stageName: String,
    val category: String,
    @SerialName("base_city") val baseCity: String,
    val genre: String? = null,
    val bio: String? = null,
    @SerialName("cover_gradient_index") val coverGradientIndex: Int = 0,
    @SerialName("days_available") val daysAvailable: List<String> = emptyList(),
    @SerialName("default_time_slots") val defaultTimeSlots: List<String> = emptyList(),
    @SerialName("instagram_handle") val instagramHandle: String? = null,
    @SerialName("spotify_artist_url") val spotifyArtistUrl: String? = null,
    @SerialName("youtube_channel_url") val youtubeChannelUrl: String? = null,
    @SerialName("setup_complete") val setupComplete: Boolean,
)

@Serializable
internal data class DbArtistCover(
    @SerialName("artist_id") val artistId: String,
    @SerialName("storage_path") val storagePath: String,
    val position: Int = 0,
)
