package `in`.artistant.app.feature.epk

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
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
import coil3.compose.AsyncImage
import `in`.artistant.app.common.util.formatInr
import `in`.artistant.app.data.model.Sample
import `in`.artistant.app.data.repository.ArtistLink
import `in`.artistant.app.data.repository.ArtistMediaItem
import `in`.artistant.app.designsystem.component.BackHeader
import `in`.artistant.app.designsystem.component.HRule
import `in`.artistant.app.designsystem.component.SampleRow
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.ArtistGradient
import `in`.artistant.app.domain.artist.PackagePricing
import `in`.artistant.app.domain.sample.SamplePlayback
import `in`.artistant.app.platform.media.SamplePlayerHandle
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * The press kit's section editors — the screens behind a row on the hub.
 *
 * **They are panes, not destinations.** `EpkScreen`'s KDoc says why: every one of
 * them is a view of the same ViewModel, the same debounced write queue and the
 * same upload queue, so a nav entry per section would buy a back gesture
 * [androidx.activity.compose.BackHandler] already gives us in exchange for a
 * second Hilt scope or `hiltViewModel(parentEntry)` plumbing.
 *
 * The section bodies below are the pre-redesign editor's, moved rather than
 * rewritten. That is deliberate: each one already carries a wipe guard, a
 * hydration gate and a read-only state that took several rounds to get right
 * (see their own KDoc), and none of that is what the redesign changed. What
 * changed is where they live and how the artist gets to them.
 *
 * Their light-palette pass is the design system's — `EpkField`, `EpkChip` and
 * `EpkSectionHeader` in `EpkComponents.kt` read the same tokens every other
 * screen does, so these came across the redesign without a per-widget restyle.
 */

/**
 * One section editor, and its title in the back bar.
 *
 * `Gallery` covers cover AND gallery because they are one `artist_media` list in
 * position order — the cover is simply position 0, which is why the reorder
 * control's first action is "Make cover". Splitting them into two panes would
 * mean two views of one list that can each invalidate the other.
 */
enum class EpkPane(val title: String) {
    Gallery("Cover and gallery"),
    Samples("Audio samples"),
    Packages("Packages and pricing"),
    Tech("Tech rider"),
    Links("Links and socials"),
}

/**
 * The pane frame: a centred back bar, then the section, scrolling.
 *
 * A `LazyColumn` of one item rather than a `verticalScroll(Column)` so the
 * keyboard inset and the tail padding behave the way they do on the hub — and so
 * a pane that grows a second block later does not have to change container.
 */
