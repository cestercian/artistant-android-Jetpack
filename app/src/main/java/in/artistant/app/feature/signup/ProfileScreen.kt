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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
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
    val dimens = AppTheme.dimens
    val space = dimens.space
    var cityOpen by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize().background(colors.bg).statusBarsPadding()) {
        // Top chrome: hairline back chevron over the flow's shared progress bar.
        //
        // The bar was a second implementation of it inlined here — narrow
        // right-aligned segments with a third "past" state — so the indicator
        // changed shape and colour semantics between consecutive steps of one
        // flow. One component owns the treatment; this step only names its index.
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = space.xl, end = space.xl, top = space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                // The 30dp disc sits inside a `rowMin` tap target rather than being
                // grown to it — the touch floor is a hit area, not a size. Same
                // shape `SignupBackButton` takes on the other steps.
                Modifier
                    .size(dimens.size.rowMin)
                    .clip(CircleShape)
                    .clickable(onClick = onBack)
                    .semantics { testTag = "profile.back"; contentDescription = "Back" },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    // A circle, spelled as one. It used to be a 30dp box under a
                    // `RoundedCornerShape(15.dp)` — the same shape by arithmetic,
                    // but one that silently stops being a circle the day the box
                    // is resized. iOS draws `Circle().stroke(…)` here.
                    Modifier.size(30.dp).clip(CircleShape).border(dimens.size.hairline, colors.line, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    // A chevron, not a cross. The control goes BACK a signup step —
                    // it does not close or abandon the flow — and a cross is the
                    // universal "dismiss this whole thing" glyph. Same glyph the
                    // other signup steps use (`SignupBackButton`), so the affordance
                    // does not change meaning halfway through the flow.
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = null,
                        tint = colors.ink2,
                        modifier = Modifier.size(AppTheme.dimens.size.iconLg),
                    )
                }
            }
        }
        SignupProgressDots(bar = progressIndex(SignupStep.Profile, state.mode))

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
                style = AppTheme.type.signupDisplay,
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
                            // Same step as a field's typed line: the city row is
                            // one of the three inputs, and it reading a size
                            // below the other two made it look like a caption.
                            style = AppTheme.type.headline,
                            color = if (state.city.isEmpty()) colors.ink4 else colors.ink,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = colors.ink3, modifier = Modifier.size(AppTheme.dimens.size.iconMd))
                    }
                    Box(Modifier.fillMaxWidth().height(dimens.size.hairline).background(if (state.city.isEmpty()) colors.line else colors.brand.copy(alpha = 0.4f)))
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
        //
        // `navigationBarsPadding().imePadding()` for the same reason WizardFooter
        // carries them: the window is edge-to-edge and does not resize for the
        // keyboard, so without the ime inset the CTA — and the bottom of the
        // scroll region above it, which is weighted against this bar — sit under
        // the keyboard the moment Handle or Name takes focus.
        val disabled = state.isSaving || !state.profileValid
        Box(
            modifier = Modifier
                .fillMaxWidth().navigationBarsPadding().imePadding()
                .padding(horizontal = space.xl).padding(bottom = space.xxl).height(54.dp)
                .clip(RoundedCornerShape(dimens.radii.buttonLg))
                .border(dimens.size.strokeEmphasis, if (disabled) colors.line else colors.brand, RoundedCornerShape(dimens.radii.buttonLg))
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
    val dimens = AppTheme.dimens
    when (status) {
        HandleStatus.Empty -> Unit
        HandleStatus.Invalid -> Text("3–24 · a–z 0–9 _", style = AppTheme.type.caption, color = colors.warm)
        HandleStatus.Checking -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dimens.space.xs)) {
            CircularProgressIndicator(color = colors.ink3, strokeWidth = dimens.size.strokeEmphasis, modifier = Modifier.size(dimens.size.iconSm))
            Text("Checking…", style = AppTheme.type.monoSmall, color = colors.ink3)
        }
        HandleStatus.Available -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dimens.space.xs)) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = colors.brand, modifier = Modifier.size(dimens.size.iconSm))
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
