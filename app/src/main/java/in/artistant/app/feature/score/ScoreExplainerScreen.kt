package `in`.artistant.app.feature.score

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.artistant.app.data.model.ScoreBreakdown
import `in`.artistant.app.designsystem.component.HRule
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.component.ScoreRing
import `in`.artistant.app.designsystem.theme.AppTheme

/** One weighted metric row (label · weight% + value + clamped bar). */
private data class Metric(val name: String, val weight: Int, val value: Int)

/** The 5 weighted metrics from the breakdown; Cancellations is flipped (100 − rate). */
private fun metricsOf(b: ScoreBreakdown): List<Metric> = listOf(
    Metric("Show-up rate", 30, clamp(b.showUpRate)),
    Metric("Reviews", 25, clamp(b.reviewScore)),
    Metric("Reply speed", 20, clamp(b.replySpeed)),
    Metric("Cancellations", 15, clamp(100 - b.cancellationRate)),
    Metric("Social proof", 10, clamp(b.socialProof)),
)

private fun clamp(v: Int) = v.coerceIn(0, 100)

/**
 * Score Explainer (port of iOS `ScoreExplainerView`). Centered ring + body, a
 * hairline-row tiers table, and the weighted "what goes into it" rows. A failed
 * breakdown fetch shows an explicit retryable error, never a fake zero.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoreExplainerScreen(
    onBack: () -> Unit,
    viewModel: ScoreExplainerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    var showHistory by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = colors.bg,
        topBar = {
            TopAppBar(
                title = { Text("Bookability Score™", style = AppTheme.type.headline, color = colors.ink) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.ink)
                    }
                },
                actions = {
                    IconButton(onClick = { showHistory = true }) {
                        Icon(Icons.AutoMirrored.Filled.ShowChart, contentDescription = "View history", tint = colors.ink)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bg),
            )
        },
    ) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState()).padding(horizontal = space.xl),
        ) {
            when (val s = state) {
                ScoreExplainerUiState.Loading, is ScoreExplainerUiState.Loaded -> {
                    val breakdown = (s as? ScoreExplainerUiState.Loaded)?.breakdown ?: ScoreBreakdown.newArtist
                    Hero(breakdown)
                    TiersSection()
                    MetricsSection(breakdown)
                    Spacer(Modifier.height(space.xxl))
                }
                ScoreExplainerUiState.Error -> ErrorState(onRetry = viewModel::load)
            }
        }
    }

    if (showHistory) ScoreHistorySheet(onDismiss = { showHistory = false })
}

@Composable
private fun Hero(breakdown: ScoreBreakdown) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Column(
        Modifier.fillMaxWidth().padding(vertical = space.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(space.lg),
    ) {
        ScoreRing(value = breakdown.numericScore, size = 120.dp, stroke = 7.dp, showLabel = false)
        Text(
            "A trust score combining reply speed, show-up rate, reviews, social proof, and cancellations.",
            style = AppTheme.type.callout,
            color = colors.ink2,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TiersSection() {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    // (label, range, dot color) — bands match ScoreBands.
    val tiers = listOf(
        Triple("New", "0–60", colors.ink4),
        Triple("Rising", "60–75", colors.warm),
        Triple("Trusted", "75–90", colors.good),
        Triple("Elite", "90+", colors.brand),
    )
    Column(Modifier.padding(top = space.md), verticalArrangement = Arrangement.spacedBy(space.md)) {
        Text("TIERS", style = AppTheme.type.caption.copy(fontWeight = FontWeight.Bold), color = colors.ink3)
        Column {
            HRule()
            tiers.forEach { (label, range, c) ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = space.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(space.sm),
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(c))
                    Text(label, style = AppTheme.type.callout.copy(fontWeight = FontWeight.SemiBold), color = c, modifier = Modifier.weight(1f))
                    Text(range, style = AppTheme.type.monoSmall, color = colors.ink3)
                }
                HRule()
            }
        }
    }
}

@Composable
private fun MetricsSection(breakdown: ScoreBreakdown) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Column(Modifier.padding(top = space.xl), verticalArrangement = Arrangement.spacedBy(space.md)) {
        Text("WHAT GOES INTO IT", style = AppTheme.type.caption.copy(fontWeight = FontWeight.Bold), color = colors.ink3)
        Column {
            HRule()
            metricsOf(breakdown).forEach { m ->
                MetricRow(m)
                HRule()
            }
        }
    }
}

@Composable
private fun MetricRow(m: Metric) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Column(Modifier.padding(vertical = space.md), verticalArrangement = Arrangement.spacedBy(space.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(m.name, style = AppTheme.type.footnote.copy(fontWeight = FontWeight.SemiBold), color = colors.ink)
            Spacer(Modifier.size(4.dp))
            Text("· ${m.weight}%", style = AppTheme.type.footnote, color = colors.ink3, modifier = Modifier.weight(1f))
            Text("${m.value}", style = AppTheme.type.monoSmall.copy(fontWeight = FontWeight.Bold), color = colors.ink)
        }
        Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(colors.bgSoft)) {
            Box(Modifier.fillMaxWidth(m.value / 100f).height(3.dp).clip(CircleShape).background(colors.brand))
        }
    }
}

@Composable
private fun ErrorState(onRetry: () -> Unit) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Column(
        Modifier.fillMaxWidth().padding(vertical = space.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(space.lg),
    ) {
        Text("Couldn't load your score", style = AppTheme.type.displaySmall, color = colors.ink)
        Text(
            "We hit a problem fetching your Bookability breakdown — this isn't your real score.",
            style = AppTheme.type.callout,
            color = colors.ink2,
            textAlign = TextAlign.Center,
        )
        PrimaryButton(text = "Retry", onClick = onRetry)
    }
}
