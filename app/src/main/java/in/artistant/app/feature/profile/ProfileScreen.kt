package `in`.artistant.app.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.artistant.app.designsystem.component.Banner
import `in`.artistant.app.designsystem.component.BannerTone
import `in`.artistant.app.designsystem.component.IconCircle
import `in`.artistant.app.designsystem.component.ListRow
import `in`.artistant.app.designsystem.component.ScreenHeader
import `in`.artistant.app.designsystem.component.SkeletonBlock
import `in`.artistant.app.designsystem.component.SkeletonCircle
import `in`.artistant.app.designsystem.theme.AppRole
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.ArtistantTheme

/**
 * Design screen 26 — **"One account, two modes"**.
 *
 * The client's Profile tab root: who you are, what you have going, and the handful of places
 * you go from here. It is deliberately NOT the settings list — that is screen 47, one tap away
 * behind the gear, and keeping them apart is what stops the tab root becoming a wall of rows.
 *
 * **The pill is the note.** "A host who starts performing switches here instead of signing up
 * again": the account is one row in `public.users` with a `role` column, and
 * `users_update_self` (mig 0002) lets it change its own. So switching is a write, not a second
 * signup — see [ProfileViewModel.switchToArtistMode] for what happens after it.
 *
 * **Two rows the design draws are not here**, and their absence is the honest reading of a
 * no-payments v1: "Payment and billing" and "Invoices and GST" describe a product that takes
 * money, and this one does not — there is no payment method, no GSTIN column anywhere in the
 * 105 canonical migrations, and nothing for either row to open. A row that pushes an empty
 * screen is worse than a row that isn't there.
 *
 * **"Your bookings" is here** because the light tab bar dropped the Bookings glyph (the P1
 * decision on `ClientTabsScaffold`), which makes this screen its front door. The stat band's
 * "Upcoming" column opens the drill-down LIST (screen 32) — a different screen — so the row is
 * not a duplicate of the counter above it.
 */
@Composable
fun ProfileScreen(
    /**
     * Deliberately has NO default, unlike the rest of these. It is the only way to reach the
     * settings list, and that list is the only way to reach sign-out, delete, export and the
     * unblock screen. A default would let a new host silently ship a Profile tab with no exit;
     * the compiler asking the question is the point.
     */
    onAccount: () -> Unit,
    onBookings: () -> Unit,
    onArtistList: (ArtistListKind) -> Unit,
    onNotifications: () -> Unit,
    onPrivacy: () -> Unit,
    /** @see AccountScreen.onSafetyCentre — nullable for the same reason; the row is omitted. */
    onSafetyCentre: (() -> Unit)?,
    /**
     * Re-run the root gate after a role switch. Without it the write lands and the app stays
     * in the client scaffold until the next cold start — see [ProfileViewModel.switchToArtistMode].
     */
    onRoleSwitched: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ProfileContent(
        state = state,
        onAccount = onAccount,
        onBookings = onBookings,
        onArtistList = onArtistList,
        onNotifications = onNotifications,
        onPrivacy = onPrivacy,
        onSafetyCentre = onSafetyCentre,
        onSwitchToArtist = { viewModel.switchToArtistMode(onRoleSwitched) },
        onRetry = viewModel::refresh,
        onDismissMessage = viewModel::clearActionFeedback,
        modifier = modifier,
    )
}

