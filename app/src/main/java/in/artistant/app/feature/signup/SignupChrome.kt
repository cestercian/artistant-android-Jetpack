package `in`.artistant.app.feature.signup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import `in`.artistant.app.designsystem.component.AppTextField
import `in`.artistant.app.designsystem.component.Banner
import `in`.artistant.app.designsystem.component.BannerTone
import `in`.artistant.app.designsystem.component.IconCircle
import `in`.artistant.app.designsystem.component.hairlineTop
import `in`.artistant.app.designsystem.theme.AppTheme

/**
 * Shared chrome for the "Getting started" section (design screens 11 / 12 / 27 /
 * 28 / 29 / 31 / 62 / 90 / 114 / 118 / 119).
 *
 * Every one of those screens is built the same way: a header band with a back
 * circle in it, a body inset by the page gutter, and — on the ones that end in a
 * decision — a CTA bar pinned to the bottom edge behind a hairline.
 * [SignupScaffold] is that shape, so the eleven screens differ only in what they
 * put inside it.
 *
 * The phone bezel, notch and fake status bar in the extracted markup are design
 * chrome and are deliberately NOT drawn (REDESIGN_2026-09 §5.3); the real status
 * bar and navigation bar arrive as window insets instead.
 */
@Composable
fun SignupScaffold(
    modifier: Modifier = Modifier,
    header: @Composable (() -> Unit)? = null,
    /**
     * The pinned bottom bar. Null on a screen whose last control scrolls with the
     * body, non-null everywhere the design draws the hairline-topped white bar.
     */
    footer: @Composable (ColumnScope.() -> Unit)? = null,
    /** Body scrolls. Off for the few screens the design fits to the viewport. */
    scrollable: Boolean = true,
    background: Color = AppTheme.colors.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    val dimens = AppTheme.dimens
    val gutter = dimens.component.gutter
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(background)
            .statusBarsPadding(),
    ) {
        if (header != null) {
            Box(Modifier.fillMaxWidth().padding(horizontal = gutter, vertical = dimens.space.sm)) {
                header()
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(horizontal = gutter),
            content = content,
        )
        if (footer != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppTheme.colors.surface)
                    .hairlineTop()
                    // The bar owns the bottom inset AND the keyboard inset: the
                    // window is edge-to-edge and does not resize for the IME, so
                    // without `imePadding` the CTA on the handle and code steps
                    // would sit under the keyboard the moment a field takes focus.
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = gutter)
                    .padding(top = dimens.space.lg, bottom = dimens.space.xl),
                verticalArrangement = Arrangement.spacedBy(dimens.space.md),
                content = footer,
            )
        }
    }
}

/**
 * The header band: a back circle, an optional title block, and a trailing slot.
 *
 * The trailing slot reserves the back circle's width even when empty, so a title
 * is centred against the BAR rather than against the space left between the
 * controls. An asymmetric reservation reads as almost-centred, which is worse
 * than either extreme.
 */
@Composable
fun SignupHeader(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    title: String? = null,
    subtitle: String? = null,
    /** Left-align the title block (screens 31 / 62 / 114) instead of centring it. */
    titleAtStart: Boolean = false,
    /** Anything that replaces the title entirely — the 29 / 90 step strip. */
    middle: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
    ) {
        if (onBack != null) {
            IconCircle(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                onClick = onBack,
                size = dimens.component.iconCircleSm,
            )
        } else {
            Spacer(Modifier.width(dimens.component.iconCircleSm))
        }
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = if (titleAtStart) Alignment.CenterStart else Alignment.Center,
        ) {
            when {
                middle != null -> middle()
                title != null -> Column(
                    horizontalAlignment = if (titleAtStart) Alignment.Start else Alignment.CenterHorizontally,
                ) {
                    Text(
                        title,
                        style = AppTheme.type.sectionTitle.copy(fontSize = AppTheme.type.body.fontSize),
                        color = colors.ink,
                        maxLines = 1,
                        textAlign = if (titleAtStart) TextAlign.Start else TextAlign.Center,
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            subtitle,
                            style = AppTheme.type.caption,
                            color = colors.ink4,
                            maxLines = 1,
                            textAlign = if (titleAtStart) TextAlign.Start else TextAlign.Center,
                        )
                    }
                }
            }
        }
        if (trailing != null) trailing() else Spacer(Modifier.width(dimens.component.iconCircleSm))
    }
}

