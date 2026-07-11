package `in`.artistant.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import `in`.artistant.app.designsystem.theme.AppRole
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.feature.artist.ArtistRouteLoader
import `in`.artistant.app.feature.booking.BookingScreen
import `in`.artistant.app.feature.booking.CheckoutScreen
import `in`.artistant.app.feature.booking.ConfirmedScreen
import `in`.artistant.app.feature.booking.RequestQuoteScreen
import `in`.artistant.app.feature.bookings.BookingDetailScreen
import `in`.artistant.app.feature.bookings.BookingsScreen
import `in`.artistant.app.feature.discover.DiscoverScreen
import `in`.artistant.app.feature.messages.ChatOpenViewModel
import `in`.artistant.app.feature.messages.ChatScreen
import `in`.artistant.app.feature.messages.MessagesScreen
import `in`.artistant.app.feature.paywall.PaywallScreen
import `in`.artistant.app.feature.profile.ProfileScreen
import `in`.artistant.app.feature.search.SearchScreen
import `in`.artistant.app.core.config.AppEnvironment
import `in`.artistant.app.ui.Placeholder

// Client bottom nav: Discover · Bookings · Messages · Profile · Search.
// (Search is a normal 5th destination — Android has no iOS-26 search-circle.)
private enum class ClientTab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Discover("discover", "Discover", Icons.Filled.Explore),
    Bookings("bookings", "Bookings", Icons.Filled.CalendarMonth),
    Messages("messages", "Messages", Icons.AutoMirrored.Filled.Chat),
    Profile("profile", "Profile", Icons.Filled.Person),
    Search("search", "Search", Icons.Filled.Search),
}

@Composable
fun ClientTabsScaffold() {
    val nav = rememberNavController()

    // Push deep-link: a parked tab selection for the CLIENT side switches the bottom tab before
    // the inner screen consumes its id. Ignore (and don't consume) a target for the other role —
    // the artist scaffold isn't even composed here, so this is defensive. Consume after switching
    // so a re-composition can't re-switch.
    val tabDeepLink: TabDeepLinkViewModel = hiltViewModel()
    val pendingTab by tabDeepLink.pendingTab.collectAsStateWithLifecycle()
    LaunchedEffect(pendingTab) {
        pendingTab?.takeIf { it.role == AppRole.Client }?.let { target ->
            navigateToTab(nav, target.route)
            tabDeepLink.consumePendingTab()
        }
    }

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
            composable(ClientTab.Bookings.route) { BookingsTab() }
            // M4: Messages is a real tab now.
            composable(ClientTab.Messages.route) { MessagesTab() }
            // M6: Profile hosts the calendar-sync toggle (full header/stats/actions M5+).
            composable(ClientTab.Profile.route) { ProfileScreen() }
        }
    }
}

/**
 * The client artist + booking-funnel + chat destinations, registered ONCE and reused
 * by every client tab that can reach them (Browse, Bookings, Messages). Extracting it
 * kills the per-tab copy-paste and puts the real Chat wiring + the find-or-create
 * "Message" entry point (was a `threadId = "pending"` stub) in a single place. [nav]
 * is the tab's own nested controller — pushes stay inside that tab's back stack.
 */
