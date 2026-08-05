package `in`.artistant.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import `in`.artistant.app.designsystem.component.AmbientRoleWash
import `in`.artistant.app.designsystem.component.FloatingTabAction
import `in`.artistant.app.designsystem.component.FloatingTabBar
import `in`.artistant.app.designsystem.component.FloatingTabItem
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.hilt.navigation.compose.hiltViewModel
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.motion
import `in`.artistant.app.designsystem.theme.reduceMotion
import `in`.artistant.app.feature.artist.ArtistProfileScreen
import `in`.artistant.app.feature.booking.BookingDetailScreen
import `in`.artistant.app.feature.booking.BookingScreen
import `in`.artistant.app.feature.booking.CheckoutScreen
import `in`.artistant.app.feature.booking.ConfirmedScreen
import `in`.artistant.app.feature.booking.RequestQuoteScreen
import `in`.artistant.app.feature.bookings.BookingsScreen
import `in`.artistant.app.feature.discover.DiscoverScreen
import `in`.artistant.app.feature.messages.ChatOpenViewModel
import `in`.artistant.app.feature.messages.ChatScreen
import `in`.artistant.app.feature.messages.MessagesScreen
import `in`.artistant.app.feature.search.SearchScreen
import `in`.artistant.app.designsystem.theme.AppRole
import `in`.artistant.app.feature.profile.ArtistListScreen
import `in`.artistant.app.feature.profile.ProfileScreen
import `in`.artistant.app.feature.paywall.PaywallScreen

/**
 * Client navigation: four peer destinations in the floating pill, plus Search.
 *
 * Search is intentionally NOT one of the four. It renders as the detached circle
 * beside the pill because it is an action on the catalogue rather than a place
 * you go — the same reason Discover no longer carries an inline search field.
 * It stays in this enum only so the deep-link router and `showBottomBar` can
 * still treat it as a top-level route.
 */
private enum class ClientTab(val route: String, val label: String, val icon: ImageVector) {
    Discover("discover", "Discover", Icons.Filled.Explore),
    Bookings("bookings", "Bookings", Icons.Filled.CalendarMonth),
    Messages("messages", "Messages", Icons.Filled.Chat),
    Profile("profile", "Profile", Icons.Filled.Person),
    Search("search", "Search", Icons.Filled.Search),
}

/** The four that live inside the pill, in bar order. */
private val PILL_TABS = listOf(
    ClientTab.Discover, ClientTab.Bookings, ClientTab.Messages, ClientTab.Profile,
)

private const val ARTIST_PROFILE_ROUTE = "artist/{artistId}"

