package `in`.artistant.app.feature.epk

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import `in`.artistant.app.data.model.ArtistGradient
import `in`.artistant.app.data.model.ArtistLink
import `in`.artistant.app.data.model.ArtistMediaItem
import `in`.artistant.app.data.model.SampleRow
import `in`.artistant.app.designsystem.component.HRule
import `in`.artistant.app.designsystem.component.UploadProgressBanner
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.feature.wizard.PricingTier
import `in`.artistant.app.feature.wizard.WizardConstants
import `in`.artistant.app.feature.wizard.rememberAudioPicker
import `in`.artistant.app.feature.wizard.rememberImageLibraryPicker

/**
 * The artist's EPK editor (port of iOS `EPKView`). A single scrollable editor: cover +
 * gradient, photos, samples, socials, read-only bio, pricing (debounced), tech rider,
 * custom links, and the share-link card. Every section persists on its own; the VM
 * surfaces any failure through the top error banner. Glass is approximated with the flat
 * dark tokens (no live blur) per the M5 Android spec.
 */
@Composable
fun EpkScreen(viewModel: EpkViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uploadBanner by viewModel.uploadBanner.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space

    // The link sheet target: null = closed, a link = edit, ADD_SENTINEL = new.
    var linkEdit by remember { mutableStateOf<LinkEditTarget?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = space.xl)
            .padding(top = space.lg, bottom = space.xxl),
        verticalArrangement = Arrangement.spacedBy(space.xl),
    ) {
        Text("Profile", style = AppTheme.type.displayMedium, color = colors.ink)
        Text("Your booking-ready profile", style = AppTheme.type.footnote, color = colors.ink3)

        state.error?.let { ErrorBanner(it, onDismiss = viewModel::dismissError) }
        // Photos drain here; the banner's "retry all" just prunes the finished batch (the
        // real re-add surface is the photo picker above), matching the queue's own contract.
        UploadProgressBanner(state = uploadBanner, onRetryAll = viewModel::clearFinishedUploads)

        CoverSection(state, viewModel)
        BioSection(state)
        PhotosSection(state, viewModel)
        SamplesSection(state, viewModel)
        SocialsSection(state, viewModel)
        PricingSection(state, viewModel)
        TechSection(state, viewModel)
        LinksSection(state, onAdd = { linkEdit = LinkEditTarget.New }, onEdit = { linkEdit = LinkEditTarget.Edit(it) })
        ShareLinkCard(state.shareUrl)
    }

    when (val target = linkEdit) {
        LinkEditTarget.New -> EditArtistLinkSheet(
            onSave = { label, url -> viewModel.addLink(label, url) },
            onDismiss = { linkEdit = null },
        )
        is LinkEditTarget.Edit -> EditArtistLinkSheet(
            initialLabel = target.link.label,
            initialUrl = target.link.url,
            isExisting = true,
            onSave = { label, url -> viewModel.updateLink(target.link, label, url) },
            onDelete = { viewModel.deleteLink(target.link) },
            onDismiss = { linkEdit = null },
        )
        null -> Unit
    }
}

/** Link sheet routing target (iOS `LinkEditTarget`). */
private sealed interface LinkEditTarget {
    data object New : LinkEditTarget
    data class Edit(val link: ArtistLink) : LinkEditTarget
}

// MARK: - Error banner

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppTheme.dimens.radii.md))
            .background(colors.hot.copy(alpha = 0.12f))
            .border(1.dp, colors.hot.copy(alpha = 0.25f), RoundedCornerShape(AppTheme.dimens.radii.md))
            .padding(space.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space.sm),
    ) {
        Text(message, style = AppTheme.type.footnote, color = colors.ink, modifier = Modifier.weight(1f))
        Icon(
            Icons.Filled.Close,
            contentDescription = "Dismiss",
            tint = colors.ink3,
            modifier = Modifier.size(AppTheme.dimens.size.iconMd).clickable { onDismiss() },
        )
    }
}

