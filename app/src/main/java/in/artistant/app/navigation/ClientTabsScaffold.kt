package `in`.artistant.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Chat
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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.feature.artist.ArtistProfileScreen
import `in`.artistant.app.feature.booking.BookingDetailScreen
import `in`.artistant.app.feature.booking.BookingScreen
import `in`.artistant.app.feature.booking.CheckoutScreen
import `in`.artistant.app.feature.booking.ConfirmedScreen
import `in`.artistant.app.feature.booking.RequestQuoteScreen
import `in`.artistant.app.feature.bookings.BookingsScreen
import `in`.artistant.app.feature.discover.DiscoverScreen
import `in`.artistant.app.feature.search.SearchScreen
import `in`.artistant.app.ui.Placeholder

// Client bottom nav: Discover · Bookings · Messages · Profile · Search.
private enum class ClientTab(val route: String, val label: String, val icon: ImageVector) {
    Discover("discover", "Discover", Icons.Filled.Explore),
    Bookings("bookings", "Bookings", Icons.Filled.CalendarMonth),
    Messages("messages", "Messages", Icons.Filled.Chat),
    Profile("profile", "Profile", Icons.Filled.Person),
    Search("search", "Search", Icons.Filled.Search),
}

private const val ARTIST_PROFILE_ROUTE = "artist/{artistId}"

@Composable
fun ClientTabsScaffold() {
    val nav = rememberNavController()
    val current by nav.currentBackStackEntryAsState()
    val route = current?.destination?.route
    val showBottomBar = ClientTab.entries.any { it.route == route }

    Scaffold(
        containerColor = AppTheme.colors.bg,
        bottomBar = {
            if (!showBottomBar) return@Scaffold
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
            composable(ClientTab.Discover.route) {
                DiscoverScreen(onArtistClick = { id -> nav.navigate("artist/$id") })
            }
            composable(ClientTab.Bookings.route) {
                BookingsScreen(
                    onBookingClick = { id -> nav.navigate(ClientNavRoutes.bookingDetail(id)) },
                )
            }
            composable(ClientTab.Messages.route) { Placeholder(ClientTab.Messages.label) }
            composable(ClientTab.Profile.route) { Placeholder(ClientTab.Profile.label) }
            composable(ClientTab.Search.route) {
                SearchScreen(onArtistClick = { id -> nav.navigate("artist/$id") })
            }
            composable(
                route = ARTIST_PROFILE_ROUTE,
                arguments = listOf(navArgument("artistId") { type = NavType.StringType }),
            ) {
                ArtistProfileScreen(
                    onBack = { nav.popBackStack() },
                    onBook = { artistId -> nav.navigate(ClientNavRoutes.bookingCompose(artistId)) },
                    onMessage = { /* deferred M4 */ },
                )
            }
            composable(
                route = ClientNavRoutes.BOOKING,
                arguments = listOf(navArgument("artistId") { type = NavType.StringType }),
            ) {
                BookingScreen(
                    onBack = { nav.popBackStack() },
                    onContinue = { nav.navigate(ClientNavRoutes.CHECKOUT) },
                )
            }
            composable(ClientNavRoutes.CHECKOUT) {
                CheckoutScreen(
                    onBack = { nav.popBackStack() },
                    onConfirmed = { bookingId ->
                        nav.navigate(ClientNavRoutes.confirmed(bookingId)) {
                            popUpTo(ClientNavRoutes.CHECKOUT) { inclusive = true }
                        }
                    },
                )
            }
            composable(
                route = ClientNavRoutes.CONFIRMED,
                arguments = listOf(navArgument("bookingId") { type = NavType.StringType }),
            ) { entry ->
                val bookingId = entry.arguments?.getString("bookingId").orEmpty()
                ConfirmedScreen(
                    bookingId = bookingId,
                    onViewBooking = { id ->
                        nav.navigate(ClientNavRoutes.bookingDetail(id)) {
                            popUpTo(ClientTab.Discover.route) { inclusive = false }
                        }
                    },
                    onBackToDiscover = {
                        nav.popBackStack(ClientTab.Discover.route, inclusive = false)
                    },
                )
            }
            composable(
                route = ClientNavRoutes.BOOKING_DETAIL,
                arguments = listOf(navArgument("bookingId") { type = NavType.StringType }),
            ) {
                BookingDetailScreen(
                    isArtistViewer = false,
                    onBack = { nav.popBackStack() },
                )
            }
            composable(
                route = ClientNavRoutes.REQUEST_QUOTE,
                arguments = listOf(navArgument("artistId") { type = NavType.StringType }),
            ) {
                RequestQuoteScreen(
                    onBack = { nav.popBackStack() },
                    onSuccess = { nav.popBackStack() },
                )
            }
        }
    }
}
