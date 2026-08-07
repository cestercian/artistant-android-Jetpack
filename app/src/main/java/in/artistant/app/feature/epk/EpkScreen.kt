package `in`.artistant.app.feature.epk

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import `in`.artistant.app.common.util.formatInr
import `in`.artistant.app.data.model.Artist
import `in`.artistant.app.data.model.Sample
import `in`.artistant.app.data.repository.ArtistLink
import `in`.artistant.app.data.repository.ArtistMediaItem
import `in`.artistant.app.designsystem.component.ButtonVariant
import `in`.artistant.app.designsystem.component.EmptyState
import `in`.artistant.app.designsystem.component.HRule
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.component.RevealOnAppear
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.domain.artist.PackagePricing
import kotlinx.coroutines.delay

/**
 * The artist's press-kit editor — the private, editable twin of
 * `ArtistProfileScreen`.
 *
 * It is laid out as the public page is: a cover carrying the identity, then a
 * column of editorial blocks separated by air and hairlines, nothing wrapped in
 * a card. Editing happens IN the blocks rather than behind a pencil icon or a
 * separate form, so the artist is always looking at something close to what a
 * client will see.
 *
 * **What is editable here, and why the rest is not.** Photos, pricing tiers,
 * samples, the tech rider and external links each have a repository that can
 * write them, so each is edited in place. The bio, the three social accounts and
 * the cover gradient have no narrow write path on Android — the only code that
 * writes those columns is the wizard's publish, which upserts the WHOLE artist
 * row and would blank every field it does not carry. So they render read-only
 * here and are reported as a gap rather than wired to a control that would
 * quietly destroy the artist's other fields.
 *
 * **Performance.** One lazy scroll container; sections are `item`s so an
 * offscreen block is not composed at all. The hero is the only place with a
 * gradient, and it has exactly two layers (the palette floor and the fade into
 * the page) — the same pair the public profile uses. Per-row callbacks are
 * key-taking method references on the ViewModel, so they are stable across
 * recomposition and a keystroke in one pricing field does not re-run the other
 * eight sections.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpkScreen(
    onEditInWizard: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EpkViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AppTheme.colors

    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.onSamplePicked(uri, uri.lastPathSegment)
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.onPhotoPicked(uri)
    }

    // Transient confirmations clear themselves. They exist for writes with no
    // visible result (a debounced pricing save changes nothing on screen), so
    // they must not linger and become ambient noise.
    LaunchedEffect(state.statusNote) {
        if (state.statusNote != null) {
            delay(STATUS_NOTE_MS)
            viewModel.consumeStatusNote()
        }
    }

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = viewModel::refresh,
        modifier = modifier.fillMaxSize().background(colors.bg),
    ) {
        when {
            state.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.brand)
                }
            }
            // Not published yet. The wizard is the correct destination HERE and
            // only here: there is no server-side profile for a re-publish to
            // overwrite, which is not true once the artist has one.
            state.artist == null && !state.setupComplete -> {
                EmptyState(
                    title = "Your profile isn't live yet",
                    body = "Finish the setup wizard and your press kit appears here, ready to edit.",
                    actionLabel = "Finish your profile",
                    onAction = onEditInWizard,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            state.artist == null -> {
                EmptyState(
                    title = "Couldn't load your profile",
                    body = state.loadError,
                    actionLabel = "Retry",
                    onAction = viewModel::refresh,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            else -> RevealOnAppear {
                EpkEditor(
                    state = state,
                    viewModel = viewModel,
                    onPickPhoto = { mime -> pickPhoto.launch(mime) },
                    onPickAudio = { mimes -> pickAudio.launch(mimes) },
                )
            }
        }
    }

    val editor = state.linkEditor
    if (editor != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = viewModel::dismissLinkEditor,
            sheetState = sheetState,
            containerColor = colors.bg,
        ) {
            LinkEditorSheetContent(
                editor = editor,
                busy = state.busyLinks,
                onLabel = viewModel::onLinkEditorLabel,
                onUrl = viewModel::onLinkEditorUrl,
                onSave = viewModel::saveLinkEditor,
                onDelete = { editor.id?.let(viewModel::deleteLink) },
            )
        }
    }
}

@Composable
private fun EpkEditor(
    state: EpkUiState,
    viewModel: EpkViewModel,
    onPickPhoto: (String) -> Unit,
    onPickAudio: (Array<String>) -> Unit,
) {
    val artist = state.artist ?: return
    val dimens = AppTheme.dimens
    val space = dimens.space

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = dimens.hero.scrollTailroom),
        verticalArrangement = Arrangement.spacedBy(space.xl),
    ) {
        item(key = "hero") {
            EpkHero(artist = artist, coverUrl = state.photos.firstOrNull()?.publicUrl ?: artist.coverUrl)
        }
        item(key = "status") {
            StatusBlock(
                state = state,
                artist = artist,
                onRetry = viewModel::refresh,
                onDismissSaveError = viewModel::dismissSaveError,
                modifier = Modifier.padding(horizontal = space.lg),
            )
        }
        item(key = "photos") {
            PhotosSection(
                photos = state.photos,
                uploading = state.uploadingPhoto,
                canReorder = state.photosHydrated,
                onAdd = { onPickPhoto("image/*") },
                onDelete = viewModel::deletePhoto,
                onMove = viewModel::movePhoto,
                modifier = Modifier.padding(horizontal = space.lg),
            )
        }
        item(key = "about") {
            AboutSection(bio = artist.bio, modifier = Modifier.padding(horizontal = space.lg))
        }
        item(key = "pricing") {
            PricingSection(
                rows = state.packageRows,
                fallbackPrice = artist.price,
                hydrated = state.packagesHydrated,
                saving = state.savingPackages,
                onAdd = viewModel::addPackageRow,
                onName = viewModel::onPackageName,
                onDuration = viewModel::onPackageDuration,
                onPrice = viewModel::onPackagePrice,
                onPopular = viewModel::onPackagePopular,
                onRemove = viewModel::removePackageRow,
                modifier = Modifier.padding(horizontal = space.lg),
            )
        }
        item(key = "samples") {
            SamplesSection(
                samples = state.samples,
                onAdd = { onPickAudio(arrayOf("audio/*")) },
                onDelete = viewModel::deleteSample,
                modifier = Modifier.padding(horizontal = space.lg),
            )
        }
        item(key = "tech") {
            TechSection(
                items = state.techItems,
                draft = state.techDraft,
                hydrated = state.techHydrated,
                saving = state.savingTech,
                onToggle = viewModel::toggleTechPreset,
                onDraft = viewModel::onTechDraft,
                onAddDraft = viewModel::addTechDraft,
                modifier = Modifier.padding(horizontal = space.lg),
            )
        }
        item(key = "socials") {
            SocialSection(artist = artist, modifier = Modifier.padding(horizontal = space.lg))
        }
        item(key = "links") {
            LinksSection(
                links = state.links,
                onAdd = { viewModel.openLinkEditor(null) },
                onEdit = viewModel::openLinkEditor,
                modifier = Modifier.padding(horizontal = space.lg),
            )
        }
        item(key = "share") {
            ShareLinkSection(handle = artist.handle, modifier = Modifier.padding(horizontal = space.lg))
        }
    }
}

// ── Hero ─────────────────────────────────────────────────────────────────────

/**
 * The cover, exactly as a client meets it.
 *
 * The gradient floor paints first so a slow or missing cover is never a hole,
 * and the fade at the bottom ends on the PAGE background rather than on black —
 * ramping to black bottoms out darker than `bg` and leaves a visible step where
 * the seam is meant to vanish. Same construction as the public profile's hero,
 * deliberately: this is the editor showing the artist the real thing, not a
 * preview of it.
 *
 * The cover image is the first photo in position order, not `artist.coverUrl`.
 * That field comes from the cached artist row, which the reorder write does not
 * invalidate — reading it here would leave the hero showing the previous cover
 * after the artist had just promoted a new one.
 */
