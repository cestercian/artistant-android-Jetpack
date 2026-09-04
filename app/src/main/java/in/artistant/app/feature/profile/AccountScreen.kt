package `in`.artistant.app.feature.profile

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.artistant.app.BuildConfig
import `in`.artistant.app.designsystem.component.BackHeader
import `in`.artistant.app.designsystem.component.EyebrowLabel
import `in`.artistant.app.designsystem.component.ListRow
import `in`.artistant.app.designsystem.component.SwitchRow
import `in`.artistant.app.designsystem.theme.AppRole
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.ArtistantTheme
import `in`.artistant.app.platform.calendar.CalendarSyncService

/**
 * Design screens 47 and 69 — **"One implementation, two hosts"**.
 *
 * The settings list, pushed from the client's Profile gear (26) and from the artist's press
 * kit. There is one list: the ARTIST group is INJECTED when the account is an artist, never
 * forked into a second screen. The design's own note is the reason — the forked version is how
 * the artist side ended up without a delete row at all, because nobody remembered to copy it
 * across.
 *
 * The stat band above the list changes columns with the role, because the numbers an artist
 * cares about are not the ones a host does: Gigs / Bookability / Completed against Upcoming /
 * Saved / Completed. It is the same [AccountStatBand], fed different stats.
 *
 * **What the design draws and this does differently, deliberately:**
 * - "Sync gigs to calendar" keeps its subtitle in BOTH states, not just off. The design writes
 *   "Off — needs calendar access" on one screen and "Calendar access is off — tap to enable" on
 *   the other; on Android the row also has to say WHERE it is writing once it is on, because
 *   the device can have several writable calendars and picking the Google-account one is how
 *   "sync to Google Calendar" works with no Google API.
 * - Rows the design does not draw — Notifications, Language & region, Accessibility, Devices
 *   (124 / 130 / 129 / 128), and the System section's Activity, Send feedback, What's new and
 *   Rate Artistant (123 / 64 / 137 / 138) — live here, because their screens exist and the
 *   design gives them no other entry point. §5.4 of the redesign plan requires every new screen
 *   to be reachable; the settings list is where a setting is reachable from. Activity is drawn
 *   in the client's Discover header too (the bell on 02); the artist's Studio header (09) has a
 *   "Taking gigs" pill in that slot and no bell, so for an artist this row is the only way in.
 */
