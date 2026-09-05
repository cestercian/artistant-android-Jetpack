package `in`.artistant.app.feature.profile

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.designsystem.component.BackHeader
import `in`.artistant.app.designsystem.component.EyebrowLabel
import `in`.artistant.app.designsystem.component.ListRow
import `in`.artistant.app.designsystem.component.SwitchRow
import `in`.artistant.app.designsystem.component.hairlineBottom
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.ArtistantTheme
import `in`.artistant.app.designsystem.theme.reduceMotion
import `in`.artistant.app.platform.preferences.AccessibilityPreferences
import `in`.artistant.app.platform.preferences.AccessibilitySettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The two switches, plus the one thing that can go wrong on a screen with no network calls.
 *
 * [actionError] is a system handoff this device could not complete: both non-switch rows push
 * into Android's own settings, and neither `ACTION_DISPLAY_SETTINGS` nor
 * `ACTION_ACCESSIBILITY_SETTINGS` is guaranteed to resolve on every OEM or Go build.
 */
data class AccessibilityUiState(
    val settings: AccessibilitySettings = AccessibilitySettings(),
    val actionError: String? = null,
)

@HiltViewModel
class AccessibilityViewModel @Inject constructor(
    private val prefs: AccessibilityPreferences,
) : ViewModel() {
    private val _state = MutableStateFlow(AccessibilityUiState())
    val state: StateFlow<AccessibilityUiState> = _state

    init {
        viewModelScope.launch { prefs.all.collect { s -> _state.update { it.copy(settings = s) } } }
    }

    /** State first, then persist — see `NotificationSettingsViewModel.set`. */
    fun setAlwaysShowLabels(enabled: Boolean) {
        _state.update { it.copy(settings = it.settings.copy(alwaysShowLabels = enabled)) }
        viewModelScope.launch { runCatching { prefs.setAlwaysShowLabels(enabled) } }
    }

    fun setAutoplayVideos(enabled: Boolean) {
        _state.update { it.copy(settings = it.settings.copy(autoplayVideos = enabled)) }
        viewModelScope.launch { runCatching { prefs.setAutoplayVideos(enabled) } }
    }

    /**
     * A settings screen this device could not open.
     *
     * Reported rather than swallowed: a row that does nothing on tap, twice, is
     * indistinguishable from a broken app — and this is the accessibility screen, where the
     * two rows it happens to are the ones pointing at the settings that actually matter.
     */
    fun reportActionError(message: String) = _state.update { it.copy(actionError = message) }

    fun clearActionError() = _state.update { it.copy(actionError = null) }
}

/**
 * Design screen 129 — **"Not an afterthought"**.
 *
 * The design draws six controls. On Android **four of them are not the app's to own**, and the
 * honest port states each one and points at the system screen that does own it rather than
 * shipping a switch that silently fails:
 *
 * - **Text size** is a slider on iOS ("Follows iOS Dynamic Type"). Android has no supported
 *   per-app text-size API. The whole type ramp is in `sp`, so the system font scale already
 *   reaches every screen — which is the thing the slider was for. Stated, with a route to
 *   Android's display settings.
 * - **Reduce motion** is already honoured: `LocalReduceMotion` reads
 *   `Settings.Global.ANIMATOR_DURATION_SCALE`, and every animation in the app goes through
 *   `MotionSpecs`. The row shows what the system currently says, live, and links to
 *   accessibility settings. An app-level override would need a second source of truth for one
 *   flag and would silently disagree with the system the first time either changed.
 * - **Bold text** and **higher contrast** are OS-wide rendering settings on Android
 *   (`font_weight_adjustment`, high-contrast text). The platform applies them to this app
 *   without being asked.
 *
 * That leaves two real controls, both app-side, both persisted — see
 * [AccessibilityPreferences]. The one accent on this screen belongs to their switches.
 */
@Composable
fun AccessibilityScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AccessibilityViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    AccessibilityContent(
        state = state,
        systemReduceMotion = AppTheme.reduceMotion,
        onBack = onBack,
        onAlwaysShowLabels = viewModel::setAlwaysShowLabels,
        onAutoplayVideos = viewModel::setAutoplayVideos,
        onOpenTextSize = {
            openSystemSettings(
                context,
                Settings.ACTION_DISPLAY_SETTINGS,
                "This device has no display settings screen to open. Text size lives in " +
                    "Settings › Display.",
            )?.let(viewModel::reportActionError)
        },
        onOpenMotion = {
            openSystemSettings(
                context,
                Settings.ACTION_ACCESSIBILITY_SETTINGS,
                "This device has no accessibility settings screen to open. Reduce motion " +
                    "lives in Settings › Accessibility.",
            )?.let(viewModel::reportActionError)
        },
        onDismissError = viewModel::clearActionError,
        modifier = modifier,
    )
}

