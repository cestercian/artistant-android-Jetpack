package `in`.artistant.app.feature.signup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.ArtistantTheme

/**
 * Screen 01 — **the one dark room**.
 *
 * The only dark screen in the product. The designer's note is the whole brief for it: "splash
 * stays black so the app opens like a house light going down — everything after it is
 * daylight." Every other surface in the app is `surface` on `page`; this one is `darkest`, the
 * same value the launch window (`@color/splash_bg`) is painted in, so the hand-off from the
 * pre-Compose window to the first Compose frame has no seam in it.
 *
 * **It carries no actions.** The extracted markup draws "Get started" and "I already have an
 * account" on it, and those two buttons are real — they are just not this screen's. This is
 * what the app shows while [in.artistant.app.ui.RootViewModel] is still deciding which surface
 * the user belongs on (restoring a persisted session, fetching a profile), and a button that
 * cannot know yet whether the user even needs it is a button that lies. The moment the gate
 * answers, a signed-out user gets [WelcomeScreen] — screen 118, the same headline and the same
 * pair of CTAs, in daylight — and everyone else goes straight past it into the app.
 *
 * What it replaces is worse than what it is: the gate used to render an empty themed tree while
 * loading, which showed the bare window — a white flash between a black launch screen and
 * whatever came next.
 */
@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.darkest)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = dimens.space.xl)
            .padding(top = dimens.space.lg, bottom = dimens.space.xxl)
            .semantics { testTag = "splash" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Artistant",
            style = AppTheme.type.displayHero.copy(fontWeight = FontWeight.Black),
            color = colors.accent,
            modifier = Modifier.semantics { contentDescription = "Artistant" },
        )

        // The hero's slot. There is no bundled launch image and no server call has been made
        // yet, so this is the placeholder the design draws — a dark well under the same
        // bottom-up scrim every photo in the app wears — and not a stand-in photograph.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = dimens.space.xl)
                .clip(RoundedCornerShape(dimens.radii.xxl))
                .background(colors.dark),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            HERO_SCRIM_START to Color.Transparent,
                            1f to colors.mediaScrim,
                        ),
                    ),
            )
        }

        Spacer(Modifier.height(dimens.space.xl))
        Text(
            "Book the artist.\nMake the night.",
            style = AppTheme.type.displayHero,
            color = colors.onDark,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(dimens.space.md))
        Text(
            "Bands, DJs, comics and classical acts —\nfor brands, weddings and house shows.",
            style = AppTheme.type.body,
            color = colors.onDarkSoft,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(dimens.space.xl))
    }
}

/** The scrim starts just under halfway, which is where the design's gradient stop sits. */
private const val HERO_SCRIM_START = 0.48f

@Preview(showBackground = true, backgroundColor = 0xFF0F100C, heightDp = 760)
@Composable
private fun SplashPreview() {
    ArtistantTheme { SplashScreen() }
}