@Composable
fun AccountScreen(
    onBack: () -> Unit,
    /** @see ProfileScreen.onAccount — no default, for the same reason. */
    onBlockedAccounts: () -> Unit,
    onPrivacy: () -> Unit,
    /**
     * The trust & safety centre (design 131), which `feature/messages` owns.
     *
     * Was nullable while this section compiled against a graph that had no such destination —
     * a settings row that pushes nothing is the "failing silently on tap" the redesign's notes
     * keep ruling out, so the row was omitted rather than dead. `redesign/messaging-safety`
     * registered the screen on both graphs, so the seam is closed and the row is unconditional.
     */
    onSafetyCentre: () -> Unit,
    /**
     * The help centre (design 63), owned by `feature/system`.
     *
     * This row used to raise an inline Help/Feedback sheet that this package owned. Section SH
     * replaced it with real screens — and `FeedbackScreen` files into the same `app_feedback`
     * (mig 0073) behind an outbox with a session guard, which the sheet had no notion of, so a
     * note written while signed out was filed against whoever signed in next. Two feedback
     * paths where one of them gets the `user_id` wrong is not a choice worth keeping, so the
     * sheet is gone and the row pushes SH's screen.
     */
    onHelpCentre: () -> Unit,
    /** Design 64 — the feedback form, which had no entry point on either graph until now. */
    onFeedback: () -> Unit,
    /** Design 123 — the activity log. The artist's only way in; see the class note. */
    onActivity: () -> Unit,
    /**
     * Design 137 — a REQUEST to present the release-notes sheet, not a route.
     *
     * The sheet is hosted at the root (`ArtistantNavHost`), so this row cannot push it; the
     * scaffold hands over the root-scoped `WhatsNewViewModel`'s own trigger instead.
     */
    onWhatsNew: () -> Unit,
    /** Design 138 — leaves the app for the Play listing. */
    onRateApp: () -> Unit,
    onNotifications: () -> Unit,
    onLanguage: () -> Unit,
    onAccessibility: () -> Unit,
    onDevices: () -> Unit,
    onDataExport: () -> Unit,
    onDeleteAccount: () -> Unit,
    onSubscription: () -> Unit,
    modifier: Modifier = Modifier,
    onManageAvailability: (() -> Unit)? = null,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AppTheme.colors

    val calendarPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val ok = grants[Manifest.permission.READ_CALENDAR] == true &&
            grants[Manifest.permission.WRITE_CALENDAR] == true
        viewModel.onCalendarPermissionResult(ok)
    }

    Box(modifier.fillMaxSize()) {
        AccountContent(
            state = state,
            onBack = onBack,
            onBlockedAccounts = onBlockedAccounts,
            onPrivacy = onPrivacy,
            onSafetyCentre = onSafetyCentre,
            onFeedback = onFeedback,
            onActivity = onActivity,
            onWhatsNew = onWhatsNew,
            onRateApp = onRateApp,
            onNotifications = onNotifications,
            onLanguage = onLanguage,
            onAccessibility = onAccessibility,
            onDevices = onDevices,
            onDataExport = onDataExport,
            onDeleteAccount = onDeleteAccount,
            onSubscription = onSubscription,
            onManageAvailability = {
                onManageAvailability?.invoke() ?: viewModel.manageAvailabilityMissingNav()
            },
            onHelp = onHelpCentre,
            onSignOut = viewModel::showSignOutConfirm,
            onCalendarToggle = { on ->
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
            onDismissMessage = viewModel::clearActionFeedback,
        )

        if (state.showSignOutConfirm) {
            AlertDialog(
                shape = RoundedCornerShape(AppTheme.dimens.radii.xxl),
                containerColor = colors.surface,
                onDismissRequest = viewModel::dismissSignOutConfirm,
                title = { Text("Sign out?", style = AppTheme.type.sectionTitle, color = colors.ink) },
                text = {
                    Text(
                        "This clears your data from this device. Your bookings and chats are " +
                            "safe on your account and re-sync when you sign back in.",
                        style = AppTheme.type.body,
                        color = colors.ink3,
                    )
                },
                confirmButton = {
                    TextButton(onClick = viewModel::signOut) {
                        Text("Sign out", style = AppTheme.type.rowTitle, color = colors.danger)
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissSignOutConfirm) {
                        Text("Cancel", style = AppTheme.type.rowTitle, color = colors.ink2)
                    }
                },
            )
        }

    }
}

