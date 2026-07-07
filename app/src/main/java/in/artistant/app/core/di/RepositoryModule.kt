package `in`.artistant.app.core.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import `in`.artistant.app.data.repository.ArtistsRepository
import `in`.artistant.app.data.repository.BookingsRepository
import `in`.artistant.app.data.repository.RequestsRepository
import `in`.artistant.app.data.repository.ReviewsRepository
import `in`.artistant.app.data.repository.SavedArtistsRepository
import `in`.artistant.app.data.repository.ScoreRepository
import `in`.artistant.app.data.repository.SearchRepository
import `in`.artistant.app.data.repository.SupabaseArtistsRepository
import `in`.artistant.app.data.repository.SupabaseBookingsRepository
import `in`.artistant.app.data.repository.SupabaseRequestsRepository
import `in`.artistant.app.data.repository.SupabaseReviewsRepository
import `in`.artistant.app.data.repository.SupabaseSavedArtistsRepository
import `in`.artistant.app.data.repository.SupabaseScoreRepository
import `in`.artistant.app.data.repository.SupabaseSearchRepository
import `in`.artistant.app.data.repository.SupabaseUsersRepository
import `in`.artistant.app.data.repository.UsersRepository
import `in`.artistant.app.feature.search.DataStoreSearchRecents
import `in`.artistant.app.feature.search.SearchRecents
import `in`.artistant.app.platform.payments.MockPaymentsService
import `in`.artistant.app.platform.payments.PaymentsService

/**
 * Binds each repository interface → its Supabase impl. Repositories land here as their
 * screens ship (M1+). Abstract so `@Binds` has a home.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun bindUsers(impl: SupabaseUsersRepository): UsersRepository

    // M2a Browse read layer.
    @Binds
    abstract fun bindArtists(impl: SupabaseArtistsRepository): ArtistsRepository

    @Binds
    abstract fun bindSearch(impl: SupabaseSearchRepository): SearchRepository

    @Binds
    abstract fun bindReviews(impl: SupabaseReviewsRepository): ReviewsRepository

    @Binds
    abstract fun bindScore(impl: SupabaseScoreRepository): ScoreRepository

    @Binds
    abstract fun bindSavedArtists(impl: SupabaseSavedArtistsRepository): SavedArtistsRepository

    // M2b Browse — Search recents persistence seam.
    @Binds
    abstract fun bindSearchRecents(impl: DataStoreSearchRecents): SearchRecents

    // M3 Booking funnel + gig requests.
    @Binds
    abstract fun bindBookings(impl: SupabaseBookingsRepository): BookingsRepository

    @Binds
    abstract fun bindRequests(impl: SupabaseRequestsRepository): RequestsRepository

    // Payments seam — dormant mock in v1 (real provider is M7).
    @Binds
    abstract fun bindPayments(impl: MockPaymentsService): PaymentsService
}
