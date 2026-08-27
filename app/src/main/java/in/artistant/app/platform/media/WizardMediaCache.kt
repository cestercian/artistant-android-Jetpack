package `in`.artistant.app.platform.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-disk staging for wizard / EPK pending media — port of iOS WizardMediaCache.
 * Files live under `cacheDir/artist-wizard/`; refs are filenames only so a process
 * restart can still find them. Gallery / SAF picks copy into this folder.
 */
@Singleton
class WizardMediaCache @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * Where staged files live. Resolving the path deliberately does NOT create
     * the directory: `mkdirs` is a syscall, and every `PendingPhoto.file(cache)`
     * lookup used to make one — including the ones on the main thread, where the
     * ViewModel resolves a preview path. Only the two write paths need the
     * directory to exist, and they ask for it by name.
     */
    private val root: File
        get() = File(context.cacheDir, "artist-wizard")

    private fun rootDir(): File = root.also { it.mkdirs() }

    data class PendingPhoto(val fileName: String) {
        fun file(cache: WizardMediaCache): File = File(cache.root, fileName)
    }

    data class PendingAudio(
        val fileName: String,
        val title: String,
        val durationSeconds: Double,
    ) {
        fun file(cache: WizardMediaCache): File = File(cache.root, fileName)
    }

    /**
     * The picked file's human name, or null when the provider won't say.
     *
     * `Uri.lastPathSegment` is NOT this. A `OpenDocument()` pick hands back a
     * document URI whose last segment is a provider-defined id — `audio:1000000042`
     * from the media documents provider, `primary:Music/song` from the storage one
     * — so using it as a sample title puts a provider's internal identifier on the
     * artist's public profile, with no rename anywhere in the EPK to undo it. Only
     * `OpenableColumns.DISPLAY_NAME` answers the question that was actually asked.
     *
     * Blocking (a content-provider query), so callers belong off the main thread.
     * [adoptPhoto] and [adoptAudio] make that hop for themselves because they
     * copy whole files; this is one cursor read and leaves the choice to the
     * caller. A provider that refuses the query or has no such column returns
     * null rather than throwing: a missing name is a fallback, not a failed
     * import.
     */
    fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
            }
    }.getOrNull()?.takeIf { it.isNotBlank() }

    /**
     * Copy a picked photo into the cache.
     *
     * Suspending, and on IO, because the copy is the whole file. Both call sites
     * launch on `viewModelScope`, which is `Main.immediate`, and a SAF pick backed
     * by Drive or Photos makes `openInputStream` fetch the bytes over the network
     * before the first one arrives — that is an ANR, not jank. The hop lives here
     * rather than at each call site so a fifth caller cannot forget it.
     */
    suspend fun adoptPhoto(uri: Uri): PendingPhoto = withContext(Dispatchers.IO) {
        val name = "photo-${UUID.randomUUID()}.jpg"
        copyInto(uri, File(rootDir(), name), "Could not read photo")
        PendingPhoto(name)
    }

    /**
     * Copy a picked clip into the cache, rejecting what the bucket will not take.
     *
     * `artist-samples` accepts exactly [ACCEPTED_AUDIO_MIME_TYPES] and caps
     * objects at [MAX_AUDIO_BYTES] (migration 0010). The picker used to offer
     * the wildcard audio MIME and nothing checked afterwards, so a WAV — the
     * format a recorded
     * live take arrives in — staged fine, sat in the samples list, published
     * fine, and then 400'd three times in the upload queue, which parks failures
     * in a set no screen reads. The artist saw "You're live" and the clip simply
     * never existed. Same for anything over 10 MiB, which `SamplesRepository`
     * additionally reads whole into memory.
     *
     * Rejecting at the pick turns a silent late failure into an immediate,
     * explainable one — the call iOS made too (`acceptedAudio` / `maxAudioBytes`
     * in WizardMediaCache.swift).
     *
     * The duration is measured here, off the staged copy. It used to be a
     * caller-supplied parameter defaulting to 0.0 that nothing on this platform
     * ever filled in, so every clip the app published wrote `0:00` into
     * `samples.duration_label` and rendered as "AUDIO CLIP" in the editor —
     * while the draft's own KDoc claimed it was "read from the media at pick
     * time". iOS measures at adoption (AVAudioPlayer); now so do we.
     */
    suspend fun adoptAudio(uri: Uri, title: String): PendingAudio =
        withContext(Dispatchers.IO) {
            val ext = stagedAudioExtension(context.contentResolver.getType(uri), uri.lastPathSegment)
                ?: error(UNSUPPORTED_AUDIO_MESSAGE)
            if ((declaredSize(uri) ?: 0L) > MAX_AUDIO_BYTES) error(OVERSIZE_AUDIO_MESSAGE)

            val name = "audio-${UUID.randomUUID()}.$ext"
            val dest = File(rootDir(), name)
            copyInto(uri, dest, "Could not read audio")
            // The provider's SIZE column is advisory — plenty of them answer null,
            // and a cloud document can answer for a different revision — so the
            // copy is measured too. Cheaper to check twice than to publish a
            // sample that only exists locally.
            if (dest.length() > MAX_AUDIO_BYTES) {
                dest.delete()
                error(OVERSIZE_AUDIO_MESSAGE)
            }
            PendingAudio(name, title.ifBlank { "Sample" }, measuredDurationSeconds(dest))
        }

    /**
     * The staged clip's length in seconds, or 0.0 when the container won't say.
     *
     * Read off the local copy rather than the source URI: the bytes are already
     * here, so there is no second cloud fetch and no permission to re-check.
     * Zero stays the honest "not measured" value the editor's duration label and
     * `SamplePlayback` both already know how to read.
     */
    private fun measuredDurationSeconds(file: File): Double {
        val retriever = MediaMetadataRetriever()
        val millis = runCatching {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        }.getOrNull()
        // release(), not use{}: MediaMetadataRetriever only became AutoCloseable
        // at API 29 and this module ships to 26.
        runCatching { retriever.release() }
        return millis?.takeIf { it > 0L }?.div(1000.0) ?: 0.0
    }

    /**
     * Stream [uri] into [dest], leaving nothing behind if it fails.
     *
     * A copy that dies half-way — provider crashed, cache full, cloud fetch timed
     * out — would otherwise leave a truncated file under a name the draft is
     * about to record, and the upload queue would later send those bytes as the
     * artist's cover.
     */
    private fun copyInto(uri: Uri, dest: File, notReadable: String) {
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: error(notReadable)
        } catch (t: Throwable) {
            dest.delete()
            throw t
        }
    }

    /** The provider's stated byte count, or null when it won't say. */
    private fun declaredSize(uri: Uri): Long? = runCatching {
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                val column = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (column >= 0 && cursor.moveToFirst() && !cursor.isNull(column)) {
                    cursor.getLong(column)
                } else {
                    null
                }
            }
    }.getOrNull()?.takeIf { it > 0L }

    /**
     * Whether a staged file is still there.
     *
     * Resume needs this: the draft records filenames, but this is `cacheDir` and
     * the OS may reclaim it at any time, so a recorded name is a claim to verify
     * rather than a fact. Blank names answer false instead of resolving to the
     * directory itself.
     */
    fun exists(fileName: String): Boolean =
        fileName.isNotBlank() && File(root, fileName).isFile

    /** Every staged file, for the resume sweep that deletes the unreferenced ones. */
    fun stagedFileNames(): List<String> =
        root.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()

    /**
     * Delete specific staged files.
     *
     * Separate from [clearAll] because the resume sweep must remove only the
     * orphans it identified — the files the restored draft still points at are
     * the artist's work in progress.
     */
    fun delete(fileNames: Collection<String>) {
        fileNames.forEach { name ->
            if (name.isNotBlank()) File(root, name).takeIf { it.isFile }?.delete()
        }
    }

    fun clearAll() {
        root.listFiles()?.forEach { it.delete() }
    }

    companion object {
        /**
         * The audio the `artist-samples` bucket accepts — its `allowed_mime_types`
         * verbatim (migration 0010) — and therefore exactly what the picker offers.
         *
         * One list, two uses, on purpose: a picker that offers a format the bucket
         * rejects is the whole bug. It must stay in sync with 0010 and with
         * `SamplesRepository.audioFormat`, which turns the staged extension back
         * into the `Content-Type` the upload declares.
         */
        val ACCEPTED_AUDIO_MIME_TYPES: List<String> = listOf(
            "audio/mpeg",
            "audio/mp4",
            "audio/aac",
            "audio/m4a",
            "audio/x-m4a",
        )

        /** The bucket's per-object cap — 10 MiB (migration 0010). */
        const val MAX_AUDIO_BYTES: Long = 10L * 1024 * 1024

        const val UNSUPPORTED_AUDIO_MESSAGE =
            "That format isn't supported — use an MP3, M4A or AAC clip."

        const val OVERSIZE_AUDIO_MESSAGE =
            "That clip is too large — keep samples under 10 MB (a two-minute MP3 is well under)."

        /**
         * Reported MIME → the extension we stage it as. Every value must map back
         * to an accepted MIME through `SamplesRepository.audioFormat`, which reads
         * the extension off the staged file when it sets `Content-Type`.
         */
        private val AUDIO_EXT_BY_MIME = mapOf(
            "audio/mpeg" to "mp3",
            // Not in the bucket's list, but real providers report it for an MP3 —
            // and the .mp3 extension maps back to audio/mpeg on the way out.
            "audio/mp3" to "mp3",
            "audio/mp4" to "m4a",
            "audio/m4a" to "m4a",
            "audio/x-m4a" to "m4a",
            "audio/aac" to "aac",
        )

        /** Extension → the same, for the picks whose MIME the provider won't state. */
        private val AUDIO_EXT_BY_EXT = mapOf(
            "mp3" to "mp3",
            "m4a" to "m4a",
            "aac" to "aac",
            // No extension at all. The picker's MIME filter already matched, so in
            // practice this is AAC/M4A — the same fallback iOS makes.
            "" to "m4a",
        )

        /**
         * What to stage a picked clip as, or null when the bucket would reject it.
         *
         * The reported MIME decides whenever there is one: it is the field the
         * picker filtered on and the field Storage checks. A stated-but-unaccepted
         * type is a rejection and never a reason to go looking at the extension —
         * that fallback is how `take-3.wav` would have been staged as `.m4a` and
         * uploaded as bytes no decoder can read, which is worse than the 400.
         */
        fun stagedAudioExtension(mimeType: String?, fileName: String?): String? {
            val mime = mimeType?.substringBefore(';')?.trim()?.lowercase()
            if (!mime.isNullOrEmpty()) return AUDIO_EXT_BY_MIME[mime]
            val ext = fileName?.substringAfterLast('.', "")?.trim()?.lowercase().orEmpty()
            return AUDIO_EXT_BY_EXT[ext]
        }
    }
}
