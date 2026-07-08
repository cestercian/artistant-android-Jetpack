package `in`.artistant.app.platform.upload

import `in`.artistant.app.platform.media.PendingAudioRef
import `in`.artistant.app.platform.media.PendingMediaRef
import java.util.UUID

/**
 * The enqueue seam the wizard's Publish step calls (implemented by [UploadQueue]). Extracted
 * as an interface for ONE reason: the publish-ordering unit test needs a fake to prove media
 * uploads are enqueued only AFTER the artist row + packages/tech land — and [UploadQueue] can't
 * be constructed off-device (it needs a Context + WorkManager). Nothing else is on it.
 */
interface MediaUploadEnqueuer {
    fun enqueuePhoto(ref: PendingMediaRef, artistId: String, position: Int?): UUID
    fun enqueueVideo(ref: PendingMediaRef, artistId: String): UUID
    fun enqueueAudioSample(ref: PendingAudioRef, artistId: String): UUID
}

/**
 * The read seam ArtistHome's banner observes (implemented by [UploadQueue]). Same
 * single reason as [MediaUploadEnqueuer]: [UploadQueue] can't be constructed
 * off-device (Context + WorkManager), so the ArtistHome ViewModel depends on this
 * interface and the unit test injects a fake emitting a canned [UploadBannerState].
 */
interface UploadBannerSource {
    fun bannerStateFlow(): kotlinx.coroutines.flow.Flow<UploadBannerState>
    /** Clear the stalled/finished batch (the banner's "Retry all"). */
    fun clearFinished()
}
