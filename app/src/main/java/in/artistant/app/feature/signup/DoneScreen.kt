package `in`.artistant.app.feature.signup

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.theme.AppRole
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.ArtistantTheme

/**
 * Screen 30 — **ends on the score**.
 *
 * The design's note explains the whole shape of it: "the last signup beat teaches the one
 * concept the whole marketplace rests on." So the celebration is one line, and the rest of the
 * screen is a primer on the Bookability Score — which is the thing a new user has to
 * understand before the artist list means anything.
 *
 * **What is missing from it, and why.** The design's subtitle reads "412 acts play your city".
 * There is no count endpoint behind that number and no repository this section owns that could
 * fetch one, and a hard-coded 412 is exactly the fabricated figure REDESIGN_2026-09 §5.2 rules
 * out — so the sentence keeps its shape and drops its number. The count is listed as a gap in
 * the PR; wiring it needs an artists-in-city aggregate the client can read under RLS.
 *
 * The score card's `86` stays, because it is not a claim: it is an illustration of what a
 * score looks like, sitting beside a paragraph that says whose score it would be ("every
 * artist is rated"). Nothing on this screen attributes it to the reader or to anyone else.
 */
@Composable
fun DoneScreen(
    firstName: String,
    city: String,
    onStartExploring: () -> Unit,
    modifier: Modifier = Modifier,
    role: AppRole = AppRole.Client,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val space = dimens.space

    // Spring pop-in on appear (iOS scale 0.6 → 1.0).
    var popped by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (popped) 1f else POP_FROM,
        animationSpec = spring(dampingRatio = POP_DAMPING, stiffness = Spring.StiffnessLow),
        label = "donePop",
    )
    LaunchedEffect(Unit) { popped = true }

    SignupScaffold(
        modifier = modifier.semantics { testTag = "screen.done" },
        // Fills the viewport: the footnote is pushed to the bottom of the body by a flex
        // spacer, which has no meaning inside an infinitely-tall scroll.
        scrollable = false,
        footer = {
            PrimaryButton(
                text = if (role == AppRole.Artist) "Set up your profile" else "Start browsing",
                onClick = onStartExploring,
                fullWidth = true,
                modifier = Modifier.semantics { testTag = "done.continue" },
            )
        },
    ) {
        Spacer(Modifier.height(space.xl))
        Box(
            Modifier
                .size(dimens.component.emptyGlyphCircle)
                .scale(scale)
                .clip(CircleShape)
                .background(colors.accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = colors.onAccent,
                modifier = Modifier.size(dimens.size.iconXl),
            )
        }

        Spacer(Modifier.height(space.xl))
        Text(
            // The city is the greeting when we know it; the name is the fallback, and a bare
            // "You're in." is what is left when signup collected neither (a login-mode walk).
            when {
                city.isNotBlank() -> "You're in,\n$city."
                firstName.isNotBlank() -> "You're in,\n$firstName."
                else -> "You're in."
            },
            style = AppTheme.type.displayHero,
            color = colors.ink,
        )
        Spacer(Modifier.height(space.md))
        Text(
            "Bands, DJs, comics, classical and dance — all booking through Artistant.",
            style = AppTheme.type.body,
            color = colors.ink4,
        )

        Spacer(Modifier.height(space.xl))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(dimens.radii.card))
                .background(colors.surface3)
                .padding(space.lg)
                .semantics(mergeDescendants = true) { testTag = "done.scorePrimer" },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(space.md),
            ) {
                Box(
                    Modifier
                        .size(dimens.component.iconCircleSm)
                        .clip(CircleShape)
                        .background(colors.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        EXAMPLE_SCORE,
                        style = AppTheme.type.monoPill.copy(fontWeight = FontWeight.Black),
                        color = colors.onAccent,
                    )
                }
                Text(
                    "The Bookability Score",
                    style = AppTheme.type.sectionTitle,
                    color = colors.ink,
                )
            }
            Spacer(Modifier.height(space.md))
            Text(
                "Every artist is rated for reliability, not just talent — reply speed, show-up " +
                    "rate, reviews and cancellations. It is the number to trust when two acts " +
                    "sound alike.",
                style = AppTheme.type.subtitle,
                color = colors.ink2,
            )
        }

        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(space.lg))
        Text(
            if (role == AppRole.Artist) {
                "You can switch to hosting anytime from your profile."
            } else {
                "You can switch to performing anytime from your profile."
            },
            style = AppTheme.type.caption,
            color = colors.ink4,
        )
        Spacer(Modifier.height(space.md))
    }
}

/** The score in the primer — an illustration of the scale, attributed to nobody. */
private const val EXAMPLE_SCORE = "86"

private const val POP_FROM = 0.6f
private const val POP_DAMPING = 0.55f

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, heightDp = 820)
@Composable
private fun DonePreview() {
    ArtistantTheme {
        DoneScreen(firstName = "Rhea", city = "Bengaluru", onStartExploring = {})
    }
}
