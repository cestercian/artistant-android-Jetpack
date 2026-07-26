package `in`.artistant.app.core.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import `in`.artistant.app.data.payments.MockPaymentsService
import `in`.artistant.app.data.payments.PaymentsService
import `in`.artistant.app.data.repository.ArtistsRepository
import `in`.artistant.app.data.repository.BookingsRepository
import `in`.artistant.app.data.repository.RequestsRepository
import `in`.artistant.app.data.repository.ReviewsRepository
import `in`.artistant.app.data.repository.SearchRepository
import `in`.artistant.app.data.repository.SupabaseArtistsRepository
import `in`.artistant.app.data.repository.SupabaseBookingsRepository
import `in`.artistant.app.data.repository.SupabaseRequestsRepository
import `in`.artistant.app.data.repository.SupabaseReviewsRepository
import `in`.artistant.app.data.repository.SupabaseSearchRepository
import `in`.artistant.app.data.repository.SupabaseUsersRepository
import `in`.artistant.app.data.repository.UsersRepository

/**
 * Binds each repository interface → its Supabase impl. Repositories land here as their
 * screens ship (M1+). Abstract so `@Binds` has a home.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun bindUsers(impl: SupabaseUsersRepository): UsersRepository

    @Binds
    abstract fun bindArtists(impl: SupabaseArtistsRepository): ArtistsRepository

    @Binds
    abstract fun bindSearch(impl: SupabaseSearchRepository): SearchRepository

    @Binds
    abstract fun bindReviews(impl: SupabaseReviewsRepository): ReviewsRepository

    @Binds
    abstract fun bindBookings(impl: SupabaseBookingsRepository): BookingsRepository

    @Binds
    abstract fun bindRequests(impl: SupabaseRequestsRepository): RequestsRepository

    /** Dormant mock payments — real Razorpay is a later one-line swap. */
    @Binds
    abstract fun bindPayments(impl: MockPaymentsService): PaymentsService
}
