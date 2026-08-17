package `in`.artistant.app.data.repository

import `in`.artistant.app.testsupport.CLIENT_ID
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the signup profile write actually puts on the wire.
 *
 * The bug this pins is invisible in the Kotlin: it lives in the gap between the
 * DTO the code builds and the JSON kotlinx emits from it. supabase-kt's default
 * serializer encodes with plain `Json.Default`, and `SupabaseClientFactory.create()`
 * installs nothing that changes it, so two kotlinx defaults decide the wire shape:
 * `encodeDefaults` is false, so a property holding its declared default is dropped,
 * and `explicitNulls` is true, so a nullable property WITHOUT a default is sent as
 * `null`.
 *
 * PostgREST upserts as `INSERT … ON CONFLICT (id) DO UPDATE SET <keys present>`,
 * which makes those two cases opposites: an omitted key keeps what is stored, an
 * explicit null erases it.
 */
class UserUpsertWireShapeTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun encode(row: UserUpsertRow): JsonObject =
        json.parseToJsonElement(json.encodeToString(row)) as JsonObject

    private fun row(termsAcceptedAt: String? = null) = UserUpsertRow(
        id = CLIENT_ID,
        handle = "yash_01",
        fullName = "Yash Kumar",
        city = "Bangalore",
        role = "client",
        termsAcceptedAt = termsAcceptedAt,
    )

    @Test
    fun `a save with no consent to assert omits the consent column entirely`() {
        // The failure this comes from: the gate re-enters the flow at the profile
        // step — after a process kill, or on the Degrade route for a COMPLETE user
        // whose profile fetch failed — where the terms checkbox is out of reach and
        // the flag reads false. Sending `"terms_accepted_at": null` there is an
        // `ON CONFLICT DO UPDATE SET terms_accepted_at = NULL`: an ordinary relaunch
        // erasing a legal consent the user really did give.
        val body = encode(row(termsAcceptedAt = null))

        assertFalse(
            "an omitted key keeps the stored timestamp; an explicit null erases it",
            "terms_accepted_at" in body,
        )
        // Everything the user just typed still has to overwrite what is stored.
        assertEquals(setOf("id", "handle", "full_name", "city", "role"), body.keys)
    }

    @Test
    fun `an accepted checkbox does send the stamp`() {
        val body = encode(row(termsAcceptedAt = "2026-08-17T10:00:00Z"))

        assertTrue("terms_accepted_at" in body)
        assertEquals(
            "2026-08-17T10:00:00Z",
            body.getValue("terms_accepted_at").jsonPrimitive.content,
        )
    }

    @Test
    fun `the profile columns are sent even when they match a plausible default`() {
        // No defaults on those five, on purpose: a blank city or a re-saved
        // identical handle must still be SET, not silently dropped the way the
        // wizard row's fields once were.
        val body = encode(
            UserUpsertRow(
                id = CLIENT_ID,
                handle = "",
                fullName = "",
                city = "",
                role = "client",
            ),
        )

        assertEquals(setOf("id", "handle", "full_name", "city", "role"), body.keys)
    }
}