@Composable
private fun EpkHero(artist: Artist, coverUrl: String?) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val space = dimens.space
    Box(
        Modifier
            .fillMaxWidth()
            .height(dimens.size.heroShort),
    ) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(artist.gradient)))
        if (!coverUrl.isNullOrBlank()) {
            AsyncImage(
                model = coverUrl,
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
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = space.lg)
                .padding(bottom = space.lg),
            verticalArrangement = Arrangement.spacedBy(space.sm),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                if (artist.category.isNotBlank()) MediaChip(artist.category)
                if (artist.city.isNotBlank()) MediaChip(artist.city, showPin = true)
            }
            Text(
                artist.name.ifBlank { "Your stage name" },
                style = AppTheme.type.profileHeroName,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (artist.genre.isNotBlank()) {
                Text(artist.genre, style = AppTheme.type.callout, color = colors.inkOnMedia)
            }
        }
    }
}

/** Translucent caption on the cover — an opaque fill would punch a hole in the photo. */
@Composable
private fun MediaChip(text: String, showPin: Boolean = false) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Row(
        Modifier
            .clip(CircleShape)
            .background(colors.chipOnMedia)
            .border(dimens.size.hairline, colors.chipOnMediaLine, CircleShape)
            .padding(horizontal = dimens.space.md, vertical = dimens.space.xs),
        horizontalArrangement = Arrangement.spacedBy(dimens.space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showPin) {
            Icon(
                Icons.Filled.LocationOn,
                contentDescription = null,
                tint = colors.ink2,
                modifier = Modifier.size(dimens.size.iconSm),
            )
        }
        Text(text, style = AppTheme.type.caption, color = colors.ink2)
    }
}

