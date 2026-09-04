package `in`.artistant.app.feature.signup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.designsystem.component.Banner
import `in`.artistant.app.designsystem.component.BannerTone
import `in`.artistant.app.designsystem.component.ListRow
import `in`.artistant.app.designsystem.component.hairlineBottom
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.ArtistantTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What screen 62 shows. Two switches and nothing else that can fail. */
data class PrivacyUiState(
    val readReceipts: Boolean = true,
    val showCity: Boolean = true,
)

/** Reads and writes [PrivacyPreferences]. No network, so no loading or failed state. */
@HiltViewModel
class PrivacyViewModel @Inject constructor(
    private val prefs: PrivacyPreferences,
) : ViewModel() {
    private val _state = MutableStateFlow(PrivacyUiState())
    val state: StateFlow<PrivacyUiState> = _state

    init {
        viewModelScope.launch {
            combine(prefs.readReceipts, prefs.showCity) { receipts, city ->
                PrivacyUiState(readReceipts = receipts, showCity = city)
            }.collect { _state.value = it }
        }
    }

    /**
     * State first, then persist.
     *
     * A switch that waits for DataStore to echo the write back before it moves is a switch
     * that visibly lags the thumb, and `DataStore.edit` throws `IOException` on a preferences
     * file it cannot write — unguarded inside `viewModelScope.launch` that reaches the
     * thread's default handler and takes the app down. Same shape as
     * [SignupViewModel.agreeCommunity], for the same two reasons.
     */
    fun setReadReceipts(enabled: Boolean) {
        _state.update { it.copy(readReceipts = enabled) }
        viewModelScope.launch { runCatching { prefs.setReadReceipts(enabled) } }
    }

    fun setShowCity(enabled: Boolean) {
        _state.update { it.copy(showCity = enabled) }
        viewModelScope.launch { runCatching { prefs.setShowCity(enabled) } }
    }

}

/**
 * Screen 62 — **two toggles, both explained**.
 *
 * The design's note is about the subtitles: "each switch says what the other side sees, so
 * nobody has to guess at the consequence." A privacy control whose label is a noun ("Read
 * receipts") makes the user model the system; one whose label is the consequence ("People you
 * chat with can see when you've read their messages") does not.
 *
 * **Where the settings live, and why it is stated.** Neither switch has a column in the
 * canonical schema, so both are device preferences — see [PrivacyPreferences] for the
 * migration evidence. The note under them says so, because a settings screen that silently
 * fails to follow you to a new phone is a settings screen that lied.
 *
 * The footer is the one line on this screen that is NOT a setting, and it says so out loud:
 * the phone-number rule is how the platform works, not something anyone can switch off. That
 * is the same sentence the sign-in screen's legal line carries, deliberately.
 */
@Composable
fun PrivacyScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenLegal: (LegalDoc) -> Unit = {},
    onDataExport: (() -> Unit)? = null,
    viewModel: PrivacyViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PrivacyContent(
        state = state,
        onBack = onBack,
        onReadReceipts = viewModel::setReadReceipts,
        onShowCity = viewModel::setShowCity,
        onOpenLegal = onOpenLegal,
        onDataExport = onDataExport,
        modifier = modifier,
    )
}

@Composable
private fun PrivacyContent(
    state: PrivacyUiState,
    onBack: () -> Unit,
    onReadReceipts: (Boolean) -> Unit,
    onShowCity: (Boolean) -> Unit,
    onOpenLegal: (LegalDoc) -> Unit,
    onDataExport: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val space = dimens.space

    SignupScaffold(
        modifier = modifier.semantics { testTag = "screen.privacy" },
        header = {
            SignupHeader(
                onBack = onBack,
                title = "Privacy",
                subtitle = "Controls what others see",
                titleAtStart = true,
            )
        },
    ) {
        Spacer(Modifier.height(space.sm))
        PrivacyToggle(
            title = "Show when I've read messages",
            detail = "People you chat with can see when you've read their messages.",
            checked = state.readReceipts,
            onChange = onReadReceipts,
            testTag = "privacy.readReceipts",
        )
        PrivacyToggle(
            title = "Show my city on my profile",
            detail = "Your city appears on your profile header. Turn off to hide it.",
            checked = state.showCity,
            onChange = onShowCity,
            testTag = "privacy.showCity",
        )

        ListRow(
            title = "Privacy policy",
            onClick = { onOpenLegal(LegalDoc.Privacy) },
            trailing = {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = colors.ink4,
                    modifier = Modifier.size(dimens.size.iconLg),
                )
            },
            modifier = Modifier.semantics { testTag = "privacy.policy" },
        )
        // Data export is a real screen owned by the account section. Offered only when the
        // host passes a destination for it, because a settings row that pushes nothing is
        // the "failing silently on tap" the redesign's notes keep ruling out.
        if (onDataExport != null) {
            ListRow(
                title = "Data export",
                subtitle = "Download everything we hold",
                onClick = onDataExport,
                trailing = {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = colors.ink4,
                        modifier = Modifier.size(dimens.size.iconLg),
                    )
                },
                modifier = Modifier.semantics { testTag = "privacy.export" },
            )
        }

        Spacer(Modifier.height(space.lg))
        Banner(
            title = "Both switches are saved on this device. Artistant has no server-side " +
                "privacy setting, so they don't follow you to another phone.",
            tone = BannerTone.Note,
        )

        Spacer(Modifier.height(space.lg))
        Text(
            "Your phone number is never shown to an artist before a booking is confirmed. " +
                "That isn't a setting — it's how the platform works.",
            style = AppTheme.type.caption,
            color = colors.ink4,
        )
        Spacer(Modifier.height(space.xl))
    }
}

/**
 * One switch row.
 *
 * Material's `Switch` for the behaviour — the thumb drag, the a11y role, the state
 * announcement — repainted in the tokens. REDESIGN_2026-09 §7: M3 is the substrate, never the
 * look, and never a default M3 colour.
 */
@Composable
private fun PrivacyToggle(
    title: String,
    detail: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    testTag: String,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .hairlineBottom()
            .padding(vertical = dimens.space.lg)
            .semantics(mergeDescendants = true) { this.testTag = testTag },
        horizontalArrangement = Arrangement.spacedBy(dimens.space.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = AppTheme.type.rowTitle.copy(fontSize = AppTheme.type.body.fontSize),
                color = colors.ink,
            )
            Spacer(Modifier.height(dimens.space.xs))
            Text(detail, style = AppTheme.type.caption, color = colors.ink4)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.surface,
                checkedTrackColor = colors.accent,
                checkedBorderColor = colors.accent,
                uncheckedThumbColor = colors.surface,
                uncheckedTrackColor = colors.hairline,
                uncheckedBorderColor = colors.lineStrong,
            ),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, heightDp = 760)
@Composable
private fun PrivacyPreview() {
    ArtistantTheme {
        PrivacyContent(
            state = PrivacyUiState(),
            onBack = {},
            onReadReceipts = {},
            onShowCity = {},
            onOpenLegal = {},
            onDataExport = {},
        )
    }
}
