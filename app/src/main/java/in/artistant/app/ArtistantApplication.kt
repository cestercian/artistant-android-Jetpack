package `in`.artistant.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import `in`.artistant.app.navigation.pushEntryPoint
import `in`.artistant.app.platform.push.NotificationChannels
import timber.log.Timber
import javax.inject.Inject

/**
 * Hilt root + WorkManager (UploadQueue drain) + debug logging + notification channels
 * + FCM re-register.
 */
@HiltAndroidApp
class ArtistantApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
        // Before anything can post: on API 26+ a notify() naming a channel that was never
        // created is DROPPED by the platform, so `ArtistantMessagingService` showing an
        // arriving push depends on this line. Idempotent — re-creating a channel is a no-op.
        NotificationChannels.register(this)
        // Soft-fail when Firebase isn't configured; cheap when permission not granted.
        pushEntryPoint(this).pushService().registerIfPermitted()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
