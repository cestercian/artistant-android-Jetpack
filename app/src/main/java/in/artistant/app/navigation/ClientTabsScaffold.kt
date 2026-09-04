package `in`.artistant.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import `in`.artistant.app.designsystem.component.LightTabAction
import `in`.artistant.app.designsystem.component.LightTabBar
import `in`.artistant.app.designsystem.component.LightTabItem
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
import `in`.artistant.app.feature.artist.ArtistReviewsScreen
import `in`.artistant.app.feature.score.BookabilityScreen
import `in`.artistant.app.feature.booking.BookingDetailScreen
import `in`.artistant.app.feature.booking.BookingScreen
import `in`.artistant.app.feature.booking.CheckoutScreen
import `in`.artistant.app.feature.booking.ConfirmedScreen
import `in`.artistant.app.feature.booking.InvoiceScreen
import `in`.artistant.app.feature.booking.MatchConfirmedScreen
import `in`.artistant.app.feature.booking.RequestQuoteScreen
import `in`.artistant.app.feature.booking.ReviewSheetViewModel
import `in`.artistant.app.feature.bookings.BookingsScreen
import `in`.artistant.app.feature.bookings.MonthCalendarScreen
import `in`.artistant.app.feature.discover.DiscoverScreen
import `in`.artistant.app.feature.messages.ArchivedScreen
import `in`.artistant.app.feature.messages.ChatOpenViewModel
import `in`.artistant.app.feature.messages.ChatScreen
import `in`.artistant.app.feature.messages.MessagesScreen
import `in`.artistant.app.feature.messages.SafetyCentreScreen
import `in`.artistant.app.feature.messages.SupportScreen
import `in`.artistant.app.feature.search.SearchScreen
import `in`.artistant.app.designsystem.theme.AppRole
import `in`.artistant.app.feature.profile.ArtistListKind
import `in`.artistant.app.feature.profile.ArtistListScreen
import `in`.artistant.app.feature.profile.BlockedAccountsScreen
import `in`.artistant.app.feature.signup.LegalDoc
import `in`.artistant.app.feature.signup.LegalScreen
import `in`.artistant.app.feature.signup.PrivacyScreen
import `in`.artistant.app.feature.profile.AccessibilityScreen
import `in`.artistant.app.feature.profile.AccessibilityViewModel
import `in`.artistant.app.feature.profile.AccountScreen
import `in`.artistant.app.feature.profile.DataExportScreen
import `in`.artistant.app.feature.profile.DeleteAccountScreen
import `in`.artistant.app.feature.profile.DevicesScreen
import `in`.artistant.app.feature.profile.LanguageScreen
import `in`.artistant.app.feature.profile.NotificationSettingsScreen
import `in`.artistant.app.feature.profile.ProfileScreen
import `in`.artistant.app.feature.paywall.PaywallScreen
import `in`.artistant.app.feature.system.ActivityScreen
import `in`.artistant.app.feature.system.AppStore
import `in`.artistant.app.feature.system.FeedbackScreen
import `in`.artistant.app.feature.system.HelpCentreScreen
import `in`.artistant.app.feature.system.RatePromptHost
import `in`.artistant.app.feature.system.RatePromptViewModel
import `in`.artistant.app.feature.system.ToastViewModel
import `in`.artistant.app.feature.system.WhatsNewViewModel
import `in`.artistant.app.ui.RootViewModel

/**
 * Client navigation: five top-level routes, four of which are glyphs in the bar.
 *
 * The light design draws Home · Search · [+] · Messages · Profile (screens 02
 * and 19). Search moved INTO the bar — the dark design hung it off the side as a
 * detached circle, and the new bar's middle slot belongs to an action instead.
 *
 * **Bookings is the one that came out.** It keeps its route, its push deep link
 * and its bar — a notification about a booking still lands on it, and the bar
 * still draws while it is open — it simply is not one of the four glyphs. The
 * design's own screens disagree here (10 and 26 put a calendar in the fourth
 * slot, 02 and 19 put Messages and Profile there); the two that agree with each
 * other win, and Bookings stays reachable from Profile and from every booking
 * card.
 */