// MARK: - Cover

@Composable
private fun CoverSection(state: EpkUiState, vm: EpkViewModel) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    // Reuse the M5b library picker to replace the cover photo (enqueues via UploadQueue;
    // position-0 photo IS the cover, so a fresh artist's first pick becomes the cover).
    val pickCover = rememberImageLibraryPicker(vm::addPhoto)

    Column(verticalArrangement = Arrangement.spacedBy(space.md)) {
        SectionTitle("Cover")
        Box(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(AppTheme.dimens.radii.md))
                .background(Brush.linearGradient(ArtistGradient.palette(state.coverGradientIndex))),
        ) {
            // Priority photo > gradient. A cover VIDEO shows a chip (live playback lives on
            // the published profile; an ExoPlayer preview isn't worth it in the editor).
            // ponytail: video-replace stays in the wizard (needs VideoTrimmer + a Context).
            state.coverPhoto?.publicUrl?.let { url ->
                AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))),
                ),
            )
            Column(Modifier.align(Alignment.BottomStart).padding(space.lg)) {
                if (state.coverVideo != null) {
                    Text(
                        "VIDEO SET",
                        style = AppTheme.type.caption.copy(fontWeight = FontWeight.Black),
                        color = Color.White,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.4f))
                            .padding(horizontal = space.sm, vertical = space.xs),
                    )
                    Spacer(Modifier.height(4.dp))
                }
                if (state.category.isNotBlank()) {
                    Text(state.category.uppercase(), style = AppTheme.type.caption.copy(fontWeight = FontWeight.Black), color = Color.White)
                }
                Text(state.stageName.ifBlank { "Your stage name" }, style = AppTheme.type.displaySmall, color = Color.White)
                val cityLine = listOfNotNull(state.genre?.ifBlank { null }, state.baseCity.ifBlank { null }).joinToString(" · ")
                if (cityLine.isNotEmpty()) {
                    Text(cityLine, style = AppTheme.type.footnote.copy(fontWeight = FontWeight.SemiBold), color = Color.White.copy(alpha = 0.85f))
                }
            }
        }

        // Replace-cover-photo affordance + the 6-preset gradient grid (persists on tap).
        Text(
            if (state.coverPhoto == null) "Add a cover photo" else "Replace cover photo",
            style = AppTheme.type.caption.copy(fontWeight = FontWeight.Bold),
            color = colors.brand,
            modifier = Modifier.clickable { pickCover() },
        )
        GradientGrid(selected = state.coverGradientIndex, onPick = vm::setGradient)
    }
}

/** Six-preset gradient picker — a plain 2×3 grid (avoids a nested LazyGrid in the scroll). */
@Composable
private fun GradientGrid(selected: Int, onPick: (Int) -> Unit) {
    val colors = AppTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0..2, 3..5).forEach { range ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                range.forEach { idx ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Brush.linearGradient(ArtistGradient.palette(idx)))
                            .border(2.dp, if (idx == selected) colors.brand else colors.lineSoft, RoundedCornerShape(8.dp))
                            .clickable { onPick(idx) },
                    )
                }
            }
        }
    }
}

// MARK: - Bio (read-only, matches iOS)

@Composable
private fun BioSection(state: EpkUiState) {
    val colors = AppTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.dimens.space.md)) {
        SectionTitle("About")
        if (state.bio.isBlank()) {
            Text(
                "Add a short bio to help clients book you. Re-run the wizard's bio step to add one.",
                style = AppTheme.type.footnote,
                color = colors.ink3,
            )
        } else {
            Text(state.bio, style = AppTheme.type.body, color = colors.ink)
        }
    }
}

// MARK: - Photos (3-col grid)

