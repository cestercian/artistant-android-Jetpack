package `in`.artistant.app.core.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import `in`.artistant.app.core.config.AppEnvironment
import `in`.artistant.app.platform.billing.AccountTokenStore
import `in`.artistant.app.platform.billing.MockSubscriptionService
import `in`.artistant.app.platform.billing.PlayBillingSubscriptionService
import `in`.artistant.app.platform.billing.SubscriptionService
import `in`.artistant.app.platform.billing.SubscriptionTokenWriter
import `in`.artistant.app.state.EntitlementStore
import javax.inject.Singleton

/**
 * The M7 subscription seam's composition root. Two @Provides so the DORMANT default is chosen
 * once, here, at the graph root:
 *
 * - [provideSubscriptionService] returns the Play-free [MockSubscriptionService] in v1 and only
 *   constructs [PlayBillingSubscriptionService] when `subscriptionsEnabled` — so with the flag
 *   off, PlayBilling is never even instantiated and BillingClient is never touched.
 * - [provideEntitlementStore] passes the flag in as the store's `enabled`, so the store is inert
 *   with the flag off (the token-writer / account-token seams are bound via @Binds in
 *   RepositoryModule, alongside the other platform seams).
 */
@Module
@InstallIn(SingletonComponent::class)
object BillingModule {

    @Provides
    @Singleton
    fun provideSubscriptionService(
        @ApplicationContext context: Context,
    ): SubscriptionService =
        if (AppEnvironment.subscriptionsEnabled) PlayBillingSubscriptionService(context)
        else MockSubscriptionService()

    @Provides
    @Singleton
    fun provideEntitlementStore(
        service: SubscriptionService,
        accountTokens: AccountTokenStore,
        tokenWriter: SubscriptionTokenWriter,
    ): EntitlementStore = EntitlementStore(
        service = service,
        accountTokens = accountTokens,
        tokenWriter = tokenWriter,
        enabled = AppEnvironment.subscriptionsEnabled,
    )
}
