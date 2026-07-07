package `in`.artistant.app.feature.signup

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Mic
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import kotlinx.coroutines.delay
import `in`.artistant.app.designsystem.theme.AppRole
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.accent

/**
 * Role picker (iOS `SignupRoleView`): two full-bleed "worlds" — booking (lime) and being an
 * artist (violet) — each previewing that role's accent. **Tap = commit**: set the role, show a
 * brief selected state (border + glow + check, sibling dims), then advance. A short appear-
 * debounce blocks a touch carried over from the welcome transition from auto-committing.
 *
 * @param testMode true under instrumentation/a11y so the debounce is skipped (deliberate taps,
 *   never carry-over) — parity with iOS `UITestSupport.isUITest || VoiceOver`.
 */
@Composable
fun RoleScreen(
    onPick: (AppRole) -> Unit,
    onAdvance: () -> Unit,
    modifier: Modifier = Modifier,
    testMode: Boolean = false,
) {
    val space = AppTheme.dimens.space
    // The committing role (null until a panel is tapped): drives the selected/dimmed visuals and
    // blocks a second commit during the hand-off.
    var committing by remember { mutableStateOf<AppRole?>(null) }
    // Arm a beat after appearing so a carried-over touch can't auto-commit. Deliberate taps under
    // test/a11y arm immediately.
    var armed by remember { mutableStateOf(testMode) }
    LaunchedEffect(Unit) {
        if (!armed) { delay(450); armed = true }
    }

    fun pick(role: AppRole) {
        if (!armed || committing != null) return // guard the double-tap / carry-over
        committing = role
        onPick(role) // sets role + fires the selection haptic + syncs prefs/theme
        // Hold the selected state visible for a beat, then advance (iOS 0.34s).
        // The advance runs from a LaunchedEffect keyed on `committing` below so it survives
        // recomposition without a raw handler thread.
    }

    LaunchedEffect(committing) {
        if (committing != null) { delay(340); onAdvance() }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppTheme.colors.bg)
            .padding(top = space.sm, bottom = space.lg),
        verticalArrangement = Arrangement.spacedBy(space.lg),
    ) {
        SignupProgressDots(bar = ProgressBar(0, 5))

        // Neutral editorial headline — the panels carry the color, so "stage" is the only
        // accent (italic, but ink not brand, so it implies no side).
        Text(
            text = androidx.compose.ui.text.buildAnnotatedString {
                append("Which side of\nthe ")
                withStyle(androidx.compose.ui.text.SpanStyle(fontStyle = FontStyle.Italic)) { append("stage") }
                append("?")
            },
            style = AppTheme.type.displayHero,
            color = AppTheme.colors.ink,
            modifier = Modifier.padding(horizontal = space.xl),
        )

        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = space.lg),
            verticalArrangement = Arrangement.spacedBy(space.md),
        ) {
            RolePanel(
                role = AppRole.Client,
                title = "I'm booking artists",
                sub = "For a fest, club, brand event, wedding or private gig.",
                glyph = Icons.Filled.ConfirmationNumber,
                committing = committing,
                testTag = "role.client",
                modifier = Modifier.weight(1f),
            ) { pick(AppRole.Client) }
            RolePanel(
                role = AppRole.Artist,
                title = "I'm an artist",
                sub = "Set up your booking-ready profile in 4 minutes.",
                glyph = Icons.Filled.Mic,
                committing = committing,
                testTag = "role.artist",
                modifier = Modifier.weight(1f),
            ) { pick(AppRole.Artist) }
        }
    }
}

@Composable
private fun RolePanel(
    role: AppRole,
    title: String,
    sub: String,
    glyph: ImageVector,
    committing: AppRole?,
    testTag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = AppTheme.colors
    val accent = role.accent().brand
    val selected = committing == role
    val dimmed = committing != null && committing != role
    val shape = RoundedCornerShape(28.dp)

    // Animate the select/dim transition (iOS `.easeOut(0.24)`).
    val panelAlpha by animateFloatAsState(if (dimmed) 0.45f else 1f, label = "roleAlpha")
    val panelScale by animateFloatAsState(if (dimmed) 0.97f else 1f, label = "roleScale")
    val borderColor by animateColorAsState(if (selected) accent else colors.line, label = "roleBorder")

    Box(
        modifier = modifier
            .fillMaxWidth()
            .scale(panelScale)
            .alpha(panelAlpha)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(accent.copy(alpha = 0.24f), colors.bg.copy(alpha = 0.15f), colors.bg.copy(alpha = 0.05f)),
                ),
            )
            .border(if (selected) 2.dp else 1.dp, borderColor, shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .semantics {
                this.testTag = testTag
                contentDescription = "$title. Selects this role and continues"
            },
    ) {
        // Faint oversized glyph bleeding off the top-right corner.
        Icon(
            glyph,
            contentDescription = null,
            tint = accent.copy(alpha = 0.13f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp)
                .size(150.dp),
        )
        // Bottom-left label block.
        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(AppTheme.dimens.space.xl),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(title, style = AppTheme.type.displayTitle, color = colors.ink)
            Text(sub, style = AppTheme.type.callout, color = colors.ink2)
        }
        if (selected) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.align(Alignment.TopStart).padding(AppTheme.dimens.space.lg).size(30.dp),
            )
        }
    }
}
