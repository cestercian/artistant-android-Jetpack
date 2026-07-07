package `in`.artistant.app.feature.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.component.Skeleton
import `in`.artistant.app.designsystem.theme.AppTheme

/**
 * Resolves a `ClientRoute.ArtistProfile(id)` to the profile screen (iOS
 * `ArtistRouteLoader`): a skeleton while [ArtistProfileViewModel] hydrates the
 * full artist, then the screen, or a not-found / error surface. An already-cached
 * artist resolves without a network round-trip so the skeleton is momentary.
 */
@Composable
fun ArtistRouteLoader(
    onBack: () -> Unit,
    onBooking: (String) -> Unit,
    onRequestQuote: (String) -> Unit,
    onMessage: (String) -> Unit,
    viewModel: ArtistProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val savedIds by viewModel.saved.ids.collectAsStateWithLifecycle()

    when (val s = state) {
        ArtistProfileUiState.Loading -> LoadingSkeleton()
        ArtistProfileUiState.NotFound -> NotFound(onBack)
        is ArtistProfileUiState.Error -> ErrorState(s.message, viewModel::load, onBack)
        is ArtistProfileUiState.Loaded -> ArtistProfileScreen(
            loaded = s,
            isSaved = savedIds.contains(s.artist.id),
            onBack = onBack,
            onSave = viewModel::toggleSaved,
            onBooking = onBooking,
            onRequestQuote = onRequestQuote,
            onMessage = onMessage,
        )
    }
}

@Composable
private fun LoadingSkeleton() {
    val space = AppTheme.dimens.space
    Column(Modifier.fillMaxSize().background(AppTheme.colors.bg), verticalArrangement = Arrangement.spacedBy(space.lg)) {
        Skeleton(Modifier.fillMaxWidth().height(320.dp), cornerRadius = AppTheme.dimens.radii.lg)
        Skeleton(Modifier.fillMaxWidth().height(84.dp).padding(horizontal = space.xl))
        Skeleton(Modifier.fillMaxWidth().height(200.dp).padding(horizontal = space.xl))
    }
}

@Composable
private fun NotFound(onBack: () -> Unit) {
    val space = AppTheme.dimens.space
    Column(
        Modifier.fillMaxSize().background(AppTheme.colors.bg).padding(space.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Artist not found", style = AppTheme.type.displaySmall, color = AppTheme.colors.ink)
        Spacer(Modifier.height(space.sm))
        Text(
            "This artist may have been unpublished. Head back to Discover to see who's available.",
            style = AppTheme.type.footnote,
            color = AppTheme.colors.ink3,
        )
        Spacer(Modifier.height(space.lg))
        PrimaryButton(text = "Back", onClick = onBack)
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit, onBack: () -> Unit) {
    val space = AppTheme.dimens.space
    Column(
        Modifier.fillMaxSize().background(AppTheme.colors.bg).padding(space.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Couldn't load this artist", style = AppTheme.type.displaySmall, color = AppTheme.colors.ink)
        Spacer(Modifier.height(space.sm))
        Text(message, style = AppTheme.type.footnote, color = AppTheme.colors.ink3)
        Spacer(Modifier.height(space.lg))
        PrimaryButton(text = "Retry", onClick = onRetry)
    }
}