private fun NavGraphBuilder.clientArtistFunnel(nav: NavHostController) {
    composable<ClientRoute.ArtistProfile> {
        // Find-or-create the real thread on "Message", then push its chat.
        val chatOpen: ChatOpenViewModel = hiltViewModel()
        ArtistRouteLoader(
            onBack = { nav.popBackStack() },
            onBooking = { nav.navigate(ClientRoute.Booking(it)) },
            onRequestQuote = { nav.navigate(ClientRoute.RequestQuote(it)) },
            onMessage = { artistId ->
                chatOpen.open(artistId, bookingId = null) { tid ->
                    nav.navigate(ClientRoute.Chat(threadId = tid))
                }
            },
        )
        ChatOpeningOverlay(chatOpen)
    }
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
            // M7 dormant gate: routes to the paywall when subscriptionsEnabled + not entitled.
            onPaywall = { nav.navigate(ClientRoute.Paywall) },
        )
    }
    // M7 — the real paywall (dormant: only reachable via the flag-gated checkout gate). Sells
    // the client product; onComplete pops back to Checkout so the resumed Confirm now passes.
    composable<ClientRoute.Paywall> {
        PaywallScreen(
            productId = AppEnvironment.CLIENT_MONTHLY_PRODUCT_ID,
            onClose = { nav.popBackStack() },
            onComplete = { nav.popBackStack() },
        )
    }
    composable<ClientRoute.Confirmed> {
        ConfirmedScreen(
            onViewBooking = { id -> nav.navigate(ClientRoute.BookingDetail(id)) },
            // Unwind the funnel back to whatever this tab's root is.
            onBackToDiscover = { nav.popBackStack(nav.graph.findStartDestination().id, inclusive = false) },
        )
    }
    composable<ClientRoute.BookingDetail> { entry ->
        val bookingId = entry.toRoute<ClientRoute.BookingDetail>().bookingId
        val chatOpen: ChatOpenViewModel = hiltViewModel()
        BookingDetailScreen(
            onBack = { nav.popBackStack() },
            // Message from a booking carries the booking id so the thread is the
            // booking's thread (not the bookingless inquiry).
            onMessage = { artistId ->
                chatOpen.open(artistId, bookingId = bookingId) { tid ->
                    nav.navigate(ClientRoute.Chat(threadId = tid))
                }
            },
        )
        ChatOpeningOverlay(chatOpen)
    }
    composable<ClientRoute.RequestQuote> {
        RequestQuoteScreen(onBack = { nav.popBackStack() }, onDone = { nav.popBackStack() })
    }
    composable<ClientRoute.Chat> { entry ->
        val threadId = entry.toRoute<ClientRoute.Chat>().threadId
        ChatScreen(
            role = AppRole.Client,
            onBack = { nav.popBackStack() },
            // Client viewer: the chat's avatar opens the artist profile in-tab.
            onArtist = { artistId -> nav.navigate(ClientRoute.ArtistProfile(artistId)) },
        )
    }
    composable<ClientRoute.ScoreExplainer> { Placeholder("Bookability Score") }
}

/** Full-screen blocking spinner while find-or-create resolves the chat thread. */
@Composable
private fun ChatOpeningOverlay(chatOpen: ChatOpenViewModel) {
    val opening by chatOpen.opening.collectAsStateWithLifecycle()
    if (opening) {
        Box(
            Modifier.fillMaxSize().background(AppTheme.colors.bg.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = AppTheme.colors.brand)
        }
    }
}

/** A Browse tab shell — a nested [NavHost] whose root is [root] (Discover or Search). */
@Composable
private fun BrowseTab(root: @Composable (onArtist: (String) -> Unit) -> Unit) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = BrowseRoot) {
        composable<BrowseRoot> {
            root { id -> nav.navigate(ClientRoute.ArtistProfile(id)) }
        }
        clientArtistFunnel(nav)
    }
}

/** Client Bookings tab — month calendar; tapping a day's booking pushes its detail. */
@Composable
private fun BookingsTab() {
    val nav = rememberNavController()
    // Deep-stack push fix: force to root while a booking id is parked so BookingsScreen's consumer
    // runs even if a saved deep BookingDetail was restored (see DeepLinkRouter).
    val deepLink: TabDeepLinkViewModel = hiltViewModel()
    val pendingBooking by deepLink.pendingBookingId.collectAsStateWithLifecycle()
    ForceRootForDeepLink(nav, pendingBooking)
    NavHost(navController = nav, startDestination = BookingsRoot) {
        composable<BookingsRoot> {
            BookingsScreen(onOpenBooking = { nav.navigate(ClientRoute.BookingDetail(it)) })
        }
        clientArtistFunnel(nav)
    }
}

/** Client Messages tab — thread list; tapping a thread pushes its chat. */
@Composable
private fun MessagesTab() {
    val nav = rememberNavController()
    // Deep-stack push fix: force to root while a message thread id is parked so MessagesScreen's
    // consumer runs even if a saved deep Chat was restored (see DeepLinkRouter).
    val deepLink: TabDeepLinkViewModel = hiltViewModel()
    val pendingThread by deepLink.pendingThreadId.collectAsStateWithLifecycle()
    ForceRootForDeepLink(nav, pendingThread)
    NavHost(navController = nav, startDestination = MessagesRoot) {
        composable<MessagesRoot> {
            MessagesScreen(
                role = AppRole.Client,
                onOpenThread = { nav.navigate(ClientRoute.Chat(threadId = it)) },
            )
        }
        clientArtistFunnel(nav)
    }
}

/** The nested-graph root marker for a Browse tab (Discover / Search content). */
@kotlinx.serialization.Serializable
private data object BrowseRoot

/** The nested-graph root marker for the Bookings tab. */
@kotlinx.serialization.Serializable
private data object BookingsRoot

/** The nested-graph root marker for the Messages tab. */
@kotlinx.serialization.Serializable
private data object MessagesRoot
