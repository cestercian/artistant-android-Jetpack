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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextOverflow
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.feature.signup.EditorialHeadline

/**
 * The frame every wizard step draws inside: editorial headline, one line of
 * subtitle, then the step's own rows.
 *
 * ## One lazy container per step
 *
 * The scaffold owns a single [LazyColumn] and steps contribute [LazyListScope]
 * items into it. A form-heavy screen is easy to make slow, and the two ways it
 * usually happens are a nested scroller (which forces its children to measure at
 * infinite height, so every row composes even when eight of them are off screen)
 * and a `verticalScroll` `Column`, which does the same thing by construction.
 * The pricing step alone can hold six tiers × four fields; keeping them lazy is
 * the difference between composing what is visible and composing all of it on
 * every keystroke.
 *
 * The headline is an item rather than a fixed header so it scrolls away — on a
 * short phone with the keyboard up, a pinned 28sp serif headline costs a third
 * of the remaining space.
 */
@Composable
fun WizardStepScaffold(
    step: WizardStep,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    val space = AppTheme.dimens.space
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .semantics { testTag = "wizard.step.${step.name.lowercase()}" },
        contentPadding = PaddingValues(
            start = space.xl,
            end = space.xl,
            top = space.md,
            // Tailroom so the last row clears the footer CTA rather than sitting
            // under it — the alternative is an artist who cannot see the field
            // they are typing into.
            bottom = space.xxl,
        ),
        verticalArrangement = Arrangement.spacedBy(space.lg),
    ) {
        item(key = "wizard.headline") { WizardStepHeader(step) }
        content()
    }
}

@Composable
private fun WizardStepHeader(step: WizardStep) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val headline = wizardStepHeadline(step)
    val subtitle = wizardStepSubtitle(step)
    Column {
        EditorialHeadline(
            lead = headline.lead,
            accent = headline.accent,
            tail = headline.tail,
            style = AppTheme.type.displayMedium,
        )
        if (subtitle.isNotEmpty()) {
            Spacer(Modifier.height(space.sm))
            Text(
                subtitle,
                style = AppTheme.type.footnote,
                color = colors.ink3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The thin segmented progress bar.
 *
 * Segments are driven off the flow order, not the enum's ordinal — see
 * [WizardFlowOrder]. Filled up to and including the current step so the artist
 * reads "this is the one I'm on" rather than "this is the one I finished".
 */
@Composable
fun WizardProgressBar(step: WizardStep, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val index = wizardProgressIndex(step) ?: return
    val total = wizardProgressTotal()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = space.xl, vertical = space.sm)
            .semantics { testTag = "wizard.progress" },
        horizontalArrangement = Arrangement.spacedBy(space.xs),
    ) {
        repeat(total) { i ->
            Box(
                Modifier
                    .weight(1f)
                    .height(AppTheme.dimens.size.stroke)
                    .clip(RoundedCornerShape(AppTheme.dimens.radii.sm))
                    .background(if (i <= index) colors.brand else colors.bgSoft),
            )
        }
    }
}
