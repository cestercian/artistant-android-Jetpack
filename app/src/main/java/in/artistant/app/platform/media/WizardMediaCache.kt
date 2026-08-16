package `in`.artistant.app.platform.media

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
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
    private val root: File
        get() = File(context.cacheDir, "artist-wizard").also { it.mkdirs() }

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
     * Blocking (a content-provider query), so callers belong off the main thread —
     * the same contract [adoptPhoto] and [adoptAudio] already carry. A provider
     * that refuses the query or has no such column returns null rather than
     * throwing: a missing name is a fallback, not a failed import.
     */
    fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
            }
    }.getOrNull()?.takeIf { it.isNotBlank() }

    fun adoptPhoto(uri: Uri): PendingPhoto {
        val name = "photo-${UUID.randomUUID()}.jpg"
        val dest = File(root, name)
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Could not read photo")
        return PendingPhoto(name)
    }

    fun adoptAudio(uri: Uri, title: String, durationSeconds: Double = 0.0): PendingAudio {
        val ext = guessExt(uri) ?: "m4a"
        val name = "audio-${UUID.randomUUID()}.$ext"
        val dest = File(root, name)
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Could not read audio")
        return PendingAudio(name, title.ifBlank { "Sample" }, durationSeconds)
    }

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

    private fun guessExt(uri: Uri): String? {
        val type = context.contentResolver.getType(uri).orEmpty()
        return when {
            type.contains("mpeg") || type.contains("mp3") -> "mp3"
            type.contains("wav") -> "wav"
            type.contains("aac") -> "aac"
            type.contains("ogg") -> "ogg"
            type.contains("mp4") || type.contains("m4a") -> "m4a"
            else -> uri.lastPathSegment?.substringAfterLast('.', "")?.takeIf { it.length in 2..4 }
        }
    }
}
