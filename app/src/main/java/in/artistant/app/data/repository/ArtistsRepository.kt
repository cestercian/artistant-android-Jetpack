package `in`.artistant.app.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import `in`.artistant.app.common.util.artistMediaPublicUrl
import `in`.artistant.app.common.util.lowercaseUuid
import `in`.artistant.app.core.result.AppError
import `in`.artistant.app.core.result.mapPostgrest
import `in`.artistant.app.data.model.Artist
import `in`.artistant.app.data.model.SelfArtistRow
import `in`.artistant.app.data.model.SelfAvailability
import `in`.artistant.app.data.model.dto.DBArtist
import `in`.artistant.app.data.model.dto.DBArtistCover
import `in`.artistant.app.data.model.dto.DBPackage
import `in`.artistant.app.data.model.dto.DBSample
import `in`.artistant.app.data.model.dto.DBTechItem
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads artist data from Supabase, fronted by an in-memory id-keyed cache (port of
 * the iOS `ArtistsRepository`). The cache is what lets the app PAGE the server
 * (search / Discover) instead of holding the whole `artists` table in memory:
 * by-id lookups ([find] / [ensureFull]) resolve a tapped result even when it was
 * only ever seen as a tile-level search projection.
 *
 * A "full" cache entry is a complete 5-table stitch (packages/tech/samples/cover +
 * stats); a "partial" is a search/Discover projection ([cache]). Each entry records
 * whether it's full, so [cache] never DOWNGRADES a full profile back to a partial
 * and [fetchArtist] can skip the network on a hit.
 */
interface ArtistsRepository {
    /** Sync find — the cached row (full OR partial) if known, else null. */
    fun find(id: String): Artist?

    /** Cached artists for a set of ids, in order, skipping unknowns. */
    fun cachedArtists(ids: List<String>): List<Artist>

    /** Merge tile-level projections into the cache. Never downgrades a full entry. */
    fun cache(partials: List<Artist>)

    /** The FULL artist for [id] (fetch+cache on miss). Null = unknown / RLS-hidden. */
    suspend fun fetchArtist(id: String): Artist?

    /** Non-throwing convenience over [fetchArtist] — swallows transport errors. */
    suspend fun ensureFull(id: String): Artist?

    /** Best-effort batched full hydrate; only fetches ids not already full. */
    suspend fun fetchArtists(ids: List<String>)

    /** Full refresh — all published artists + children; rebuilds the cache. */
    suspend fun refresh(): List<Artist>

    // --- M5a artist-authoring writes (signed-in artist's own row) -----------

    /**
     * Minimal upsert run on the wizard's cover step (`onConflict=id`) so the
     * `artists` row exists BEFORE any media upload — artist_media/samples RLS
     * requires `owns_artist(id)`, which needs the row present. Returns the id.
     * Throws [AppError.UniqueViolation] if the handle is taken.
     */
    suspend fun upsertSelfArtist(
        handle: String,
        stageName: String,
        category: String,
        baseCity: String,
        genre: String? = null,
        bio: String? = null,
        coverGradientIndex: Int = 0,
    ): String

    /** Full Publish-time upsert of every wizard field + `setup_complete=true`. */
    suspend fun savePublishedProfile(
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
    ): String

    /** The signed-in artist's own row for the Profile/EPK editor; null if none. */
    suspend fun fetchSelfArtistRow(): SelfArtistRow?

    /** Own availability (weekday prefs, preferred start times); empties if no row. */
    suspend fun fetchSelfAvailability(): SelfAvailability

    /** PATCH only availability (post-onboarding editor); invalidates the cache. */
    suspend fun updateAvailability(days: List<String>, times: List<String>)

    /** PATCH only `cover_gradient_index` (EPK gradient picker). */
    suspend fun updateCoverGradient(index: Int)

    /** Flip `published=true` — the LAST step of Publish, after media lands. */
    suspend fun publish(artistId: String)

    /** Drop [id] from the id-keyed cache so the next read re-hydrates. */
    fun invalidate(id: String)

    companion object {
        /** Public CDN URL for an `artist-media` object path, or null if empty. */
        fun coverUrl(storagePath: String): String? = artistMediaPublicUrl(storagePath)
    }
}