@Composable
private fun AccountContent(
    state: ProfileUiState,
    onBack: () -> Unit,
    onBlockedAccounts: () -> Unit,
    onPrivacy: () -> Unit,
    onSafetyCentre: () -> Unit,
    onFeedback: () -> Unit,
    onActivity: () -> Unit,
    onWhatsNew: () -> Unit,
    onRateApp: () -> Unit,
    onNotifications: () -> Unit,
    onLanguage: () -> Unit,
    onAccessibility: () -> Unit,
    onDevices: () -> Unit,
    onDataExport: () -> Unit,
    onDeleteAccount: () -> Unit,
    onSubscription: () -> Unit,
    onManageAvailability: () -> Unit,
    onHelp: () -> Unit,
    onSignOut: () -> Unit,
    onCalendarToggle: (Boolean) -> Unit,
    onSelectCalendar: (Long) -> Unit,
    onDismissMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val isArtist = state.role == AppRole.Artist

    AccountScaffold(
        modifier = modifier.semantics { testTag = "screen.account" },
        header = {
            BackHeader(
                title = "Account",
                onBack = onBack,
                // The masked address, not the raw one — see maskEmail. Absent for a session
                // whose provider withheld an email, and the line is simply dropped rather
                // than showing an empty subtitle.
                subtitle = state.maskedEmail,
            )
        },
    ) {
        AccountGap()
        AccountStatBand(
            stats = if (isArtist) {
                listOf(
                    AccountStat("Gigs", accountStatValue(state.gigsCount)),
                    AccountStat("Bookability", accountStatValue(state.bookabilityScore)),
                    AccountStat("Completed", accountStatValue(state.completedCount)),
                )
            } else {
                listOf(
                    AccountStat("Upcoming", accountStatValue(state.bookingsCount)),
                    AccountStat("Saved", accountStatValue(state.savedCount)),
                    AccountStat("Completed", accountStatValue(state.completedCount)),
                )
            },
            modifier = Modifier.semantics { testTag = "account.stats" },
        )

        if (isArtist) {
            AccountGap()
            EyebrowLabel("Artist", color = colors.ink4)
            Spacer(Modifier.height(dimens.space.sm))
            ListRow(
                title = "Manage availability",
                subtitle = state.availabilitySummary,
                onClick = onManageAvailability,
                modifier = Modifier.semantics { testTag = "account.availability" },
            )
            ListRow(
                title = "Subscription",
                subtitle = state.subscriptionSubtitle,
                onClick = onSubscription,
                showHairline = false,
                modifier = Modifier.semantics { testTag = "account.subscription" },
            )
        }

        AccountGap()
        EyebrowLabel("Account", color = colors.ink4)
        Spacer(Modifier.height(dimens.space.sm))

        SwitchRow(
            title = "Sync gigs to calendar",
            subtitle = calendarSubtitle(
                enabled = state.calendarSyncEnabled,
                hasPermission = state.calendarHasPermission,
                calendarTitle = state.calendarTitle,
            ),
            checked = state.calendarSyncEnabled,
            onCheckedChange = onCalendarToggle,
            modifier = Modifier.semantics { testTag = "account.calendarSync" },
        )
        // The picker only appears when there is a choice to make. One writable calendar is not
        // a decision, and a list of one is a row that looks broken.
        if (state.calendarSyncEnabled && state.calendars.size > 1) {
            CalendarPicker(
                calendars = state.calendars,
                selectedTitle = state.calendarTitle,
                onSelect = onSelectCalendar,
            )
        }
        // The log above the setting: Activity is what arrived, Notifications is what may.
        ListRow(
            title = "Activity",
            subtitle = "Notifications received on this device",
            onClick = onActivity,
            modifier = Modifier.semantics { testTag = "account.activity" },
        )
        ListRow(title = "Notifications", onClick = onNotifications)
        ListRow(title = "Language & region", onClick = onLanguage)
        ListRow(title = "Accessibility", onClick = onAccessibility)
        ListRow(title = "Devices", onClick = onDevices)
        ListRow(
            title = "Data export",
            subtitle = "Download everything we hold",
            onClick = onDataExport,
            modifier = Modifier.semantics { testTag = "account.export" },
        )
        ListRow(title = "Privacy", onClick = onPrivacy)
        // Next to Privacy because it answers the same question — who can reach me, and what
        // happens when someone shouldn't — and above the destructive pair so the way back from
        // a block is a row people scroll past rather than hunt for.
        ListRow(title = "Trust & safety", onClick = onSafetyCentre)
        ListRow(title = "Blocked accounts", onClick = onBlockedAccounts)
        // The support tail. `Help centre` is the row the design draws (47); the three under it
        // are section SH's remaining screens, which the design gives no entry point of its own
        // and which are otherwise unreachable.
        ListRow(title = "Help centre", onClick = onHelp)
        ListRow(
            title = "Send feedback",
            subtitle = "We read everything but can't reply individually",
            onClick = onFeedback,
            modifier = Modifier.semantics { testTag = "account.feedback" },
        )
        // No chevron on either of these two: one raises a sheet in place, the other leaves the
        // app for the Play listing, and a chevron on either promises a screen that never comes.
        ListRow(
            title = "What's new",
            subtitle = "Version ${BuildConfig.VERSION_NAME}",
            onClick = onWhatsNew,
            trailing = {},
            modifier = Modifier.semantics { testTag = "account.whatsNew" },
        )
        ListRow(
            title = "Rate Artistant",
            subtitle = "Opens the Play Store listing",
            onClick = onRateApp,
            trailing = {},
            modifier = Modifier.semantics { testTag = "account.rate" },
        )
        // No chevron on either: they are terminal actions that raise a confirmation or a flow
        // in place, and a chevron on them promises a screen that never comes. `ListRow` draws
        // the chevron off `onClick`, so the trailing slot is filled with nothing instead.
        ListRow(
            title = "Sign out",
            onClick = onSignOut,
            trailing = {},
            modifier = Modifier.semantics { testTag = "account.signOut" },
        )
        ListRow(
            title = "Delete account",
            onClick = onDeleteAccount,
            destructive = true,
            trailing = {},
            showHairline = false,
            modifier = Modifier.semantics { testTag = "account.delete" },
        )

        state.actionMessage?.let { message ->
            AccountFeedbackLine(message, colors.ink3, onDismissMessage, "account.actionMessage")
        }
        state.actionError?.let { message ->
            AccountFeedbackLine(message, colors.danger, onDismissMessage, "account.actionError")
        }
        Spacer(Modifier.height(dimens.size.listTailroom))
    }
}

