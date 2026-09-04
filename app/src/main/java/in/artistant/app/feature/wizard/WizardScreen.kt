package `in`.artistant.app.feature.wizard

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.artistant.app.designsystem.component.Banner
import `in`.artistant.app.designsystem.component.BannerTone
import `in`.artistant.app.designsystem.component.EyebrowLabel
import `in`.artistant.app.designsystem.component.hairlineTop
import `in`.artistant.app.designsystem.component.IconCircle
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.component.SecondaryButton
import `in`.artistant.app.designsystem.component.SheetScaffold
import `in`.artistant.app.designsystem.component.pressScale
import `in`.artistant.app.designsystem.rememberHaptics
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.motion
import `in`.artistant.app.designsystem.theme.motionTween

/**
 * The artist onboarding wizard.
 *
 * ## Shape
 *
 * Fixed chrome top and bottom, one scrolling step between them: a back circle,
 * a progress track and a "03 / 10" counter across the top, the
 * step's own lazy list in the middle, and one CTA pinned at the bottom. The CTA
 * is pinned rather than scrolled because on a form the artist is typing into, a
 * Continue button that has scrolled off is indistinguishable from a Continue
 * button that is disabled.
 *
 * ## Why the chrome is not per-step
 *
 * Every step shares the same three affordances, so they live here once. The step
 * files own only what is different — which is also why the CTA's label, its
 * footnote and its enabled state come from [wizardCtaLabel], [wizardFooterNote]
 * and `canAdvance` rather than from a `when` inside each step.
 *
 * ## Where Save & exit lives
 *
 * The design's step bar carries a back circle and a counter and nothing else,
 * and Save & exit is a sheet (screen 72). The wizard is a mandatory gate with no
 * screen behind it, so that sheet has to stay reachable from every step or an
 * artist eight steps in has no way out but force-quitting. It hangs off the
 * leading circle on the FIRST step — where there is nowhere to go back to, so
 * the circle is a close rather than a chevron — and off a second, smaller close
 * beside the counter everywhere else.
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
    val haptics = rememberHaptics()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                WizardEvent.Finished -> onFinished()
                WizardEvent.Published -> haptics.success()
                WizardEvent.PublishFailed -> haptics.error()
            }
        }
    }

    // System back walks the flow rather than leaving it: the wizard is a gate,
    // and there is no screen behind it. On the first step, where there is no
    // previous step, it opens Save & exit — which is the honest answer to "I
    // want out of this", and the only one that keeps the draft.
    //
    // Deliberately still enabled while a publish is in flight: `back()` refuses
    // during that window, and disabling the handler instead would let the press
    // fall through and escape the gate mid-publish.
    BackHandler(enabled = state.step != WizardStep.Done) {
        if (state.step == WizardStep.Identity) confirmingExit = true else viewModel.back()
    }

    Column(
        modifier
            .fillMaxSize()
            .background(colors.surface)
            .statusBarsPadding(),
    ) {
        if (state.step != WizardStep.Done) {
            WizardTopBar(
                step = state.step,
                onBack = viewModel::back,
                onSaveAndExit = { confirmingExit = true },
            )
        }

        Box(Modifier.weight(1f)) {
            when {
                // A blank form that fills in a frame later reads as data loss;
                // hold the spinner until the draft has been consulted.
                state.isRestoring -> CircularProgressIndicator(
                    color = colors.accentInk,
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
            WizardFooter(state = state, onContinue = viewModel::next, onSkip = viewModel::next)
        }
    }

    if (confirmingExit) {
        SaveAndExitSheet(
            step = state.step,
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

/**
 * Back circle, progress track, counter — and the close that opens Save & exit.
 *
 * The Preview step draws the same bar with the track and counter swapped for its
 * own centred title (screen 45): review is not a twelfth thing to fill in, and
 * showing "10 / 11" over it would say it is.
 */
@Composable
private fun WizardTopBar(step: WizardStep, onBack: () -> Unit, onSaveAndExit: () -> Unit) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val first = step == WizardStep.Identity
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.component.gutter, vertical = dimens.space.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
    ) {
        IconCircle(
            icon = if (first) Icons.Filled.Close else Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = if (first) "Save and exit setup" else "Back",
            onClick = if (first) onSaveAndExit else onBack,
            modifier = Modifier.semantics { testTag = if (first) "wizard.saveAndExit" else "wizard.back" },
        )
        if (step == WizardStep.Preview) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    wizardStepTitle(step),
                    style = AppTheme.type.sectionTitle,
                    color = colors.ink,
                )
                Text(
                    wizardStepSubtitle(step),
                    style = AppTheme.type.caption,
                    color = colors.ink4,
                )
            }
        } else {
            WizardProgressBar(step, Modifier.weight(1f))
            wizardStepCounter(step)?.let { counter ->
                Text(
                    counter,
                    style = AppTheme.type.monoPill,
                    color = colors.ink4,
                    modifier = Modifier.semantics { testTag = "wizard.counter" },
                )
            }
        }
        if (!first) {
            // Save & exit has to stay reachable from a gate with no screen behind
            // it. On the first step it IS the leading circle; after that the
            // chevron owns that slot, so it moves here.
            IconCircle(
                icon = Icons.Filled.Close,
                contentDescription = "Save and exit setup",
                onClick = onSaveAndExit,
                size = dimens.component.iconCircleSm,
                modifier = Modifier.semantics { testTag = "wizard.saveAndExit" },
            )
        } else {
            Spacer(Modifier.width(dimens.component.iconCircleSm))
        }
    }
}