private enum class ClientTab(val route: String, val label: String, val icon: ImageVector) {
    Discover("discover", "Discover", Icons.Filled.Home),
    Search("search", "Search", Icons.Filled.Search),
    Messages("messages", "Messages", Icons.Filled.ChatBubbleOutline),
    Profile("profile", "Profile", Icons.Filled.PersonOutline),
    Bookings("bookings", "Bookings", Icons.Filled.CalendarMonth),
}

/**
 * The four glyphs, in bar order — two, the action circle, then two.
 *
 * Bookings is last in the enum precisely because it is absent here; keeping the
 * two lists in the same order otherwise means a reader can see the bar's layout
 * without cross-referencing.
 */
private val BAR_TABS = listOf(
    ClientTab.Discover, ClientTab.Search, ClientTab.Messages, ClientTab.Profile,
)

private const val ARTIST_PROFILE_ROUTE = "artist/{artistId}"

@Composable
fun ClientTabsScaffold() {
    val nav = rememberNavController()
    // The ACTIVITY's RootViewModel, not a new one: this composable is called directly from
    // `ArtistantNavHost`, above any NavHost, so `LocalViewModelStoreOwner` here is still the
    // activity and `hiltViewModel()` resolves the same instance the gate is driven by. Profile's
    // "Switch to artist mode" needs its `retryRouting` — see that destination.
    val rootViewModel: RootViewModel = hiltViewModel()
    val accessibilityViewModel: AccessibilityViewModel = hiltViewModel()
    // Accessibility -> "Always show labels" (design 129). Read here rather than inside
    // `LightTabBar` so the design-system component stays free of Hilt and of this app's
    // preference store; the host owns the preference, the bar just draws what it is told.
    val a11ySettings by accessibilityViewModel.state.collectAsStateWithLifecycle()
    val current by nav.currentBackStackEntryAsState()
    val route = current?.destination?.route
    val showBottomBar = ClientTab.entries.any { it.route == route }
    val tabRouter = rememberTabRouter()
    val pendingThread by tabRouter.pendingThreadId.collectAsStateWithLifecycle()
    val pendingBooking by tabRouter.pendingBookingDetail.collectAsStateWithLifecycle()
    val pendingTab by tabRouter.pendingClientTab.collectAsStateWithLifecycle()

    // Section SH. The toast host itself lives above the NavHost (there is one,
    // in ArtistantNavHost); this is the handle a destination raises one with.
    val toasts: ToastViewModel = hiltViewModel()
    // Screen 138. Scaffold-scoped so the booking-detail destination that arms it
    // and the sheet that draws it are the same instance.
    val ratePrompt: RatePromptViewModel = hiltViewModel()
    // Screen 137, for the account list's "What's new" row. The SAME instance the root's
    // `WhatsNewHost` collects: this composable sits above any NavHost, so
    // `LocalViewModelStoreOwner` is still the activity — exactly like `rootViewModel` above.
    // A `hiltViewModel()` inside a destination would resolve against that destination's
    // back-stack entry instead, and the sheet would be asked to open on a ViewModel nobody
    // is drawing.
    val whatsNew: WhatsNewViewModel = hiltViewModel()
    // Screen 138's other half — the row that goes to the listing without waiting for a
    // completed booking to earn the prompt.
    val context = LocalContext.current

    // Push deep links: flip tab then push the detail/chat route.
    //
    // Consumed one-shot, like the ids below it, because `LaunchedEffect` runs on
    // FIRST composition and not only on a change — and every configuration change
    // (font scale, day/night, fold resize) plus every process-death restore is a
    // fresh composition. Re-applying a retained tab there ran `navigateToTab`,
    // whose `popUpTo(start)` popped the back stack `rememberNavController` had
    // just restored: a client reading an artist profile at sunset was thrown back
    // to Discover by the day/night flip. A consumed event is null by then, so the
    // effect fires and does nothing.
    LaunchedEffect(pendingTab) {
        val tab = tabRouter.consumePendingClientTab() ?: return@LaunchedEffect
        val tabRoute = when (tab) {
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
        // The page ground. The dark design painted a role-tinted radial wash
        // behind every destination and left this transparent; the light design
        // has no wash — one flat warm off-white, with the accent appearing once
        // per screen wherever that screen decides.
        containerColor = AppTheme.colors.page,
        bottomBar = {
            if (!showBottomBar) return@Scaffold
            LightTabBar(
                items = remember {
                    BAR_TABS.map { LightTabItem(it.route, it.label, it.icon) }
                },
                selectedRoute = selectedTabRoute,
                onSelect = { route -> navigateToTab(nav, route) },
                // The raised circle is the client's primary verb: find someone to
                // book. It opens Search rather than a composer, because there is
                // nothing to compose until an artist is picked — the funnel
                // starts on a profile. A dedicated "new booking" flow is a
                // section-PR decision, not a P1 invention.
                showLabels = a11ySettings.alwaysShowLabels,
                action = LightTabAction(
                    label = "Find an artist",
                    icon = Icons.Filled.Add,
                    onClick = { navigateToTab(nav, ClientTab.Search.route) },
                ),
            )
        },
    ) { inner ->
        // Screen 138 rides over the whole graph rather than over one
        // destination: the review that arms it is submitted on booking detail,
        // but the insert outlives the sheet AND the screen (see
        // ReviewSheetViewModel), so the prompt has to survive a pop.
        RatePromptHost(ratePrompt)
        NavHost(
            navController = nav,
            startDestination = ClientTab.Discover.route,
            modifier = Modifier.fillMaxSize(),
            enterTransition = navEnter(motion, reduceMotion, tabRoutes),
            exitTransition = navExit(motion, reduceMotion, tabRoutes),
            popEnterTransition = navPopEnter(motion, reduceMotion, tabRoutes),
            popExitTransition = navPopExit(motion, reduceMotion, tabRoutes),
        ) {
            // Discover takes the ordinary inset pane now. Its hero used to be a
            // full-bleed photograph that owned the status-bar area; the light
            // design makes it a 262dp card on an ordinary page, so the screen has
            // nothing left that wants to run under the system bars.
            composable(ClientTab.Discover.route) {
                TabPane(inner) {
                    DiscoverScreen(
                        onArtistClick = { id -> nav.navigate("artist/$id") },
                        onOpenSearch = { navigateToTab(nav, ClientTab.Search.route) },
                        // The header bell (design 02) — screen 123, which until
                        // now was registered on both graphs with nothing
                        // pointing at it. Saved kept its Profile row (26) and is
                        // not lost with the heart that used to sit here.
                        onOpenActivity = { nav.navigate(ClientNavRoutes.ACTIVITY) },
                    )
                }
            }
            composable(ClientTab.Bookings.route) {
                TabPane(inner) {
                    BookingsScreen(
                        onBookingClick = { id -> nav.navigate(ClientNavRoutes.bookingDetail(id)) },
                        // The empty state's only action, and the nudge's: both
                        // send the client where the fact they are missing lives.
                        onFindArtist = { navigateToTab(nav, ClientTab.Search.route) },
                        onEditProfile = { navigateToTab(nav, ClientTab.Profile.route) },
                        onOpenCalendar = { nav.navigate(ClientNavRoutes.MONTH_CALENDAR) },
                        onOpenChat = { threadId -> nav.navigate(ClientNavRoutes.chat(threadId)) },
                    )
                }
            }
            composable(ClientNavRoutes.MONTH_CALENDAR) {
                TabPane(inner) {
                    MonthCalendarScreen(
                        onBack = { nav.popBackStack() },
                        onBookingClick = { id -> nav.navigate(ClientNavRoutes.bookingDetail(id)) },
                    )
                }
            }
            composable(ClientTab.Messages.route) {
                TabPane(inner) {
                    MessagesScreen(
                        onThreadClick = { id -> nav.navigate(ClientNavRoutes.chat(id)) },
                        onBookingClick = { id -> nav.navigate(ClientNavRoutes.bookingDetail(id)) },
                        onOpenArchive = { nav.navigate(ClientNavRoutes.ARCHIVED) },
                        onOpenSupport = { nav.navigate(ClientNavRoutes.SUPPORT) },
                    )
                }
            }
            composable(ClientNavRoutes.ARCHIVED) {
                TabPane(inner) {
                    ArchivedScreen(
                        onBack = { nav.popBackStack() },
                        onThreadClick = { id -> nav.navigate(ClientNavRoutes.chat(id)) },
                        onOpenSafetyCentre = { nav.navigate(ClientNavRoutes.SAFETY_CENTRE) },
                    )
                }
            }
            composable(ClientNavRoutes.SUPPORT) {
                TabPane(inner) {
                    SupportScreen(
                        onBack = { nav.popBackStack() },
                        bookingsLabel = ClientTab.Bookings.label,
                        onOpenBookings = { navigateToTab(nav, ClientTab.Bookings.route) },
                    )
                }
            }
            composable(ClientNavRoutes.SAFETY_CENTRE) {
                TabPane(inner) {
                    SafetyCentreScreen(
                        onBack = { nav.popBackStack() },
                        // Reporting happens inside a conversation, so the remedy
                        // is the inbox, not a form with no thread behind it.
                        onReportConversation = { navigateToTab(nav, ClientTab.Messages.route) },
                        onBlockedAccounts = { nav.navigate(ClientNavRoutes.BLOCKED_ACCOUNTS) },
                    )
                }
            }
            composable(ClientTab.Profile.route) {
                TabPane(inner) {
                    ProfileScreen(
                        onAccount = { nav.navigate(ClientNavRoutes.ACCOUNT) },
                        // Profile is Bookings' front door: the light bar dropped the calendar
                        // glyph, so this row and the booking cards are the only ways in.
                        onBookings = { navigateToTab(nav, ClientTab.Bookings.route) },
                        onArtistList = { kind -> nav.navigate(ClientNavRoutes.artistList(kind.raw)) },
                        onNotifications = { nav.navigate(ClientNavRoutes.NOTIFICATIONS) },
                        onPrivacy = { nav.navigate(ClientNavRoutes.PRIVACY) },
                        onSafetyCentre = { nav.navigate(ClientNavRoutes.SAFETY_CENTRE) },
                        // The role write lands on the server, but the root gate only re-reads
                        // the profile when the SESSION changes — which a role switch is not.
                        // `RootViewModel` is resolved from the activity's store here (this
                        // composable sits above the NavHost, so `hiltViewModel()` returns the
                        // same instance `ArtistantNavHost` created), and its `retryRouting` is
                        // the nudge that moves the gate Client → Artist without a cold start.
                        onRoleSwitched = rootViewModel::retryRouting,
                    )
                }
            }
            composable(ClientNavRoutes.ACCOUNT) {
                TabPane(inner) {
                    AccountScreen(
                        onBack = { nav.popBackStack() },
                        onBlockedAccounts = { nav.navigate(ClientNavRoutes.BLOCKED_ACCOUNTS) },
                        onPrivacy = { nav.navigate(ClientNavRoutes.PRIVACY) },
                        onSafetyCentre = { nav.navigate(ClientNavRoutes.SAFETY_CENTRE) },
                        onHelpCentre = { nav.navigate(ClientNavRoutes.HELP_CENTRE) },
                        onFeedback = { nav.navigate(ClientNavRoutes.FEEDBACK) },
                        onActivity = { nav.navigate(ClientNavRoutes.ACTIVITY) },
                        // Not a route: screen 137 is presented by the root's host, and this is
                        // the root-scoped ViewModel that host draws — see [SystemRoutes].
                        onWhatsNew = whatsNew::showOnDemand,
                        onRateApp = { AppStore.openListing(context) },
                        onNotifications = { nav.navigate(ClientNavRoutes.NOTIFICATIONS) },
                        onLanguage = { nav.navigate(ClientNavRoutes.LANGUAGE) },
                        onAccessibility = { nav.navigate(ClientNavRoutes.ACCESSIBILITY) },
                        onDevices = { nav.navigate(ClientNavRoutes.DEVICES) },
                        onDataExport = { nav.navigate(ClientNavRoutes.DATA_EXPORT) },
                        onDeleteAccount = { nav.navigate(ClientNavRoutes.DELETE_ACCOUNT) },
                        onSubscription = { nav.navigate(ClientNavRoutes.PAYWALL) },
                    )
                }
            }
            composable(ClientNavRoutes.NOTIFICATIONS) {
                TabPane(inner) { NotificationSettingsScreen(onBack = { nav.popBackStack() }) }
            }
            composable(ClientNavRoutes.ACCESSIBILITY) {
                TabPane(inner) { AccessibilityScreen(onBack = { nav.popBackStack() }) }
            }
            composable(ClientNavRoutes.LANGUAGE) {
                TabPane(inner) { LanguageScreen(onBack = { nav.popBackStack() }) }
            }
            composable(ClientNavRoutes.DEVICES) {
                TabPane(inner) { DevicesScreen(onBack = { nav.popBackStack() }) }
            }
            composable(ClientNavRoutes.DATA_EXPORT) {
                TabPane(inner) {
                    DataExportScreen(
                        onBack = { nav.popBackStack() },
                        // The scripted support assistant, not the safety centre: a failed
                        // export is a support question, and 34 is the screen that takes one.
                        onContactSupport = { nav.navigate(ClientNavRoutes.SUPPORT) },
                    )
                }
            }
            composable(ClientNavRoutes.DELETE_ACCOUNT) {
                TabPane(inner) {
                    DeleteAccountScreen(
                        onBack = { nav.popBackStack() },
                        onContactSupport = { nav.navigate(ClientNavRoutes.SUPPORT) },
                        // Nothing to navigate TO: the account is gone, so the cleared session
                        // propagates to the root gate and replaces this whole graph with the
                        // signup flow. Popping the stack here would only race that.
                        onFinished = {},
                    )
                }
            }
            composable(ClientNavRoutes.BLOCKED_ACCOUNTS) {
                TabPane(inner) {
                    BlockedAccountsScreen(
                        onBack = { nav.popBackStack() },
                        // "Block is not report" needs somewhere to go, and a
                        // report is filed inside a conversation — so the remedy
                        // is the inbox, not a form with no thread behind it.
                        onReportConversation = { navigateToTab(nav, ClientTab.Messages.route) },
                    )
                }
            }
            // Section SH — design screens 63 / 64 / 123. Registered identically
            // on the artist graph; see [SystemRoutes] for why they are one table
            // rather than two.
            composable(ClientNavRoutes.HELP_CENTRE) {
                TabPane(inner) {
                    HelpCentreScreen(
                        role = AppRole.Client,
                        onBack = { nav.popBackStack() },
                        // The promoted card's only action is "add your name",
                        // and the name lives on the profile.
                        onFixProfile = { navigateToTab(nav, ClientTab.Profile.route) },
                    )
                }
            }
            composable(ClientNavRoutes.FEEDBACK) {
                TabPane(inner) {
                    FeedbackScreen(
                        onClose = { nav.popBackStack() },
                        onToast = toasts::show,
                    )
                }
            }
            composable(ClientNavRoutes.ACTIVITY) {
                TabPane(inner) {
                    ActivityScreen(
                        role = AppRole.Client,
                        onOpenBooking = { id -> nav.navigate(ClientNavRoutes.bookingDetail(id)) },
                        onOpenThread = { id -> nav.navigate(ClientNavRoutes.chat(id)) },
                        // A chat push whose thread id never arrived still has to
                        // reach the user's messages — the same fallback
                        // `TabRouter.apply` makes for the notification tap.
                        onOpenMessages = { navigateToTab(nav, ClientTab.Messages.route) },
                        // Gig requests, Gigs and the studio are the artist's
                        // graph. Left null so a row that would go there renders
                        // as a record rather than as a control that eats the tap.
                    )
                }
            }
            // Design screens 62 / 31 / 114 (section GS). Registered on both graphs
            // because neither is role-specific. Reached from the account settings
            // list's "Privacy" row above; the legal viewer is also reachable from the
            // signup flow's welcome and sign-in screens.
            composable(ClientNavRoutes.PRIVACY) {
                TabPane(inner) {
                    PrivacyScreen(
                        onBack = { nav.popBackStack() },
                        onOpenLegal = { doc -> nav.navigate(ClientNavRoutes.legal(doc.name)) },
                    )
                }
            }
            composable(
                route = ClientNavRoutes.LEGAL,
                arguments = listOf(navArgument("doc") { type = NavType.StringType }),
            ) { entry ->
                TabPane(inner) {
                    // An unknown or missing argument opens the terms rather than
                    // failing: the viewer is segmented, so the wrong opening tab costs
                    // one tap and a crash costs the screen.
                    val doc = LegalDoc.entries
                        .firstOrNull { it.name == entry.arguments?.getString("doc") }
                        ?: LegalDoc.Terms
                    LegalScreen(doc = doc, onClose = { nav.popBackStack() })
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
                        // The three kinds are one screen with three row sources
                        // (screen 32's note), and the kind is a route argument —
                        // so switching chips REPLACES this entry rather than
                        // stacking on it. Without the inclusive pop, tapping
                        // through Saved → Bookings → Completed would leave three
                        // copies of the same screen on the stack for back to walk
                        // down one at a time.
                        onSelectKind = { picked ->
                            nav.navigate(ClientNavRoutes.artistList(picked.raw)) {
                                popUpTo(ClientNavRoutes.ARTIST_LIST) { inclusive = true }
                            }
                        },
                        onBrowseDiscover = { navigateToTab(nav, ClientTab.Discover.route) },
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
                // Both of the VM's flows are collected here because nothing else
                // reads them on this route: the Message tap sat silent through the
                // find-or-create round-trip, and when that call threw (offline, an
                // RLS denial) `_error` was written to a flow no composable observed
                // — so the button simply did nothing, with no message, ever.
                // BookingDetail's dock renders the same pair. See [ChatOpenFeedback].
                val openingChat by chatOpen.opening.collectAsStateWithLifecycle()
                val chatError by chatOpen.error.collectAsStateWithLifecycle()
                Box(Modifier.fillMaxSize()) {
                    // Second full-bleed destination (Discover is the other): the
                    // cover runs under the status bar, so this one also takes NO
                    // scaffold inset and applies the system-bar padding itself —
                    // to the floating hero controls at the top and to the action
                    // dock at the bottom. Wrapping it in [TabPane] pushed the whole
                    // page down by the status-bar height and left a letterbox of
                    // page background above the photo.
                    ArtistProfileScreen(
                        onBack = { nav.popBackStack() },
                        onBook = { artistId -> nav.navigate(ClientNavRoutes.bookingCompose(artistId)) },
                        onRequestQuote = { artistId -> nav.navigate(ClientNavRoutes.requestQuote(artistId)) },
                        onMessage = { artistId ->
                            chatOpen.open(artistId, bookingId = null) { threadId ->
                                nav.navigate(ClientNavRoutes.chat(threadId))
                            }
                        },
                        // Screen 55's route out. Not `popBackStack` — a stale
                        // share link opens this destination with nothing under
                        // it, and popping an empty stack leaves the app on a
                        // blank frame. Discover is somewhere to be.
                        onBrowse = {
                            nav.navigate(ClientTab.Discover.route) {
                                popUpTo(ClientTab.Discover.route) { inclusive = true }
                            }
                        },
                        onSeeReviews = { artistId ->
                            nav.navigate(ClientNavRoutes.artistReviews(artistId))
                        },
                        onSeeBookability = { artistId ->
                            nav.navigate(ClientNavRoutes.bookability(artistId))
                        },
                    )
                    ChatOpenFeedback(
                        opening = openingChat,
                        error = chatError,
                        onDismissError = chatOpen::dismissError,
                    )
                }
            }
            composable(
                route = ClientNavRoutes.ARTIST_REVIEWS,
                arguments = listOf(navArgument("artistId") { type = NavType.StringType }),
            ) { entry ->
                TabPane(inner) {
                    val artistId = entry.arguments?.getString("artistId").orEmpty()
                    ArtistReviewsScreen(
                        onBack = { nav.popBackStack() },
                        onRequestQuote = {
                            nav.navigate(ClientNavRoutes.requestQuote(artistId))
                        },
                    )
                }
            }
            composable(
                route = ClientNavRoutes.BOOKABILITY,
                arguments = listOf(navArgument("artistId") { type = NavType.StringType }),
            ) {
                TabPane(inner) {
                    BookabilityScreen(onBack = { nav.popBackStack() })
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
                        // Only the client seat has a counterparty with a public
                        // profile, so only this scaffold wires the participant row.
                        onArtistClick = { id -> nav.navigate("artist/$id") },
                        onOpenSafetyCentre = { nav.navigate(ClientNavRoutes.SAFETY_CENTRE) },
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
                        // The WHOLE funnel goes, not just this step. Popping only
                        // checkout left `booking/{artistId}` under the confirmation,
                        // so system back from "Request sent." re-entered the composer
                        // for the request that had just been filed — with its
                        // ViewModel state intact but the draft store already cleared
                        // — and Continue walked the client into checkout again to
                        // file a duplicate the no-overlap constraint then rejected,
                        // reporting an error for a booking that had actually
                        // succeeded. Back now lands on the artist's profile, where
                        // the funnel started. The screen's own "Back to discover"
                        // and "View booking" pop to the tab root and are unaffected.
                        onConfirmed = { bookingId ->
                            nav.navigate(ClientNavRoutes.confirmed(bookingId)) {
                                popUpTo(ClientNavRoutes.BOOKING) { inclusive = true }
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
                // Its own instance, scoped to this destination. The Message CTA
                // on the confirmed branch is a round-trip like the profile's, so
                // it needs the same spinner-and-error pair rather than a
                // navigation that can silently do nothing.
                val confirmChat: ChatOpenViewModel = hiltViewModel()
                val confirmOpening by confirmChat.opening.collectAsStateWithLifecycle()
                val confirmError by confirmChat.error.collectAsStateWithLifecycle()
                TabPane(inner) {
                    Box(Modifier.fillMaxSize()) {
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
                            onOpenInvoice = { id -> nav.navigate(ClientNavRoutes.invoice(id)) },
                            // Only reachable on the confirmed branch, where a
                            // thread already exists (mig 0015 creates it on
                            // confirm), so find-or-create resolves rather than
                            // inserts.
                            onMessageArtist = { artistId ->
                                confirmChat.open(artistId, bookingId = bookingId) { threadId ->
                                    nav.navigate(ClientNavRoutes.chat(threadId))
                                }
                            },
                        )
                        ChatOpenFeedback(
                            opening = confirmOpening,
                            error = confirmError,
                            onDismissError = confirmChat::dismissError,
                        )
                    }
                }
            }
            // Screen 94. The messaging section navigates here when an in-thread
            // quote is accepted — this is the destination it lands on.
            composable(
                route = ClientNavRoutes.MATCH_CONFIRMED,
                arguments = listOf(navArgument("bookingId") { type = NavType.StringType }),
            ) { entry ->
                val bookingId = entry.arguments?.getString("bookingId").orEmpty()
                TabPane(inner) {
                    MatchConfirmedScreen(
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
            // Screen 132.
            composable(
                route = ClientNavRoutes.INVOICE,
                arguments = listOf(navArgument("bookingId") { type = NavType.StringType }),
            ) { entry ->
                TabPane(inner) {
                    InvoiceScreen(
                        bookingId = entry.arguments?.getString("bookingId").orEmpty(),
                        onBack = { nav.popBackStack() },
                    )
                }
            }
            composable(
                route = ClientNavRoutes.BOOKING_DETAIL,
                arguments = listOf(navArgument("bookingId") { type = NavType.StringType }),
            ) {
                // Design 138's trigger, resolved HERE rather than inside the
                // booking screen.
                //
                // The rating prompt fires "after a good outcome", and the only
                // one this app has is a review the client chose to leave. The
                // screen already takes its `ReviewSheetViewModel` as a
                // parameter, so the same instance can be resolved from the
                // scaffold and watched — no change to `feature/booking`, and no
                // second definition of "a review landed".
                //
                // Watched as composition state, not by collecting the flow: the
                // screen consumes `submitted` in its own effect immediately, and
                // a `StateFlow` collector would be free to conflate true→false
                // into a single false and never see it. Both effects key on the
                // same snapshot value, so both observe the `true`.
                val reviewVm: ReviewSheetViewModel = hiltViewModel()
                val review by reviewVm.state.collectAsStateWithLifecycle()
                LaunchedEffect(review.submitted) {
                    if (review.submitted) ratePrompt.recordReviewSubmitted()
                }
                TabPane(inner) {
                    BookingDetailScreen(
                        isArtistViewer = false,
                        onBack = { nav.popBackStack() },
                        onOpenChat = { threadId -> nav.navigate(ClientNavRoutes.chat(threadId)) },
                        // "Book again" off a cancelled booking starts where a
                        // booking always starts — the artist's profile.
                        onBookAgain = { artistId -> nav.navigate("artist/$artistId") },
                        // Support lives inside the inbox on both roles.
                        onOpenSupport = { navigateToTab(nav, ClientTab.Messages.route) },
                        reviewVm = reviewVm,
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

/**
 * What the artist profile's Message button has to say for itself.
 *
 * The tap is not a navigation — [ChatOpenViewModel] resolves (or creates) the thread row first,
 * so there is a round-trip behind it and the round-trip can fail. BookingDetail renders both
 * facts in its dock; this page has no dock to hang them on (its Message control is a glyph
 * floating on the cover), so they render over it: a scrim + spinner while the call is in flight
 * — the flag's own KDoc calls it a blocking spinner — and a dismissible dialog when it throws.
 *
 * The scrim swallows taps for the duration. `open()` already guards a second Message tap, but
 * the page underneath still carries Book and Request a quote, and a navigation fired from there
 * would race the chat push landing on top of it. It uses `glassScrim` — the strongest black in
 * the ladder — for the same reason: over a cover photo a lighter wash reads as a tint rather
 * than as a page that is busy.
 */
@Composable
private fun ChatOpenFeedback(opening: Boolean, error: String?, onDismissError: () -> Unit) {
    val colors = AppTheme.colors
    if (opening) {
        Box(
            Modifier
                .fillMaxSize()
                .background(colors.glassScrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { /* swallow */ },
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = colors.brand)
        }
    }
    error?.let { message ->
        AlertDialog(
            shape = RoundedCornerShape(AppTheme.dimens.radii.xxl),
            onDismissRequest = onDismissError,
            title = { Text("Couldn't open the chat") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = onDismissError) { Text("OK") } },
        )
    }
}
