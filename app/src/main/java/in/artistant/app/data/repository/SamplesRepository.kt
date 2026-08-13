package `in`.artistant.app.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import `in`.artistant.app.core.result.mapPostgrest
import `in`.artistant.app.core.result.AppError
import `in`.artistant.app.data.model.Sample
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * `public.samples` + `artist-samples` bucket — port of iOS SamplesRepository.
 * Wizard / UploadQueue use [upload] (append). EPK delete is targeted by id —
 * never [replaceAll] for single-row edits (orphan prune can kill unknown rows).
 */
interface SamplesRepository {
    suspend fun list(artistId: String): List<Sample>
    suspend fun upload(
        audioFile: File,
        title: String,
        durationSeconds: Double,
        artistId: String,
    ): Sample
    suspend fun delete(sampleId: String, storagePathOrUrl: String?)
}

@Singleton
class SupabaseSamplesRepository @Inject constructor(
    private val client: SupabaseClient,
) : SamplesRepository {

    override suspend fun list(artistId: String): List<Sample> =
        try {
            client.from("samples")
                .select {
                    filter { eq("artist_id", artistId.lowercase()) }
                    order("position", Order.ASCENDING)
                }
                .decodeList<SampleRow>()
                .map { it.toDomain() }
        } catch (t: Throwable) {
            throw mapPostgrest(t)
        }

    override suspend fun upload(
        audioFile: File,
        title: String,
        durationSeconds: Double,
        artistId: String,
    ): Sample {
        val id = UUID.randomUUID().toString().lowercase()
        val artist = artistId.lowercase()
        val (ext, mime) = audioFormat(audioFile)
        val path = "$artist/$id.$ext"
        val bytes = audioFile.readBytes()
        try {
            client.storage.from(BUCKET).upload(path, bytes) {
                contentType = ContentType.parse(mime)
                upsert = false
            }
        } catch (t: Throwable) {
            throw mapPostgrest(t)
        }
        val publicUrl = client.storage.from(BUCKET).publicUrl(path)
        // Position can race under concurrent uploads; retry on unique_violation.
        var lastError: Throwable? = null
        repeat(MAX_POSITION_RETRIES) {
            val position = nextPosition(artist)
            try {
                val row = client.from("samples")
                    .insert(
                        SampleInsert(
                            id = id,
                            artistId = artist,
                            position = position,
                            title = title.trim().ifBlank { "Sample" },
                            durationLabel = formatDuration(durationSeconds),
                            audioUrl = publicUrl,
                        ),
                    ) { select() }
                    .decodeSingle<SampleRow>()
                return row.toDomain()
            } catch (t: Throwable) {
                lastError = t
                val mapped = mapPostgrest(t)
                if (mapped !is AppError.UniqueViolation) {
                    runCatching { client.storage.from(BUCKET).delete(path) }
                    throw mapped
                }
            }
        }
        runCatching { client.storage.from(BUCKET).delete(path) }
        throw mapPostgrest(lastError ?: IllegalStateException("sample upload failed"))
    }

    override suspend fun delete(sampleId: String, storagePathOrUrl: String?) {
        try {
            client.from("samples").delete {
                filter { eq("id", sampleId.lowercase()) }
            }
        } catch (t: Throwable) {
            throw mapPostgrest(t)
        }
        val path = storagePathFromUrl(storagePathOrUrl) ?: return
        runCatching { client.storage.from(BUCKET).delete(path) }
    }

    private suspend fun nextPosition(artistId: String): Int {
        val rows = client.from("samples")
            .select(Columns.list("position")) {
                filter { eq("artist_id", artistId) }
                order("position", Order.DESCENDING)
                limit(1)
            }
            .decodeList<PositionRow>()
        return (rows.firstOrNull()?.position ?: -1) + 1
    }

    companion object {
        private const val BUCKET = "artist-samples"
        private const val MAX_POSITION_RETRIES = 5

        fun formatDuration(seconds: Double): String {
            val total = seconds.toInt().coerceAtLeast(0)
            val m = total / 60
            val s = total % 60
            return "%d:%02d".format(m, s)
        }

        fun audioFormat(file: File): Pair<String, String> {
            val ext = file.extension.lowercase().ifBlank { "m4a" }
            val mime = when (ext) {
                "mp3" -> "audio/mpeg"
                "wav" -> "audio/wav"
                "aac" -> "audio/aac"
                "ogg" -> "audio/ogg"
                else -> "audio/mp4"
            }
            return ext to mime
        }

        /** Extract bucket-relative path from a public URL, or return as-is if already a path. */
        fun storagePathFromUrl(urlOrPath: String?): String? {
            if (urlOrPath.isNullOrBlank()) return null
            val marker = "/object/public/$BUCKET/"
            val idx = urlOrPath.indexOf(marker)
            return if (idx >= 0) urlOrPath.substring(idx + marker.length) else urlOrPath
        }
    }
}

class FakeSamplesRepository : SamplesRepository {
    private val byArtist = mutableMapOf<String, MutableList<Sample>>()
    val uploadedFiles = mutableListOf<File>()

    override suspend fun list(artistId: String): List<Sample> =
        byArtist[artistId.lowercase()].orEmpty()

    override suspend fun upload(
        audioFile: File,
        title: String,
        durationSeconds: Double,
        artistId: String,
    ): Sample {
        uploadedFiles.add(audioFile)
        val sample = Sample(
            id = UUID.randomUUID().toString().lowercase(),
            title = title,
            duration = SupabaseSamplesRepository.formatDuration(durationSeconds),
        )
        byArtist.getOrPut(artistId.lowercase()) { mutableListOf() }.add(sample)
        return sample
    }

    override suspend fun delete(sampleId: String, storagePathOrUrl: String?) {
        byArtist.values.forEach { list -> list.removeAll { it.id == sampleId.lowercase() } }
    }
}

@Serializable
private data class SampleInsert(
    val id: String,
    @SerialName("artist_id") val artistId: String,
    val position: Int,
    val title: String,
    @SerialName("duration_label") val durationLabel: String,
    @SerialName("audio_url") val audioUrl: String,
)

@Serializable
private data class SampleRow(
    val id: String,
    @SerialName("artist_id") val artistId: String,
    val title: String,
    @SerialName("duration_label") val durationLabel: String = "",
    @SerialName("audio_url") val audioUrl: String? = null,
) {
    // audioUrl was already being fetched here and then discarded on the way to
    // the domain model, so the EPK knew where every clip lived and still had no
    // way to play one.
    fun toDomain() = Sample(id = id, title = title, duration = durationLabel, audioUrl = audioUrl)
}

@Serializable
private data class PositionRow(val position: Int)
