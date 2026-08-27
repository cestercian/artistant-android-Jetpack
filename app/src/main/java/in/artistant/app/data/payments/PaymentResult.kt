package `in`.artistant.app.data.payments

import `in`.artistant.app.data.model.BookingDraft
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dormant payment seam — port of iOS `PaymentResult` / `MockPaymentsService`.
 * v1 matchmaker still "collects" a mock receipt so create can stamp
 * razorpay_* columns; real Razorpay stays a one-line swap later.
 */

enum class PaymentEscrowState { Held, Bypassed }

data class PaymentResult(
    val orderId: String,
    val paymentId: String? = null,
    val methodLabel: String,
    val escrowState: PaymentEscrowState = PaymentEscrowState.Held,
)

interface PaymentsService {
    suspend fun collectPayment(draft: BookingDraft): PaymentResult
}

@Singleton
class MockPaymentsService @Inject constructor() : PaymentsService {
    /**
     * When true, throws after the latency beat — the fault seam that covers
     * checkout's untyped catch. Nothing else can reach it: the bookings fake
     * only throws `BookingRepositoryError`, which the typed branch above it
     * takes, so a payment failure is the one way in.
     */
    @Volatile var failCollect: Boolean = false

    override suspend fun collectPayment(draft: BookingDraft): PaymentResult {
        delay(350)
        if (failCollect) {
            throw IllegalStateException("Simulated payment failure")
        }
        return PaymentResult(
            orderId = "order_mock_${System.currentTimeMillis() / 1000}",
            paymentId = null,
            methodLabel = draft.paymentMethod.label,
            escrowState = PaymentEscrowState.Held,
        )
    }
}
