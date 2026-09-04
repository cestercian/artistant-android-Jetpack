package `in`.artistant.app.feature.messages

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import `in`.artistant.app.designsystem.component.BackHeader
import `in`.artistant.app.designsystem.component.EyebrowLabel
import `in`.artistant.app.designsystem.component.ListRow
import `in`.artistant.app.designsystem.theme.AppTheme

/**
 * Trust & safety (design 131).
 *
 * **Three rules beat a policy page.** The whole screen is one dark card that
 * says what it is for, three numbered rules, and a list where every remedy is one
 * tap from the advice that mentions it. Nothing here links out to a web page: a
 * safety screen whose answer is "read our terms" is a safety screen nobody reads.
 *
 * Every row below leads to something this app can actually do. Rows whose
 * destination does not exist are not drawn — an "Emergency help · local numbers
 * by city" row is in the design, but the app has no per-city number list and no
 * table to hold one, so inventing three numbers would be the most dangerous
 * possible thing to fabricate. It is listed as a gap in the PR instead.
 */
@Composable
fun SafetyCentreScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** Straight into the inbox, where a conversation can be reported. */
    onReportConversation: () -> Unit = {},
    /** The blocked-accounts list, where a block can be undone. */
    onBlockedAccounts: () -> Unit = {},
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens

    Column(modifier.fillMaxSize().background(colors.page)) {
        BackHeader(
            title = "Trust & safety",
            onBack = onBack,
            modifier = Modifier.padding(horizontal = dimens.component.gutter),
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimens.component.gutter),
        ) {
            Spacer(Modifier.height(dimens.space.md))
            // The one dark object on a light screen. It carries the promise the
            // three rules below deliver on, and the darkness is what makes it
            // read as the screen's thesis rather than as another card.
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(dimens.radii.card))
                    .background(colors.ink)
                    .padding(dimens.space.lg),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
                ) {
                    Icon(
                        Icons.Filled.Shield,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(dimens.size.iconLg),
                    )
                    Text(
                        "Booking safely",
                        style = AppTheme.type.sectionTitle,
                        color = colors.onDark,
                    )
                }
                Spacer(Modifier.height(dimens.space.md))
                Text(
                    "Three rules that prevent almost every problem we see.",
                    style = AppTheme.type.subtitle,
                    color = colors.onDarkSoft,
                )
            }

            Spacer(Modifier.height(dimens.space.lg))
            SAFETY_RULES.forEachIndexed { index, rule ->
                SafetyRule(number = index + 1, title = rule.first, body = rule.second)
                Spacer(Modifier.height(dimens.space.md))
            }

            Spacer(Modifier.height(dimens.space.sm))
            EyebrowLabel("IF SOMETHING GOES WRONG")
            Spacer(Modifier.height(dimens.space.sm))
            ListRow(
                title = "Report a conversation",
                subtitle = "Goes to our safety team",
                onClick = onReportConversation,
                modifier = Modifier.semantics { testTag = "safety.reportConversation" },
            )
            ListRow(
                title = "Blocked accounts",
                subtitle = "Private — they aren't told, and you can undo it here",
                onClick = onBlockedAccounts,
                showHairline = false,
                modifier = Modifier.semantics { testTag = "safety.blocked" },
            )
            Spacer(Modifier.height(dimens.size.listTailroom))
        }
    }
}

/** One numbered rule: an accent tile with its ordinal, a title and two lines. */
@Composable
private fun SafetyRule(number: Int, title: String, body: String) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.radii.lg))
            .background(colors.surface3)
            .padding(dimens.space.md),
        horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
    ) {
        Box(
            Modifier
                .size(dimens.size.iconXl)
                .clip(RoundedCornerShape(dimens.radii.sm))
                .background(colors.accent),
            contentAlignment = Alignment.Center,
        ) {
            Text(number.toString(), style = AppTheme.type.badge, color = colors.onAccent)
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = AppTheme.type.rowTitle.copy(fontWeight = FontWeight.Bold),
                color = colors.ink,
            )
            Spacer(Modifier.height(dimens.space.xs))
            Text(body, style = AppTheme.type.subtitle, color = colors.ink2)
        }
    }
}

/**
 * The three rules, verbatim from design 131.
 *
 * A constant rather than three inline blocks so the copy is reviewable as copy,
 * and so nobody adds a fourth without noticing that "three rules" is in the
 * heading above them.
 */
private val SAFETY_RULES = listOf(
    "Keep it on Artistant" to
        "If someone moves you to WhatsApp before a booking is confirmed, we can't help you " +
        "if it goes wrong.",
    "Never pay in advance off-platform" to
        "No genuine artist needs a deposit wired before you've met or agreed terms in the app.",
    "Read the score, not the follower count" to
        "Bookability is built from what happened on real bookings. Followers can be bought.",
)