@Composable
private fun AccessibilityContent(
    state: AccessibilityUiState,
    systemReduceMotion: Boolean,
    onBack: () -> Unit,
    onAlwaysShowLabels: (Boolean) -> Unit,
    onAutoplayVideos: (Boolean) -> Unit,
    onOpenTextSize: () -> Unit,
    onOpenMotion: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens

    AccountScaffold(
        modifier = modifier.semantics { testTag = "screen.accessibility" },
        header = { BackHeader(title = "Accessibility", onBack = onBack) },
    ) {
        AccountGap()
        EyebrowLabel("Text size", color = colors.ink4)
        Spacer(Modifier.height(dimens.space.sm))
        ListRow(
            title = "Follows your system text size",
            subtitle = "Every screen reflows at the largest size without truncating a price " +
                "or a date.",
            onClick = onOpenTextSize,
            modifier = Modifier.semantics { testTag = "a11y.textSize" },
            showHairline = false,
        )

        AccountGap()
        EyebrowLabel("Display", color = colors.ink4)
        Spacer(Modifier.height(dimens.space.sm))
        ListRow(
            title = "Reduce motion",
            subtitle = if (systemReduceMotion) {
                "On in your system settings — Artistant cross-fades instead of sliding."
            } else {
                "Off in your system settings. Turn it on there and Artistant follows."
            },
            onClick = onOpenMotion,
            modifier = Modifier.semantics { testTag = "a11y.reduceMotion" },
        )
        AccessibilityFact(
            title = "Bold text and higher contrast",
            detail = "Android applies both across every app, including this one. There's " +
                "nothing to switch on here.",
        )
        SwitchRow(
            title = "Always show labels",
            subtitle = "Adds text under the tab-bar icons",
            checked = state.settings.alwaysShowLabels,
            onCheckedChange = onAlwaysShowLabels,
            showHairline = false,
            modifier = Modifier.semantics { testTag = "a11y.alwaysShowLabels" },
        )

        AccountGap()
        EyebrowLabel("Audio and video", color = colors.ink4)
        Spacer(Modifier.height(dimens.space.sm))
        SwitchRow(
            title = "Autoplay artist videos",
            subtitle = "Off means tap to play, always muted first",
            checked = state.settings.autoplayVideos,
            onCheckedChange = onAutoplayVideos,
            showHairline = false,
            modifier = Modifier.semantics { testTag = "a11y.autoplay" },
        )

        state.actionError?.let { message ->
            AccountGap()
            AccountFeedbackLine(message, colors.danger, onDismissError, "a11y.actionError")
        }

        AccountGap(2)
        Text(
            "Nothing in this version of Artistant starts playing on its own — samples and " +
                "videos wait for a tap. This switch is what an autoplaying screen would read " +
                "if one ever ships. Both switches are saved on this device and stay put when " +
                "you sign out: they describe the phone, not the account.",
            style = AppTheme.type.caption,
            color = colors.ink4,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(dimens.size.listTailroom))
    }
}

/**
 * A row that reads like the rows around it and carries no control.
 *
 * Same title/detail typography and the same hairline as [ListRow] and [SwitchRow], so it sits
 * IN the list rather than under it — but with nothing on the right, because there is nothing to
 * tap. The alternative, a disabled switch, states the same fact and invites the tap anyway.
 * Same pattern (and same reasoning) as `PrivacyScreen`'s city fact.
 */
@Composable
private fun AccessibilityFact(title: String, detail: String) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Column(
        Modifier
            .fillMaxWidth()
            .hairlineBottom()
            .padding(vertical = dimens.space.md)
            .semantics(mergeDescendants = true) { testTag = "a11y.systemFact" },
    ) {
        Text(
            title,
            style = AppTheme.type.rowTitle.copy(fontSize = AppTheme.type.body.fontSize),
            color = colors.ink,
        )
        Spacer(Modifier.height(dimens.space.xs))
        Text(detail, style = AppTheme.type.caption, color = colors.ink4)
    }
}

/**
 * Open a system settings screen, or return the line to show when this device has none.
 *
 * `startActivity` throws ActivityNotFoundException on the main thread straight out of a click
 * handler, and neither of these actions is guaranteed on every OEM/Go build. A crash from
 * tapping a settings row is a bug — but so is swallowing it: the row then does nothing at all,
 * twice, which reads as a broken app, and it is these two rows that point at the settings this
 * screen is actually about. So the failure is caught AND said out loud, with where to go
 * instead.
 */
private fun openSystemSettings(context: Context, action: String, failure: String): String? =
    runCatching { context.startActivity(Intent(action)); null }.getOrElse { failure }

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, heightDp = 800)
@Composable
private fun AccessibilityPreview() {
    ArtistantTheme {
        AccessibilityContent(
            state = AccessibilityUiState(AccessibilitySettings(alwaysShowLabels = true)),
            systemReduceMotion = false,
            onBack = {},
            onAlwaysShowLabels = {},
            onAutoplayVideos = {},
            onOpenTextSize = {},
            onOpenMotion = {},
            onDismissError = {},
        )
    }
}
