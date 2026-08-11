package `in`.artistant.app.core.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import `in`.artistant.app.data.payments.MockPaymentsService
import `in`.artistant.app.data.payments.PaymentsService
import `in`.artistant.app.data.repository.AccountRepository
import `in`.artistant.app.data.repository.ArtistLinksRepository
import `in`.artistant.app.data.repository.ArtistMediaRepository
import `in`.artistant.app.data.repository.ArtistsRepository
import `in`.artistant.app.data.repository.BookingsRepository
import `in`.artistant.app.data.repository.MessagesRepository
import `in`.artistant.app.data.repository.PackagesRepository
import `in`.artistant.app.data.repository.BlockRepository
import `in`.artistant.app.data.repository.ReportsRepository
import `in`.artistant.app.data.repository.RequestsRepository
import `in`.artistant.app.data.repository.ReviewsRepository
import `in`.artistant.app.data.repository.SamplesRepository
import `in`.artistant.app.data.repository.SavedArtistsRepository
import `in`.artistant.app.data.repository.ScoreRepository
import `in`.artistant.app.data.repository.SearchRepository
import `in`.artistant.app.data.repository.SupabaseAccountRepository
import `in`.artistant.app.data.repository.SupabaseArtistLinksRepository
import `in`.artistant.app.data.repository.SupabaseArtistMediaRepository
import `in`.artistant.app.data.repository.SupabaseArtistsRepository
import `in`.artistant.app.data.repository.SupabaseBookingsRepository
import `in`.artistant.app.data.repository.SupabaseMessagesRepository
import `in`.artistant.app.data.repository.SupabasePackagesRepository
import `in`.artistant.app.data.repository.SupabaseBlockRepository
import `in`.artistant.app.data.repository.SupabaseReportsRepository
import `in`.artistant.app.data.repository.SupabaseRequestsRepository
import `in`.artistant.app.data.repository.SupabaseReviewsRepository
import `in`.artistant.app.data.repository.SupabaseSamplesRepository
import `in`.artistant.app.data.repository.SupabaseSavedArtistsRepository
import `in`.artistant.app.data.repository.SupabaseScoreRepository
import `in`.artistant.app.data.repository.SupabaseSearchRepository
import `in`.artistant.app.data.repository.SupabaseTechRiderRepository
import `in`.artistant.app.data.repository.SupabaseUsersRepository
import `in`.artistant.app.data.repository.TechRiderRepository
import `in`.artistant.app.data.repository.UsersRepository
import `in`.artistant.app.harness.HarnessRepositories
import javax.inject.Singleton

/**
 * DEBUG variant of the repository bindings.
 *
 * The canonical module lives in `app/src/release/` and is the unchanged `@Binds` original:
 * interface → Supabase implementation, nothing else. This debug twin exists so a
 * `-e uitest`-launched process can serve seeded in-memory fakes instead, and Hilt has no way
 * to override an existing binding in a normal (non-test) build — the binding itself has to
 * differ per variant, which means the module has to live in a variant source set.
 *
 * SAFETY: because the two files sit in `debug/` and `release/` rather than `main/`, a release
 * build compiles the plain `@Binds` version and never links against `HarnessRepositories`,
 * the fakes, or any fixture. The harness is absent from the artifact, not disabled in it.
 *
 * BEHAVIOUR WHEN THE HARNESS IS OFF: every provider falls through to the same real Supabase
 * implementation the release module binds, so an ordinary debug run is byte-for-byte
 * equivalent in behaviour to before this file existed.
 *
 * `@Singleton` is explicit on every provider. The originals were `@Binds` onto `@Singleton`
 * implementation classes, so they were already app-scoped; the fakes must be too, or mutable
 * fixture state would reset every time a new screen injected the repository.
 */
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideAccount(real: SupabaseAccountRepository): AccountRepository =
        HarnessRepositories.account ?: real

    @Provides
    @Singleton
    fun provideUsers(real: SupabaseUsersRepository): UsersRepository =
        HarnessRepositories.users ?: real

    @Provides
    @Singleton
    fun provideArtists(real: SupabaseArtistsRepository): ArtistsRepository =
        HarnessRepositories.artists ?: real

    @Provides
    @Singleton
    fun provideSearch(real: SupabaseSearchRepository): SearchRepository =
        HarnessRepositories.search ?: real

    @Provides
    @Singleton
    fun provideReviews(real: SupabaseReviewsRepository): ReviewsRepository =
        HarnessRepositories.reviews ?: real

    @Provides
    @Singleton
    fun provideSavedArtists(real: SupabaseSavedArtistsRepository): SavedArtistsRepository =
        HarnessRepositories.savedArtists ?: real

    @Provides
    @Singleton
    fun providePackages(real: SupabasePackagesRepository): PackagesRepository =
        HarnessRepositories.packages ?: real

    @Provides
    @Singleton
    fun provideTechRider(real: SupabaseTechRiderRepository): TechRiderRepository =
        HarnessRepositories.techRider ?: real

    @Provides
    @Singleton
    fun provideSamples(real: SupabaseSamplesRepository): SamplesRepository =
        HarnessRepositories.samples ?: real

    @Provides
    @Singleton
    fun provideArtistMedia(real: SupabaseArtistMediaRepository): ArtistMediaRepository =
        HarnessRepositories.artistMedia ?: real

    @Provides
    @Singleton
    fun provideArtistLinks(real: SupabaseArtistLinksRepository): ArtistLinksRepository =
        HarnessRepositories.artistLinks ?: real

    @Provides
    @Singleton
    fun provideScore(real: SupabaseScoreRepository): ScoreRepository =
        HarnessRepositories.score ?: real

    @Provides
    @Singleton
    fun provideReports(real: SupabaseReportsRepository): ReportsRepository =
        HarnessRepositories.reports ?: real

    @Provides
    @Singleton
    fun provideBlock(real: SupabaseBlockRepository): BlockRepository =
        HarnessRepositories.block ?: real

    @Provides
    @Singleton
    fun provideBookings(real: SupabaseBookingsRepository): BookingsRepository =
        HarnessRepositories.bookings ?: real

    @Provides
    @Singleton
    fun provideRequests(real: SupabaseRequestsRepository): RequestsRepository =
        HarnessRepositories.requests ?: real

    @Provides
    @Singleton
    fun provideMessages(real: SupabaseMessagesRepository): MessagesRepository =
        HarnessRepositories.messages ?: real

    /** Dormant mock payments — real Razorpay is a later one-line swap. Unchanged from release. */
    @Provides
    @Singleton
    fun providePayments(impl: MockPaymentsService): PaymentsService = impl
}
