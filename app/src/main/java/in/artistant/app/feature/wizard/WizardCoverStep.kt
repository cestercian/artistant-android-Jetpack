package `in`.artistant.app.feature.wizard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import `in`.artistant.app.data.model.ArtistGradient
import `in`.artistant.app.designsystem.theme.AppTheme

/**
 * Step 6 — cover. Priority video > photo > gradient (iOS `ArtistCoverStep`). Picks DON'T upload
 * here; they stage into the wizard cache and drain on Publish. A gallery of extra photos + a
 * gradient fallback complete the step.
 *
 * ponytail: this is the FIRST-run wizard, so there's no already-uploaded remote media to
 * reconcile/delete (that lives in the M5c EPK editor) — the step only manages pending picks.
 * Video shows a gradient + "queued" chip rather than a live player (playback is on the published
 * profile; an ExoPlayer preview isn't worth it here).
 */
@Composable
fun WizardCoverStep(state: WizardUiState, vm: WizardViewModel) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space

    val pickCoverPhoto = rememberImageLibraryPicker(vm::stageCoverPhoto)
    val captureCoverPhoto = rememberCameraPhotoCapture(vm::stageCoverPhoto)
    val pickCoverVideo = rememberVideoLibraryPicker(vm::stageCoverVideo)
    val captureCoverVideo = rememberCameraVideoCapture(vm::stageCoverVideo)
    val pickGalleryPhoto = rememberImageLibraryPicker(vm::addGalleryPhoto)
    val captureGalleryPhoto = rememberCameraPhotoCapture(vm::addGalleryPhoto)

    val hasCover = state.coverPhoto != null || state.coverVideo != null

    WizardScaffold(
        step = WizardStep.Cover,
        title = "Pick your cover",
        subtitle = if (hasCover) "Replace either the photo or the video below, or remove to fall back to a gradient."
        else "Drop a photo or a short video. Pick a gradient if you'd rather skip uploads for now.",
        ctaText = "Continue →",
        ctaEnabled = true, // uploads run only on Publish
        onBack = vm::back,
        onCta = vm::advance,
    ) {
        WizardCoverPreview(state, vm)
        Spacer(Modifier.height(space.lg))

        MediaSlot(
            title = "PHOTO",
            valueLabel = state.coverPhoto?.let { "${it.width ?: 0} × ${it.height ?: 0} · queued" },
            isSet = state.coverPhoto != null,
            onLibrary = pickCoverPhoto,
            onCamera = captureCoverPhoto,
            onRemove = vm::removeCoverPhoto,
        )
        Spacer(Modifier.height(space.md))
        MediaSlot(
            title = "VIDEO",
            valueLabel = state.coverVideo?.let {
                "%.1fs · %d × %d · queued".format(it.durationSeconds ?: 0.0, it.width ?: 0, it.height ?: 0)
            },
            isSet = state.coverVideo != null,
            onLibrary = pickCoverVideo,
            onCamera = captureCoverVideo,
            onRemove = vm::removeCoverVideo,
        )

        if (state.coverPhoto != null || state.gallery.isNotEmpty()) {
            Spacer(Modifier.height(space.lg))
            WizardSectionLabel("MORE PHOTOS · ${state.gallery.size}/5")
            Spacer(Modifier.height(space.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                state.gallery.forEach { ref ->
                    GalleryThumb(file = vm.cacheFile(ref.cacheFilename), onRemove = { vm.removeGalleryPhoto(ref.id) })
                }
                if (state.gallery.size < 5) {
                    AddGalleryCell(onClick = pickGalleryPhoto, onCamera = captureGalleryPhoto)
                }
            }
        }

        state.publishError?.let {
            Spacer(Modifier.height(space.md))
            Text(it, style = AppTheme.type.footnote.copy(fontWeight = FontWeight.SemiBold), color = colors.hot)
        }

        if (!hasCover) {
            Spacer(Modifier.height(space.lg))
            WizardSectionLabel("OR PICK A GRADIENT")
            Spacer(Modifier.height(space.sm))
            GradientPicker(selected = state.coverGradientIndex, onPick = vm::setGradient)
        }
    }
}

/** The portrait cover preview shared by the cover step + the final preview step (matches the
 *  published profile hero: pending photo over gradient, name/category overlay). */