// ── Status ───────────────────────────────────────────────────────────────────

/**
 * What is still missing, and what just failed.
 *
 * The completeness line is not a score — it is the section list filtered to the
 * empty ones, at the top of a page that is eight scrolls long. Without it the
 * artist has to walk the whole editor to find the one block holding their
 * profile back.
 */
@Composable
private fun StatusBlock(
    state: EpkUiState,
    artist: Artist,
    onRetry: () -> Unit,
    onDismissSaveError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val completeness = epkCompleteness(
        photoCount = state.photos.size,
        bio = artist.bio,
        packageCount = state.packageRows.count(::packageRowIsSavable),
        sampleCount = state.samples.size,
        techCount = state.techItems.size,
        socialCount = socialLinkCount(
            artist.spotifyArtistUrl,
            artist.instagramHandle,
            artist.youtubeChannelUrl,
        ),
        linkCount = state.links.size,
    )
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(space.md)) {
        state.loadError?.let {
            EpkBanner(message = it, actionLabel = "Retry", onAction = onRetry)
        }
        state.saveError?.let {
            EpkBanner(message = it, onDismiss = onDismissSaveError)
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${completeness.complete}/${completeness.total}",
                style = AppTheme.type.monoStat,
                color = if (completeness.isComplete) colors.good else colors.ink,
            )
            // The save state rides here rather than beside each section: there
            // is one write queue, and one place to look for it beats three.
            val note = when {
                state.anySaveInFlight -> "Saving…"
                state.statusNote != null -> state.statusNote
                else -> null
            }
            if (note != null) {
                Text(note, style = AppTheme.type.caption, color = colors.ink3)
            }
        }
        Text(
            if (completeness.isComplete) {
                "Your press kit is complete."
            } else {
                "Still missing ${completeness.missing.joinToString(", ")}."
            },
            style = AppTheme.type.footnote,
            color = colors.ink3,
        )
    }
}

// ── Photos ───────────────────────────────────────────────────────────────────

