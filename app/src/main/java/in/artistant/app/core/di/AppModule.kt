package `in`.artistant.app.core.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import `in`.artistant.app.platform.observability.Analytics
import `in`.artistant.app.platform.observability.Crash
import `in`.artistant.app.platform.observability.PostHogAnalytics
import `in`.artistant.app.platform.observability.SentryCrash
import `in`.artistant.app.platform.storage.AppPreferences
import javax.inject.Singleton

/**
 * App-level provides: DataStore prefs + the observability seams. Kept as one small
 * module rather than three so the wiring stays in one place.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideAppPreferences(@ApplicationContext context: Context): AppPreferences =
        AppPreferences(context)

    // Real observability wrappers — DARK-UNTIL-KEY: a guarded no-op until the
    // operator sets POSTHOG_API_KEY / SENTRY_DSN (see the wrapper class headers).
    @Provides @Singleton fun provideAnalytics(): Analytics = PostHogAnalytics()
    @Provides @Singleton fun provideCrash(): Crash = SentryCrash()
}