@Composable
private fun ProfileContent(
    state: ProfileUiState,
    onAccount: () -> Unit,
    onBookings: () -> Unit,
    onArtistList: (ArtistListKind) -> Unit,
    onNotifications: () -> Unit,
    onPrivacy: () -> Unit,
    onSafetyCentre: (() -> Unit)?,
    onSwitchToArtist: () -> Unit,
    onRetry: () -> Unit,
    onDismissMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val space = dimens.space

    AccountScaffold(
        modifier = modifier.semantics { testTag = "screen.profile" },
        header = {
            ScreenHeader(
                title = "Profile",
                trailing = {
                    IconCircle(
                        icon = Icons.Filled.Settings,
                        contentDescription = "Account settings",
                        onClick = onAccount,
                        modifier = Modifier.semantics { testTag = "profile.account" },
                    )
                },
            )
        },
    ) {
        AccountGap()

        // Loading and failed are two different screens and say which one they are
        // (REDESIGN_2026-09 §2). Only the FIRST load gets the skeleton: a refresh over a
        // profile we already have keeps the name on screen rather than replacing a real
        // identity with grey bars.
        when {
            state.isLoading && state.profile == null -> ProfileIdentitySkeleton()
            state.error != null && state.profile == null -> {
                Banner(
                    title = "Couldn't load your profile",
                    tone = BannerTone.Failure,
                    detail = state.error,
                    actionLabel = "Retry",
                    onAction = onRetry,
                )
            }
            else -> ProfileIdentity(
                state = state,
                onSwitchToArtist = onSwitchToArtist,
            )
        }

        AccountGap()
        // Three tap targets laid OVER the band rather than inside it, so the two dividers stay
        // one un-clickable rule and the band component itself stays free of navigation. They
        // are `matchParentSize`, not a row beneath — a row beneath would add its own 44dp of
        // dead space under the numbers and split the target from the thing it targets.
        //
        // Each column stays tappable while its number is unknown: the drill-down list does its
        // own read and reports its own failure, so "—" plus a tap is the honest route to the
        // error the header used to hide behind a zero.
        Box(Modifier.fillMaxWidth()) {
            AccountStatBand(
                stats = listOf(
                    AccountStat("Upcoming", accountStatValue(state.bookingsCount)),
                    AccountStat("Saved", accountStatValue(state.savedCount)),
                    AccountStat("Completed", accountStatValue(state.completedCount)),
                ),
                modifier = Modifier.semantics { testTag = "profile.stats" },
            )
            Row(Modifier.matchParentSize()) {
                StatTarget("Upcoming bookings", Modifier.weight(1f)) {
                    onArtistList(ArtistListKind.Bookings)
                }
                StatTarget("Saved artists", Modifier.weight(1f)) {
                    onArtistList(ArtistListKind.Saved)
                }
                StatTarget("Completed bookings", Modifier.weight(1f)) {
                    onArtistList(ArtistListKind.Completed)
                }
            }
        }

        AccountGap()
        ListRow(
            title = "Your bookings",
            subtitle = bookingsRowSubtitle(state.bookingsCount),
            onClick = onBookings,
            modifier = Modifier.semantics { testTag = "profile.bookings" },
        )
        ListRow(
            title = "Saved artists",
            subtitle = savedRowSubtitle(state.savedCount),
            onClick = { onArtistList(ArtistListKind.Saved) },
        )
        ListRow(title = "Notifications", onClick = onNotifications)
        if (onSafetyCentre != null) {
            ListRow(title = "Help and safety", onClick = onSafetyCentre)
        }
        ListRow(title = "Legal and privacy", onClick = onPrivacy, showHairline = false)

        // Tap-to-dismiss, the same affordance every other action failure in this package
        // gets. Nothing else clears it — a failed role switch used to sit under the list for
        // the life of the ViewModel.
        state.actionError?.let { message ->
            AccountGap()
            Text(
                message,
                style = AppTheme.type.caption,
                color = colors.danger,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onDismissMessage)
                    .padding(vertical = space.sm)
                    .semantics { testTag = "profile.actionError" },
            )
        }
        Spacer(Modifier.height(dimens.size.listTailroom))
    }
}

/**
 * Avatar, name, meta line, and the mode pill (screen 26).
 *
 * The avatar is initials on a hairline disc rather than a photo: `public.users` has an
 * `avatar_url` column but nothing in this app ever writes one, so a photo slot here would be
 * an empty circle on every account. Initials are derived from a name we always have.
 */
