package `in`.artistant.app.harness

import android.app.Activity
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import `in`.artistant.app.designsystem.theme.AppRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Bootstraps the DEBUG fixture harness. Debug-source-set only — see [HarnessFlags] for why
 * a release artifact cannot contain any of this.
 *
 * WHY A CONTENTPROVIDER
 * ---------------------
 * The flags ride in on `MainActivity`'s launch intent, but they must be readable *before*
 * Hilt provisions anything, and `MainActivity` lives in `main/` where we must not add debug
 * wiring. A `ContentProvider` declared in `app/src/debug/AndroidManifest.xml` is created by
 * the framework before `Application.onCreate`, needs no cooperation from production code,
 * and disappears entirely from release builds along with its manifest entry. (Same
 * auto-install trick LeakCanary uses.)
 *
 * It does no content work at all — every `ContentProvider` method is an inert stub. Its only
 * job is to register the lifecycle callback below.
 */
class HarnessInstaller : ContentProvider() {

    override fun onCreate(): Boolean {
        val app = context?.applicationContext as? Application ?: return false
        app.registerActivityLifecycleCallbacks(HarnessLifecycleCallbacks(app))
        return true
    }

    // --- Inert ContentProvider surface. Nothing here is ever called. ---
    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}

/**
 * Reads the launch intent and installs [HarnessState] as early as the framework allows.
 *
 * ORDERING (the load-bearing part)
 * --------------------------------
 * `onActivityPreCreated` is dispatched from `Activity.performCreate` *before*
 * `Activity.onCreate` is invoked — and Hilt's generated `Hilt_MainActivity.onCreate` calls
 * `inject()` as its first statement. So pre-created runs before the activity's injection,
 * before `SessionManager`/`SavedStore` are constructed, and long before any ViewModel asks
 * for a repository. That is precisely what makes a provision-time fake/real choice in the
 * debug `RepositoryModule` correct rather than racy.
 *
 * `onActivityPreCreated` is API 29+. On API 26–28 we fall back to `onActivityCreated`, which
 * fires after the activity's Hilt injection — so the handful of repositories reachable from
 * `SessionManager` (only `SavedArtistsRepository`) would already have resolved to the real
 * implementation. Everything else still swaps. Documented rather than worked around: the
 * harness targets a modern emulator, and adding per-interface lazy delegates to cover two
 * old API levels would cost far more than it buys.
 */
private class HarnessLifecycleCallbacks(
    private val app: Application,
) : Application.ActivityLifecycleCallbacks {

    // Latch so a warm re-launch (onNewIntent) can't re-install flags after the DI graph has
    // already resolved against the first set — that would be a lie about what's on screen.
    // Pass flags on a cold start; `am start -S` force-stops first, which is what the docs use.
    private var installed = false

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
        install(activity)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        // API 26–28 fallback only; a no-op once onActivityPreCreated has latched.
        install(activity)
    }

    private fun install(activity: Activity) {
        if (installed) return
        installed = true

        val raw = runCatching { activity.intent?.getStringExtra(HarnessFlags.EXTRA) }.getOrNull()
        val flags = HarnessFlags.parse(raw)
        HarnessState.install(flags)

        if (!flags.active) return
        Timber.i("[harness] active: $flags")
        HarnessSession.bootstrap(app, flags)
    }

    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}

/**
 * The auth bypass.
 *
 * `RootViewModel` gates the whole app on `SessionManager.sessionStatus`, which is just
 * supabase-kt's `Auth.sessionStatus`. Rather than fork the routing (which would mean editing
 * production code and would stop the harness from exercising the real gate), we hand
 * supabase-kt a synthetic session via `importSession`. With `autoRefresh = false` that call
 * performs no network I/O whatsoever — it assigns `SessionStatus.Authenticated` and returns.
 *
 * The real routing then runs end-to-end: `RootViewModel` sees an authenticated session, asks
 * `UsersRepository` for the profile (the seeded fake answers), and picks the tab shell. So
 * the harness proves the production gate works instead of bypassing it.
 */
private object HarnessSession {

    // Long-lived: the import waits for supabase-kt's own storage restore to settle first.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface HarnessEntryPoint {
        fun supabaseClient(): SupabaseClient
    }

    fun bootstrap(app: Application, flags: HarnessFlags) {
        // No role requested → leave the real auth gate completely alone. `seed-fixture-data`
        // on its own still swaps repositories, which is useful for inspecting signup screens.
        val role = flags.skipSignupAs ?: return

        val client = runCatching {
            EntryPointAccessors.fromApplication(app, HarnessEntryPoint::class.java).supabaseClient()
        }.getOrElse {
            Timber.e(it, "[harness] couldn't reach the Supabase client; auth bypass skipped")
            return
        }

        scope.launch {
            // supabase-kt restores any persisted session asynchronously on client creation and
            // then publishes NotAuthenticated/Authenticated. Importing before that settles
            // would get clobbered by the restore, so wait for the first settled value.
            runCatching {
                client.auth.sessionStatus.first { it !is SessionStatus.Initializing }
                client.auth.importSession(fakeSession(role), autoRefresh = false)
                Timber.i("[harness] synthetic $role session installed")
            }.onFailure { Timber.e(it, "[harness] session import failed") }
        }
    }

    /**
     * A synthetic session for the fixture user.
     *
     * `refreshToken` is deliberately BLANK: `importSession(autoRefresh = false)` only writes
     * the session to supabase-kt's persistent storage when the refresh token is non-blank, so
     * a blank one keeps this fake session purely in-memory. It therefore evaporates with the
     * process and can never be mistaken for a real cached login on the next launch.
     *
     * The far-future expiry stops any expiry-driven refresh path from waking up.
     */
    private fun fakeSession(role: AppRole) = UserSession(
        accessToken = "harness-synthetic-access-token",
        refreshToken = "",
        expiresIn = 60L * 60 * 24 * 365,
        tokenType = "bearer",
        user = UserInfo(
            id = HarnessFixtures.selfUserId(role),
            aud = "authenticated",
            email = HarnessFixtures.selfEmail(role),
            role = "authenticated",
        ),
    )
}
