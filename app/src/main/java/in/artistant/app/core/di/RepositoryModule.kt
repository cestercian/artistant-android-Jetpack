package `in`.artistant.app.core.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Empty @Binds host — repositories bind their interface → Supabase impl here as
 * they land (M1+). Abstract so future `@Binds` methods have a home without
 * creating a second module. ponytail: intentionally empty in M0.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule
