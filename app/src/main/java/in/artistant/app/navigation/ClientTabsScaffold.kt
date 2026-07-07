package `in`.artistant.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.feature.artist.ArtistRouteLoader
import `in`.artistant.app.feature.booking.BookingScreen
import `in`.artistant.app.feature.booking.CheckoutScreen
import `in`.artistant.app.feature.booking.ConfirmedScreen
import `in`.artistant.app.feature.booking.RequestQuoteScreen
import `in`.artistant.app.feature.bookings.BookingDetailScreen
import `in`.artistant.app.feature.bookings.BookingsScreen
import `in`.artistant.app.feature.discover.DiscoverScreen
import `in`.artistant.app.feature.search.SearchScreen
import `in`.artistant.app.ui.Placeholder

// Client bottom nav: Discover · Bookings · Messages · Profile · Search.
// (Search is a normal 5th destination — Android has no iOS-26 search-circle.)
private enum class ClientTab(val route: String, val label: String, val icon: ImageVector) {
    Discover("discover", "Discover", Icons.Filled.Explore),
    Bookings("bookings", "Bookings", Icons.Filled.CalendarMonth),
    Messages("messages", "Messages", Icons.AutoMirrored.Filled.Chat),
    Profile("profile", "Profile", Icons.Filled.Person),
    Search("search", "Search", Icons.Filled.Search),
}

@Composable
fun ClientTabsScaffold() {
    val nav = rememberNavController()
    Scaffold(
        containerColor = AppTheme.colors.bg,
        bottomBar = {
            val current by nav.currentBackStackEntryAsState()
            NavigationBar(containerColor = AppTheme.colors.bgElev) {
                ClientTab.entries.forEach { tab ->
                    val selected = current?.destination?.hierarchy?.any { it.route == tab.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = { navigateToTab(nav, tab.route) },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label, style = AppTheme.type.caption) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AppTheme.colors.brandInk,
                            indicatorColor = AppTheme.colors.brand,
                            unselectedIconColor = AppTheme.colors.ink3,
                            selectedTextColor = AppTheme.colors.ink,
                            unselectedTextColor = AppTheme.colors.ink3,
                        ),
                    )
                }
            }
        },
    ) { inner ->
        NavHost(
            navController = nav,
            startDestination = ClientTab.Discover.route,
            modifier = Modifier.padding(inner),
        ) {
            // Discover + Search are real Browse tabs, each with its own nested
            // stack so a tapped tile pushes ArtistProfile within that tab.
            composable(ClientTab.Discover.route) {
                // `nav` is the OUTER (bottom-nav) controller, so the masthead's
                // profile chip switches the selected tab — not a push inside the
                // Discover stack.
                BrowseTab { onArtist ->
                    DiscoverScreen(
                        onArtist = onArtist,
                        onProfile = { navigateToTab(nav, ClientTab.Profile.route) },
                    )
                }
            }
            composable(ClientTab.Search.route) {
                BrowseTab { onArtist -> SearchScreen(onArtist = onArtist) }
            }
            // Bookings is a real tab now (M3); Messages/Profile stay placeholders (M4+).
            composable(ClientTab.Bookings.route) { BookingsTab() }
            composable(ClientTab.Messages.route) { Placeholder(ClientTab.Messages.label) }
            composable(ClientTab.Profile.route) { Placeholder(ClientTab.Profile.label) }
        }
    }
}

/**
 * A Browse tab shell — a nested [NavHost] whose root is [root] (Discover or
 * Search), able to push [ClientRoute.ArtistProfile] and the stubbed booking /
 * quote / chat targets. [root] receives an `onArtist` that navigates into the
 * profile. Each tab keeps its own back stack.
 */
@Composable
private fun BrowseTab(root: @Composable (onArtist: (String) -> Unit) -> Unit) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = BrowseRoot) {
        composable<BrowseRoot> {
            root { id -> nav.navigate(ClientRoute.ArtistProfile(id)) }
        }
        composable<ClientRoute.ArtistProfile> {
            ArtistRouteLoader(
                onBack = { nav.popBackStack() },
                onBooking = { nav.navigate(ClientRoute.Booking(it)) },
                onRequestQuote = { nav.navigate(ClientRoute.RequestQuote(it)) },
                // Chat find-or-create is M4 — push the stub for now.
                onMessage = { nav.navigate(ClientRoute.Chat(threadId = "pending")) },
            )
        }
        // M3 booking funnel: Book → Checkout → Confirmed → (View booking) Detail.
        composable<ClientRoute.Booking> {
            BookingScreen(
                onBack = { nav.popBackStack() },
                onCheckout = { nav.navigate(ClientRoute.Checkout) },
            )
        }
        composable<ClientRoute.Checkout> {
            CheckoutScreen(
                onBack = { nav.popBackStack() },
                onConfirmed = { id -> nav.navigate(ClientRoute.Confirmed(id)) },
            )
        }
        composable<ClientRoute.Confirmed> {
            ConfirmedScreen(
                onViewBooking = { id -> nav.navigate(ClientRoute.BookingDetail(id)) },
                // Unwind the whole funnel back to the browse root.
                onBackToDiscover = { nav.popBackStack(BrowseRoot, inclusive = false) },
            )
        }
        composable<ClientRoute.BookingDetail> {
            BookingDetailScreen(
                onBack = { nav.popBackStack() },
                // Chat is M4 — push the stub.
                onMessage = { nav.navigate(ClientRoute.Chat(threadId = "pending")) },
            )
        }
        composable<ClientRoute.RequestQuote> {
            RequestQuoteScreen(onBack = { nav.popBackStack() }, onDone = { nav.popBackStack() })
        }
        composable<ClientRoute.Chat> { Placeholder("Chat — coming in M4") }
        composable<ClientRoute.ScoreExplainer> { Placeholder("Bookability Score") }
    }
}

/**
 * The client Bookings tab — a month calendar of the user's bookings with its own
 * nested stack so tapping a day's booking pushes its detail (M3).
 */
@Composable
private fun BookingsTab() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = BookingsRoot) {
        composable<BookingsRoot> {
            BookingsScreen(onOpenBooking = { nav.navigate(ClientRoute.BookingDetail(it)) })
        }
        composable<ClientRoute.BookingDetail> {
            BookingDetailScreen(
                onBack = { nav.popBackStack() },
                onMessage = { nav.navigate(ClientRoute.Chat(threadId = "pending")) },
            )
        }
        composable<ClientRoute.Chat> { Placeholder("Chat — coming in M4") }
    }
}

/** The nested-graph root marker for a Browse tab (Discover / Search content). */
@kotlinx.serialization.Serializable
private data object BrowseRoot

/** The nested-graph root marker for the Bookings tab. */
@kotlinx.serialization.Serializable
private data object BookingsRoot
