package `in`.artistant.app.feature.wizard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import `in`.artistant.app.designsystem.theme.AppTheme

/** Step 3 — pricing tiers, prefilled from the chosen category, editable here (iOS `ArtistPricingStep`). */
@Composable
fun WizardPricingStep(state: WizardUiState, vm: WizardViewModel) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    WizardScaffold(
        step = WizardStep.Pricing,
        title = "Set your pricing",
        subtitle = "We seeded these from your category — tweak to taste.",
        ctaEnabled = state.pricingValid,
        onBack = vm::back,
        onCta = vm::advance,
    ) {
        state.packages.forEach { tier ->
            TierCard(tier = tier, onChange = vm::updateTier, onRemove = { vm.removeTier(tier.id) })
            Spacer(Modifier.height(space.md))
        }
        // Dashed "add tier" affordance.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, colors.brand.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                .clickable(onClick = vm::addTier)
                .padding(vertical = space.lg),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = colors.brand, modifier = Modifier.size(AppTheme.dimens.size.iconMd))
            Spacer(Modifier.width(6.dp))
            Text("Add tier", style = AppTheme.type.footnote.copy(fontWeight = FontWeight.SemiBold), color = colors.brand)
        }
    }
}

@Composable
private fun TierCard(tier: PricingTier, onChange: (PricingTier) -> Unit, onRemove: () -> Unit) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.bgCard)
            .padding(space.lg),
        verticalArrangement = Arrangement.spacedBy(space.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BareField(
                value = tier.name,
                onValueChange = { onChange(tier.copy(name = it)) },
                placeholder = "Tier name",
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Filled.Delete,
                contentDescription = "Remove tier",
                tint = colors.hot,
                modifier = Modifier.size(AppTheme.dimens.size.iconLg).clickable(onClick = onRemove),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(space.md), verticalAlignment = Alignment.CenterVertically) {
            SoftField(
                value = tier.duration,
                onValueChange = { onChange(tier.copy(duration = it)) },
                placeholder = "Duration",
                modifier = Modifier.weight(1f),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.bgSoft)
                    .padding(horizontal = space.md),
            ) {
                Text("₹", style = AppTheme.type.body, color = colors.ink3)
                SoftInner(
                    value = if (tier.price == 0) "" else tier.price.toString(),
                    onValueChange = { raw -> onChange(tier.copy(price = raw.filter { it.isDigit() }.toIntOrNull() ?: 0)) },
                    placeholder = "20000",
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Popular", style = AppTheme.type.footnote.copy(fontWeight = FontWeight.SemiBold), color = colors.ink2, modifier = Modifier.weight(1f))
            Switch(
                checked = tier.popular,
                onCheckedChange = { onChange(tier.copy(popular = it)) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.brandInk,
                    checkedTrackColor = colors.brand,
                    uncheckedThumbColor = colors.ink3,
                    uncheckedTrackColor = colors.bgSoft,
                ),
            )
        }
    }
}

/** Transparent-container single-line field (no Material chrome) used for the tier name. */
@Composable
private fun BareField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = colors.ink4) },
        singleLine = true,
        textStyle = LocalTextStyle.current.copy(color = colors.ink, fontWeight = FontWeight.SemiBold),
        colors = transparentFieldColors(),
        modifier = modifier,
    )
}

/** bgSoft-filled compact field (duration). */
@Composable
private fun SoftField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = colors.ink4) },
        singleLine = true,
        textStyle = LocalTextStyle.current.copy(color = colors.ink),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = colors.bgSoft,
            unfocusedContainerColor = colors.bgSoft,
            cursorColor = colors.brand,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier,
    )
}

/** Transparent inner field for the price (sits inside the ₹ row). */
@Composable
private fun SoftInner(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = colors.ink4) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        textStyle = LocalTextStyle.current.copy(color = colors.ink, fontWeight = FontWeight.SemiBold),
        colors = transparentFieldColors(),
        modifier = modifier,
    )
}

@Composable
private fun transparentFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    cursorColor = AppTheme.colors.brand,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
)
