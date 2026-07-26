package `in`.artistant.app.feature.epk

import androidx.compose.foundation.background
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
import `in`.artistant.app.designsystem.component.EmptyState
import `in`.artistant.app.designsystem.component.HRule
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.component.ButtonVariant
import `in`.artistant.app.designsystem.theme.AppTheme
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Read-only EPK editor shell — port of iOS `EPKView` scaffold. Shows the signed-in
 * artist's published row; inline edits and media management land in a later M5 pass.
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
                        PrimaryButton(
                            text = "Finish your profile",
                            onClick = onEditInWizard,
                            fullWidth = true,
                        )
                        Spacer(Modifier.height(space.lg))
                    }
                    val artist = state.artist
                    if (artist != null) {
                        EpkHero(artist)
                        Spacer(Modifier.height(space.lg))
                        EpkSection(title = "Bio") {
                            Text(
                                artist.bio.ifBlank { "No bio yet." },
                                style = AppTheme.type.body,
                                color = if (artist.bio.isBlank()) colors.ink3 else colors.ink2,
                            )
                        }
                        EpkSection(title = "Packages") {
                            if (artist.packages.isEmpty()) {
                                Text(
                                    "No packages on server yet — wizard draft not persisted to packages table.",
                                    style = AppTheme.type.footnote,
                                    color = colors.ink3,
                                )
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
                        }
                        EpkSection(title = "Tech rider") {
                            Text(
                                artist.tech.joinToString(", ").ifBlank { "No tech items yet." },
                                style = AppTheme.type.body,
                                color = if (artist.tech.isEmpty()) colors.ink3 else colors.ink2,
                            )
                        }
                        Spacer(Modifier.height(space.lg))
                        PrimaryButton(
                            text = "Edit in wizard",
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
                        PrimaryButton(
                            text = "Open wizard",
                            onClick = onEditInWizard,
                            fullWidth = true,
                        )
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
