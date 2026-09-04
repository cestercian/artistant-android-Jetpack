package `in`.artistant.app.feature.wizard

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import `in`.artistant.app.designsystem.component.AppTextField
import `in`.artistant.app.designsystem.component.Banner
import `in`.artistant.app.designsystem.component.BannerTone
import `in`.artistant.app.designsystem.component.EyebrowLabel
import `in`.artistant.app.designsystem.component.SecondaryButton
import `in`.artistant.app.designsystem.component.pressScale
import `in`.artistant.app.designsystem.rememberHaptics
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.ArtistGradient
import `in`.artistant.app.domain.artist.ServiceTags
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

// ── Cover (screen 41) ────────────────────────────────────────────────────────

fun LazyListScope.coverStep(state: WizardUiState, vm: WizardViewModel) {
    item(key = "cover.slot") { CoverSlot(state) }
    item(key = "cover.actions") { CoverActions(state, vm) }
    // Emitted only when there is something to say — the scaffold spaces its
    // items, so an always-present empty row opens a hole above the picker.
    state.mediaError?.let { message -> item(key = "cover.error") { CoverPermissionBanner(message) } }
    item(key = "cover.gradient") {
        Column {
            EyebrowLabel("Behind your photo")
            Spacer(Modifier.height(AppTheme.dimens.space.md))
            GradientPicker(selected = state.coverGradientIndex, onSelect = vm::onCoverGradientSelected)
            Spacer(Modifier.height(AppTheme.dimens.space.md))
            Text(
                // The gradient is never dead weight: it is the floor the profile
                // paints before a cover loads, and the whole cover when there
                // isn't one. Worth saying, or artists treat it as a leftover.
                "Shows while your photo loads, and stands in if you skip one.",
                style = AppTheme.type.caption,
                color = AppTheme.colors.ink4,
            )
        }
    }
    item(key = "cover.note") {
        Text(
            "Staged uploads are cached on disk, so quitting the app won't lose them. " +
                "The photo uploads once you're live.",
            style = AppTheme.type.caption,
            color = AppTheme.colors.ink4,
        )
    }
}

/**
 * The cover slot at the exact shape the profile crops to: gradient floor, then
 * the photo, then the recommended-ratio tag.
 *
 * Painting the gradient first rather than as an `else` branch means a missing or
 * slow photo is never a hole — the same rule the profile hero encodes.
 */
@Composable
private fun CoverSlot(state: WizardUiState) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val shape = RoundedCornerShape(dimens.radii.card)
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(dimens.aspect.editorial)
            .clip(shape)
            .background(colors.placeholder)
            .border(dimens.size.hairline, colors.hairline, shape)
            .semantics { testTag = "wizard.cover.preview" },
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
                Text("Cover photo", style = AppTheme.type.subtitle, color = colors.ink4)
            }
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            ArtistGradient.palette(state.coverGradientIndex),
                        ),
                    ),
            )
            AsyncImage(
                // Read straight off the staged file — the pick has already been
                // copied into the wizard cache, so there is nothing to wait on.
                // The path is resolved in the ViewModel, which is where the cache
                // is injected; building a second cache instance here would sever
                // it from the singleton the upload queue drains.
                model = remember(state.pendingCoverPath) { File(state.pendingCoverPath) },
                contentDescription = "Your cover photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(
            "Recommended 4:5",
            style = AppTheme.type.caption.copy(fontWeight = FontWeight.Bold),
            color = colors.onDark,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(dimens.space.md)
                .clip(CircleShape)
                .background(colors.dark)
                .padding(horizontal = dimens.space.md, vertical = dimens.space.sm),
        )
    }
}

