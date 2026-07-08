package `in`.artistant.app.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import `in`.artistant.app.common.util.lowercaseUuid
import `in`.artistant.app.common.util.storagePathFromPublicUrl
import `in`.artistant.app.common.util.storagePublicUrl
import `in`.artistant.app.core.result.AppError
import `in`.artistant.app.core.result.mapPostgrest
import `in`.artistant.app.data.model.SampleInput
import `in`.artistant.app.data.model.SampleRow
import `in`.artistant.app.data.model.dto.replaceSamplesParams
import kotlinx.serialization.Serializable
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Read + write for `public.samples` — the per-artist audio clips on the EPK
 * "Music" tab (port of iOS `SamplesRepository`). Audio bytes live in the public
 * `artist-samples` bucket at `<artistId>/<sampleId>.<ext>`; each row carries a
 * public-URL pointer + title + duration + position.
 *
 * Two write paths:
 *  - [upload] — single-sample append (what the upload queue drains, one task per
 *    sample). Uploads bytes, then inserts with a MAX(position)+1 lookup and a
 *    `23505` collision retry (two devices racing the same position), rolling the
 *    storage object back if the insert never lands.
 *  - [replaceAll] — atomic delete+insert via the `replace_samples` RPC for the
 *    future EPK bulk editor, plus best-effort storage cleanup of orphaned objects.
 */
interface SamplesRepository {
    suspend fun list(artistId: String): List<SampleRow>

    /** Append one uploaded sample. [ext]/[mime] preserve the source audio format. */
    suspend fun upload(
        audioBytes: ByteArray,
        ext: String,
        mime: String,
        title: String,
        durationSeconds: Double,
        artistId: String,
    ): SampleRow

    /** Atomic replace-all + orphan storage prune (EPK bulk editor). */
    suspend fun replaceAll(artistId: String, samples: List<SampleInput>)
}

@Singleton
class SupabaseSamplesRepository @Inject constructor(
    private val client: SupabaseClient,
) : SamplesRepository {

    override suspend fun list(artistId: String): List<SampleRow> =
        client.postgrest.from("samples")
            .select(SAMPLE_COLUMNS) {
                filter { eq("artist_id", artistId.lowercaseUuid()) }
                order("position", Order.ASCENDING)
            }
            .decodeList<DBSampleFull>()
            .map { it.toDomain() }

    override suspend fun upload(
        audioBytes: ByteArray,
        ext: String,
        mime: String,
        title: String,
        durationSeconds: Double,
        artistId: String,
    ): SampleRow {
        val artist = artistId.lowercaseUuid()
        val sampleId = UUID.randomUUID().toString().lowercaseUuid()
        // Path layout matches the storage RLS: first segment = artist uuid, so
        // `owns_artist(split_part(name,'/',1))` gates the write to their folder.
        val path = "$artist/$sampleId.$ext"

        client.storage.from(BUCKET).upload(path, audioBytes) {
            upsert = false
            contentType = ContentType.parse(mime)
        }
        val publicUrl = storagePublicUrl(BUCKET, path)

        // Insert with position retry; on ANY failure roll the uploaded object back
        // so a failed insert doesn't leak bytes into the artist's quota.
        return try {
            insertWithPositionRetry(
                nextPosition = { nextPosition(artist) },
                insert = { position ->
                    try {
                        client.postgrest.from("samples")
                            .insert(
                                SampleInsert(
                                    id = sampleId,
                                    artist_id = artist,
                                    position = position,
                                    title = title.trim(),
                                    duration_label = formatDuration(durationSeconds),
                                    audio_url = publicUrl,
                                ),
                            ) { select(SAMPLE_COLUMNS) }
                            .decodeSingle<DBSampleFull>()
                            .toDomain()
                    } catch (t: Throwable) {
                        throw mapPostgrest(t) // 23505 → UniqueViolation (retryable)
                    }
                },
            )
        } catch (t: Throwable) {
            runCatching { client.storage.from(BUCKET).delete(path) }
            throw t
        }
    }

    override suspend fun replaceAll(artistId: String, samples: List<SampleInput>) {
        val artist = artistId.lowercaseUuid()
        // Snapshot existing audio_urls BEFORE the RPC — afterwards the rows are
        // gone and we'd have no way to know which bucket objects to prune.
        val existingPaths = runCatching {
            client.postgrest.from("samples")
                .select(Columns.list("audio_url")) { filter { eq("artist_id", artist) } }
                .decodeList<AudioUrlOnly>()
                .mapNotNull { storagePathFromPublicUrl(BUCKET, it.audio_url) }
                .toSet()
        }.getOrDefault(emptySet())

        client.postgrest.rpc("replace_samples", replaceSamplesParams(artist, samples))

        // Prune objects that were on an old row but not in the new set. Best-effort
        // (a leaked object is a quota nit, not a correctness bug). Subtracting the
        // new set means a rename/re-order that keeps the same URLs touches nothing.
        val newPaths = samples.mapNotNull { storagePathFromPublicUrl(BUCKET, it.audioUrl) }.toSet()
        val orphans = (existingPaths - newPaths).toList()
        if (orphans.isNotEmpty()) runCatching { client.storage.from(BUCKET).delete(orphans) }
    }

    private suspend fun nextPosition(artistId: String): Int {
        val rows = client.postgrest.from("samples")
            .select(Columns.list("position")) {
                filter { eq("artist_id", artistId) }
                order("position", Order.DESCENDING)
                limit(1)
            }
            .decodeList<PositionOnly>()
        return (rows.firstOrNull()?.position ?: -1) + 1
    }

    @Serializable
    private data class SampleInsert(
        val id: String,
        val artist_id: String,
        val position: Int,
        val title: String,
        val duration_label: String,
        val audio_url: String?,
    )

    @Serializable private data class PositionOnly(val position: Int)
    @Serializable private data class AudioUrlOnly(val audio_url: String? = null)

    @Serializable
    private data class DBSampleFull(
        val id: String,
        val artist_id: String,
        val position: Int,
        val title: String,
        val duration_label: String,
        val audio_url: String? = null,
        val spotify_track_url: String? = null,
        val cover_art_url: String? = null,
    ) {
        fun toDomain() = SampleRow(
            id = id,
            artistId = artist_id,
            position = position,
            title = title,
            durationLabel = duration_label,
            audioUrl = audio_url,
            spotifyTrackUrl = spotify_track_url,
            coverArtUrl = cover_art_url,
        )
    }

    companion object {
        const val BUCKET = "artist-samples"
        private val SAMPLE_COLUMNS = Columns.list(
            "id", "artist_id", "position", "title", "duration_label",
            "audio_url", "spotify_track_url", "cover_art_url",
        )
    }
}

