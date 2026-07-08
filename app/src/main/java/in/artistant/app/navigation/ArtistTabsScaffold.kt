package `in`.artistant.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import `in`.artistant.app.designsystem.theme.AppRole
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.feature.artisthome.ArtistHomeScreen
import `in`.artistant.app.feature.availability.ManageAvailabilityScreen
import `in`.artistant.app.feature.epk.EpkScreen
import `in`.artistant.app.feature.gigrequest.GigRequestDetailScreen
import `in`.artistant.app.feature.messages.ChatScreen
import `in`.artistant.app.feature.messages.MessagesScreen
import `in`.artistant.app.feature.score.ScoreExplainerScreen
import `in`.artistant.app.ui.Placeholder

// Artist bottom nav: Home · Gigs · Messages · EPK.
private enum class ArtistTab(val route: String, val label: String, val icon: ImageVector) {
    Home("home", "Home", Icons.Filled.Dashboard),
    Gigs("gigs", "Gigs", Icons.Filled.WorkOutline),
    Messages("messages", "Messages", Icons.AutoMirrored.Filled.Chat),
    Epk("epk", "EPK", Icons.Filled.LibraryMusic),
}

@Composable
fun ArtistTabsScaffold() {
    val nav = rememberNavController()
    Scaffold(
        containerColor = AppTheme.colors.bg,
        bottomBar = {
            val current by nav.currentBackStackEntryAsState()
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
            ArtistTab.entries.forEach { tab ->
                when (tab) {
                    // M5c: Home is a real tab now — dashboard + score/availability/gig-request pushes.
                    ArtistTab.Home -> composable(tab.route) { HomeTab() }
                    ArtistTab.Gigs -> composable(tab.route) { GigsTab() }
                    // M4: Messages is a real tab for the artist too.
                    ArtistTab.Messages -> composable(tab.route) { MessagesTab() }
                    // M5c part 2: EPK is now the real profile editor.
                    ArtistTab.Epk -> composable(tab.route) { EpkScreen() }
                }
            }
        }
    }
}

/**
 * Artist Home tab — the dashboard plus its pushed routes: Score Explainer, the
 * availability editor, and the gig-request detail. The gig-request list on Home and
 * the detail screen both read the shared @Singleton RequestStore, so tapping a row
 * and navigating is all it takes — the store is already seeded by the dashboard load.
 * The subscribe banner's Paywall is M7 → a stub for now (and gated off by default).
 */
@Composable
private fun HomeTab() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = HomeRoot) {
        composable<HomeRoot> {
            ArtistHomeScreen(
                onOpenScoreExplainer = { nav.navigate(ArtistRoute.ScoreExplainer) },
                onManageAvailability = { nav.navigate(ArtistRoute.ManageAvailability) },
                onOpenGigRequest = { nav.navigate(ArtistRoute.GigRequest(id = it)) },
                onSubscribe = { nav.navigate(PaywallStub) },
            )
        }
        composable<ArtistRoute.ScoreExplainer> {
            ScoreExplainerScreen(onBack = { nav.popBackStack() })
        }
        composable<ArtistRoute.ManageAvailability> {
            ManageAvailabilityScreen(onDone = { nav.popBackStack() })
        }
        composable<ArtistRoute.GigRequest> {
            GigRequestDetailScreen(onBack = { nav.popBackStack() })
        }
        composable<PaywallStub> { Placeholder("Paywall (M7)") }
    }
}

/** Artist Gigs tab — placeholder root + the gig-request detail route (M5 wires the entry). */
@Composable
private fun GigsTab() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = GigsRoot) {
        composable<GigsRoot> { Placeholder(ArtistTab.Gigs.label) }
        composable<ArtistRoute.GigRequest> {
            GigRequestDetailScreen(onBack = { nav.popBackStack() })
        }
    }
}

/**
 * Artist Messages tab — thread list + chat (SHARED screens, artist role). No
 * artist-profile push from chat: the artist's counterpart is the client, whose
 * avatar is static (parity with iOS's non-navigable artist-viewer avatar).
 */
@Composable
private fun MessagesTab() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = ArtistMessagesRoot) {
        composable<ArtistMessagesRoot> {
            MessagesScreen(
                role = AppRole.Artist,
                onOpenThread = { nav.navigate(ArtistRoute.Chat(threadId = it)) },
            )
        }
        composable<ArtistRoute.Chat> {
            ChatScreen(
                role = AppRole.Artist,
                onBack = { nav.popBackStack() },
                // No onArtist — artist viewer's counterpart avatar is not navigable.
            )
        }
    }
}

/** Nested-graph root marker for the artist Home tab. */
@kotlinx.serialization.Serializable
private data object HomeRoot

/** Placeholder destination for the M7 Paywall (subscribe banner target). */
@kotlinx.serialization.Serializable
private data object PaywallStub

/** Nested-graph root marker for the artist Gigs tab. */
@kotlinx.serialization.Serializable
private data object GigsRoot

/** Nested-graph root marker for the artist Messages tab. */
@kotlinx.serialization.Serializable
private data object ArtistMessagesRoot

/**
 * Shared bottom-nav click behaviour: single-top, restore state, and pop to the
 * graph start so tabs don't stack. Used by both role scaffolds.
 */
internal fun navigateToTab(nav: NavController, route: String) {
    nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
