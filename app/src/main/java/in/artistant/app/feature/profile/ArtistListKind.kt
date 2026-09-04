package `in`.artistant.app.feature.profile

/**
 * Which Profile stat opened the list — port of iOS `ArtistListKind`, re-cut for
 * screen 32.
 *
 * The design's note is the whole reason this enum exists: "Bookings, Saved and
 * Completed share this screen — the stat you tapped picks the rows." The three
 * are not three screens, they are one screen with three row sources, and the
 * chip rail at the top lets a reader move between them without going back to the
 * profile to tap a different number.
 *
 * Raw value doubles as the nav-arg and the a11y id suffix.
 */
enum class ArtistListKind(val raw: String) {
    Bookings("bookings"),
    Saved("saved"),
    Completed("completed");

    /** The header's title (screen 32 draws "Saved artists", not "Saved"). */
    val title: String
        get() = when (this) {
            Bookings -> "Bookings"
            Saved -> "Saved artists"
            Completed -> "Completed"
        }

    /** The short form the switcher chip carries — the header says the long one. */
    val chipLabel: String
        get() = when (this) {
            Bookings -> "Bookings"
            Saved -> "Saved"
            Completed -> "Completed"
        }

    /**
     * The header's subtitle: "12 acts".
     *
     * Counted, singular-aware, and named after what the rows actually are — a
     * booking is not an act, and a past show is neither.
     */
    fun countLabel(count: Int): String = when (this) {
        Bookings -> if (count == 1) "1 booking" else "$count bookings"
        Saved -> if (count == 1) "1 act" else "$count acts"
        Completed -> if (count == 1) "1 past show" else "$count past shows"
    }

    val emptyTitle: String
        get() = when (this) {
            Bookings -> "No bookings yet"
            Saved -> "Nothing saved yet"
            Completed -> "No past shows"
        }

    /**
     * The empty body.
     *
     * Saved's is the design's own copy (screen 112) and its second sentence is
     * doing the work the note describes — "the date-freed-up badge is the hook,
     * worth stating before there is anything in the list". It states what saving
     * an act BUYS you, so the empty screen is a reason to come back rather than a
     * report that you have not used a feature.
     */
    val emptyBody: String
        get() = when (this) {
            Bookings -> "Book an artist from Discover and it'll show up here."
            Saved -> "Tap the heart on any act to keep them here. " +
                "Saved acts show a badge when they free up your date."
            Completed -> "Once a show wraps, it moves here as history."
        }

    /** The empty state's one action — every empty state carries one. */
    val emptyAction: String get() = "Browse Discover"

    /** The headline over a failed load. Failure is not emptiness. */
    val failedTitle: String
        get() = when (this) {
            Bookings -> "Couldn't load your bookings"
            Saved -> "Couldn't load your saved acts"
            Completed -> "Couldn't load your past shows"
        }

    companion object {
        fun fromRaw(raw: String): ArtistListKind =
            entries.firstOrNull { it.raw.equals(raw, ignoreCase = true) } ?: Saved
    }
}
