package `in`.artistant.app.feature.booking

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    val radii = AppTheme.dimens.radii

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
            BasicTextField(
                value = state.amountInr,
                onValueChange = viewModel::setAmount,
                textStyle = AppTheme.type.monoMedium.copy(color = colors.ink),
                modifier = fieldModifier(),
            )
            Spacer(Modifier.height(space.lg))
            Text("Date", style = AppTheme.type.caption, color = colors.ink3)
            Spacer(Modifier.height(space.sm))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                viewModel.dateChips().forEach { chip ->
                    val selected = chip.label == state.dateLabel
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(radii.sm))
                            .background(if (selected) colors.brand else colors.bg)
                            .border(1.dp, if (selected) colors.brand else colors.line, RoundedCornerShape(radii.sm))
                            .clickable { viewModel.pickChip(chip) }
                            .padding(horizontal = space.md, vertical = space.sm),
                    ) {
                        Text(
                            chip.label.take(12),
                            style = AppTheme.type.caption,
                            color = if (selected) colors.brandInk else colors.ink,
                        )
                    }
                }
            }
            Spacer(Modifier.height(space.lg))
            Text("Message", style = AppTheme.type.caption, color = colors.ink3)
            Spacer(Modifier.height(space.xs))
            BasicTextField(
                value = state.message,
                onValueChange = viewModel::setMessage,
                textStyle = AppTheme.type.body.copy(color = colors.ink),
                modifier = fieldModifier().height(AppTheme.dimens.size.heroShort / 4),
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
                    CircularProgressIndicator(color = colors.brand)
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