@Singleton
class SupabaseArtistsRepository @Inject constructor(
    private val client: SupabaseClient,
) : ArtistsRepository {

    // id → best-known entry: the Artist plus whether it's a FULL 5-table stitch or
    // a tile-level partial. ONE map, not two — so "never downgrade a full to a
    // partial" is a single atomic compute()/put on the key, not a check-then-put
    // race across two structures. ConcurrentHashMap so the sync `find` (UI thread)
    // reads safely while suspend fetches mutate.
    private data class Cached(val artist: Artist, val hydrated: Boolean)
    private val entries = ConcurrentHashMap<String, Cached>()

    override fun find(id: String): Artist? = entries[id.lowercaseUuid()]?.artist

    override fun cachedArtists(ids: List<String>): List<Artist> =
        ids.mapNotNull { entries[it.lowercaseUuid()]?.artist }

    override fun cache(partials: List<Artist>) {
        // Atomic per key: compute() runs the remap under the bin lock, so a partial
        // can NEVER overwrite a concurrently-hydrated full entry — the search-tap
        // race (SearchRepository.cache(page) landing while a tapped artist hydrates
        // via fetchArtist). Full always wins; absent/partial takes the new partial.
        for (a in partials) {
            val key = a.id.lowercaseUuid()
            entries.compute(key) { _, existing ->
                if (existing?.hydrated == true) existing else Cached(a, hydrated = false)
            }
        }
    }

    override suspend fun fetchArtist(id: String): Artist? {
        val key = id.lowercaseUuid()
        entries[key]?.let { if (it.hydrated) return it.artist }
        val artist = fetchMany(listOf(key)).firstOrNull() ?: return null
        indexFull(listOf(artist))
        return artist
    }

    override suspend fun ensureFull(id: String): Artist? {
        val key = id.lowercaseUuid()
        entries[key]?.let { if (it.hydrated) return it.artist }
        return try {
            fetchArtist(key)
        } catch (_: Throwable) {
            null  // caller treats null as not-found
        }
    }

    override suspend fun fetchArtists(ids: List<String>) {
        val missing = ids.map { it.lowercaseUuid() }.filter { entries[it]?.hydrated != true }
        if (missing.isEmpty()) return
        try {
            indexFull(fetchMany(missing))
        } catch (_: Throwable) {
            // Leave the cache untouched; caller shows whatever tiles it has.
        }
    }

    override suspend fun refresh(): List<Artist> {
        val fetched = fetchAll()
        indexFull(fetched)
        return fetched
    }

    // --- Writes ----------------------------------------------------------

    override suspend fun upsertSelfArtist(
        handle: String,
        stageName: String,
        category: String,
        baseCity: String,
        genre: String?,
        bio: String?,
        coverGradientIndex: Int,
    ): String {
        val userId = requireUserId()
        val row = MinimalUpsert(
            id = userId,
            handle = handle.lowercaseUuid().trim(),
            stage_name = stageName,
            category = category,
            base_city = baseCity,
            genre = genre,
            bio = bio,
            cover_gradient_index = coverGradientIndex,
        )
        runCatching {
            client.postgrest.from("artists").upsert(row) { onConflict = "id" }
        }.onFailure { throw mapPostgrest(it) } // 23505 on artists_handle_key → UniqueViolation
        return userId
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
    ): String {
        val userId = requireUserId()
        val row = FullUpsert(
            id = userId,
            handle = handle.lowercaseUuid().trim(),
            stage_name = stageName,
            category = category,
            base_city = baseCity,
            genre = genre,
            bio = bio,
            cover_gradient_index = coverGradientIndex,
            days_available = daysAvailable,
            default_time_slots = timeSlots,
            event_types = eventTypes,
            instagram_handle = instagramHandle,
            spotify_artist_url = spotifyArtistUrl,
            youtube_channel_url = youtubeChannelUrl,
            setup_complete = true,
        )
        runCatching {
            client.postgrest.from("artists").upsert(row) { onConflict = "id" }
        }.onFailure { throw mapPostgrest(it) }
        invalidate(userId)
        return userId
    }

    override suspend fun fetchSelfArtistRow(): SelfArtistRow? {
        val userId = client.auth.currentSessionOrNull()?.user?.id?.lowercaseUuid() ?: return null
        return client.postgrest.from("artists")
            .select(SELF_COLUMNS) {
                filter { eq("id", userId) }
                limit(1)
            }
            .decodeList<SelfRow>()
            .firstOrNull()
            ?.toDomain()
    }

    override suspend fun fetchSelfAvailability(): SelfAvailability {
        val userId = requireUserId()
        // List select (not single) so a missing row is `[]` not a thrown no-rows.
        val row = client.postgrest.from("artists")
            .select(Columns.list("days_available", "default_time_slots")) {
                filter { eq("id", userId) }
                limit(1)
            }
            .decodeList<AvailRow>()
            .firstOrNull()
        return SelfAvailability(row?.days_available ?: emptyList(), row?.default_time_slots ?: emptyList())
    }

    override suspend fun updateAvailability(days: List<String>, times: List<String>) {
        val userId = requireUserId()
        client.postgrest.from("artists")
            .update(AvailUpdate(days, times)) { filter { eq("id", userId) } }
        invalidate(userId)
    }

    override suspend fun updateCoverGradient(index: Int) {
        val userId = requireUserId()
        client.postgrest.from("artists")
            .update(GradientUpdate(index)) { filter { eq("id", userId) } }
        invalidate(userId)
    }

    override suspend fun publish(artistId: String) {
        client.postgrest.from("artists")
            .update(PublishUpdate(true)) { filter { eq("id", artistId.lowercaseUuid()) } }
        invalidate(artistId)
    }

    override fun invalidate(id: String) {
        entries.remove(id.lowercaseUuid())
    }

    private fun requireUserId(): String =
        client.auth.currentSessionOrNull()?.user?.id?.lowercaseUuid()
            ?: throw AppError.NotFoundOrUnauthorized

    @Serializable
    private data class MinimalUpsert(
        val id: String,
        val handle: String,
        val stage_name: String,
        val category: String,
        val base_city: String,
        val genre: String?,
        val bio: String?,
        val cover_gradient_index: Int,
    )

    @Serializable
    private data class FullUpsert(
        val id: String,
        val handle: String,
        val stage_name: String,
        val category: String,
        val base_city: String,
        val genre: String?,
        val bio: String?,
        val cover_gradient_index: Int,
        val days_available: List<String>,
        val default_time_slots: List<String>,
        val event_types: List<String>,
        val instagram_handle: String?,
        val spotify_artist_url: String?,
        val youtube_channel_url: String?,
        val setup_complete: Boolean,
    )

    @Serializable private data class AvailUpdate(val days_available: List<String>, val default_time_slots: List<String>)
    @Serializable private data class GradientUpdate(val cover_gradient_index: Int)
    @Serializable private data class PublishUpdate(val published: Boolean)
    @Serializable private data class AvailRow(val days_available: List<String>? = null, val default_time_slots: List<String>? = null)

    @Serializable
    private data class SelfRow(
        val stage_name: String,
        val handle: String,
        val category: String,
        val base_city: String,
        val genre: String? = null,
        val bio: String? = null,
        val cover_gradient_index: Int = 0,
        val published: Boolean = false,
        val setup_complete: Boolean = false,
        val instagram_handle: String? = null,
        val spotify_artist_url: String? = null,
        val youtube_channel_url: String? = null,
    ) {
        fun toDomain() = SelfArtistRow(
            stageName = stage_name,
            handle = handle,
            category = category,
            baseCity = base_city,
            genre = genre,
            bio = bio,
            coverGradientIndex = cover_gradient_index,
            published = published,
            setupComplete = setup_complete,
            instagramHandle = instagram_handle,
            spotifyArtistUrl = spotify_artist_url,
            youtubeChannelUrl = youtube_channel_url,
        )
    }

    // --- Internals -------------------------------------------------------

    /** Merge full artists into the cache, marked hydrated. A plain put — a full
     *  entry unconditionally wins, and put is atomic per key. */
    private fun indexFull(artists: List<Artist>) {
        for (a in artists) {
            entries[a.id.lowercaseUuid()] = Cached(a, hydrated = true)
        }
    }

    /** All published artists (score desc) + children, stitched. */
    private suspend fun fetchAll(): List<Artist> = coroutineScope {
        val artistsD = async {
            client.postgrest.from("artists").select(ARTIST_COLUMNS) {
                filter { eq("published", true) }
                order("score", Order.DESCENDING)
            }.decodeList<DBArtist>()
        }
        val packagesD = async { allPackages() }
        val techD = async { allTech() }
        val samplesD = async { allSamples() }
        val coversD = async { allCovers() }
        stitch(artistsD.await(), packagesD.await(), techD.await(), samplesD.await(), coversD.await())
    }

    /** Same 5-table fan-out as [fetchAll] scoped to a set of ids (by-id hydrate). */
    private suspend fun fetchMany(ids: List<String>): List<Artist> = coroutineScope {
        val artistsD = async {
            client.postgrest.from("artists").select(ARTIST_COLUMNS) {
                filter { isIn("id", ids) }
            }.decodeList<DBArtist>()
        }
        val packagesD = async { packagesFor(ids) }
        val techD = async { techFor(ids) }
        val samplesD = async { samplesFor(ids) }
        val coversD = async { coversFor(ids) }
        stitch(artistsD.await(), packagesD.await(), techD.await(), samplesD.await(), coversD.await())
    }

    // Child-row queries. Split full-table vs by-id variants to keep each query's
    // filter explicit; all are ordered (artist_id, position) so per-artist groups
    // stay in position order and rows.first is position-0.

    private suspend fun allPackages() =
        client.postgrest.from("packages").select(PACKAGE_COLUMNS) {
            order("artist_id", Order.ASCENDING); order("position", Order.ASCENDING)
        }.decodeList<DBPackage>()

    private suspend fun packagesFor(ids: List<String>) =
        client.postgrest.from("packages").select(PACKAGE_COLUMNS) {
            filter { isIn("artist_id", ids) }
            order("artist_id", Order.ASCENDING); order("position", Order.ASCENDING)
        }.decodeList<DBPackage>()

    private suspend fun allTech() =
        client.postgrest.from("tech_rider").select(TECH_COLUMNS) {
            order("artist_id", Order.ASCENDING); order("position", Order.ASCENDING)
        }.decodeList<DBTechItem>()

    private suspend fun techFor(ids: List<String>) =
        client.postgrest.from("tech_rider").select(TECH_COLUMNS) {
            filter { isIn("artist_id", ids) }
            order("artist_id", Order.ASCENDING); order("position", Order.ASCENDING)
        }.decodeList<DBTechItem>()

    private suspend fun allSamples() =
        client.postgrest.from("samples").select(SAMPLE_COLUMNS) {
            order("artist_id", Order.ASCENDING); order("position", Order.ASCENDING)
        }.decodeList<DBSample>()

    private suspend fun samplesFor(ids: List<String>) =
        client.postgrest.from("samples").select(SAMPLE_COLUMNS) {
            filter { isIn("artist_id", ids) }
            order("artist_id", Order.ASCENDING); order("position", Order.ASCENDING)
        }.decodeList<DBSample>()

    private suspend fun allCovers() =
        client.postgrest.from("artist_media").select(COVER_COLUMNS) {
            filter { eq("kind", "photo") }
            order("artist_id", Order.ASCENDING); order("position", Order.ASCENDING)
        }.decodeList<DBArtistCover>()

    private suspend fun coversFor(ids: List<String>) =
        client.postgrest.from("artist_media").select(COVER_COLUMNS) {
            filter { eq("kind", "photo"); isIn("artist_id", ids) }
            order("artist_id", Order.ASCENDING); order("position", Order.ASCENDING)
        }.decodeList<DBArtistCover>()

    companion object {
        private val ARTIST_COLUMNS = Columns.list(
            "id", "handle", "stage_name", "category", "genre", "base_city", "bio",
            "cover_gradient_index", "followers_label", "streams_label", "response_label",
            "on_time_rate", "total_gigs", "rating", "score",
            "spotify_artist_url", "instagram_handle", "youtube_channel_url",
            "days_available", "default_time_slots",
        )
        private val PACKAGE_COLUMNS = Columns.list(
            "artist_id", "position", "name", "duration_label", "price_inr", "includes", "popular",
        )
        private val TECH_COLUMNS = Columns.list("artist_id", "item")
        private val SAMPLE_COLUMNS = Columns.list(
            "artist_id", "title", "duration_label", "spotify_track_url", "cover_art_url",
        )
        private val COVER_COLUMNS = Columns.list("artist_id", "storage_path", "position")
        private val SELF_COLUMNS = Columns.list(
            "stage_name", "handle", "category", "base_city", "genre", "bio",
            "cover_gradient_index", "published", "setup_complete",
            "instagram_handle", "spotify_artist_url", "youtube_channel_url",
        )

        /**
         * Shared child-row grouping + domain mapping. Groups children by artist,
         * and for covers keeps only the first (position-0) resolved URL — the
         * (artist_id, position) ordering guarantees rows.first is position-0.
         */
        internal fun stitch(
            artists: List<DBArtist>,
            packages: List<DBPackage>,
            tech: List<DBTechItem>,
            samples: List<DBSample>,
            covers: List<DBArtistCover>,
        ): List<Artist> {
            val packagesByArtist = packages.groupBy { it.artist_id }
            val techByArtist = tech.groupBy { it.artist_id }
            val samplesByArtist = samples.groupBy { it.artist_id }
            val coverByArtist = covers.groupBy { it.artist_id }
                .mapNotNull { (id, rows) ->
                    rows.firstOrNull()?.let { artistMediaPublicUrl(it.storage_path)?.let { url -> id to url } }
                }.toMap()

            return artists.map { row ->
                row.toArtist(
                    packages = packagesByArtist[row.id]?.map { it.toPackage() } ?: emptyList(),
                    tech = techByArtist[row.id]?.map { it.item } ?: emptyList(),
                    samples = samplesByArtist[row.id]?.map { it.toSample() } ?: emptyList(),
                    coverUrl = coverByArtist[row.id],
                )
            }
        }
    }
}
