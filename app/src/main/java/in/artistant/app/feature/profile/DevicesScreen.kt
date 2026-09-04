package `in`.artistant.app.feature.profile

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.data.repository.AccountRepository
import `in`.artistant.app.designsystem.component.BackHeader
import `in`.artistant.app.designsystem.component.Banner
import `in`.artistant.app.designsystem.component.BannerTone
import `in`.artistant.app.designsystem.component.SecondaryButton
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.ArtistantTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Screen 128's state — one action, and what it last did. */
data class DevicesUiState(
    val working: Boolean = false,
    val signedOutOthers: Boolean = false,
    val failure: String? = null,
)

@HiltViewModel
class DevicesViewModel @Inject constructor(
    private val account: AccountRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(DevicesUiState())
    val state: StateFlow<DevicesUiState> = _state

    /**
     * Revoke every other session.
     *
     * Guarded against a second tap while the first is in flight: the call is idempotent
     * server-side, but a double-tap would produce two overlapping requests whose results race
     * to write the outcome line, and "signed out" flickering back to "failed" is a worse story
     * than either.
     */
    fun signOutOthers() = viewModelScope.launch {
        if (_state.value.working) return@launch
        _state.update { it.copy(working = true, failure = null, signedOutOthers = false) }
        runCatching { account.signOutOtherDevices() }
            .onSuccess { _state.update { it.copy(working = false, signedOutOthers = true) } }
            .onFailure { e ->
                _state.update {
                    it.copy(
                        working = false,
                        failure = e.message ?: "Couldn't sign out your other devices.",
                    )
                }
            }
    }
}

/**
 * Design screen 128 — **"Account security, visible"**.
 *
 * The design draws three device rows and a recent-activity log. **Neither exists on this
 * backend and neither is invented here.** Supabase gives a client no way to enumerate its own
 * sessions — there is no endpoint, and `auth.sessions` is in the `auth` schema, which RLS does
 * not expose to the anon/authenticated roles — and there is no activity table anywhere in the
 * 105 canonical migrations. Drawing "iPad Air · Bengaluru · 2 days ago" from nothing would be
 * the single most dangerous fabrication in the app: it is a security screen, and a fake session
 * list is one someone would act on.
 *
 * So the screen keeps the part that is REAL and, crucially, keeps the ACTION. This device is
 * described from the platform (`Build.MANUFACTURER`/`MODEL` and the OS version — facts, not
 * guesses), the absence of the list is stated in the words a person would use, and the control
 * the whole screen exists for still works: "Sign out everywhere else" revokes every other
 * session through [AccountRepository.signOutOtherDevices]. Someone who thinks their account is
 * compromised can still fix it here, which is what the design's note is about.
 */
@Composable
fun DevicesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DevicesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DevicesContent(
        state = state,
        thisDevice = thisDeviceLabel(),
        androidVersion = "Android ${Build.VERSION.RELEASE}",
        onBack = onBack,
        onSignOutOthers = viewModel::signOutOthers,
        modifier = modifier,
    )
}

