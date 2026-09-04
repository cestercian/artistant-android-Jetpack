package `in`.artistant.app.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import `in`.artistant.app.data.model.SearchCatalog
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.component.SheetScaffold
import `in`.artistant.app.designsystem.rememberHaptics
import `in`.artistant.app.designsystem.theme.AppTheme

/** The design's `rgba(20,21,15,.4)` scrim, expressed on the ink token. */
private const val SCRIM_ALPHA = 0.4f

/** See `SearchFilterSheet`'s SHEET_FRACTION — this sheet starts a little lower. */
private const val SHEET_FRACTION = 0.79f

/**
 * Compare by service (screen 53).
 *
 * **Radio, not checkbox**, and the design's note gives the reason: comparing
 * means one lens at a time. The implementation reason is the same shape — the
 * RPC's `p_services` is an ARRAY OVERLAP test, so two selected services return
 * artists offering either, which widens the feed. A control called "compare"
 * that broadens what you are looking at is worse than useless.
 *
 * The design puts an act count beside every service. There is none to put:
 * `search_facets` publishes counts per category and per city and for nothing
 * else, and `service_tags` has no facet at all. The rows carry the name alone,
 * and the button carries the only count this screen actually knows — how many
 * acts the current search returns.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareByServiceSheet(
    state: SearchUiState,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
    onApply: () -> Unit,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val selected = state.services.firstOrNull()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        contentColor = colors.ink,
        scrimColor = colors.ink.copy(alpha = SCRIM_ALPHA),
        dragHandle = null,
    ) {
        SheetScaffold(
            modifier = Modifier
                .fillMaxHeight(SHEET_FRACTION)
                .semantics { testTag = "sheet.compareByService" },
        ) {
            SheetHeaderRow(
                leadingLabel = "Clear",
                leadingEnabled = selected != null,
                title = "Compare by service",
                onLeading = { onSelect(null) },
                onClose = onDismiss,
            )
            Text(
                text = "Narrow the whole feed to one service type. " +
                    "Pick one to see only acts who offer it.",
                style = AppTheme.type.subtitle,
                color = colors.ink2,
                modifier = Modifier.padding(top = dimens.space.md),
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(top = dimens.space.md)
                    .verticalScroll(rememberScrollState()),
            ) {
                SearchCatalog.services.forEach { (slug, label) ->
                    ServiceRow(
                        label = label,
                        selected = slug == selected,
                        // Tapping the live row clears it. Without that a radio
                        // list is a one-way door: once any service is picked the
                        // only way back to "all services" is the header's Clear,
                        // which reads as "clear the sheet", not "clear this row".
                        onClick = { onSelect(if (slug == selected) null else slug) },
                    )
                }
            }
            SheetRule()
            PrimaryButton(
                text = searchApplyLabel(
                    resultCount = state.results.size,
                    hasActiveQuery = state.hasActiveQuery,
                    isLoading = state.isLoading,
                    canLoadMore = state.canLoadMore,
                ),
                onClick = onApply,
                fullWidth = true,
                modifier = Modifier.padding(top = dimens.space.lg),
            )
        }
    }
}

/** One service: an accent-filled tick when chosen, a hairline ring when not. */
@Composable
private fun ServiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val haptics = rememberHaptics()
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.RadioButton) {
                    haptics.tap()
                    onClick()
                }
                .padding(vertical = dimens.space.lg)
                .semantics { contentDescription = if (selected) "$label, selected" else label },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
        ) {
            Box(
                Modifier
                    .size(dimens.size.radio)
                    .clip(CircleShape)
                    .then(
                        if (selected) {
                            Modifier.background(colors.accent)
                        } else {
                            Modifier.border(dimens.size.strokeEmphasis, colors.lineStrong, CircleShape)
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = colors.onAccent,
                        modifier = Modifier.size(dimens.size.iconSm),
                    )
                }
            }
            Text(
                text = label,
                style = AppTheme.type.body.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                ),
                color = colors.ink,
                modifier = Modifier.weight(1f),
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(dimens.size.hairline)
                .background(colors.hairline),
        )
    }
}