/**
 * Three-column square grid with a selection-driven action row.
 *
 * The actions sit BELOW the grid rather than inside each tile. A third of a
 * phone's width is roughly 110dp, which cannot hold four controls at the 44dp
 * touch-target floor — putting them there means either sub-target buttons or
 * unlabelled 16dp glyphs. Selecting a tile and acting on it in a full-width row
 * keeps every control legible and tappable, and it makes "which photo am I about
 * to delete" unambiguous, which a row of tiny × buttons does not.
 *
 * The grid is a `Column` of `Row`s, not a nested lazy grid: nesting a vertically
 * scrolling lazy grid inside a `LazyColumn` is an infinite-height constraint
 * crash, and at a six-photo cap there is nothing to virtualise anyway.
 */
@Composable
private fun PhotosSection(
    photos: List<ArtistMediaItem>,
    uploading: Boolean,
    canReorder: Boolean,
    onAdd: () -> Unit,
    onDelete: (ArtistMediaItem) -> Unit,
    onMove: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val space = dimens.space
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    // Resolve against the CURRENT list rather than trusting the saved id: a
    // background refresh can delete the selection out from under us, and a stale
    // id must simply mean "nothing selected", never a crash or a wrong target.
    val selectedIndex = photos.indexOfFirst { it.id == selectedId }.takeIf { it >= 0 }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(space.md)) {
        EpkSectionHeader(
            title = "Photos",
            actionLabel = if (uploading) "Uploading…" else "+ Add",
            onAction = onAdd,
            actionEnabled = canAddPhoto(photos.size, uploading),
            trailingNote = if (photos.isEmpty()) null else "${photos.size}/$MAX_PHOTOS",
        )
        if (photos.isEmpty()) {
            Text(
                "Add a photo — the first one becomes your cover, and it is the only thing most clients see before they tap.",
                style = AppTheme.type.footnote,
                color = colors.ink3,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(space.xs)) {
                photos.chunked(PHOTO_COLUMNS).forEach { rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(space.xs)) {
                        rowItems.forEach { item ->
                            PhotoCell(
                                item = item,
                                isCover = photos.firstOrNull()?.id == item.id,
                                selected = item.id == selectedId,
                                onClick = { selectedId = if (item.id == selectedId) null else item.id },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        // Keep the last row's cells the same size as a full row's
                        // rather than letting two photos stretch to half-width.
                        repeat(PHOTO_COLUMNS - rowItems.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            if (selectedIndex != null) {
                PhotoActionRow(
                    index = selectedIndex,
                    lastIndex = photos.lastIndex,
                    canReorder = canReorder,
                    onMove = onMove,
                    onDelete = {
                        onDelete(photos[selectedIndex])
                        selectedId = null
                    },
                )
            } else {
                Text(
                    "Tap a photo to reorder or remove it.",
                    style = AppTheme.type.caption,
                    color = colors.ink3,
                )
            }
        }
    }
}

@Composable
private fun PhotoCell(
    item: ArtistMediaItem,
    isCover: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Box(
        modifier
            .aspectRatio(dimens.aspect.square)
            .clip(RoundedCornerShape(dimens.radii.sm))
            .background(colors.bgSoft)
            .border(
                dimens.size.stroke,
                if (selected) colors.brand else Color.Transparent,
                RoundedCornerShape(dimens.radii.sm),
            )
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = if (isCover) "Cover photo" else "Gallery photo"
            },
    ) {
        AsyncImage(
            model = item.publicUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (isCover) {
            Text(
                "COVER",
                style = AppTheme.type.monoMicro,
                color = colors.brandInk,
                modifier = Modifier
                    .padding(dimens.space.xs)
                    .clip(CircleShape)
                    .background(colors.brand)
                    .padding(horizontal = dimens.space.sm, vertical = dimens.space.xs),
            )
        }
    }
}

@Composable
private fun PhotoActionRow(
    index: Int,
    lastIndex: Int,
    canReorder: Boolean,
    onMove: (Int, Int) -> Unit,
    onDelete: () -> Unit,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Column {
        HRule()
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (index > 0 && canReorder) {
                EpkRowAction("Make cover", { onMove(index, 0) }, tone = colors.brand)
                EpkRowAction("Earlier", { onMove(index, index - 1) }, tone = colors.ink2)
            }
            if (index < lastIndex && canReorder) {
                EpkRowAction("Later", { onMove(index, index + 1) }, tone = colors.ink2)
            }
            Spacer(Modifier.weight(1f))
            EpkRowAction("Remove", onDelete, tone = colors.hot)
        }
        HRule()
    }
}

// ── About ────────────────────────────────────────────────────────────────────

/**
 * The bio, read-only.
 *
 * There is no narrow write for `artists.bio` on Android — the only writer is the
 * wizard's whole-row upsert, which sends every profile column and would blank
 * the ones it does not carry (socials, cover gradient) on the way past. An edit
 * control here would therefore be a control that silently destroys three other
 * fields, so the section shows the bio and says where it comes from instead.
 */
@Composable
private fun AboutSection(bio: String, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(space.md)) {
        EpkSectionHeader(title = "About")
        if (bio.isBlank()) {
            Text(
                "No bio yet. Clients read this before anything else on your profile.",
                style = AppTheme.type.footnote,
                color = colors.ink3,
            )
        } else {
            Text(bio, style = AppTheme.type.body, color = colors.ink2)
        }
    }
}

// ── Pricing ──────────────────────────────────────────────────────────────────

/**
 * The tiers, edited in place.
 *
 * Every field autosaves — there is no Save button — because a pricing editor
 * with one is a pricing editor artists leave half-saved. The write is debounced
 * and the state of it is reported at the top of the page, so "did that stick" is
 * always answerable without an extra tap.
 *
 * The whole block goes read-only when the server list has not been read. That is
 * the wipe guard made visible: these rows persist by replacing the entire set,
 * so editing an unread set would publish an empty one.
 */
@Composable
private fun PricingSection(
    rows: List<PackageRow>,
    fallbackPrice: Int,
    hydrated: Boolean,
    saving: Boolean,
    onAdd: () -> Unit,
    onName: (String, String) -> Unit,
    onDuration: (String, String) -> Unit,
    onPrice: (String, String) -> Unit,
    onPopular: (String, Boolean) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    // The live "from" figure goes through the shared helper — the same one the
    // public profile and the booking dock use — so the editor can never quote a
    // different minimum than the page it is editing.
    val preview = previewPackages(rows)
    val fromPrice = PackagePricing.fromPrice(preview, fallback = fallbackPrice)

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(space.md)) {
        EpkSectionHeader(
            title = "Pricing tiers",
            actionLabel = "+ Add tier",
            onAction = onAdd,
            actionEnabled = hydrated,
            trailingNote = if (saving) "Saving…" else null,
        )
        if (!hydrated) {
            Text(
                "Couldn't read your published tiers, so editing is off — pull to refresh to try again.",
                style = AppTheme.type.footnote,
                color = colors.warm,
            )
        }
        if (preview.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                Text(
                    formatInr(fromPrice),
                    style = AppTheme.type.monoHero,
                    color = colors.ink,
                    modifier = Modifier.alignByBaseline(),
                )
                Text(
                    "clients see this as your from price",
                    style = AppTheme.type.footnote,
                    color = colors.ink3,
                    modifier = Modifier.alignByBaseline(),
                )
            }
        }
        if (rows.isEmpty()) {
            Text(
                "No tiers yet. Add at least one so clients can book without asking what you charge.",
                style = AppTheme.type.footnote,
                color = colors.ink3,
            )
        } else {
            val badgeMeansSomething = popularBadgeWouldMeanSomething(rows)
            Column {
                HRule()
                rows.forEach { row ->
                    PackageEditorRow(
                        row = row,
                        enabled = hydrated,
                        badgeMeansSomething = badgeMeansSomething,
                        onName = onName,
                        onDuration = onDuration,
                        onPrice = onPrice,
                        onPopular = onPopular,
                        onRemove = onRemove,
                    )
                    HRule()
                }
            }
            if (!badgeMeansSomething && rows.any { it.popular }) {
                Text(
                    "A \"Popular\" badge only shows when some tiers carry it and others don't — right now it would appear on every tier, so clients won't see it at all.",
                    style = AppTheme.type.caption,
                    color = colors.ink3,
                )
            }
        }
    }
}

