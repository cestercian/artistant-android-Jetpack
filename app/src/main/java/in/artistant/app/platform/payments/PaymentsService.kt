package `in`.artistant.app.platform.payments

import `in`.artistant.app.data.model.BookingDraft

/**
 * The single seam every booking-confirmation flow goes through (iOS
 * `PaymentsService` + `PaymentResult`). v1 is a no-payments matchmaker, so the
 * seam always resolves to [MockPaymentsService] — it synthesizes a held-escrow
 * result and lets the booking row write without collecting money. Real payments
 * are M7: add a concrete provider behind this same interface, flip the DI binding,
 * no screen changes.
 */
interface PaymentsService {
    /** Collect payment for [draft]. Returns on success; throws [PaymentException]. */
    suspend fun collectPayment(draft: BookingDraft): PaymentResult
}

/**
 * Outcome shape every payments backend returns — fields map to Razorpay's
 * order/payment object 1:1 so the M7 swap is a config flip:
 *   [orderId] → `bookings.razorpay_order_id`
 *   [paymentId] → `bookings.razorpay_payment_id` (webhook-populated in M7)
 *   [methodLabel] → user-facing method string
 */
data class PaymentResult(
    val orderId: String,
    val paymentId: String?,
    val methodLabel: String,
    val escrowState: PaymentEscrowState,
)

enum class PaymentEscrowState {
    /** Funds captured into hold. Default for mock + real; releases post-show. */
    Held,

    /** Escrow bypassed (testing / future flows). Not used in v1. */
    Bypassed,
}

/** Failure modes a payments backend can surface. */
sealed class PaymentException(message: String) : Exception(message) {
    data object UserCancelled : PaymentException("Payment cancelled by user")
    class ProviderError(msg: String) : PaymentException("Payment failed: $msg")
    data object MissingDraft : PaymentException("Internal error — no booking draft")
}
