package `in`.artistant.app.feature.system

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import `in`.artistant.app.designsystem.component.AccentNote
import `in`.artistant.app.designsystem.component.BackHeader
import `in`.artistant.app.designsystem.component.BottomActionBar
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.ArtistantTheme

/** Where the operator publishes incident detail. Shown as a link, never fetched. */
private const val STATUS_PAGE = "status.artistant.in"

/**
 * Screen 121 — "Artistant is down".
 *
 * The design's note: *owns the failure*. Three things do that work and all three
 * are load-bearing copy rather than decoration —
 *
 *  - **"This is us, not you."** Without it the user's first move is to check
 *    their own connection, reinstall, or sign out, and signing out during an
 *    outage is how a bad hour becomes a lost account.
 *  - **A scoped impact line.** "Bookings and messages affected" tells a host
 *    whose gig is tomorrow whether this is their problem. An unscoped outage
 *    screen sends everybody to support to ask the same question.
 *  - **"Confirmed bookings are unaffected."** The one thing a marketplace user
 *    is actually afraid of during an outage is that the date they agreed has
 *    evaporated. It has not; the row is in the database.
 *
 * Unlike screen 120 this one has a way out — the design draws a back control —
 * because the app's cached surfaces still work and trapping someone behind a
 * screen that promises to "clear itself" is worse than letting them look. See
 * [SystemGate.resolve].
 */
@Composable
fun ServiceOutageScreen(
    impact: String,
    startedLabel: String?,
    onCheckAgain: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    checking: Boolean = false,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val context = LocalContext.current

    Column(
        modifier
            .fillMaxSize()
            .background(colors.page),
    ) {
        Column(
            Modifier
                .weight(1f)
                .statusBarsPadding()
                .padding(horizontal = dimens.component.gutter),
        ) {
            // No title. The screen says its own name in 23sp two thirds of the
            // way down; repeating it in the bar would be the only thing on this
            // page said twice.
            BackHeader(title = "", onBack = onBack, backLabel = "Back to the app")

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    Modifier
                        .size(dimens.component.emptyGlyphCircle)
                        .clip(CircleShape)
                        .background(colors.surface3),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CloudOff,
                        contentDescription = null,
                        tint = colors.ink3,
                        modifier = Modifier.size(dimens.component.emptyGlyph),
                    )
                }
                Text(
                    text = "Artistant is down",
                    style = AppTheme.type.displaySmall,
                    color = colors.ink,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = dimens.space.xl),
                )
                Text(
                    text = "This is us, not you. We're working on it and this screen " +
                        "will clear itself.",
                    style = AppTheme.type.body,
                    color = colors.ink4,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(top = dimens.space.md)
                        .widthIn(max = dimens.component.readingMeasure),
                )

                Column(
                    Modifier
                        .padding(top = dimens.space.xl)
                        .widthIn(max = dimens.component.emptyActionWidth)
                        .clip(RoundedCornerShape(dimens.radii.lg))
                        .background(colors.surface3)
                        .padding(dimens.space.lg),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(dimens.space.sm),
                    ) {
                        Box(
                            Modifier
                                .size(dimens.dashboard.bannerDot)
                                .clip(CircleShape)
                                // Warm, not danger: an outage is a wait, and a
                                // red dot on this screen reads as "your booking
                                // is broken", which is the exact fear the note
                                // below exists to answer.
                                .background(colors.warm),
                        )
                        Text(
                            text = impact,
                            style = AppTheme.type.rowTitle.copy(fontWeight = FontWeight.Bold),
                            color = colors.ink,
                        )
                    }
                    if (!startedLabel.isNullOrBlank()) {
                        Text(
                            text = startedLabel,
                            style = AppTheme.type.caption.copy(fontWeight = FontWeight.Normal),
                            color = colors.ink2,
                            modifier = Modifier.padding(top = dimens.space.sm),
                        )
                    }
                }
            }

            AccentNote(
                text = "Confirmed bookings are unaffected — the artist still has your date. " +
                    "Nothing you already agreed is at risk.",
                modifier = Modifier.padding(bottom = dimens.space.sm),
            )
        }

        BottomActionBar {
            PrimaryButton(
                text = if (checking) "Checking…" else "Check again",
                onClick = onCheckAgain,
                fullWidth = true,
                enabled = !checking,
            )
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = STATUS_PAGE,
                    style = AppTheme.type.subtitle.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.accentInk,
                    modifier = Modifier
                        .clickable(role = Role.Button) {
                            AppStore.openUrl(context, "https://$STATUS_PAGE")
                        }
                        .padding(dimens.space.xs),
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFAF6)
@Composable
private fun ServiceOutagePreview() {
    ArtistantTheme {
        ServiceOutageScreen(
            impact = "Bookings and messages affected",
            startedLabel = "Started 9:22 am · last checked just now",
            onCheckAgain = {},
            onBack = {},
        )
    }
}
