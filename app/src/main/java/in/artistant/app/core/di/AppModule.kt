package `in`.artistant.app.core.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import `in`.artistant.app.platform.observability.Analytics
import `in`.artistant.app.platform.observability.Crash
import `in`.artistant.app.platform.observability.PostHogAnalytics
import `in`.artistant.app.platform.observability.SentryCrash
import `in`.artistant.app.platform.storage.AppPreferences
import `in`.artistant.app.platform.storage.KeyValueStore
import `in`.artistant.app.platform.auth.AuthGateway
import `in`.artistant.app.platform.auth.SessionManager
import `in`.artistant.app.platform.storage.SignupConsentStore
import `in`.artistant.app.feature.bookings.BookingsLocalStore
import `in`.artistant.app.feature.bookings.DataStoreBookingsLocalStore
import `in`.artistant.app.feature.search.DataStoreSearchRecents
import `in`.artistant.app.feature.search.SearchRecents

/**
 * App-level bindings: the DataStore prefs seams + the observability seams. Kept as one
 * small module rather than three so the wiring stays in one place.
 *
 * `@Binds`, never a hand-built `@Provides`. Every impl bound here already carries
 * `@Singleton @Inject constructor`, and a `@Provides` that calls that constructor
 * itself leaves the CONCRETE type's just-in-time binding standing alongside it: the
 * `Crash` key got the module's instance while anything injecting `SentryCrash`
 * directly got a second singleton — one whose `attachedUserId` `SessionManager` never
 * reaches, because it only ever touches the `Crash`-bound copy. Aliasing instead of
 * constructing leaves exactly one instance per impl. `AppPreferences` needs no entry
 * at all now that its constructor asks for `@ApplicationContext`; Hilt builds it from
 * its own `@Singleton @Inject constructor`.
 *
 * Abstract so `@Binds` has a home (same shape as `RepositoryModule`).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {
    /** [AppPreferences] IS the consent store — the interface only keeps signup unit-testable. */
    @Binds
    abstract fun bindSignupConsentStore(prefs: AppPreferences): SignupConsentStore

    /** Ditto for the generic snapshot store — see [KeyValueStore]. */
    @Binds
    abstract fun bindKeyValueStore(prefs: AppPreferences): KeyValueStore

    @Binds
    abstract fun bindSearchRecents(impl: DataStoreSearchRecents): SearchRecents

    /**
     * The Bookings tab's local state: the offline snapshot behind screen 122 —
     * the essentials of the night, kept so a basement Wi-Fi cannot take the venue
     * and the load-in note with it — plus the profile nudge's dismissal.
     */
    @Binds
    abstract fun bindBookingsLocalStore(
        impl: DataStoreBookingsLocalStore,
    ): BookingsLocalStore

    /** [SessionManager] IS the gateway — the interface only keeps the auth VM unit-testable. */
    @Binds
    abstract fun bindAuthGateway(impl: SessionManager): AuthGateway

    // Real observability wrappers — DARK-UNTIL-KEY: a guarded no-op until the
    // operator sets POSTHOG_API_KEY / SENTRY_DSN (see the wrapper class headers).
    @Binds
    abstract fun bindAnalytics(impl: PostHogAnalytics): Analytics

    @Binds
    abstract fun bindCrash(impl: SentryCrash): Crash
}