@Composable
private fun PhotosSection(state: EpkUiState, vm: EpkViewModel) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val pickPhoto = rememberImageLibraryPicker(vm::addPhoto)
    // The whole grid = cover (position 0) + gallery, so the cover reads as part of the feed.
    val cells = listOfNotNull(state.coverPhoto) + state.galleryPhotos

    Column(verticalArrangement = Arrangement.spacedBy(space.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("Photos", Modifier.weight(1f))
            // Cap surfaced as a count; Add is hidden at the cap (delete to make room).
            Text("${state.photoCount} / 6", style = AppTheme.type.caption.copy(fontWeight = FontWeight.Bold), color = colors.ink3)
        }
        if (cells.isEmpty()) {
            Text("No photos yet. Add a few so clients can see you play.", style = AppTheme.type.footnote, color = colors.ink3)
        }
        // Non-lazy 3-col grid (<=6 photos → at most 2 rows; avoids a nested LazyGrid).
        (cells + if (state.atPhotoCap) emptyList() else listOf<ArtistMediaItem?>(null)).chunked(3).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                rowItems.forEach { item ->
                    Box(Modifier.weight(1f)) {
                        if (item == null) AddPhotoCell(onClick = pickPhoto)
                        else PhotoCell(item, isCover = item.id == state.coverPhoto?.id, onDelete = { vm.deletePhoto(item) })
                    }
                }
                // Pad the last row so cells stay square-aligned.
                repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun PhotoCell(item: ArtistMediaItem, isCover: Boolean, onDelete: () -> Unit) {
    val colors = AppTheme.colors
    Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(6.dp)).background(colors.bgCard)) {
        AsyncImage(model = item.publicUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        if (isCover) {
            Text(
                "COVER",
                style = AppTheme.type.caption.copy(fontWeight = FontWeight.Black),
                color = colors.brandInk,
                modifier = Modifier.align(Alignment.TopStart).padding(6.dp).clip(CircleShape).background(colors.brand)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        Icon(
            Icons.Filled.Close,
            contentDescription = "Delete photo",
            tint = Color.White,
            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f))
                .padding(4.dp).size(AppTheme.dimens.size.iconMd).clickable { onDelete() },
        )
    }
}

@Composable
private fun AddPhotoCell(onClick: () -> Unit) {
    val colors = AppTheme.colors
    Box(
        Modifier.aspectRatio(1f).clip(RoundedCornerShape(6.dp)).border(1.dp, colors.line, RoundedCornerShape(6.dp)).clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Add, contentDescription = "Add photo", tint = colors.ink3, modifier = Modifier.size(AppTheme.dimens.size.iconXl))
    }
}

// MARK: - Samples

@Composable
private fun SamplesSection(state: EpkUiState, vm: EpkViewModel) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val pickAudio = rememberAudioPicker { uri, name -> vm.addSample(uri, name) }

    Column(verticalArrangement = Arrangement.spacedBy(space.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("Music samples", Modifier.weight(1f))
            when {
                state.uploadingSample -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = colors.brand)
                !state.atSampleCap -> Text(
                    "+ Add",
                    style = AppTheme.type.footnote.copy(fontWeight = FontWeight.Bold),
                    color = colors.brand,
                    modifier = Modifier.clickable { pickAudio() },
                )
            }
        }
        if (state.samples.isEmpty() && !state.uploadingSample) {
            Text("Add a few audio clips — anything 30s–2 min works.", style = AppTheme.type.footnote, color = colors.ink3)
        } else {
            state.samples.forEachIndexed { i, s ->
                if (i == 0) HRule()
                SampleRowView(s, onDelete = { vm.deleteSample(s) }, deleteEnabled = !state.uploadingSample)
                HRule()
            }
        }
    }
}