/**
 * The pinned CTA, plus the quiet line the design puts under it.
 *
 * `imePadding` so the keyboard lifts it instead of covering it — without it, the
 * identity and bio steps put the button under the keyboard the moment the field
 * takes focus, which is where "the wizard is stuck" reports come from.
 *
 * The footnote is a tap target on the optional steps ("Skip for now") and plain
 * copy everywhere else. Same word, two jobs, and the difference is whether the
 * CTA beside it already says it.
 */
@Composable
private fun WizardFooter(state: WizardUiState, onContinue: () -> Unit, onSkip: () -> Unit) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val note = wizardFooterNote(state)
    val skippable = note == SKIP_NOTE
    val interaction = remember { MutableInteractionSource() }
    Column(
        Modifier
            .fillMaxWidth()
            // Opaque, with a hairline over it. The step above is a scroller and
            // the CTA is pinned, so a transparent bar lets the last field slide
            // under the button and read as a rendering fault. The design draws
            // the same rule on every step that has something to scroll.
            .background(colors.surface)
            .hairlineTop()
            .navigationBarsPadding()
            .imePadding()
            .padding(
                horizontal = dimens.component.gutter,
                vertical = dimens.space.md,
            ),
        verticalArrangement = Arrangement.spacedBy(dimens.space.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The publish sequence narrates itself. A three-round-trip wait behind a
        // silent button is where artists tap twice.
        if (state.isPublishing) {
            Text(
                wizardPublishProgressLabel(state.publishPhase),
                style = AppTheme.type.caption,
                color = colors.ink3,
            )
        }
        PrimaryButton(
            text = wizardCtaLabel(state),
            onClick = onContinue,
            enabled = state.canAdvance,
            fullWidth = true,
            modifier = Modifier.semantics { testTag = "wizard.continue" },
        )
        if (note != null) {
            Text(
                note,
                style = AppTheme.type.caption.copy(
                    fontWeight = if (skippable) FontWeight.Bold else FontWeight.Medium,
                ),
                color = if (skippable) colors.ink3 else colors.ink4,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .then(
                        if (skippable) {
                            Modifier
                                .pressScale(interaction)
                                .clickable(
                                    interactionSource = interaction,
                                    indication = null,
                                    onClick = onSkip,
                                )
                        } else {
                            Modifier
                        },
                    )
                    .padding(dimens.space.sm)
                    .semantics { testTag = "wizard.footerNote" },
            )
        }
    }
}

/** The one string [wizardFooterNote] returns that is also an action. */
private const val SKIP_NOTE = "Skip for now"

/**
 * Save & exit (screen 72) — a sheet, not a dialog.
 *
 * It signs the artist out, which is an unrecoverable-feeling action from a stray
 * tap, so it asks first and spends the space saying exactly what survives: how
 * many steps are banked, and that the staged cover and clips are on disk rather
 * than in memory. The sentence "your progress is saved" on its own is the one
 * that gets read as marketing; the segment count under it is the evidence.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SaveAndExitSheet(step: WizardStep, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = colors.surface,
        contentColor = colors.ink,
    ) {
        SheetScaffold(showGrabber = true) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.width(dimens.component.iconCircleSm))
                Text(
                    "Save & exit",
                    style = AppTheme.type.sectionTitle,
                    color = colors.ink,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconCircle(
                    icon = Icons.Filled.Close,
                    contentDescription = "Keep going",
                    onClick = onDismiss,
                    size = dimens.component.iconCircleSm,
                )
            }
            Banner(
                title = "Your progress is saved.",
                tone = BannerTone.Info,
                detail = "You'll sign out and can pick up right here when you sign back in.",
                modifier = Modifier.padding(top = dimens.space.lg),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = dimens.space.xl),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
            ) {
                EyebrowLabel("Saved so far")
                Box(Modifier.weight(1f)) {
                    androidx.compose.material3.HorizontalDivider(
                        thickness = dimens.size.hairline,
                        color = colors.hairline,
                    )
                }
                Text(
                    wizardSavedSoFarLabel(step),
                    style = AppTheme.type.monoPill,
                    color = colors.ink,
                )
            }
            WizardProgressBar(step, Modifier.padding(top = dimens.space.md))
            Text(
                "Including your cover photo and clips — staged media is cached on disk, not held in memory.",
                style = AppTheme.type.caption,
                color = colors.ink4,
                modifier = Modifier.padding(top = dimens.space.md),
            )
            PrimaryButton(
                text = "Save and sign out",
                onClick = onConfirm,
                fullWidth = true,
                modifier = Modifier
                    .padding(top = dimens.space.xl)
                    .semantics { testTag = "wizard.confirmExit" },
            )
            SecondaryButton(
                text = "Keep going",
                onClick = onDismiss,
                fullWidth = true,
                modifier = Modifier.padding(top = dimens.space.md),
            )
        }
    }
}
