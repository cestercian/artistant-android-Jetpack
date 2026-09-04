package `in`.artistant.app.feature.system

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.artistant.app.designsystem.component.BackHeader
import `in`.artistant.app.designsystem.component.EyebrowLabel
import `in`.artistant.app.designsystem.component.SearchBar
import `in`.artistant.app.designsystem.component.SegmentedControl
import `in`.artistant.app.designsystem.component.EmptyState
import `in`.artistant.app.designsystem.component.hairlineBottom
import `in`.artistant.app.designsystem.theme.AppRole
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.ArtistantTheme
import `in`.artistant.app.designsystem.theme.motion
import `in`.artistant.app.designsystem.theme.motionTween

/**
 * Screen 63 — Help.
 *
 * Three things in the design's order, and the order is the design:
 *
 *  1. **Anything blocking the user, promoted above everything else.** That is
 *     the screen's note verbatim. A help centre that makes somebody search for
 *     the answer to a problem the app already knows they have is a help centre
 *     that has decided its FAQ matters more than its user. Exactly one card, and
 *     only when it is real — see [outstandingHelpItem].
 *  2. **The FAQ set switches by audience.** Not by role: a client running a
 *     venue may well want to read what the artist side is told, and hiding it
 *     would be the app deciding what they are allowed to know. Role only picks
 *     the opening segment.
 *  3. **Search**, over both the questions and the answers, ranked so a matching
 *     question wins ([helpArticles]).
 *
 * Answers expand in place. The design draws a chevron, which means a pushed
 * article on iOS — and there is no article store, on the server or in the
 * binary. An accordion shows the answer that exists rather than pushing a screen
 * that does not.
 */
@Composable
fun HelpCentreScreen(
    role: AppRole,
    onBack: () -> Unit,
    onFixProfile: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HelpCentreViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens

    LaunchedEffect(role) { viewModel.start(role) }

    Column(
        modifier
            .fillMaxSize()
            .background(colors.page)
            .padding(horizontal = dimens.component.gutter),
    ) {
        BackHeader(title = "Help", subtitle = state.greeting, onBack = onBack)

        Column(Modifier.verticalScroll(rememberScrollState())) {
            SearchBar(
                value = state.query,
                onValueChange = viewModel::setQuery,
                hint = "Search help articles",
                onClear = { viewModel.setQuery("") }.takeIf { state.query.isNotEmpty() },
                modifier = Modifier.padding(top = dimens.space.lg),
            )

            SegmentedControl(
                options = HelpAudience.entries,
                selected = state.audience,
                onSelect = viewModel::setAudience,
                label = { it.label },
                modifier = Modifier.padding(top = dimens.space.md),
            )

            state.outstanding?.let { action ->
                OutstandingCard(
                    action = action,
                    onFix = onFixProfile,
                    modifier = Modifier.padding(top = dimens.space.lg),
                )
            }

            if (state.articles.isEmpty()) {
                EmptyState(
                    title = "No answer for that",
                    body = "Try a different word, or send us the question — we read " +
                        "everything.",
                    modifier = Modifier.padding(top = dimens.space.xl),
                )
            } else {
                Column(Modifier.padding(top = dimens.space.lg)) {
                    state.articles.forEach { article ->
                        FaqRow(
                            article = article,
                            expanded = article.question in state.expanded,
                            onToggle = { viewModel.toggle(article.question) },
                        )
                    }
                }
            }
            Box(Modifier.size(dimens.size.listTailroom))
        }
    }
}

/**
 * The promoted blocking item.
 *
 * Accent-washed with a NEAR-BLACK action pill, which is the one place in a
 * content block the design puts a dark fill: the card is already spending the
 * screen's accent on itself, so the button inside it cannot also be lime without
 * disappearing into its own background.
 */
@Composable
private fun OutstandingCard(
    action: HelpAction,
    onFix: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val shape = RoundedCornerShape(dimens.radii.lg)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.accent.copy(alpha = CARD_FILL))
            .padding(dimens.space.lg),
    ) {
        EyebrowLabel("Action required", color = colors.accentDeep)
        Row(
            Modifier.padding(top = dimens.space.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = action.title,
                    style = AppTheme.type.rowTitle.copy(fontWeight = FontWeight.Bold),
                    color = colors.ink,
                )
                Text(
                    text = action.detail,
                    style = AppTheme.type.caption.copy(fontWeight = FontWeight.Normal),
                    color = colors.ink2,
                    modifier = Modifier.padding(top = dimens.space.xs),
                )
            }
            Text(
                text = action.actionLabel,
                style = AppTheme.type.subtitle.copy(fontWeight = FontWeight.Bold),
                color = colors.onDark,
                modifier = Modifier
                    .clip(RoundedCornerShape(dimens.radii.md))
                    .background(colors.ink)
                    .clickable(role = Role.Button, onClick = onFix)
                    .padding(
                        horizontal = dimens.space.md,
                        vertical = dimens.space.sm,
                    ),
            )
        }
    }
}

@Composable
private fun FaqRow(article: HelpArticle, expanded: Boolean, onToggle: () -> Unit) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val rotation by animateFloatAsState(
        targetValue = if (expanded) CHEVRON_OPEN_DEGREES else 0f,
        animationSpec = motionTween(AppTheme.motion.indicator),
        label = "faqChevron",
    )
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onToggle)
            .hairlineBottom()
            .padding(vertical = dimens.space.md),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
        ) {
            Text(
                text = article.question,
                style = AppTheme.type.body.copy(fontWeight = FontWeight.SemiBold),
                color = colors.ink,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.lineStrong,
                modifier = Modifier
                    .size(dimens.size.iconLg)
                    .rotate(rotation),
            )
        }
        AnimatedVisibility(visible = expanded) {
            Text(
                text = article.answer,
                style = AppTheme.type.body,
                color = colors.ink3,
                modifier = Modifier.padding(top = dimens.space.sm, end = dimens.space.xxl),
            )
        }
    }
}

/** The accent wash behind the promoted card — the same 28% the design draws. */
private const val CARD_FILL = 0.28f

/** A chevron pointing right becomes one pointing down. */
private const val CHEVRON_OPEN_DEGREES = 90f

@Preview(showBackground = true, backgroundColor = 0xFFFAFAF6)
@Composable
private fun HelpPreview() {
    ArtistantTheme {
        Column(Modifier.padding(AppTheme.dimens.component.gutter)) {
            OutstandingCard(
                action = HelpAction(
                    title = "Add your name",
                    detail = "Artists see it when you send a request",
                ),
                onFix = {},
            )
            FaqRow(
                article = HelpContent.articles.first(),
                expanded = true,
                onToggle = {},
            )
        }
    }
}
