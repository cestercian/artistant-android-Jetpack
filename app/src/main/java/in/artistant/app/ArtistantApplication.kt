package `in`.artistant.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import `in`.artistant.app.navigation.pushEntryPoint
import timber.log.Timber
import javax.inject.Inject

/** Hilt root + WorkManager (UploadQueue drain) + debug logging + FCM re-register. */
@HiltAndroidApp
class ArtistantApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
        // Soft-fail when Firebase isn't configured; cheap when permission not granted.
        pushEntryPoint(this).pushService().registerOnLaunchIfPermitted()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
