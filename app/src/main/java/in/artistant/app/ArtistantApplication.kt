package `in`.artistant.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import `in`.artistant.app.navigation.pushEntryPoint
import timber.log.Timber

/** Hilt root + debug logging + cold-launch FCM re-register. */
@HiltAndroidApp
class ArtistantApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
        // Soft-fail when Firebase isn't configured; cheap when permission not granted.
        pushEntryPoint(this).pushService().registerOnLaunchIfPermitted()
    }
}
