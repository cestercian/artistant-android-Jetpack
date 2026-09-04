package `in`.artistant.app.feature.profile

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.designsystem.component.AccentNote
import `in`.artistant.app.designsystem.component.BackHeader
import `in`.artistant.app.designsystem.component.Banner
import `in`.artistant.app.designsystem.component.BannerTone
import `in`.artistant.app.designsystem.component.EyebrowLabel
import `in`.artistant.app.designsystem.component.SwitchRow
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.ArtistantTheme
import `in`.artistant.app.platform.preferences.NotificationPreferences
import `in`.artistant.app.platform.preferences.NotificationSettings
import `in`.artistant.app.platform.preferences.NotificationToggle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Screen 124's whole state: eight switches, plus whether the OS is letting anything through. */
data class NotificationSettingsUiState(
    val settings: NotificationSettings = NotificationSettings(),
    /**
     * The OS-level permission. Null on API < 33, where notifications need no runtime grant and
     * there is nothing to state — the banner is simply not drawn rather than claiming a
     * permission the platform does not have.
     */
    val systemPermissionGranted: Boolean? = null,
)

@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(
    private val prefs: NotificationPreferences,
) : ViewModel() {
    private val _state = MutableStateFlow(NotificationSettingsUiState())
    val state: StateFlow<NotificationSettingsUiState> = _state

    init {
        viewModelScope.launch {
            prefs.all.collect { settings -> _state.update { it.copy(settings = settings) } }
        }
    }

    /** Re-read on every resume: the user can leave to system settings and revoke the grant. */
    fun refreshPermission(granted: Boolean?) =
        _state.update { it.copy(systemPermissionGranted = granted) }

    /**
     * State first, then persist.
     *
     * A switch that waits for DataStore to echo the write back visibly lags the thumb, and
     * `DataStore.edit` throws IOException on a preferences file it cannot write — unguarded
     * inside `viewModelScope.launch` that reaches the thread's default handler and takes the
     * app down. Same shape as `PrivacyViewModel.setReadReceipts`, for the same two reasons.
     */
    fun set(toggle: NotificationToggle, enabled: Boolean) {
        _state.update { it.copy(settings = it.settings.with(toggle, enabled)) }
        viewModelScope.launch { runCatching { prefs.set(toggle, enabled) } }
    }
}

/**
 * Design screen 124 — **"Granular, and honest by default"**.
 *
 * Two groups of switches, quiet hours, and a footer that states the rule the design's note
 * cares about: marketing is off by default and stays off unless you turn it on, and the signup
 * opt-in is a separate thing from the OS permission.
 *
 * **What these switches actually do, said out loud.** The canonical schema has no
 * notification-preference table — `device_tokens` (mig 0001) holds a token and nothing else,
 * and the three push triggers (mig 0016) fan out unconditionally. So the server is not told:
 * these are device preferences that decide what THIS app raises. The banner at the top of the
 * list says so instead of implying the account was updated. See [NotificationPreferences].
 *
 * **The permission banner is a real read**, not a decoration: `POST_NOTIFICATIONS` is a
 * runtime grant on API 33+, and a list of eight switches above a revoked permission is eight
 * controls over nothing. Denied gets a route to the system screen that owns it.
 */
@Composable
fun NotificationSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotificationSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Re-read on every ON_RESUME, not once. The banner's own action sends the user OUT to the
    // system notification screen to change exactly this grant, so the one moment the answer is
    // most likely to be stale is the moment they come back.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermission(notificationPermissionGranted(context))
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    NotificationSettingsContent(
        state = state,
        onBack = onBack,
        onToggle = viewModel::set,
        onOpenSystemSettings = { openNotificationSettings(context) },
        modifier = modifier,
    )
}

