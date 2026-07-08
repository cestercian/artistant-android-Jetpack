package `in`.artistant.app.feature.wizard

import `in`.artistant.app.data.model.ArtistPackage
import `in`.artistant.app.data.repository.ArtistsRepository
import `in`.artistant.app.data.repository.PackagesRepository
import `in`.artistant.app.data.repository.TechRiderRepository
import `in`.artistant.app.platform.media.PendingAudioRef
import `in`.artistant.app.platform.media.PendingMediaRef
import `in`.artistant.app.platform.upload.MediaUploadEnqueuer
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** Every scalar field the Publish step writes to `public.artists` + its children. */
data class WizardPublishFields(
    val handle: String,
    val stageName: String,
    val category: String,
    val baseCity: String,
    val genre: String?,
    val bio: String?,
    val coverGradientIndex: Int,
    val daysAvailable: List<String>,
    val timeSlots: List<String>,
    val eventTypes: List<String>,
    val instagramHandle: String?,
    val spotifyArtistUrl: String?,
    val youtubeChannelUrl: String?,
    val packages: List<ArtistPackage>,
    val tech: List<String>,
)

/** The staged-but-not-yet-uploaded media the queue drains after the row lands. */
data class WizardPendingMedia(
    val coverPhoto: PendingMediaRef?,
    val coverVideo: PendingMediaRef?,
    val gallery: List<PendingMediaRef>,
    val samples: List<PendingAudioRef>,
)

/**
 * The Publish orchestration (port of iOS `ArtistPreviewStep.runPublish`), kept as a pure
 * suspend function so the ORDERING contract is unit-testable with fakes — no ViewModel, no
 * Compose, no Context.
 *
 * Ordering is load-bearing and mirrors iOS's post-bugfix flow:
 *   1. `savePublishedProfile` — a full `onConflict=id` upsert. This is what ENSURES the
 *      `artists` row exists (so artist_media/samples RLS `owns_artist(id)` passes) AND sets
 *      `setup_complete=true`. It runs FIRST, before any media, so the row is present before
 *      the queue starts uploading. (A separate `upsertSelfArtist` first would be redundant —
 *      this upsert already ensures the row.)
 *   2. packages + tech replace — small atomic RPCs, done in parallel, still BEFORE media so
 *      the profile is complete on the backend the moment it goes live.
 *   3. `publish(id)` — flip `published=true` synchronously (iOS "go-live-now"). Going live
 *      must NEVER depend on a background file transfer: the old deferred-publish-flag flow
 *      left artists invisible in Discover forever if any upload failed terminally.
 *   4. enqueue pending media LAST — cover (position 0), video, gallery (append), samples.
 *      These backfill async; the profile shows its gradient cover until the hero lands.
 *
 * Returns the artist id. Throws on the first synchronous failure (row/packages/tech/publish),
 * which the caller surfaces on the preview screen — the artist is never told they published
 * when the row write failed.
 */
suspend fun runWizardPublish(
    fields: WizardPublishFields,
    media: WizardPendingMedia,
    artists: ArtistsRepository,
    packages: PackagesRepository,
    tech: TechRiderRepository,
    enqueuer: MediaUploadEnqueuer,
): String {
    // 1. Row write (ensures row + setup_complete=true). Returns the artist id (= user id).
    val id = artists.savePublishedProfile(
        handle = fields.handle,
        stageName = fields.stageName,
        category = fields.category,
        baseCity = fields.baseCity,
        genre = fields.genre,
        bio = fields.bio,
        coverGradientIndex = fields.coverGradientIndex,
        daysAvailable = fields.daysAvailable,
        timeSlots = fields.timeSlots,
        eventTypes = fields.eventTypes,
        instagramHandle = fields.instagramHandle,
        spotifyArtistUrl = fields.spotifyArtistUrl,
        youtubeChannelUrl = fields.youtubeChannelUrl,
    )

    // 2. Packages + tech in parallel — both must land before we go live.
    coroutineScope {
        val p = async { packages.replaceAll(id, fields.packages) }
        val t = async { tech.replaceAll(id, fields.tech) }
        p.await(); t.await()
    }

    // 3. Go live synchronously (never gated on media).
    artists.publish(id)

    // 4. Enqueue media LAST. Cover photo pins position 0; gallery appends (null → next slot).
    media.coverPhoto?.let { enqueuer.enqueuePhoto(it, id, 0) }
    media.coverVideo?.let { enqueuer.enqueueVideo(it, id) }
    media.gallery.forEach { enqueuer.enqueuePhoto(it, id, null) }
    media.samples.forEach { enqueuer.enqueueAudioSample(it, id) }

    return id
}
