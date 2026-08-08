package `in`.artistant.app.harness

import `in`.artistant.app.data.model.Artist
import `in`.artistant.app.data.model.ArtistGradient
import `in`.artistant.app.data.model.ArtistPackage
import `in`.artistant.app.data.model.Booking
import `in`.artistant.app.data.model.BookingStatus
import `in`.artistant.app.data.model.EscrowStatus
import `in`.artistant.app.data.model.GigRequest
import `in`.artistant.app.data.model.GigRequestStatus
import `in`.artistant.app.data.model.PaymentMethod
import `in`.artistant.app.data.model.Review
import `in`.artistant.app.data.model.Sample
import `in`.artistant.app.data.model.SelfProfile
import `in`.artistant.app.data.model.StoredRequest
import `in`.artistant.app.data.repository.PackageDraft
import `in`.artistant.app.data.repository.ScoreBreakdown
import `in`.artistant.app.data.repository.ScoreHistoryPoint
import `in`.artistant.app.designsystem.theme.AppRole
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Deterministic in-memory fixture data for the DEBUG harness. Debug source set only, so none
 * of this can reach a release artifact (see [HarnessFlags]).
 *
 * Ids mirror the iOS XCUITest fixtures' shape — fixed, obviously-synthetic UUIDs — so the two
 * harnesses stay recognisable side by side and so any row that surfaces in a log is instantly
 * identifiable as fixture data. Everything is UUID-shaped because several repositories assume
 * UUID ids.
 *
 * DATES ARE RELATIVE TO LAUNCH, NOT PINNED. The iOS fixtures pin absolute instants because
 * XCUITest asserts on rendered strings. This harness has a different job: it exists so a human
 * can LOOK at a screen. A pinned date would quietly rot into "No upcoming gigs" a few months
 * after this file was written, which is exactly the empty dashboard the harness is meant to
 * cure. Offsets are whole days from launch, so the shape of the data is stable even though the
 * literal strings are not.
 */
object HarnessFixtures {

    /** The fixture artist. When booting `skip-signup-as-artist`, this is also the signed-in user. */
    const val ARTIST_ID = "11111111-1111-1111-1111-111111111111"

    /** The fixture client — the signed-in user when booting `skip-signup-as-client`. */
    const val CLIENT_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"

    const val THREAD_ID = "22222222-2222-2222-2222-222222222222"
    const val REQUEST_ID = "33333333-3333-3333-3333-333333333333"
    const val BOOKING_ID = "44444444-4444-4444-4444-444444444444"
    const val REVIEW_ID = "55555555-5555-5555-5555-555555555555"
    const val PENDING_BOOKING_ID = "66666666-6666-6666-6666-666666666666"

    // --- Session identity -----------------------------------------------------------------

    /** Which uid the synthetic session is issued for, per booted role. */
    fun selfUserId(role: AppRole): String = when (role) {
        AppRole.Artist -> ARTIST_ID
        AppRole.Client -> CLIENT_ID
    }

    fun selfEmail(role: AppRole): String = when (role) {
        AppRole.Artist -> "fixture.artist@artistant.invalid"
        AppRole.Client -> "fixture.client@artistant.invalid"
    }

    /**
     * The `public.users` row the harness pretends the server holds.
     *
     * `isComplete` must be true (role + non-blank handle) or `RootViewModel` routes to
     * Onboarding instead of the tabs. `artistSetupComplete` is true for artists unless a
     * wizard-landing flag asked otherwise — that is the exact switch `gateFor` reads to choose
     * `RootGate.ArtistWizard` over `RootGate.Tabs`.
     */
    fun selfProfile(flags: HarnessFlags): SelfProfile? {
        val role = flags.skipSignupAs ?: return null
        return SelfProfile(
            role = role,
            fullName = if (role == AppRole.Artist) "Fixture Artist" else "Fixture Client",
            city = "Bangalore",
            handle = if (role == AppRole.Artist) "fixtureartist" else "fixtureclient",
            artistSetupComplete = when {
                role != AppRole.Artist -> null
                flags.landInWizardAt != null -> false
                else -> true
            },
        )
    }

    // --- Packages -------------------------------------------------------------------------

    /**
     * Three tiers at DELIBERATELY different prices. The headline "from ₹…" on Discover, search
     * and the artist profile is derived as the MINIMUM package price, and a fixture where every
     * tier costs the same would render a correct "from" and a broken one identically. 60k is the
     * floor, so any surface showing 95k or 140k as the headline is wrong on sight.
     */
    val packages: List<ArtistPackage> = listOf(
        ArtistPackage(
            id = "pkg-acoustic",
            name = "Acoustic duo",
            duration = "45 min",
            price = 60_000,
            includes = listOf("Two performers", "Own PA", "One sound check"),
        ),
        ArtistPackage(
            id = "pkg-headline",
            name = "60-min headline",
            duration = "60 min",
            price = 95_000,
            includes = listOf("Full band", "Sound check", "Backline"),
            popular = true,
        ),
        ArtistPackage(
            id = "pkg-extended",
            name = "90-min extended",
            duration = "90 min",
            price = 140_000,
            includes = listOf("Full band", "Sound check", "Backline", "Encore set"),
        ),
    )

