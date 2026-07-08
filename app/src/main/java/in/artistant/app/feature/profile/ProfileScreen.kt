package `in`.artistant.app.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import `in`.artistant.app.designsystem.theme.AppTheme

/**
 * Client Profile tab (replaces the M0 Placeholder). Minimal for now — the full header /
 * stats / saved carousel / account actions are M5+ (see [ProfileViewModel]); this hosts the
 * Settings section, whose first row is the calendar-sync toggle ([CalendarSyncSection]).
 * When the full profile lands, drop CalendarSyncSection into its Settings block.
 */
@Composable
fun ProfileScreen(modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = space.xl, vertical = space.xl),
    ) {
        Text("Profile", style = AppTheme.type.displaySmall, color = colors.ink)
        Spacer(Modifier.height(space.xxl))
        Text(
            "SETTINGS",
            style = AppTheme.type.monoSmall.copy(fontWeight = FontWeight.Bold),
            color = colors.ink3,
        )
        Spacer(Modifier.height(space.lg))
        CalendarSyncSection()
    }
}