@Composable
private fun PackageEditorRow(
    row: PackageRow,
    enabled: Boolean,
    badgeMeansSomething: Boolean,
    onName: (String, String) -> Unit,
    onDuration: (String, String) -> Unit,
    onPrice: (String, String) -> Unit,
    onPopular: (String, Boolean) -> Unit,
    onRemove: (String) -> Unit,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Column(
        Modifier.fillMaxWidth().padding(vertical = space.sm),
        verticalArrangement = Arrangement.spacedBy(space.sm),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(space.md),
            verticalAlignment = Alignment.Bottom,
        ) {
            EpkField(
                value = row.name,
                onValueChange = { onName(row.key, it) },
                placeholder = "Tier name",
                enabled = enabled,
                textStyle = AppTheme.type.callout,
                contentDescription = "Tier name",
                modifier = Modifier.weight(NAME_CELL_WEIGHT),
            )
            // Weighted, not a fixed Dp: at large font scales a fixed price cell
            // clips ₹10,00,000, and the name field should give up the room rather
            // than the number the whole row exists to state.
            Row(
                Modifier.weight(PRICE_CELL_WEIGHT),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text("₹", style = AppTheme.type.monoMedium, color = colors.ink3)
                Spacer(Modifier.width(space.xs))
                EpkField(
                    value = row.price,
                    onValueChange = { onPrice(row.key, it) },
                    placeholder = "0",
                    enabled = enabled,
                    textStyle = AppTheme.type.monoMedium,
                    keyboardType = KeyboardType.Number,
                    textAlign = TextAlign.End,
                    contentDescription = "Price in rupees",
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EpkField(
                value = row.duration,
                onValueChange = { onDuration(row.key, it) },
                placeholder = "Duration (e.g. 60 min)",
                enabled = enabled,
                textStyle = AppTheme.type.footnote,
                contentDescription = "Set duration",
                modifier = Modifier.weight(1f),
            )
            EpkChip(
                label = "Popular",
                // Reflects what the artist SET, not whether it will render on the
                // public page — a control that ignores your tap is worse than one
                // whose effect the note below explains.
                selected = row.popular,
                enabled = enabled,
                onClick = { onPopular(row.key, !row.popular) },
            )
            EpkRowAction("Remove", { onRemove(row.key) }, tone = colors.hot, enabled = enabled)
        }
    }
}

// ── Samples ──────────────────────────────────────────────────────────────────

@Composable
private fun SamplesSection(
    samples: List<Sample>,
    onAdd: () -> Unit,
    onDelete: (Sample) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(space.md)) {
        EpkSectionHeader(
            title = "Music samples",
            actionLabel = "+ Add",
            onAction = onAdd,
            actionEnabled = canAddSample(samples.size, uploadInFlight = false),
            trailingNote = if (samples.isEmpty()) null else "${samples.size}/$MAX_SAMPLES",
        )
        if (samples.isEmpty()) {
            Text(
                "Add a few clips so clients can hear you — anything 30s to 2 min works.",
                style = AppTheme.type.footnote,
                color = colors.ink3,
            )
        } else {
            Column {
                HRule()
                samples.forEach { sample ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = space.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                sample.title,
                                style = AppTheme.type.callout,
                                color = colors.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (sample.duration.isNotBlank()) {
                                Text(sample.duration, style = AppTheme.type.monoSmall, color = colors.ink3)
                            }
                        }
                        EpkRowAction("Remove", { onDelete(sample) }, tone = colors.hot)
                    }
                    HRule()
                }
            }
        }
    }
}