@Composable
internal fun EpkSectionPane(
    pane: EpkPane,
    state: EpkUiState,
    viewModel: EpkViewModel,
    player: SamplePlayerHandle,
    onBack: () -> Unit,
    onAddPhoto: () -> Unit,
    onAddSample: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = AppTheme.dimens
    val gutter = Modifier.padding(horizontal = dimens.component.gutter)
    Column(modifier.fillMaxSize()) {
        BackHeader(
            title = pane.title,
            onBack = onBack,
            modifier = gutter.padding(vertical = dimens.space.sm),
        )
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = dimens.space.lg,
                bottom = dimens.hero.scrollTailroom,
            ),
            verticalArrangement = Arrangement.spacedBy(dimens.space.xl),
        ) {
            when (pane) {
                EpkPane.Gallery -> {
                    item(key = "photos") {
                        PhotosSection(
                            photos = state.photos,
                            uploading = state.uploadingPhoto,
                            canReorder = state.photosHydrated,
                            onAdd = onAddPhoto,
                            onDelete = viewModel::deletePhoto,
                            onMove = viewModel::movePhoto,
                            modifier = gutter,
                        )
                    }
                    item(key = "palette") {
                        Column(gutter) {
                            GradientPicker(
                                selectedIndex = shownCoverGradient(
                                    state.coverGradientIndex,
                                    state.artist?.coverGradientIndex ?: 0,
                                ),
                                enabled = state.identityHydrated,
                                onPick = viewModel::onCoverGradientPicked,
                            )
                        }
                    }
                }

                EpkPane.Samples -> item(key = "samples") {
                    SamplesSection(
                        samples = state.samples,
                        // Clips the queue is still carrying, plus the one still
                        // being copied into the cache. Neither has a `samples` row
                        // yet but both are about to, so the cap counts them.
                        uploading = state.samplesUploading + state.samplesStaging,
                        // The State, not its value: read inside the section so a
                        // position tick five times a second recomposes one row
                        // group rather than the whole pane.
                        playback = player.playback,
                        onPlay = player::onTap,
                        onAdd = onAddSample,
                        onDelete = viewModel::deleteSample,
                        modifier = gutter,
                    )
                }

                EpkPane.Packages -> item(key = "pricing") {
                    PricingSection(
                        rows = state.packageRows,
                        fallbackPrice = state.artist?.price ?: 0,
                        hydrated = state.packagesHydrated,
                        saving = state.savingPackages,
                        discountPct = shownNewArtistDiscount(
                            state.newArtistDiscountPct,
                            state.artist?.newArtistDiscountPct ?: 0,
                        ),
                        weekendPremiumPct = shownWeekendPremium(
                            state.weekendPremiumPct,
                            state.artist?.weekendPremiumPct ?: 0,
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
                        modifier = gutter,
                    )
                }

                EpkPane.Tech -> item(key = "tech") {
                    TechSection(
                        items = state.techItems,
                        draft = state.techDraft,
                        hydrated = state.techHydrated,
                        saving = state.savingTech,
                        onToggle = viewModel::toggleTechPreset,
                        onDraft = viewModel::onTechDraft,
                        onAddDraft = viewModel::addTechDraft,
                        modifier = gutter,
                    )
                }

                EpkPane.Links -> {
                    item(key = "links") {
                        LinksSection(
                            links = state.links,
                            onAdd = { viewModel.openLinkEditor(null) },
                            onEdit = viewModel::openLinkEditor,
                            modifier = gutter,
                        )
                    }
                    item(key = "socials") {
                        SocialSection(
                            draft = state.socialDraft,
                            canEdit = state.identityHydrated,
                            saving = state.savingSocials,
                            onChanged = viewModel::onSocialChanged,
                            modifier = gutter,
                        )
                    }
                }
            }
        }
    }
}

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
internal fun PhotosSection(
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
internal fun PhotoCell(
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
                // The brand rim is the only thing saying which photo the action
                // row underneath belongs to, and a rim does not read aloud.
                this.selected = selected
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
internal fun PhotoActionRow(
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
internal fun GradientPicker(
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

@Composable
internal fun SamplesSection(
    samples: List<Sample>,
    uploading: Int,
    playback: State<SamplePlayback>,
    onPlay: (Sample) -> Unit,
    onAdd: () -> Unit,
    onDelete: (Sample) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    // Stored rows plus the ones still on their way up. The counter reads the same
    // number the Add affordance is gated on, so "6/6" and a disabled + Add tell
    // one story instead of leaving a dead button beside a count that says 5.
    val staged = samples.size + uploading
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(space.md)) {
        EpkSectionHeader(
            title = "Music samples",
            actionLabel = "+ Add",
            onAction = onAdd,
            actionEnabled = canAddSample(stored = samples.size, uploading = uploading),
            trailingNote = if (staged == 0) null else "$staged/$MAX_SAMPLES",
        )
        if (samples.isEmpty()) {
            Text(
                "Add a few clips so clients can hear you — anything 30s to 2 min works.",
                style = AppTheme.type.footnote,
                color = colors.ink3,
            )
        } else {
            // The artist gets the same playable row a client sees on the public
            // profile, so "what does this sound like on my page" is answered here
            // rather than by publishing and looking. The player itself belongs to
            // the screen, not to this item — see [EpkEditor].
            val current by playback
            Column {
                HRule()
                samples.forEach { sample ->
                    SampleRow(
                        sample = sample,
                        playback = current,
                        onTap = { onPlay(sample) },
                        trailing = { EpkRowAction("Remove", { onDelete(sample) }, tone = colors.hot) },
                    )
                    HRule()
                }
            }
        }
    }
}

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
internal fun PricingSection(
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

@Composable
internal fun PackageEditorRow(
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

@Composable
internal fun NewArtistOfferRow(
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
internal fun WeekendPremiumRow(
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
 * Presets first, then whatever the artist added themselves, then a field to add
 * more. One flow of chips rather than a list with checkboxes: a rider is a set,
 * and a set reads faster as chips than as rows.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TechSection(
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

@Composable
internal fun LinksSection(
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
                        Text("Edit", style = AppTheme.type.footnote, color = colors.accentInk)
                    }
                    HRule()
                }
            }
        }
    }
}

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
internal fun SocialSection(
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
internal fun SocialField(
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

@Composable
internal fun ShareLinkSection(handle: String, modifier: Modifier = Modifier) {
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
                        color = colors.accentInk,
                        modifier = Modifier
                            // A 9sp word is a 30dp target; the label keeps its size
                            // and the tap node grows to the floor around it.
                            .heightIn(min = AppTheme.dimens.size.rowMin)
                            .clickable {
                                clipboard.setText(AnnotatedString(url))
                                copied = true
                            }
                            .wrapContentHeight()
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
private const val COPIED_RESET_MS = 1_400L

/** Tier row split: the name takes two thirds, the price the remaining third. */
private const val NAME_CELL_WEIGHT = 2f
private const val PRICE_CELL_WEIGHT = 1f
