package `in`.artistant.app.feature.epk

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import `in`.artistant.app.data.repository.ArtistMediaItem
import `in`.artistant.app.designsystem.component.DashedSlot
import `in`.artistant.app.designsystem.component.MediaSlot
import `in`.artistant.app.designsystem.component.hairlineBottom
import `in`.artistant.app.designsystem.component.pressScale
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.ArtistantTheme

/**
 * The press kit's own blocks — design screens 23 (filled), 87 (empty) and 76
 * (uploading).
 *
 * They live in the feature package rather than in `designsystem/component`
 * because nothing else in the app has a completion meter with a payoff line or a
 * three-up gallery strip. The generic halves they are built from — [MediaSlot],
 * [DashedSlot], `Banner`, `ListRow` — are already shared; what is here is the
 * arrangement, and an arrangement with one caller is not a component.
 */

// ── Completion ───────────────────────────────────────────────────────────────

/**
 * The meter and the sentence under it (23).
 *
 * Two lines that say different things on purpose. The bar says how far along the
 * kit is, which is the part that feels like progress; the sentence says what to
 * do next, which is the only part an artist can act on. A percentage on its own
 * is a grade, and nobody has ever finished a profile because they were graded.
 */
@Composable
fun EpkCompletionMeter(completion: EpkCompletion, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Column(
        modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "${completion.percent}% complete. ${completion.summary}"
            },
        verticalArrangement = Arrangement.spacedBy(dimens.space.sm),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(dimens.pressKit.meter)
                .clip(RoundedCornerShape(dimens.radii.xs))
                .background(colors.hairline),
        ) {
            // `fillMaxWidth(fraction)` rather than a measured width: the bar has
            // no fixed size (it is the page's width less the gutter), so the fill
            // has to be expressed as a share of whatever the parent turns out to
            // be.
            if (completion.fraction > 0f) {
                Box(
                    Modifier
                        .fillMaxWidth(completion.fraction)
                        .fillMaxSize()
                        .background(colors.accent),
                )
            }
        }
        Text(completion.summary, style = AppTheme.type.subtitle, color = colors.ink4)
    }
}

// ── Upload banner ────────────────────────────────────────────────────────────

/**
 * What the upload queue is doing, at the top of the page (76 / 66).
 *
 * Not a `Banner`: the shared component is a title, a detail and one trailing
 * control, and this needs a progress bar under all three. The tone is the shared
 * `Note` tint either way, so the two read as the same family.
 *
 * **The bar fills in ink, not in the accent** — that is the design's own choice
 * (76) and it is the right one: the accent circle beside the title is already
 * spending the screen's one accent, and a lime bar on a lime card is invisible.
 */
@Composable
fun EpkQueueBanner(
    banner: EpkUploadBanner,
    onDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val failed = banner is EpkUploadBanner.Stalled
    val fill = if (failed) colors.dangerSoft else colors.accent.copy(alpha = NOTE_FILL)
    val stroke = if (failed) colors.dangerLine else colors.accent.copy(alpha = NOTE_LINE)
    val discFill = if (failed) colors.dangerLine else colors.accent
    val discInk = if (failed) colors.danger else colors.ink
    val shape = RoundedCornerShape(dimens.radii.lg)
    val title = when (banner) {
        is EpkUploadBanner.Working -> banner.title
        is EpkUploadBanner.Stalled -> banner.title
    }
    val detail = when (banner) {
        is EpkUploadBanner.Working -> banner.detail
        is EpkUploadBanner.Stalled -> banner.detail
    }

    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(fill)
            .border(dimens.size.hairline, stroke, shape)
            .padding(dimens.space.md),
        verticalArrangement = Arrangement.spacedBy(dimens.space.md),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(dimens.size.ringXs)
                    .clip(CircleShape)
                    .background(discFill),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.ArrowUpward,
                    contentDescription = null,
                    tint = discInk,
                    modifier = Modifier.size(dimens.size.iconMd),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = AppTheme.type.rowTitle.copy(fontWeight = FontWeight.Bold),
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!detail.isNullOrBlank()) {
                    Text(
                        detail,
                        style = AppTheme.type.caption,
                        color = colors.ink2,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                "Details",
                style = AppTheme.type.caption.copy(fontWeight = FontWeight.Bold),
                color = if (failed) colors.danger else colors.accentDeep,
                modifier = Modifier
                    .sizeIn(minWidth = dimens.size.rowMin, minHeight = dimens.size.rowMin)
                    .wrapContentSize()
                    .clip(RoundedCornerShape(dimens.radii.sm))
                    .clickable(role = Role.Button, onClick = onDetails)
                    .padding(horizontal = dimens.space.sm, vertical = dimens.space.sm),
            )
        }
        if (banner is EpkUploadBanner.Working) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(dimens.pressKit.uploadMeter)
                    .clip(RoundedCornerShape(dimens.radii.xs))
                    .background(colors.hairline),
            ) {
                if (banner.fraction > 0f) {
                    Box(
                        Modifier
                            .fillMaxWidth(banner.fraction)
                            .fillMaxSize()
                            .background(colors.ink),
                    )
                }
            }
        }
    }
}