@Composable
internal fun WizardCoverPreview(state: WizardUiState, vm: WizardViewModel) {
    val colors = AppTheme.colors
    val gradient = Brush.linearGradient(ArtistGradient.palette(state.coverGradientIndex))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f / 1.1f)
            .clip(RoundedCornerShape(18.dp))
            .background(gradient),
    ) {
        if (state.coverPhoto != null) {
            AsyncImage(
                model = vm.cacheFile(state.coverPhoto.cacheFilename),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Bottom scrim + title overlay (matches the published profile hero).
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))),
            ),
        )
        Column(Modifier.align(Alignment.BottomStart).padding(AppTheme.dimens.space.xl)) {
            if (state.coverVideo != null) {
                Text(
                    "VIDEO QUEUED",
                    style = AppTheme.type.caption.copy(fontWeight = FontWeight.Black),
                    color = Color.White,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.4f))
                        .padding(horizontal = AppTheme.dimens.space.sm, vertical = AppTheme.dimens.space.xs),
                )
                Spacer(Modifier.height(4.dp))
            }
            Text(
                state.stageName.ifEmpty { "Your stage name" },
                style = AppTheme.type.displaySmall,
                color = Color.White,
            )
            Text(
                "${state.category.ifEmpty { "Category" }} · ${state.baseCity.ifEmpty { "City" }}",
                style = AppTheme.type.footnote.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White.copy(alpha = 0.85f),
            )
        }
        if (state.isProcessingVideo) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        }
    }
}

@Composable
private fun MediaSlot(
    title: String,
    valueLabel: String?,
    isSet: Boolean,
    onLibrary: () -> Unit,
    onCamera: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space.sm),
    ) {
        Column(Modifier.weight(1f)) {
            WizardSectionLabel(title)
            Text(
                valueLabel ?: if (isSet) "Saved" else "Not added",
                style = AppTheme.type.footnote.copy(fontWeight = FontWeight.SemiBold),
                color = if (isSet) colors.ink else colors.ink3,
            )
        }
        SlotButton(if (isSet) "Replace" else "Add", onLibrary)
        SlotButton("Camera", onCamera)
        if (isSet) {
            Text("Remove", style = AppTheme.type.caption.copy(fontWeight = FontWeight.SemiBold), color = colors.ink2,
                modifier = Modifier.clickable(onClick = onRemove).padding(space.sm))
        }
    }
}

@Composable
private fun SlotButton(label: String, onClick: () -> Unit) {
    val colors = AppTheme.colors
    Text(
        label,
        style = AppTheme.type.caption.copy(fontWeight = FontWeight.SemiBold),
        color = colors.brandInk,
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(colors.brand)
            .clickable(onClick = onClick)
            .padding(horizontal = AppTheme.dimens.space.md, vertical = AppTheme.dimens.space.sm),
    )
}

@Composable
private fun GalleryThumb(file: java.io.File, onRemove: () -> Unit) {
    val colors = AppTheme.colors
    Box(Modifier.size(88.dp).clip(RoundedCornerShape(12.dp)).background(colors.bgCard)) {
        AsyncImage(model = file, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Text(
            "✕",
            style = AppTheme.type.caption.copy(fontWeight = FontWeight.Black),
            color = Color.White,
            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                .clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.55f))
                .clickable(onClick = onRemove).padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun AddGalleryCell(onClick: () -> Unit, onCamera: () -> Unit) {
    val colors = AppTheme.colors
    Column(
        modifier = Modifier.size(88.dp).clip(RoundedCornerShape(12.dp))
            .border(1.dp, colors.line, RoundedCornerShape(12.dp)),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("+ Library", style = AppTheme.type.caption, color = colors.ink3, modifier = Modifier.clickable(onClick = onClick).padding(2.dp))
        Text("Camera", style = AppTheme.type.caption, color = colors.ink3, modifier = Modifier.clickable(onClick = onCamera).padding(2.dp))
    }
}

/** Plain 2×3 gradient grid — only 6 presets, so a non-lazy Column-of-Rows avoids the
 *  nested-scroll footgun of a LazyVerticalGrid inside the scaffold's scrolling Column. */
@Composable
private fun GradientPicker(selected: Int, onPick: (Int) -> Unit) {
    val colors = AppTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0..2, 3..5).forEach { rowRange ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowRange.forEach { idx ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(64.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Brush.linearGradient(ArtistGradient.palette(idx)))
                            .border(2.dp, if (idx == selected) colors.brand else colors.lineSoft, RoundedCornerShape(12.dp))
                            .clickable { onPick(idx) },
                    )
                }
            }
        }
    }
}
