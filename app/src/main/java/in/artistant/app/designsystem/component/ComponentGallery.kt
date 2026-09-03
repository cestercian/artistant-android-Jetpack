package `in`.artistant.app.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import `in`.artistant.app.designsystem.theme.AppRole
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.ArtistantTheme

/**
 * Every v2 primitive on one page — proof that the theme, the tokens and the
 * component library compile and lay out together.
 *
 * It renders identically under both roles now, and the two previews below keep
 * exercising both anyway. That is the point of them: the redesign made
 * `withRole` an identity, and if a second accent ever leaks back in, the pair
 * stops matching and someone notices here rather than on a device.
 */
@Composable
fun ComponentGallery() {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.page)
            .verticalScroll(rememberScrollState())
            .padding(dimens.component.gutter),
        verticalArrangement = Arrangement.spacedBy(dimens.space.lg),
    ) {
        ScreenHeader(
            title = "Components",
            subtitle = "Artistant iOS Light · one accent",
            trailing = {
                IconCircle(Icons.Filled.Notifications, "Notifications", onClick = {}, dot = true)
            },
        )

        SearchBarButton("Search artists, genres, cities", onClick = {})

        Row(horizontalArrangement = Arrangement.spacedBy(dimens.space.sm)) {
            Chip("For you", selected = true, onClick = {})
            Chip("Bands", selected = false, onClick = {})
            Chip("DJs", selected = false, onClick = {})
        }

        HeroCard(
            title = "The Tilt Collective",
            meta = "Indie folk band · 5 pc · Bengaluru",
            badge = "Top rated",
            rating = "4.92 (128)",
            price = "₹42,000",
            priceSuffix = "/ 90 min set",
            onToggleSave = {},
            onClick = {},
        )

        SectionHeader("Available Sat night", actionLabel = "See all", onAction = {})
        Row(horizontalArrangement = Arrangement.spacedBy(dimens.space.md)) {
            Tile("Kabir Sen", meta = "Techno DJ · ₹28,000", onClick = {}, modifier = Modifier.weight(1f))
            Tile("Ananya Rao", meta = "Stand-up · ₹35,000", onClick = {}, modifier = Modifier.weight(1f))
        }

        SectionHeader("Buttons")
        PrimaryButton("Send request", {}, fullWidth = true)
        SecondaryButton("Clear filters", {}, fullWidth = true)
        PrimaryButton("Get started", {}, fullWidth = true, enabled = false)

        SectionHeader("Status")
        Row(horizontalArrangement = Arrangement.spacedBy(dimens.space.sm)) {
            StatusPill("Available Fri", StatusTone.Live)
            StatusPill("Awaiting", StatusTone.Pending)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(dimens.space.sm)) {
            Pill("NEUTRAL")
            Pill("BRAND", tone = PillTone.Brand)
            Pill("ELITE", tone = PillTone.Good)
            Pill("BUSY", tone = PillTone.Hot)
        }

        SectionHeader("Notices")
        Banner(
            "Couldn't refresh your dashboard",
            BannerTone.Failure,
            detail = "Availability and requests may be stale.",
            actionLabel = "Retry",
            onAction = {},
        )
        Banner("Phone numbers stay hidden until you book", BannerTone.Info)
        Toast("Venue address copied")

        SectionHeader("Fields")
        AppTextField(value = "", onValueChange = {}, label = "Mobile number", hint = "+91 …")
        OtpField(value = "4729", onValueChange = {})

        SectionHeader("Rows")
        Column {
            ListRow("Saved artists", subtitle = "12 acts", onClick = {})
            ListRow("Notifications", onClick = {})
            ListRow("Sync gigs to calendar", value = "On", showHairline = false)
        }

        SectionHeader("Loading")
        SkeletonRail()

        SectionHeader("Empty")
        EmptyState(
            title = "No artists for this yet",
            body = "Nothing matches \"throat singing\" with your three filters on.",
            icon = Icons.Filled.SearchOff,
            actionLabel = "Notify me when one joins",
            onAction = {},
            secondaryLabel = "Clear filters",
            onSecondary = {},
        )

        SectionHeader("Chrome")
        CardView {
            Text("Card title", style = AppTheme.type.sectionTitle, color = colors.ink)
            Text("Hairline card, no chrome.", style = AppTheme.type.caption, color = colors.ink2)
        }
        Box {
            LightTabBar(
                items = listOf(
                    LightTabItem("discover", "Discover", Icons.Filled.Home),
                    LightTabItem("search", "Search", Icons.Filled.Search),
                    LightTabItem("messages", "Messages", Icons.Filled.ChatBubbleOutline),
                    LightTabItem("profile", "Profile", Icons.Filled.PersonOutline),
                ),
                selectedRoute = "discover",
                onSelect = {},
                action = LightTabAction("Find an artist", Icons.Filled.Add) {},
            )
        }
    }
}

@Preview(name = "Client", backgroundColor = 0xFFFAFAF6, showBackground = true, heightDp = 1900)
@Composable
private fun GalleryClientPreview() {
    ArtistantTheme(role = AppRole.Client) { ComponentGallery() }
}

@Preview(name = "Artist", backgroundColor = 0xFFFAFAF6, showBackground = true, heightDp = 1900)
@Composable
private fun GalleryArtistPreview() {
    ArtistantTheme(role = AppRole.Artist) { ComponentGallery() }
}