// ── Tech rider ───────────────────────────────────────────────────────────────

/**
 * Presets first, then whatever the artist added themselves, then a field to add
 * more. One flow of chips rather than a list with checkboxes: a rider is a set,
 * and a set reads faster as chips than as rows.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TechSection(
    items: List<String>,
    draft: String,
    hydrated: Boolean,
    saving: Boolean,
    onToggle: (String) -> Unit,
    onDraft: (String) -> Unit,
    onAddDraft: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    // Presets in their canonical order, then the artist's own additions in the
    // order they were added — so the rider reads the same way every time.
    val custom = items.filterNot { item -> TECH_PRESETS.any { it.equals(item, ignoreCase = true) } }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(space.md)) {
        EpkSectionHeader(title = "Tech rider", trailingNote = if (saving) "Saving…" else null)
        if (!hydrated) {
            Text(
                "Couldn't read your rider, so editing is off — pull to refresh to try again.",
                style = AppTheme.type.footnote,
                color = colors.warm,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(space.sm),
            verticalArrangement = Arrangement.spacedBy(space.sm),
        ) {
            TECH_PRESETS.forEach { preset ->
                EpkChip(
                    label = preset,
                    selected = items.any { it.equals(preset, ignoreCase = true) },
                    enabled = hydrated,
                    onClick = { onToggle(preset) },
                )
            }
            custom.forEach { item ->
                EpkChip(label = item, selected = true, enabled = hydrated, onClick = { onToggle(item) })
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EpkField(
                value = draft,
                onValueChange = onDraft,
                placeholder = "Add your own…",
                enabled = hydrated,
                textStyle = AppTheme.type.footnote,
                contentDescription = "Add a tech rider item",
                modifier = Modifier.weight(1f),
            )
            EpkRowAction(
                "Add",
                onAddDraft,
                tone = colors.brand,
                enabled = hydrated && draft.isNotBlank(),
            )
        }
    }
}

// ── Connected accounts ───────────────────────────────────────────────────────

/**
 * Read-only, for the same reason the bio is: the three social columns have no
 * narrow write path on Android, and the only code that writes them sends the
 * whole artist row.
 */