@Composable
private fun SampleRowView(s: SampleRow, onDelete: () -> Unit, deleteEnabled: Boolean) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Row(
        Modifier.fillMaxWidth().padding(vertical = space.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space.md),
    ) {
        Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = colors.brand, modifier = Modifier.size(AppTheme.dimens.size.iconLg))
        Column(Modifier.weight(1f)) {
            Text(s.title, style = AppTheme.type.callout.copy(fontWeight = FontWeight.SemiBold), color = colors.ink, maxLines = 1)
            Text(s.durationLabel, style = AppTheme.type.monoSmall, color = colors.ink3)
        }
        Icon(
            Icons.Filled.Delete,
            contentDescription = "Delete ${s.title}",
            tint = colors.ink4,
            modifier = Modifier.size(AppTheme.dimens.size.iconLg)
                .then(if (deleteEnabled) Modifier.clickable { onDelete() } else Modifier),
        )
    }
}

// MARK: - Socials

@Composable
private fun SocialsSection(state: EpkUiState, vm: EpkViewModel) {
    val space = AppTheme.dimens.space
    Column(verticalArrangement = Arrangement.spacedBy(space.md)) {
        SectionTitle("Connected accounts")
        SoftLabeledField("Spotify", state.spotify, vm::setSpotify, "open.spotify.com/artist/…", KeyboardType.Uri)
        SoftLabeledField("Instagram", state.instagram, vm::setInstagram, "@yourhandle", KeyboardType.Text)
        SoftLabeledField("YouTube", state.youtube, vm::setYoutube, "youtube.com/@yourchannel", KeyboardType.Uri)
    }
}

@Composable
private fun SoftLabeledField(label: String, value: String, onChange: (String) -> Unit, placeholder: String, keyboardType: KeyboardType) {
    val colors = AppTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.dimens.space.xs)) {
        Text(label.uppercase(), style = AppTheme.type.caption.copy(fontWeight = FontWeight.Bold), color = colors.ink3)
        TextField(
            value = value,
            onValueChange = onChange,
            placeholder = { Text(placeholder, color = colors.ink4) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            textStyle = LocalTextStyle.current.copy(color = colors.ink),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = colors.bgSoft,
                unfocusedContainerColor = colors.bgSoft,
                cursorColor = colors.brand,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// MARK: - Pricing (debounced saves in the VM)

@Composable
private fun PricingSection(state: EpkUiState, vm: EpkViewModel) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Column(verticalArrangement = Arrangement.spacedBy(space.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("Pricing tiers", Modifier.weight(1f))
            Text("+ Add", style = AppTheme.type.footnote.copy(fontWeight = FontWeight.Bold), color = colors.brand, modifier = Modifier.clickable { vm.addTier() })
        }
        state.packages.forEach { tier ->
            TierEditor(tier, onChange = vm::updateTier, onRemove = { vm.removeTier(tier.id) })
        }
    }
}

@Composable
private fun TierEditor(tier: PricingTier, onChange: (PricingTier) -> Unit, onRemove: () -> Unit) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.bgCard).padding(space.lg),
        verticalArrangement = Arrangement.spacedBy(space.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BareField(tier.name, { onChange(tier.copy(name = it)) }, "Tier name", Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("₹", style = AppTheme.type.body, color = colors.ink3)
                BareField(
                    if (tier.price == 0) "" else tier.price.toString(),
                    { raw -> onChange(tier.copy(price = raw.filter { it.isDigit() }.toIntOrNull() ?: 0)) },
                    "20000",
                    Modifier.width(90.dp),
                    keyboardType = KeyboardType.Number,
                )
            }
            Icon(Icons.Filled.Delete, contentDescription = "Remove tier", tint = colors.ink4, modifier = Modifier.size(AppTheme.dimens.size.iconLg).clickable { onRemove() })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            BareField(tier.duration, { onChange(tier.copy(duration = it)) }, "Duration", Modifier.weight(1f))
            Text("Popular", style = AppTheme.type.caption.copy(fontWeight = FontWeight.SemiBold), color = colors.ink3)
            Switch(
                checked = tier.popular,
                onCheckedChange = { onChange(tier.copy(popular = it)) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.brandInk,
                    checkedTrackColor = colors.brand,
                    uncheckedThumbColor = colors.ink3,
                    uncheckedTrackColor = colors.bgSoft,
                ),
            )
        }
    }
}

