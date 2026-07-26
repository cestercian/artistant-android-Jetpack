package `in`.artistant.app.data.repository

/**
 * Payload for the wizard Publish / Done write — mirrors iOS
 * `ArtistsRepository.savePublishedProfile` (artist row upsert + setup_complete).
 * Packages / tech / samples table writes land in a later M5 pass.
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