/**
 * The step strip the design draws on exactly one screen — 29 / 90, "04 / 06".
 *
 * Three states, not two: the steps behind you are accent, the step you are ON is
 * `lineStrong`, and the ones ahead are `hairline`. The middle state is what says
 * where you are without spending the screen's one accent on it, and it is
 * measured off the markup rather than invented.
 */
@Composable
fun SignupProgressStrip(bar: ProgressBar?, modifier: Modifier = Modifier) {
    if (bar == null) return
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Step ${bar.index + 1} of ${bar.total}" },
        horizontalArrangement = Arrangement.spacedBy(dimens.space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(bar.total) { i ->
            Box(
                Modifier
                    .weight(1f)
                    // A hairline with a job: the strip is 4 tall, which is the
                    // xs step. Taller and it reads as a loading bar.
                    .height(dimens.space.xs)
                    .clip(RoundedCornerShape(dimens.radii.sm))
                    .background(
                        when {
                            i < bar.index -> colors.accent
                            i == bar.index -> colors.lineStrong
                            else -> colors.hairline
                        },
                    ),
            )
        }
    }
}

/**
 * A label above a control that is not an [in.artistant.app.designsystem.component.AppTextField]
 * — the city picker, the phone row.
 *
 * Exists so a label does not change weight depending on what it happens to be
 * labelling: `AppTextField` draws exactly this style for the fields it owns.
 */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = AppTheme.type.caption.copy(fontWeight = FontWeight.SemiBold),
        color = AppTheme.colors.ink4,
        modifier = modifier.padding(bottom = AppTheme.dimens.space.sm),
    )
}

/**
 * An eyebrow inside the body copy — "TRY ONE OF THESE", "NOT ARRIVING?".
 *
 * The design sets these in the SANS at 12.5/700 with wide tracking, not in the
 * mono eyebrow the rest of the app uses, so this is not
 * [in.artistant.app.designsystem.component.EyebrowLabel]. Uppercasing happens at
 * the call site, as it does everywhere else — Compose has no text-transform.
 */
@Composable
fun SignupEyebrow(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = AppTheme.type.caption.copy(fontWeight = FontWeight.Bold),
        color = AppTheme.colors.ink3,
        modifier = modifier,
    )
}

/**
 * The consent checkbox (screens 27 / 118): a rounded square that fills accent
 * with a check when on and is a `lineStrong` outline when off.
 *
 * Not Material's `Checkbox`: that control is 48dp of built-in touch target with a
 * 20dp box drawn inside it and its own ripple geometry, and the design puts this
 * square at the top-left of a wrapping paragraph where a 48dp node pushes the
 * copy out of alignment. The ROW around it carries the tap target instead.
 */
@Composable
fun ConsentCheckbox(checked: Boolean, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val shape = RoundedCornerShape(dimens.radii.sm)
    Box(
        modifier = modifier
            .size(dimens.component.checkbox)
            .clip(shape)
            .then(
                if (checked) {
                    Modifier.background(colors.accent)
                } else {
                    Modifier.border(dimens.component.checkboxStroke, colors.lineStrong, shape)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = colors.onAccent,
                modifier = Modifier.size(dimens.size.iconMd),
            )
        }
    }
}

/**
 * The app mark — a `darkest` rounded square carrying a lime "A" (screen 118).
 *
 * The letter is the MONO face at Black, which is how the adaptive launcher icon
 * draws it too. Two marks meant to be the same mark have to come from the same
 * rules, or the icon on the home screen and the icon on the first screen of the
 * app are visibly different logos.
 */
