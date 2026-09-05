package `in`.artistant.app.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.ArtistantTheme

/**
 * A banner's tone — a statement about urgency, from which the fill follows.
 *
 * [Info] states a fact the page wants you to have. [Attention] means something
 * of yours is unresolved. [Failure] means something did not work. [Promotion] is
 * the page asking for something, and is the only one that takes the accent fill
 * — which is what makes a lime banner self-evidently an offer rather than a
 * warning.
 *
 * The three non-promotional tones each take a soft fill and a matching line from
 * the palette (`warmSoft`/`warmLine`, `dangerSoft`/`dangerLine`). On a light
 * page a tinted fill on its own is nearly invisible at banner size, and a line
 * on its own reads as a table cell; the pair is what makes the block a block.
 *
 * [Note] is the accent's QUIET register — the same lime at a fifth of its weight,
 * behind a line at a little over half. It is what the design uses for the aside
 * that sits under a form and explains the machinery ("Checked live against the
 * server", "Autofill works", "Agencies and wedding planners choose hosting"). It
 * is not [Promotion]: a solid lime block on a signup step out-shouts the field
 * the step is actually about, and it is not [Info] either, because `surface3` on
 * `surface` is nearly the same colour and the aside disappears.
 */
enum class BannerTone { Info, Note, Attention, Failure, Promotion }

/**
 * A full-width notice that lives inline in a scroll, not over it.
 *
 * The alternative — a snackbar or a dialog — is wrong for everything this is
 * used for: a profile that isn't finished, a refresh that failed, a subscription
 * that hasn't started. None of those are momentary, and all of them should still
 * be true when the user comes back to the screen tomorrow. So this stays in the
 * flow and stays until the underlying fact changes. A [Toast] is the other half
 * of that pair: momentary, and gone on its own.
 *
 * @param onClick makes the whole banner the target (press-scale + ripple).
 * @param actionLabel renders a trailing action pill INSTEAD of the chevron — for
 *   the retry case, where the banner itself isn't a destination. The design
 *   draws that pill as dark-on-light (screen 86), which is the one place a
 *   near-black fill appears in a content block: it has to out-rank a tinted
 *   banner without spending the screen's accent on a recovery button.
 * @param actionEnabled false while the action is WORKING — the pill stays on
 *   screen, wearing the label the caller gives it ("Sending report…"), and stops
 *   taking taps. It is a separate parameter because the obvious alternative,
 *   dropping `onAction` for the round trip, does not do this: the pill needs
 *   BOTH halves to render at all, so a null callback took the whole control off
 *   the banner mid-tap and the in-flight label was never drawn. A recovery
 *   button that vanishes when you press it is the one thing worse than one that
 *   files the report twice.
 */