@Composable
private fun CoverActions(state: WizardUiState, vm: WizardViewModel) {
    val dimens = AppTheme.dimens
    val context = LocalContext.current
    val hasCover = state.pendingCover != null

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) vm.onCoverPicked(uri)
    }
    // A camera capture needs a destination URI before the intent fires, so the
    // file is minted here and held across the permission round-trip.
    //
    // `rememberSaveable`, not `remember`: the camera is a separate full-screen
    // activity and ours can be recreated behind it (low memory, "don't keep
    // activities", a config change while the shutter is up). The result registry
    // restores its pending launch and still reports ok=true, so a plain
    // `remember` came back null and the capture was dropped without a word —
    // with the photo sitting on disk under a name nothing remembered. Stored as
    // a String because that is savable everywhere a Bundle goes.
    var cameraUri by rememberSaveable { mutableStateOf<String?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) cameraUri?.let { vm.onCoverPicked(it.toUri()) }
    }
    val launchCamera = {
        val file = File(context.cacheDir, "artist-wizard/camera-${UUID.randomUUID()}.jpg")
            .also { it.parentFile?.mkdirs() }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        cameraUri = uri.toString()
        takePicture.launch(uri)
    }
    val requestCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // A refusal has to say something. After the second one the OS stops
        // showing the dialog at all, so without this the button is simply dead
        // and looks identical to the working one beside it.
        if (granted) launchCamera() else vm.onCameraUnavailable()
    }

    Column {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
        ) {
            SecondaryButton(
                text = "Camera",
                onClick = { requestCamera.launch(Manifest.permission.CAMERA) },
                fullWidth = false,
                leading = {
                    Icon(
                        Icons.Filled.PhotoCamera,
                        contentDescription = null,
                        tint = AppTheme.colors.ink,
                        modifier = Modifier.size(dimens.size.iconLg),
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .semantics { testTag = "wizard.cover.camera" },
            )
            SecondaryButton(
                text = if (hasCover) "Replace" else "Library",
                onClick = { pickPhoto.launch("image/*") },
                fullWidth = false,
                leading = {
                    Icon(
                        Icons.Filled.PhotoLibrary,
                        contentDescription = null,
                        tint = AppTheme.colors.ink,
                        modifier = Modifier.size(dimens.size.iconLg),
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .semantics { testTag = "wizard.cover.choose" },
            )
        }
        if (hasCover) {
            Spacer(Modifier.height(dimens.space.md))
            Text(
                "Remove photo",
                style = AppTheme.type.caption.copy(fontWeight = FontWeight.Bold),
                color = AppTheme.colors.danger,
                modifier = Modifier
                    .clip(RoundedCornerShape(dimens.radii.sm))
                    .clickable(role = Role.Button, onClick = vm::clearCoverPick)
                    .padding(dimens.space.sm),
            )
        }
    }
}

/**
 * The refusal, with somewhere to go.
 *
 * A denied camera permission is permanent from inside the app on Android 11+ —
 * the OS stops showing the dialog after the second refusal — so a retry button
 * would be a button that provably does nothing. The only real recovery is the
 * system settings page for this app, which is what the action opens, and the
 * library beside it is the other one, which is why the copy names it.
 */
@Composable
private fun CoverPermissionBanner(message: String) {
    val context = LocalContext.current
    Banner(
        title = message,
        tone = BannerTone.Attention,
        detail = "Open Settings → Artistant → Permissions to turn the camera on, or pick from your library.",
        actionLabel = "Settings",
        onAction = {
            runCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        },
        modifier = Modifier.semantics { testTag = "wizard.media.error" },
    )
}

@Composable
private fun GradientPicker(selected: Int, onSelect: (Int) -> Unit) {
    val haptics = rememberHaptics()
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val shape = RoundedCornerShape(dimens.radii.md)
    Row(horizontalArrangement = Arrangement.spacedBy(dimens.space.sm)) {
        (0..GRADIENT_LAST).forEach { index ->
            val interaction = remember(index) { MutableInteractionSource() }
            val isSelected = index == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(dimens.size.swatchH)
                    .clip(shape)
                    .background(
                        Brush.linearGradient(ArtistGradient.palette(index)),
                    )
                    .border(
                        if (isSelected) dimens.size.stroke else dimens.size.hairline,
                        if (isSelected) colors.ink else colors.hairline,
                        shape,
                    )
                    .pressScale(interaction)
                    .clickable(interactionSource = interaction, indication = null) {
                        haptics.select()
                        onSelect(index)
                    }
                    .semantics {
                        testTag = "wizard.cover.gradient.$index"
                        contentDescription = "Gradient ${index + 1}${if (isSelected) ", selected" else ""}"
                    },
            )
        }
    }
}

/** Six swatches — the range `ArtistGradient` clamps to. */
private const val GRADIENT_LAST = 5

// ── Socials (screen 42) ──────────────────────────────────────────────────────

/**
 * One row per platform: what to paste, and what it is worth.
 *
 * The design draws these as CONNECT rows, because a connected handle can be
 * verified and a pasted link can only be trusted. There is no Instagram,
 * Spotify or YouTube OAuth on this backend and no link endpoint to build one
 * against, so these stay paste fields — and the banner says out loud that
 * nothing here is verified rather than letting the row's shape imply it is.
 */
fun LazyListScope.socialsStep(state: WizardUiState, vm: WizardViewModel) {
    item(key = "socials.instagram") {
        SocialRow(
            label = "Instagram",
            value = state.instagramHandle,
            onValueChange = vm::onInstagramChanged,
            hint = "@yourhandle",
            helper = "We deep-link clients straight into the Instagram app.",
            keyboardType = KeyboardType.Text,
            tag = "wizard.socials.instagram",
        )
    }
    item(key = "socials.spotify") {
        SocialRow(
            label = "Spotify",
            value = state.spotifyArtistUrl,
            onValueChange = vm::onSpotifyChanged,
            hint = "open.spotify.com/artist/…",
            helper = "From Spotify for Artists → Profile → Share.",
            keyboardType = KeyboardType.Uri,
            tag = "wizard.socials.spotify",
        )
    }
    item(key = "socials.youtube") {
        SocialRow(
            label = "YouTube",
            value = state.youtubeChannelUrl,
            onValueChange = vm::onYoutubeChanged,
            hint = "youtube.com/@yourchannel",
            helper = "Channel URL — handle URLs (with @) work too.",
            keyboardType = KeyboardType.Uri,
            tag = "wizard.socials.youtube",
        )
    }
    item(key = "socials.note") {
        Banner(
            title = "These links are not verified.",
            tone = BannerTone.Info,
            detail = "Anyone can paste a link, and we don't check who owns it — " +
                "we show them on your profile and count them towards the social proof part of your score.",
        )
    }
}

@Composable
private fun SocialRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    helper: String,
    keyboardType: KeyboardType,
    tag: String,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Column(
        Modifier
            .fillMaxWidth()
            .semantics { testTag = tag },
        verticalArrangement = Arrangement.spacedBy(dimens.space.sm),
    ) {
        AppTextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            hint = hint,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            trailing = {
                if (value.isNotBlank()) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Clear $label",
                        tint = colors.ink4,
                        modifier = Modifier
                            .size(dimens.size.iconLg)
                            .clip(CircleShape)
                            .clickable(role = Role.Button) { onValueChange("") },
                    )
                }
            },
        )
        Text(helper, style = AppTheme.type.caption, color = colors.ink4)
    }
}

