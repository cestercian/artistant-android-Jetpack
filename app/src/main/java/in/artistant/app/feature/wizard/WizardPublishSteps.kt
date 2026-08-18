package `in`.artistant.app.feature.wizard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import `in`.artistant.app.common.util.formatInr
import `in`.artistant.app.designsystem.component.HRule
import `in`.artistant.app.designsystem.component.Pill
import `in`.artistant.app.designsystem.component.PillTone
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.component.RevealOnAppear
import `in`.artistant.app.designsystem.component.pressScale
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.ArtistGradient
import `in`.artistant.app.feature.epk.EpkSectionHeader
import `in`.artistant.app.feature.signup.EditorialHeadline
import java.io.File

/**
 * The last two steps: read your own profile as a client would, then publish.
 *
 * The preview is not decoration. It is the only point in the flow where the
 * artist sees the fields they typed one screen at a time assembled into the
 * thing a client will actually judge — which is why every section carries an
 * edit jump back to the step that owns it, rather than making them press Back
 * nine times.
 */

// ── Preview ──────────────────────────────────────────────────────────────────

fun LazyListScope.previewStep(state: WizardUiState, vm: WizardViewModel) {
    item(key = "preview.hero") { PreviewHero(state, vm) }
    item(key = "preview.stats") { PreviewStats(state) }
    item(key = "preview.packages") { PreviewPackages(state, vm) }
    if (state.techItems.isNotEmpty()) {
        item(key = "preview.tech") { PreviewTech(state, vm) }
    }
    item(key = "preview.extras") { PreviewExtras(state, vm) }
}

@Composable
private fun PreviewHero(state: WizardUiState, vm: WizardViewModel) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val space = dimens.space
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(dimens.aspect.editorial)
            .clip(RoundedCornerShape(dimens.radii.lg))
            .semantics { testTag = "wizard.preview.hero" },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(ArtistGradient.palette(state.coverGradientIndex))),
        )
        state.pendingCoverPath?.let { path ->
            AsyncImage(
                model = remember(path) { File(path) },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        1f - dimens.fraction.heroFade to Color.Transparent,
                        1f to colors.bg,
                    ),
                ),
        )
        EditChip(
            onClick = { vm.jumpTo(WizardStep.Cover) },
            tag = "wizard.preview.editCover",
            onMedia = true,
            modifier = Modifier.align(Alignment.TopEnd).padding(space.md),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(space.lg),
            verticalArrangement = Arrangement.spacedBy(space.xs),
        ) {
            if (state.category.isNotBlank()) {
                Text(
                    state.category.uppercase(),
                    style = AppTheme.type.monoMicro,
                    color = colors.inkOnMedia,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(colors.chipOnMedia)
                        .padding(horizontal = space.sm, vertical = space.xs),
                )
            }
            Text(
                state.stageName.ifBlank { "Your stage name" },
                style = AppTheme.type.profileHeroName,
                color = colors.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOf(state.genre.ifBlank { "Genre" }, state.baseCity.ifBlank { "City" })
                    .joinToString(" · "),
                style = AppTheme.type.callout,
                color = colors.ink2,
            )
        }
    }
}

@Composable
private fun PreviewStats(state: WizardUiState) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.radii.md))
            .background(colors.bgCard)
            .padding(vertical = dimens.space.lg, horizontal = dimens.space.md)
            .semantics { testTag = "wizard.preview.stats" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WizardStat("@${state.handle}", "Handle", Modifier.weight(1f))
        WizardStatDivider()
        WizardStat(
            sortedWeekdays(state.daysAvailable).joinToString(" "),
            "Days",
            Modifier.weight(1f).padding(start = dimens.space.md),
        )
        WizardStatDivider()
        WizardStat(
            state.previewPackages.size.toString(),
            "Tiers",
            Modifier.weight(1f).padding(start = dimens.space.md),
        )
    }
}

@Composable
private fun PreviewPackages(state: WizardUiState, vm: WizardViewModel) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Column {
        EpkSectionHeader(title = "Packages")
        Spacer(Modifier.height(space.sm))
        HRule()
        state.previewPackages.forEach { pkg ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = space.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(pkg.name, style = AppTheme.type.callout, color = colors.ink)
                        if (pkg.popular) {
                            Spacer(Modifier.size(space.sm))
                            Pill(text = "Popular", tone = PillTone.Brand)
                        }
                    }
                    if (pkg.duration.isNotBlank()) {
                        Text(pkg.duration, style = AppTheme.type.caption, color = colors.ink3)
                    }
                }
                Text(formatInr(pkg.price), style = AppTheme.type.monoPrice, color = colors.ink)
            }
            HRule()
        }
        Spacer(Modifier.height(space.sm))
        EditChip(onClick = { vm.jumpTo(WizardStep.Pricing) }, tag = "wizard.preview.editPricing")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PreviewTech(state: WizardUiState, vm: WizardViewModel) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Column {
        EpkSectionHeader(title = "Tech rider")
        Spacer(Modifier.height(space.sm))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(space.sm),
            verticalArrangement = Arrangement.spacedBy(space.sm),
        ) {
            state.techItems.forEach { item ->
                Text(
                    item,
                    style = AppTheme.type.caption,
                    color = colors.ink2,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(colors.bgCard)
                        .padding(horizontal = space.md, vertical = space.sm),
                )
            }
        }
        Spacer(Modifier.height(space.sm))
        EditChip(onClick = { vm.jumpTo(WizardStep.Tech) }, tag = "wizard.preview.editTech")
    }
}

