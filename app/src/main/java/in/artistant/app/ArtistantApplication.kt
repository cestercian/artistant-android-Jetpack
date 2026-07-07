package `in`.artistant.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

/** Hilt root + debug logging bootstrap. */
@HiltAndroidApp
class ArtistantApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
    }
}
