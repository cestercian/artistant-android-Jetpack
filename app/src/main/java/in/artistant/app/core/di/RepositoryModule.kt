package `in`.artistant.app.core.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import `in`.artistant.app.data.repository.ArtistLinksRepository
import `in`.artistant.app.data.repository.ArtistMediaRepository
import `in`.artistant.app.data.repository.ArtistsRepository
import `in`.artistant.app.data.repository.BookingsRepository
import `in`.artistant.app.data.repository.PackagesRepository
import `in`.artistant.app.data.repository.SamplesRepository
import `in`.artistant.app.data.repository.SupabaseArtistLinksRepository
import `in`.artistant.app.data.repository.SupabaseArtistMediaRepository
import `in`.artistant.app.data.repository.SupabasePackagesRepository
import `in`.artistant.app.data.repository.SupabaseSamplesRepository
import `in`.artistant.app.data.repository.SupabaseTechRiderRepository
import `in`.artistant.app.data.repository.TechRiderRepository
import `in`.artistant.app.data.repository.MessagesRepository
import `in`.artistant.app.data.repository.RequestsRepository
import `in`.artistant.app.data.repository.ReviewsRepository
import `in`.artistant.app.data.repository.SavedArtistsRepository
import `in`.artistant.app.data.repository.ScoreRepository
import `in`.artistant.app.data.repository.SearchRepository
import `in`.artistant.app.data.repository.SupabaseArtistsRepository
import `in`.artistant.app.data.repository.SupabaseBookingsRepository
import `in`.artistant.app.data.repository.SupabaseMessagesRepository
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
import `in`.artistant.app.platform.upload.MediaUploadEnqueuer
import `in`.artistant.app.platform.upload.UploadQueue

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

    // M4 Messaging — realtime chat + redaction.
    @Binds
    abstract fun bindMessages(impl: SupabaseMessagesRepository): MessagesRepository

    // M5a Artist-authoring write layer + media pipeline.
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

    // M5b wizard Publish — the enqueue seam over the (WorkManager-backed) UploadQueue. The
    // interface exists purely so the publish-ordering test can fake it (UploadQueue needs a
    // Context); the concrete @Singleton is still injectable directly for cancelAll/resume.
    @Binds
    abstract fun bindMediaUploadEnqueuer(impl: UploadQueue): MediaUploadEnqueuer

    // M5c ArtistHome banner — read seam over the same UploadQueue, faked in the
    // ArtistHome ViewModel test for the same Context-can't-be-constructed reason.
    @Binds
    abstract fun bindUploadBannerSource(impl: UploadQueue): `in`.artistant.app.platform.upload.UploadBannerSource

    // M5c EPK editor — media-staging seam over WizardMediaCache (needs a Context),
    // faked in the EpkViewModel test so the ViewModel stays plain-JVM constructible.
    @Binds
    abstract fun bindEpkMediaStager(
        impl: `in`.artistant.app.platform.media.WizardMediaCache,
    ): `in`.artistant.app.platform.media.EpkMediaStager

    // Payments seam — dormant mock in v1 (real provider is M7).
    @Binds
    abstract fun bindPayments(impl: MockPaymentsService): PaymentsService

    // M6 DPDP §11 — data-export + delete-account Edge Function wrapper.
    @Binds
    abstract fun bindAccountService(
        impl: `in`.artistant.app.platform.account.SupabaseAccountService,
    ): `in`.artistant.app.platform.account.AccountService

    // M6 calendar-sync seam — inert Noop until PART 2 lands the real Calendar-Provider impl.
    @Binds
    abstract fun bindCalendarSync(
        impl: `in`.artistant.app.platform.calendar.NoopCalendarSync,
    ): `in`.artistant.app.platform.calendar.CalendarSync
}
