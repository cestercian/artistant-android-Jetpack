package `in`.artistant.app.feature.booking

import `in`.artistant.app.common.util.formatInr
import `in`.artistant.app.data.model.Booking
import `in`.artistant.app.data.model.BookingStatus

/**
 * Screen 132's decisions, kept out of the Composable so they can be tested.
 *
 * The design's note is a legal one: **"a record, not a tax invoice"** — because
 * v1 takes no money, an Indian GST invoice is exactly what this document must
 * not claim to be. Everything here exists to keep that true.
 */

/** One line of the record: what it is, and the amount beside it. */
data class InvoiceLine(val label: String, val amount: String, val emphasis: Boolean = false)

/**
 * A short, quotable reference for a booking — "AR-3F9A2C".
 *
 * Derived from the row's own id rather than stored, because `bookings` has no
 * reference column and inventing one client-side would produce a number that
 * means nothing to the server. This is the leading hex of the UUID, uppercased,
 * the way a short git sha works: a human can read it over the phone and support
 * can resolve it back with a prefix match. Deterministic, so the same booking is
 * the same reference on every screen and every device.
 *
 * A blank id yields a blank reference. The screens treat that as "no reference to
 * show" rather than printing "AR-" with nothing after it.
 */
fun bookingReference(bookingId: String): String {
    val hex = bookingId.filter { it.isLetterOrDigit() }.take(REFERENCE_CHARS)
    return if (hex.isEmpty()) "" else "AR-${hex.uppercase()}"
}

private const val REFERENCE_CHARS = 6

/**
 * The money on the record, and only what is actually owed to whom.
 *
 * `bookings` persists `platform_fee_inr` and `gst_inr` — [BookingMath] computes
 * them on every create and the columns have been there since the first
 * migration — but **v1 collects none of it.** The host settles the artist fee
 * directly with the artist; Artistant is not a party to that payment. So the
 * record shows the fee, states the platform's own fee as the zero it is, and
 * totals to the fee.
 *
 * Printing the persisted platform fee and GST here would put two numbers on a
 * document that nobody has ever been charged, which is precisely the claim the
 * design's note says this document must not make.
 *
 * The design's "Travel — ₹0, within city" line is absent: there is no travel
 * column on the row, and a zero we cannot source is a number we made up.
 */
fun invoiceLines(booking: Booking): List<InvoiceLine> = listOf(
    InvoiceLine("Artist fee", formatInr(booking.fee)),
    InvoiceLine("Artistant fee", "₹0 — no fee in this version"),
    InvoiceLine("Total", formatInr(booking.fee), emphasis = true),
)

/** The booking's own terms, as the record restates them. */
fun invoiceBookingRows(booking: Booking, artistName: String): List<InvoiceLine> = buildList {
    val act = artistName.trim()
    if (act.isNotEmpty()) add(InvoiceLine("Act", act))
    booking.packageName?.trim()?.takeIf { it.isNotEmpty() }?.let { add(InvoiceLine("Package", it)) }
    val whenLine = listOf(booking.date, booking.time)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString(" · ")
    if (whenLine.isNotEmpty()) add(InvoiceLine("Date", whenLine))
    booking.venue.trim().takeIf { it.isNotEmpty() && !it.equals("TBD", ignoreCase = true) }
        ?.let { add(InvoiceLine("Venue", it)) }
}

/**
 * The record as plain text, for the share sheet.
 *
 * Text rather than a PDF, and the share label says so. A PDF of a document that
 * is careful NOT to be a tax invoice would be handed on as one — the format
 * carries an implication the copy then has to spend a paragraph undoing — and the
 * closing line is the same disclaimer the screen shows.
 */
fun invoiceShareText(booking: Booking, artistName: String, reference: String): String {
    val header = listOfNotNull(
        "Artistant — booking record",
        reference.takeIf { it.isNotBlank() }?.let { "#$it" },
    ).joinToString(" ")
    val terms = invoiceBookingRows(booking, artistName).map { "${it.label}: ${it.amount}" }
    val money = invoiceLines(booking).map { "${it.label}: ${it.amount}" }
    return (listOf(header) + terms + money + listOf(invoiceDisclaimer(booking.status)))
        .joinToString("\n")
}

/**
 * The paragraph this whole screen exists to carry, in the tense the row can
 * actually support.
 *
 * It used to open "Settled directly with the artist." on every booking — and a
 * client can reach this screen one tap after checkout, over a `pending_confirm`
 * row the artist has not even seen. That sentence asserted a completed payment
 * on a booking with no artist agreement behind it and no money moved.
 *
 * **Nothing in `bookings` records settlement.** There is an `escrow_status`
 * column, but v1 writes `held` on every create and no code path ever advances
 * it, so reading it here would dress a payments-era default up as a fact. Until
 * a column actually records "the host paid the artist", this document never
 * uses the past tense: the fee is always something *to be* settled, and what
 * varies is only whether there is an agreement to settle yet.
 *
 * The invariant every branch keeps: Artistant is not a party to the payment and
 * issues no tax invoice for it. That is the half the design's note ("a record,
 * not a tax invoice") exists for, and it is true in every state.
 */
fun invoiceDisclaimer(status: BookingStatus): String = when (status) {
    // Not accepted yet: there is no agreement to settle, so the sentence has to
    // start by saying so rather than by describing a payment.
    BookingStatus.PendingConfirm ->
        "Not confirmed yet — nothing is owed until the artist accepts. The fee is then to be " +
            "settled directly with them: Artistant is not a party to that payment and issues " +
            "no tax invoice for it."
    BookingStatus.Confirmed, BookingStatus.Completed ->
        "The fee is to be settled directly with the artist. Artistant is not a party to that " +
            "payment and issues no tax invoice for it — this is your record of what was agreed."
    BookingStatus.Cancelled ->
        "This booking was cancelled and nothing is owed. Artistant was never a party to the " +
            "payment and issues no tax invoice for it — this is your record of what was agreed."
    // Disputed and Unknown both mean "we cannot characterise this booking's
    // state", and neither may imply anything about money in either direction.
    BookingStatus.Disputed, BookingStatus.Unknown ->
        "Any fee is settled directly between you and the artist. Artistant is not a party to " +
            "that payment and issues no tax invoice for it — this is your record of what was " +
            "agreed."
}
