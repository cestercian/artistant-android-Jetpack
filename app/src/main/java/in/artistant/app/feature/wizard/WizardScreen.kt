package `in`.artistant.app.feature.wizard

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.component.pressScale
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.motion
import `in`.artistant.app.designsystem.theme.motionTween
import `in`.artistant.app.feature.signup.SignupBackButton
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut

/**
 * The artist onboarding wizard.
 *
 * ## Shape
 *
 * Fixed chrome top and bottom, one scrolling step between them: a back chevron
 * and Save & exit above the progress bar, the step's own lazy list in the
 * middle, and one CTA pinned at the bottom. The CTA is pinned rather than
 * scrolled because on a form the artist is typing into, a Continue button that
 * has scrolled off is indistinguishable from a Continue button that is disabled.
 *
 * ## Why the chrome is not per-step
 *
 * Every step shares the same three affordances, so they live here once. The step
 * files own only what is different — which is also why the CTA's label and
 * enabled state come from [wizardCtaLabel] and `canAdvance` rather than from a
 * `when` inside each step.
 */
@Composable
fun WizardScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WizardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    var confirmingExit by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                WizardEvent.Finished -> onFinished()
            }
        }
    }

    // System back walks the flow rather than leaving it: the wizard is a gate,
    // and there is no screen behind it. Disabled on the first and last steps,
    // where back would mean "leave" and Save & exit is the honest way to do that.
    // Deliberately still enabled while a publish is in flight: `back()` refuses
    // during that window, and disabling the handler instead would let the press
    // fall through and escape the gate mid-publish.
    BackHandler(enabled = state.step != WizardStep.Identity && state.step != WizardStep.Done) {
        viewModel.back()
    }

    Column(
        modifier
            .fillMaxSize()
            .background(colors.bg)
            .statusBarsPadding(),
    ) {
        if (state.step != WizardStep.Done) {
            WizardTopBar(
                canGoBack = state.step != WizardStep.Identity,
                onBack = viewModel::back,
                onSaveAndExit = { confirmingExit = true },
            )
            WizardProgressBar(state.step)
        }

        Box(Modifier.weight(1f)) {
            when {
                // A blank form that fills in a frame later reads as data loss;
                // hold the spinner until the draft has been consulted.
                state.isRestoring -> CircularProgressIndicator(
                    color = colors.brand,
                    modifier = Modifier.align(Alignment.Center),
                )
                state.step == WizardStep.Done -> WizardDoneScreen(
                    state = state,
                    onOpenDashboard = viewModel::finishFromDone,
                )
                else -> WizardStepContent(state, viewModel)
            }
        }

        if (state.step != WizardStep.Done) {
            WizardFooter(state = state, onContinue = viewModel::next)
        }
    }

    if (confirmingExit) {
        SaveAndExitDialog(
            onConfirm = {
                confirmingExit = false
                viewModel.saveAndExit()
            },
            onDismiss = { confirmingExit = false },
        )
    }
}

/**
 * The step body, cross-faded on change.
 *
 * Fade only, no slide: the steps are different lengths, and sliding a 2-row step
 * out while a 9-row step slides in makes the CTA jump. The duration collapses to
 * zero under reduce-motion, which turns this into a plain swap.
 */
@Composable
private fun WizardStepContent(state: WizardUiState, viewModel: WizardViewModel) {
    // Built here rather than inside `transitionSpec`, which is not a composable
    // scope. motionTween owns the reduce-motion branch, so there is no second
    // place for a call site to forget it.
    val fade = motionTween<Float>(AppTheme.motion.tabSwitch)
    AnimatedContent(
        targetState = state.step,
        transitionSpec = { fadeIn(fade) togetherWith fadeOut(fade) },
        label = "wizard.step",
    ) { step ->
        WizardStepScaffold(step) {
            when (step) {
                WizardStep.Identity -> identityStep(state, viewModel)
                WizardStep.Location -> locationStep(state, viewModel)
                WizardStep.Pricing -> pricingStep(state, viewModel)
                WizardStep.Tech -> techStep(state, viewModel)
                WizardStep.Availability -> availabilityStep(state, viewModel)
                WizardStep.Cover -> coverStep(state, viewModel)
                WizardStep.Socials -> socialsStep(state, viewModel)
                WizardStep.Bio -> bioStep(state, viewModel)
                WizardStep.Samples -> samplesStep(state, viewModel)
                WizardStep.Preview -> previewStep(state, viewModel)
                // Rendered outside the scaffold; this branch is unreachable.
                WizardStep.Done -> Unit
            }
        }
    }
}

@Composable
private fun WizardTopBar(canGoBack: Boolean, onBack: () -> Unit, onSaveAndExit: () -> Unit) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = space.xl, vertical = space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (canGoBack) {
            SignupBackButton(
                onClick = onBack,
                modifier = Modifier.semantics { testTag = "wizard.back" },
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            "Save & exit",
            style = AppTheme.type.footnote.copy(fontWeight = FontWeight.SemiBold),
            color = colors.ink3,
            modifier = Modifier
                .pressScale(interaction)
                .clickable(interactionSource = interaction, indication = null, onClick = onSaveAndExit)
                .padding(space.sm)
                .semantics { testTag = "wizard.saveAndExit" },
        )
    }
}

/**
 * The pinned CTA.
 *
 * `imePadding` so the keyboard lifts it instead of covering it — without it, the
 * identity and bio steps put the button under the keyboard the moment the field
 * takes focus, which is where "the wizard is stuck" reports come from.
 */
@Composable
private fun WizardFooter(state: WizardUiState, onContinue: () -> Unit) {
    val space = AppTheme.dimens.space
    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = space.xl, vertical = space.md),
        verticalArrangement = Arrangement.spacedBy(space.sm),
    ) {
        // The publish sequence narrates itself. A three-round-trip wait behind a
        // silent button is where artists tap twice.
        if (state.isPublishing) {
            Text(
                wizardPublishProgressLabel(state.publishPhase),
                style = AppTheme.type.caption,
                color = AppTheme.colors.ink3,
            )
        }
        PrimaryButton(
            text = wizardCtaLabel(state),
            onClick = onContinue,
            enabled = state.canAdvance,
            fullWidth = true,
            modifier = Modifier.semantics { testTag = "wizard.continue" },
        )
    }
}

/**
 * Save & exit is confirmed, not immediate.
 *
 * It signs the artist out — an unrecoverable-feeling action from a stray tap on
 * a header — so it asks first, and says in the same breath that the progress
 * survives. Without that sentence the honest reading of "Save & exit" is
 * ambiguous about which of the two words wins.
 */
@Composable
private fun SaveAndExitDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val colors = AppTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.bgElev,
        titleContentColor = colors.ink,
        textContentColor = colors.ink2,
        title = { Text("Save & exit setup?", style = AppTheme.type.headline) },
        text = {
            Text(
                "Your progress is saved. You'll sign out and can pick up right here when you sign back in.",
                style = AppTheme.type.footnote,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.semantics { testTag = "wizard.confirmExit" }) {
                Text("Save & exit", style = AppTheme.type.footnote, color = colors.brand)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Keep setting up", style = AppTheme.type.footnote, color = colors.ink3)
            }
        },
    )
}
