package `in`.artistant.app.harness

import `in`.artistant.app.data.repository.AccountRepository
import `in`.artistant.app.data.repository.ArtistLinksRepository
import `in`.artistant.app.data.repository.ArtistMediaRepository
import `in`.artistant.app.data.repository.ArtistsRepository
import `in`.artistant.app.data.repository.BookingsRepository
import `in`.artistant.app.data.repository.FakeUsersRepository
import `in`.artistant.app.data.repository.MessagesRepository
import `in`.artistant.app.data.repository.PackagesRepository
import `in`.artistant.app.data.repository.ReportsRepository
import `in`.artistant.app.data.repository.RequestsRepository
import `in`.artistant.app.data.repository.ReviewsRepository
import `in`.artistant.app.data.repository.SamplesRepository
import `in`.artistant.app.data.repository.SavedArtistsRepository
import `in`.artistant.app.data.repository.ScoreRepository
import `in`.artistant.app.data.repository.SearchRepository
import `in`.artistant.app.data.repository.TechRiderRepository
import `in`.artistant.app.data.repository.UsersRepository

/**
 * Registry of the seeded in-memory repositories the DEBUG harness swaps in.
 *
 * Each accessor returns null when the harness is off — or when no fake has been written for
 * that seam yet — and the debug `RepositoryModule` falls back to the real Supabase
 * implementation. Two things follow from that shape:
 *
 *  1. A plain debug install with no `-e uitest` extra behaves EXACTLY as before. The fakes
 *     only ever appear for a process that was launched asking for them.
 *  2. Growing the harness is purely additive here and never touches the DI module.
 *
 * Instances are `by lazy` so a fake is only built when something asks for it, and so each is
 * a process-wide singleton — mutable fixture state (an accepted booking, a sent message) has
 * to survive navigation the way the real repositories do.
 */
object HarnessRepositories {

    private val on: Boolean get() = HarnessState.useFakes

    // --- Users: the seam the auth bypass depends on ---------------------------------------
    // RootViewModel asks this for the signed-in profile the moment the synthetic session
    // lands. Returning a COMPLETE profile is what moves the gate from Onboarding to the tab
    // shell; a null or half-filled one would strand the harness in signup.
    private val usersImpl: FakeUsersRepository by lazy {
        FakeUsersRepository(selfProfile = HarnessFixtures.selfProfile(HarnessState.flags))
    }

    val users: UsersRepository? get() = if (on) usersImpl else null

    // --- Not yet faked -------------------------------------------------------------------
    // Null means "use the real Supabase repository". These land as the artist and client
    // paths are built out; keeping the accessors declared now keeps RepositoryModule stable.
    val account: AccountRepository? get() = null
    val artists: ArtistsRepository? get() = null
    val search: SearchRepository? get() = null
    val reviews: ReviewsRepository? get() = null
    val savedArtists: SavedArtistsRepository? get() = null
    val packages: PackagesRepository? get() = null
    val techRider: TechRiderRepository? get() = null
    val samples: SamplesRepository? get() = null
    val artistMedia: ArtistMediaRepository? get() = null
    val artistLinks: ArtistLinksRepository? get() = null
    val score: ScoreRepository? get() = null
    val reports: ReportsRepository? get() = null
    val bookings: BookingsRepository? get() = null
    val requests: RequestsRepository? get() = null
    val messages: MessagesRepository? get() = null
}