// ── Bio (screen 43) ──────────────────────────────────────────────────────────

fun LazyListScope.bioStep(state: WizardUiState, vm: WizardViewModel) {
    item(key = "bio.field") { BioField(state, vm) }
    item(key = "bio.tags") {
        WizardChipSection(
            title = "Service tags",
            options = ServiceTags.catalog.map { it.second },
            isSelected = { label ->
                state.serviceTags.any { slug ->
                    ServiceTags.label(slug) == label
                }
            },
            onToggle = { label ->
                ServiceTags.catalog.firstOrNull { it.second == label }
                    ?.let { vm.toggleServiceTag(it.first) }
            },
            tag = "wizard.bio.serviceTags",
        )
    }
    item(key = "bio.tagsNote") {
        Banner(
            title = "Tags are matched against the occasion a host picks.",
            tone = BannerTone.Info,
            detail = "They move you up a search result more than the bio does. " +
                "Up to ${ServiceTags.MAX_TAGS} — a profile that claims " +
                "everything says nothing.",
        )
    }
}

@Composable
private fun BioField(state: WizardUiState, vm: WizardViewModel) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val shape = RoundedCornerShape(dimens.radii.control)
    Column(verticalArrangement = Arrangement.spacedBy(dimens.space.sm)) {
        EyebrowLabel("Short bio")
        BasicTextField(
            value = state.bio,
            onValueChange = vm::onBioChanged,
            textStyle = AppTheme.type.body.copy(color = colors.ink),
            cursorBrush = SolidColor(colors.accentInk),
            // Multi-line on purpose — a bio is two sentences, and a single-line
            // field turns the second one into horizontal scrolling.
            singleLine = false,
            minLines = BIO_MIN_LINES,
            maxLines = BIO_MAX_LINES,
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(colors.surface2)
                .border(dimens.size.hairline, colors.hairline, shape)
                .padding(dimens.space.lg)
                .semantics { testTag = "wizard.bio.field" },
            decorationBox = { inner ->
                if (state.bio.isEmpty()) {
                    Text(
                        "Warm four-part harmonies and a live cajón. We play weddings, " +
                            "brand launches and living rooms.",
                        style = AppTheme.type.body,
                        color = colors.hint,
                    )
                }
                inner()
            },
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                // What good looks like at this length, not just how many
                // characters are left. The hint changes four times on the way to
                // a finished bio; a static line would be wrong for three of them.
                bioGuidance(state.bio.length),
                style = AppTheme.type.caption,
                color = colors.ink4,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(dimens.space.sm))
            Text(
                "${state.bio.length} / $WIZARD_BIO_MAX",
                style = AppTheme.type.monoPill,
                // Quiet, then warm as the ceiling approaches, then hot at it —
                // the counter is the only place the limit is ever mentioned.
                color = when (bioCounterTone(state.bio.length)) {
                    WizardCounterTone.Quiet -> colors.ink4
                    WizardCounterTone.Warn -> colors.warm
                    WizardCounterTone.Over -> colors.danger
                },
                modifier = Modifier.semantics { testTag = "wizard.bio.counter" },
            )
        }
    }
}

