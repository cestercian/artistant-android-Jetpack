package `in`.artistant.app.feature.messages

import `in`.artistant.app.data.model.Message
import `in`.artistant.app.data.model.MessageDelivery
import `in`.artistant.app.data.model.MessageKind
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Which day bucket a transcript separator falls in. */
enum class DaySeparator { Today, Yesterday, Earlier }

/**
 * Pure grouping rules for chat timestamps.
 *
 * Deliberately NOT "a time under every bubble": iOS ChatView stamps on two
 * boundaries only — a centered separator at each calendar-day change, and the
 * edges of a sender run (name+time above the first incoming bubble, a bare time
 * below the last outgoing one). There is no elapsed-time gap threshold anywhere
 * in that file, so there is none here either; a rapid back-and-forth stays clean
 * and a conversation resumed the next morning still gets its date.
 *
 * All of it is id-set based rather than index-based so the composable can stay a
 * `LazyColumn` keyed on message id — it only ever asks "is this row a boundary?".
 *
 * Formatting lives in the UI (the platform's locale- and 24-hour-aware
 * formatter), which is why this file returns decisions, not strings.
 */
object ChatTimestamps {

    /**
     * Which side of the transcript a row sits on. System notices are their own
     * slot: they have no sender, so they must never be attributed to the
     * counterparty, and they legitimately break a run in both directions.
     */
    private enum class Slot { Mine, Theirs, System }

    private fun slotOf(message: Message): Slot = when {
        message.kind == MessageKind.System -> Slot.System
        message.isMine -> Slot.Mine
        else -> Slot.Theirs
    }

    private fun dayOf(epochMs: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()

    /**
     * Ids of the first message of each calendar day, in [zone].
     *
     * Compares dates, not elapsed time — two messages twenty minutes apart across
     * midnight are different days, and a twelve-hour gap inside one day is not.
     * [messages] is assumed already sorted ascending by `sentAtEpochMs`, which is
     * what ChatViewModel's merge maintains.
     */
    fun dayStartIds(messages: List<Message>, zone: ZoneId = ZoneId.systemDefault()): Set<String> {
        val ids = LinkedHashSet<String>()
        var previousDay: LocalDate? = null
        for (message in messages) {
            val day = dayOf(message.sentAtEpochMs, zone)
            if (day != previousDay) ids += message.id
            previousDay = day
        }
        return ids
    }

    /**
     * Ids that begin a run of incoming messages — the rows that get the
     * "Name · Role · time" caption. Consecutive messages from the counterparty
     * share one caption rather than repeating the name on every bubble.
     */
    fun incomingRunStartIds(messages: List<Message>): Set<String> {
        val ids = LinkedHashSet<String>()
        var previous: Slot? = null
        for (message in messages) {
            val slot = slotOf(message)
            if (slot == Slot.Theirs && previous != Slot.Theirs) ids += message.id
            previous = slot
        }
        return ids
    }

    /**
     * Ids that END a run of the viewer's own messages — the rows that get the
     * trailing time caption underneath. Needs the look-ahead (rather than the
     * look-behind the incoming case uses) because the stamp belongs to the last
     * bubble of the burst, not the first.
     */
    fun outgoingRunEndIds(messages: List<Message>): Set<String> {
        val ids = LinkedHashSet<String>()
        messages.forEachIndexed { index, message ->
            if (slotOf(message) != Slot.Mine) return@forEachIndexed
            val next = messages.getOrNull(index + 1)
            if (next == null || slotOf(next) != Slot.Mine) ids += message.id
        }
        return ids
    }

    /**
     * The last of the viewer's own messages the counterparty has already read —
     * the single row that gets a "Read" caption under it.
     *
     * One caption, not a tick per bubble: reads are monotonic, so marking the
     * newest read message says everything the per-bubble version would, without
     * stamping a column of identical glyphs down the thread.
     *
     * Three things disqualify a row and each is deliberate. A `Sending` or
     * `Failed` bubble has no server row that could have been read. A system
     * notice is not the viewer's message. And with no receipt at all — receipts
     * off, nothing read yet, or a server predating the `thread_reads` table —
     * there is nothing to claim, so it returns null rather than guessing.
     */
    fun lastReadOwnMessageId(messages: List<Message>, counterpartLastReadAtMs: Long?): String? {
        val cutoff = counterpartLastReadAtMs ?: return null
        return messages.lastOrNull { message ->
            message.isMine &&
                message.kind != MessageKind.System &&
                message.delivery == MessageDelivery.Sent &&
                message.sentAtEpochMs <= cutoff
        }?.id
    }

    /**
     * How long until the local calendar day rolls over, from [nowMs] in [zone].
     *
     * The relative labels below are only correct for the day they were computed
     * on, so a screen left open has to be told when "today" stops being today.
     * This is that interval — the UI sleeps exactly this long rather than polling
     * the clock, so a thread open overnight costs one wake-up, not a loop.
     *
     * Uses `atStartOfDay(zone)` rather than adding 24h so a DST transition (or a
     * zone whose midnight doesn't exist on a given date) still lands on the real
     * first instant of the next day. Floored at 1ms: a backwards clock adjustment
     * could otherwise produce a non-positive delay, which would spin.
     */
    fun millisUntilNextDay(nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
        val nextMidnight = dayOf(nowMs, zone)
            .plusDays(1)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
        return (nextMidnight - nowMs).coerceAtLeast(1L)
    }

    /**
     * Which label a day separator should use. [nowMs] is injected rather than
     * read from the clock so the relative buckets are testable and so a long-lived
     * screen recomposes against a single consistent "now".
     */
    fun daySeparator(
        epochMs: Long,
        nowMs: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): DaySeparator {
        val day = dayOf(epochMs, zone)
        val today = dayOf(nowMs, zone)
        return when (day) {
            today -> DaySeparator.Today
            today.minusDays(1) -> DaySeparator.Yesterday
            else -> DaySeparator.Earlier
        }
    }
}