@Composable
private fun DevicesContent(
    state: DevicesUiState,
    thisDevice: String,
    androidVersion: String,
    onBack: () -> Unit,
    onSignOutOthers: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens

    AccountScaffold(
        modifier = modifier.semantics { testTag = "screen.devices" },
        header = {
            BackHeader(title = "Devices", onBack = onBack, subtitle = "Where you're signed in")
        },
        footer = {
            SecondaryButton(
                text = if (state.working) "Signing out…" else "Sign out everywhere else",
                onClick = onSignOutOthers,
                fullWidth = true,
                enabled = !state.working,
                modifier = Modifier.semantics { testTag = "devices.signOutOthers" },
            )
        },
    ) {
        AccountGap()
        ThisDeviceCard(name = thisDevice, detail = "$androidVersion · signed in now")

        AccountGap()
        // The honest replacement for the design's two other device rows. Stated as a fact
        // about the platform, not as an apology, and paired with the thing that still works.
        Banner(
            title = "We can't list your other devices",
            tone = BannerTone.Note,
            detail = "Artistant's sign-in provider doesn't let an app read the list of " +
                "sessions on your account, so nothing here can name them. Signing out " +
                "everywhere else still ends every one of them.",
            modifier = Modifier.semantics { testTag = "devices.noList" },
        )

        AccountGap()
        Text(
            "If you don't think you should be signed in anywhere else, sign out everywhere " +
                "else and then change your password. Anyone still holding a session loses it " +
                "immediately.",
            style = AppTheme.type.body,
            color = colors.ink3,
            modifier = Modifier.fillMaxWidth(),
        )

        if (state.signedOutOthers) {
            AccountGap()
            Banner(
                title = "Every other session is signed out",
                tone = BannerTone.Info,
                detail = "This device stays signed in. Anywhere else has to sign in again.",
                modifier = Modifier.semantics { testTag = "devices.signedOut" },
            )
        }
        state.failure?.let { message ->
            AccountGap()
            Banner(
                title = "Couldn't sign out your other devices",
                tone = BannerTone.Failure,
                detail = message,
                actionLabel = "Try again",
                onAction = onSignOutOthers,
                modifier = Modifier.semantics { testTag = "devices.failure" },
            )
        }
        Spacer(Modifier.height(dimens.size.listTailroom))
    }
}

/** The one row the design draws that we can honestly fill: this phone, right now. */
@Composable
private fun ThisDeviceCard(name: String, detail: String) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val shape = RoundedCornerShape(dimens.radii.lg)
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.brandSoft, shape)
            .border(dimens.component.focusStroke, colors.accent, shape)
            .padding(dimens.space.lg)
            .semantics(mergeDescendants = true) { testTag = "devices.thisDevice" },
        horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(dimens.size.avatarSm)
                .background(colors.accent, RoundedCornerShape(dimens.radii.md)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Smartphone,
                contentDescription = null,
                tint = colors.onAccent,
                modifier = Modifier.size(dimens.size.iconLg),
            )
        }
        Column(Modifier.weight(1f)) {
            Text("$name · this device", style = AppTheme.type.rowTitle, color = colors.ink)
            Text(
                detail,
                style = AppTheme.type.caption,
                color = colors.ink3,
                modifier = Modifier.padding(top = dimens.space.xs / 2),
            )
        }
    }
}

/**
 * How this phone names itself — "Google Pixel 8", "Samsung SM-G991B", "Xiaomi 14".
 *
 * `Build.MODEL` alone is often a bare part number ("SM-G991B" tells nobody anything), and
 * `MANUFACTURER` alone is just a brand, so the pair is joined — EXCEPT where the model already
 * carries the brand ("Xiaomi 14"), which would otherwise render "Xiaomi Xiaomi 14". Both values
 * are platform facts; nothing here is inferred or prettified beyond the case of the first
 * letter, because this is a security screen and the name has to match what the person's other
 * devices call this one.
 */
internal fun deviceLabel(manufacturer: String, model: String): String {
    val make = manufacturer.trim()
    val name = model.trim()
    return when {
        name.isEmpty() -> make.replaceFirstChar { it.uppercase() }.ifEmpty { "This phone" }
        make.isEmpty() || name.startsWith(make, ignoreCase = true) -> name
        else -> "${make.replaceFirstChar { it.uppercase() }} $name"
    }
}

private fun thisDeviceLabel(): String = deviceLabel(Build.MANUFACTURER, Build.MODEL)

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, heightDp = 760)
@Composable
private fun DevicesPreview() {
    ArtistantTheme {
        DevicesContent(
            state = DevicesUiState(),
            thisDevice = "Pixel 8",
            androidVersion = "Android 15",
            onBack = {},
            onSignOutOthers = {},
        )
    }
}
