package `in`.artistant.app.core.di

import dagger.Binds
import dagger.Module
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
import `in`.artistant.app.data.repository.RequestsRepository
import `in`.artistant.app.data.repository.ReviewsRepository
import `in`.artistant.app.data.repository.SavedArtistsRepository
import `in`.artistant.app.data.repository.SamplesRepository
import `in`.artistant.app.data.repository.SearchRepository
import `in`.artistant.app.data.repository.SupabaseAccountRepository
import `in`.artistant.app.data.repository.SupabaseArtistLinksRepository
import `in`.artistant.app.data.repository.SupabaseArtistMediaRepository
import `in`.artistant.app.data.repository.SupabaseArtistsRepository
import `in`.artistant.app.data.repository.SupabaseBookingsRepository
import `in`.artistant.app.data.repository.SupabaseMessagesRepository
import `in`.artistant.app.data.repository.SupabasePackagesRepository
import `in`.artistant.app.data.repository.SupabaseRequestsRepository
import `in`.artistant.app.data.repository.SupabaseReviewsRepository
import `in`.artistant.app.data.repository.SupabaseSavedArtistsRepository
import `in`.artistant.app.data.repository.SupabaseSamplesRepository
import `in`.artistant.app.data.repository.SupabaseSearchRepository
import `in`.artistant.app.data.repository.SupabaseTechRiderRepository
import `in`.artistant.app.data.repository.SupabaseUsersRepository
import `in`.artistant.app.data.repository.TechRiderRepository
import `in`.artistant.app.data.repository.UsersRepository

/**
 * Binds each repository interface → its Supabase impl. Repositories land here as their
 * screens ship (M1+). Abstract so `@Binds` has a home.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun bindAccount(impl: SupabaseAccountRepository): AccountRepository

    @Binds
    abstract fun bindUsers(impl: SupabaseUsersRepository): UsersRepository

    @Binds
    abstract fun bindArtists(impl: SupabaseArtistsRepository): ArtistsRepository

    @Binds
    abstract fun bindSearch(impl: SupabaseSearchRepository): SearchRepository

    @Binds
    abstract fun bindReviews(impl: SupabaseReviewsRepository): ReviewsRepository

    @Binds
    abstract fun bindSavedArtists(impl: SupabaseSavedArtistsRepository): SavedArtistsRepository

    @Binds
    abstract fun bindPackages(impl: SupabasePackagesRepository): PackagesRepository

    @Binds
    abstract fun bindTechRider(impl: SupabaseTechRiderRepository): TechRiderRepository

    @Binds
    abstract fun bindSamples(impl: SupabaseSamplesRepository): SamplesRepository

    @Binds
    abstract fun bindArtistMedia(impl: SupabaseArtistMediaRepository): ArtistMediaRepository

    @Binds
    abstract fun bindArtistLinks(impl: SupabaseArtistLinksRepository): ArtistLinksRepository

    @Binds
    abstract fun bindBookings(impl: SupabaseBookingsRepository): BookingsRepository

    @Binds
    abstract fun bindRequests(impl: SupabaseRequestsRepository): RequestsRepository

    @Binds
    abstract fun bindMessages(impl: SupabaseMessagesRepository): MessagesRepository

    /** Dormant mock payments — real Razorpay is a later one-line swap. */
    @Binds
    abstract fun bindPayments(impl: MockPaymentsService): PaymentsService
}
