package `in`.artistant.app.feature.signup

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role as A11yRole
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import `in`.artistant.app.designsystem.component.Banner
import `in`.artistant.app.designsystem.component.BannerTone
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.theme.AppRole
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.ArtistantTheme
import `in`.artistant.app.designsystem.theme.motion
import `in`.artistant.app.designsystem.theme.motionTween

/**
 * Screens 11 and 71 — the role picker, and the role picker with a hydration failure on top of it.
 *
 * **11: two doors, no lock.** The design's note is "role changes the nav, never the account —
 * so nobody signs up twice", and the layout says the same thing: two cards of equal weight, one
 * selected, one Continue. The old picker committed on tap and self-advanced after a 340ms hold,
 * which made the choice feel like a trapdoor; this one selects on tap and moves on Continue,
 * because that is what the design draws and because a role is a thing you should be able to
 * look at before you take it.
 *
 * **71: a blip is not a new account.** When the root gate's profile hydration fails it can no
 * longer tell a returning artist from a brand-new client, and its fallback is to show this
 * screen. Left alone that is a silent demotion — a returning artist lands in the client tabs
 * with nothing on screen explaining why they were asked a question they answered months ago.
 * [hydrationError] puts the failure at the top of the screen with the Retry that undoes it,
 * which is the whole content of design screen 71.
 */
@Composable
fun RoleScreen(
    selected: AppRole,
    onPick: (AppRole) -> Unit,
    onAdvance: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    hydrationError: String? = null,
    onRetryHydration: () -> Unit = {},
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val space = dimens.space

    SignupScaffold(
        modifier = modifier.semantics { testTag = "screen.role" },
        header = { SignupHeader(onBack = onBack) },
        footer = {
            PrimaryButton(
                text = "Continue",
                onClick = onAdvance,
                fullWidth = true,
                modifier = Modifier.semantics { testTag = "role.continue" },
            )
        },
    ) {
        if (hydrationError != null) {
            Spacer(Modifier.height(space.sm))
            HydrationErrorBanner(detail = hydrationError, onRetry = onRetryHydration)
        }

        Spacer(Modifier.height(space.lg))
        Text(
            "Which side of the\nstage are you on?",
            style = AppTheme.type.screenTitle,
            color = colors.ink,
        )
        Spacer(Modifier.height(space.sm))
        Text(
            "You can switch anytime — plenty of people do both.",
            style = AppTheme.type.subtitle,
            color = colors.ink4,
        )
        Spacer(Modifier.height(space.xl))

        RoleCard(
            title = "I'm hosting",
            body = "A wedding, a brand night, a living room. Find an act and agree a price.",
            glyph = Icons.Filled.Equalizer,
            selected = selected == AppRole.Client,
            testTag = "role.client",
            onClick = { onPick(AppRole.Client) },
        )
        Spacer(Modifier.height(space.md))
        RoleCard(
            title = "I'm performing",
            body = "Set your rate, hold your calendar, get paid after the set.",
            glyph = Icons.Filled.Mic,
            selected = selected == AppRole.Artist,
            testTag = "role.artist",
            onClick = { onPick(AppRole.Artist) },
        )
        Spacer(Modifier.height(space.lg))

        Banner(
            title = "Agencies and wedding planners choose hosting — team seats live in settings.",
            tone = BannerTone.Note,
        )
        Spacer(Modifier.height(space.lg))
    }
}

/**
 * One of the two doors.
 *
 * Selection is a TINT plus a ring plus a filled radio, not one of the three. The design uses
 * all three because each one fails on its own for someone: the tint is the whole card and
 * carries at a glance, the ring survives a colour-blind reading, and the radio is what a
 * screen reader has to be told about. The unselected card is `surface3` with a hairline, so
 * the pair reads as two options rather than as one option and one warning.
 */
@Composable
private fun RoleCard(
    title: String,
    body: String,
    glyph: ImageVector,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val space = dimens.space
    val shape = RoundedCornerShape(dimens.radii.xl)
    val interaction = remember { MutableInteractionSource() }

    val fill by animateColorAsState(
        targetValue = if (selected) colors.accent.copy(alpha = SELECTED_TINT) else colors.surface3,
        animationSpec = motionTween<Color>(AppTheme.motion.tabSwitch),
        label = "roleFill",
    )
    val stroke by animateColorAsState(
        targetValue = if (selected) colors.accent else colors.hairline,
        animationSpec = motionTween<Color>(AppTheme.motion.tabSwitch),
        label = "roleStroke",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(fill)
            .border(dimens.component.focusStroke, stroke, shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = A11yRole.RadioButton,
                onClick = onClick,
            )
            .padding(space.lg)
            .semantics(mergeDescendants = true) {
                this.testTag = testTag
                this.selected = selected
                contentDescription = "$title. $body"
            },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(dimens.component.iconCircle)
                    .clip(RoundedCornerShape(dimens.radii.md))
                    .background(if (selected) colors.accent else colors.hairline),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    glyph,
                    contentDescription = null,
                    tint = if (selected) colors.onAccent else colors.ink2,
                    modifier = Modifier.size(dimens.size.iconLg),
                )
            }
            Box(
                Modifier
                    .size(dimens.size.radio)
                    .clip(CircleShape)
                    .then(
                        if (selected) {
                            Modifier.background(colors.accent)
                        } else {
                            Modifier.border(dimens.component.checkboxStroke, colors.lineStrong, CircleShape)
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = colors.onAccent,
                        modifier = Modifier.size(dimens.size.iconSm),
                    )
                }
            }
        }
        Spacer(Modifier.height(space.md))
        Text(
            title,
            style = AppTheme.type.sectionTitle.copy(fontSize = AppTheme.type.cardTitle.fontSize),
            color = colors.ink,
        )
        Spacer(Modifier.height(space.xs))
        Text(body, style = AppTheme.type.subtitle, color = colors.ink2)
    }
}

/** The selected card's tint — the accent at ~26%, which is what the markup draws. */
private const val SELECTED_TINT = 0.26f

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, heightDp = 800)
@Composable
private fun RolePickerPreview() {
    ArtistantTheme {
        RoleScreen(selected = AppRole.Client, onPick = {}, onAdvance = {}, onBack = {})
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, heightDp = 800)
@Composable
private fun RolePickerHydrationErrorPreview() {
    ArtistantTheme {
        RoleScreen(
            selected = AppRole.Client,
            onPick = {},
            onAdvance = {},
            onBack = {},
            hydrationError = "We signed you in but your details are missing.",
        )
    }
}