@Composable
private fun ProfileIdentity(state: ProfileUiState, onSwitchToArtist: () -> Unit) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(dimens.space.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(dimens.size.avatarLg)
                .background(colors.hairline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                initials(state.displayName),
                style = AppTheme.type.displaySub,
                color = colors.ink2,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(state.displayName, style = AppTheme.type.displaySub, color = colors.ink)
            Text(
                state.subtitle,
                style = AppTheme.type.subtitle,
                color = colors.ink4,
                modifier = Modifier.padding(top = dimens.space.xs / 2),
            )
            if (state.role == AppRole.Client) {
                Spacer(Modifier.height(dimens.space.sm))
                ModePill(working = state.switchingRole, onClick = onSwitchToArtist)
            }
        }
    }
}

/**
 * "Switch to artist mode" — a `surface2` capsule, not a CTA.
 *
 * The screen's one accent belongs to the tab bar's action circle (REDESIGN_2026-09 §2: one
 * accent per screen), and this is a mode change rather than the page's primary verb. It gets a
 * quiet capsule with a mic glyph, exactly as the design draws it.
 */
@Composable
private fun ModePill(working: Boolean, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Row(
        Modifier
            .background(colors.surface2, CircleShape)
            .clickable(enabled = !working, role = Role.Button, onClick = onClick)
            .padding(horizontal = dimens.space.md, vertical = dimens.space.sm)
            .semantics { testTag = "profile.switchToArtist" },
        horizontalArrangement = Arrangement.spacedBy(dimens.space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (working) {
            CircularProgressIndicator(
                Modifier.size(dimens.size.iconMd),
                strokeWidth = dimens.size.stroke,
                color = colors.accentInk,
            )
        } else {
            Icon(
                Icons.Filled.MicNone,
                contentDescription = null,
                tint = colors.ink,
                modifier = Modifier.size(dimens.size.iconMd),
            )
        }
        Text(
            if (working) "Switching…" else "Switch to artist mode",
            style = AppTheme.type.chip,
            color = colors.ink,
        )
    }
}

/** An invisible tap target covering one column of [AccountStatBand]. */
@Composable
private fun StatTarget(label: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .fillMaxHeight()
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
    )
}

/** The first letters of up to two words — "Rhea Menon" → "RM", "You" → "Y". */
internal fun initials(name: String): String =
    name.trim()
        .split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")
        .ifEmpty { "A" }

/**
 * "2 upcoming" / "Nothing on right now" / null.
 *
 * Null when the count is unknown, so the row shows one line instead of asserting a number we
 * could not read — the same rule the stat band's em dash follows.
 */
internal fun bookingsRowSubtitle(count: Int?): String? = when {
    count == null -> null
    count == 0 -> "Nothing on right now"
    count == 1 -> "1 upcoming"
    else -> "$count upcoming"
}

/** "12 acts" / "None saved yet". Never null: the saved set is local and always has an answer. */
internal fun savedRowSubtitle(count: Int): String =
    if (count == 0) "None saved yet" else if (count == 1) "1 act" else "$count acts"

/** The identity block's first-load stand-in: a disc and two bars, at the real geometry. */
@Composable
private fun ProfileIdentitySkeleton() {
    val dimens = AppTheme.dimens
    Row(
        Modifier.fillMaxWidth().semantics { testTag = "profile.loading" },
        horizontalArrangement = Arrangement.spacedBy(dimens.space.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkeletonCircle(dimens.size.avatarLg)
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(dimens.space.sm),
        ) {
            SkeletonBlock(
                Modifier
                    .width(dimens.component.skeletonTitleWidth)
                    .height(dimens.component.skeletonTitleHeight),
            )
            SkeletonBlock(
                Modifier
                    .width(dimens.component.skeletonSectionWidth)
                    .height(dimens.component.skeletonLineHeight),
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, heightDp = 800)
@Composable
private fun ProfilePreview() {
    ArtistantTheme {
        ProfileContent(
            state = ProfileUiState(
                isLoading = false,
                bookingsCount = 2,
                savedCount = 12,
                completedCount = 7,
            ),
            onAccount = {},
            onBookings = {},
            onArtistList = {},
            onNotifications = {},
            onPrivacy = {},
            onSafetyCentre = {},
            onSwitchToArtist = {},
            onRetry = {},
            onDismissMessage = {},
        )
    }
}
