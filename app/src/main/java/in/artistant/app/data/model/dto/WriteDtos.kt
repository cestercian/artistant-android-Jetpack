package `in`.artistant.app.data.model.dto

import `in`.artistant.app.common.util.lowercaseUuid
import `in`.artistant.app.data.model.ArtistPackage
import `in`.artistant.app.data.model.SampleInput
import kotlinx.serialization.Serializable

/**
 * Wire payloads for the atomic `replace_*` RPCs (API_MAPPING §8) — ports of the
 * iOS `Params`/`*JSON` encodable structs in Packages/TechRider/SamplesRepository.
 *
 * PostgREST binds RPC args BY NAME, so the field names here (`target_artist_id`,
 * `packages_json`, …) must match the SQL function signatures exactly. Kept as
 * pure builder functions (no network) so the payload shaping — lowercased id,
 * 0-based positions, trimmed/empty-filtered items, empty `includes` default — is
 * unit-testable without a live Supabase.
 */

@Serializable
data class ReplacePackagesParams(
    val target_artist_id: String,
    val packages_json: List<PackageJson>,
) {
    @Serializable
    data class PackageJson(
        val position: Int,
        val name: String,
        val duration_label: String,
        val price_inr: Int,
        // DB column is NOT NULL text[]; iOS emits empty since its model has no
        // `includes`. We DO carry it (the read model has includes) but default
        // to empty so a package built without it still satisfies the constraint.
        val includes: List<String>,
        val popular: Boolean,
    )
}

fun replacePackagesParams(artistId: String, packages: List<ArtistPackage>) =
    ReplacePackagesParams(
        target_artist_id = artistId.lowercaseUuid(),
        packages_json = packages.mapIndexed { idx, p ->
            ReplacePackagesParams.PackageJson(
                position = idx,
                name = p.name,
                duration_label = p.duration,
                price_inr = p.price,
                includes = p.includes,
                popular = p.popular,
            )
        },
    )

@Serializable
data class ReplaceTechRiderParams(
    val target_artist_id: String,
    val items: List<String>,
)

fun replaceTechRiderParams(artistId: String, items: List<String>) =
    ReplaceTechRiderParams(
        target_artist_id = artistId.lowercaseUuid(),
        // Trim + drop empties client-side so the RPC's unnest() doesn't insert
        // blank rows (mirrors iOS TechRiderRepository.replaceAll).
        items = items.map { it.trim() }.filter { it.isNotEmpty() },
    )

@Serializable
data class ReplaceSamplesParams(
    val target_artist_id: String,
    val samples_json: List<SampleJson>,
) {
    @Serializable
    data class SampleJson(
        val position: Int,
        val title: String,
        val duration_label: String,
        val audio_url: String?,
        val spotify_track_url: String?,
        val cover_art_url: String?,
    )
}

fun replaceSamplesParams(artistId: String, samples: List<SampleInput>) =
    ReplaceSamplesParams(
        target_artist_id = artistId.lowercaseUuid(),
        samples_json = samples.mapIndexed { idx, s ->
            ReplaceSamplesParams.SampleJson(
                position = idx,
                title = s.title,
                duration_label = s.durationLabel,
                audio_url = s.audioUrl,
                spotify_track_url = s.spotifyTrackUrl,
                cover_art_url = s.coverArtUrl,
            )
        },
    )