// ── Cover and gallery ────────────────────────────────────────────────────────

/**
 * The cover band (23 filled, 87 empty).
 *
 * The empty state is a [DashedSlot], not a grey rectangle with a caption. A solid
 * placeholder means "media is loading"; an outline means "put something here",
 * and on a page whose entire job is to say what is missing, the difference is the
 * message.
 */
@Composable
fun EpkCoverBlock(
    coverUrl: String?,
    onAdd: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = AppTheme.dimens
    if (coverUrl == null) {
        DashedSlot(
            modifier = modifier.fillMaxWidth(),
            height = dimens.pressKit.coverEmpty,
            label = "Add a cover photo or video",
            onClick = onAdd,
        )
    } else {
        MediaSlot(
            modifier = modifier
                .fillMaxWidth()
                .height(dimens.pressKit.cover),
            radius = dimens.radii.card,
            onClick = onOpen,
        ) {
            AsyncImage(
                model = coverUrl,
                contentDescription = "Your cover photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * The three-up gallery strip (23 / 76).
 *
 * Three cells, always, because a strip that grows a fourth cell at four photos
 * re-flows the whole page as an upload lands. Photos beyond the third are reached
 * through the strip's own tap, which opens the gallery editor — the count in the
 * header says how many are behind it, so nothing is hidden without being
 * declared.
 *
 * [uploading] draws the cell the design draws while a photo is in flight: the
 * `placeholder` fill with an up-arrow. It is not a spinner — the queue has no
 * per-file progress to report (see `uploadBannerFor`), and a spinner would be an
 * animation standing in for a number.
 */
@Composable
fun EpkGalleryStrip(
    photos: List<ArtistMediaItem>,
    uploading: Boolean,
    canAdd: Boolean,
    onOpen: () -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    // The cover is the first photo, and it already has its own band above. The
    // strip is everything else — showing the cover twice would make a two-photo
    // kit look like a three-photo one.
    val rest = photos.drop(1)
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(dimens.space.sm),
    ) {
        repeat(GALLERY_CELLS) { index ->
            val photo = rest.getOrNull(index)
            val cell = Modifier
                .weight(1f)
                .height(dimens.pressKit.galleryCell)
            when {
                photo != null -> MediaSlot(cell, radius = dimens.radii.md, onClick = onOpen) {
                    AsyncImage(
                        model = photo.publicUrl,
                        contentDescription = "Gallery photo ${index + 1}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                // The in-flight cell sits immediately after the last stored photo,
                // which is where the new one will land.
                uploading && index == rest.size -> MediaSlot(cell, radius = dimens.radii.md) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.ArrowUpward,
                            contentDescription = "Uploading a photo",
                            tint = colors.ink4,
                            modifier = Modifier.size(dimens.size.iconLg),
                        )
                    }
                }
                canAdd && index == rest.size + (if (uploading) 1 else 0) -> DashedSlot(
                    modifier = cell,
                    radius = dimens.radii.md,
                    contentDescription = "Add a photo",
                    onClick = onAdd,
                )
                else -> Box(cell)
            }
        }
    }
}

// ── Section rows ─────────────────────────────────────────────────────────────

/**
 * One row of the Sections list (23).
 *
 * The leading square is the state, and it is a square rather than a tick in a
 * circle because a circle at this size reads as an avatar. Filled sections get
 * the accent tint and a check; gaps get the quiet `surface2` and the section's own
 * glyph, so the eye can find the gaps by colour before reading a word.
 *
 * [EpkSectionRow.detail] carries either the fact or the payoff, and the payoff is
 * tinted `accentInk` — the design's own choice on screen 23. That is the one
 * place on the page where a *missing* thing is drawn in the positive colour, and
 * it is the whole trick: the line reads as something to gain rather than
 * something you failed to do.
 */
@Composable
fun EpkSectionListRow(
    row: EpkSectionRow,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showHairline: Boolean = true,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interaction,
                indication = ripple(),
                role = Role.Button,
                onClick = onClick,
            )
            .then(if (showHairline) Modifier.hairlineBottom() else Modifier)
            .padding(vertical = dimens.space.md)
            .semantics(mergeDescendants = true) {
                contentDescription = "${row.title}. ${row.detail}"
            },
        horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(dimens.size.ringXs)
                .clip(RoundedCornerShape(dimens.radii.md))
                .background(if (row.filled) colors.accent.copy(alpha = TICK_FILL) else colors.surface2),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (row.filled) Icons.Filled.Check else sectionGlyph(row.key),
                contentDescription = null,
                tint = if (row.filled) colors.accentInk else colors.ink4,
                modifier = Modifier.size(dimens.size.iconMd),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                row.title,
                style = AppTheme.type.rowTitle,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                row.detail,
                style = AppTheme.type.caption,
                color = if (row.filled) colors.ink4 else colors.accentInk,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.lineStrong,
            modifier = Modifier.size(dimens.size.iconLg),
        )
    }
}

