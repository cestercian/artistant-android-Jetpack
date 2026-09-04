package `in`.artistant.app.feature.system

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Bindings for section SH's seams.
 *
 * Lives beside the code it binds rather than in `core/di`, for the reason the
 * package-by-feature layout exists: these three interfaces have exactly one
 * implementation each and exactly one set of callers, and a reader looking at
 * `ActivityLog` should find its binding in the same directory rather than in a
 * 60-line module shared by the whole app.
 *
 * [SystemStatusSource] is deliberately NOT here. It is the one seam the debug
 * harness has to be able to replace (`force-update` / `service-outage`), and
 * Hilt cannot override a binding in a normal build — so it is provided by the
 * per-variant `RepositoryModule`s, exactly like the repository fakes.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SystemModule {

    @Binds
    @Singleton
    abstract fun bindSystemPreferences(impl: DataStoreSystemPreferences): SystemPreferences

    @Binds
    @Singleton
    abstract fun bindActivityLog(impl: DataStoreActivityLog): ActivityLog

    @Binds
    @Singleton
    abstract fun bindFeedbackOutbox(impl: DataStoreFeedbackOutbox): FeedbackOutbox

    @Binds
    @Singleton
    abstract fun bindFeedbackDrainScheduler(
        impl: WorkManagerFeedbackDrainScheduler,
    ): FeedbackDrainScheduler
}
