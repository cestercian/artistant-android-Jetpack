package `in`.artistant.app.feature.wizard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import `in`.artistant.app.designsystem.theme.AppTheme

/**
 * The frame every wizard step draws inside: a plain 26/700 question, one line of
 * subtitle, then the step's own rows.
 *
 * ## One lazy container per step
 *
 * The scaffold owns a single [LazyColumn] and steps contribute [LazyListScope]
 * items into it. A form-heavy screen is easy to make slow, and the two ways it
 * usually happens are a nested scroller (which forces its children to measure at
 * infinite height, so every row composes even when eight of them are off screen)
 * and a `verticalScroll` `Column`, which does the same thing by construction.
 * The pricing step alone can hold six tiers x four fields; keeping them lazy is
 * the difference between composing what is visible and composing all of it on
 * every keystroke.
 *
 * The headline is an item rather than a fixed header so it scrolls away — on a
 * short phone with the keyboard up, a pinned 26sp headline costs a third of the
 * remaining space.
 */
@Composable
fun WizardStepScaffold(
    step: WizardStep,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    val dimens = AppTheme.dimens
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .semantics { testTag = "wizard.step.${step.name.lowercase()}" },
        contentPadding = PaddingValues(
            start = dimens.component.gutter,
            end = dimens.component.gutter,
            top = dimens.space.sm,
            // Tailroom so the last row clears the footer CTA rather than sitting
            // under it — the alternative is an artist who cannot see the field
            // they are typing into.
            bottom = dimens.space.xxl,
        ),
        verticalArrangement = Arrangement.spacedBy(dimens.space.xl),
    ) {
        item(key = "wizard.headline") { WizardStepHeader(step) }
        content()
    }
}

/**
 * The question, then what answering it does.
 *
 * Both are plain — no tinted run, no italic. The light design spends its one
 * accent on the CTA, and a headline that borrows it makes the button stop being
 * the only thing on the page asking to be pressed.
 */
@Composable
private fun WizardStepHeader(step: WizardStep) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val subtitle = wizardStepSubtitle(step)
    Column {
        Text(
            wizardStepTitle(step),
            style = AppTheme.type.screenTitle,
            color = colors.ink,
        )
        if (subtitle.isNotEmpty()) {
            Spacer(Modifier.height(dimens.space.sm))
            Text(subtitle, style = AppTheme.type.body, color = colors.ink3)
        }
    }
}

/**
 * The segmented progress track: eleven pills, filled for the steps left behind.
 *
 * Filled means FINISHED, not "reached" — the current step's segment stays grey
 * until the artist advances off it. That is what the counter beside it says too
 * ("02 / 11" with one segment lit), and the Save & exit sheet repeats the same
 * arithmetic in words. Three surfaces, one rule.
 *
 * Segments are driven off the flow order rather than the enum's ordinal — see
 * [WizardFlowOrder] for why those are allowed to diverge.
 */
@Composable
fun WizardProgressBar(step: WizardStep, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val index = wizardProgressIndex(step) ?: return
    val total = wizardProgressTotal()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                testTag = "wizard.progress"
                contentDescription = "Step ${index + 1} of $total"
            },
        horizontalArrangement = Arrangement.spacedBy(dimens.space.xs),
    ) {
        repeat(total) { i ->
            Box(
                Modifier
                    .weight(1f)
                    // 3dp: at `space.xs` an eleven-cell track reads as a row
                    // of dashes competing with the headline under it, and a
                    // hairline disappears against `page`. The dashboard's
                    // meter is the same thin-bar-that-must-register problem,
                    // already measured.
                    .height(dimens.dashboard.meterHeight)
                    .clip(CircleShape)
                    .background(if (i < index) colors.accent else colors.hairline),
            )
        }
    }
}
