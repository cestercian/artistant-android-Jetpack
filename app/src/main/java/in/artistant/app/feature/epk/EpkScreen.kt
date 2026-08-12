package `in`.artistant.app.feature.epk

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.semantics.selected
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
import `in`.artistant.app.data.model.ArtistGradient
import `in`.artistant.app.data.model.ArtistPrompt
import `in`.artistant.app.data.model.Sample
import `in`.artistant.app.data.repository.ArtistLink
import `in`.artistant.app.data.repository.ArtistMediaItem
import `in`.artistant.app.designsystem.component.BottomDarkenScrim
import `in`.artistant.app.designsystem.component.ButtonVariant
import `in`.artistant.app.designsystem.component.EmptyState
import `in`.artistant.app.designsystem.component.HRule
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.component.RevealOnAppear
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.domain.artist.ArtistPrompts
import `in`.artistant.app.domain.artist.PackagePricing
import `in`.artistant.app.domain.artist.ServiceTags
import kotlinx.coroutines.delay
import java.util.Locale

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
    onOpenAccount: () -> Unit,
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

    Column(modifier.fillMaxSize().background(colors.bg)) {
        // OUTSIDE the scroll, and outside the load/empty/error branch below.
        //
        // Pinned because that is what it is on the reference — navigation chrome
        // under the status bar, not a piece of content that scrolls away. Pinned
        // ALSO because the avatar in it is the artist's only route to account
        // settings, and therefore to account deletion: rendering it inside the
        // loaded branch would mean an artist whose profile failed to load, or who
        // has not published one yet, could not reach it at all.
        EpkTitleBar(
            title = "Profile",
            subtitle = "Your booking-ready profile",
            // Falls back to a generic seed rather than a blank disc — the artist
            // row may not have loaded, and an empty circle reads as broken.
            avatarName = state.artist?.name?.takeIf { it.isNotBlank() } ?: "You",
            onOpenAccount = onOpenAccount,
            modifier = Modifier
                .padding(horizontal = AppTheme.dimens.space.lg)
                .padding(top = AppTheme.dimens.space.md, bottom = AppTheme.dimens.space.lg),
        )
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize(),
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
        // ── Section order ────────────────────────────────────────────────
        // Cover, then ABOUT -> WHAT YOU OFFER -> PERSONALITY -> PHOTOS ->
        // MUSIC SAMPLES -> CONNECTED ACCOUNTS -> PRICING TIERS -> TECH RIDER ->
        // LINKS -> SHARE LINK, matching the reference client.
        //
        // The order is an argument, not a filing system, and it had drifted into
        // the wrong one. Photos led, so the screen opened by asking for assets
        // before it had asked who the artist is; pricing sat directly under the
        // service tags, which reads as "here is what you do, now price it" when
        // the tiers are actually priced against the whole kit; and connected
        // accounts had fallen past the tech rider, stranding the proof-of-reach
        // section among the logistics.
        //
        // Reference order goes identity -> offer -> voice -> evidence ->
        // commercials -> logistics -> distribution, which is also the order an
        // artist can actually answer in.
        //
        // Keys are semantic strings, so reordering keeps each item's identity
        // and any state saved against it.

        // Errors and save status ride ABOVE the cover — the reference puts its
        // banners there, and a failure notice below the fold is a failure notice
        // nobody reads. (The x/7 completeness counter inside this block has no
        // reference equivalent; it is ours, and this is the one place it can sit
        // without displacing a section.)
        item(key = "status") {
            StatusBlock(
                state = state,
                onRetry = viewModel::refresh,
                onDismissSaveError = viewModel::dismissSaveError,
                modifier = Modifier.padding(horizontal = space.lg),
            )
        }
        item(key = "cover") {
            CoverSection(
                artist = artist,
                // Reads the photos list, not the photos SECTION, so this is
                // unaffected by where that section now sits.
                coverUrl = state.photos.firstOrNull()?.publicUrl ?: artist.coverUrl,
                selectedGradient = shownCoverGradient(state.coverGradientIndex, artist.coverGradientIndex),
                canEdit = state.identityHydrated,
                onPickGradient = viewModel::onCoverGradientPicked,
                modifier = Modifier.padding(horizontal = space.lg),
            )
        }
        item(key = "about") {
            AboutSection(
                bio = state.bioDraft,
                canEdit = state.identityHydrated,
                onBioChanged = viewModel::onBioChanged,
                modifier = Modifier.padding(horizontal = space.lg),
            )
        }
        item(key = "services") {
            ServicesSection(
                selected = shownServiceTags(state.serviceTags, artist.serviceTags),
                canEdit = state.identityHydrated,
                onToggle = viewModel::onServiceTagToggled,
                modifier = Modifier.padding(horizontal = space.lg),
            )
        }
        item(key = "prompts") {
            PromptsSection(
                drafts = state.promptDrafts,
                canEdit = state.identityHydrated,
                saving = state.savingPrompts,
                onAnswer = viewModel::onPromptAnswerChanged,
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
        item(key = "samples") {
            SamplesSection(
                samples = state.samples,
                onAdd = { onPickAudio(arrayOf("audio/*")) },
                onDelete = viewModel::deleteSample,
                modifier = Modifier.padding(horizontal = space.lg),
            )
        }
        item(key = "socials") {
            SocialSection(
                draft = state.socialDraft,
                canEdit = state.identityHydrated,
                saving = state.savingSocials,
                onChanged = viewModel::onSocialChanged,
                modifier = Modifier.padding(horizontal = space.lg),
            )
        }
        item(key = "pricing") {
            PricingSection(
                rows = state.packageRows,
                fallbackPrice = artist.price,
                hydrated = state.packagesHydrated,
                saving = state.savingPackages,
                discountPct = shownNewArtistDiscount(
                    state.newArtistDiscountPct,
                    artist.newArtistDiscountPct,
                ),
                weekendPremiumPct = shownWeekendPremium(
                    state.weekendPremiumPct,
                    artist.weekendPremiumPct,
                ),
                canEditOffer = state.identityHydrated,
                onToggleOffer = viewModel::onNewArtistOfferToggled,
                onStepWeekendPremium = viewModel::onWeekendPremiumStepped,
                onAdd = viewModel::addPackageRow,
                onName = viewModel::onPackageName,
                onDuration = viewModel::onPackageDuration,
                onPrice = viewModel::onPackagePrice,
                onPopular = viewModel::onPackagePopular,
                onRemove = viewModel::removePackageRow,
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

// ── Cover ────────────────────────────────────────────────────────────────────

/**
 * The cover as a labelled, bounded PREVIEW — a picture of the hero rather than
 * the hero itself — with the palette picker under it.
 *
 * This was a full-bleed hero, which is what the artist's PUBLIC page is. Wearing
 * the public page's chrome made the editor read as a live preview an artist edits
 * by poking at it, and the consequence was structural rather than cosmetic: a
 * bled cover has no room for a label, so the section could not say what it was,
 * could not carry an action, and could not sit in the same rhythm as the eight
 * labelled sections below it. Boxed and labelled, it is one section among nine,
 * and the picker has somewhere to live.
 *
 * The scrim is a bottom-darken to black inside the card's own bounds, not the
 * hero's fade into the page background: this card has an edge, so there is no
 * seam to dissolve — the scrim exists only to keep white type legible over an
 * arbitrary photo.
 *
 * The cover image is the first photo in position order, not `artist.coverUrl`.
 * That field comes from the cached artist row, which the reorder write does not
 * invalidate — reading it here would leave the preview showing the previous cover
 * right after the artist promoted a new one.
 */
@Composable
private fun CoverSection(
    artist: Artist,
    coverUrl: String?,
    selectedGradient: Int,
    canEdit: Boolean,
    onPickGradient: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val space = dimens.space
    val shape = RoundedCornerShape(dimens.radii.lg)
    // Render the PICKED palette, not the one the artist row was hydrated with.
    // The write is fire-and-forget against the server; if the preview waited for
    // the round-trip, tapping a swatch would look like it did nothing.
    val palette = ArtistGradient.palette(selectedGradient)

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(space.md)) {
        EpkSectionHeader(title = "Cover")
        Box(
            Modifier
                .fillMaxWidth()
                .height(dimens.size.coverPreview)
                .clip(shape),
        ) {
            // Palette floor first, so a slow or missing photo is never a hole.
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(palette)))
            if (!coverUrl.isNullOrBlank()) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // No size modifier: the scrim applies `matchParentSize` itself, which
            // deliberately keeps it out of the Box's measurement. Passing
            // `fillMaxSize` here would put it back in and let the overlay
            // participate in sizing the fixed-height preview.
            BottomDarkenScrim()
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(space.lg),
                verticalArrangement = Arrangement.spacedBy(space.xs),
            ) {
                if (artist.category.isNotBlank()) {
                    Row { MediaChip(artist.category.uppercase(Locale.US)) }
                }
                Text(
                    artist.name.ifBlank { "Your stage name" },
                    style = AppTheme.type.displaySmall,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                // Genre and city on ONE line, in that order — the two facts a
                // client pairs when they picture the booking, and two separate
                // lines under a name is a stack, not an identity.
                val meta = listOfNotNull(
                    artist.genre.takeIf { it.isNotBlank() },
                    artist.city.takeIf { it.isNotBlank() },
                ).joinToString(" · ")
                if (meta.isNotEmpty()) {
                    Text(meta, style = AppTheme.type.footnote, color = colors.inkOnMedia)
                }
            }
        }
        GradientPicker(
            selectedIndex = selectedGradient,
            enabled = canEdit,
            onPick = onPickGradient,
        )
    }
}

/**
 * The palette row: six little covers, the picked one ringed.
 *
 * Kept visible even once a real photo is set, because it is not dead then — it is
 * what shows while a photo loads, what shows if it fails, and what a client sees
 * on any surface that has not fetched the image yet. Hiding it behind "no photo"
 * would mean the artist can only choose their fallback during the one window
 * where they cannot see it.
 *
 * Landscape swatches rather than dots: the shape is the only thing telling the
 * artist that what they are picking is a cover.
 */
@Composable
private fun GradientPicker(
    selectedIndex: Int,
    enabled: Boolean,
    onPick: (Int) -> Unit,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val shape = RoundedCornerShape(dimens.radii.sm)
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(dimens.space.sm),
    ) {
        repeat(ArtistGradient.count) { index ->
            val isSelected = index == selectedIndex
            Box(
                Modifier
                    .size(dimens.size.swatchW, dimens.size.swatchH)
                    .clip(shape)
                    .background(Brush.linearGradient(ArtistGradient.palette(index)))
                    .border(
                        dimens.size.stroke,
                        // Ring the picked one in the role accent; everything else
                        // gets the quiet rule, so the row reads as one choice made
                        // rather than six things outlined.
                        if (isSelected) colors.brand else colors.lineSoft,
                        shape,
                    )
                    .clickable(enabled = enabled) { onPick(index) }
                    .semantics {
                        contentDescription = "Cover palette ${index + 1}"
                        if (isSelected) selected = true
                    },
            )
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
    onRetry: () -> Unit,
    onDismissSaveError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val completeness = epkCompleteness(
        photoCount = state.photos.size,
        // The DRAFT, matching how the tier count reads the draft rows: the
        // checklist is feedback on the edit in progress, so a bio that has been
        // typed but not yet debounced still counts as written.
        bio = state.bioDraft,
        packageCount = state.packageRows.count(::packageRowIsSavable),
        sampleCount = state.samples.size,
        techCount = state.techItems.size,
        // The DRAFT, for the same reason the bio above reads its draft: the
        // checklist is feedback on the edit in progress, so an account pasted a
        // second ago should tick immediately rather than after the debounce.
        socialCount = state.socialDraft.linkedCount,
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
/**
 * The bio, as an actual field.
 *
 * This section used to RENDER the bio and offer no way to change it, which made
 * the editor's most-read section its only unwritable one — the empty state said
 * clients read this first, and then gave the artist nowhere to write it. The
 * narrow single-column write existed in the data layer with no caller; this is
 * the caller.
 *
 * Autosaves on a debounce like the rest of the editor, so there is no Save
 * button to leave half-pressed. Read-only until the artist row has been read,
 * matching every other write on this screen: the row has to be seen before it
 * can be written.
 */
@Composable
private fun AboutSection(
    bio: String,
    canEdit: Boolean,
    onBioChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(space.md)) {
        EpkSectionHeader(title = "About")
        EpkField(
            value = bio,
            onValueChange = onBioChanged,
            placeholder = "Clients read this before anything else on your profile.",
            enabled = canEdit,
            singleLine = false,
            minLines = BIO_FIELD_MIN_LINES,
            contentDescription = "Bio",
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "A line or two on what a client is booking.",
                style = AppTheme.type.caption,
                color = colors.ink3,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${bio.length} / $MAX_BIO_CHARS",
                style = AppTheme.type.monoMicro,
                // Only loud at the wall, because that is the only moment the count
                // explains something the artist can otherwise only experience as
                // keystrokes going missing.
                color = if (bioIsAtCap(bio.length)) colors.warm else colors.ink3,
            )
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
    /**
     * Both modifiers live on the artist ROW, not in the packages table, so they
     * share the row's gate ([canEditOffer]) rather than the tiers' hydration flag.
     */
    discountPct: Int,
    weekendPremiumPct: Int,
    canEditOffer: Boolean,
    onToggleOffer: () -> Unit,
    onStepWeekendPremium: () -> Unit,
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
        // The two price modifiers, adjacent and identically shaped. They are the
        // only two things on this screen that change what a client pays relative
        // to the tiers above, so they read as a pair rather than as two unrelated
        // switches that happen to live in the pricing section.
        NewArtistOfferRow(
            pct = discountPct,
            enabled = canEditOffer,
            onToggle = onToggleOffer,
        )
        WeekendPremiumRow(
            pct = weekendPremiumPct,
            enabled = canEditOffer,
            onStep = onStepWeekendPremium,
        )
    }
}

/**
 * The public new-artist discount, as a switch.
 *
 * This column was rendered on the artist's own public profile — "New-artist
 * offer: N% off your booking" — by a reader with no writer anywhere in the
 * Android app, wizard included. An artist whose row carried it (set by another
 * client on the shared backend) was advertising a discount they had no way to
 * withdraw. So the control here is not a new feature so much as the missing half
 * of one that was already live in front of clients.
 *
 * The sub-line says who honours it, because the app does not: there is no
 * payments path in v1, so this is a promise the artist keeps in their own quote.
 * A discount control that looked automatic would have artists discovering at
 * quote time that the number was theirs to absorb.
 */
/**
 * "What you offer" — the curated service chips.
 *
 * **This section is what makes Discover's services filter mean anything.** The
 * filter has been sending these exact slugs to the server for a while, but no
 * screen in this app could ever put one in the column, so ticking "Wedding /
 * sangeet" as a client narrowed the results to whichever artists had set their
 * tags on the other client. An artist can now answer the question the filter
 * asks.
 *
 * The sub-line says so plainly rather than describing the chips, because the
 * reason to spend thirty seconds here is not "a nicer profile" — it is being
 * findable by clients who filter for exactly what you do.
 *
 * Chips, not a text field: the write vocabulary has to match the filter's
 * vocabulary exactly (an overlap match has no fuzziness), and free text is how an
 * artist types "DJ" and disappears from every search for "DJ set".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ServicesSection(
    selected: List<String>,
    canEdit: Boolean,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(space.md)) {
        EpkSectionHeader(
            title = "What you offer",
            trailingNote = if (selected.isEmpty()) null else "${selected.size}/${ServiceTags.MAX_TAGS}",
        )
        Text(
            "Clients filter by these. Pick the sets you actually play.",
            style = AppTheme.type.footnote,
            color = colors.ink3,
        )
        if (!canEdit) {
            Text(
                "Couldn't read your profile, so editing is off — pull to refresh to try again.",
                style = AppTheme.type.footnote,
                color = colors.warm,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(space.sm),
            verticalArrangement = Arrangement.spacedBy(space.sm),
        ) {
            ServiceTags.catalog.forEach { (slug, label) ->
                EpkChip(
                    label = label,
                    selected = slug in selected,
                    enabled = canEdit,
                    onClick = { onToggle(slug) },
                )
            }
            // Anything stored that this build's taxonomy does not know — another
            // client's or an admin backfill's tag. Shown selected and tappable so
            // the artist can see and withdraw a claim their profile is making,
            // rather than having it silently absent from the editor that is
            // supposed to show their whole profile.
            selected.filterNot { it in ServiceTags.slugs }.forEach { slug ->
                EpkChip(
                    label = ServiceTags.label(slug),
                    selected = true,
                    enabled = canEdit,
                    onClick = { onToggle(slug) },
                )
            }
        }
    }
}

@Composable
private fun NewArtistOfferRow(
    pct: Int,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Column(verticalArrangement = Arrangement.spacedBy(space.sm)) {
        HRule()
        Row(
            Modifier.fillMaxWidth().padding(top = space.sm),
            horizontalArrangement = Arrangement.spacedBy(space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("New-artist offer", style = AppTheme.type.callout, color = colors.ink)
                Text(
                    "Shown on your profile. You honour it in your quote.",
                    style = AppTheme.type.caption,
                    color = colors.ink3,
                )
            }
            EpkChip(
                label = newArtistDiscountLabel(pct),
                selected = pct > 0,
                enabled = enabled,
                onClick = onToggle,
            )
        }
    }
}

/**
 * The Fri–Sun surcharge, built to match [NewArtistOfferRow] row-for-row.
 *
 * Same anatomy, same chip, same sub-line construction — because a client sees
 * both applied to the same quote, and two differently-shaped controls would imply
 * one is automatic and the other is not. Neither is: v1 has no payments path, so
 * both are promises the artist keeps when they quote.
 *
 * The chip steps rather than toggles (see `weekendPremiumStepTarget`), so its
 * label always states the current number — a stepper whose label did not change
 * would look like a switch that failed to flip.
 */
@Composable
private fun WeekendPremiumRow(
    pct: Int,
    enabled: Boolean,
    onStep: () -> Unit,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Column(verticalArrangement = Arrangement.spacedBy(space.sm)) {
        HRule()
        Row(
            Modifier.fillMaxWidth().padding(top = space.sm),
            horizontalArrangement = Arrangement.spacedBy(space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Weekend premium", style = AppTheme.type.callout, color = colors.ink)
                Text(
                    "Shown on your profile. You apply it in your quote.",
                    style = AppTheme.type.caption,
                    color = colors.ink3,
                )
            }
            EpkChip(
                label = weekendPremiumLabel(pct),
                selected = pct > 0,
                enabled = enabled,
                onClick = onStep,
            )
        }
    }
}

/**
 * The personality deck — four fixed questions, optional answers.
 *
 * Fixed questions rather than artist-authored ones because the question string is
 * the prompt's identity on the wire: an artist who reworded a question here would
 * orphan the answer they wrote on the other client, and the profile would show
 * both. The deck matches the reference client's exactly for that reason.
 *
 * Unanswered prompts are still rendered — that is what invites an answer — but
 * only answered ones persist, so leaving all four blank stores an empty array
 * rather than four empty rows.
 */
@Composable
private fun PromptsSection(
    drafts: List<ArtistPrompt>,
    canEdit: Boolean,
    saving: Boolean,
    onAnswer: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(space.md)) {
        EpkSectionHeader(
            // "Personality", not "In your words" — the deck already matches the
            // reference question-for-question, and the header was the last piece
            // of it still saying something different.
            title = "Personality",
            trailingNote = if (saving) "Saving…" else null,
        )
        Text(
            "Optional. Answer what you like — clients read these before they message.",
            style = AppTheme.type.footnote,
            color = colors.ink3,
        )
        if (!canEdit) {
            Text(
                "Couldn't read your profile, so editing is off — pull to refresh to try again.",
                style = AppTheme.type.footnote,
                color = colors.warm,
            )
        }
        Column {
            HRule()
            ArtistPrompts.questions.forEach { question ->
                Column(
                    Modifier.fillMaxWidth().padding(vertical = space.sm),
                    verticalArrangement = Arrangement.spacedBy(space.xs),
                ) {
                    Text(question, style = AppTheme.type.footnote, color = colors.ink3)
                    EpkField(
                        value = ArtistPrompts.answerFor(drafts, question),
                        onValueChange = { onAnswer(question, it) },
                        placeholder = "Your answer",
                        enabled = canEdit,
                        singleLine = false,
                        minLines = 2,
                        textStyle = AppTheme.type.body,
                        contentDescription = question,
                    )
                }
                HRule()
            }
        }
    }
}

@Composable
private fun PackageEditorRow(
    row: PackageRow,
    enabled: Boolean,
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
        // Why this row is not going anywhere. The save silently omits rows that
        // are missing a name or a price, so without this the artist watches a
        // tier they typed disappear on the next refresh and has no way to learn
        // which half was missing.
        packageRowBlocker(row)?.let { blocker ->
            Text(
                blocker,
                style = AppTheme.type.caption,
                // Loud only once there is something to lose. A row that has just
                // been added is blank by definition, and warning about it on the
                // frame it appears blames the artist for pressing Add.
                color = if (packageRowIsPartiallyFilled(row)) colors.warm else colors.ink3,
            )
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
 * The three accounts, as three fields.
 *
 * These were read-only rows showing "Linked / Not linked", because the only
 * writer for these columns was the wizard's whole-row upsert. The narrow write
 * exists now, so the row that reported a state becomes the field that sets it —
 * and the "Linked" chip goes away with it, since a field showing the value has
 * already answered the question the chip was there to answer.
 *
 * **Editing is off until the artist row has been read**, and this is the section
 * where that matters most: the write sends all three columns at once, so a save
 * from an un-hydrated screen would not "fail to update one" — it would unlink
 * all three. The gate is enforced in the ViewModel too; disabling here is what
 * makes the reason visible instead of leaving the artist typing into a field
 * that silently declines to save.
 *
 * Helper copy per platform is carried over from the wizard's socials step
 * verbatim. It is not decoration: the Spotify artist URL in particular is buried
 * in Spotify for Artists rather than the normal share sheet, and artists
 * reliably paste their personal profile URL instead.
 */
@Composable
private fun SocialSection(
    draft: SocialDraft,
    canEdit: Boolean,
    saving: Boolean,
    onChanged: (SocialPlatform, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(space.md)) {
        EpkSectionHeader(
            title = "Connected accounts",
            trailingNote = if (saving) "Saving…" else null,
        )
        if (!canEdit) {
            Text(
                "Couldn't read your profile, so editing is off — pull to refresh to try again.",
                style = AppTheme.type.footnote,
                color = colors.warm,
            )
        }
        // Spotify first: it is the one a client is most likely to open before
        // deciding, and the order matches the public profile's.
        SocialField(
            platform = SocialPlatform.Spotify,
            label = "Spotify",
            value = draft.spotify,
            placeholder = "open.spotify.com/artist/…",
            helper = "From Spotify for Artists → Profile → Share.",
            keyboardType = KeyboardType.Uri,
            enabled = canEdit,
            onChanged = onChanged,
        )
        SocialField(
            platform = SocialPlatform.Instagram,
            label = "Instagram",
            value = draft.instagram,
            placeholder = "@yourhandle",
            // Text, not Uri: this field takes a handle, and a Uri keyboard puts
            // "/" and ".com" where the artist wants letters.
            keyboardType = KeyboardType.Text,
            helper = "We deep-link clients straight into the Instagram app.",
            enabled = canEdit,
            onChanged = onChanged,
        )
        SocialField(
            platform = SocialPlatform.YouTube,
            label = "YouTube",
            value = draft.youtube,
            placeholder = "youtube.com/@yourchannel",
            helper = "Channel URL — handle URLs (with @) work too.",
            keyboardType = KeyboardType.Uri,
            enabled = canEdit,
            onChanged = onChanged,
        )
        Text(
            // Says how to unlink, because there is no delete affordance and an
            // artist who wants one off will otherwise go looking for a button
            // that does not exist.
            "Clear a field to unlink that account.",
            style = AppTheme.type.caption,
            color = colors.ink3,
        )
    }
}

@Composable
private fun SocialField(
    platform: SocialPlatform,
    label: String,
    value: String,
    placeholder: String,
    helper: String,
    keyboardType: KeyboardType,
    enabled: Boolean,
    onChanged: (SocialPlatform, String) -> Unit,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(space.xs)) {
        Text(label, style = AppTheme.type.callout, color = colors.ink)
        EpkField(
            value = value,
            onValueChange = { onChanged(platform, it) },
            placeholder = placeholder,
            enabled = enabled,
            textStyle = AppTheme.type.footnote,
            keyboardType = keyboardType,
            contentDescription = "$label link",
        )
        Text(helper, style = AppTheme.type.caption, color = colors.ink3)
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