@Composable
private fun BareField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, keyboardType: KeyboardType = KeyboardType.Text) {
    val colors = AppTheme.colors
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = colors.ink4) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        textStyle = LocalTextStyle.current.copy(color = colors.ink, fontWeight = FontWeight.SemiBold),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            cursorColor = colors.brand,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = modifier,
    )
}

// MARK: - Tech rider

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TechSection(state: EpkUiState, vm: EpkViewModel) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Column(verticalArrangement = Arrangement.spacedBy(space.md)) {
        SectionTitle("Tech rider")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            WizardConstants.techPresets.forEach { item ->
                val on = item in state.tech
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(space.xs),
                    modifier = Modifier
                        .clip(CircleShape)
                        .then(if (on) Modifier.background(colors.brand) else Modifier.border(1.dp, colors.line, CircleShape))
                        .clickable { vm.toggleTech(item) }
                        .padding(horizontal = space.md, vertical = space.sm),
                ) {
                    Icon(
                        if (on) Icons.Filled.Check else Icons.Filled.Add,
                        contentDescription = null,
                        tint = if (on) colors.brandInk else colors.ink3,
                        modifier = Modifier.size(AppTheme.dimens.size.iconSm),
                    )
                    Text(item, style = AppTheme.type.footnote.copy(fontWeight = FontWeight.SemiBold), color = if (on) colors.brandInk else colors.ink2)
                }
            }
        }
    }
}

// MARK: - Links

@Composable
private fun LinksSection(state: EpkUiState, onAdd: () -> Unit, onEdit: (ArtistLink) -> Unit) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Column(verticalArrangement = Arrangement.spacedBy(space.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("Links", Modifier.weight(1f))
            Text("+ Add", style = AppTheme.type.footnote.copy(fontWeight = FontWeight.Bold), color = colors.brand, modifier = Modifier.clickable { onAdd() })
        }
        if (state.links.isEmpty()) {
            Text("Bandcamp, SoundCloud, your site — anywhere a client should land.", style = AppTheme.type.footnote, color = colors.ink3)
        } else {
            state.links.forEachIndexed { i, link ->
                if (i == 0) HRule()
                Column(
                    Modifier.fillMaxWidth().clickable { onEdit(link) }.padding(vertical = space.md),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(link.label, style = AppTheme.type.callout.copy(fontWeight = FontWeight.SemiBold), color = colors.ink)
                    Text(link.url, style = AppTheme.type.caption, color = colors.ink3, maxLines = 1)
                }
                HRule()
            }
        }
    }
}

// MARK: - Share link

@Composable
private fun ShareLinkCard(shareUrl: String) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { kotlinx.coroutines.delay(1_400); copied = false } }

    Column(verticalArrangement = Arrangement.spacedBy(space.sm)) {
        SectionTitle("Share link")
        HRule()
        Row(Modifier.fillMaxWidth().padding(vertical = space.md), verticalAlignment = Alignment.CenterVertically) {
            Text("https://$shareUrl", style = AppTheme.type.monoSmall.copy(fontWeight = FontWeight.SemiBold), color = colors.ink2, modifier = Modifier.weight(1f))
            Text(
                if (copied) "COPIED ✓" else "COPY",
                style = AppTheme.type.caption.copy(fontWeight = FontWeight.Black),
                color = colors.brand,
                modifier = Modifier.clickable {
                    clipboard.setText(AnnotatedString("https://$shareUrl"))
                    copied = true
                },
            )
        }
        HRule()
    }
}

// MARK: - Shared

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = AppTheme.type.caption.copy(fontWeight = FontWeight.Bold),
        color = AppTheme.colors.ink3,
        modifier = modifier,
    )
}
