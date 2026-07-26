package `in`.artistant.app.feature.profile

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.artistant.app.core.config.AppEnvironment
import `in`.artistant.app.data.repository.ExportResult
import `in`.artistant.app.designsystem.component.HRule
import `in`.artistant.app.designsystem.theme.AppRole
import `in`.artistant.app.designsystem.theme.AppTheme

/**
 * Signed-in account hub — port of iOS `ProfileView` (M6 slice).
 *
 * Identity header + settings rows: sign out, delete account, data export,
 * privacy/help links, and artist availability stub. Stats carousel and
 * calendar sync are deferred.
 */
@Composable
fun ProfileScreen(
    onNavigateToPaywall: () -> Unit = {},
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val size = AppTheme.dimens.size
    val context = LocalContext.current

    LaunchedEffect(state.pendingExport) {
        val export = state.pendingExport ?: return@LaunchedEffect
        when (export) {
            is ExportResult.Inline -> {
                context.startActivity(Intent.createChooser(exportShareIntent(export.json), "Share export"))
            }
            is ExportResult.SignedUrl -> {
                context.startActivity(exportViewIntent(export.url))
            }
        }
        viewModel.clearPendingExport()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg),
    ) {
        when {
            state.isLoading && state.profile == null -> {
                CircularProgressIndicator(
                    color = colors.brand,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            state.error != null && state.profile == null -> {
                Column(
                    Modifier.align(Alignment.Center).padding(space.xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(space.md),
                ) {
                    Text(state.error!!, style = AppTheme.type.callout, color = colors.ink2, textAlign = TextAlign.Center)
                    Text(
                        "Retry",
                        style = AppTheme.type.callout.copy(fontWeight = FontWeight.Bold),
                        color = colors.brand,
                        modifier = Modifier.clickable { viewModel.refresh() },
                    )
                }
            }
            else -> {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (onBack != null) {
                        TextButton(onClick = onBack, modifier = Modifier.padding(space.sm)) {
                            Text("Back", style = AppTheme.type.callout, color = colors.ink2)
                        }
                    }

                    // Identity header
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = space.xl)
                            .padding(top = space.xxl, bottom = space.xl),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(space.lg),
                    ) {
                        val roleLabel = when (state.role) {
                            AppRole.Client -> "CLIENT"
                            AppRole.Artist -> "ARTIST"
                        }
                        val cityLabel = state.profile?.city?.trim()?.uppercase().orEmpty()
                        val year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
                        Text(
                            if (cityLabel.isBlank()) "$roleLabel · $year" else "$roleLabel · $cityLabel · $year",
                            style = AppTheme.type.monoSmall.copy(fontWeight = FontWeight.Bold),
                            color = colors.ink3,
                        )
                        Box(
                            Modifier
                                .size(size.avatarXl)
                                .clip(CircleShape)
                                .background(colors.bgCard),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                state.displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                                style = AppTheme.type.title,
                                color = colors.brand,
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(state.displayName, style = AppTheme.type.displayTitle, color = colors.ink)
                            state.handleLabel?.let {
                                Text(it, style = AppTheme.type.monoSmall, color = colors.ink3)
                            }
                            Text(state.subtitle, style = AppTheme.type.footnote, color = colors.ink2)
                        }
                    }

                    HRule()

                    // Settings
                    Column(
                        Modifier.padding(horizontal = space.xl, vertical = space.xxl),
                        verticalArrangement = Arrangement.spacedBy(space.md),
                    ) {
                        Text("Settings", style = AppTheme.type.displaySub, color = colors.ink)

                        Column {
                            HRule()
                            if (state.subscriptionsEnabled) {
                                SettingsRow("Subscription", onClick = onNavigateToPaywall)
                                HRule()
                            }
                            if (state.role == AppRole.Artist) {
                                SettingsRow("Manage availability", onClick = viewModel::manageAvailabilityStub)
                                HRule()
                            }
                            SettingsRow("Notifications") {
                                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                })
                            }
                            HRule()
                            SettingsRow("Privacy") {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, AppEnvironment.privacyPolicyUrl.toUri()),
                                )
                            }
                            HRule()
                            SettingsRow("Export my data", working = state.isExporting, onClick = viewModel::exportData)
                            HRule()
                            SettingsRow("Get help") {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, "mailto:${AppEnvironment.supportEmail}".toUri()),
                                )
                            }
                            HRule()
                            SettingsRow("Sign out", tint = colors.warm, onClick = viewModel::showSignOutConfirm)
                            HRule()
                            SettingsRow("Delete account", tint = colors.hot, onClick = viewModel::showDeleteConfirm)
                            HRule()
                        }
                    }

                    state.actionMessage?.let { msg ->
                        Text(
                            msg,
                            style = AppTheme.type.footnote,
                            color = colors.ink2,
                            modifier = Modifier.padding(horizontal = space.xl, vertical = space.sm),
                        )
                    }
                    state.actionError?.let { msg ->
                        Text(
                            msg,
                            style = AppTheme.type.footnote,
                            color = colors.hot,
                            modifier = Modifier.padding(horizontal = space.xl, vertical = space.sm),
                        )
                    }

                    Spacer(Modifier.size(56.dp))
                }
            }
        }
    }

    if (state.showSignOutConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissSignOutConfirm,
            title = { Text("Sign out?") },
            text = {
                Text(
                    "This clears your data from this device. Your bookings and chats are safe on your account and re-sync when you sign back in.",
                    style = AppTheme.type.footnote,
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::signOut) {
                    Text("Sign out", color = colors.hot)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissSignOutConfirm) {
                    Text("Cancel")
                }
            },
        )
    }

    if (state.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = {
                if (!state.isDeleting) viewModel.dismissDeleteConfirm()
            },
            title = { Text("Delete account?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(space.sm)) {
                    Text(
                        "This permanently erases your account and personal data. This cannot be undone.",
                        style = AppTheme.type.footnote,
                    )
                    state.actionError?.let {
                        Text(it, style = AppTheme.type.footnote, color = colors.hot)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::deleteAccount,
                    enabled = !state.isDeleting,
                ) {
                    if (state.isDeleting) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Delete forever", color = colors.hot)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDeleteConfirm, enabled = !state.isDeleting) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun SettingsRow(
    title: String,
    tint: androidx.compose.ui.graphics.Color = AppTheme.colors.ink,
    working: Boolean = false,
    onClick: () -> Unit,
) {
    val space = AppTheme.dimens.space
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = !working, onClick = onClick)
            .padding(vertical = space.lg),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = AppTheme.type.callout, color = tint)
        if (working) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = AppTheme.colors.brand)
        } else {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = AppTheme.colors.ink3,
            )
        }
    }
}
