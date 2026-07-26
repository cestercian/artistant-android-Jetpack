package `in`.artistant.app.feature.signup

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.theme.AppTheme

/**
 * One-time community pledge gate before role pick — port of iOS
 * `CommunityCommitmentView` (ACCT-05). Agreeing persists via prefs; declining
 * shows the can't-continue explainer in place.
 */
@Composable
fun CommunityCommitmentScreen(
    onAgree: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val space = AppTheme.dimens.space
    var declined by remember { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxSize()
            .semantics {
                testTag = "screen.community"
                contentDescription = "Community commitment"
            },
    ) {
        SignupProgressDots(bar = ProgressBar(0, 5))
        SignupBackButton(
            onClick = onBack,
            modifier = Modifier
                .padding(horizontal = space.xl, vertical = space.md)
                .semantics { testTag = "community.back" },
        )

        AnimatedContent(
            targetState = declined,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "communityDeclined",
            modifier = Modifier.weight(1f),
        ) { isDeclined ->
            if (isDeclined) {
                DeclinedBody(onGoBack = { declined = false })
            } else {
                PledgeBody(
                    onAgree = onAgree,
                    onDecline = { declined = true },
                )
            }
        }
    }
}

@Composable
private fun PledgeBody(
    onAgree: () -> Unit,
    onDecline: () -> Unit,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = space.xl)
            .padding(bottom = space.xxl),
        verticalArrangement = Arrangement.spacedBy(space.xl),
    ) {
        Spacer(Modifier.height(space.xl))
        Text(
            buildAnnotatedString {
                append("Artistant is\nfor ")
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append("everyone") }
                append(".")
            },
            style = AppTheme.type.displayHero,
            color = colors.ink,
        )
        Text(
            "Every client and every artist here deserves respect — regardless of religion, caste, gender, sexual orientation, disability, or appearance. Harassment, discrimination, and hate have no place on Artistant.",
            style = AppTheme.type.callout,
            color = colors.ink2,
        )
        Spacer(Modifier.weight(1f))
        PrimaryButton(
            text = "Agree and continue",
            onClick = onAgree,
            fullWidth = true,
            modifier = Modifier.semantics { testTag = "community.agree" },
        )
        Text(
            "Decline",
            style = AppTheme.type.callout,
            color = colors.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(AppTheme.dimens.size.controlMin)
                .border(1.dp, colors.line, RoundedCornerShape(AppTheme.dimens.radii.lg))
                .clickable(onClick = onDecline)
                .padding(vertical = space.md)
                .semantics { testTag = "community.decline" },
        )
    }
}

@Composable
private fun DeclinedBody(onGoBack: () -> Unit) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = space.xl)
            .padding(bottom = space.xxl),
        verticalArrangement = Arrangement.spacedBy(space.xl),
    ) {
        Spacer(Modifier.height(space.xl))
        Text(
            "We can't\ncontinue.",
            style = AppTheme.type.displayHero,
            color = colors.ink,
        )
        Text(
            "Artistant only works if everyone commits to treating each other with respect. Without agreeing, you can't use the app — but you're welcome back the moment you're ready.",
            style = AppTheme.type.callout,
            color = colors.ink2,
        )
        Spacer(Modifier.weight(1f))
        PrimaryButton(
            text = "Go back",
            onClick = onGoBack,
            fullWidth = true,
            modifier = Modifier.semantics { testTag = "community.goBack" },
        )
    }
}
