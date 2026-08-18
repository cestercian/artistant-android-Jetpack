package `in`.artistant.app.feature.wizard

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import `in`.artistant.app.data.model.ArtistGradient
import `in`.artistant.app.designsystem.component.pressScale
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.feature.epk.EpkRowAction
import `in`.artistant.app.feature.epk.EpkSectionHeader
import `in`.artistant.app.feature.signup.SignupInputRow
import `in`.artistant.app.platform.media.WizardMediaCache
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

/**
 * The four optional steps: cover, socials, bio, samples.
 *
 * None of them gates Continue. The CTA flips to "Skip for now" while they are
 * empty, which is the only honest way to say "this costs nothing" — a disabled
 * button with unchanged copy reads as a form the artist has failed to fill.
 */

// ── Cover ────────────────────────────────────────────────────────────────────

fun LazyListScope.coverStep(state: WizardUiState, vm: WizardViewModel) {
    item(key = "cover.preview") { CoverPreview(state) }
    item(key = "cover.actions") { CoverActions(state, vm) }
    item(key = "cover.gradient") {
        Column {
            EpkSectionHeader(
                title = "Gradient",
                trailingNote = if (state.pendingCover == null) null else "Behind your photo",
            )
            Spacer(Modifier.height(AppTheme.dimens.space.sm))
            GradientPicker(selected = state.coverGradientIndex, onSelect = vm::onCoverGradientSelected)
            Spacer(Modifier.height(AppTheme.dimens.space.sm))
            Text(
                // The gradient is never dead weight: it is the floor the profile
                // paints before a cover loads, and the whole cover when there
                // isn't one. Worth saying, or artists treat it as a leftover.
                "Shows while your photo loads, and stands in if you skip one.",
                style = AppTheme.type.caption,
                color = AppTheme.colors.ink3,
            )
        }
    }
}

/**
 * The hero, at the exact shape and stacking order the public profile uses:
 * gradient floor, then photo, then the bottom fade, then the name plate.
 *
 * Painting the gradient first rather than as an `else` branch means a missing or
 * slow photo is never a hole — the same rule the EPK hero encodes.
 */
@Composable
private fun CoverPreview(state: WizardUiState) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val space = dimens.space
    val context = LocalContext.current
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(dimens.aspect.editorial)
            .clip(RoundedCornerShape(dimens.radii.lg))
            .semantics { testTag = "wizard.cover.preview" },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(ArtistGradient.palette(state.coverGradientIndex))),
        )
        state.pendingCoverPath?.let { path ->
            AsyncImage(
                // Read straight off the staged file — the pick has already been
                // copied into the wizard cache, so there is nothing to wait on.
                // The path is resolved in the ViewModel, which is where the cache
                // is injected; building a second cache instance here would sever
                // it from the singleton the upload queue drains.
                model = remember(path) { File(path) },
                contentDescription = "Your cover photo",
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
                        // Ends on the page background, never on black: a fade to
                        // black over a dark-grey page leaves a visible seam.
                        1f to colors.bg,
                    ),
                ),
        )
        if (state.pendingCover != null) {
            Text(
                "COVER",
                style = AppTheme.type.monoMicro,
                color = colors.inkOnMedia,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(space.md)
                    .clip(CircleShape)
                    .background(colors.chipOnMedia)
                    .padding(horizontal = space.sm, vertical = space.xs),
            )
        }
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(space.lg),
            verticalArrangement = Arrangement.spacedBy(space.xs),
        ) {
            Text(
                state.stageName.ifBlank { "Your stage name" },
                style = AppTheme.type.profileHeroName,
                color = colors.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOf(state.category.ifBlank { "Category" }, state.baseCity.ifBlank { "City" })
                    .joinToString(" · "),
                style = AppTheme.type.callout,
                color = colors.ink2,
            )
        }
    }
}

@Composable
private fun CoverActions(state: WizardUiState, vm: WizardViewModel) {
    val space = AppTheme.dimens.space
    val context = LocalContext.current
    val hasCover = state.pendingCover != null

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) vm.onCoverPicked(uri)
    }
    // A camera capture needs a destination URI before the intent fires, so the
    // file is minted here and remembered across the permission round-trip.
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) cameraUri?.let(vm::onCoverPicked)
    }
    val launchCamera = {
        val file = File(context.cacheDir, "artist-wizard/camera-${UUID.randomUUID()}.jpg")
            .also { it.parentFile?.mkdirs() }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        cameraUri = uri
        takePicture.launch(uri)
    }
    val requestCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera()
    }

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(space.md),
    ) {
        CoverAction(
            label = if (hasCover) "Replace photo" else "Choose photo",
            onClick = { pickPhoto.launch("image/*") },
            emphasised = true,
            modifier = Modifier.weight(1f),
            tag = "wizard.cover.choose",
        )
        CoverAction(
            label = "Take photo",
            onClick = { requestCamera.launch(Manifest.permission.CAMERA) },
            emphasised = false,
            modifier = Modifier.weight(1f),
            tag = "wizard.cover.camera",
        )
    }
    if (hasCover) {
        Spacer(Modifier.height(space.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Uploads once you're live.",
                style = AppTheme.type.caption,
                color = AppTheme.colors.ink3,
                modifier = Modifier.weight(1f),
            )
            EpkRowAction(label = "Remove", onClick = vm::clearCoverPick, tone = AppTheme.colors.hot)
        }
    }
}

