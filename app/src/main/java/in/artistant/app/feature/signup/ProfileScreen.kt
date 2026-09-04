package `in`.artistant.app.feature.signup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.WarningAmber
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import `in`.artistant.app.data.model.HandleRules
import `in`.artistant.app.designsystem.component.AppTextField
import `in`.artistant.app.designsystem.component.Banner
import `in`.artistant.app.designsystem.component.BannerTone
import `in`.artistant.app.designsystem.component.Chip
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.ArtistantTheme

/** Supported booking cities — shared with the artist wizard on iOS (AppEnvironment.supportedCities). */
private val cities = listOf("Bangalore", "Chennai", "Delhi", "Goa", "Hyderabad", "Kolkata", "Mumbai", "Pune")

/** The handle field's ceiling, from `HandleRules`' own `^[a-z0-9_]{3,24}$`. */
private const val HANDLE_MAX = 24

/**
 * Screens 29 and 90 — pick a handle, and the same screen with the handle already taken.
 *
 * **Four live states, and the fourth is the point.** Checking, available, taken, and
 * couldn't-check are four different things and the design insists they look like four
 * different things: "the last one is stated, never faked." So a failed availability call
 * renders as a warm "Couldn't check" and never as a green tick — while still leaving Continue
 * tappable, because a network blip is not a reason to wedge someone out of their own signup
 * and the `users_handle_key` unique constraint is the real backstop (see
 * [SignupUiState.handleAvailable]). If the handle turns out to be taken, the upsert says so
 * and the flow bounces straight back to this field.
 *
 * **Taken offers a way out.** Screen 90 pairs the red field with four suggestions, which
 * [HandleSuggestions] generates from the typed name and the chosen city. They are suggestions,
 * not reservations: tapping one puts it in the field and it goes through the same live check
 * as anything else.
 *
 * The design's "THE OTHER THREE STATES" panel on screen 90 is deliberately NOT drawn — it is
 * the designer documenting the state set inside the canvas, not a control. What it documents
 * is implemented instead, which is the same information in the place it belongs.
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
    hydrationError: String? = null,
    onRetryHydration: () -> Unit = {},
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val space = dimens.space
    var cityOpen by remember { mutableStateOf(false) }
    val bar = progressIndex(SignupStep.Profile, state.mode)

    SignupScaffold(
        modifier = modifier.semantics { testTag = "screen.profile" },
        header = {
            SignupHeader(
                onBack = onBack,
                middle = { SignupProgressStrip(bar) },
                trailing = {
                    if (bar != null) {
                        Text(
                            bar.label,
                            style = AppTheme.type.caption.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.ink4,
                        )
                    }
                },
            )
        },
        footer = {
            PrimaryButton(
                text = if (state.isSaving) "Saving…" else "Continue",
                onClick = onContinue,
                fullWidth = true,
                enabled = !state.isSaving && state.profileValid,
                modifier = Modifier.semantics { testTag = "profile.continue" },
            )
        },
    ) {
        // The gate enters the flow here after a failed profile hydration, so this is the screen
        // that has to carry the failure — see [HydrationErrorBanner]. Design screen 71 draws it
        // on the role picker, which is one back-press behind this one and shows it too.
        if (hydrationError != null) {
            Spacer(Modifier.height(space.sm))
            HydrationErrorBanner(detail = hydrationError, onRetry = onRetryHydration)
        }
        Spacer(Modifier.height(space.md))
        Text("Pick a handle", style = AppTheme.type.screenTitle, color = colors.ink)
        Spacer(Modifier.height(space.sm))
        Text(
            "Your address on Artistant. Lowercase, numbers and underscores.",
            style = AppTheme.type.subtitle,
            color = colors.ink4,
        )

        Spacer(Modifier.height(space.xl))
        AppTextField(
            value = state.handle,
            onValueChange = onHandleChange,
            label = "Handle",
            hint = "yourname",
            enabled = !state.isSaving,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Next,
            ),
            leading = { Text("@", style = AppTheme.type.body, color = colors.ink4) },
            trailing = { HandleIndicator(state.handleStatus) },
            modifier = Modifier
                .handleRing(state.handleStatus)
                .semantics { testTag = "profile.handle" },
        )
        Spacer(Modifier.height(space.sm))
        HandleHelper(state.handle, state.handleStatus)

        // Screen 90: the alternatives. Only when the handle is genuinely taken — the other
        // three states have nothing to suggest, and a rail of chips under a field that is
        // fine would read as a requirement to use one of them.
        if (state.handleStatus == HandleStatus.Taken) {
            val alternatives = remember(state.handle, state.city) {
                HandleSuggestions.alternatives(state.handle, state.city)
            }
            if (alternatives.isNotEmpty()) {
                Spacer(Modifier.height(space.lg))
                SignupEyebrow("TRY ONE OF THESE")
                Spacer(Modifier.height(space.sm))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(space.sm),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { testTag = "profile.handleAlternatives" },
                ) {
                    alternatives.take(ALTERNATIVES_PER_ROW).forEach { suggestion ->
                        Chip(
                            label = suggestion,
                            selected = false,
                            onClick = { onHandleChange(suggestion) },
                        )
                    }
                }
                if (alternatives.size > ALTERNATIVES_PER_ROW) {
                    Spacer(Modifier.height(space.sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                        alternatives.drop(ALTERNATIVES_PER_ROW).forEach { suggestion ->
                            Chip(
                                label = suggestion,
                                selected = false,
                                onClick = { onHandleChange(suggestion) },
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(space.lg))
        AppTextField(
            value = state.name,
            onValueChange = onNameChange,
            label = "Name",
            hint = "First and last",
            enabled = !state.isSaving,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next,
            ),
            modifier = Modifier.semantics { testTag = "profile.name" },
        )

        Spacer(Modifier.height(space.md))
        FieldLabel("City")
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(dimens.radii.control))
                    .background(colors.surface2)
                    .border(
                        dimens.size.hairline,
                        colors.hairline,
                        RoundedCornerShape(dimens.radii.control),
                    )
                    .defaultMinSize(minHeight = dimens.component.control)
                    .clickable(enabled = !state.isSaving, role = Role.Button) { cityOpen = true }
                    .padding(horizontal = space.lg, vertical = space.md)
                    .semantics(mergeDescendants = true) {
                        testTag = "profile.city"
                        contentDescription = "City. ${state.city.ifEmpty { "Not chosen" }}"
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    state.city.ifEmpty { "Choose your city" },
                    style = AppTheme.type.body.copy(fontWeight = FontWeight.Medium),
                    color = if (state.city.isEmpty()) colors.hint else colors.ink,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = colors.ink4,
                    modifier = Modifier.size(dimens.size.iconLg),
                )
            }
            DropdownMenu(
                expanded = cityOpen,
                onDismissRequest = { cityOpen = false },
                containerColor = colors.surface,
            ) {
                cities.forEach { c ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                c,
                                style = AppTheme.type.body,
                                color = colors.ink,
                            )
                        },
                        trailingIcon = {
                            if (state.city == c) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = colors.accentInk,
                                )
                            }
                        },
                        onClick = { onCityChange(c); cityOpen = false },
                    )
                }
            }
        }
        Spacer(Modifier.height(space.sm))
        Text(
            "Name is what artists see when you book.",
            style = AppTheme.type.caption,
            color = colors.ink4,
        )

        state.saveError?.let { message ->
            Spacer(Modifier.height(space.md))
            Banner(
                title = message,
                tone = BannerTone.Failure,
                modifier = Modifier.semantics { testTag = "profile.saveError" },
            )
        }

        Spacer(Modifier.height(space.lg))
        Banner(
            title = "Checked live against the server. If we can't reach it we'll say so rather " +
                "than let you pick a handle that's taken.",
            tone = BannerTone.Note,
        )
        Spacer(Modifier.height(space.xl))
    }
}

/** Four chips at most, two to a row — which is how the design wraps them. */
private const val ALTERNATIVES_PER_ROW = 2