@Composable
private fun SocialSection(artist: Artist, modifier: Modifier = Modifier) {
    val space = AppTheme.dimens.space
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(space.md)) {
        EpkSectionHeader(title = "Connected accounts")
        Column {
            HRule()
            SocialRow("Spotify", artist.spotifyArtistUrl)
            HRule()
            SocialRow("Instagram", artist.instagramHandle)
            HRule()
            SocialRow("YouTube", artist.youtubeChannelUrl)
            HRule()
        }
    }
}

@Composable
private fun SocialRow(label: String, value: String?) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val linked = !value.isNullOrBlank()
    Row(
        Modifier.fillMaxWidth().padding(vertical = space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = AppTheme.type.callout, color = colors.ink)
            if (linked) {
                Text(
                    value.orEmpty(),
                    style = AppTheme.type.caption,
                    color = colors.ink3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            if (linked) "Linked" else "Not linked",
            style = AppTheme.type.footnote,
            color = if (linked) colors.good else colors.ink3,
        )
    }
}

// ── Links ────────────────────────────────────────────────────────────────────

@Composable
private fun LinksSection(
    links: List<ArtistLink>,
    onAdd: () -> Unit,
    onEdit: (ArtistLink) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(space.md)) {
        EpkSectionHeader(title = "Links", actionLabel = "+ Add", onAction = onAdd)
        if (links.isEmpty()) {
            Text(
                "Bandcamp, SoundCloud, your own site — anywhere a client should land.",
                style = AppTheme.type.footnote,
                color = colors.ink3,
            )
        } else {
            Column {
                HRule()
                links.forEach { link ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onEdit(link) }
                            .padding(vertical = space.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(link.label, style = AppTheme.type.callout, color = colors.ink)
                            Text(
                                link.url,
                                style = AppTheme.type.caption,
                                color = colors.ink3,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text("Edit", style = AppTheme.type.footnote, color = colors.brand)
                    }
                    HRule()
                }
            }
        }
    }
}

