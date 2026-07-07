package `in`.artistant.app.core.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import `in`.artistant.app.platform.observability.Analytics
import `in`.artistant.app.platform.observability.Crash
import `in`.artistant.app.platform.observability.NoopAnalytics
import `in`.artistant.app.platform.observability.NoopCrash
import `in`.artistant.app.platform.storage.AppPreferences
import javax.inject.Singleton

/**
 * App-level provides: DataStore prefs + the observability no-op seams. Kept as
 * one small module rather than three so M0 wiring stays in one place.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideAppPreferences(@ApplicationContext context: Context): AppPreferences =
        AppPreferences(context)

    // No-op observability until PostHog/Sentry keys land (dark-until-key).
    @Provides @Singleton fun provideAnalytics(): Analytics = NoopAnalytics()
    @Provides @Singleton fun provideCrash(): Crash = NoopCrash()
}
