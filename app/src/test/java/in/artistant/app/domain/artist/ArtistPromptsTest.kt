package `in`.artistant.app.domain.artist

import `in`.artistant.app.data.model.ArtistPrompt
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The `artists.prompts` jsonb encoding.
 *
 * The cluster that matters most here is the tolerant decode. The column has no
 * shape constraint, so anything the reference client, an admin or a backfill
 * writes has to be survivable — and this DTO backs the profile screen's only
 * read, which means a throwing decode would turn one malformed entry into an
 * artist page that will not open. Every "junk in" case below asserts a skipped
 * entry rather than an exception.
 */
class ArtistPromptsTest {

    private fun json(raw: String): JsonArray = Json.parseToJsonElement(raw) as JsonArray

    // ── Decode: the happy path ───────────────────────────────────────────────

    @Test
    fun decode_readsTheWireShape() {
        val out = ArtistPrompts.decode(json("""[{"q":"Your dream venue?","a":"Hampi, at dawn."}]"""))
        assertEquals(listOf(ArtistPrompt("Your dream venue?", "Hampi, at dawn.")), out)
    }

    @Test
    fun decode_preservesOrder() {
        val out = ArtistPrompts.decode(json("""[{"q":"one","a":"1"},{"q":"two","a":"2"}]"""))
        assertEquals(listOf("one", "two"), out.map { it.question })
    }

    @Test
    fun decode_trimsWhitespace() {
        val out = ArtistPrompts.decode(json("""[{"q":"  q  ","a":"  a  "}]"""))
        assertEquals(ArtistPrompt("q", "a"), out.single())
    }

    // ── Decode: everything that must NOT throw ───────────────────────────────

    @Test
    fun decode_ofNullIsEmpty() {
        // A pre-0073 server has no column at all.
        assertTrue(ArtistPrompts.decode(null).isEmpty())
    }

    @Test
    fun decode_ofANonArrayIsEmpty() {
        assertTrue(ArtistPrompts.decode(JsonPrimitive("nope")).isEmpty())
        assertTrue(ArtistPrompts.decode(JsonObject(emptyMap())).isEmpty())
    }

    @Test
    fun decode_skipsEntriesThatAreNotObjects() {
        val out = ArtistPrompts.decode(json("""["bare string", 7, {"q":"q","a":"a"}]"""))
        assertEquals(1, out.size)
    }

    @Test
    fun decode_skipsEntriesMissingEitherKey() {
        assertTrue(ArtistPrompts.decode(json("""[{"q":"q"}]""")).isEmpty())
        assertTrue(ArtistPrompts.decode(json("""[{"a":"a"}]""")).isEmpty())
        assertTrue(ArtistPrompts.decode(json("""[{}]""")).isEmpty())
    }

    @Test
    fun decode_skipsBlankQuestionsAndAnswers() {
        // An unanswered prompt is not a fact worth rendering, and a question-less
        // entry has nothing to render as.
        assertTrue(ArtistPrompts.decode(json("""[{"q":"q","a":"   "}]""")).isEmpty())
        assertTrue(ArtistPrompts.decode(json("""[{"q":"  ","a":"a"}]""")).isEmpty())
    }

    @Test
    fun decode_skipsNonStringValuesRatherThanThrowing() {
        // The exact case a typed DTO would have thrown on, taking the whole
        // artist fetch — and so the profile screen — down with it.
        val out = ArtistPrompts.decode(json("""[{"q":"q","a":42},{"q":"ok","a":"yes"}]"""))
        assertEquals(listOf(ArtistPrompt("ok", "yes")), out)
    }

