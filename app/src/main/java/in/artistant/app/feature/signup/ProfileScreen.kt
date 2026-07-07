package `in`.artistant.app.feature.signup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.KeyboardType
import `in`.artistant.app.designsystem.theme.AppTheme

/** Supported booking cities — shared with the artist wizard on iOS (AppEnvironment.supportedCities). */
private val cities = listOf("Bangalore", "Chennai", "Delhi", "Goa", "Hyderabad", "Kolkata", "Mumbai", "Pune")

/**
 * Profile basics (iOS `SignupProfileView`): handle + name + city. Continue is disabled until the
 * handle is available and name + city are filled; on tap the container upserts the row then
 * advances. Quiet-editorial treatment: mono kicker, heavy-sans italic-accent headline, hairline
 * underline fields with a live handle indicator, an outlined lime CTA.
 */
@Composable
fun ProfileScreen(
    state: SignupUiState,
    onHandleChange: (String) -> Unit,
    onNameChange: (String) -> Unit,
    onCityChange: (String) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    var cityOpen by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize().background(colors.bg)) {
        // Top chrome: hairline back chevron + compact right-aligned 5-segment progress (step 4).
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = space.xl, end = space.xl, top = space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(30.dp).clip(RoundedCornerShape(15.dp)).border(1.dp, colors.line, RoundedCornerShape(15.dp))
                    .clickable(onClick = onBack).semantics { testTag = "profile.back"; contentDescription = "Back" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Close, contentDescription = null, tint = colors.ink2, modifier = Modifier.size(AppTheme.dimens.size.iconSm))
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(5) { i ->
                    Box(
                        Modifier.width(16.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(
                            when {
                                i == 3 -> colors.brand
                                i < 3 -> colors.brand.copy(alpha = 0.45f)
                                else -> Color.White.copy(alpha = 0.16f)
                            },
                        ),
                    )
                }
            }
        }

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = space.xl).padding(top = space.lg),
        ) {
            Text(
                "04 — ABOUT YOU",
                style = AppTheme.type.monoSmall.copy(fontWeight = FontWeight.Bold),
                color = colors.ink3,
                modifier = Modifier.semantics { testTag = "profile.kicker" },
            )
            Spacer(Modifier.height(space.lg))
            // Heavy-sans headline with italic-lime "in" accent (iOS uses sans here, not serif).
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = colors.ink)) { append("A few words,\nthen we're ") }
                    withStyle(SpanStyle(color = colors.brand, fontStyle = FontStyle.Italic)) { append("in") }
                    withStyle(SpanStyle(color = colors.ink)) { append(".") }
                },
                style = AppTheme.type.title.copy(fontSize = 44.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.height(space.lg))
            Text("This is what artists see when you book.", style = AppTheme.type.footnote, color = colors.ink3)

            Spacer(Modifier.height(space.xxl))

            // Handle — with the live availability indicator + status-tinted underline.
            SignupInputRow(
                label = "Handle",
                value = state.handle,
                onValueChange = onHandleChange,
                placeholder = "yourname",
                prefix = "@",
                keyboardType = KeyboardType.Ascii,
                underline = handleUnderline(state.handleStatus, colors),
                modifier = Modifier.semantics { testTag = "profile.handle" },
                trailing = { HandleIndicator(state.handleStatus) },
            )
            Spacer(Modifier.height(space.xl))
            SignupInputRow(
                label = "Name",
                value = state.name,
                onValueChange = onNameChange,
                placeholder = "First and last",
                modifier = Modifier.semantics { testTag = "profile.name" },
            )
            Spacer(Modifier.height(space.xl))

            // City — single editorial row opening a dropdown.
            Column(verticalArrangement = Arrangement.spacedBy(space.sm)) {
                Text("CITY", style = AppTheme.type.caption, color = colors.ink3)
                Box {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { cityOpen = true }.padding(vertical = space.sm)
                            .semantics { testTag = "profile.city" },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            state.city.ifEmpty { "Choose your city" },
                            style = AppTheme.type.body.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
                            color = if (state.city.isEmpty()) colors.ink4 else colors.ink,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = colors.ink3, modifier = Modifier.size(AppTheme.dimens.size.iconMd))
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(if (state.city.isEmpty()) colors.line else colors.brand.copy(alpha = 0.4f)))
                    DropdownMenu(expanded = cityOpen, onDismissRequest = { cityOpen = false }) {
                        cities.forEach { c ->
                            DropdownMenuItem(
                                text = { Text(c, color = colors.ink) },
                                trailingIcon = { if (state.city == c) Icon(Icons.Filled.Check, contentDescription = null, tint = colors.brand) },
                                onClick = { onCityChange(c); cityOpen = false },
                            )
                        }
                    }
                }
            }

            state.saveError?.let {
                Spacer(Modifier.height(space.lg))
                Text(it, style = AppTheme.type.footnote.copy(fontWeight = FontWeight.SemiBold), color = colors.hot)
            }
            Spacer(Modifier.height(space.xl))
        }

        // Outlined lime CTA (kept local so only this step is outlined, matching iOS).
        val disabled = state.isSaving || !state.profileValid
        Box(
            modifier = Modifier
                .fillMaxWidth().padding(horizontal = space.xl).padding(bottom = space.xxl).height(54.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(1.5.dp, if (disabled) colors.line else colors.brand, RoundedCornerShape(16.dp))
                .clickable(enabled = !disabled, onClick = onContinue)
                .semantics { testTag = "profile.continue" },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (state.isSaving) "Saving…" else "Continue →",
                style = AppTheme.type.body.copy(fontWeight = FontWeight.Black),
                color = if (disabled) colors.ink4 else colors.brand,
            )
        }
    }
}

/** Live handle status chip (iOS `handleStatusIndicator`): spinner / free tick / taken / hint. */
@Composable
private fun HandleIndicator(status: HandleStatus) {
    val colors = AppTheme.colors
    when (status) {
        HandleStatus.Empty -> Unit
        HandleStatus.Invalid -> Text("3–24 · a–z 0–9 _", style = AppTheme.type.caption, color = colors.warm)
        HandleStatus.Checking -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            CircularProgressIndicator(color = colors.ink3, strokeWidth = 1.5.dp, modifier = Modifier.size(12.dp))
            Text("Checking…", style = AppTheme.type.monoSmall, color = colors.ink3)
        }
        HandleStatus.Available -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = colors.brand, modifier = Modifier.size(AppTheme.dimens.size.iconSm))
            Text("free", style = AppTheme.type.monoSmall, color = colors.brand)
        }
        HandleStatus.Taken -> Text("Taken", style = AppTheme.type.caption, color = colors.hot)
        HandleStatus.Error -> Text("Couldn't check", style = AppTheme.type.caption, color = colors.warm)
    }
}

private fun handleUnderline(status: HandleStatus, colors: `in`.artistant.app.designsystem.theme.AppColors): Color? = when (status) {
    HandleStatus.Available -> colors.brand
    HandleStatus.Taken, HandleStatus.Invalid -> colors.hot.copy(alpha = 0.5f)
    HandleStatus.Checking, HandleStatus.Error -> colors.brand.copy(alpha = 0.4f)
    HandleStatus.Empty -> null // let SignupInputRow default (line, or brand-tint on non-empty)
}
