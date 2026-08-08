package `in`.artistant.app.harness

import `in`.artistant.app.data.model.SelfProfile
import `in`.artistant.app.designsystem.theme.AppRole

/**
 * Deterministic in-memory fixture data for the DEBUG harness. Debug source set only, so none
 * of this can reach a release artifact (see [HarnessFlags]).
 *
 * Ids mirror the iOS XCUITest fixtures' shape — fixed, obviously-synthetic UUIDs — so the two
 * harnesses stay recognisable side by side and so any row that leaks into a log is instantly
 * identifiable as fixture data. Everything is UUID-shaped because several repositories assume
 * UUID ids.
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
     * wizard-landing flag asked for the wizard tier specifically — that is the exact switch
     * `gateFor` reads to choose `RootGate.ArtistWizard` over `RootGate.Tabs`.
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
}
