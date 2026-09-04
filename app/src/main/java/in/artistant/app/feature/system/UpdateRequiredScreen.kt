package `in`.artistant.app.feature.system

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import `in`.artistant.app.designsystem.component.AccentNote
import `in`.artistant.app.designsystem.component.BottomActionBar
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.ArtistantTheme

/**
 * Screen 120 — "Time for an update."
 *
 * The design's note is the specification: *a hard gate, explained*. Force-update
 * screens usually ship two words and a button, and the two things they omit are
 * the two things the user actually wants — **why** they are locked out, and
 * **what happened to their stuff**. Both are on this screen, and the version
 * pair is what makes the first one checkable rather than a claim.
 *
 * **There is genuinely no way past it.** No back control, and [BackHandler]
 * swallows the system gesture, because the reason for the gate is that this
 * build can write state the server no longer understands — a booking row a newer
 * client cannot read is not a bug the user can be asked to accept "just this
 * once". The screen says so, in its own footnote, which is the difference
 * between a locked door and a locked door with a sign on it.
 *
 * Wired to nothing today: see [SystemStatusSource] for why the backend cannot
 * report a minimum version, and the debug harness's `force-update` flag for how
 * this gets on screen.
 */
@Composable
fun UpdateRequiredScreen(
    installed: String,
    minimum: String,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val context = LocalContext.current

    // The hard part of "no dismiss": a gate with no back BUTTON is still exited
    // by the back gesture, which would drop the user into the app the gate
    // exists to keep them out of.
    BackHandler(enabled = true) { /* deliberately inert */ }

    Column(
        modifier
            .fillMaxSize()
            .background(colors.page),
    ) {
        Column(
            Modifier
                .weight(1f)
                .statusBarsPadding()
                .padding(horizontal = dimens.component.gutter),
        ) {
            Spacer(Modifier.height(dimens.space.xxl))
            Box(
                Modifier
                    .size(dimens.funnel.outcomeDisc)
                    .clip(RoundedCornerShape(dimens.radii.xl))
                    .background(colors.accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowUpward,
                    contentDescription = null,
                    tint = colors.onAccent,
                    modifier = Modifier.size(dimens.size.iconXl),
                )
            }
            Text(
                text = "Time for an update.",
                style = AppTheme.type.displayHero,
                color = colors.ink,
                modifier = Modifier.padding(top = dimens.space.xl),
            )
            Text(
                text = "This version can no longer talk to Artistant safely. " +
                    "Updating takes about a minute.",
                style = AppTheme.type.body,
                color = colors.ink4,
                modifier = Modifier.padding(top = dimens.space.md),
            )

            // The two numbers side by side. A gate that only says "update" is
            // indistinguishable from a bug; this is what the user can check.
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = dimens.space.xl)
                    .clip(RoundedCornerShape(dimens.radii.lg))
                    .background(colors.surface3)
                    .padding(dimens.space.lg),
                verticalArrangement = Arrangement.spacedBy(dimens.space.sm),
            ) {
                VersionRow("You have", installed)
                VersionRow("Minimum supported", minimum, emphasise = true)
            }

            AccentNote(
                text = "Your bookings, messages and drafts are safe on your account — " +
                    "nothing is lost by updating.",
                modifier = Modifier.padding(top = dimens.space.lg),
            )

            Spacer(Modifier.weight(1f))

            Text(
                text = "There is no dismiss on this screen by design — " +
                    "an unsupported client can corrupt booking state.",
                style = AppTheme.type.caption.copy(fontWeight = FontWeight.Normal),
                color = colors.ink4,
                modifier = Modifier.padding(bottom = dimens.space.md),
            )
        }

        BottomActionBar {
            PrimaryButton(
                // "App Store" on the design; this is the Play listing, and
                // naming the wrong store is the fastest way to make a gate look
                // like a phishing screen.
                text = "Update on Google Play",
                onClick = { AppStore.openListing(context) },
                fullWidth = true,
            )
        }
    }
}

@Composable
private fun VersionRow(label: String, value: String, emphasise: Boolean = false) {
    val colors = AppTheme.colors
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = AppTheme.type.subtitle, color = colors.ink2)
        Text(
            text = value,
            // Mono, because these are numerals being compared to each other and
            // proportional digits make "2.1.4" and "2.4.0" hard to line up.
            style = AppTheme.type.monoPill.copy(
                fontWeight = if (emphasise) FontWeight.Bold else FontWeight.SemiBold,
            ),
            color = colors.ink,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFAF6)
@Composable
private fun UpdateRequiredPreview() {
    ArtistantTheme {
        UpdateRequiredScreen(installed = "2.1.4", minimum = "2.4.0")
    }
}
