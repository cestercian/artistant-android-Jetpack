package `in`.artistant.app.platform.payments

import `in`.artistant.app.data.model.BookingDraft
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stand-in for a real provider until M7 (iOS `MockPaymentsService`). Returns a
 * synthesized held-escrow result after a brief "confirming…" beat — no real money
 * moves. [simulatedLatencyMillis] and [failCollect] are mutable test seams
 * (fault-injection for the confirm-failure branch); production leaves the defaults.
 */
@Singleton
class MockPaymentsService @Inject constructor() : PaymentsService {

    var simulatedLatencyMillis: Long = 350
    var failCollect: Boolean = false

    override suspend fun collectPayment(draft: BookingDraft): PaymentResult {
        delay(simulatedLatencyMillis)
        if (failCollect) {
            throw PaymentException.ProviderError("Simulated payment failure (test seam)")
        }
        // `order_mock_` prefix mirrors Razorpay's `order_*` shape so logs grep cleanly.
        val orderId = "order_mock_${System.currentTimeMillis() / 1000}"
        return PaymentResult(
            orderId = orderId,
            paymentId = null,                 // real webhook populates this in M7
            methodLabel = draft.paymentMethod.label,
            escrowState = PaymentEscrowState.Held,
        )
    }
}
