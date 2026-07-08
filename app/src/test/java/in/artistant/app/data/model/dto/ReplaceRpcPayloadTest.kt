package `in`.artistant.app.data.model.dto

import `in`.artistant.app.data.model.ArtistPackage
import `in`.artistant.app.data.model.SampleInput
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The `replace_*` RPC payloads bind BY NAME server-side, so these assert the exact
 * field names + the shaping the iOS side does: lowercased target id, 0-based
 * positions, trimmed/empty-filtered tech items.
 */
class ReplaceRpcPayloadTest {

    private val json = Json { encodeDefaults = true }

    @Test
    fun `packages payload numbers positions from zero and lowercases the id`() {
        val params = replacePackagesParams(
            artistId = "AB-CD",
            packages = listOf(
                ArtistPackage("Acoustic", "45 min", 15000, includes = listOf("PA"), popular = true),
                ArtistPackage("Full band", "90 min", 40000, includes = emptyList()),
            ),
        )
        assertEquals("ab-cd", params.target_artist_id)
        assertEquals(listOf(0, 1), params.packages_json.map { it.position })
        assertEquals("Acoustic", params.packages_json[0].name)
        assertEquals("45 min", params.packages_json[0].duration_label)
        assertEquals(15000, params.packages_json[0].price_inr)
        assertTrue(params.packages_json[0].popular)
        assertEquals(emptyList<String>(), params.packages_json[1].includes)

        // Wire names must be the SQL arg names, not camelCase.
        val encoded = json.encodeToString(params)
        assertTrue("target_artist_id" in encoded)
        assertTrue("packages_json" in encoded)
        assertTrue("duration_label" in encoded)
        assertTrue("price_inr" in encoded)
    }

    @Test
    fun `tech rider payload trims and drops blank items`() {
        val params = replaceTechRiderParams(
            artistId = "A1",
            items = listOf("  Mic  ", "", "  ", "Monitor"),
        )
        assertEquals(listOf("Mic", "Monitor"), params.items)
        assertEquals("a1", params.target_artist_id)
    }

    @Test
    fun `samples payload carries positions plus nullable urls`() {
        val params = replaceSamplesParams(
            artistId = "A1",
            samples = listOf(
                SampleInput("Live cut", "3:20", audioUrl = "https://x/y.m4a"),
                SampleInput("Spotify", "2:10", spotifyTrackUrl = "https://open.spotify/x"),
            ),
        )
        assertEquals(listOf(0, 1), params.samples_json.map { it.position })
        assertEquals("https://x/y.m4a", params.samples_json[0].audio_url)
        assertEquals(null, params.samples_json[0].spotify_track_url)
        assertEquals("https://open.spotify/x", params.samples_json[1].spotify_track_url)
        assertEquals(null, params.samples_json[1].audio_url)

        val encoded = json.encodeToString(params)
        assertTrue("samples_json" in encoded)
        assertTrue("duration_label" in encoded)
    }
}