private const val BIO_MIN_LINES = 4
private const val BIO_MAX_LINES = 7

// ── Samples (screen 44) ──────────────────────────────────────────────────────

fun LazyListScope.samplesStep(state: WizardUiState, vm: WizardViewModel) {
    item(key = "samples.header") {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            EyebrowLabel("Your samples", Modifier.weight(1f))
            Text(
                "${state.pendingSamples.size} / $WIZARD_MAX_SAMPLES",
                style = AppTheme.type.monoPill,
                color = AppTheme.colors.ink,
            )
        }
    }
    items(state.pendingSamples, key = { "sample.${it.fileName}" }) { sample ->
        SampleRow(
            title = sample.title,
            durationSeconds = sample.durationSeconds,
            onTitleChange = { vm.onSampleTitleChanged(sample.fileName, it) },
            onRemove = { vm.removeSample(sample.fileName) },
        )
    }
    item(key = "samples.add") { AddSampleButton(state, vm) }
    item(key = "samples.uploads") { UploadStatus(state, vm) }
    state.mediaError?.let { message ->
        item(key = "samples.error") {
            Banner(
                title = message,
                tone = BannerTone.Failure,
                modifier = Modifier.semantics { testTag = "wizard.media.error" },
            )
        }
    }
}

/**
 * A staged clip: a play disc, an editable title, and the duration.
 *
 * The disc is drawn but inert — nothing in the wizard plays audio, and a button
 * that looks pressable and is not would be worse than none. It is here because
 * the row has to read as a clip rather than as a filename, which is what tells
 * the artist these are the things a client will hear.
 */
@Composable
private fun SampleRow(
    title: String,
    durationSeconds: Double,
    onTitleChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.radii.card))
            .background(colors.surface3)
            .padding(dimens.space.md)
            .semantics { testTag = "wizard.samples.row" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
    ) {
        Box(
            Modifier
                .size(dimens.size.avatarSm)
                .clip(CircleShape)
                .background(colors.accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = colors.onAccent,
                modifier = Modifier.size(dimens.size.iconLg),
            )
        }
        Column(Modifier.weight(1f)) {
            BasicTextField(
                value = title,
                onValueChange = onTitleChange,
                singleLine = true,
                textStyle = AppTheme.type.rowTitle.copy(color = colors.ink),
                cursorBrush = SolidColor(colors.accentInk),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Sample title" },
            )
            Spacer(Modifier.height(dimens.space.xs))
            Text(durationLabel(durationSeconds), style = AppTheme.type.monoPill, color = colors.ink4)
        }
        Icon(
            Icons.Outlined.DeleteOutline,
            contentDescription = "Remove this clip",
            tint = colors.ink4,
            modifier = Modifier
                .size(dimens.size.rowMin)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onRemove)
                .padding(dimens.space.md),
        )
    }
}

/**
 * `m:ss`, or a neutral line when the duration is unknown.
 *
 * Staging measures the copy now, but a container can still refuse to answer, so
 * zero keeps meaning "not measured" rather than "empty clip" — rendering it as
 * `0:00` would claim a fact we do not have.
 */
