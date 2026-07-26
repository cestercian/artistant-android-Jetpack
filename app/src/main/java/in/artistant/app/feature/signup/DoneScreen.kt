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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.component.ScoreRing
import `in`.artistant.app.designsystem.theme.AppTheme

/**
 * The celebration screen (iOS `SignupDoneView`): spring pop-in brand checkmark, editorial
 * "You're in, {firstName}." (guarded so an empty name reads "You're in."), a Bookability-Score
 * primer, and "Start exploring →" which fires the completion analytics + hands off to the gate.
 */
@Composable
fun DoneScreen(
    firstName: String,
    city: String,
    onStartExploring: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    // Spring pop-in the checkmark on appear (iOS scale 0.6 → 1.0).
    var popped by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (popped) 1f else 0.6f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow),
        label = "donePop",
    )
    val alpha by animateFloatAsState(if (popped) 1f else 0f, label = "doneAlpha")
    LaunchedEffect(Unit) { popped = true }

    Column(
        modifier = modifier.fillMaxSize().background(colors.bg).padding(horizontal = space.xl).padding(bottom = space.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(80.dp))

        Box(
            Modifier.size(64.dp).scale(scale).clip(CircleShape).background(colors.brand),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = colors.brandInk, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(space.xxl + space.sm))

        Text(
            if (firstName.isBlank()) "You're in." else "You're in, $firstName.",
            style = AppTheme.type.displayTitle.copy(fontSize = 40.sp),
            color = colors.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.alpha(alpha),
        )
        Spacer(Modifier.height(space.lg))
        // "booking-ready" stays the mono accent run; the rest is callout ink2.
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = colors.ink2)) { append(if (city.isBlank()) "Discover " else "$city is full of ") }
                withStyle(SpanStyle(color = colors.ink, fontFamily = AppTheme.type.monoMedium.fontFamily, fontWeight = FontWeight.Black)) { append("booking-ready") }
                withStyle(SpanStyle(color = colors.ink2)) { append(" artists right now.") }
            },
            style = AppTheme.type.callout,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 300.dp),
        )

        Spacer(Modifier.weight(1f))

        // Score primer — no card chrome, just the ring + two lines.
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = space.xl), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(space.md)) {
            ScoreRing(value = 94, size = 48.dp, stroke = 4.dp, showLabel = false)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Bookability Score™", style = AppTheme.type.footnote.copy(fontWeight = FontWeight.Black), color = colors.ink)
                Text("Every artist rated for reliability — not just talent.", style = AppTheme.type.caption, color = colors.ink3)
            }
        }

        PrimaryButton(
            text = "Start exploring →",
            onClick = onStartExploring,
            fullWidth = true,
            modifier = Modifier.semantics { testTag = "done.continue" },
        )
    }
}
