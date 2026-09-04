package `in`.artistant.app.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import `in`.artistant.app.designsystem.component.hairlineTop
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.ArtistantTheme

/**
 * Shared chrome for the "Account & settings" section (design screens 25 / 47 / 48 / 49 / 69 /
 * 81 / 82 / 91 / 92 / 93 / 113 / 115 / 116 / 124 / 128 / 129 / 130).
 *
 * Every one of those is the same page: a header band, a body inset by the page gutter, and —
 * on the ones that end in a decision — a bar pinned to the bottom edge behind a hairline.
 * [AccountScaffold] is that shape.
 *
 * It lives in this feature package rather than in `designsystem/component/` even though
 * seventeen screens use it, for the reason `feature/signup` owns its near-identical
 * `SignupChrome`: a page scaffold is a SECTION's argument about how its pages are built, and
 * two sections that both put one in the shared library end up with two general scaffolds that
 * differ in ways no caller can see. If a third section wants this shape, that is the moment to
 * merge the two — not before.
 *
 * The phone bezel, notch and fake status bar in the extracted markup are design chrome and are
 * deliberately not drawn (REDESIGN_2026-09 §5.3); the real bars arrive as window insets.
 */
@Composable
fun AccountScaffold(
    modifier: Modifier = Modifier,
    header: @Composable (() -> Unit)? = null,
    /** The pinned bottom bar. Null on a screen whose last control scrolls with the body. */
    footer: @Composable (ColumnScope.() -> Unit)? = null,
    scrollable: Boolean = true,
    background: Color = AppTheme.colors.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    val dimens = AppTheme.dimens
    val gutter = dimens.component.gutter
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(background),
    ) {
        if (header != null) {
            Box(Modifier.fillMaxWidth().padding(horizontal = gutter, vertical = dimens.space.sm)) {
                header()
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(horizontal = gutter),
            content = content,
        )
        if (footer != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppTheme.colors.surface)
                    .hairlineTop()
                    // The bar owns the navigation-bar inset AND the keyboard inset: the window
                    // is edge-to-edge and does not resize for the IME, so without `imePadding`
                    // the delete screen's confirm button would sit under the keyboard the
                    // moment its "type DELETE" field takes focus.
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = gutter)
                    .padding(top = dimens.space.lg, bottom = dimens.space.xl),
                verticalArrangement = Arrangement.spacedBy(dimens.space.md),
                content = footer,
            )
        }
    }
}

/**
 * The big statement at the top of a body — "Get a copy of everything", "This can't be undone".
 *
 * 26/700 with the design's tight tracking, over an optional paragraph. Separate from the
 * header band above it because these screens carry BOTH: a small centred nav title that says
 * where you are, and a page title that says what this page is for.
 */
@Composable
fun AccountPageTitle(
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Column(modifier.fillMaxWidth()) {
        Text(title, style = AppTheme.type.screenTitle, color = colors.ink)
        if (!body.isNullOrBlank()) {
            Text(
                body,
                style = AppTheme.type.body,
                color = colors.ink4,
                modifier = Modifier.padding(top = dimens.space.sm),
            )
        }
    }
}

/** One column of [AccountStatBand]. A null [value] prints an em dash — see [accountStatValue]. */
data class AccountStat(val label: String, val value: String)

/**
 * The three-up counter band under an account header (screens 26 / 47 / 69).
 *
 * Fenced by hairlines top and bottom, which is what separates the counters from the identity
 * block above them: without the rule the numbers read as part of the name.
 *
 * `IntrinsicSize.Min` so the two dividers size themselves to the row rather than to a fixed
 * height — a hardcoded one is right for exactly one type ramp and stops short of the labels it
 * divides at every larger font scale.
 */
@Composable
fun AccountStatBand(stats: List<AccountStat>, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(dimens.size.hairline).background(colors.hairline))
        Row(
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(vertical = dimens.space.lg),
        ) {
            stats.forEachIndexed { index, stat ->
                if (index > 0) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .padding(vertical = dimens.space.sm)
                            .width(dimens.size.hairline)
                            .background(colors.hairline),
                    )
                }
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(dimens.space.xs),
                ) {
                    Text(stat.value, style = AppTheme.type.monoCount, color = colors.ink)
                    Text(
                        stat.label,
                        style = AppTheme.type.caption,
                        color = colors.ink4,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(dimens.size.hairline).background(colors.hairline))
    }
}

/**
 * What one counter prints.
 *
 * A count we HAVE is printed. A count we could not read prints an em dash, because "you have 0
 * bookings" and "we couldn't reach the server" are opposite claims and this band is the only
 * thing on the screen making either.
 *
 * A delegation rather than a second copy of `count?.toString() ?: "—"`: [profileStatValue] is
 * the rule, it has a test that pins the dash, and two independent spellings of one rule is how
 * the artist band and the client band end up disagreeing about what "unknown" looks like.
 */
fun accountStatValue(count: Int?): String = profileStatValue(count)

/** Vertical air between two blocks in an account body. */
@Composable
fun AccountGap(times: Int = 1) {
    Spacer(Modifier.height(AppTheme.dimens.space.lg * times))
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun AccountChromePreview() {
    ArtistantTheme {
        AccountScaffold {
            AccountGap()
            AccountPageTitle(
                "Get a copy of everything",
                body = "One JSON file, assembled on our side.",
            )
            AccountGap()
            AccountStatBand(
                listOf(
                    AccountStat("Gigs", accountStatValue(128)),
                    AccountStat("Bookability", accountStatValue(86)),
                    AccountStat("Completed", accountStatValue(null)),
                ),
            )
        }
    }
}
