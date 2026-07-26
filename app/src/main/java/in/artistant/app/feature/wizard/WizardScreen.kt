package `in`.artistant.app.feature.wizard

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.artistant.app.common.util.formatInr
import `in`.artistant.app.data.model.ArtistGradient
import `in`.artistant.app.designsystem.component.ButtonVariant
import `in`.artistant.app.designsystem.component.HRule
import `in`.artistant.app.designsystem.component.Pill
import `in`.artistant.app.designsystem.component.PillTone
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.feature.booking.DefaultTimeSlots
import `in`.artistant.app.feature.signup.SignupBackButton
import `in`.artistant.app.feature.signup.SignupProgressDots
import `in`.artistant.app.feature.signup.SignupInputRow
import `in`.artistant.app.feature.signup.ProgressBar

/**
 * Artist onboarding wizard host — scaffold port of iOS `ArtistWizardView`. Step bodies are
 * inline simple forms; cover/samples are placeholders until CameraX + SAF land (M5 media).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WizardScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WizardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                WizardEvent.Finished -> onFinished()
            }
        }
    }

    BackHandler(enabled = state.step != WizardStep.Identity && state.step != WizardStep.Done) {
        viewModel.back()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg)
            .statusBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = space.lg, vertical = space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.step != WizardStep.Identity && state.step != WizardStep.Done) {
                SignupBackButton(onClick = viewModel::back)
                Spacer(Modifier.weight(1f))
            } else {
                Spacer(Modifier.weight(1f))
            }
        }

        wizardProgressIndex(state.step)?.let { idx ->
            SignupProgressDots(
                ProgressBar(index = idx, total = wizardProgressTotal()),
            )
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = space.xl),
        ) {
            Text(wizardStepTitle(state.step), style = AppTheme.type.displaySub, color = colors.ink)
            Spacer(Modifier.height(space.sm))
            Text(wizardStepSubtitle(state.step), style = AppTheme.type.body, color = colors.ink3)
            Spacer(Modifier.height(space.xl))

            when (state.step) {
                WizardStep.Identity -> IdentityStepContent(state, viewModel)
                WizardStep.Location -> LocationStepContent(state, viewModel)
                WizardStep.Pricing -> PricingStepContent(state, viewModel)
                WizardStep.Tech -> TechStepContent(state, viewModel)
                WizardStep.Availability -> AvailabilityStepContent(state, viewModel)
                WizardStep.Cover -> CoverStepContent(state, viewModel)
                WizardStep.Socials -> SocialsStepContent(state, viewModel)
                WizardStep.Bio -> BioStepContent(state, viewModel)
                WizardStep.Samples -> SamplesStepContent(state, viewModel)
                WizardStep.Preview -> PreviewStepContent(state)
                WizardStep.Done -> DoneStepContent(state.stageName)
            }

            state.publishError?.let { msg ->
                Spacer(Modifier.height(space.md))
                Text(msg, style = AppTheme.type.footnote, color = colors.hot)
            }
            Spacer(Modifier.height(space.xxl))
        }

        Column(Modifier.padding(space.xl)) {
            when (state.step) {
                WizardStep.Done -> PrimaryButton(
                    text = "Go to dashboard",
                    onClick = viewModel::finishFromDone,
                    fullWidth = true,
                )
                WizardStep.Cover -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(space.md)) {
                        PrimaryButton(
                            text = "Skip",
                            onClick = viewModel::next,
                            variant = `in`.artistant.app.designsystem.component.ButtonVariant.Ghost,
                            modifier = Modifier.weight(1f),
                        )
                        PrimaryButton(
                            text = "Continue",
                            onClick = viewModel::next,
                            enabled = state.canAdvance && !state.isPublishing,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                WizardStep.Samples -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(space.md)) {
                        PrimaryButton(
                            text = "Skip",
                            onClick = viewModel::next,
                            variant = `in`.artistant.app.designsystem.component.ButtonVariant.Ghost,
                            modifier = Modifier.weight(1f),
                        )
                        PrimaryButton(
                            text = "Continue",
                            onClick = viewModel::next,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                else -> {
                    val cta = when (state.step) {
                        WizardStep.Preview -> if (state.isPublishing) "Publishing…" else "Publish"
                        else -> "Continue"
                    }
                    PrimaryButton(
                        text = cta,
                        onClick = viewModel::next,
                        enabled = state.canAdvance && !state.isPublishing,
                        fullWidth = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun IdentityStepContent(state: WizardUiState, vm: WizardViewModel) {
    val space = AppTheme.dimens.space
    SignupInputRow("Stage name", state.stageName, vm::onStageNameChanged)
    Spacer(Modifier.height(space.lg))
    SignupInputRow("Handle", state.handle, vm::onHandleChanged, prefix = "@")
    Spacer(Modifier.height(space.lg))
    Text("Category", style = AppTheme.type.caption, color = AppTheme.colors.ink3)
    Spacer(Modifier.height(space.sm))
    CategoryChips(selected = state.category, onSelect = vm::onCategorySelected)
    Spacer(Modifier.height(space.lg))
    SignupInputRow("Genre (optional)", state.genre, vm::onGenreChanged)
}

@Composable
private fun LocationStepContent(state: WizardUiState, vm: WizardViewModel) {
    SignupInputRow("Base city", state.baseCity, vm::onBaseCityChanged)
}

@Composable
private fun PricingStepContent(state: WizardUiState, vm: WizardViewModel) {
    val space = AppTheme.dimens.space
    SignupInputRow("Package name", state.packageName, vm::onPackageNameChanged)
    Spacer(Modifier.height(space.lg))
    SignupInputRow("Duration", state.packageDuration, vm::onPackageDurationChanged)
    Spacer(Modifier.height(space.lg))
    SignupInputRow("Price (INR)", state.packagePrice, vm::onPackagePriceChanged)
    Spacer(Modifier.height(space.sm))
    Text(
        "Saved to packages on publish.",
        style = AppTheme.type.footnote,
        color = AppTheme.colors.ink3,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TechStepContent(state: WizardUiState, vm: WizardViewModel) {
    val space = AppTheme.dimens.space
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            SignupInputRow(
                "Add item",
                state.techDraft,
                vm::onTechDraftChanged,
            )
        }
        PrimaryButton(text = "Add", onClick = vm::addTechItem)
    }
    Spacer(Modifier.height(space.md))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(space.sm)) {
        state.techItems.forEach { item ->
            Pill(text = item, tone = PillTone.Brand, modifier = Modifier.clickable { vm.removeTechItem(item) })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AvailabilityStepContent(state: WizardUiState, vm: WizardViewModel) {
    val space = AppTheme.dimens.space
    Text("Days", style = AppTheme.type.caption, color = AppTheme.colors.ink3)
    Spacer(Modifier.height(space.sm))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(space.sm)) {
        WizardWeekdays.forEach { day ->
            SelectableChip(day, day in state.daysAvailable) { vm.toggleDay(day) }
        }
    }
    Spacer(Modifier.height(space.lg))
    Text("Start times", style = AppTheme.type.caption, color = AppTheme.colors.ink3)
    Spacer(Modifier.height(space.sm))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(space.sm)) {
        DefaultTimeSlots.forEach { slot ->
            SelectableChip(slot, slot in state.timeSlots) { vm.toggleTimeSlot(slot) }
        }
    }
}

@Composable
private fun CoverStepContent(state: WizardUiState, vm: WizardViewModel) {
    val space = AppTheme.dimens.space
    val context = androidx.compose.ui.platform.LocalContext.current
    val pickPhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) vm.onCoverPicked(uri)
    }
    // Camera still capture via TakePicture → FileProvider (CameraX preview deferred;
    // still-image capture is the cover path iOS uses most often).
    var cameraUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { ok ->
        if (ok) cameraUri?.let { vm.onCoverPicked(it) }
    }
    val requestCamera = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            val file = java.io.File(context.cacheDir, "artist-wizard/camera-${java.util.UUID.randomUUID()}.jpg").also {
                it.parentFile?.mkdirs()
            }
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            cameraUri = uri
            takePicture.launch(uri)
        }
    }
    Text(
        "Shoot or pick a cover photo, or choose a gradient fallback. Upload runs after you go live.",
        style = AppTheme.type.body,
        color = AppTheme.colors.ink2,
    )
    Spacer(Modifier.height(space.md))
    PrimaryButton(
        text = if (state.pendingCover != null) "Change cover photo" else "Choose cover photo",
        onClick = { pickPhoto.launch("image/*") },
        fullWidth = true,
    )
    Spacer(Modifier.height(space.sm))
    PrimaryButton(
        text = "Take photo",
        onClick = {
            requestCamera.launch(android.Manifest.permission.CAMERA)
        },
        variant = ButtonVariant.Ghost,
        fullWidth = true,
    )
    if (state.pendingCover != null) {
        Spacer(Modifier.height(space.sm))
        Text(
            "Cover ready — uploads after Publish.",
            style = AppTheme.type.footnote,
            color = AppTheme.colors.brand,
            modifier = Modifier.clickable { vm.clearCoverPick() },
        )
    }
    Spacer(Modifier.height(space.lg))
    Text("Gradient fallback", style = AppTheme.type.caption, color = AppTheme.colors.ink3)
    Spacer(Modifier.height(space.sm))
    GradientPicker(selected = state.coverGradientIndex, onSelect = vm::onCoverGradientSelected)
}

@Composable
private fun SamplesStepContent(state: WizardUiState, vm: WizardViewModel) {
    val space = AppTheme.dimens.space
    val pickAudio = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            vm.onSamplePicked(uri, uri.lastPathSegment)
        }
    }
    Text(
        "Up to six audio clips via the system document picker. Uploads after Publish.",
        style = AppTheme.type.body,
        color = AppTheme.colors.ink2,
    )
    Spacer(Modifier.height(space.md))
    PrimaryButton(
        text = "Add audio sample",
        onClick = {
            if (state.pendingSamples.size < 6) {
                pickAudio.launch(arrayOf("audio/*"))
            }
        },
        enabled = state.pendingSamples.size < 6,
        fullWidth = true,
    )
    Spacer(Modifier.height(space.md))
    state.pendingSamples.forEach { sample ->
        Row(
            Modifier.fillMaxWidth().padding(vertical = space.xs),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(sample.title, style = AppTheme.type.callout, color = AppTheme.colors.ink)
            Text(
                "Remove",
                style = AppTheme.type.footnote,
                color = AppTheme.colors.hot,
                modifier = Modifier.clickable { vm.removeSample(sample.fileName) },
            )
        }
    }
    if (state.pendingSamples.isEmpty()) {
        Text("No samples yet — skip is fine.", style = AppTheme.type.footnote, color = AppTheme.colors.ink3)
    }
}

@Composable
private fun SocialsStepContent(state: WizardUiState, vm: WizardViewModel) {
    val space = AppTheme.dimens.space
    SignupInputRow("Instagram handle", state.instagramHandle, vm::onInstagramChanged)
    Spacer(Modifier.height(space.lg))
    SignupInputRow("Spotify artist URL", state.spotifyArtistUrl, vm::onSpotifyChanged)
    Spacer(Modifier.height(space.lg))
    SignupInputRow("YouTube channel URL", state.youtubeChannelUrl, vm::onYoutubeChanged)
}

@Composable
private fun BioStepContent(state: WizardUiState, vm: WizardViewModel) {
    SignupInputRow("Bio", state.bio, vm::onBioChanged)
    Spacer(Modifier.height(AppTheme.dimens.space.sm))
    Text(
        "${state.bio.length}/200",
        style = AppTheme.type.footnote,
        color = AppTheme.colors.ink3,
    )
}

@Composable
private fun GradientPicker(onSelect: (Int) -> Unit, selected: Int = 0) {
    val space = AppTheme.dimens.space
    Row(horizontalArrangement = Arrangement.spacedBy(space.sm)) {
        (0..5).forEach { index ->
            Box(
                Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(AppTheme.dimens.radii.sm))
                    .background(Brush.linearGradient(ArtistGradient.palette(index)))
                    .then(
                        if (index == selected) {
                            Modifier.border(
                                2.dp,
                                AppTheme.colors.brand,
                                RoundedCornerShape(AppTheme.dimens.radii.sm),
                            )
                        } else {
                            Modifier
                        },
                    )
                    .clickable { onSelect(index) },
            )
        }
    }
}

@Composable
private fun PreviewStepContent(state: WizardUiState) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Box(
        Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(AppTheme.dimens.radii.md))
            .background(Brush.linearGradient(ArtistGradient.palette(state.coverGradientIndex))),
    )
    Spacer(Modifier.height(space.lg))
    Text(state.stageName, style = AppTheme.type.headline, color = colors.ink)
    Text("@${state.handle} · ${state.category}", style = AppTheme.type.caption, color = colors.ink3)
    Text(state.baseCity, style = AppTheme.type.footnote, color = colors.ink2)
    Spacer(Modifier.height(space.md))
    HRule()
    Spacer(Modifier.height(space.md))
    state.previewPackages.forEach { pkg ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(pkg.name, style = AppTheme.type.callout, color = colors.ink)
                Text(pkg.duration, style = AppTheme.type.footnote, color = colors.ink3)
            }
            Text(formatInr(pkg.price), style = AppTheme.type.monoMedium, color = colors.ink)
        }
    }
    if (state.techItems.isNotEmpty()) {
        Spacer(Modifier.height(space.md))
        Text("Tech", style = AppTheme.type.caption, color = colors.ink3)
        Text(state.techItems.joinToString(", "), style = AppTheme.type.footnote, color = colors.ink2)
    }
    if (state.pendingCover != null || state.pendingSamples.isNotEmpty()) {
        Spacer(Modifier.height(space.md))
        Text(
            buildString {
                if (state.pendingCover != null) append("Cover photo queued. ")
                if (state.pendingSamples.isNotEmpty()) {
                    append("${state.pendingSamples.size} sample(s) queued.")
                }
            },
            style = AppTheme.type.footnote,
            color = colors.brand,
        )
    }
    if (state.bio.isNotBlank()) {
        Spacer(Modifier.height(space.md))
        Text(state.bio, style = AppTheme.type.body, color = colors.ink2)
    }
}

@Composable
private fun DoneStepContent(stageName: String) {
    Text(
        "Nice work, $stageName. Your profile is published — head to Home to watch for requests.",
        style = AppTheme.type.body,
        color = AppTheme.colors.ink2,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryChips(selected: String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(AppTheme.dimens.space.sm)) {
        WizardCategories.forEach { cat ->
            SelectableChip(cat, cat == selected) { onSelect(cat) }
        }
    }
}

@Composable
private fun SelectableChip(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Pill(text = label, tone = PillTone.Brand, modifier = Modifier.clickable(onClick = onClick))
    } else {
        Text(
            label,
            style = AppTheme.type.caption,
            color = AppTheme.colors.ink2,
            modifier = Modifier
                .clip(CircleShape)
                .background(AppTheme.colors.bgSoft)
                .clickable(onClick = onClick)
                .padding(horizontal = AppTheme.dimens.space.md, vertical = AppTheme.dimens.space.xs),
        )
    }
}