/**
 * The status ring around the handle field.
 *
 * A border on top of [AppTextField]'s own, drawn only for the two states that have a colour to
 * say — available is accent, taken is danger. The other three keep the field's default chrome,
 * because "checking" and "couldn't check" are things the CHIP says; painting the whole field
 * for them would make a transient state look like a rejection.
 */
@Composable
private fun Modifier.handleRing(status: HandleStatus): Modifier {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val ring: Color? = when (status) {
        HandleStatus.Available -> colors.accent
        HandleStatus.Taken -> colors.danger
        else -> null
    }
    return if (ring == null) {
        this
    } else {
        this.border(
            dimens.component.focusStroke,
            ring,
            RoundedCornerShape(dimens.radii.control),
        )
    }
}

/** The line under the field: the address the handle becomes, and how much room is left. */
@Composable
private fun HandleHelper(handle: String, status: HandleStatus) {
    val colors = AppTheme.colors
    if (status == HandleStatus.Taken) {
        Text(
            "Someone already has @${HandleRules.normalize(handle)}.",
            style = AppTheme.type.caption.copy(fontWeight = FontWeight.SemiBold),
            color = colors.danger,
            modifier = Modifier.semantics { testTag = "profile.handleTaken" },
        )
        return
    }
    Text(
        if (handle.isEmpty()) {
            "3–24 characters. Letters, numbers and underscores."
        } else {
            "artistant.in/@$handle · ${handle.length} of $HANDLE_MAX characters"
        },
        style = AppTheme.type.caption,
        color = colors.ink4,
    )
}

