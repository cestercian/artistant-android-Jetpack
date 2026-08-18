package `in`.artistant.app.data.repository

/**
 * Payload for the wizard Publish / Done write — mirrors iOS
 * `ArtistsRepository.savePublishedProfile` (artist row upsert; `setup_complete`
 * rides the go-live PATCH, not this one). Packages / tech go through their RPCs
 * in the same publish orchestration; cover / samples enqueue on UploadQueue
 * after go-live.
 */
data class WizardProfileDraft(
    val artistId: String,
    val stageName: String,
    val handle: String,
    val category: String,
    val baseCity: String,
    val genre: String,
    val bio: String,
    val coverGradientIndex: Int,
    val daysAvailable: List<String>,
    val timeSlots: List<String>,
    val instagramHandle: String?,
    val spotifyArtistUrl: String?,
    val youtubeChannelUrl: String?,
)