    @Test
    fun decode_clampsAnOverlongStoredAnswer() {
        val long = "x".repeat(ArtistPrompts.MAX_ANSWER_LENGTH + 50)
        val out = ArtistPrompts.decode(
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("q", JsonPrimitive("q"))
                        put("a", JsonPrimitive(long))
                    },
                )
            },
        )
        assertEquals(ArtistPrompts.MAX_ANSWER_LENGTH, out.single().answer.length)
    }

    // ── Encode, and the round trip ───────────────────────────────────────────

    @Test
    fun encode_producesTheWireShape() {
        val out = ArtistPrompts.encode(listOf(ArtistPrompt("q", "a")))
        assertEquals("""[{"q":"q","a":"a"}]""", out.toString())
    }

    @Test
    fun encode_dropsUnansweredPrompts() {
        // "Unanswered" is absence, not an empty string — otherwise the profile
        // renders empty rows and the editor cannot tell the two apart.
        val out = ArtistPrompts.encode(
            listOf(ArtistPrompt("q", "   "), ArtistPrompt("kept", "yes")),
        )
        assertEquals(1, out.size)
    }

    @Test
    fun encodeThenDecode_isIdentityForCleanInput() {
        val deck = listOf(ArtistPrompt("one", "1"), ArtistPrompt("two", "2"))
        assertEquals(deck, ArtistPrompts.decode(ArtistPrompts.encode(deck)))
    }

    @Test
    fun decodeThenEncode_isStableForTheReferenceShape() {
        // Round-tripping through this client must not rewrite what the other one
        // stored — the two share the column.
        val raw = json("""[{"q":"Your dream venue?","a":"Hampi, at dawn."}]""")
        assertEquals(raw.toString(), ArtistPrompts.encode(ArtistPrompts.decode(raw)).toString())
    }

    // ── Upsert ───────────────────────────────────────────────────────────────

    @Test
    fun upsert_appendsANewAnswer() {
        val out = ArtistPrompts.upsert(emptyList(), "q", "a")
        assertEquals(listOf(ArtistPrompt("q", "a")), out)
    }

    @Test
    fun upsert_editsInPlaceSoOrderDoesNotJump() {
        // Fixing a typo must not move the answer to the bottom of the deck.
        val deck = listOf(ArtistPrompt("one", "1"), ArtistPrompt("two", "2"))
        val out = ArtistPrompts.upsert(deck, "one", "1st")
        assertEquals(listOf("one", "two"), out.map { it.question })
        assertEquals("1st", out.first().answer)
    }

    @Test
    fun upsert_clearingRemovesTheEntry() {
        val deck = listOf(ArtistPrompt("one", "1"), ArtistPrompt("two", "2"))
        assertEquals(listOf(ArtistPrompt("two", "2")), ArtistPrompts.upsert(deck, "one", "  "))
    }

    @Test
    fun upsert_clampsALongAnswer() {
        val out = ArtistPrompts.upsert(emptyList(), "q", "y".repeat(1_000))
        assertEquals(ArtistPrompts.MAX_ANSWER_LENGTH, out.single().answer.length)
    }

    @Test
    fun upsert_ignoresABlankQuestion() {
        assertTrue(ArtistPrompts.upsert(emptyList(), "   ", "a").isEmpty())
    }

    // ── answerFor / needsSave ────────────────────────────────────────────────

    @Test
    fun answerFor_returnsEmptyForAnUnansweredQuestion() {
        assertEquals("", ArtistPrompts.answerFor(emptyList(), "q"))
        assertEquals("a", ArtistPrompts.answerFor(listOf(ArtistPrompt("q", "a")), "q"))
    }

    @Test
    fun needsSave_isFalseWhenNothingChanged() {
        val deck = listOf(ArtistPrompt("q", "a"))
        assertTrue(!ArtistPrompts.needsSave(deck, deck))
    }

    @Test
    fun needsSave_ignoresDifferencesTheColumnWouldNotStore() {
        // The debounce fires on any keystroke, including the ones that type a
        // character and delete it — a whitespace-only difference is not a write.
        val saved = listOf(ArtistPrompt("q", "a"))
        val draft = listOf(ArtistPrompt("q", "a "), ArtistPrompt("blank", ""))
        assertTrue(!ArtistPrompts.needsSave(draft, saved))
    }

    @Test
    fun needsSave_isTrueForARealEdit() {
        assertTrue(
            ArtistPrompts.needsSave(
                listOf(ArtistPrompt("q", "b")),
                listOf(ArtistPrompt("q", "a")),
            ),
        )
    }

    @Test
    fun needsSave_isTrueWhenTheLastAnswerIsCleared() {
        // The un-publish case. Comparing sizes or "is the draft empty" would have
        // treated this as nothing to do and stranded the answer on the server.
        assertTrue(ArtistPrompts.needsSave(emptyList(), listOf(ArtistPrompt("q", "a"))))
    }

    // ── The deck ─────────────────────────────────────────────────────────────

    @Test
    fun questions_areUnique() {
        // The question string IS the prompt's identity on the wire; a duplicate
        // would make two editor fields fight over one stored answer.
        assertEquals(ArtistPrompts.questions.size, ArtistPrompts.questions.toSet().size)
    }
}
