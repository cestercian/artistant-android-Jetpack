package `in`.artistant.app.feature.wizard

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.ui.rememberHaptics

/** Step 11 — celebration after publish; single CTA into the artist dashboard (iOS
 *  `ArtistWizardDoneStep`). [onOpenDashboard] re-runs routing so the gate lands on artist tabs. */
@Composable
fun WizardDoneStep(handle: String, onOpenDashboard: () -> Unit) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space

    val haptics = rememberHaptics()
    var popped by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (popped) 1f else 0.6f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow),
        label = "wizardDonePop",
    )
    // iOS-parity: success buzz as the "You're live" checkmark pops in.
    LaunchedEffect(Unit) { popped = true; haptics.success() }

    Column(
        modifier = Modifier.fillMaxSize().background(colors.bg).padding(horizontal = space.xl).padding(bottom = space.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.size(88.dp).scale(scale).clip(CircleShape).background(colors.brand),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = colors.brandInk, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(space.xxl))
        Text("You're live.", style = AppTheme.type.displayTitle, color = colors.ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(space.md))
        Text(
            "Your profile is now bookable on artistant.in/$handle.",
            style = AppTheme.type.footnote,
            color = colors.ink2,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 280.dp),
        )
        Spacer(Modifier.weight(1f))
        PrimaryButton(text = "Open dashboard →", onClick = onOpenDashboard, fullWidth = true)
    }
}
