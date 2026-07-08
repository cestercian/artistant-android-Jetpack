package `in`.artistant.app.feature.wizard

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.feature.signup.SignupBackButton

/**
 * The artist onboarding wizard container (iOS `ArtistWizardView`). Switches on the current step
 * with a crossfade; each step renders its own [WizardScaffold] chrome. System back walks the
 * wizard back (except on the first + Done steps). [onDone] fires when the artist taps "Open
 * dashboard" on the celebration step — the gate re-routes into the artist tabs.
 */
@Composable
fun ArtistWizardScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    vm: WizardViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    BackHandler(enabled = state.step != WizardStep.Identity && state.step != WizardStep.Done) {
        vm.back()
    }

    Box(modifier = modifier.fillMaxSize().background(AppTheme.colors.bg)) {
        AnimatedContent(
            targetState = state.step,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
            label = "wizardStep",
        ) { step ->
            when (step) {
                WizardStep.Identity -> WizardIdentityStep(state, vm)
                WizardStep.Location -> WizardLocationStep(state, vm)
                WizardStep.Pricing -> WizardPricingStep(state, vm)
                WizardStep.Tech -> WizardTechStep(state, vm)
                WizardStep.Availability -> WizardAvailabilityStep(state, vm)
                WizardStep.Cover -> WizardCoverStep(state, vm)
                WizardStep.Socials -> WizardSocialsStep(state, vm)
                WizardStep.Bio -> WizardBioStep(state, vm)
                WizardStep.Samples -> WizardSamplesStep(state, vm)
                WizardStep.Preview -> WizardPreviewStep(state, vm)
                WizardStep.Done -> WizardDoneStep(handle = state.handle, onOpenDashboard = onDone)
            }
        }
    }
}

/**
 * Shared step chrome (iOS `WizardScreen`): a segment progress bar over [WIZARD_FLOW], an optional
 * hairline back chevron, a serif headline + subtitle, the scrollable step [content], and a
 * full-width brand CTA gated by [ctaEnabled]. Editorial-dark; no card chrome.
 */
@Composable
fun WizardScaffold(
    step: WizardStep,
    title: String,
    subtitle: String?,
    onBack: () -> Unit,
    onCta: () -> Unit,
    modifier: Modifier = Modifier,
    canBack: Boolean = true,
    ctaText: String = "Continue →",
    ctaEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Column(modifier = modifier.fillMaxSize().background(colors.bg)) {
        WizardProgressBar(step)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = space.xl)
                .padding(top = space.xl),
        ) {
            if (canBack) {
                SignupBackButton(onClick = onBack)
                Spacer(Modifier.height(space.lg))
            }
            Text(title, style = AppTheme.type.displayMedium, color = colors.ink)
            if (subtitle != null) {
                Spacer(Modifier.height(space.sm))
                Text(subtitle, style = AppTheme.type.footnote, color = colors.ink3)
            }
            Spacer(Modifier.height(space.xl))
            content()
            Spacer(Modifier.height(space.xxl))
        }
        PrimaryButton(
            text = ctaText,
            onClick = onCta,
            fullWidth = true,
            enabled = ctaEnabled,
            modifier = Modifier.padding(horizontal = space.xl).padding(bottom = space.xxl),
        )
    }
}

/** The segment progress bar (iOS `ArtistWizardView.progressBar`): one filled segment per step up
 *  to the current one, hidden entirely on the Done step. */
@Composable
private fun WizardProgressBar(step: WizardStep) {
    if (step == WizardStep.Done) return
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val visible = WIZARD_FLOW.dropLast(1) // hide the Done segment
    val count = visible.size
    val idx = WIZARD_FLOW.indexOf(step).coerceIn(0, count - 1)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = space.xl, vertical = space.md),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(count) { i ->
            Box(
                Modifier
                    .weight(1f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (i <= idx) colors.brand else colors.bgSoft),
            )
        }
    }
}

/**
 * A selectable capsule chip (iOS wizard `chip`): white(ink)-fill + no border when on, hairline
 * outline when off. Shared by the location/availability/event-type/category chip groups.
 */
@Composable
fun WizardChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val shape = CircleShape
    Text(
        text = label,
        style = AppTheme.type.footnote.copy(fontWeight = FontWeight.SemiBold),
        color = if (selected) colors.bg else colors.ink2,
        modifier = modifier
            .clip(shape)
            .then(
                if (selected) Modifier.background(colors.ink, shape)
                else Modifier.border(1.dp, colors.line, shape),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = space.md, vertical = space.sm),
    )
}

/** Small-caps section label used inside the wizard steps (iOS `section` header). */
@Composable
fun WizardSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = AppTheme.type.caption, color = AppTheme.colors.ink3, modifier = modifier)
}