@Composable
fun ClientTabsScaffold() {
    val nav = rememberNavController()
    val current by nav.currentBackStackEntryAsState()
    val route = current?.destination?.route
    val showBottomBar = ClientTab.entries.any { it.route == route }
    val tabRouter = rememberTabRouter()
    val pendingThread by tabRouter.pendingThreadId.collectAsStateWithLifecycle()
    val pendingBooking by tabRouter.pendingBookingDetail.collectAsStateWithLifecycle()
    val clientTab by tabRouter.clientTab.collectAsStateWithLifecycle()

    // Push deep links: flip tab then push the detail/chat route.
    LaunchedEffect(clientTab) {
        val tabRoute = when (clientTab) {
            ClientDeepTab.Discover -> ClientTab.Discover.route
            ClientDeepTab.Bookings -> ClientTab.Bookings.route
            ClientDeepTab.Messages -> ClientTab.Messages.route
            ClientDeepTab.Profile -> ClientTab.Profile.route
            ClientDeepTab.Search -> ClientTab.Search.route
        }
        navigateToTab(nav, tabRoute)
    }
    LaunchedEffect(pendingThread) {
        val id = tabRouter.consumePendingThread() ?: return@LaunchedEffect
        navigateToTab(nav, ClientTab.Messages.route)
        nav.navigate(ClientNavRoutes.chat(id))
    }
    LaunchedEffect(pendingBooking) {
        val id = tabRouter.consumePendingBookingDetail() ?: return@LaunchedEffect
        navigateToTab(nav, ClientTab.Bookings.route)
        nav.navigate(ClientNavRoutes.bookingDetail(id))
    }

    val selectedTabRoute = ClientTab.entries
        .firstOrNull { tab -> current?.destination?.hierarchy?.any { it.route == tab.route } == true }
        ?.route

    // Graph-wide motion, resolved here because Navigation's transition slots are
    // non-composable lambdas and cannot read the theme themselves. Search counts
    // as a tab root for transition purposes even though it renders outside the
    // pill — moving to it is still a lateral move, not a push.
    val motion = AppTheme.motion
    val reduceMotion = AppTheme.reduceMotion
    val tabRoutes = remember { ClientTab.entries.map { it.route }.toSet() }

    Scaffold(
        // Transparent so the ambient wash below shows through. The wash paints
        // the background colour itself, so nothing is lost.
        containerColor = Color.Transparent,
        modifier = Modifier.ambientBackdrop(),
        bottomBar = {
            if (!showBottomBar) return@Scaffold
            FloatingTabBar(
                items = remember {
                    PILL_TABS.map { FloatingTabItem(it.route, it.label, it.icon) }
                },
                selectedRoute = selectedTabRoute,
                onSelect = { route -> navigateToTab(nav, route) },
                trailing = FloatingTabAction(
                    route = ClientTab.Search.route,
                    label = ClientTab.Search.label,
                    icon = ClientTab.Search.icon,
                    selected = selectedTabRoute == ClientTab.Search.route,
                    onClick = { navigateToTab(nav, ClientTab.Search.route) },
                ),
            )
        },
    ) { inner ->
        NavHost(
            navController = nav,
            startDestination = ClientTab.Discover.route,
            modifier = Modifier.fillMaxSize(),
            enterTransition = navEnter(motion, reduceMotion, tabRoutes),
            exitTransition = navExit(motion, reduceMotion, tabRoutes),
            popEnterTransition = navPopEnter(motion, reduceMotion, tabRoutes),
            popExitTransition = navPopExit(motion, reduceMotion, tabRoutes),
        ) {
            // Discover is the ONE full-bleed destination: its hero photo runs
            // under the status bar and its rails scroll behind the floating tab
            // bar, so it takes no scaffold insets and reserves its own tailroom.
            // Every other destination gets the normal inset pane via [TabPane].
            composable(ClientTab.Discover.route) {
                DiscoverScreen(onArtistClick = { id -> nav.navigate("artist/$id") })
            }
            composable(ClientTab.Bookings.route) {
                TabPane(inner) {
                    BookingsScreen(
                        onBookingClick = { id -> nav.navigate(ClientNavRoutes.bookingDetail(id)) },
                    )
                }
            }
            composable(ClientTab.Messages.route) {
                TabPane(inner) {
                    MessagesScreen(onThreadClick = { id -> nav.navigate(ClientNavRoutes.chat(id)) })
                }
            }
            composable(ClientTab.Profile.route) {
                TabPane(inner) {
                    ProfileScreen(
                        onNavigateToPaywall = { nav.navigate(ClientNavRoutes.PAYWALL) },
                        onArtistList = { kind -> nav.navigate(ClientNavRoutes.artistList(kind.raw)) },
                    )
                }
            }
            composable(
                route = ClientNavRoutes.ARTIST_LIST,
                arguments = listOf(navArgument("kind") { type = NavType.StringType }),
            ) {
                TabPane(inner) {
                    ArtistListScreen(
                        onBack = { nav.popBackStack() },
                        onArtistClick = { id -> nav.navigate("artist/$id") },
                        onBookingClick = { id -> nav.navigate(ClientNavRoutes.bookingDetail(id)) },
                    )
                }
            }
            composable(ClientNavRoutes.PAYWALL) {
                TabPane(inner) {
                    PaywallScreen(
                        role = AppRole.Client,
                        onClose = { nav.popBackStack() },
                    )
                }
            }
            composable(ClientTab.Search.route) {
                TabPane(inner) {
                    SearchScreen(onArtistClick = { id -> nav.navigate("artist/$id") })
                }
            }
            composable(
                route = ARTIST_PROFILE_ROUTE,
                arguments = listOf(navArgument("artistId") { type = NavType.StringType }),
            ) {
                val chatOpen: ChatOpenViewModel = hiltViewModel()
                TabPane(inner) {
                    ArtistProfileScreen(
                        onBack = { nav.popBackStack() },
                        onBook = { artistId -> nav.navigate(ClientNavRoutes.bookingCompose(artistId)) },
                        onRequestQuote = { artistId -> nav.navigate(ClientNavRoutes.requestQuote(artistId)) },
                        onMessage = { artistId ->
                            chatOpen.open(artistId, bookingId = null) { threadId ->
                                nav.navigate(ClientNavRoutes.chat(threadId))
                            }
                        },
                    )
                }
            }
            composable(
                route = ClientNavRoutes.CHAT,
                arguments = listOf(navArgument("threadId") { type = NavType.StringType }),
            ) {
                TabPane(inner) {
                    ChatScreen(
                        onBack = { nav.popBackStack() },
                        onBookingClick = { id -> nav.navigate(ClientNavRoutes.bookingDetail(id)) },
                    )
                }
            }
            composable(
                route = ClientNavRoutes.BOOKING,
                arguments = listOf(navArgument("artistId") { type = NavType.StringType }),
            ) {
                TabPane(inner) {
                    BookingScreen(
                        onBack = { nav.popBackStack() },
                        onContinue = { nav.navigate(ClientNavRoutes.CHECKOUT) },
                    )
                }
            }
            composable(ClientNavRoutes.CHECKOUT) {
                TabPane(inner) {
                    CheckoutScreen(
                        onBack = { nav.popBackStack() },
                        onConfirmed = { bookingId ->
                            nav.navigate(ClientNavRoutes.confirmed(bookingId)) {
                                popUpTo(ClientNavRoutes.CHECKOUT) { inclusive = true }
                            }
                        },
                        onPaywall = { nav.navigate(ClientNavRoutes.PAYWALL) },
                    )
                }
            }
            composable(
                route = ClientNavRoutes.CONFIRMED,
                arguments = listOf(navArgument("bookingId") { type = NavType.StringType }),
            ) { entry ->
                val bookingId = entry.arguments?.getString("bookingId").orEmpty()
                TabPane(inner) {
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
            }
            composable(
                route = ClientNavRoutes.BOOKING_DETAIL,
                arguments = listOf(navArgument("bookingId") { type = NavType.StringType }),
            ) {
                TabPane(inner) {
                    BookingDetailScreen(
                        isArtistViewer = false,
                        onBack = { nav.popBackStack() },
                        onOpenChat = { threadId -> nav.navigate(ClientNavRoutes.chat(threadId)) },
                    )
                }
            }
            composable(
                route = ClientNavRoutes.REQUEST_QUOTE,
                arguments = listOf(navArgument("artistId") { type = NavType.StringType }),
            ) {
                TabPane(inner) {
                    RequestQuoteScreen(
                        onBack = { nav.popBackStack() },
                        onSuccess = { nav.popBackStack() },
                    )
                }
            }
        }
    }
}
