package `in`.artistant.app.domain.artist

import `in`.artistant.app.data.model.ArtistPrompt
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The personality deck behind `artists.prompts` (jsonb, migration 0073).
 *
 * **Why this is a hand-written parse rather than a `@Serializable` DTO.** The
 * column is `jsonb` with no shape constraint — the database will accept any JSON
 * the reference client, an admin, or a future backfill puts there. If the artist
 * row declared `List<DbPrompt>` and one entry were malformed, kotlinx would throw
 * and take the **whole artist fetch** down with it: the profile screen's only
 * read is that stitch, so a single bad prompt would mean the artist's page
 * refuses to open. Parsing element-by-element and skipping what does not fit
 * degrades to a missing prompt instead, which is the proportionate failure.
 *
 * The wire shape is exactly `[{"q":…,"a":…}]`, and this file is the only place
 * that knows those two keys — [decode] and [encode] are each other's inverse, so
 * a value written here round-trips through the other client unchanged.
 */
object ArtistPrompts {

    private const val KEY_QUESTION = "q"
    private const val KEY_ANSWER = "a"

    /**
     * The deck, in ask order. Matches the reference client's four questions so an
     * artist who answered on one client sees their own words on the other — the
     * question string IS the identity of a prompt on the wire, so a reworded
     * question here would silently orphan an existing answer.
     */
    val questions: List<String> = listOf(
        "What's the strangest gig you've played?",
        "The song that never leaves your set?",
        "Your dream venue?",
        "What should a client know before booking you?",
    )

    /**
     * Answer ceiling. A prompt is a one-line piece of personality; the cap exists
     * so a paste truncates where the artist can see it rather than being accepted,
     * sent, and bounced by the column.
     */
    const val MAX_ANSWER_LENGTH: Int = 280

    /**
     * Tolerant read of whatever is in the column.
     *
     * Skips anything that is not an object with a non-blank `q` and a non-blank
     * `a`: an unanswered prompt is not a fact worth rendering, and a question-less
     * entry has nothing to render *as*. Order is preserved so the artist's own
     * arrangement survives a round-trip. Null / a non-array (the column is `not
     * null default '[]'`, but a client reading a pre-0073 server gets null) yields
     * an empty deck.
     */
    fun decode(element: JsonElement?): List<ArtistPrompt> {
        val array = element as? JsonArray ?: return emptyList()
        return array.mapNotNull { entry ->
            val obj = entry as? JsonObject ?: return@mapNotNull null
            val question = obj.stringOrNull(KEY_QUESTION)?.trim().orEmpty()
            val answer = obj.stringOrNull(KEY_ANSWER)?.trim().orEmpty()
            if (question.isEmpty() || answer.isEmpty()) return@mapNotNull null
            ArtistPrompt(question = question, answer = answer.take(MAX_ANSWER_LENGTH))
        }
    }

    /**
     * The array to PATCH back.
     *
     * Trims and drops blanks on the way out for the same reason [decode] does on
     * the way in: an entry with an empty answer is an unanswered prompt, and
     * storing those would have the profile render empty rows and the editor
     * unable to tell "never answered" from "answered with nothing".
     */
    fun encode(prompts: List<ArtistPrompt>): JsonArray = buildJsonArray {
        prompts.forEach { prompt ->
            val question = prompt.question.trim()
            val answer = prompt.answer.trim().take(MAX_ANSWER_LENGTH)
            if (question.isEmpty() || answer.isEmpty()) return@forEach
            add(
                buildJsonObject {
                    put(KEY_QUESTION, JsonPrimitive(question))
                    put(KEY_ANSWER, JsonPrimitive(answer))
                },
            )
        }
    }

    /** The stored answer for one question, or "" when it is unanswered. */
    fun answerFor(prompts: List<ArtistPrompt>, question: String): String =
        prompts.firstOrNull { it.question == question }?.answer.orEmpty()

    /**
     * Set or clear one answer, returning the new deck.
     *
     * Clearing REMOVES the entry rather than storing an empty string — the column
     * holds answered prompts, so "unanswered" is absence. Editing an existing
     * answer updates in place so the artist's ordering does not jump every time
     * they fix a typo; a new answer appends.
     */
    fun upsert(current: List<ArtistPrompt>, question: String, answer: String): List<ArtistPrompt> {
        val q = question.trim()
        val a = answer.trim().take(MAX_ANSWER_LENGTH)
        if (q.isEmpty()) return current
        if (a.isEmpty()) return current.filterNot { it.question == q }
        return if (current.any { it.question == q }) {
            current.map { if (it.question == q) it.copy(answer = a) else it }
        } else {
            current + ArtistPrompt(question = q, answer = a)
        }
    }

    /**
     * Clamp typing to what the column takes.
     *
     * Applied on input rather than on save so the artist sees the limit as they
     * hit it — the same reason the bio field clamps on the way in.
     */
    fun clampAnswerInput(value: String): String = value.take(MAX_ANSWER_LENGTH)

    /**
     * Has anything actually changed? Guards the debounced write against the
     * keystrokes that type a character and delete it again.
     */
    fun needsSave(draft: List<ArtistPrompt>, saved: List<ArtistPrompt>): Boolean =
        encode(draft) != encode(saved)

    /** `JsonObject["k"]` as a string, tolerating a non-string / absent value. */
    private fun JsonObject.stringOrNull(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.jsonPrimitive?.content
}
