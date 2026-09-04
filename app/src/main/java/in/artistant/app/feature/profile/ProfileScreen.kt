package `in`.artistant.app.feature.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.artistant.app.data.repository.ExportResult
import `in`.artistant.app.designsystem.component.Avatar
import `in`.artistant.app.designsystem.component.HRule
import `in`.artistant.app.designsystem.component.RevealOnAppear
import `in`.artistant.app.designsystem.component.ScreenTitleBar
import `in`.artistant.app.feature.booking.FunnelHeader
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
    /**
     * Deliberately has NO default, unlike the rest of these. It is the only way
     * to reach [BlockedAccountsScreen], and that screen is the only way to undo
     * a block — blocking removes the conversation the in-chat Unblock lives in.
     * A default would let a new host silently ship the app without an exit from
     * a safety action; the compiler asking the question is the point.
     */
    onBlockedAccounts: () -> Unit,
    /**
     * Also without a default, for the same reason. The "Privacy" row used to hand off to the
     * hosted policy in a browser, which left the app's own privacy screen with no entry point
     * anywhere in either tab graph — registered, routable and unreachable. A default would let
     * the next host reintroduce exactly that.
     */
    onPrivacy: () -> Unit,
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
        val failure = when (export) {
            is ExportResult.Inline -> handOff(
                context,
                Intent.createChooser(exportShareIntent(export.json), "Share export"),
                "Couldn't open the share sheet — no app on this device can take the export.",
            )
            is ExportResult.SignedUrl -> handOff(
                context,
                exportViewIntent(export.url),
                "Couldn't open a browser for the export link on this device.",
            )
        }
        failure?.let(viewModel::reportActionError)
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
                    color = colors.accentInk,
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
                        color = colors.accentInk,
                        modifier = Modifier.clickable { viewModel.refresh() },
                    )
                }
            }
            else -> {
                // The title band is OUTSIDE the scroll, because that is what it
                // is on iOS: an inline navigation title pinned under the status
                // bar, not a piece of content that scrolls away with the hero.
                // The client reaches this as a tab root ("Profile"); the artist
                // pushes it from their press kit, so they get the back control
                // and the screen calls itself "Account" — the same split, and
                // the same two words, the iOS build uses.
                Column(Modifier.fillMaxSize()) {
                    if (onBack != null) {
                        FunnelHeader(title = "Account", onBack = onBack)
                    } else {
                        ScreenTitleBar("Profile")
                    }
                    RevealOnAppear {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                    ) {

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
                            // The account's own vintage, not today's year — see
                            // ProfileUiState.vintageYear.
                            val year = state.vintageYear
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
                            // Name over subtitle, nothing between them. The
                            // username used to sit here, but neither iOS identity
                            // hero renders one — the handle belongs to the public
                            // press kit, not to the private account page — and a
                            // third line squeezed the pair into one dense block.
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(space.sm),
                            ) {
                                Text(state.displayName, style = AppTheme.type.displayTitle, color = colors.ink)
                                Text(state.subtitle, style = AppTheme.type.footnote, color = colors.ink2)
                            }
                        }

                        // The stat triple is fenced by hairlines top AND bottom —
                        // the rule above is what separates the counters from the
                        // identity block, and without it the numbers read as part
                        // of the hero rather than as their own band.
                        if (state.role == AppRole.Client && onArtistList != null) {
                            HRule()
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

                            // ROW ORDER IS THE iOS ORDER, and it is not arbitrary:
                            // identity (email) → the role's own rows → the
                            // preference rows (notifications, privacy, export) →
                            // calendar sync → help → the two destructive exits.
                            // Calendar sync used to lead the list, which put a
                            // toggle for an optional device mirror above the
                            // user's own account details.
                            Column {
                                HRule()
                                // Read-only identity, not an action — no chevron,
                                // not clickable. Masked at render; see maskEmail.
                                state.maskedEmail?.let { masked ->
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = space.lg),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text("Email", style = AppTheme.type.callout, color = colors.ink)
                                        Text(masked, style = AppTheme.type.monoSmall, color = colors.ink3)
                                    }
                                    HRule()
                                }
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
                                SettingsRow("Notifications") {
                                    handOff(
                                        context,
                                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                        },
                                        "Couldn't open notification settings on this device.",
                                    )?.let(viewModel::reportActionError)
                                }
                                HRule()
                                // The in-app privacy screen (design 62), not the hosted policy:
                                // the switch it carries is a setting, and a settings row that
                                // leaves the app for a document is not where a setting lives.
                                // The policy is still one tap further in, from that screen's own
                                // "Privacy policy" row, which opens the viewer that links the
                                // authoritative online copy.
                                SettingsRow("Privacy", onClick = onPrivacy)
                                HRule()
                                // Sits next to Privacy because it belongs to the
                                // same question — who can see me and who I've
                                // shut out — and ABOVE the export/calendar rows
                                // so the way back from a block is a settings row
                                // people scroll past, not one they hunt for.
                                SettingsRow("Blocked accounts", onClick = onBlockedAccounts)
                                HRule()
                                SettingsRow("Export my data", working = state.isExporting, onClick = viewModel::exportData)
                                HRule()
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
                                SettingsRow("Get help", onClick = viewModel::showHelp)
                                HRule()
                                SettingsRow("Sign out", tint = colors.warm, onClick = viewModel::showSignOutConfirm)
                                HRule()
                                SettingsRow("Delete account", tint = colors.hot, onClick = viewModel::showDeleteConfirm)
                                HRule()
                            }
                        }

                        // Both lines are TAP-TO-DISMISS, the same affordance
                        // BlockedAccountsScreen gives its action error. Nothing
                        // else clears either one: "Calendar permission denied —
                        // enable it in system Settings." used to sit under the
                        // settings list for the life of the ViewModel, long after
                        // the user had granted the permission and watched the
                        // sync work.
                        state.actionMessage?.let { msg ->
                            Text(
                                msg,
                                style = AppTheme.type.footnote,
                                color = colors.ink2,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = viewModel::clearActionFeedback)
                                    .padding(horizontal = space.xl, vertical = space.sm),
                            )
                        }
                        // Not while the delete sheet is up: that dialog renders
                        // the very same string inside itself, so the failed
                        // delete was being stated twice at once.
                        if (!state.showDeleteConfirm) {
                            state.actionError?.let { msg ->
                                Text(
                                    msg,
                                    style = AppTheme.type.footnote,
                                    color = colors.hot,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(onClick = viewModel::clearActionFeedback)
                                        .padding(horizontal = space.xl, vertical = space.sm),
                                )
                            }
                        }

                        Spacer(Modifier.height(size.listTailroom))
                    }
                    }
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
                        CircularProgressIndicator(
                            Modifier.size(size.iconMd),
                            strokeWidth = size.stroke,
                            color = colors.accentInk,
                        )
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
            // No indication: this is a scrim, not a button, and Material's default
            // ripple on a full-screen tap surface expands across the whole window.
            val scrimInteraction = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(colors.bg.copy(alpha = 0.72f))
                    .clickable(
                        interactionSource = scrimInteraction,
                        indication = null,
                        onClick = viewModel::dismissHelp,
                    ),
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

