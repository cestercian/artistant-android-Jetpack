package `in`.artistant.app.feature.signup

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.ArtistantTheme

/** The four rules, numbered, in the design's order (screen 27). */
private val pledgeRules = listOf(
    "Show up" to "If you commit to a date, you play it — or you tell the other side early.",
    "Say the real number" to "Quotes are all-inclusive. No fee appears after a handshake.",
    "Keep it here" to "Payments and contact stay on Artistant until a booking is confirmed.",
    "Treat people well" to "No harassment, no discrimination, no pressure. Report anything that feels off.",
)

/**
 * Screen 27 — the community pledge, shown exactly once.
 *
 * "Shown exactly once" is the design's note and it is also the mechanism: agreeing writes a
 * DataStore flag ([in.artistant.app.platform.storage.SignupConsentStore.communityAgreed]) and
 * the flag is what routes the role step past this screen forever after. The footer says so out
 * loud — "Shown once — you won't see this again" — which is the whole reason it can ask for a
 * commitment rather than a dismissal.
 *
 * The tick is REQUIRED, not decorative: "Agree and continue" is disabled until it is on, and
 * that pairing is what makes the agreement an affirmative act rather than a button someone
 * tapped past. There is no Decline: declining a pledge you have not agreed to is what the back
 * chevron already does, and a dedicated dead-end screen for it (which is what this used to
 * have) is a screen that exists only to tell the user off.
 */
@Composable
fun CommunityCommitmentScreen(
    onAgree: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val space = dimens.space
    var agreed by remember { mutableStateOf(false) }

    SignupScaffold(
        modifier = modifier.semantics {
            testTag = "screen.community"
            contentDescription = "Community commitment"
        },
        header = { SignupHeader(onBack = onBack) },
        footer = {
            PrimaryButton(
                text = "Agree and continue",
                onClick = onAgree,
                fullWidth = true,
                enabled = agreed,
                modifier = Modifier.semantics { testTag = "community.agree" },
            )
            Text(
                "Shown once — you won't see this again",
                style = AppTheme.type.caption,
                color = colors.ink4,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        Spacer(Modifier.height(space.md))
        Text("Before you join", style = AppTheme.type.screenTitle, color = colors.ink)
        Spacer(Modifier.height(space.sm))
        Text(
            "Artistant works because both sides keep to four things. Read them once.",
            style = AppTheme.type.subtitle,
            color = colors.ink4,
        )
        Spacer(Modifier.height(space.lg))

        pledgeRules.forEachIndexed { index, (title, body) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = space.md)
                    .clip(RoundedCornerShape(dimens.radii.lg))
                    .background(colors.surface3)
                    .padding(space.lg)
                    .semantics(mergeDescendants = true) {},
                horizontalArrangement = Arrangement.spacedBy(space.md),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    Modifier
                        .size(dimens.size.iconXl)
                        .clip(RoundedCornerShape(dimens.radii.sm))
                        .background(colors.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${index + 1}",
                        style = AppTheme.type.caption.copy(fontWeight = FontWeight.Black),
                        color = colors.onAccent,
                    )
                }
                Column {
                    Text(
                        title,
                        style = AppTheme.type.body.copy(fontWeight = FontWeight.Bold),
                        color = colors.ink,
                    )
                    Spacer(Modifier.height(space.xs))
                    Text(body, style = AppTheme.type.subtitle, color = colors.ink2)
                }
            }
        }

        // The tick. Same construction as the welcome screen's consent block, minus the card —
        // this one sits directly on the page because the four rules above it are already
        // cards, and a fifth would read as a fifth rule.
        val interaction = remember { MutableInteractionSource() }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(dimens.radii.md))
                .clickable(interactionSource = interaction, indication = null, role = Role.Checkbox) {
                    agreed = !agreed
                }
                .padding(vertical = space.sm)
                .semantics {
                    testTag = "community.consent"
                    contentDescription =
                        "I agree to the community commitment and understand my account can be removed for breaking it"
                    toggleableState = if (agreed) ToggleableState.On else ToggleableState.Off
                },
            horizontalArrangement = Arrangement.spacedBy(space.md),
            verticalAlignment = Alignment.Top,
        ) {
            ConsentCheckbox(checked = agreed)
            Text(
                "I agree to the community commitment and understand my account can be removed for breaking it.",
                style = AppTheme.type.subtitle,
                color = colors.ink2,
            )
        }
        Spacer(Modifier.height(space.md))
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, heightDp = 800)
@Composable
private fun CommunityPledgePreview() {
    ArtistantTheme { CommunityCommitmentScreen(onAgree = {}, onBack = {}) }
}