@Composable
private fun CoverAction(
    label: String,
    onClick: () -> Unit,
    emphasised: Boolean,
    modifier: Modifier = Modifier,
    tag: String,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(dimens.radii.md)
    Box(
        modifier
            .clip(shape)
            .background(if (emphasised) colors.brand else Color.Transparent)
            .border(dimens.size.hairline, if (emphasised) Color.Transparent else colors.line, shape)
            .pressScale(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = dimens.space.md)
            .semantics { testTag = tag },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = AppTheme.type.footnote.copy(fontWeight = FontWeight.SemiBold),
            color = if (emphasised) colors.brandInk else colors.ink2,
        )
    }
}

@Composable
private fun GradientPicker(selected: Int, onSelect: (Int) -> Unit) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val shape = RoundedCornerShape(dimens.radii.sm)
    Row(horizontalArrangement = Arrangement.spacedBy(dimens.space.sm)) {
        (0..5).forEach { index ->
            val interaction = remember { MutableInteractionSource() }
            val isSelected = index == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(dimens.size.heroControl)
                    .clip(shape)
                    .background(Brush.linearGradient(ArtistGradient.palette(index)))
                    .border(
                        if (isSelected) dimens.size.stroke else dimens.size.hairline,
                        if (isSelected) colors.brand else colors.line,
                        shape,
                    )
                    .pressScale(interaction)
                    .clickable(interactionSource = interaction, indication = null) { onSelect(index) }
                    .semantics {
                        testTag = "wizard.cover.gradient.$index"
                        contentDescription = "Gradient ${index + 1}${if (isSelected) ", selected" else ""}"
                    },
            )
        }
    }
}

// ── Socials ──────────────────────────────────────────────────────────────────

/**
 * One card per platform: what to paste, where to find it, and what it buys.
 *
 * Paste rather than OAuth because there is no client-side link endpoint on this
 * platform yet. The pasted value still earns its keep — the profile deep-links
 * clients straight into the app — so the field is the product, not a stopgap
 * screen. The helper line under each is doing real work: the Spotify artist URL
 * in particular is buried in Spotify for Artists, not the normal share sheet,
 * and artists reliably paste their personal profile instead.
 */
fun LazyListScope.socialsStep(state: WizardUiState, vm: WizardViewModel) {
    item(key = "socials.instagram") {
        SocialCard(
            label = "Instagram",
            value = state.instagramHandle,
            onValueChange = vm::onInstagramChanged,
            placeholder = "@yourhandle",
            helper = "We deep-link clients straight into the Instagram app.",
            keyboardType = KeyboardType.Text,
            tag = "wizard.socials.instagram",
        )
    }
    item(key = "socials.spotify") {
        SocialCard(
            label = "Spotify",
            value = state.spotifyArtistUrl,
            onValueChange = vm::onSpotifyChanged,
            placeholder = "open.spotify.com/artist/…",
            helper = "From Spotify for Artists → Profile → Share.",
            keyboardType = KeyboardType.Uri,
            tag = "wizard.socials.spotify",
        )
    }
    item(key = "socials.youtube") {
        SocialCard(
            label = "YouTube",
            value = state.youtubeChannelUrl,
            onValueChange = vm::onYoutubeChanged,
            placeholder = "youtube.com/@yourchannel",
            helper = "Channel URL — handle URLs (with @) work too.",
            keyboardType = KeyboardType.Uri,
            tag = "wizard.socials.youtube",
        )
    }
}

@Composable
private fun SocialCard(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    helper: String,
    keyboardType: KeyboardType,
    tag: String,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.radii.md))
            .background(colors.bgCard)
            .padding(dimens.space.lg)
            .semantics { testTag = tag },
        verticalArrangement = Arrangement.spacedBy(dimens.space.sm),
    ) {
        SignupInputRow(
            label = label,
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            keyboardType = keyboardType,
        )
        Text(helper, style = AppTheme.type.caption, color = colors.ink3)
    }
}

// ── Bio ──────────────────────────────────────────────────────────────────────

fun LazyListScope.bioStep(state: WizardUiState, vm: WizardViewModel) {
    item(key = "bio.field") { BioField(state, vm) }
}

