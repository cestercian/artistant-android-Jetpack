package `in`.artistant.app.core.di

import io.github.jan.supabase.SupabaseClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import `in`.artistant.app.core.network.SupabaseClientFactory
import javax.inject.Singleton

/** Provides the one app-wide SupabaseClient singleton. */
@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {
    @Provides
    @Singleton
    fun provideSupabaseClient(): SupabaseClient = SupabaseClientFactory.create()
}
