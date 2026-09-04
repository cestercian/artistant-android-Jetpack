package `in`.artistant.app.feature.wizard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import `in`.artistant.app.common.util.formatInr
import `in`.artistant.app.designsystem.component.Banner
import `in`.artistant.app.designsystem.component.BannerTone
import `in`.artistant.app.designsystem.component.EyebrowLabel
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.component.RevealOnAppear
import `in`.artistant.app.designsystem.component.pressScale
import `in`.artistant.app.designsystem.rememberHaptics
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.ArtistGradient
import `in`.artistant.app.domain.score.ScoreBands
import `in`.artistant.app.domain.artist.ServiceTags
import java.io.File

/**
 * The last two steps: read your own profile as a client would, then publish.
 *
 * The preview is not decoration. It is the only point in the flow where the
 * artist sees the fields they typed one screen at a time assembled into the
 * thing a client will actually judge — which is why every row carries an edit
 * jump back to the step that owns it, rather than making them press Back nine
 * times.
 */

// ── Preview (screen 45) ──────────────────────────────────────────────────────

fun LazyListScope.previewStep(state: WizardUiState, vm: WizardViewModel) {
    item(key = "preview.cover") { PreviewCover(state, vm) }
    item(key = "preview.identity") { PreviewIdentity(state, vm) }
    item(key = "preview.rows") { PreviewRows(state, vm) }
    state.publishError?.let { message ->
        item(key = "preview.error") {
            Banner(
                title = "Couldn't publish.",
                tone = BannerTone.Failure,
                detail = message,
                modifier = Modifier.semantics { testTag = "wizard.preview.error" },
            )
        }
    }
}

@Composable
private fun PreviewCover(state: WizardUiState, vm: WizardViewModel) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val shape = RoundedCornerShape(dimens.radii.card)
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(PREVIEW_COVER_RATIO)
            .clip(shape)
            .background(colors.placeholder)
            .border(dimens.size.hairline, colors.hairline, shape)
            .semantics { testTag = "wizard.preview.hero" },
        contentAlignment = Alignment.Center,
    ) {
        if (state.pendingCoverPath == null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(dimens.space.sm),
            ) {
                Icon(
                    Icons.Outlined.Image,
                    contentDescription = null,
                    tint = colors.ink4,
                    modifier = Modifier.size(dimens.component.emptyGlyph),
                )
                Text("Cover", style = AppTheme.type.subtitle, color = colors.ink4)
            }
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(ArtistGradient.palette(state.coverGradientIndex))),
            )
            AsyncImage(
                model = remember(state.pendingCoverPath) { File(state.pendingCoverPath) },
                contentDescription = "Your cover photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        EditPill(
            onClick = { vm.jumpTo(WizardStep.Cover) },
            tag = "wizard.preview.editCover",
            onSurface = true,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(dimens.space.md),
        )
    }
}

@Composable
private fun PreviewIdentity(state: WizardUiState, vm: WizardViewModel) {
    val colors = AppTheme.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                state.stageName.ifBlank { "Your stage name" },
                style = AppTheme.type.displaySub,
                color = colors.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(
                    state.genre.trim().ifBlank { null },
                    state.category.ifBlank { null },
                    state.baseCity.ifBlank { null },
                ).joinToString(" · ").ifBlank { "Genre · Category · City" },
                style = AppTheme.type.subtitle,
                color = colors.ink4,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        EditPill(onClick = { vm.jumpTo(WizardStep.Identity) }, tag = "wizard.preview.editIdentity")
    }
}

/**
 * Every section as a row that states what it holds and jumps back to the step
 * that owns it.
 *
 * The value line is the point: "2 tiers · ₹15k–₹38k" is a fact the artist can
 * check against what they meant, where "Packages ›" is a door they have to open
 * to find out. Skipped steps say "Not added" rather than disappearing — the
 * artist should discover a thin profile here, where one tap fixes it, and not
 * from a week of silence.
 */
