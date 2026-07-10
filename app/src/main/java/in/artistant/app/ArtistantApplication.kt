package `in`.artistant.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import `in`.artistant.app.platform.push.NotificationChannels
import timber.log.Timber
import javax.inject.Inject

/**
 * Hilt root + debug logging bootstrap.
 *
 * Implements [Configuration.Provider] so WorkManager builds its config from the
 * Hilt-provided [HiltWorkerFactory] — that's what lets `@HiltWorker` upload
 * workers (M5a) get the Supabase repositories injected. The default
 * `WorkManagerInitializer` is removed in the manifest so this on-demand config
 * wins on the first `WorkManager.getInstance(...)`.
 */
@HiltAndroidApp
class ArtistantApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
        // Declare the push channels up front (idempotent) so P2b's NotificationCompat has a target.
        NotificationChannels.register(this)
    }
}
