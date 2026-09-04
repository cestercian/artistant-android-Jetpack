package `in`.artistant.app.feature.booking

import `in`.artistant.app.data.model.GigRequest
import `in`.artistant.app.data.model.GigRequestStatus
import `in`.artistant.app.data.model.StoredRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Screen 61's validation and its reference line.
 *
 * The zero case is the one with teeth: `RequestsRepository.counter` flips the
 * request to `countered`, which takes it out of the artist's Accept/Decline
 * dock — so a ₹0 counter sent by clearing the number pad is an unrecoverable
 * state, reached by deleting six characters.
 */
class CounterOfferLogicTest {

    private fun request(
        amount: Int = 38_000,
        client: String? = "Rhea Menon",
        date: String = "Sat, Oct 12, 2026",
    ) = StoredRequest(
        raw = GigRequest(
            id = "r-1",
            client = client,
            message = "",
            date = date,
            amount = amount,
        ),
        status = GigRequestStatus.Open,
    )

    // --- the send gate -------------------------------------------------------

    @Test
    fun anEmptyFieldAndAZeroAreDifferentMistakes() {
        assertEquals("Enter your number.", counterAmountError(""))
        assertEquals("Enter your number.", counterAmountError("   "))
        assertEquals("A counter has to be above ₹0.", counterAmountError("0"))
    }

    @Test
    fun anOrdinaryNumberSends() {
        assertNull(counterAmountError("42000"))
        assertNull(counterAmountError("1"))
    }

    @Test
    fun somethingThatIsNotANumberIsTreatedAsUnanswered() {
        // The field filters to digits, so this only happens if another caller
        // writes through the ViewModel — and "enter your number" is still what
        // the artist has to do about it.
        assertEquals("Enter your number.", counterAmountError("abc"))
    }

    // --- the delta line ------------------------------------------------------

    @Test
    fun delta_saysHowFarAboveOrBelowTheirOfferYouAre() {
        assertEquals(
            "₹4,000 above their offer of ₹38,000",
            counterDeltaLine(theirOffer = 38_000, yours = 42_000),
        )
        assertEquals(
            "₹2,000 below their offer of ₹38,000",
            counterDeltaLine(theirOffer = 38_000, yours = 36_000),
        )
        assertEquals("The same as their offer", counterDeltaLine(38_000, 38_000))
    }

    @Test
    fun delta_isAbsentWhileThereIsNothingToCompare() {
        assertNull(counterDeltaLine(theirOffer = 38_000, yours = null))
        assertNull(counterDeltaLine(theirOffer = 38_000, yours = 0))
        assertNull(counterDeltaLine(theirOffer = 0, yours = 42_000))
    }

    // --- the reference line --------------------------------------------------

    @Test
    fun reference_namesTheRequesterAndTheDate() {
        assertEquals("Rhea Menon · Sat, Oct 12, 2026", counterReferenceLine(request()))
    }

    @Test
    fun reference_omitsANameNothingCanAnswer() {
        // `gig_requests.client_name` is null on rows written before mig 0100's
        // backfill, and printing a literal "Client" would state the same fact
        // about every requester.
        assertEquals("Sat, Oct 12, 2026", counterReferenceLine(request(client = null)))
        assertEquals("Rhea Menon", counterReferenceLine(request(client = "Rhea Menon", date = " ")))
    }

    @Test
    fun reference_isEmptyWithNoRequestAtAll() {
        assertEquals("", counterReferenceLine(null))
    }

    // --- the UI state's own derivations --------------------------------------

    @Test
    fun state_seedsTheDeltaAndTheGateOffTheFieldItIsShowing() {
        val state = CounterOfferUiState(request = request(amount = 38_000), amount = "42000")

        assertEquals(38_000, state.theirOffer)
        assertEquals("₹4,000 above their offer of ₹38,000", state.delta)
        assertEquals(true, state.canSend)
    }

    @Test
    fun state_cannotSendWhileOneIsAlreadyInFlight() {
        val state = CounterOfferUiState(
            request = request(),
            amount = "42000",
            isSending = true,
        )

        assertEquals(false, state.canSend)
    }
}