@Composable
private fun PreviewRows(state: WizardUiState, vm: WizardViewModel) {
    val dimens = AppTheme.dimens
    Column(verticalArrangement = Arrangement.spacedBy(dimens.space.sm)) {
        PreviewRow(
            label = "Bio",
            value = if (state.bio.isBlank()) "Not added" else "${state.bio.length} characters",
            filled = state.bio.isNotBlank(),
            onEdit = { vm.jumpTo(WizardStep.Bio) },
        )
        PreviewRow(
            label = "Packages",
            value = packagesSummary(state),
            filled = state.previewPackages.isNotEmpty(),
            onEdit = { vm.jumpTo(WizardStep.Pricing) },
        )
        PreviewRow(
            label = "Tech rider",
            value = if (state.techItems.isEmpty()) {
                "Not added"
            } else {
                "${state.techItems.size} line${if (state.techItems.size == 1) "" else "s"}"
            },
            filled = state.techItems.isNotEmpty(),
            onEdit = { vm.jumpTo(WizardStep.Tech) },
        )
        PreviewRow(
            label = "Availability",
            value = availabilityBadge(state.daysAvailable, state.timeSlots) ?: "No badge yet",
            filled = availabilityBadge(state.daysAvailable, state.timeSlots) != null,
            onEdit = { vm.jumpTo(WizardStep.Availability) },
        )
        PreviewRow(
            label = "Samples",
            value = if (state.pendingSamples.isEmpty()) {
                "Not added"
            } else {
                "${state.pendingSamples.size} clip${if (state.pendingSamples.size == 1) "" else "s"} " +
                    "— upload after you publish"
            },
            filled = state.pendingSamples.isNotEmpty(),
            onEdit = { vm.jumpTo(WizardStep.Samples) },
        )
        PreviewRow(
            label = "Services",
            value = if (state.serviceTags.isEmpty()) {
                "Not added"
            } else {
                ServiceTags.labels(state.serviceTags).joinToString(", ")
            },
            filled = state.serviceTags.isNotEmpty(),
            onEdit = { vm.jumpTo(WizardStep.Bio) },
        )
        PreviewRow(
            label = "Socials",
            value = socialSummary(state),
            filled = wizardStepIsFilled(state, WizardStep.Socials),
            onEdit = { vm.jumpTo(WizardStep.Socials) },
        )
    }
}

/** "2 tiers · ₹15,000–₹38,000", derived through the same filter publish uses. */
private fun packagesSummary(state: WizardUiState): String {
    val savable = state.previewPackages
    if (savable.isEmpty()) return "No publishable tier yet"
    val prices = savable.map { it.price }
    val range = if (prices.min() == prices.max()) {
        formatInr(prices.min())
    } else {
        "${formatInr(prices.min())}–${formatInr(prices.max())}"
    }
    return "${savable.size} tier${if (savable.size == 1) "" else "s"} · $range"
}

private fun socialSummary(state: WizardUiState): String {
    val present = buildList {
        if (state.instagramHandle.isNotBlank()) add("Instagram")
        if (state.spotifyArtistUrl.isNotBlank()) add("Spotify")
        if (state.youtubeChannelUrl.isNotBlank()) add("YouTube")
    }
    return if (present.isEmpty()) "Not added" else present.joinToString(", ")
}

@Composable
private fun PreviewRow(label: String, value: String, filled: Boolean, onEdit: () -> Unit) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.radii.buttonLg))
            .background(colors.surface3)
            .padding(horizontal = dimens.space.lg, vertical = dimens.space.md)
            .semantics(mergeDescendants = true) {
                contentDescription = "$label. $value"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = AppTheme.type.rowTitle, color = colors.ink)
            Text(
                value,
                style = AppTheme.type.caption,
                color = if (filled) colors.ink3 else colors.ink4,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        EditPill(onClick = onEdit, tag = "wizard.preview.edit.${label.lowercase()}")
    }
}

/**
 * "Edit" as a word in the accent green, not a pencil glyph.
 *
 * At this size a glyph needs a label for accessibility anyway, and the preview
 * carries eight of them — eight identical 16dp icons down a page is a puzzle,
 * eight words are a list. `accentInk` rather than `accent`, because lime as text
 * on off-white is decoration rather than a link.
 */
@Composable
private fun EditPill(
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
    onSurface: Boolean = false,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    Text(
        "Edit",
        style = AppTheme.type.caption.copy(fontWeight = FontWeight.Bold),
        color = colors.accentInk,
        modifier = modifier
            .pressScale(interaction)
            .clip(CircleShape)
            // On the cover it floats over a photo, so it takes an opaque disc;
            // in a row it sits on `surface3` and needs no chrome at all.
            .then(if (onSurface) Modifier.background(colors.surface) else Modifier)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = dimens.space.md, vertical = dimens.space.sm)
            .semantics {
                testTag = tag
                contentDescription = "Edit this section"
            },
    )
}

