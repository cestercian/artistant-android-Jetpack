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
import `in`.artistant.app.feature.gigrequest.GigRequestDetailScreen
import `in`.artistant.app.feature.messages.ChatScreen
import `in`.artistant.app.feature.messages.MessagesScreen
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
                    ArtistTab.Gigs -> composable(tab.route) { GigsTab() }
                    // M4: Messages is a real tab for the artist too. Home/EPK stay stubs (M5+).
                    ArtistTab.Messages -> composable(tab.route) { MessagesTab() }
                    else -> composable(tab.route) { Placeholder(tab.label) }
                }
            }
        }
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
