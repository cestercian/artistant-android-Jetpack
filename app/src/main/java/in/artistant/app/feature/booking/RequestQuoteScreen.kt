package `in`.artistant.app.feature.booking

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.artistant.app.designsystem.component.DateCell
import `in`.artistant.app.designsystem.component.dateChipLines
import `in`.artistant.app.designsystem.component.dockSurface
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.feature.signup.EditorialHeadline

/** Custom quote request — port of iOS `RequestQuoteView`. */
@Composable
fun RequestQuoteScreen(
    onBack: () -> Unit,
    onSuccess: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RequestQuoteViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space

    if (state.success) {
        Column(
            modifier.fillMaxSize().background(colors.bg).padding(space.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(space.xxl))
            EditorialHeadline("Request ", "sent", ".", style = AppTheme.type.displayHero)
            Spacer(Modifier.height(space.md))
            Text(
                "${state.artistName.ifBlank { "The artist" }} will respond to your quote.",
                style = AppTheme.type.callout,
                color = colors.ink3,
            )
            Spacer(Modifier.height(space.xxl))
            PrimaryButton(text = "Done", onClick = onSuccess, fullWidth = true)
        }
        return
    }

    Column(modifier.fillMaxSize().background(colors.bg)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.ink)
            }
            Text("Request a quote", style = AppTheme.type.headline, color = colors.ink)
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(space.lg),
        ) {
            Text("Amount (INR)", style = AppTheme.type.caption, color = colors.ink3)
            Spacer(Modifier.height(space.xs))
            // Number pad, matching iOS `.keyboardType(.numberPad)`. Without it the
            // field opened the full text IME while `setAmount` strips every
            // non-digit as you type, so "25,000" lost its separator under the
            // client's finger with nothing to explain it, and a typed "₹" left the
            // field empty and the submit refusing with "Enter a valid amount."
            BasicTextField(
                value = state.amountInr,
                onValueChange = viewModel::setAmount,
                textStyle = AppTheme.type.monoMedium.copy(color = colors.ink),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = fieldModifier(),
            )
            Spacer(Modifier.height(space.lg))
            Text("Date", style = AppTheme.type.caption, color = colors.ink3)
            Spacer(Modifier.height(space.sm))
            // Same strip anatomy as the booking funnel: shared [DateCell] cards fed
            // by [dateChipLines], never a local lookalike. This site used to render
            // `chip.label.take(12)` inside a hand-rolled pill, which clipped the
            // canonical "EEE, MMM d, yyyy" label mid-token — "Wed, Aug 5, 2026"
            // reached the screen as "Wed, Aug 5, ", a dangling separator. Splitting
            // the date into a weekday line over a day numeral is what removes the
            // need to clip at all, so the bug can't come back by widening a count.
            //
            // Chips are remembered rather than re-derived per recomposition: the
            // rendered day now comes from `chip.epochMs`, and `dateChips()` stamps
            // each chip with `System.currentTimeMillis()` on every call, so an
            // un-remembered list would rebuild its epochs under the user mid-screen.
            val dateChips = remember(viewModel) { viewModel.dateChips() }
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(space.sm),
            ) {
                dateChips.forEach { chip ->
                    DateCell(
                        lines = dateChipLines(chip.epochMs),
                        isSelected = chip.label == state.dateLabel,
                        // This screen asks for a quote on any date — it carries no
                        // per-artist availability, so every chip is selectable and
                        // the dot reads free. No legend either: a legend over a
                        // strip with a single state would explain a distinction
                        // that isn't being drawn.
                        isFree = chip.available,
                        enabled = chip.available,
                        onClick = { viewModel.pickChip(chip) },
                    )
                }
            }
            Spacer(Modifier.height(space.lg))
            Text("Message", style = AppTheme.type.caption, color = colors.ink3)
            Spacer(Modifier.height(space.xs))
            BasicTextField(
                value = state.message,
                onValueChange = viewModel::setMessage,
                textStyle = AppTheme.type.body.copy(color = colors.ink),
                // Three lines of the field's own type, not a fraction of a hero
                // height: the box has to hold text, so it is measured in text.
                minLines = 3,
                // Prose about the event, so sentence case — the IME default is
                // no capitalization, which left every line starting lowercase.
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    capitalization = KeyboardCapitalization.Sentences,
                ),
                modifier = fieldModifier(),
            )
            state.errorMessage?.let {
                Spacer(Modifier.height(space.md))
                Text(it, style = AppTheme.type.footnote, color = colors.hot)
            }
        }
        Column(
            Modifier.dockSurface().padding(space.lg),
        ) {
            if (state.isSubmitting) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.accentInk)
                }
            } else {
                PrimaryButton(text = "Send request", onClick = viewModel::submit, fullWidth = true)
            }
        }
    }
}

@Composable
private fun fieldModifier(): Modifier {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val radii = AppTheme.dimens.radii
    return Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(radii.sm))
        .background(colors.bgSoft)
        .padding(space.md)
}