/** A tap-to-dismiss line under the list — the same affordance every action failure here gets. */
@Composable
private fun AccountFeedbackLine(
    message: String,
    color: androidx.compose.ui.graphics.Color,
    onDismiss: () -> Unit,
    tag: String,
) {
    Text(
        message,
        style = AppTheme.type.caption,
        color = color,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onDismiss)
            .padding(vertical = AppTheme.dimens.space.sm)
            .semantics { testTag = tag },
    )
}

/**
 * Which calendar the mirror writes to, when there is more than one to choose from.
 *
 * Indented under the switch rather than pushed to its own screen: the choice is one line per
 * option and it only exists while the toggle is on, so a destination for it would be a screen
 * that is empty most of the time.
 */
@Composable
private fun CalendarPicker(
    calendars: List<CalendarSyncService.CalendarOption>,
    selectedTitle: String,
    onSelect: (Long) -> Unit,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = dimens.space.md, bottom = dimens.space.sm),
        verticalArrangement = Arrangement.spacedBy(dimens.space.xs),
    ) {
        calendars.forEach { option ->
            val selected = option.title == selectedTitle
            Text(
                option.title,
                style = AppTheme.type.subtitle,
                color = if (selected) colors.accentInk else colors.ink3,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(option.id) }
                    .padding(vertical = dimens.space.sm),
            )
        }
    }
}

/**
 * The calendar row's second line.
 *
 * Three states, because they are three different situations and only one of them is actionable
 * by the switch alone: permission never granted, granted but off, and on (where the useful
 * fact is no longer "what does this do" but "where is it writing").
 */
internal fun calendarSubtitle(
    enabled: Boolean,
    hasPermission: Boolean,
    calendarTitle: String,
): String = when {
    enabled -> "Writing to $calendarTitle"
    !hasPermission -> "Calendar access is off — tap to enable"
    else -> "Off — confirmed bookings aren't mirrored"
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, heightDp = 900)
@Composable
private fun AccountPreview() {
    ArtistantTheme {
        AccountContent(
            state = ProfileUiState(
                isLoading = false,
                role = AppRole.Artist,
                email = "tilt@artistant.in",
                gigsCount = 128,
                bookabilityScore = 86,
                completedCount = 121,
                availabilitySummary = "Thu–Sun evenings",
            ),
            onBack = {},
            onBlockedAccounts = {},
            onPrivacy = {},
            onSafetyCentre = {},
            onFeedback = {},
            onActivity = {},
            onWhatsNew = {},
            onRateApp = {},
            onNotifications = {},
            onLanguage = {},
            onAccessibility = {},
            onDevices = {},
            onDataExport = {},
            onDeleteAccount = {},
            onSubscription = {},
            onManageAvailability = {},
            onHelp = {},
            onSignOut = {},
            onCalendarToggle = {},
            onSelectCalendar = {},
            onDismissMessage = {},
        )
    }
}
