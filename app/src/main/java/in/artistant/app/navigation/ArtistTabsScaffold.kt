package `in`.artistant.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.WorkOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.feature.artisthome.ArtistHomeScreen
import `in`.artistant.app.feature.booking.BookingDetailScreen
import `in`.artistant.app.feature.gigs.ArtistGigsScreen
import `in`.artistant.app.feature.epk.EpkScreen
import `in`.artistant.app.feature.wizard.WizardScreen
import `in`.artistant.app.feature.gigs.GigRequestDetailScreen
import `in`.artistant.app.designsystem.theme.AppRole
import `in`.artistant.app.feature.messages.ChatScreen
import `in`.artistant.app.feature.messages.MessagesScreen
import `in`.artistant.app.feature.availability.ManageAvailabilityScreen
import `in`.artistant.app.feature.profile.ProfileScreen
import `in`.artistant.app.feature.score.ScoreExplainerScreen
import `in`.artistant.app.feature.paywall.PaywallScreen

// Artist bottom nav: Home · Gigs · Messages · EPK.
private enum class ArtistTab(val route: String, val label: String, val icon: ImageVector) {
    Home("home", "Home", Icons.Filled.Dashboard),
    Gigs("gigs", "Gigs", Icons.Filled.WorkOutline),
    Messages("messages", "Messages", Icons.Filled.Chat),
    Epk("epk", "EPK", Icons.Filled.LibraryMusic),
}

@Composable
fun ArtistTabsScaffold() {
    val nav = rememberNavController()
    val current by nav.currentBackStackEntryAsState()
    val route = current?.destination?.route
    val showBottomBar = ArtistTab.entries.any { it.route == route }
    val tabRouter = rememberTabRouter()
    val pendingThread by tabRouter.pendingThreadId.collectAsStateWithLifecycle()
    val pendingGig by tabRouter.pendingGigRequestId.collectAsStateWithLifecycle()
    val artistTab by tabRouter.artistTab.collectAsStateWithLifecycle()

    LaunchedEffect(artistTab) {
        val tabRoute = when (artistTab) {
            ArtistDeepTab.Home -> ArtistTab.Home.route
            ArtistDeepTab.Gigs -> ArtistTab.Gigs.route
            ArtistDeepTab.Messages -> ArtistTab.Messages.route
            ArtistDeepTab.Epk -> ArtistTab.Epk.route
        }
        navigateToTab(nav, tabRoute)
    }
    LaunchedEffect(pendingThread) {
        val id = tabRouter.consumePendingThread() ?: return@LaunchedEffect
        navigateToTab(nav, ArtistTab.Messages.route)
        nav.navigate(ArtistNavRoutes.chat(id))
    }
    LaunchedEffect(pendingGig) {
        val id = tabRouter.consumePendingGigRequest() ?: return@LaunchedEffect
        navigateToTab(nav, ArtistTab.Home.route)
        nav.navigate(ArtistNavRoutes.gigRequest(id))
    }

    Scaffold(
        containerColor = AppTheme.colors.bg,
        bottomBar = {
            if (!showBottomBar) return@Scaffold
            NavigationBar(containerColor = AppTheme.colors.bgElev) {
                ArtistTab.entries.forEach { tab ->
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
            startDestination = ArtistTab.Home.route,
            modifier = Modifier.padding(inner),
        ) {
            composable(ArtistTab.Home.route) {
                ArtistHomeScreen(
                    onBookingClick = { id -> nav.navigate(ArtistNavRoutes.bookingDetail(id)) },
                    onGigRequestClick = { id -> nav.navigate(ArtistNavRoutes.gigRequest(id)) },
                    onProfileClick = { nav.navigate(ArtistNavRoutes.PROFILE) },
                    onOpenWizard = { nav.navigate(ArtistNavRoutes.WIZARD) },
                    onScoreExplainer = { nav.navigate(ArtistNavRoutes.SCORE_EXPLAINER) },
                )
            }
            composable(ArtistTab.Gigs.route) {
                ArtistGigsScreen(
                    onBookingClick = { id -> nav.navigate(ArtistNavRoutes.bookingDetail(id)) },
                )
            }
            composable(ArtistNavRoutes.PROFILE) {
                ProfileScreen(
                    onBack = { nav.popBackStack() },
                    onNavigateToPaywall = { nav.navigate(ArtistNavRoutes.PAYWALL) },
                    onManageAvailability = { nav.navigate(ArtistNavRoutes.MANAGE_AVAILABILITY) },
                )
            }
            composable(ArtistNavRoutes.MANAGE_AVAILABILITY) {
                ManageAvailabilityScreen(onBack = { nav.popBackStack() })
            }
            composable(ArtistNavRoutes.SCORE_EXPLAINER) {
                ScoreExplainerScreen(onBack = { nav.popBackStack() })
            }
            composable(ArtistNavRoutes.PAYWALL) {
                PaywallScreen(
                    role = AppRole.Artist,
                    onClose = { nav.popBackStack() },
                )
            }
            composable(ArtistTab.Messages.route) {
                MessagesScreen(onThreadClick = { id -> nav.navigate(ArtistNavRoutes.chat(id)) })
            }
            composable(ArtistTab.Epk.route) {
                EpkScreen(onEditInWizard = { nav.navigate(ArtistNavRoutes.WIZARD) })
            }
            composable(ArtistNavRoutes.WIZARD) {
                WizardScreen(onFinished = { nav.popBackStack() })
            }
            composable(
                route = ArtistNavRoutes.CHAT,
                arguments = listOf(navArgument("threadId") { type = NavType.StringType }),
            ) {
                ChatScreen(
                    onBack = { nav.popBackStack() },
                    onBookingClick = { id -> nav.navigate(ArtistNavRoutes.bookingDetail(id)) },
                )
            }
            composable(
                route = ArtistNavRoutes.BOOKING_DETAIL,
                arguments = listOf(navArgument("bookingId") { type = NavType.StringType }),
            ) {
                BookingDetailScreen(
                    isArtistViewer = true,
                    onBack = { nav.popBackStack() },
                    onOpenChat = { threadId -> nav.navigate(ArtistNavRoutes.chat(threadId)) },
                )
            }
            composable(
                route = ArtistNavRoutes.GIG_REQUEST,
                arguments = listOf(navArgument("requestId") { type = NavType.StringType }),
            ) {
                GigRequestDetailScreen(onBack = { nav.popBackStack() })
            }
        }
    }
}

/**
 * Shared bottom-nav click behaviour: single-top, restore state, and pop to the
 * graph start so tabs don't stack. Used by both role scaffolds.
 */
internal fun navigateToTab(nav: androidx.navigation.NavController, route: String) {
    nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