/**
 * The optional steps, reported honestly.
 *
 * Skipping is allowed, so the preview names what was skipped rather than hiding
 * it — the artist should find out here, where one tap fixes it, and not from a
 * thin-looking profile a week later.
 */
@Composable
private fun PreviewExtras(state: WizardUiState, vm: WizardViewModel) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Column(verticalArrangement = Arrangement.spacedBy(space.sm)) {
        EpkSectionHeader(title = "Also on your profile")
        HRule()
        ExtraRow(
            label = "Bio",
            value = state.bio.ifBlank { "Not added" },
            filled = state.bio.isNotBlank(),
            onEdit = { vm.jumpTo(WizardStep.Bio) },
        )
        HRule()
        ExtraRow(
            label = "Social links",
            value = socialSummary(state),
            filled = wizardStepIsFilled(state, WizardStep.Socials),
            onEdit = { vm.jumpTo(WizardStep.Socials) },
        )
        HRule()
        ExtraRow(
            label = "Audio samples",
            value = if (state.pendingSamples.isEmpty()) {
                "Not added"
            } else {
                "${state.pendingSamples.size} queued — uploads after you go live"
            },
            filled = state.pendingSamples.isNotEmpty(),
            onEdit = { vm.jumpTo(WizardStep.Samples) },
        )
        state.publishError?.let { message ->
            Spacer(Modifier.height(space.sm))
            Text(
                message,
                style = AppTheme.type.footnote,
                color = colors.hot,
                modifier = Modifier.semantics { testTag = "wizard.preview.error" },
            )
        }
    }
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
private fun ExtraRow(label: String, value: String, filled: Boolean, onEdit: () -> Unit) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Row(
        Modifier.fillMaxWidth().padding(vertical = space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = AppTheme.type.footnote, color = colors.ink)
            Text(
                value,
                style = AppTheme.type.caption,
                color = if (filled) colors.ink2 else colors.ink3,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        EditChip(onClick = onEdit, tag = "wizard.preview.edit.${label.lowercase()}")
    }
}

/**
 * "Edit" as a word in the accent, not a pencil glyph.
 *
 * At this size a glyph needs a label for accessibility anyway, and the preview
 * carries six of them — six identical 16dp icons down a page is a puzzle, six
 * words is a list.
 */
@Composable
private fun EditChip(
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
    onMedia: Boolean = false,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val interaction = remember { MutableInteractionSource() }
    Text(
        "EDIT",
        style = AppTheme.type.monoMicro,
        color = if (onMedia) colors.inkOnMedia else colors.brand,
        modifier = modifier
            .clip(CircleShape)
            .then(if (onMedia) Modifier.background(colors.chipOnMedia) else Modifier)
            .pressScale(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = space.sm, vertical = space.xs)
            .semantics {
                testTag = tag
                contentDescription = "Edit this section"
            },
    )
}

// ── Done ─────────────────────────────────────────────────────────────────────

/**
 * The celebration, and the one screen in the flow that is not a form.
 *
 * It renders its own layout rather than going through [WizardStepScaffold]:
 * there is nothing to scroll, and centring the mark is the whole point. The
 * handle is quoted back because it is the first time the artist sees their
 * profile as an address rather than as a field they filled in.
 */
@Composable
fun WizardDoneScreen(state: WizardUiState, onOpenDashboard: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val space = dimens.space

    // RevealOnAppear rather than a hand-rolled spring: it is the repo's one
    // entrance animation and it already collapses to nothing under
    // reduce-motion, which a success screen is exactly the wrong place to skip.
    RevealOnAppear {
        Column(
            modifier
                .fillMaxSize()
                .padding(horizontal = space.xl)
                .semantics { testTag = "wizard.step.done" },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Two concentric discs: a soft halo the accent can bloom into,
                // then the solid mark. One flat disc reads as a system alert.
                Box(
                    Modifier
                        .size(dimens.size.ringXl)
                        .clip(CircleShape)
                        .background(colors.brand.copy(alpha = 0.10f)),
                )
                Box(
                    Modifier
                        .size(dimens.size.ringLg)
                        .clip(CircleShape)
                        .background(colors.brand),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = colors.brandInk,
                        modifier = Modifier.size(dimens.size.iconXl),
                    )
                }
            }
            Spacer(Modifier.height(space.xxl))
            EditorialHeadline(
                lead = "You're ",
                accent = "live",
                tail = ".",
                style = AppTheme.type.displayTitle,
            )
            Spacer(Modifier.height(space.md))
            Text(
                "Clients can find and book you at artistant.in/${state.handle}.",
                style = AppTheme.type.footnote,
                color = colors.ink2,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(space.xxl))
            PrimaryButton(
                text = "Open dashboard",
                onClick = onOpenDashboard,
                fullWidth = true,
                modifier = Modifier.semantics { testTag = "wizard.done.openDashboard" },
            )
        }
    }
}