/**
 * The three counters under the identity hero. [bookings] and [completed] are
 * nullable because the read that feeds them is best-effort — see
 * [profileStatValue], which is what turns "we don't know" into an em dash rather
 * than into a zero the account never earned.
 */
@Composable
private fun ProfileStatsRow(
    bookings: Int?,
    saved: Int,
    completed: Int?,
    onClick: (ArtistListKind) -> Unit,
) {
    val space = AppTheme.dimens.space
    // IntrinsicSize.Min so the two dividers can size themselves to the row
    // rather than to a hardcoded 40dp. The old fixed height was right for one
    // type ramp and wrong for every other — it would not follow the system font
    // scale, so at large text the rules stopped short of the labels they divide.
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(vertical = space.xl),
    ) {
        StatCol("Bookings", bookings, Modifier.weight(1f)) {
            onClick(ArtistListKind.Bookings)
        }
        StatDivider()
        StatCol("Saved", saved, Modifier.weight(1f)) {
            onClick(ArtistListKind.Saved)
        }
        StatDivider()
        StatCol("Completed", completed, Modifier.weight(1f)) {
            onClick(ArtistListKind.Completed)
        }
    }
}

/**
 * The rule between two stat columns. Full row height less a small inset at each
 * end, so it reads as a separator between the columns rather than as a tick mark
 * floating beside them.
 */
@Composable
private fun StatDivider() {
    Box(
        Modifier
            .fillMaxHeight()
            .padding(vertical = AppTheme.dimens.space.sm)
            .width(AppTheme.dimens.size.hairline)
            .background(AppTheme.colors.lineSoft),
    )
}

