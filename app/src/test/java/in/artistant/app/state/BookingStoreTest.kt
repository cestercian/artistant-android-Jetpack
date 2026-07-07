package `in`.artistant.app.state

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * Pure tests for [BookingStore.preferredInitialDate] — the iOS-parity draft-date
 * seed. `today` is injected so the scan is deterministic without the system clock.
 * 2026-01-05 is a Monday (Jan 1 2026 is a Thursday), which anchors every case.
 */
class BookingStoreTest {

    private val monday = LocalDate.of(2026, 1, 5) // Monday

    @Test fun `picks the next matching weekday within 30 days`() {
        // Sat-only artist, today is Monday → next Saturday is Jan 10 (offset 5).
        assertEquals(
            LocalDate.of(2026, 1, 10),
            BookingStore.preferredInitialDate(listOf("Sat"), monday),
        )
    }

    @Test fun `skips today when today's weekday is the only available one`() {
        // Mon-only artist, today is Monday → skips offset 0, next Monday is Jan 12.
        assertEquals(
            LocalDate.of(2026, 1, 12),
            BookingStore.preferredInitialDate(listOf("Mon"), monday),
        )
    }

    @Test fun `falls back to 14 days out when no weekday matches in 30 days`() {
        // A name that never matches exercises the loop's no-match fallback path.
        assertEquals(
            monday.plusDays(14),
            BookingStore.preferredInitialDate(listOf("Funday"), monday),
        )
    }

    @Test fun `falls back to 14 days out when daysAvailable is null or empty`() {
        assertEquals(monday.plusDays(14), BookingStore.preferredInitialDate(null, monday))
        assertEquals(monday.plusDays(14), BookingStore.preferredInitialDate(emptyList(), monday))
    }
}