    /** Same three tiers in draft form — how `FakePackagesRepository` accepts a seed. */
    val packageDrafts: List<PackageDraft> = packages.map {
        PackageDraft(
            name = it.name,
            durationLabel = it.duration,
            priceInr = it.price,
            includes = it.includes,
            popular = it.popular,
        )
    }

    val techRider: List<String> = listOf(
        "2× XLR vocal mics + stands",
        "DI box for keys",
        "8-channel mixer",
        "Stage monitors ×2",
    )

    val samples: List<Sample> = listOf(
        Sample(id = "sample-1", title = "Rooftop set — opener", duration = "3:42"),
        Sample(id = "sample-2", title = "Wedding reception medley", duration = "4:11"),
    )

    // --- The artist ------------------------------------------------------------------------

    /**
     * The fixture artist: the signed-in user under `skip-signup-as-artist`, and the one card
     * every client-side surface resolves. Every optional field is populated on purpose — an
     * empty bio or an empty samples list would silently exercise a fallback branch and hide
     * whatever the operator is trying to look at.
     *
     * `price` is the minimum package price so the "from" headline is internally consistent.
     */
    val artist: Artist = Artist(
        id = ARTIST_ID,
        name = "Fixture Artist",
        handle = "fixtureartist",
        category = "Indie Band",
        genre = "Indie rock",
        city = "Bangalore",
        price = packages.minOf { it.price },
        duration = "60-min set",
        score = 82,
        gradient = ArtistGradient.palette(0),
        bio = "Fixture artist used by the debug harness to render Discover, the artist " +
            "profile, the booking funnel and the EPK without a real account or any network.",
        followers = "12k",
        streams = "84k",
        response = "Replies in ~1h",
        onTime = 96,
        gigs = 42,
        rating = 4.8,
        packages = packages,
        tech = techRider,
        samples = samples,
        reviews = emptyList(),
        instagramHandle = "fixtureartist",
        daysAvailable = listOf("Fri", "Sat"),
        timeSlots = listOf("8:30 PM", "9:00 PM", "10:00 PM"),
    )

    // --- Score ------------------------------------------------------------------------------

    /**
     * Above the New-tier threshold on purpose: `ScoreBreakdown.numericScore` returns null (and
     * the ring renders "NEW") for anyone under 5 gigs, so a low-gig fixture would show the empty
     * ring the harness exists to get past.
     */
    val score = ScoreBreakdown(
        score = 82,
        showUpRate = 96,
        reviewScore = 88,
        replySpeed = 74,
        cancellationRate = 4,
        socialProof = 61,
        totalGigs = 42,
    )

    /** A rising trend so the score history sparkline has a readable shape. */
    val scoreHistory: List<ScoreHistoryPoint> = listOf(64, 68, 71, 75, 78, 82)
        .mapIndexed { i, s -> ScoreHistoryPoint(score = s, computedAtIso = isoDaysFromNow(-(30 - i * 6))) }

    // --- Bookings ----------------------------------------------------------------------------

    /**
     * One booking in each status the artist dashboard buckets on, so every branch of the gigs
     * list, the status timeline and the dashboard counters has a row to render:
     *
     *   pending_confirm → the artist's Accept / Decline surface
     *   confirmed       → "upcoming gigs" + the booked days on the availability strip
     *   completed       → the counters, and the client-side review prompt
     *   cancelled       → the neutral/terminal styling
     *
     * Two confirmed rows so "next gig in N days" has something to choose between rather than
     * silently taking the single-item path.
     */
    val bookings: List<Booking> = listOf(
        booking(
            id = BOOKING_ID,
            daysFromNow = 12,
            hour = 20,
            venue = "Phoenix Marketcity",
            client = "Meera Iyer",
            status = BookingStatus.Confirmed,
            packageIndex = 1,
        ),
        booking(
            id = "77777777-7777-7777-7777-777777777777",
            daysFromNow = 26,
            hour = 21,
            venue = "The Humming Tree",
            client = "Nikhil Rao",
            status = BookingStatus.Confirmed,
            packageIndex = 2,
        ),
        booking(
            id = "88888888-8888-8888-8888-888888888888",
            daysFromNow = 5,
            hour = 19,
            venue = "Bangalore International Centre",
            client = "Aarav Shah",
            status = BookingStatus.PendingConfirm,
            packageIndex = 0,
        ),
        booking(
            id = "99999999-9999-9999-9999-999999999999",
            daysFromNow = -18,
            hour = 20,
            venue = "Fandom at Gilly's",
            client = "Priya Nair",
            status = BookingStatus.Completed,
            packageIndex = 1,
        ),
        booking(
            id = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
            daysFromNow = -4,
            hour = 22,
            venue = "Church Street Social",
            client = "Rohan Gupta",
            status = BookingStatus.Cancelled,
            packageIndex = 0,
        ),
    )