@Composable
private fun NotificationSettingsContent(
    state: NotificationSettingsUiState,
    onBack: () -> Unit,
    onToggle: (NotificationToggle, Boolean) -> Unit,
    onOpenSystemSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val settings = state.settings

    AccountScaffold(
        modifier = modifier.semantics { testTag = "screen.notifications" },
        header = {
            BackHeader(title = "Notifications", onBack = onBack, subtitle = "What we send, and how")
        },
    ) {
        AccountGap()
        when (state.systemPermissionGranted) {
            true -> AccentNote(
                text = "System permission is on. These control what we send inside that.",
                modifier = Modifier.semantics { testTag = "notifications.permissionOn" },
            )
            false -> Banner(
                title = "Notifications are blocked for Artistant",
                tone = BannerTone.Attention,
                detail = "Android is dropping every one of these. Turn the permission back on " +
                    "and the switches below take effect again.",
                actionLabel = "Open settings",
                onAction = onOpenSystemSettings,
                modifier = Modifier.semantics { testTag = "notifications.permissionOff" },
            )
            // API < 33: no runtime grant exists, so there is no permission fact to state.
            null -> Unit
        }

        AccountGap()
        EyebrowLabel("Bookings", color = colors.ink4)
        Spacer(Modifier.height(dimens.space.sm))
        SwitchRow(
            title = "Quotes and replies",
            subtitle = "A request, a counter, or a reply in a thread",
            checked = settings.quotesAndReplies,
            onCheckedChange = { onToggle(NotificationToggle.QuotesAndReplies, it) },
            modifier = Modifier.semantics { testTag = "notifications.quotes" },
        )
        SwitchRow(
            title = "Booking confirmed or declined",
            subtitle = "When the other side answers a request",
            checked = settings.bookingUpdates,
            onCheckedChange = { onToggle(NotificationToggle.BookingUpdates, it) },
        )
        SwitchRow(
            title = "Show-day reminder",
            subtitle = "The evening before",
            checked = settings.showDayReminder,
            onCheckedChange = { onToggle(NotificationToggle.ShowDayReminder, it) },
        )
        SwitchRow(
            title = "Load-in reminder",
            subtitle = "Three hours before",
            checked = settings.loadInReminder,
            onCheckedChange = { onToggle(NotificationToggle.LoadInReminder, it) },
            showHairline = false,
        )

        AccountGap()
        EyebrowLabel("Everything else", color = colors.ink4)
        Spacer(Modifier.height(dimens.space.sm))
        SwitchRow(
            title = "New acts in your city",
            subtitle = "Weekly at most",
            checked = settings.newActs,
            onCheckedChange = { onToggle(NotificationToggle.NewActs, it) },
            modifier = Modifier.semantics { testTag = "notifications.newActs" },
        )
        SwitchRow(
            title = "Tips and offers",
            subtitle = "Product news and promotions",
            checked = settings.tipsAndOffers,
            onCheckedChange = { onToggle(NotificationToggle.TipsAndOffers, it) },
            modifier = Modifier.semantics { testTag = "notifications.tips" },
        )
        SwitchRow(
            title = "Review reminders",
            subtitle = "Once, 24h after a show",
            checked = settings.reviewReminders,
            onCheckedChange = { onToggle(NotificationToggle.ReviewReminders, it) },
            showHairline = false,
        )

        AccountGap()
        EyebrowLabel("Quiet hours", color = colors.ink4)
        Spacer(Modifier.height(dimens.space.sm))
        SwitchRow(
            title = "10:00 pm – 8:00 am",
            subtitle = "Urgent booking changes still come through",
            checked = settings.quietHours,
            onCheckedChange = { onToggle(NotificationToggle.QuietHours, it) },
            showHairline = false,
            modifier = Modifier.semantics { testTag = "notifications.quietHours" },
        )

        AccountGap(2)
        Text(
            "Marketing is off by default and stays off unless you turn it on — the signup " +
                "opt-in is separate from permission. These choices live on this device: " +
                "Artistant has no server-side notification setting, so they don't follow you " +
                "to another phone.",
            style = AppTheme.type.caption,
            color = colors.ink4,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(dimens.size.listTailroom))
    }
}

/**
 * Whether the OS is currently letting this app post notifications.
 *
 * Null below API 33: `POST_NOTIFICATIONS` does not exist there, notifications need no runtime
 * grant, and answering `true` would state a permission the platform never asked for.
 */
private fun notificationPermissionGranted(context: Context): Boolean? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
}

/**
 * Open the OS notification screen for this app, falling back to the app's own settings page.
 *
 * `ACTION_APP_NOTIFICATION_SETTINGS` is not on every OEM/Go build, and `startActivity` throws
 * ActivityNotFoundException on the main thread straight out of a click handler — which is a
 * crash from tapping a banner. The fallback (`ACTION_APPLICATION_DETAILS_SETTINGS`) is
 * guaranteed by the platform, and a failure of BOTH is swallowed: a settings screen that
 * cannot open is a disappointment, not a crash.
 */
private fun openNotificationSettings(context: Context) {
    val direct = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(android.net.Uri.fromParts("package", context.packageName, null))
    if (runCatching { context.startActivity(direct) }.isFailure) {
        runCatching { context.startActivity(fallback) }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, heightDp = 900)
@Composable
private fun NotificationSettingsPreview() {
    ArtistantTheme {
        NotificationSettingsContent(
            state = NotificationSettingsUiState(systemPermissionGranted = true),
            onBack = {},
            onToggle = { _, _ -> },
            onOpenSystemSettings = {},
        )
    }
}