@Composable
private fun StatCol(
    title: String,
    value: Int?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = AppTheme.colors
    Column(
        modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppTheme.dimens.space.xs),
    ) {
        Text(profileStatValue(value), style = AppTheme.type.monoCount, color = colors.ink)
        Text(
            title.uppercase(),
            style = AppTheme.type.statLabel,
            color = colors.ink3,
        )
    }
}

/**
 * Hand an intent to the system, or return the line to show when nothing on the
 * device can take it.
 *
 * `startActivity` throws ActivityNotFoundException when no activity matches, on
 * the main thread, straight out of a click handler — which is an app crash from
 * tapping a settings row. Two of these are genuinely unresolvable in the field:
 * `ACTION_APP_NOTIFICATION_SETTINGS` is not on every OEM/Go build, and an
 * `ACTION_VIEW` on an https URL needs a browser, which a locked-down or
 * work-profile device may not offer. Same call BookingDetailScreen makes for its
 * maps/dial/share handoffs, and ArtistProfileScreen for its share sheet: a row
 * that can't open is a message, not a crash.
 */
private fun handOff(context: Context, intent: Intent, failure: String): String? =
    runCatching { context.startActivity(intent); null }.getOrElse { failure }

/**
 * The calendar-sync control: a plain switch row, with the write target revealed
 * underneath only once it is on.
 *
 * The row used to carry a permanent second line — "Mirrors confirmed bookings
 * onto this device." off, "Writing to <calendar>" on — which made it the only
 * two-line row in the list and broke the settings rhythm every other row keeps.
 * iOS explains nothing in the off state (the label already says what it does)
 * and surfaces the destination as its OWN sub-row once there is a destination
 * to name, so that is what this does now: the label answers "what", and the
 * target answers "where", but only when "where" exists.
 *
 * The picker still lists every writable calendar when there is more than one,
 * because choosing a Google-account calendar is how "sync to Google Calendar"
 * works with no Google API — same as iOS.
 */
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
    Column(Modifier.fillMaxWidth()) {
        // `sm`, not the `lg` every text-only row uses. Material's Switch expands
        // itself to the 48dp minimum interactive size, so it already carries
        // 8dp of slop above and below its 32dp track. Padding it by a full `lg`
        // on top of that double-counts the breathing room and made this the
        // tallest row in the list by 16dp — a toggle sitting visibly lower than
        // the rules that bracket it. Total here lands within a couple of units
        // of the iOS row, whose own switch is shorter to begin with.
        Row(
            Modifier.fillMaxWidth().padding(vertical = space.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Sync gigs to calendar",
                style = AppTheme.type.callout,
                color = colors.ink,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
        if (enabled) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = space.lg),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Calendar", style = AppTheme.type.footnote, color = colors.ink3)
                Text(calendarTitle, style = AppTheme.type.footnote, color = colors.ink)
            }
            if (calendars.size > 1) {
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
                Spacer(Modifier.height(space.sm))
            }
        }
    }
}

/**
 * One settings row.
 *
 * The chevron is NOT decoration — it means "this opens something". Sign out and
 * Delete account are terminal actions that raise a confirmation in place, so
 * they get no chevron; drawing one on them promised a screen that never comes.
 * The test is the tint: a row rendered in a status colour (warm/hot) is
 * destructive by definition, which is why the condition reads off [tint] rather
 * than asking each call site to repeat itself.
 */
@Composable
private fun SettingsRow(
    title: String,
    tint: androidx.compose.ui.graphics.Color = AppTheme.colors.ink,
    working: Boolean = false,
    onClick: () -> Unit,
) {
    val space = AppTheme.dimens.space
    val navigates = tint == AppTheme.colors.ink
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
            CircularProgressIndicator(
                Modifier.size(AppTheme.dimens.size.iconMd),
                strokeWidth = AppTheme.dimens.size.stroke,
                color = AppTheme.colors.accentInk,
            )
        } else if (navigates) {
            // Sized, not left at Material's 24dp default. A settings row is
            // 16 + content + 16, so an unsized chevron — taller than the 15sp
            // label beside it — was the thing SETTING the row height, and every
            // row in the list came out 4.4 units taller than the reference's.
            // The reference draws this glyph at footnote weight, i.e. smaller
            // than the label, never larger.
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = AppTheme.colors.ink3,
                modifier = Modifier.size(AppTheme.dimens.size.iconMd),
            )
        }
    }
}
