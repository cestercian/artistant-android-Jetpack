package `in`.artistant.app.feature.profile

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import `in`.artistant.app.data.model.Artist
import `in`.artistant.app.designsystem.component.EmptyState
import `in`.artistant.app.designsystem.component.HRule
import `in`.artistant.app.designsystem.component.Pill
import `in`.artistant.app.designsystem.theme.AppTheme

/**
 * Profile-stat destination — port of iOS `ArtistListView`.
 * Saved rows open the artist; booking/completed rows open the booking detail.
 */
@Composable
fun ArtistListScreen(
    onBack: () -> Unit,
    onArtistClick: (artistId: String) -> Unit,
    onBookingClick: (bookingId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ArtistListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val kind = state.kind

    Column(modifier.fillMaxSize().background(colors.bg)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.ink)
            }
            Text(kind.title, style = AppTheme.type.headline, color = colors.ink)
        }

        when {
            state.isLoading && state.rows.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.brand)
                }
            }
            state.error != null && state.rows.isEmpty() -> {
                EmptyState(
                    title = "Couldn't load",
                    body = state.error,
                    actionLabel = "Retry",
                    onAction = viewModel::refresh,
                )
            }
            else -> {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = space.xl),
                ) {
                    Text(
                        kind.kicker(state.rows.size),
                        style = AppTheme.type.monoSmall.copy(fontWeight = FontWeight.Bold),
                        color = colors.ink3,
                    )
                    Spacer(Modifier.height(space.sm))
                    Text(kind.headline, style = AppTheme.type.displaySub, color = colors.ink)
                    Spacer(Modifier.height(space.sm))
                    Text(kind.sub, style = AppTheme.type.footnote, color = colors.ink2)
                    Spacer(Modifier.height(space.xl))

                    if (state.rows.isEmpty()) {
                        EmptyState(title = kind.emptyTitle, body = kind.emptySub)
                    } else {
                        state.rows.forEach { row ->
                            ArtistListRowUi(
                                row = row,
                                onClick = {
                                    when {
                                        row.bookingId != null -> onBookingClick(row.bookingId)
                                        row.artistId != null -> onArtistClick(row.artistId)
                                    }
                                },
                            )
                            HRule()
                        }
                        Spacer(Modifier.height(space.lg))
                        Text(kind.footer, style = AppTheme.type.monoSmall, color = colors.ink3)
                        Spacer(Modifier.height(space.xxl))
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistListRowUi(
    row: ArtistListRow,
    onClick: () -> Unit,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val artist = row.artist
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = space.md),
        horizontalArrangement = Arrangement.spacedBy(space.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtistThumb(artist)
        Column(Modifier.weight(1f)) {
            Text(
                artist?.name ?: row.fallbackTitle,
                style = AppTheme.type.body.copy(fontWeight = FontWeight.SemiBold),
                color = colors.ink,
                maxLines = 1,
            )
            artist?.let {
                Text(
                    "${it.genre} · ${it.city}",
                    style = AppTheme.type.footnote,
                    color = colors.ink3,
                    maxLines = 1,
                )
            }
            if (row.pills.isNotEmpty()) {
                Spacer(Modifier.height(space.xs))
                Row(horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                    row.pills.forEach { (text, tone) ->
                        Pill(text, tone = tone)
                    }
                }
            }
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.ink3,
        )
    }
}

@Composable
private fun ArtistThumb(artist: Artist?) {
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(AppTheme.dimens.radii.sm)
    Box(
        Modifier
            .width(62.dp)
            .height(78.dp)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    artist?.gradient?.takeIf { it.isNotEmpty() }
                        ?: listOf(colors.bgCard, colors.bgSoft),
                ),
            ),
    ) {
        artist?.coverUrl?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
