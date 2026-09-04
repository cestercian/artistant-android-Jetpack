package `in`.artistant.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import `in`.artistant.app.designsystem.component.LightTabAction
import `in`.artistant.app.designsystem.component.LightTabBar
import `in`.artistant.app.designsystem.component.LightTabItem
import `in`.artistant.app.designsystem.theme.AppRole
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.motion
import `in`.artistant.app.designsystem.theme.reduceMotion
import `in`.artistant.app.feature.artisthome.ArtistHomeScreen
import `in`.artistant.app.feature.availability.ManageAvailabilityScreen
import `in`.artistant.app.feature.booking.BookingDetailScreen
import `in`.artistant.app.feature.epk.EpkScreen
import `in`.artistant.app.feature.gigs.ArtistGigsScreen
import `in`.artistant.app.feature.gigs.GigRequestDetailScreen
import `in`.artistant.app.feature.messages.ArchivedScreen
import `in`.artistant.app.feature.messages.ChatScreen
import `in`.artistant.app.feature.messages.MessagesScreen
import `in`.artistant.app.feature.messages.SafetyCentreScreen
import `in`.artistant.app.feature.messages.SupportScreen
import `in`.artistant.app.feature.paywall.PaywallScreen
import `in`.artistant.app.feature.profile.BlockedAccountsScreen
import `in`.artistant.app.feature.profile.ProfileScreen
import `in`.artistant.app.feature.score.ScoreEditor
import `in`.artistant.app.feature.score.ScoreExplainerScreen
import `in`.artistant.app.feature.score.ScoreHistoryScreen
import `in`.artistant.app.feature.wizard.WizardScreen

/**
 * Artist bottom nav: Studio · Gigs · [+] · Messages · Profile (screen 09).
 *
 * The fourth glyph is a person and its label is "Profile", but its route is
 * still `epk` — and that is not a mismatch. The press kit IS the artist's own
 * profile: it is the page they edit about themselves, and their account
 * settings already hang off the avatar in its title bar. Renaming the route
 * would break the push deep link for nothing.
 */
private enum class ArtistTab(val route: String, val label: String, val icon: ImageVector) {
    Home("home", "Studio", Icons.Filled.Home),
    Gigs("gigs", "Gigs", Icons.Filled.CalendarMonth),
    Messages("messages", "Messages", Icons.Filled.ChatBubbleOutline),
    Epk("epk", "Profile", Icons.Filled.PersonOutline),
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
    val pendingTab by tabRouter.pendingArtistTab.collectAsStateWithLifecycle()

