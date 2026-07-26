package `in`.artistant.app.feature.profile

/**
 * Which Profile stat opened the list — port of iOS `ArtistListKind`.
 * Raw value doubles as the nav-arg / a11y id suffix.
 */
enum class ArtistListKind(val raw: String) {
    Bookings("bookings"),
    Saved("saved"),
    Completed("completed");

    val title: String
        get() = when (this) {
            Bookings -> "Bookings"
            Saved -> "Saved"
            Completed -> "Completed"
        }

    val headline: String
        get() = when (this) {
            Bookings -> "Your matches"
            Saved -> "Saved artists"
            Completed -> "Completed"
        }

    val sub: String
        get() = when (this) {
            Bookings -> "Artists you've booked or are negotiating with."
            Saved -> "Hearts from Discover — tap any row to reopen their profile."
            Completed -> "Shows you've already hosted — reopen the artist anytime."
        }

    val footer: String
        get() = when (this) {
            Bookings -> "SORTED BY EVENT DATE"
            Saved -> "MOST RECENT FIRST"
            Completed -> "ALL EVENTS DELIVERED"
        }

    fun kicker(count: Int): String = when (this) {
        Bookings -> "$count BOOKING${if (count == 1) "" else "S"}"
        Saved -> "$count ARTIST${if (count == 1) "" else "S"}"
        Completed -> "$count PAST SHOW${if (count == 1) "" else "S"}"
    }

    val emptyTitle: String
        get() = when (this) {
            Bookings -> "No bookings yet"
            Saved -> "Nothing saved yet"
            Completed -> "No past shows"
        }

    val emptySub: String
        get() = when (this) {
            Bookings -> "Book an artist from Discover and it'll show up here."
            Saved -> "Tap the heart on any artist's profile to save them here."
            Completed -> "Once a show wraps, it moves here as history."
        }

    companion object {
        fun fromRaw(raw: String): ArtistListKind =
            entries.firstOrNull { it.raw.equals(raw, ignoreCase = true) } ?: Saved
    }
}
