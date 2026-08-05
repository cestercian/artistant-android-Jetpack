package `in`.artistant.app.feature.messages

import `in`.artistant.app.data.model.Message
import `in`.artistant.app.data.model.MessageKind
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Transcript time-stamping rules.
 *
 * iOS ChatView does NOT stamp every bubble and has no time-gap threshold — it
 * groups by calendar day (a centered separator) and by sender run (the incoming
 * run gets a "Name · Role · time" caption above it, the outgoing run gets a bare
 * time below it). These are the pure decisions behind that; the actual strings
 * are formatted in the composable with the platform's locale/24-hour-aware
 * formatter, which is why nothing here asserts on a rendered time.
 */
class ChatTimestampsTest {

    private val zone: ZoneId = ZoneId.of("Asia/Kolkata")

    /** Epoch millis for a wall-clock time in the fixture zone. */
    private fun at(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int = 0,
    ): Long = LocalDateTime.of(year, month, day, hour, minute)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()

    private fun msg(
        id: String,
        at: Long,
        mine: Boolean = false,
        kind: MessageKind = MessageKind.User,
    ) = Message(
        id = id,
        threadId = "t-1",
        senderId = if (mine) "me" else "them",
        body = "…",
        sentAtEpochMs = at,
        kind = kind,
        isMine = mine,
    )

    // --- day separators -----------------------------------------------------

    @Test
    fun theFirstMessageOfEachCalendarDayCarriesTheSeparator() {
        val messages = listOf(
            msg("a", at(2026, 8, 3, 9)),
            msg("b", at(2026, 8, 3, 22)),
            msg("c", at(2026, 8, 4, 1)),
            msg("d", at(2026, 8, 4, 12)),
        )

        assertEquals(setOf("a", "c"), ChatTimestamps.dayStartIds(messages, zone))
    }

    /**
     * 23:50 and 00:10 are twenty minutes apart but on different days, so the
     * boundary is the calendar date — not any elapsed-time threshold.
     */
    @Test
    fun aMidnightCrossingSplitsDaysEvenWhenTheMessagesAreMinutesApart() {
        val messages = listOf(
            msg("a", at(2026, 8, 3, 23, 50)),
            msg("b", at(2026, 8, 4, 0, 10)),
        )

        assertEquals(setOf("a", "b"), ChatTimestamps.dayStartIds(messages, zone))
    }

    @Test
    fun anEmptyTranscriptHasNoSeparators() {
        assertEquals(emptySet<String>(), ChatTimestamps.dayStartIds(emptyList(), zone))
    }

    @Test
    fun theSeparatorLabelIsRelativeForTodayAndYesterday() {
        val now = at(2026, 8, 4, 12)

        assertEquals(DaySeparator.Today, ChatTimestamps.daySeparator(at(2026, 8, 4, 1), now, zone))
        assertEquals(
            DaySeparator.Yesterday,
            ChatTimestamps.daySeparator(at(2026, 8, 3, 23), now, zone),
        )
        assertEquals(
            DaySeparator.Earlier,
            ChatTimestamps.daySeparator(at(2026, 8, 2, 23), now, zone),
        )
    }

    // --- incoming runs ------------------------------------------------------

    @Test
    fun onlyTheFirstBubbleOfAnIncomingRunIsCaptioned() {
        val messages = listOf(
            msg("t1", at(2026, 8, 4, 9)),
            msg("t2", at(2026, 8, 4, 9, 1)),
            msg("m1", at(2026, 8, 4, 9, 2), mine = true),
            msg("t3", at(2026, 8, 4, 9, 3)),
        )

        assertEquals(setOf("t1", "t3"), ChatTimestamps.incomingRunStartIds(messages))
    }

    @Test
    fun aTranscriptOfOnlyMyOwnMessagesHasNoIncomingCaptions() {
        val messages = listOf(
            msg("m1", at(2026, 8, 4, 9), mine = true),
            msg("m2", at(2026, 8, 4, 10), mine = true),
        )

        assertEquals(emptySet<String>(), ChatTimestamps.incomingRunStartIds(messages))
    }

    /**
     * A system notice has no sender, so it must never be captioned with the
     * counterparty's name — and it breaks the run, so the next incoming bubble
     * starts a fresh one.
     */
    @Test
    fun aSystemNoticeIsNeverCaptionedAndBreaksTheRun() {
        val messages = listOf(
            msg("t1", at(2026, 8, 4, 9)),
            msg("sys", at(2026, 8, 4, 9, 1), kind = MessageKind.System),
            msg("t2", at(2026, 8, 4, 9, 2)),
        )

        assertEquals(setOf("t1", "t2"), ChatTimestamps.incomingRunStartIds(messages))
    }

    // --- outgoing runs ------------------------------------------------------

    @Test
    fun onlyTheLastBubbleOfAnOutgoingRunIsStamped() {
        val messages = listOf(
            msg("m1", at(2026, 8, 4, 9), mine = true),
            msg("m2", at(2026, 8, 4, 9, 1), mine = true),
            msg("t1", at(2026, 8, 4, 9, 2)),
            msg("m3", at(2026, 8, 4, 9, 3), mine = true),
        )

        assertEquals(setOf("m2", "m3"), ChatTimestamps.outgoingRunEndIds(messages))
    }

    @Test
    fun theFinalOutgoingBubbleEndsItsRun() {
        val messages = listOf(
            msg("t1", at(2026, 8, 4, 9)),
            msg("m1", at(2026, 8, 4, 9, 1), mine = true),
        )

        assertEquals(setOf("m1"), ChatTimestamps.outgoingRunEndIds(messages))
    }

    @Test
    fun aSystemNoticeAlsoClosesAnOutgoingRun() {
        val messages = listOf(
            msg("m1", at(2026, 8, 4, 9), mine = true),
            msg("sys", at(2026, 8, 4, 9, 1), kind = MessageKind.System),
            msg("m2", at(2026, 8, 4, 9, 2), mine = true),
        )

        assertEquals(setOf("m1", "m2"), ChatTimestamps.outgoingRunEndIds(messages))
    }

    @Test
    fun anIncomingOnlyTranscriptHasNoOutgoingStamps() {
        val messages = listOf(msg("t1", at(2026, 8, 4, 9)), msg("t2", at(2026, 8, 4, 10)))

        assertEquals(emptySet<String>(), ChatTimestamps.outgoingRunEndIds(messages))
    }
}