private fun durationLabel(seconds: Double): String {
    if (seconds <= 0.0) return "AUDIO CLIP"
    val total = seconds.roundToInt()
    return String.format(Locale.US, "%d:%02d", total / 60, total % 60)
}

@Composable
private fun AddSampleButton(state: WizardUiState, vm: WizardViewModel) {
    val atCap = state.pendingSamples.size >= WIZARD_MAX_SAMPLES
    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        // The URI only. The title comes from the provider's DISPLAY_NAME, read on
        // IO in the ViewModel — `lastPathSegment` here is a document id
        // ("audio:1000000042"), which is what used to reach the public profile.
        if (uri != null) vm.onSamplePicked(uri)
    }
    DashedAction(
        label = if (atCap) "Six clips is the maximum" else "Add a clip",
        enabled = !atCap,
        onClick = {
            // Only what the samples bucket accepts. `audio/*` offered WAV and
            // OGG, which stage and publish fine and then 400 in the upload
            // queue — a failure the artist never sees. The adoption path
            // re-checks, because a picker filter is a hint, not a gate.
            pickAudio.launch(WizardMediaCache.ACCEPTED_AUDIO_MIME_TYPES.toTypedArray())
        },
        tag = "wizard.samples.add",
    )
}

/**
 * What the upload queue is doing, in place.
 *
 * Read off the queue rather than animated locally, because the queue is the only
 * thing that knows: it persists to disk, replays after a kill, and can still be
 * draining work from a previous publish when the artist re-enters the wizard. A
 * bar we drove ourselves would be a picture of an upload rather than a report of
 * one — and the failed case, which is the one worth surfacing, would have
 * nowhere to come from at all.
 */
@Composable
private fun UploadStatus(state: WizardUiState, vm: WizardViewModel) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val uploads = state.uploads
    val inFlight = uploads.pending.size + if (uploads.isRunning) 1 else 0
    when {
        uploads.failed.isNotEmpty() -> Banner(
            title = "${uploads.failed.size} upload${if (uploads.failed.size == 1) "" else "s"} didn't finish.",
            tone = BannerTone.Failure,
            detail = "The files are still on disk. Retrying sends them again.",
            actionLabel = "Retry",
            onAction = vm::retryFailedUploads,
            modifier = Modifier.semantics { testTag = "wizard.samples.uploadsFailed" },
        )
        inFlight > 0 -> Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(dimens.radii.buttonLg))
                .background(colors.brandSoft)
                .padding(dimens.space.lg)
                .semantics { testTag = "wizard.samples.uploading" },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
        ) {
            Box(
                Modifier
                    .size(dimens.component.toastIcon)
                    .clip(CircleShape)
                    .background(colors.accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Upload,
                    contentDescription = null,
                    tint = colors.onAccent,
                    modifier = Modifier.size(dimens.size.iconMd),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "Uploading $inFlight file${if (inFlight == 1) "" else "s"}",
                    style = AppTheme.type.subtitle.copy(fontWeight = FontWeight.Bold),
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(dimens.space.sm))
                UploadTrack(completed = uploads.batchCompleted, total = uploads.batchTotal)
            }
        }
        else -> Text(
            "Clips upload after you publish. The queue is written to disk, " +
                "so closing the app pauses it rather than losing it.",
            style = AppTheme.type.caption,
            color = colors.ink4,
        )
    }
}

/**
 * Batch progress as a filled track.
 *
 * Driven off the queue's own completed/total counters, so it reports files
 * finished rather than bytes sent — bytes are not something this queue measures,
 * and a bar that crept on a timer would be a lie with a smooth animation on it.
 */
@Composable
private fun UploadTrack(completed: Int, total: Int) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val fraction = if (total <= 0) 0f else (completed.toFloat() / total).coerceIn(0f, 1f)
    Box(
        Modifier
            .fillMaxWidth()
            .height(dimens.dashboard.meterHeight)
            .clip(CircleShape)
            .background(colors.onAccent.copy(alpha = TRACK_ALPHA)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(dimens.dashboard.meterHeight)
                .clip(CircleShape)
                .background(colors.onAccent),
        )
    }
}

/** The unfilled half of the track, on an accent-tinted ground. */
private const val TRACK_ALPHA = 0.18f