/** The preview cover is wider than the 3:4 crop — it is a card, not a hero. */
private const val PREVIEW_COVER_RATIO = 4f / 3f

// ── You're live (screen 46) ──────────────────────────────────────────────────

/**
 * The celebration, and the one screen in the flow that is not a form.
 *
 * It renders its own layout rather than going through [WizardStepScaffold]:
 * there is nothing to fill in. It ends on the ADDRESS, because that is the first
 * time the handle stops being a field the artist typed and becomes somewhere a
 * client can go — and it sets the score expectation in the same breath, so
 * "New" is a starting tier rather than a verdict discovered later.
 */
@Composable
fun WizardDoneScreen(state: WizardUiState, onOpenDashboard: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val clipboard = LocalClipboardManager.current
    val haptics = rememberHaptics()
    val address = wizardPublicAddress(state.handle)

    // RevealOnAppear rather than a hand-rolled spring: it is the repo's one
    // entrance animation and it already collapses to nothing under
    // reduce-motion, which a success screen is exactly the wrong place to skip.
    RevealOnAppear {
        Column(
            modifier
                .fillMaxSize()
                .padding(horizontal = dimens.component.gutter)
                .semantics { testTag = "wizard.step.done" },
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier
                    .size(dimens.size.ringMd)
                    .clip(CircleShape)
                    .background(colors.accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = colors.onAccent,
                    modifier = Modifier.size(dimens.size.iconXl),
                )
            }
            Spacer(Modifier.height(dimens.space.xl))
            Text("You're live.", style = AppTheme.type.displayHero, color = colors.ink)
            Spacer(Modifier.height(dimens.space.md))
            Text(
                buildString {
                    append("Hosts searching ")
                    append(state.genre.trim().ifBlank { state.category.ifBlank { "live acts" } })
                    if (state.baseCity.isNotBlank()) append(" in ${state.baseCity}")
                    append(" can find and book you from now.")
                },
                style = AppTheme.type.body,
                color = colors.ink3,
            )
            if (address != null) {
                Spacer(Modifier.height(dimens.space.xl))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(dimens.radii.card))
                        .background(colors.surface3)
                        .clickable(role = Role.Button) {
                            clipboard.setText(AnnotatedString(address))
                            haptics.success()
                        }
                        .padding(dimens.space.lg)
                        .semantics { testTag = "wizard.done.address" },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        EyebrowLabel("Your public profile")
                        Spacer(Modifier.height(dimens.space.sm))
                        Text(
                            address,
                            style = AppTheme.type.rowTitle.copy(fontWeight = FontWeight.Bold),
                            color = colors.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(
                        Icons.Filled.ContentCopy,
                        contentDescription = "Copy your profile address",
                        tint = colors.ink4,
                        modifier = Modifier.size(dimens.size.iconLg),
                    )
                }
            }
            Spacer(Modifier.height(dimens.space.md))
            NewTierCard()
            Spacer(Modifier.height(dimens.space.xxl))
            PrimaryButton(
                text = "Open my dashboard",
                onClick = onOpenDashboard,
                fullWidth = true,
                modifier = Modifier.semantics { testTag = "wizard.done.openDashboard" },
            )
        }
    }
}

/**
 * What the score says on day one, and what moves it.
 *
 * The thresholds come from [ScoreBands] rather than being written out here: the
 * number of completed gigs that leaves the New tier is a product rule the score
 * screen already states, and two copies of it is how the wizard starts promising
 * something the score does not do.
 */
@Composable
private fun NewTierCard() {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val shape = RoundedCornerShape(dimens.radii.card)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.brandSoft)
            .border(dimens.size.hairline, colors.accent, shape)
            .padding(dimens.space.lg)
            .semantics { testTag = "wizard.done.scoreNote" },
        verticalArrangement = Arrangement.spacedBy(dimens.space.md),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
        ) {
            Text(
                "New",
                style = AppTheme.type.caption.copy(fontWeight = FontWeight.Bold),
                color = colors.onAccent,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(colors.accent)
                    .padding(horizontal = dimens.space.md, vertical = dimens.space.sm),
            )
            Text(
                "Your score starts at “New”",
                style = AppTheme.type.rowTitle.copy(fontWeight = FontWeight.Bold),
                color = colors.ink,
            )
        }
        Text(
            "${ScoreBands.MIN_GIGS_FOR_RANK} completed gigs move you off the New tier. " +
                "Reply speed counts from your first request.",
            style = AppTheme.type.subtitle,
            color = colors.ink3,
        )
    }
}
