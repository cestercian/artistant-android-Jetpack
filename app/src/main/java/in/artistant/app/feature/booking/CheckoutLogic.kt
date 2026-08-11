package `in`.artistant.app.feature.booking

import `in`.artistant.app.data.model.BookingDraft

/**
 * Checkout's decisions, kept out of the Composable so they can be tested.
 *
 * There are only three of them, but each has already been got wrong once in
 * this product: what the review rows say when a field is empty, when the send
 * button is allowed to be dead, and what the user reads while the two-hop submit
 * is in flight.
 */

/** One collapsed review row: what was decided, and the value it was decided as. */
data class CheckoutReviewRow(val label: String, val value: String)

/**
 * The request as the client is about to send it.
 *
 * Empty fields render as words rather than as blanks — a blank row reads as a
 * rendering fault, and the client cannot tell whether they forgot the venue or
 * whether the screen lost it. Venue says "Not set" and directions say "None"
 * because those are the two states the booking form actually allows: both are
 * optional there, and neither blocks the send.
 */
fun checkoutReviewRows(draft: BookingDraft): List<CheckoutReviewRow> = listOf(
    CheckoutReviewRow("Package", draft.packageName.trim().ifEmpty { "Custom" }),
    CheckoutReviewRow("Date", draft.date),
    CheckoutReviewRow("Time", draft.time),
    CheckoutReviewRow("Venue", draft.venue.trim().ifEmpty { "Not set" }),
    CheckoutReviewRow("Guests", draft.guests.toString()),
    CheckoutReviewRow("Directions", draft.venueNotes.trim().ifEmpty { "None" }),
)

/**
 * May the client send this request?
 *
 * Deliberately narrow. An earlier over-eager guard also required a venue, which
 * silently disabled the CTA with no affordance anywhere in the funnel to satisfy
 * it — the whole booking flow dead-ended on a greyed button. Venue is optional by
 * design; "sendable with an empty venue" is accepted behaviour, not a bug.
 *
 * [artistHasNoPackages] is the one real block, and it is deliberately phrased as
 * a positive fact about the ARTIST rather than as "we couldn't resolve a
 * package": a failed artist fetch must not disable the button, because the draft
 * already carries a real fee and the request would be perfectly valid. Only an
 * artist we successfully loaded and who publishes no tiers gets blocked, and they
 * get pointed at the custom-quote path instead.
 */
fun checkoutBlocked(isSubmitting: Boolean, artistHasNoPackages: Boolean): Boolean =
    isSubmitting || artistHasNoPackages

/**
 * The two waits worth narrating on the submit path.
 *
 * The mock payment seam plus the booking write can together run past the couple
 * of seconds where a bare spinner stops reading as "working" and starts reading
 * as "stuck". Naming the step — and the artist — is what turns the wait into
 * progress.
 */
enum class CheckoutWaitPhase { Sending, Awaiting }

data class CheckoutWaitCopy(val title: String, val subtitle: String)

fun checkoutWaitCopy(phase: CheckoutWaitPhase, artistName: String): CheckoutWaitCopy {
    val who = artistName.trim().ifEmpty { "the artist" }
    return when (phase) {
        CheckoutWaitPhase.Sending -> CheckoutWaitCopy(
            title = "Sending your request to $who…",
            subtitle = "This usually takes a few seconds.",
        )
        CheckoutWaitPhase.Awaiting -> CheckoutWaitCopy(
            title = "Waiting for confirmation…",
            subtitle = "Locking in your match details.",
        )
    }
}

/**
 * The line above the CTA.
 *
 * Generic on purpose: no response-window rule exists on this path (the
 * `expires_at` in the schema belongs to the artist-side gig-request flow, not to
 * a booking), so promising "within 24 hours" would be inventing a guarantee
 * nothing enforces.
 */
const val CHECKOUT_EXPECTATION = "We'll notify you as soon as they respond."