/**
 * Add and edit share one sheet, and the sheet is where delete lives too.
 *
 * A delete affordance on the list row would be a destructive action one stray
 * tap from a scroll; behind the editor it takes a deliberate open first.
 */
@Composable
private fun LinkEditorSheetContent(
    editor: LinkEditorState,
    busy: Boolean,
    onLabel: (String) -> Unit,
    onUrl: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = space.lg)
            .padding(bottom = space.xxl),
        verticalArrangement = Arrangement.spacedBy(space.lg),
    ) {
        Text(
            if (editor.isExisting) "Edit link" else "Add link",
            style = AppTheme.type.displaySmall,
            color = colors.ink,
        )
        EpkField(
            value = editor.label,
            onValueChange = onLabel,
            placeholder = "Label — e.g. Bandcamp",
            contentDescription = "Link label",
        )
        EpkField(
            value = editor.url,
            onValueChange = onUrl,
            placeholder = "bandcamp.com/yourband",
            keyboardType = KeyboardType.Uri,
            contentDescription = "Link address",
        )
        Text(
            // Says what the save will do rather than rejecting the input: an
            // artist pasting a scheme-less host is doing the normal thing.
            "No https:// needed — we'll add it.",
            style = AppTheme.type.caption,
            color = colors.ink3,
        )
        PrimaryButton(
            text = if (busy) "Saving…" else "Save link",
            onClick = onSave,
            enabled = !busy && linkIsSavable(editor.label.trim(), editor.url.trim()),
            fullWidth = true,
        )
        if (editor.isExisting) {
            PrimaryButton(
                text = "Remove link",
                onClick = onDelete,
                enabled = !busy,
                variant = ButtonVariant.Ghost,
                fullWidth = true,
            )
        }
    }
}

// ── Share link ───────────────────────────────────────────────────────────────

@Composable
private fun ShareLinkSection(handle: String, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val clipboard = LocalClipboardManager.current
    val url = shareLinkUrl(handle)
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(COPIED_RESET_MS)
            copied = false
        }
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(space.md)) {
        EpkSectionHeader(title = "Share link")
        if (url == null) {
            // No placeholder URL. A fake link beside a Copy button is a link the
            // artist sends to a venue.
            Text(
                "Your public link appears once your profile is live.",
                style = AppTheme.type.footnote,
                color = colors.ink3,
            )
        } else {
            Column {
                HRule()
                Row(
                    Modifier.fillMaxWidth().padding(vertical = space.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(url, style = AppTheme.type.monoSmall, color = colors.ink2, modifier = Modifier.weight(1f))
                    Text(
                        if (copied) "COPIED" else "COPY",
                        style = AppTheme.type.monoMicro,
                        color = colors.brand,
                        modifier = Modifier
                            .clickable {
                                clipboard.setText(AnnotatedString(url))
                                copied = true
                            }
                            .padding(space.sm),
                    )
                }
                HRule()
            }
        }
    }
}

// ── Constants ────────────────────────────────────────────────────────────────

private const val PHOTO_COLUMNS = 3
private const val STATUS_NOTE_MS = 2_200L
private const val COPIED_RESET_MS = 1_400L

/** Tier row split: the name takes two thirds, the price the remaining third. */
private const val NAME_CELL_WEIGHT = 2f
private const val PRICE_CELL_WEIGHT = 1f