/**
 * The live status chip (iOS `handleStatusIndicator`), in the design's four states.
 *
 * `Error` is its own visible state — "Couldn't check", in `warm` — and never borrows the
 * available tick. That is the whole of the design's note for screen 90: "couldn't check never
 * masquerades as available."
 */
@Composable
private fun HandleIndicator(status: HandleStatus) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val gap = Arrangement.spacedBy(dimens.space.xs)
    when (status) {
        HandleStatus.Empty -> Unit
        HandleStatus.Invalid -> Text(
            "3–24 · a–z 0–9 _",
            style = AppTheme.type.caption,
            color = colors.warm,
        )
        HandleStatus.Checking -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = gap,
            modifier = Modifier.semantics { testTag = "profile.handleChecking" },
        ) {
            CircularProgressIndicator(
                color = colors.ink3,
                strokeWidth = dimens.size.hairline,
                modifier = Modifier.size(dimens.size.iconSm),
            )
            Text("Checking…", style = AppTheme.type.caption, color = colors.ink3)
        }
        HandleStatus.Available -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = gap,
            modifier = Modifier.semantics { testTag = "profile.handleAvailable" },
        ) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = colors.accentDeep,
                modifier = Modifier.size(dimens.size.iconMd),
            )
            Text(
                "Available",
                style = AppTheme.type.caption.copy(fontWeight = FontWeight.Bold),
                color = colors.accentDeep,
            )
        }
        HandleStatus.Taken -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = gap,
        ) {
            Icon(
                Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = colors.danger,
                modifier = Modifier.size(dimens.size.iconMd),
            )
            Text(
                "Taken",
                style = AppTheme.type.caption.copy(fontWeight = FontWeight.Bold),
                color = colors.danger,
            )
        }
        HandleStatus.Error -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = gap,
            modifier = Modifier.semantics { testTag = "profile.handleUnchecked" },
        ) {
            Icon(
                Icons.Filled.WarningAmber,
                contentDescription = null,
                tint = colors.warm,
                modifier = Modifier.size(dimens.size.iconMd),
            )
            Text(
                "Couldn't check",
                style = AppTheme.type.caption.copy(fontWeight = FontWeight.Bold),
                color = colors.warm,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, heightDp = 900)
@Composable
private fun HandleAvailablePreview() {
    ArtistantTheme {
        ProfileScreen(
            state = SignupUiState(
                handle = "rheamenon",
                handleStatus = HandleStatus.Available,
                name = "Rhea Menon",
                city = "Bangalore",
            ),
            onHandleChange = {},
            onNameChange = {},
            onCityChange = {},
            onBack = {},
            onContinue = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, heightDp = 900)
@Composable
private fun HandleTakenPreview() {
    ArtistantTheme {
        ProfileScreen(
            state = SignupUiState(
                handle = "tilt",
                handleStatus = HandleStatus.Taken,
                name = "Rhea Menon",
                city = "Bangalore",
            ),
            onHandleChange = {},
            onNameChange = {},
            onCityChange = {},
            onBack = {},
            onContinue = {},
        )
    }
}