@Composable
private fun BioField(state: WizardUiState, vm: WizardViewModel) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val space = dimens.space
    val shape = RoundedCornerShape(dimens.radii.md)
    Column(verticalArrangement = Arrangement.spacedBy(space.sm)) {
        BasicTextField(
            value = state.bio,
            onValueChange = vm::onBioChanged,
            textStyle = AppTheme.type.body.copy(color = colors.ink),
            cursorBrush = SolidColor(colors.brand),
            // Multi-line on purpose — a bio is two sentences, and a single-line
            // field turns the second one into horizontal scrolling.
            singleLine = false,
            minLines = 4,
            maxLines = 7,
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(colors.bgCard)
                .border(dimens.size.hairline, colors.lineSoft, shape)
                .padding(space.lg)
                .semantics { testTag = "wizard.bio.field" },
            decorationBox = { inner ->
                if (state.bio.isEmpty()) {
                    Text(
                        "e.g. Indie-folk trio out of Bangalore. Mellow weddings, smoky pub sets, " +
                            "the occasional festival stage.",
                        style = AppTheme.type.body,
                        color = colors.ink4,
                    )
                }
                inner()
            },
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Short works best — you can change it later.",
                style = AppTheme.type.caption,
                color = colors.ink3,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${state.bio.length} / $WIZARD_BIO_MAX",
                style = AppTheme.type.monoMicro,
                // Quiet, then warm as the ceiling approaches, then hot at it —
                // the counter is the only place the limit is ever mentioned.
                color = when (bioCounterTone(state.bio.length)) {
                    WizardCounterTone.Quiet -> colors.ink3
                    WizardCounterTone.Warn -> colors.warm
                    WizardCounterTone.Over -> colors.hot
                },
            )
        }
    }
}

// ── Samples ──────────────────────────────────────────────────────────────────

fun LazyListScope.samplesStep(state: WizardUiState, vm: WizardViewModel) {
    items(state.pendingSamples, key = { "sample.${it.fileName}" }) { sample ->
        SampleRow(
            title = sample.title,
            durationSeconds = sample.durationSeconds,
            onTitleChange = { vm.onSampleTitleChanged(sample.fileName, it) },
            onRemove = { vm.removeSample(sample.fileName) },
        )
    }
    item(key = "samples.add") { AddSampleButton(state, vm) }
}

@Composable
private fun SampleRow(
    title: String,
    durationSeconds: Double,
    onTitleChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val space = dimens.space
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.radii.md))
            .background(colors.bgCard)
            .padding(space.lg)
            .semantics { testTag = "wizard.samples.row" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            BasicTextField(
                value = title,
                onValueChange = onTitleChange,
                singleLine = true,
                textStyle = AppTheme.type.callout.copy(
                    color = colors.ink,
                    fontWeight = FontWeight.SemiBold,
                ),
                cursorBrush = SolidColor(colors.brand),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Sample title" },
            )
            Spacer(Modifier.height(space.xs))
            Text(
                durationLabel(durationSeconds),
                style = AppTheme.type.monoMicro,
                color = colors.ink3,
            )
        }
        Spacer(Modifier.width(space.sm))
        EpkRowAction(label = "Remove", onClick = onRemove, tone = colors.hot)
    }
}

/**
 * `m:ss`, or a neutral line when the duration is unknown.
 *
 * The staging path does not probe the container, so zero means "not measured"
 * rather than "empty clip" — rendering it as `0:00` would claim a fact we do not
 * have.
 */
private fun durationLabel(seconds: Double): String {
    if (seconds <= 0.0) return "AUDIO CLIP"
    val total = seconds.roundToInt()
    return String.format(Locale.US, "%d:%02d", total / 60, total % 60)
}

@Composable
private fun AddSampleButton(state: WizardUiState, vm: WizardViewModel) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val atCap = state.pendingSamples.size >= WIZARD_MAX_SAMPLES
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(dimens.radii.md)
    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) vm.onSamplePicked(uri, uri.lastPathSegment)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(dimens.size.hairline, if (atCap) colors.line else colors.brand.copy(alpha = 0.4f), shape)
            .pressScale(interaction)
            .clickable(enabled = !atCap, interactionSource = interaction, indication = null) {
                // Only what the samples bucket accepts. `audio/*` offered WAV and
                // OGG, which stage and publish fine and then 400 in the upload
                // queue — a failure the artist never sees. The adoption path
                // re-checks, because a picker filter is a hint, not a gate.
                pickAudio.launch(WizardMediaCache.ACCEPTED_AUDIO_MIME_TYPES.toTypedArray())
            }
            .padding(dimens.space.lg)
            .semantics { testTag = "wizard.samples.add" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (atCap) "That's the maximum" else "Add sample",
            style = AppTheme.type.footnote.copy(fontWeight = FontWeight.SemiBold),
            color = if (atCap) colors.ink3 else colors.brand,
            modifier = Modifier.weight(1f),
        )
        Text(
            "${state.pendingSamples.size} / $WIZARD_MAX_SAMPLES",
            style = AppTheme.type.monoMicro,
            color = colors.ink3,
        )
    }
}
