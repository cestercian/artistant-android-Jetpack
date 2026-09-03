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
import `in`.artistant.app.data.model.ArtistPackage
import `in`.artistant.app.data.model.ArtistPrompt
import `in`.artistant.app.data.model.GalleryPhoto
import `in`.artistant.app.data.model.Sample
import `in`.artistant.app.designsystem.theme.ArtistGradient
import `in`.artistant.app.domain.artist.ArtistPrompts
import `in`.artistant.app.domain.artist.ServiceTags
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
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

    /**
     * Wizard Done — upsert the signed-in artist's profile row.
     *
     * Deliberately does NOT write `setup_complete`; that flag rides [setPublished],
     * two round-trips later. See [setPublished] for why.
     */
    suspend fun publishWizardProfile(draft: WizardProfileDraft)

    /**
     * Go-live: flip `published` so Discover can see the artist, and mark the wizard
     * finished in the SAME write (sync go-live; media is async).
     *
     * `setup_complete` lives here rather than in [publishWizardProfile]'s upsert
     * because publish is three non-atomic calls and the middle one can fail — a
     * dropped connection between round trips is the ordinary failure on mobile
     * data. Marking onboarding done in the first call would leave the server at
     * `setup_complete = true, published = false`: past the wizard re-entry gate
     * (RootViewModel routes on `artistSetupComplete`), invisible in Discover, and
     * with no control anywhere in the app that can flip `published`. Written
     * together, "finished" can never outrun "live". Ported from iOS
     * `ArtistsRepository.publish(_:)`.
     */
    suspend fun setPublished(artistId: String, published: Boolean = true)

    /** Own availability columns — ManageAvailability + wizard seed. */
    suspend fun fetchSelfAvailability(): AvailabilityDraft?

    /**
     * Replace the signed-in artist's availability columns (days + preferred start
     * times), then drop the cache entry.
     *
     * Guarded like the narrow self-row edits below: the write target still comes
     * from the session, but the caller names the account the edit was COMPOSED
     * for so a save composed under one artist can never PATCH another's row. The
     * hazard is the same detached-write one `patchSelf` documents, on the column
     * pair `ManageAvailability` owns. `ManageAvailability` seeds from
     * [fetchSelfAvailability], which returns the columns WITHOUT the id, so the
     * caller captures the id from the session at the moment of that read rather
     * than re-reading it here at write time.
     *
     * @param expectedOwner the account this edit was composed for, not whoever is
     *   signed in when it runs.
     */
    suspend fun updateAvailability(
        expectedOwner: String,
        daysAvailable: List<String>,
        timeSlots: List<String>,
    )

    // ── Narrow self-row edits (the press-kit editor) ─────────────────────────
    //
    // Each of these PATCHes exactly the columns it names, filtered to the
    // signed-in artist. That narrowness is the whole point: the only writer these
    // columns had was [publishWizardProfile], which upserts the WHOLE row and
    // therefore overwrites every profile column it carries — with nulls where the
    // wizard collected nothing. Editing a bio through that path would have
    // silently cleared the artist's social links and cover choice on the way past,
    // so the editor rendered those sections read-only instead. A PATCH per concern
    // is what makes them editable without that collateral, and it is the same
    // shape [updateAvailability] already uses.
    //
    // Every one of them names the account the edit was composed FOR, and the
    // implementation refuses the write unless the signed-in user IS that account.
    // The target used to be resolved from the session at EXECUTION time and
    // compared to nothing, which is only the same thing while the two cannot
    // drift — and they do drift: the press-kit editor finishes its owed saves from
    // a scope that deliberately outlives the screen (`EpkViewModel.onCleared`), so
    // a save composed under one artist can execute after somebody else has signed
    // in on the same device and land on THEIR public row. RLS cannot see that —
    // the JWT is the new user's and the row is theirs, so the write is legitimate
    // by every rule the server has. Carrying the identity down is what turns
    // "whoever is signed in" into a checkable claim, with the same `require`
    // [publishWizardProfile] and [setPublished] already carry.
    //
    // It only earns its keep if callers pass the identity the DRAFT belongs to —
    // the hydrated row's id, captured when the edit was composed. Passing
    // `session.currentUserId` read at call time would compare the session against
    // itself and always pass, which is a guard in shape only.

    /**
     * `artists.bio`. Blank persists as NULL — an empty bio is absent, not "".
     *
     * @param expectedOwner the account this edit was composed for, not whoever is
     *   signed in when it runs.
     */
    suspend fun updateBio(expectedOwner: String, bio: String)

    /**
     * `artists.cover_gradient_index`. Clamped to the palette range before it goes.
     *
     * @param expectedOwner the account this edit was composed for, not whoever is
     *   signed in when it runs.
     */
    suspend fun updateCoverGradient(expectedOwner: String, index: Int)

    /**
     * `artists.new_artist_discount_pct`, clamped to 0–100.
     *
     * This column had a reader (the public profile prints "New-artist offer: N%
     * off your booking") and, before this, no writer anywhere in the app — not
     * even the wizard. On a backend shared with another client that DOES set it,
     * that left an artist with a discount advertised on their own profile and no
     * way to withdraw it.
     *
     * @param expectedOwner the account this edit was composed for, not whoever is
     *   signed in when it runs.
     */
    suspend fun updateNewArtistDiscount(expectedOwner: String, pct: Int)

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
     *
     * @param expectedOwner the account this edit was composed for, not whoever is
     *   signed in when it runs.
     */
    suspend fun updateServiceTags(expectedOwner: String, tags: List<String>)

    /**
     * `artists.weekend_premium_pct`, clamped to 0–100 to match the column's CHECK.
     *
     * Deliberately a sibling of [updateNewArtistDiscount] rather than one combined
     * "pricing extras" call: they are toggled from two independent controls, and a
     * combined write would make changing one send the other's value too — the
     * whole-set hazard, reintroduced for two fields that never needed it.
     *
     * @param expectedOwner the account this edit was composed for, not whoever is
     *   signed in when it runs.
     */
    suspend fun updateWeekendPremium(expectedOwner: String, pct: Int)

    /**
     * `artists.prompts` — the whole deck, every time.
     *
     * Whole-set like [updateServiceTags], and gated the same way: the array
     * replaces what is stored, so an un-hydrated caller would erase every answer
     * the artist wrote on another device.
     *
     * @param expectedOwner the account this edit was composed for, not whoever is
     *   signed in when it runs.
     */
    suspend fun updatePrompts(expectedOwner: String, prompts: List<ArtistPrompt>)

    /**
     * The three social columns, all three every time.
     *
     * Whole-set semantics on purpose: the caller has to pass the current values of
     * the two it is not editing, so an un-hydrated caller cannot send two nulls and
     * silently unlink them. See the editor's save path for the guard that enforces
     * it has read them first.
     *
     * @param expectedOwner the account this edit was composed for, not whoever is
     *   signed in when it runs.
     */
    suspend fun updateSocialLinks(
        expectedOwner: String,
        instagram: String?,
        spotify: String?,
        youtube: String?,
    )
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

    /**
     * Ids the server has already answered "no row" for.
     *
     * A confirmed miss is a fact worth keeping. [fetchArtist] short-circuits a
     * HIT on [hydratedIds], but without this a lookup that legitimately resolves
     * to nothing re-runs [fetchMany]'s whole five-table fan-out on every call —
     * and the Blocked-accounts screen does exactly that on purpose: a blocked
     * CLIENT id is never in `artists`, and its name hydration asks for every such
     * id again on every refresh.
     *
     * Cleared in precisely the two places a positive entry would be: [invalidate]
     * (the artist just wrote their own row) and [cache] (a tile projection
     * carrying the id disproves the miss).
     */
    private val missingIds = mutableSetOf<String>()
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
            // Seeing the row at all disproves an earlier miss — a search or
            // Discover page can carry an artist this repository once asked for
            // before they were published.
            missingIds.remove(id)
            byId[id] = a.copy(id = id)
            changed = true
        }
        if (changed) _cacheGeneration.value = _cacheGeneration.value + 1
    }

    override suspend fun fetchArtist(id: String): Artist? {
        val key = id.lowercase()
        if (key in hydratedIds) return byId[key]
        if (key in missingIds) return null
        val artist = fetchMany(listOf(key)).firstOrNull()
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
            // `setup_complete = true` unconditionally: it records that the wizard
            // was finished, and finishing it is not undone by later going dark.
            client.from("artists").update(PublishedPatch(published, setupComplete = true)) {
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

    override suspend fun updateAvailability(
        expectedOwner: String,
        daysAvailable: List<String>,
        timeSlots: List<String>,
    ) {
        val userId = client.auth.currentSessionOrNull()?.user?.id?.lowercase()
            ?: throw AppError.NotFoundOrUnauthorized
        require(userId == expectedOwner.lowercase()) {
            "Self-row edit must target the account it was composed for."
        }
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

    override suspend fun updateBio(expectedOwner: String, bio: String) =
        patchSelf(expectedOwner, BioPatch(bio.trim().ifBlank { null }))

    override suspend fun updateCoverGradient(expectedOwner: String, index: Int) =
        patchSelf(expectedOwner, CoverGradientPatch(ArtistGradient.clampIndex(index)))

    override suspend fun updateNewArtistDiscount(expectedOwner: String, pct: Int) =
        patchSelf(expectedOwner, NewArtistDiscountPatch(pct.coerceIn(0, MAX_PCT)))

    override suspend fun updateServiceTags(expectedOwner: String, tags: List<String>) =
        patchSelf(expectedOwner, ServiceTagsPatch(ServiceTags.normalize(tags)))

    override suspend fun updateWeekendPremium(expectedOwner: String, pct: Int) =
        patchSelf(expectedOwner, WeekendPremiumPatch(pct.coerceIn(0, MAX_PCT)))

    override suspend fun updatePrompts(expectedOwner: String, prompts: List<ArtistPrompt>) =
        patchSelf(expectedOwner, PromptsPatch(ArtistPrompts.encode(prompts)))

    override suspend fun updateSocialLinks(
        expectedOwner: String,
        instagram: String?,
        spotify: String?,
        youtube: String?,
    ) = patchSelf(
        expectedOwner,
        SocialLinksPatch(
            instagramHandle = instagram?.trim()?.ifBlank { null },
            spotifyArtistUrl = spotify?.trim()?.ifBlank { null },
            youtubeChannelUrl = youtube?.trim()?.ifBlank { null },
        ),
    )

    /**
     * One PATCH against the signed-in artist's own row, then drop the cache entry.
     *
     * [expectedOwner] is the account the patch was COMPOSED for, and the `require`
     * is what makes "self" a checked fact instead of an assumption. The session is
     * still where the write's target comes from — it has to be, since that is the
     * identity the JWT and RLS agree on — but a target nobody compared to anything
     * is only correct while composing and executing happen under one session.
     * Detached work breaks that: an owed press-kit save flushed from
     * `EpkViewModel.onCleared` after a sign-out/sign-in would PATCH the new user's
     * row with the previous artist's drafts, and the server would accept it as an
     * ordinary self-edit. Mirrors the guard [publishWizardProfile] and
     * [setPublished] already carry, error family included.
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
    private suspend inline fun <reified T : Any> patchSelf(expectedOwner: String, patch: T) {
        val userId = client.auth.currentSessionOrNull()?.user?.id?.lowercase()
            ?: throw AppError.NotFoundOrUnauthorized
        require(userId == expectedOwner.lowercase()) {
            "Self-row edit must target the account it was composed for."
        }
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
        // The wizard's first publish INSERTS the row, so this id may well be one
        // an earlier lookup recorded as absent. Dropping the miss alongside the
        // hydrated entry is what lets the artist's own profile load right after
        // going live.
        missingIds.remove(userId)
        _cacheGeneration.value = _cacheGeneration.value + 1
    }

    /**
     * The five-table profile fan-out, issued in PARALLEL.
     *
     * None of these reads depends on another's result — [stitch] is their only
     * consumer and it needs all five — so awaiting them one after another cost a
     * profile open five serial round trips (~1.5s on a 300ms mobile RTT) for work
     * that takes one. iOS fans the same five out with `async let`; in this repo
     * the idiom is `DiscoverViewModel.loadRails`.
     *
     * `coroutineScope` keeps the failure semantics identical to the sequential
     * version: the first read to throw cancels its siblings and propagates, so a
     * caller still sees one error rather than a half-stitched artist.
     */
    private suspend fun fetchMany(ids: List<String>): List<Artist> = coroutineScope {
        if (ids.isEmpty()) return@coroutineScope emptyList()
        val artists = async {
            client.from("artists")
                .select {
                    filter { isIn("id", ids) }
                }
                .decodeList<DbArtist>()
        }
        val packages = async {
            client.from("packages")
                .select {
                    filter { isIn("artist_id", ids) }
                    order("artist_id", Order.ASCENDING)
                    order("position", Order.ASCENDING)
                }
                .decodeList<DbPackage>()
        }
        val tech = async {
            client.from("tech_rider")
                .select {
                    filter { isIn("artist_id", ids) }
                    order("artist_id", Order.ASCENDING)
                    order("position", Order.ASCENDING)
                }
                .decodeList<DbTechItem>()
        }
        val samples = async {
            client.from("samples")
                .select {
                    filter { isIn("artist_id", ids) }
                    order("artist_id", Order.ASCENDING)
                    order("position", Order.ASCENDING)
                }
                .decodeList<DbSample>()
        }
        // Every photo, not just the cover: this read already returned all six of
        // them (the cap is 6/artist) and threw five away, and the About strip
        // wants exactly those five. Folding the gallery in here rather than
        // giving it its own fetch keeps a profile open at ONE round trip's worth
        // of artist_media, which is what `fetchMany`'s whole fan-out is for.
        val photos = async {
            client.from("artist_media")
                .select(ARTIST_PHOTO_COLUMNS) {
                    filter {
                        isIn("artist_id", ids)
                        eq("kind", "photo")
                    }
                    order("artist_id", Order.ASCENDING)
                    order("position", Order.ASCENDING)
                }
                .decodeList<DbArtistPhoto>()
        }
        stitch(artists.await(), packages.await(), tech.await(), samples.await(), photos.await())
    }

    companion object {
        /**
         * The columns every `artist_media` photo read asks for.
         *
         * One list rather than one per call site: both readers (this stitch and
         * the search cover batch) decode the same [DbArtistPhoto], and a DTO
         * whose fields are a superset of what some caller selected decodes to
         * defaults that are indistinguishable from real values — a row silently
         * claiming `aspect = square` because nobody asked for the column.
         */
        internal val ARTIST_PHOTO_COLUMNS: Columns = Columns.list(
            "id", "artist_id", "kind", "aspect", "storage_path", "position",
        )

        /** Public CDN URL for an `artist-media` storage path. */
        fun coverUrl(storagePath: String): String? {
            if (storagePath.isBlank()) return null
            val base = AppEnvironment.supabaseUrl.trimEnd('/')
            if (base.isBlank()) return null
            return "$base/storage/v1/object/public/artist-media/$storagePath"
        }

        /**
         * Split one artist's `artist_media` photo rows into the cover and the
         * gallery behind it.
         *
         * **The cover is the FIRST photo in position order, not the row at
         * position 0.** iOS keys its cover on `position == 0` exactly and lets
         * the gallery be `position > 0`, which on a sparse set (photo 0 deleted,
         * 1 and 2 left) shows no cover and puts every remaining photo in the
         * strip. Here the two halves are cut from one sorted list instead, so
         * the same set yields a cover and one gallery photo — and, more to the
         * point, so the cover can never ALSO appear in the strip below it. This
         * is already the app's convention: the press-kit editor badges
         * `photos.first()` as COVER and its "Make cover" action moves a photo to
         * index 0, not to position 0.
         *
         * [url] is a parameter so the split can be covered without a configured
         * Supabase project — [coverUrl] reads `BuildConfig` and returns null for
         * the blank URL a test JVM has, which would make every assertion here
         * pass for the wrong reason.
         */
        internal fun artistPhotos(
            rows: List<DbArtistPhoto>,
            url: (String) -> String? = { coverUrl(it) },
        ): ArtistPhotos {
            // `kind` is filtered server-side too, but this is where the rule
            // lives: a caller that forgets the filter should get an artist's
            // photos, never their showreel frame rendered as a photo.
            val photos = rows.filter { it.kind == PHOTO_KIND }.sortedBy { it.position }
            val gallery = photos.drop(1).mapNotNull { row ->
                val href = url(row.storagePath) ?: return@mapNotNull null
                GalleryPhoto(
                    id = row.id.lowercase(),
                    url = href,
                    // Lenient like `ArtistMediaRepository`'s own decode: an aspect
                    // this build has never heard of is a tile drawn square, not a
                    // profile that refuses to open.
                    aspect = runCatching { ArtistMediaAspect.valueOf(row.aspect) }
                        .getOrDefault(ArtistMediaAspect.square),
                )
            }
            return ArtistPhotos(
                coverUrl = photos.firstOrNull()?.let { url(it.storagePath) },
                gallery = gallery,
            )
        }

        internal fun stitch(
            artists: List<DbArtist>,
            packages: List<DbPackage>,
            tech: List<DbTechItem>,
            samples: List<DbSample>,
            photos: List<DbArtistPhoto>,
        ): List<Artist> {
            val packagesBy = packages.groupBy { it.artistId.lowercase() }
            val techBy = tech.groupBy { it.artistId.lowercase() }
            val samplesBy = samples.groupBy { it.artistId.lowercase() }
            val photosBy = photos.groupBy { it.artistId.lowercase() }
                .mapValues { (_, rows) -> artistPhotos(rows) }
            return artists.map { row ->
                val id = row.id.lowercase()
                val pkgs = packagesBy[id].orEmpty().map { it.toPackage() }
                val media = photosBy[id] ?: ArtistPhotos()
                row.toArtist(
                    packages = pkgs,
                    tech = techBy[id].orEmpty().map { it.item },
                    samples = samplesBy[id].orEmpty().map { it.toSample() },
                    coverUrl = media.coverUrl,
                    gallery = media.gallery,
                )
            }
        }
    }
}

/** `media_kind`'s photo member, as it arrives on the wire. */
internal const val PHOTO_KIND = "photo"

/** One artist's photos, already split into the cover and the strip behind it. */
internal data class ArtistPhotos(
    val coverUrl: String? = null,
    val gallery: List<GalleryPhoto> = emptyList(),
)

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
    @SerialName("weekend_premium_pct") val weekendPremiumPct: Int? = null,
    /**
     * Raw jsonb, parsed by [ArtistPrompts.decode] rather than declared as a typed
     * list. The column has no shape constraint, so a malformed entry written by
     * another client would make a typed decode throw — and this DTO backs the
     * profile screen's ONLY read, so that would turn one bad prompt into an
     * artist page that refuses to open.
     */
    @SerialName("prompts") val prompts: JsonElement? = null,
) {
    fun toArtist(
        packages: List<ArtistPackage>,
        tech: List<String>,
        samples: List<Sample>,
        coverUrl: String?,
        gallery: List<GalleryPhoto> = emptyList(),
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
            gallery = gallery,
            newArtistDiscountPct = newArtistDiscountPct ?: 0,
            serviceTags = serviceTags.orEmpty(),
            // Clamped on READ as well as on write: the CHECK constraint bounds
            // what this app can store, but the row predates it and an out-of-range
            // value would render as "220% weekend premium" on a public profile.
            weekendPremiumPct = (weekendPremiumPct ?: 0).coerceIn(0, MAX_PCT),
            prompts = ArtistPrompts.decode(prompts),
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
    // The stitch selects every column, so this was arriving from the server and
    // being dropped at decode simply because the DTO didn't name it — which is
    // why the profile could list an artist's samples but never play one.
    @SerialName("audio_url") val audioUrl: String? = null,
) {
    fun toSample() = Sample(id = id, title = title, duration = durationLabel, audioUrl = audioUrl)
}

/** The go-live write. Both flags in one PATCH — see [ArtistsRepository.setPublished]. */
@Serializable
internal data class PublishedPatch(
    val published: Boolean,
    @SerialName("setup_complete") val setupComplete: Boolean,
)

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
internal data class WeekendPremiumPatch(
    @SerialName("weekend_premium_pct") val weekendPremiumPct: Int,
)

/**
 * The prompt deck as raw JSON.
 *
 * `JsonArray` rather than a typed list so exactly one place — [ArtistPrompts] —
 * owns the `{q,a}` wire keys, and encode stays the literal inverse of the decode
 * on the read side.
 */
@Serializable
internal data class PromptsPatch(
    @SerialName("prompts") val prompts: JsonArray,
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

/**
 * The wizard's whole-row write. **Not one default on it, deliberately.**
 *
 * kotlinx encodes with `encodeDefaults = false` and the Supabase client installs
 * no serializer that changes that, so a field whose value happens to equal its
 * declared default is dropped from the body entirely. PostgREST's upsert is
 * `INSERT … ON CONFLICT (id) DO UPDATE SET <keys present>`, so a dropped key
 * keeps whatever the row already held. With defaults on, clearing a social link
 * or picking palette 0 on a re-publish sent nothing for those columns, and the
 * live profile went on linking the deleted Instagram account and rendering the
 * old palette — the artist's edit silently did not happen. Every field required
 * means every column is always sent, which is the whole-row overwrite
 * [ArtistsRepository.publishWizardProfile] is documented to perform.
 *
 * `setup_complete` is absent on purpose — it rides [ArtistsRepository.setPublished].
 */
@Serializable
internal data class WizardPublishRow(
    val id: String,
    val handle: String,
    @SerialName("stage_name") val stageName: String,
    val category: String,
    @SerialName("base_city") val baseCity: String,
    val genre: String?,
    val bio: String?,
    @SerialName("cover_gradient_index") val coverGradientIndex: Int,
    @SerialName("days_available") val daysAvailable: List<String>,
    @SerialName("default_time_slots") val defaultTimeSlots: List<String>,
    @SerialName("instagram_handle") val instagramHandle: String?,
    @SerialName("spotify_artist_url") val spotifyArtistUrl: String?,
    @SerialName("youtube_channel_url") val youtubeChannelUrl: String?,
)

/**
 * One `artist_media` row, as both photo readers need it — the profile stitch and
 * the search cover batch. Select it with
 * [SupabaseArtistsRepository.ARTIST_PHOTO_COLUMNS] so no field here is a default
 * standing in for a column nobody asked for.
 */
@Serializable
internal data class DbArtistPhoto(
    val id: String,
    @SerialName("artist_id") val artistId: String,
    val kind: String,
    val aspect: String,
    @SerialName("storage_path") val storagePath: String,
    val position: Int = 0,
)
