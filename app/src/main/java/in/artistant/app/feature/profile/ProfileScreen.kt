package `in`.artistant.app.feature.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
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
import `in`.artistant.app.designsystem.component.Avatar
import `in`.artistant.app.designsystem.component.HRule
import `in`.artistant.app.designsystem.theme.AppRole
import `in`.artistant.app.designsystem.theme.AppTheme

/**
 * Signed-in account hub — port of iOS `ProfileView` (M6 slice).
 *
 * Identity header + settings rows: sign out, delete account, data export,
 * privacy/help links, artist availability, and calendar sync toggle.
 */
@Composable
fun ProfileScreen(
    onNavigateToPaywall: () -> Unit = {},
    onManageAvailability: (() -> Unit)? = null,
    onArtistList: ((ArtistListKind) -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val size = AppTheme.dimens.size
    val context = LocalContext.current

    val calendarPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val ok = grants[Manifest.permission.READ_CALENDAR] == true &&
            grants[Manifest.permission.WRITE_CALENDAR] == true
        viewModel.onCalendarPermissionResult(ok)
    }

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
        // NOT an opaque `background(colors.bg)`: Profile is the one tab that
        // carries the role-tinted ambient wash behind its header, and an opaque
        // fill here would paint straight over the scaffold's. The scaffold owns
        // the wash rather than this screen so it spans the whole window —
        // including the strip behind the floating tab bar, which this pane is
        // inset out of. Every other tab keeps its flat fill; the glow is Profile's
        // alone, and spraying it everywhere would spend the accent on nothing.
        modifier = modifier.fillMaxSize(),
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
                        Avatar(
                            name = state.displayName,
                            size = size.avatarXl,
                            ring = true,
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(state.displayName, style = AppTheme.type.displayTitle, color = colors.ink)
                            state.handleLabel?.let {
                                Text(it, style = AppTheme.type.monoSmall, color = colors.ink3)
                            }
                            Text(state.subtitle, style = AppTheme.type.footnote, color = colors.ink2)
                        }
                    }

                    if (state.role == AppRole.Client && onArtistList != null) {
                        ProfileStatsRow(
                            bookings = state.bookingsCount,
                            saved = state.savedCount,
                            completed = state.completedCount,
                            onClick = onArtistList,
                        )
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
                                SettingsRow(
                                    "Manage availability",
                                    onClick = {
                                        onManageAvailability?.invoke()
                                            ?: viewModel.manageAvailabilityMissingNav()
                                    },
                                )
                                HRule()
                            }
                            CalendarSyncRow(
                                enabled = state.calendarSyncEnabled,
                                calendarTitle = state.calendarTitle,
                                calendars = state.calendars,
                                onToggle = { on ->
                                    if (on && !state.calendarHasPermission) {
                                        calendarPermission.launch(
                                            arrayOf(
                                                Manifest.permission.READ_CALENDAR,
                                                Manifest.permission.WRITE_CALENDAR,
                                            ),
                                        )
                                    } else {
                                        viewModel.setCalendarSyncEnabled(on)
                                    }
                                },
                                onSelectCalendar = viewModel::selectCalendar,
                            )
                            HRule()
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
                            SettingsRow("Get help", onClick = viewModel::showHelp)
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
            shape = RoundedCornerShape(AppTheme.dimens.radii.xxl),
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
            shape = RoundedCornerShape(AppTheme.dimens.radii.xxl),
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

    if (state.showHelp) {
        Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(colors.bg.copy(alpha = 0.72f))
                    .clickable(onClick = viewModel::dismissHelp),
            )
            HelpFeedbackSheet(
                sending = state.feedbackSending,
                status = state.feedbackStatus,
                statusOk = state.feedbackOk,
                onSubmit = viewModel::submitFeedback,
                onDismiss = viewModel::dismissHelp,
            )
        }
    }
}

@Composable
private fun ProfileStatsRow(
    bookings: Int,
    saved: Int,
    completed: Int,
    onClick: (ArtistListKind) -> Unit,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = space.xl),
    ) {
        StatCol("Bookings", bookings, Modifier.weight(1f)) {
            onClick(ArtistListKind.Bookings)
        }
        Box(
            Modifier
                .width(1.dp)
                .height(40.dp)
                .align(Alignment.CenterVertically)
                .background(colors.lineSoft),
        )
        StatCol("Saved", saved, Modifier.weight(1f)) {
            onClick(ArtistListKind.Saved)
        }
        Box(
            Modifier
                .width(1.dp)
                .height(40.dp)
                .align(Alignment.CenterVertically)
                .background(colors.lineSoft),
        )
        StatCol("Completed", completed, Modifier.weight(1f)) {
            onClick(ArtistListKind.Completed)
        }
    }
}

@Composable
private fun StatCol(
    title: String,
    value: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = AppTheme.colors
    Column(
        modifier
            .clickable(onClick = onClick)
            .padding(vertical = AppTheme.dimens.space.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("$value", style = AppTheme.type.monoLarge, color = colors.ink)
        Text(
            title.uppercase(),
            style = AppTheme.type.caption,
            color = colors.ink3,
        )
    }
}

@Composable
private fun CalendarSyncRow(
    enabled: Boolean,
    calendarTitle: String = "Artistant",
    calendars: List<`in`.artistant.app.platform.calendar.CalendarSyncService.CalendarOption> = emptyList(),
    onToggle: (Boolean) -> Unit,
    onSelectCalendar: (Long) -> Unit = {},
) {
    val space = AppTheme.dimens.space
    val colors = AppTheme.colors
    Column(Modifier.fillMaxWidth().padding(vertical = space.md)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Sync gigs to calendar", style = AppTheme.type.callout, color = colors.ink)
                Text(
                    if (enabled) "Writing to $calendarTitle" else "Mirrors confirmed bookings onto this device.",
                    style = AppTheme.type.footnote,
                    color = colors.ink3,
                )
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
        if (enabled && calendars.size > 1) {
            Spacer(Modifier.height(space.sm))
            calendars.forEach { option ->
                Text(
                    option.title,
                    style = AppTheme.type.footnote,
                    color = if (option.title == calendarTitle) colors.brand else colors.ink2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectCalendar(option.id) }
                        .padding(vertical = space.xs),
                )
            }
        }
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