    // One-shot, for both reasons spelled out on [ClientTabsScaffold] and
    // [TabRouter]: a recreation must not re-apply a stale tab and pop the restored
    // stack (an artist reading a gig request loses it to a font-scale change), and
    // re-arming the tab a push already holds must still emit — `ArtistHome` and
    // `ArtistGigs` carry no id, so this effect is the only thing that navigates
    // for them.
    LaunchedEffect(pendingTab) {
        val tab = tabRouter.consumePendingArtistTab() ?: return@LaunchedEffect
        val tabRoute = when (tab) {
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
        // See [ClientTabsScaffold] — the light design has no ambient wash, just
        // the flat page ground.
        containerColor = AppTheme.colors.page,
        bottomBar = {
            if (!showBottomBar) return@Scaffold
            LightTabBar(
                items = remember {
                    ArtistTab.entries.map { LightTabItem(it.route, it.label, it.icon) }
                },
                selectedRoute = selectedTabRoute,
                onSelect = { route -> navigateToTab(nav, route) },
                // Screen 09 draws a play glyph in the artist's action circle.
                // It goes to the availability editor: the artist-side verb that
                // actually changes what the market can see is "open a date", and
                // that editor already exists. Not a new flow — a shortcut to one.
                action = LightTabAction(
                    label = "Manage availability",
                    icon = Icons.Filled.PlayArrow,
                    onClick = { nav.navigate(ArtistNavRoutes.MANAGE_AVAILABILITY) },
                ),
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
                        onBlockedAccounts = { nav.navigate(ArtistNavRoutes.BLOCKED_ACCOUNTS) },
                        onBack = { nav.popBackStack() },
                        onNavigateToPaywall = { nav.navigate(ArtistNavRoutes.PAYWALL) },
                        onManageAvailability = { nav.navigate(ArtistNavRoutes.MANAGE_AVAILABILITY) },
                    )
                }
            }
            composable(ArtistNavRoutes.BLOCKED_ACCOUNTS) {
                TabPane(inner) {
                    BlockedAccountsScreen(
                        onBack = { nav.popBackStack() },
                        // "Block is not report" needs somewhere to go, and a
                        // report is filed inside a conversation — so the remedy
                        // is the inbox, not a form with no thread behind it.
                        onReportConversation = { navigateToTab(nav, ArtistTab.Messages.route) },
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
                    ScoreExplainerScreen(
                        onBack = { nav.popBackStack() },
                        // Every opportunity opens the thing it is about (design
                        // 50). The press kit owns the listing (samples, photos,
                        // packages, bio); the wizard owns the steps surfaced
                        // nowhere else (tech rider, socials); and the two
                        // score-moving rows that are not "fields" at all go where
                        // the metric is actually earned — the inbox for reply
                        // speed, the gig list for the hosts a review is asked
                        // from.
                        onOpenEditor = { editor ->
                            when (editor) {
                                ScoreEditor.PressKit -> nav.navigate(ArtistTab.Epk.route)
                                ScoreEditor.Wizard -> nav.navigate(ArtistNavRoutes.WIZARD)
                                ScoreEditor.Messages -> nav.navigate(ArtistTab.Messages.route)
                                ScoreEditor.Gigs -> nav.navigate(ArtistTab.Gigs.route)
                            }
                        },
                        onSeeHistory = { nav.navigate(ArtistNavRoutes.SCORE_HISTORY) },
                    )
                }
            }
            composable(ArtistNavRoutes.SCORE_HISTORY) {
                TabPane(inner) {
                    ScoreHistoryScreen(onBack = { nav.popBackStack() })
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
                    MessagesScreen(
                        onThreadClick = { id -> nav.navigate(ArtistNavRoutes.chat(id)) },
                        // The inbox's inline accelerator lands in the artist's
                        // own funnel — which this role calls Gigs, not Bookings.
                        onBookingClick = { id -> nav.navigate(ArtistNavRoutes.bookingDetail(id)) },
                        onOpenArchive = { nav.navigate(ArtistNavRoutes.ARCHIVED) },
                        onOpenSupport = { nav.navigate(ArtistNavRoutes.SUPPORT) },
                    )
                }
            }
            composable(ArtistNavRoutes.ARCHIVED) {
                TabPane(inner) {
                    ArchivedScreen(
                        onBack = { nav.popBackStack() },
                        onThreadClick = { id -> nav.navigate(ArtistNavRoutes.chat(id)) },
                        onOpenSafetyCentre = { nav.navigate(ArtistNavRoutes.SAFETY_CENTRE) },
                    )
                }
            }
            composable(ArtistNavRoutes.SUPPORT) {
                TabPane(inner) {
                    SupportScreen(
                        onBack = { nav.popBackStack() },
                        // Support's one real deep link lands in the artist's own
                        // funnel, which this role calls Gigs.
                        bookingsLabel = ArtistTab.Gigs.label,
                        onOpenBookings = { navigateToTab(nav, ArtistTab.Gigs.route) },
                    )
                }
            }
            composable(ArtistNavRoutes.SAFETY_CENTRE) {
                TabPane(inner) {
                    SafetyCentreScreen(
                        onBack = { nav.popBackStack() },
                        onReportConversation = { navigateToTab(nav, ArtistTab.Messages.route) },
                        onBlockedAccounts = { nav.navigate(ArtistNavRoutes.BLOCKED_ACCOUNTS) },
                    )
                }
            }
            composable(ArtistTab.Epk.route) {
                TabPane(inner) {
                    EpkScreen(
                        onEditInWizard = { nav.navigate(ArtistNavRoutes.WIZARD) },
                        // The artist's account surface — sign out, calendar sync,
                        // data export, DELETE ACCOUNT — hangs off the avatar in
                        // this screen's title bar, which is where the reference
                        // puts it. It used to hang off the dashboard greeting
                        // instead: a reasonable-looking place, and the wrong one,
                        // because the screen an artist opens to work on their own
                        // profile is the screen they go to looking for their own
                        // account. `ProfileScreen` renames itself "Account" and
                        // grows a back control when it is pushed rather than
                        // rooted, so the artist gets the right chrome for free.
                        onOpenAccount = { nav.navigate(ArtistNavRoutes.PROFILE) },
                    )
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
                        onOpenSafetyCentre = { nav.navigate(ArtistNavRoutes.SAFETY_CENTRE) },
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
 * Shared bottom-nav click behavior: single-top, restore state, and pop to the
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
 * The `NavHost` is full-bleed and each destination opts into the scaffold insets
 * instead of inheriting them. Screens that want the chrome to overlap them
 * (Discover, the artist profile) simply skip this wrapper — which is the whole
 * reason the padding moved down a level.
 *
 * [inner] carries the status-bar inset on top and, on a tab route, the tab bar's
 * full height on the bottom, so a screen wrapped here reserves exactly the space
 * the bar occupies.
 */
@Composable
internal fun TabPane(inner: PaddingValues, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(inner)) { content() }
}