/**
 * Runs [insert] with a fresh [nextPosition] each attempt, retrying only on a
 * position collision ([AppError.UniqueViolation] = SQLSTATE 23505) so the slower
 * of two racing uploads still lands instead of failing the caller. Any other
 * error propagates immediately. Pure (no Supabase types) so it's unit-testable.
 * Port of the iOS `SamplesRepository.upload` retry loop.
 */
suspend fun <T> insertWithPositionRetry(
    maxRetries: Int = 5,
    nextPosition: suspend () -> Int,
    insert: suspend (Int) -> T,
): T {
    var lastError: Throwable? = null
    repeat(maxRetries) {
        val position = nextPosition()
        try {
            return insert(position)
        } catch (e: AppError.UniqueViolation) {
            lastError = e // another uploader claimed this position — retry fresh
        }
    }
    throw lastError ?: IllegalStateException("position retry exhausted")
}

/** `30s` / `1:24` — same shape as the wizard preview so the row reads identically. */
internal fun formatDuration(seconds: Double): String {
    val secs = Math.round(seconds).toInt()
    return if (secs < 60) "${secs}s" else "%d:%02d".format(secs / 60, secs % 60)
}

/**
 * In-memory twin — models the `UNIQUE(artist_id, position)` table: [upload]
 * appends at the next free position and [list] returns them position-ordered, so
 * a test can assert sequential position assignment across multiple uploads.
 */
class FakeSamplesRepository(
    seed: Map<String, List<SampleRow>> = emptyMap(),
) : SamplesRepository {
    private val store = seed.mapValues { it.value.toMutableList() }.toMutableMap()

    override suspend fun list(artistId: String): List<SampleRow> =
        store[artistId].orEmpty().sortedBy { it.position }

    override suspend fun upload(
        audioBytes: ByteArray,
        ext: String,
        mime: String,
        title: String,
        durationSeconds: Double,
        artistId: String,
    ): SampleRow {
        val list = store.getOrPut(artistId) { mutableListOf() }
        val position = (list.maxOfOrNull { it.position } ?: -1) + 1
        val row = SampleRow(
            id = UUID.randomUUID().toString(),
            artistId = artistId,
            position = position,
            title = title.trim(),
            durationLabel = formatDuration(durationSeconds),
            audioUrl = "fake://$artistId/$position.$ext",
            spotifyTrackUrl = null,
            coverArtUrl = null,
        )
        list.add(row)
        return row
    }

    override suspend fun replaceAll(artistId: String, samples: List<SampleInput>) {
        store[artistId] = samples.mapIndexed { idx, s ->
            SampleRow(
                id = "s-$idx",
                artistId = artistId,
                position = idx,
                title = s.title,
                durationLabel = s.durationLabel,
                audioUrl = s.audioUrl,
                spotifyTrackUrl = s.spotifyTrackUrl,
                coverArtUrl = s.coverArtUrl,
            )
        }.toMutableList()
    }
}
