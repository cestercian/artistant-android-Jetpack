package `in`.artistant.app.feature.epk

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import `in`.artistant.app.common.util.formatInr
import `in`.artistant.app.data.model.Artist
import `in`.artistant.app.designsystem.component.ButtonVariant
import `in`.artistant.app.designsystem.component.EmptyState
import `in`.artistant.app.designsystem.component.HRule
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.feature.signup.SignupInputRow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * EPK editor — packages / tech / links / samples writable (iOS EPKView parity).
 * Cover reorder stays wizard-side; gallery pick for samples via SAF.
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
    val space = AppTheme.dimens.space
    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.onSamplePicked(uri, uri.lastPathSegment)
    }

    PullToRefreshBox(
        isRefreshing = state.isLoading && state.artist != null,
        onRefresh = viewModel::refresh,
        modifier = modifier.fillMaxSize().background(colors.bg),
    ) {
        when {
            state.isLoading && state.artist == null && state.error == null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.brand)
                }
            }
            state.error != null && state.artist == null -> {
                EmptyState(
                    title = "EPK unavailable",
                    body = state.error,
                    actionLabel = "Retry",
                    onAction = viewModel::refresh,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            else -> {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(space.lg),
                ) {
                    Text("EPK", style = AppTheme.type.displaySub, color = colors.ink)
                    Spacer(Modifier.height(space.sm))
                    if (!state.setupComplete) {
                        Text(
                            "Finish your profile so clients can book you.",
                            style = AppTheme.type.footnote,
                            color = colors.warm,
                        )
                        Spacer(Modifier.height(space.md))
                        PrimaryButton(text = "Finish your profile", onClick = onEditInWizard, fullWidth = true)
                        Spacer(Modifier.height(space.lg))
                    }
                    state.message?.let {
                        Text(it, style = AppTheme.type.footnote, color = colors.brand)
                        Spacer(Modifier.height(space.sm))
                    }
                    state.error?.let {
                        Text(it, style = AppTheme.type.footnote, color = colors.hot)
                        Spacer(Modifier.height(space.sm))
                    }
                    val artist = state.artist
                    if (artist != null) {
                        EpkHero(artist)
                        Spacer(Modifier.height(space.lg))
                        EpkSection(title = "Bio") {
                            Text(
                                artist.bio.ifBlank { "No bio yet — edit in wizard." },
                                style = AppTheme.type.body,
                                color = if (artist.bio.isBlank()) colors.ink3 else colors.ink2,
                            )
                        }
                        EpkSection(title = "Packages") {
                            if (state.editingPackages) {
                                SignupInputRow("Name", state.packageName, viewModel::onPackageName)
                                Spacer(Modifier.height(space.sm))
                                SignupInputRow("Duration", state.packageDuration, viewModel::onPackageDuration)
                                Spacer(Modifier.height(space.sm))
                                SignupInputRow("Price (INR)", state.packagePrice, viewModel::onPackagePrice)
                                Spacer(Modifier.height(space.md))
                                PrimaryButton(
                                    text = if (state.isSaving) "Saving…" else "Save package",
                                    onClick = viewModel::savePackages,
                                    enabled = !state.isSaving,
                                    fullWidth = true,
                                )
                            } else {
                                if (artist.packages.isEmpty()) {
                                    Text("No packages yet.", style = AppTheme.type.footnote, color = colors.ink3)
                                } else {
                                    artist.packages.forEach { pkg ->
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Column {
                                                Text(pkg.name, style = AppTheme.type.callout, color = colors.ink)
                                                Text(pkg.duration, style = AppTheme.type.footnote, color = colors.ink3)
                                            }
                                            Text(formatInr(pkg.price), style = AppTheme.type.monoMedium, color = colors.ink)
                                        }
                                    }
                                }
                                Spacer(Modifier.height(space.sm))
                                Text(
                                    "Edit packages",
                                    style = AppTheme.type.footnote,
                                    color = colors.brand,
                                    modifier = Modifier.clickable(onClick = viewModel::toggleEditingPackages),
                                )
                            }
                        }
                        EpkSection(title = "Tech rider") {
                            if (artist.tech.isEmpty()) {
                                Text("No tech items yet.", style = AppTheme.type.footnote, color = colors.ink3)
                            } else {
                                artist.tech.forEach { item ->
                                    Row(
                                        Modifier.fillMaxWidth().padding(vertical = space.xs),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(item, style = AppTheme.type.body, color = colors.ink2)
                                        Text(
                                            "Remove",
                                            style = AppTheme.type.footnote,
                                            color = colors.hot,
                                            modifier = Modifier.clickable { viewModel.removeTechItem(item) },
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(space.sm))
                            SignupInputRow("Add item", state.techDraft, viewModel::onTechDraft)
                            Spacer(Modifier.height(space.sm))
                            PrimaryButton(text = "Add", onClick = viewModel::addTechItem, variant = ButtonVariant.Ghost)
                        }
                        EpkSection(title = "Samples") {
                            if (artist.samples.isEmpty()) {
                                Text("No samples yet.", style = AppTheme.type.footnote, color = colors.ink3)
                            } else {
                                artist.samples.forEach { sample ->
                                    Row(
                                        Modifier.fillMaxWidth().padding(vertical = space.xs),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Column {
                                            Text(sample.title, style = AppTheme.type.callout, color = colors.ink)
                                            Text(sample.duration, style = AppTheme.type.footnote, color = colors.ink3)
                                        }
                                        Text(
                                            "Delete",
                                            style = AppTheme.type.footnote,
                                            color = colors.hot,
                                            modifier = Modifier.clickable { viewModel.deleteSample(sample) },
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(space.sm))
                            PrimaryButton(
                                text = "Add audio sample",
                                onClick = { pickAudio.launch(arrayOf("audio/*")) },
                                variant = ButtonVariant.Ghost,
                                fullWidth = true,
                            )
                        }
                        EpkSection(title = "Links") {
                            state.links.forEach { link ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = space.xs),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(link.label, style = AppTheme.type.callout, color = colors.ink)
                                        Text(link.url, style = AppTheme.type.footnote, color = colors.ink3)
                                    }
                                    Text(
                                        "Delete",
                                        style = AppTheme.type.footnote,
                                        color = colors.hot,
                                        modifier = Modifier.clickable { viewModel.deleteLink(link.id) },
                                    )
                                }
                            }
                            SignupInputRow("Label", state.linkLabel, viewModel::onLinkLabel)
                            Spacer(Modifier.height(space.sm))
                            SignupInputRow("URL", state.linkUrl, viewModel::onLinkUrl)
                            Spacer(Modifier.height(space.sm))
                            PrimaryButton(text = "Add link", onClick = viewModel::addLink, variant = ButtonVariant.Ghost)
                        }
                        Spacer(Modifier.height(space.lg))
                        PrimaryButton(
                            text = "Full edit in wizard",
                            onClick = onEditInWizard,
                            variant = ButtonVariant.Ghost,
                            fullWidth = true,
                        )
                    } else if (state.setupComplete) {
                        Text(
                            "Your EPK will appear here after you publish from the wizard.",
                            style = AppTheme.type.body,
                            color = colors.ink3,
                        )
                        Spacer(Modifier.height(space.lg))
                        PrimaryButton(text = "Open wizard", onClick = onEditInWizard, fullWidth = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun EpkHero(artist: Artist) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Box(
        Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clip(RoundedCornerShape(AppTheme.dimens.radii.md))
            .background(Brush.linearGradient(artist.gradient)),
        contentAlignment = Alignment.BottomStart,
    ) {
        Column(Modifier.padding(space.lg)) {
            Text(artist.name, style = AppTheme.type.headline, color = colors.ink)
            Text("@${artist.handle} · ${artist.category}", style = AppTheme.type.caption, color = colors.ink3)
            Text(artist.city, style = AppTheme.type.footnote, color = colors.ink2)
        }
    }
}

@Composable
private fun EpkSection(title: String, content: @Composable () -> Unit) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Text(title, style = AppTheme.type.headline, color = colors.ink)
    Spacer(Modifier.height(space.sm))
    content()
    Spacer(Modifier.height(space.lg))
    HRule()
    Spacer(Modifier.height(space.lg))
}