@Composable
fun Banner(
    title: String,
    tone: BannerTone,
    modifier: Modifier = Modifier,
    detail: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    actionEnabled: Boolean = true,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val interaction = remember { MutableInteractionSource() }

    val promotion = tone == BannerTone.Promotion
    val fill = when (tone) {
        BannerTone.Info -> colors.surface3
        BannerTone.Note -> colors.accent.copy(alpha = NOTE_FILL)
        BannerTone.Attention -> colors.warmSoft
        BannerTone.Failure -> colors.dangerSoft
        BannerTone.Promotion -> colors.accent
    }
    val stroke = when (tone) {
        BannerTone.Info -> colors.hairline
        BannerTone.Note -> colors.accent.copy(alpha = NOTE_LINE)
        BannerTone.Attention -> colors.warmLine
        BannerTone.Failure -> colors.dangerLine
        BannerTone.Promotion -> Color.Transparent
    }
    val glyphTint = when (tone) {
        BannerTone.Info -> colors.ink3
        BannerTone.Note -> colors.accentDeep
        BannerTone.Attention -> colors.warm
        BannerTone.Failure -> colors.danger
        BannerTone.Promotion -> colors.onAccent
    }
    val glyph = icon ?: when (tone) {
        BannerTone.Info, BannerTone.Note, BannerTone.Promotion -> Icons.Outlined.Info
        BannerTone.Attention -> Icons.Filled.WarningAmber
        BannerTone.Failure -> Icons.Filled.ErrorOutline
    }
    val titleInk = if (promotion) colors.onAccent else colors.ink
    val detailInk = if (promotion) colors.onAccent.copy(alpha = DETAIL_ON_ACCENT) else colors.ink2
    val shape = RoundedCornerShape(dimens.radii.buttonLg)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.pressScale(interaction) else Modifier)
            .clip(shape)
            .background(fill)
            .border(dimens.size.hairline, stroke, shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = ripple(),
                        role = Role.Button,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .padding(horizontal = dimens.space.lg, vertical = dimens.space.md)
            .semantics(mergeDescendants = true) {
                contentDescription = listOfNotNull(title, detail).joinToString(". ")
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
    ) {
        Icon(
            imageVector = glyph,
            contentDescription = null,
            tint = glyphTint,
            modifier = Modifier.size(dimens.size.iconLg),
        )
        Column(Modifier.weight(1f)) {
            Text(
                title,
                // A [BannerTone.Note] is an aside, not a headline: the design sets it
                // as one running sentence at body weight, and bolding it would make the
                // explanation out-rank the field it is explaining. Every other tone
                // announces a state, which is what the bold step is for.
                style = if (tone == BannerTone.Note) {
                    AppTheme.type.subtitle
                } else {
                    AppTheme.type.subtitle.copy(fontWeight = FontWeight.Bold)
                },
                color = if (tone == BannerTone.Note) colors.ink2 else titleInk,
                maxLines = if (tone == BannerTone.Note) NOTE_MAX_LINES else 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!detail.isNullOrBlank()) {
                Text(
                    detail,
                    style = AppTheme.type.caption,
                    color = detailInk,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = dimens.space.xs / 2),
                )
            }
        }
        when {
            actionLabel != null && onAction != null -> Text(
                actionLabel,
                style = AppTheme.type.caption.copy(fontWeight = FontWeight.Bold),
                // Disabled takes the app's one disabled treatment — the hairline
                // fill and `ink3` label `PrimaryButton` uses — rather than a
                // fade, which on a tinted banner reads as a rendering fault.
                color = when {
                    !actionEnabled -> colors.ink3
                    promotion -> colors.accent
                    else -> colors.onDark
                },
                maxLines = 1,
                modifier = Modifier
                    // The label stays a caption; the tap node grows to the 44dp
                    // floor around it, then `wrapContentSize` re-centres the word.
                    .sizeIn(minWidth = dimens.size.rowMin, minHeight = dimens.size.rowMin)
                    .wrapContentSize()
                    .clip(RoundedCornerShape(dimens.radii.sm))
                    .background(
                        when {
                            !actionEnabled -> colors.hairline
                            promotion -> colors.onAccent
                            else -> colors.ink
                        },
                    )
                    .clickable(enabled = actionEnabled, role = Role.Button, onClick = onAction)
                    .padding(horizontal = dimens.space.md, vertical = dimens.space.sm),
            )
            onClick != null -> Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = if (promotion) colors.onAccent else colors.ink3,
                modifier = Modifier.size(dimens.size.iconMd),
            )
        }
    }
}

/**
 * The pre-redesign name for [Banner].
 *
 * Six feature screens call this; they migrate as their sections are rewritten.
 * It is a straight delegation rather than a second implementation — two banner
 * components that drift apart is exactly the failure this whole PR exists to
 * stop.
 */
@Deprecated("Renamed to Banner (REDESIGN_2026-09 §P1).", ReplaceWith("Banner(title, tone, modifier, detail, null, onClick, actionLabel, onAction)"))
@Composable
fun InlineBanner(
    title: String,
    tone: BannerTone,
    modifier: Modifier = Modifier,
    detail: String? = null,
    onClick: (() -> Unit)? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) = Banner(
    title = title,
    tone = tone,
    modifier = modifier,
    detail = detail,
    onClick = onClick,
    actionLabel = actionLabel,
    onAction = onAction,
)

/**
 * Supporting copy on the accent fill. Full-strength ink under a bold title reads
 * as two headlines; this is the same step down the ladder `ink2` gives on a light
 * surface, expressed as opacity because the ink tokens are tuned for `page` and
 * would be muddy on lime.
 */
private const val DETAIL_ON_ACCENT = 0.72f

/**
 * The [BannerTone.Note] tint and its rim, measured off the markup
 * (`rgba(214,248,75,.22)` behind `rgba(214,248,75,.6)`).
 *
 * Expressed as alphas over the surface rather than as two new opaque tokens
 * because that is what the design is: one accent at two weights. Baking them
 * into the palette would produce a pair that only works on white, and this
 * banner also appears on `page`.
 */
private const val NOTE_FILL = 0.22f
private const val NOTE_LINE = 0.60f

/** A note is a sentence, so it gets a sentence's worth of room before it clips. */
private const val NOTE_MAX_LINES = 4

@Preview(showBackground = true, backgroundColor = 0xFFFAFAF6)
@Composable
private fun BannerPreview() {
    ArtistantTheme {
        Column(
            Modifier.padding(AppTheme.dimens.component.gutter),
            verticalArrangement = Arrangement.spacedBy(AppTheme.dimens.space.md),
        ) {
            Banner(
                "Couldn't refresh your dashboard",
                BannerTone.Failure,
                detail = "Availability and requests may be stale.",
                actionLabel = "Retry",
                onAction = {},
            )
            Banner(
                "We won't draw these days as open",
                BannerTone.Attention,
                detail = "Showing an unknown day as free is how an artist gets double-booked.",
            )
            Banner("Phone numbers stay hidden until you book", BannerTone.Info)
            Banner("Go Pro", BannerTone.Promotion, detail = "Front of the list, every search.", onClick = {})
        }
    }
}
