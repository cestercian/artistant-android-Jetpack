package `in`.artistant.app.navigation

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import `in`.artistant.app.platform.push.PushService

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PushEntryPoint {
    fun tabRouter(): TabRouter
    fun pushService(): PushService
}

@Composable
fun rememberTabRouter(): TabRouter {
    val appContext = LocalContext.current.applicationContext
    return remember(appContext) {
        EntryPointAccessors.fromApplication(appContext, PushEntryPoint::class.java).tabRouter()
    }
}

@Composable
fun rememberPushService(): PushService {
    val appContext = LocalContext.current.applicationContext
    return remember(appContext) {
        EntryPointAccessors.fromApplication(appContext, PushEntryPoint::class.java).pushService()
    }
}

fun pushEntryPoint(context: Context): PushEntryPoint =
    EntryPointAccessors.fromApplication(context.applicationContext, PushEntryPoint::class.java)
