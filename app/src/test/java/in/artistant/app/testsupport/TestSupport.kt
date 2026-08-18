package `in`.artistant.app.testsupport

import `in`.artistant.app.data.model.Artist
import `in`.artistant.app.data.model.ArtistPackage
import `in`.artistant.app.data.model.Booking
import `in`.artistant.app.data.model.BookingDraft
import `in`.artistant.app.data.model.BookingStatus
import `in`.artistant.app.data.model.EscrowStatus
import `in`.artistant.app.data.model.PaymentMethod
import `in`.artistant.app.data.payments.PaymentEscrowState
import `in`.artistant.app.data.payments.PaymentResult
import `in`.artistant.app.designsystem.theme.ArtistGradient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Shared fixtures + the Main-dispatcher rule for the restored unit suite.
 *
 * Test-only. Lives in its own package so nothing under `app/src/main` can
 * accidentally depend on it, and so the ~6 ViewModel test files don't each
 * carry a 20-line `Booking(...)` constructor call that drifts apart over time.
 */

/**
 * Swaps `Dispatchers.Main` for an [UnconfinedTestDispatcher] around each test.
 *
 * Unconfined (not StandardTestDispatcher) on purpose: every ViewModel here does
 * work in `init { refresh() }`, and unconfined runs those eagerly so a test can
 * assert on `state.value` straight after construction — the same choice the
 * pre-existing CheckoutViewModelLogicTest made.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val dispatcher: kotlinx.coroutines.test.TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

/** Stable UUIDs — `create()` rejects a non-UUID artist id, so fixtures must look real. */
const val ARTIST_ID = "11111111-1111-1111-1111-111111111111"
const val OTHER_ARTIST_ID = "22222222-2222-2222-2222-222222222222"
const val CLIENT_ID = "33333333-3333-3333-3333-333333333333"

/** Mock receipt — the dormant payments seam still stamps razorpay_* on create. */
val mockPayment = PaymentResult(
    orderId = "order_mock_1",
    methodLabel = "UPI",
    escrowState = PaymentEscrowState.Held,
)

fun booking(
    id: String = "b-1",
    artistId: String = ARTIST_ID,
    status: BookingStatus = BookingStatus.PendingConfirm,
    escrow: EscrowStatus = EscrowStatus.Held,
    date: String = "Sat, May 16, 2026",
    time: String = "8:30 PM",
    venue: String = "Rooftop",
    fee: Int = 20_000,
    clientFullName: String? = null,
    startIso: String? = null,
    createdAtEpochMs: Long = 0L,
): Booking = Booking(
    id = id,
    artistId = artistId,
    packageIndex = 0,
    date = date,
    time = time,
    venue = venue,
    guests = 80,
    fee = fee,
    platformFee = 0,
    gst = 0,
    total = fee,
    status = status,
    escrowStatus = escrow,
    paymentMethod = PaymentMethod.Upi,
    protectionEnabled = true,
    createdAtEpochMs = createdAtEpochMs,
    clientFullName = clientFullName,
    startDatetimeIso = startIso,
)

fun bookingDraft(
    artistId: String = ARTIST_ID,
    feeInr: Int = 20_000,
    venueNotes: String = "",
): BookingDraft = BookingDraft(
    artistId = artistId,
    packageIndex = 0,
    packageName = "Evening set",
    packageDuration = "2h",
    feeInr = feeInr,
    date = "Sat, May 16, 2026",
    dateRawEpochMs = 1_747_353_600_000L,
    time = "8:30 PM",
    venue = "Rooftop",
    guests = 80,
    paymentMethod = PaymentMethod.Upi,
    venueNotes = venueNotes,
)

fun artist(
    id: String = ARTIST_ID,
    name: String = "Nova Beats",
    price: Int = 25_000,
    packages: List<ArtistPackage> = emptyList(),
    daysAvailable: List<String> = emptyList(),
    timeSlots: List<String> = emptyList(),
): Artist = Artist(
    id = id,
    name = name,
    handle = name.lowercase().replace(" ", ""),
    category = "DJ",
    genre = "Electronic",
    city = "Bangalore",
    price = price,
    duration = "2h",
    score = 82,
    gradient = ArtistGradient.palette(0),
    gigs = 12,
    bio = "Live sets for rooftops and weddings.",
    packages = packages,
    daysAvailable = daysAvailable,
    timeSlots = timeSlots,
)

fun pkg(
    id: String,
    name: String,
    price: Int,
    duration: String = "2h",
    popular: Boolean = false,
): ArtistPackage = ArtistPackage(
    id = id,
    name = name,
    duration = duration,
    price = price,
    includes = emptyList(),
    popular = popular,
)