/**
 * The same row as an invitation (87).
 *
 * A card with a "+" square rather than a hairline row with a chevron. On a kit
 * where every section is empty, a list of chevrons reads as navigation the artist
 * has to work through; a stack of cards that each name what belongs in them reads
 * as a set of offers. The design's note calls them invitations, and this is the
 * difference between the two.
 */
@Composable
fun EpkInvitationRow(
    row: EpkSectionRow,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(dimens.radii.buttonLg)
    Row(
        modifier
            .fillMaxWidth()
            .pressScale(interaction)
            .clip(shape)
            .background(colors.surface3)
            .clickable(
                interactionSource = interaction,
                indication = ripple(),
                role = Role.Button,
                onClick = onClick,
            )
            .padding(dimens.space.md)
            .semantics(mergeDescendants = true) {
                contentDescription = "Add ${row.title}. ${row.invitation}"
            },
        horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(dimens.size.avatarSm)
                .clip(RoundedCornerShape(dimens.radii.md))
                .background(colors.hairline),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = null,
                tint = colors.ink2,
                modifier = Modifier.size(dimens.size.iconMd),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(row.title, style = AppTheme.type.rowTitle, color = colors.ink)
            Text(
                row.invitation,
                style = AppTheme.type.caption,
                color = colors.ink4,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A gap's own glyph, so the empty squares are not six identical grey boxes. */
private fun sectionGlyph(key: EpkSectionKey): ImageVector = when (key) {
    EpkSectionKey.Bio -> EpkSectionIcons.Bio
    EpkSectionKey.Personality -> EpkSectionIcons.Personality
    EpkSectionKey.Samples -> EpkSectionIcons.Samples
    EpkSectionKey.Packages -> EpkSectionIcons.Packages
    EpkSectionKey.Tech -> EpkSectionIcons.Tech
    EpkSectionKey.Links -> EpkSectionIcons.Links
}

/**
 * The six glyphs, resolved once.
 *
 * `Icons.Filled.X` is a `get()` that rebuilds its `ImageVector` on every read, so
 * six of them inside a `when` inside a row would rebuild six vectors per row per
 * recomposition. Hoisting them into an object makes each one a single instance
 * for the life of the process.
 */
private object EpkSectionIcons {
    val Bio: ImageVector = Icons.AutoMirrored.Filled.Notes
    val Personality: ImageVector = Icons.AutoMirrored.Filled.Chat
    val Samples: ImageVector = Icons.Filled.PlayArrow
    val Packages: ImageVector = Icons.Filled.Sell
    val Tech: ImageVector = Icons.Filled.Tune
    val Links: ImageVector = Icons.Filled.Link
}

// ── Constants ────────────────────────────────────────────────────────────────

/** The strip's cell count. Three, and it does not grow — see [EpkGalleryStrip]. */
private const val GALLERY_CELLS = 3

/** The `Banner`'s Note tint, reused so the upload card is the same family. */
private const val NOTE_FILL = 0.22f
private const val NOTE_LINE = 0.60f

/** The accent behind a filled section's tick — `rgba(214,248,75,.34)` on 23. */
private const val TICK_FILL = 0.34f

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun EpkHubPreview() {
    ArtistantTheme {
        val rows = epkSectionRows(
            bio = "Warm four-part harmonies and a live cajón.",
            serviceTagCount = 3,
            answeredPromptCount = 1,
            promptTotal = 4,
            sampleCount = 3,
            packageCount = 2,
            fromPriceInr = 26_000,
            techCount = 0,
            linkCount = 1,
            socialCount = 2,
        )
        Column(
            Modifier
                .padding(AppTheme.dimens.component.gutter)
                .width(PREVIEW_WIDTH),
            verticalArrangement = Arrangement.spacedBy(AppTheme.dimens.space.lg),
        ) {
            EpkCompletionMeter(epkCompletion(rows, hasCover = true))
            EpkQueueBanner(
                banner = EpkUploadBanner.Working(
                    title = "Uploading 2 of 3",
                    detail = "Encore, Bengaluru",
                    fraction = 0.33f,
                ),
                onDetails = {},
            )
            Column {
                rows.forEachIndexed { index, row ->
                    EpkSectionListRow(row, onClick = {}, showHairline = index < rows.lastIndex)
                }
            }
            EpkInvitationRow(rows[0], onClick = {})
        }
    }
}

private val PREVIEW_WIDTH = 350.dp
