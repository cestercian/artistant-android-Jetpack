package `in`.artistant.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.WorkOutline
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import `in`.artistant.app.designsystem.component.FloatingTabBar
import `in`.artistant.app.designsystem.component.FloatingTabItem
import `in`.artistant.app.designsystem.component.ambientRoleWash
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
import `in`.artistant.app.designsystem.theme.motion
import `in`.artistant.app.designsystem.theme.reduceMotion
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

    val selectedTabRoute = ArtistTab.entries
        .firstOrNull { tab -> current?.destination?.hierarchy?.any { it.route == tab.route } == true }
        ?.route

    // See [ClientTabsScaffold] — same reason these are resolved out here.
    val motion = AppTheme.motion
    val reduceMotion = AppTheme.reduceMotion
    val tabRoutes = remember { ArtistTab.entries.map { it.route }.toSet() }

    Scaffold(
        // Transparent so the ambient wash below shows through; the wash paints
        // the background colour itself.
        containerColor = Color.Transparent,
        modifier = Modifier.ambientBackdrop(),
        bottomBar = {
            if (!showBottomBar) return@Scaffold
            // No trailing action on the artist side — the artist has no catalogue
            // to search, so the pill takes the full width on its own.
            FloatingTabBar(
                items = remember {
                    ArtistTab.entries.map { FloatingTabItem(it.route, it.label, it.icon) }
                },
                selectedRoute = selectedTabRoute,
                onSelect = { route -> navigateToTab(nav, route) },
            )
        },
    ) { inner ->
        NavHost(
            navController = nav,
            startDestination = ArtistTab.Home.route,
            enterTransition = navEnter(motion, reduceMotion, tabRoutes),
            exitTransition = navExit(motion, reduceMotion, tabRoutes),
            popEnterTransition = navPopEnter(motion, reduceMotion, tabRoutes),
            popExitTransition = navPopExit(motion, reduceMotion, tabRoutes),
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(ArtistTab.Home.route) {
                TabPane(inner) {
                    ArtistHomeScreen(
                        onBookingClick = { id -> nav.navigate(ArtistNavRoutes.bookingDetail(id)) },
                        onGigRequestClick = { id -> nav.navigate(ArtistNavRoutes.gigRequest(id)) },
                        onProfileClick = { nav.navigate(ArtistNavRoutes.PROFILE) },
                        onOpenWizard = { nav.navigate(ArtistNavRoutes.WIZARD) },
                        onScoreExplainer = { nav.navigate(ArtistNavRoutes.SCORE_EXPLAINER) },
                        // The dashboard's availability strip is the natural place
                        // to change what days are offered; it previously had no
                        // route, so the editor was reachable only from Profile.
                        onManageAvailability = { nav.navigate(ArtistNavRoutes.MANAGE_AVAILABILITY) },
                        onSubscribe = { nav.navigate(ArtistNavRoutes.PAYWALL) },
                    )
                }
            }
            composable(ArtistTab.Gigs.route) {
                TabPane(inner) {
                    ArtistGigsScreen(
                        onBookingClick = { id -> nav.navigate(ArtistNavRoutes.bookingDetail(id)) },
                    )
                }
            }
            composable(ArtistNavRoutes.PROFILE) {
                TabPane(inner) {
                    ProfileScreen(
                        onBack = { nav.popBackStack() },
                        onNavigateToPaywall = { nav.navigate(ArtistNavRoutes.PAYWALL) },
                        onManageAvailability = { nav.navigate(ArtistNavRoutes.MANAGE_AVAILABILITY) },
                    )
                }
            }
            composable(ArtistNavRoutes.MANAGE_AVAILABILITY) {
                TabPane(inner) {
                    ManageAvailabilityScreen(onBack = { nav.popBackStack() })
                }
            }
            composable(ArtistNavRoutes.SCORE_EXPLAINER) {
                TabPane(inner) {
                    ScoreExplainerScreen(onBack = { nav.popBackStack() })
                }
            }
            composable(ArtistNavRoutes.PAYWALL) {
                TabPane(inner) {
                    PaywallScreen(
                        role = AppRole.Artist,
                        onClose = { nav.popBackStack() },
                    )
                }
            }
            composable(ArtistTab.Messages.route) {
                TabPane(inner) {
                    MessagesScreen(onThreadClick = { id -> nav.navigate(ArtistNavRoutes.chat(id)) })
                }
            }
            composable(ArtistTab.Epk.route) {
                TabPane(inner) {
                    EpkScreen(onEditInWizard = { nav.navigate(ArtistNavRoutes.WIZARD) })
                }
            }
            composable(ArtistNavRoutes.WIZARD) {
                TabPane(inner) {
                    WizardScreen(onFinished = { nav.popBackStack() })
                }
            }
            composable(
                route = ArtistNavRoutes.CHAT,
                arguments = listOf(navArgument("threadId") { type = NavType.StringType }),
            ) {
                TabPane(inner) {
                    ChatScreen(
                        onBack = { nav.popBackStack() },
                        onBookingClick = { id -> nav.navigate(ArtistNavRoutes.bookingDetail(id)) },
                    )
                }
            }
            composable(
                route = ArtistNavRoutes.BOOKING_DETAIL,
                arguments = listOf(navArgument("bookingId") { type = NavType.StringType }),
            ) {
                TabPane(inner) {
                    BookingDetailScreen(
                        isArtistViewer = true,
                        onBack = { nav.popBackStack() },
                        onOpenChat = { threadId -> nav.navigate(ArtistNavRoutes.chat(threadId)) },
                    )
                }
            }
            composable(
                route = ArtistNavRoutes.GIG_REQUEST,
                arguments = listOf(navArgument("requestId") { type = NavType.StringType }),
            ) {
                TabPane(inner) {
                    GigRequestDetailScreen(onBack = { nav.popBackStack() })
                }
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

/**
 * The standard inset pane for a destination.
 *
 * The tab bar floats now, so the `NavHost` itself is full-bleed and each
 * destination opts into the scaffold insets instead of inheriting them. Screens
 * that want the chrome to overlap them (Discover) simply skip this wrapper —
 * which is the whole reason the padding moved down a level.
 *
 * [inner] carries the status-bar inset on top and, on a tab route, the floating
 * bar's full footprint on the bottom, so a screen wrapped here reserves exactly
 * the space the bar occupies.
 */
@Composable
internal fun TabPane(inner: PaddingValues, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(inner)) { content() }
}

/**
 * Role-tinted atmosphere painted behind every destination in a scaffold.
 *
 * Screens that paint their own opaque `colors.bg` (most of them today) sit on
 * top of this and are unaffected; drop that fill from a screen and it inherits
 * the wash. Kept at the scaffold level so the glow is continuous across a tab
 * switch rather than restarting per screen.
 */
@Composable
internal fun Modifier.ambientBackdrop(): Modifier {
    val colors = AppTheme.colors
    return this.ambientRoleWash(brand = colors.brand, background = colors.bg)
}