    /**
     * The extra pending request seeded by `seed-pending-request`, kept separate from the set
     * above so the artist accept/decline surface can be driven on its own without changing the
     * baseline dashboard the other flags produce. Mirrors the iOS harness's split.
     */
    val pendingBooking: Booking = booking(
        id = PENDING_BOOKING_ID,
        daysFromNow = 9,
        hour = 21,
        venue = "Sunburn Arena",
        client = "Ishaan Verma",
        status = BookingStatus.PendingConfirm,
        packageIndex = 1,
    )

    /**
     * Build one booking. Fees follow the same 5% platform + 18% GST shape the production maths
     * uses, so the money rows add up if anyone reads them — v1 takes no payments, but a total
     * that doesn't equal its parts reads as a bug during a design review.
     */
    private fun booking(
        id: String,
        daysFromNow: Int,
        hour: Int,
        venue: String,
        client: String,
        status: BookingStatus,
        packageIndex: Int,
    ): Booking {
        val fee = packages[packageIndex].price
        val platform = fee * 5 / 100
        val gst = (fee + platform) * 18 / 100
        val startMs = daysFromNow(daysFromNow, hour)
        return Booking(
            id = id,
            artistId = ARTIST_ID,
            packageIndex = packageIndex,
            date = dayLabel(startMs),
            time = timeLabel(startMs),
            venue = venue,
            guests = 120,
            fee = fee,
            platformFee = platform,
            gst = gst,
            total = fee + platform + gst,
            status = status,
            escrowStatus = EscrowStatus.Held,
            paymentMethod = PaymentMethod.Upi,
            protectionEnabled = true,
            // Ordered by gig date so lists sorted on creation read sensibly.
            createdAtEpochMs = startMs - TimeUnit.DAYS.toMillis(21),
            clientFullName = client,
            startDatetimeIso = iso(startMs),
            endDatetimeIso = iso(startMs + TimeUnit.HOURS.toMillis(2)),
            venueNotes = "Load-in from the rear entrance; parking on level 2.",
        )
    }

    // --- Gig requests --------------------------------------------------------------------

    /** One open quote request so the artist Home request rail and Gigs tab both have a row. */
    val gigRequest = StoredRequest(
        raw = GigRequest(
            id = REQUEST_ID,
            client = "Ananya Desai",
            message = "Need a 60-minute indie set for our annual offsite. Rooftop, ~150 guests.",
            date = dayLabel(daysFromNow(34, 20)),
            amount = 95_000,
            packageLabel = "60-min headline",
            timeAgo = "12m ago",
        ),
        status = GigRequestStatus.Open,
    )

    // --- Reviews ---------------------------------------------------------------------------

    val reviews: List<Review> = listOf(
        Review(
            id = REVIEW_ID,
            name = "Priya Nair",
            org = "Ripple Events",
            rating = 5,
            body = "Turned up early, sound-checked without fuss and read the room perfectly. " +
                "Half the guests asked who they were.",
            createdAt = isoDaysFromNow(-16),
            categories = mapOf("Punctuality" to 5, "Sound" to 5, "Communication" to 4),
        ),
        Review(
            id = "cccccccc-cccc-cccc-cccc-cccccccccccc",
            name = "Rohan Gupta",
            org = "Northside Collective",
            rating = 4,
            body = "Great set. Load-in ran a little late but they still started on time.",
            createdAt = isoDaysFromNow(-52),
            categories = mapOf("Punctuality" to 4, "Sound" to 5, "Communication" to 4),
        ),
    )

    // --- Date helpers ----------------------------------------------------------------------

    private fun daysFromNow(days: Int, hour: Int): Long {
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.DAY_OF_YEAR, days)
        cal.set(java.util.Calendar.HOUR_OF_DAY, hour)
        cal.set(java.util.Calendar.MINUTE, 30)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun isoDaysFromNow(days: Int): String = iso(daysFromNow(days, 12))

    private fun iso(epochMs: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(java.util.Date(epochMs))

    private fun dayLabel(epochMs: Long): String =
        SimpleDateFormat("EEE, MMM d, yyyy", Locale.US).format(java.util.Date(epochMs))

    private fun timeLabel(epochMs: Long): String =
        SimpleDateFormat("h:mm a", Locale.US).format(java.util.Date(epochMs))
}
