package `in`.artistant.app.data.repository

import `in`.artistant.app.testsupport.ARTIST_ID
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the two publish writes actually put on the wire.
 *
 * Both bugs these pin are invisible in the Kotlin: they live in the gap between
 * the DTO the code constructs and the JSON kotlinx emits from it.
 *
 * [json] is configured the way supabase-kt's default serializer is —
 * `Json { ignoreUnknownKeys = true }`, and `SupabaseClientFactory.create()`
 * installs nothing that changes it. The part that matters is what it leaves
 * alone: `encodeDefaults` stays false, so any field holding a value equal to its
 * declared default is dropped from the body. PostgREST upserts as
 * `ON CONFLICT (id) DO UPDATE SET <keys present>`, so a dropped key is not "write
 * the default", it is "keep whatever is stored".
 */
class ArtistPublishWireShapeTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun wizardRow(
        genre: String? = "Electronic",
        bio: String? = "Rooftop sets",
        coverGradientIndex: Int = 3,
        daysAvailable: List<String> = listOf("Fri", "Sat"),
        defaultTimeSlots: List<String> = listOf("9:00 PM"),
        instagramHandle: String? = "novabeats",
        spotifyArtistUrl: String? = "https://open.spotify.com/artist/x",
        youtubeChannelUrl: String? = "https://youtube.com/@nova",
    ) = WizardPublishRow(
        id = ARTIST_ID,
        handle = "nova",
        stageName = "Nova",
        category = "DJ",
        baseCity = "Bangalore",
        genre = genre,
        bio = bio,
        coverGradientIndex = coverGradientIndex,
        daysAvailable = daysAvailable,
        defaultTimeSlots = defaultTimeSlots,
        instagramHandle = instagramHandle,
        spotifyArtistUrl = spotifyArtistUrl,
        youtubeChannelUrl = youtubeChannelUrl,
    )

    private fun encode(row: WizardPublishRow): JsonObject =
        json.parseToJsonElement(json.encodeToString(row)) as JsonObject

    // --- The wizard row ------------------------------------------------------

    @Test
    fun aRepublishThatClearsAFieldStillSendsThatColumn() {
        // The failure this comes from: publish lands the row upsert, then dies at
        // the packages call. The artist retries after deleting their Instagram
        // handle and switching to palette 0. Both values now match what a
        // defaulted DTO would have declared, so both keys vanished from the body,
        // the upsert never SET them, and the live profile kept linking an
        // Instagram account the artist had just removed.
        val cleared = encode(
            wizardRow(
                genre = null,
                bio = null,
                coverGradientIndex = 0,
                daysAvailable = emptyList(),
                defaultTimeSlots = emptyList(),
                instagramHandle = null,
                spotifyArtistUrl = null,
                youtubeChannelUrl = null,
            ),
        )

        listOf(
            "genre",
            "bio",
            "cover_gradient_index",
            "days_available",
            "default_time_slots",
            "instagram_handle",
            "spotify_artist_url",
            "youtube_channel_url",
        ).forEach { column ->
            assertTrue("$column must be sent so the upsert overwrites it", column in cleared)
        }
        // Sent as an explicit null, which is what clears the column — not omitted,
        // which would leave the stored value alone.
        assertEquals(JsonNull, cleared["instagram_handle"])
        assertEquals("0", cleared.getValue("cover_gradient_index").jsonPrimitive.content)
    }

    @Test
    fun theRowCarriesEveryPublishableColumn_everyTime() {
        val keys = encode(wizardRow()).keys

        assertEquals(
            setOf(
                "id",
                "handle",
                "stage_name",
                "category",
                "base_city",
                "genre",
                "bio",
                "cover_gradient_index",
                "days_available",
                "default_time_slots",
                "instagram_handle",
                "spotify_artist_url",
                "youtube_channel_url",
            ),
            keys,
        )
    }

    // --- setup_complete ------------------------------------------------------

    @Test
    fun theWizardUpsertNeverClaimsOnboardingIsFinished() {
        // Publish is three non-atomic calls. Marking the artist finished in the
        // first one and live in the third means a drop in between leaves a row
        // past the wizard re-entry gate and invisible in Discover, with nothing
        // in the app able to flip `published` afterwards.
        assertFalse("setup_complete" in encode(wizardRow()))
    }

    @Test
    fun goLiveWritesPublishedAndSetupCompleteTogether() {
        val patch = json.parseToJsonElement(
            json.encodeToString(PublishedPatch(published = true, setupComplete = true)),
        ) as JsonObject

        assertEquals(setOf("published", "setup_complete"), patch.keys)
        assertEquals("true", patch.getValue("published").jsonPrimitive.content)
        assertEquals("true", patch.getValue("setup_complete").jsonPrimitive.content)
    }
}