@Composable
fun AppMark(modifier: Modifier = Modifier, size: Dp = AppTheme.dimens.size.avatarLg) {
    val colors = AppTheme.colors
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(AppTheme.dimens.radii.card))
            .background(colors.darkest)
            .semantics { contentDescription = "Artistant" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "A",
            style = AppTheme.type.monoNumber.copy(
                fontSize = AppTheme.type.displayHero.fontSize,
                fontWeight = FontWeight.Black,
            ),
            color = colors.accent,
        )
    }
}

/**
 * A tappable word inside a sentence — "Terms", "Change number", "Forgot password?".
 *
 * `accentInk`, not `accent`: lime on off-white measures about 1.3:1, which is
 * decoration rather than text. The same hue dropped to a leaf green reads at
 * 5.4:1 and is still recognisably the brand signal.
 */
@Composable
fun InlineLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: TextStyle = AppTheme.type.subtitle,
) {
    Text(
        text,
        style = style.copy(fontWeight = FontWeight.SemiBold),
        color = if (enabled) AppTheme.colors.accentInk else AppTheme.colors.ink4,
        modifier = modifier
            .clip(RoundedCornerShape(AppTheme.dimens.radii.sm))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = AppTheme.dimens.space.sm, horizontal = AppTheme.dimens.space.xs),
    )
}

/**
 * Design screen 71's strip — "a blip is not a new account".
 *
 * Shown on both the role and handle steps, because those are the two screens a failed
 * hydration can land someone on and the failure has to be visible on whichever one they are
 * looking at. The design draws it on the role picker; the gate enters the flow at the handle
 * step, and the role picker is one back-press behind it.
 *
 * The Retry pill is [in.artistant.app.designsystem.component.Banner]'s dark action rather than
 * a second lime: the screen's accent already belongs to the selected role card / the Continue
 * button, and a recovery control is not the thing to spend it on.
 */
@Composable
fun HydrationErrorBanner(
    detail: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Banner(
        title = "Couldn't load your profile",
        tone = BannerTone.Failure,
        detail = detail,
        actionLabel = "Retry",
        onAction = onRetry,
        modifier = modifier.semantics { testTag = "signup.hydrate.errorBanner" },
    )
}

/**
 * The circular back affordance the pre-redesign steps used.
 *
 * Kept as a delegation to [IconCircle] so nothing outside this package has to
 * know the chrome changed; the dark hairline disc it used to draw is gone.
 */
@Composable
fun SignupBackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconCircle(
        icon = Icons.AutoMirrored.Filled.ArrowBack,
        contentDescription = "Back",
        onClick = onClick,
        size = AppTheme.dimens.component.iconCircleSm,
        modifier = modifier,
    )
}

/**
 * The dark design's borderless underline input, now a delegation to [AppTextField].
 *
 * No signup screen calls this any more — the light design's field is a filled well with a
 * label above it, which is what [AppTextField] draws. It survives because the artist setup
 * wizard (`feature/wizard`, section WZ) has six call sites, and that package belongs to
 * another agent in this redesign wave: deleting the function would land a red tree in
 * somebody else's worktree. Redirecting it means those six fields pick up the light styling
 * for free in the meantime, which is what P1's "every existing screen renders on the light
 * palette" gate asks for.
 *
 * [underline] is accepted and ignored. It was a colour for a 1dp rule that no longer exists;
 * `AppTextField` states the same thing with its own border, driven by focus and by [error].
 * Silently ignoring it is right precisely because it was decoration — the wizard's handle row
 * passes it alongside a `trailing` status chip that carries the actual meaning.
 */
@Deprecated("The light design's input is AppTextField (REDESIGN_2026-09 §P1).")
@Composable
fun SignupInputRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    prefix: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
    @Suppress("UNUSED_PARAMETER") underline: Color? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    AppTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label,
        hint = placeholder.ifBlank { null },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (password) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        leading = prefix?.let {
            { Text(it, style = AppTheme.type.body, color = AppTheme.colors.ink4) }
        },
        trailing = trailing,
    )
}
