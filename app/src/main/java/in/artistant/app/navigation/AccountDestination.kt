package `in`.artistant.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import `in`.artistant.app.feature.profile.AccountScreen
import `in`.artistant.app.feature.system.AppStore
import `in`.artistant.app.feature.system.RatePromptViewModel
import `in`.artistant.app.feature.system.WhatsNewViewModel

/**
 * The routes the settings list (design 47 / 69) pushes, per graph.
 *
 * The literals are identical strings on both roles and deliberately spelled
 * twice — see [ClientNavRoutes.BLOCKED_ACCOUNTS] — so this carries them rather
 * than reaching for one role's table from shared code.
 */
internal data class AccountRoutes(
    val blockedAccounts: String,
    val privacy: String,
    val safetyCentre: String,
    val helpCentre: String,
    val feedback: String,
    val activity: String,
    val notifications: String,
    val language: String,
    val accessibility: String,
    val devices: String,
    val dataExport: String,
    val deleteAccount: String,
    val paywall: String,
) {
    companion object {
        val Client = AccountRoutes(
            blockedAccounts = ClientNavRoutes.BLOCKED_ACCOUNTS,
            privacy = ClientNavRoutes.PRIVACY,
            safetyCentre = ClientNavRoutes.SAFETY_CENTRE,
            helpCentre = ClientNavRoutes.HELP_CENTRE,
            feedback = ClientNavRoutes.FEEDBACK,
            activity = ClientNavRoutes.ACTIVITY,
            notifications = ClientNavRoutes.NOTIFICATIONS,
            language = ClientNavRoutes.LANGUAGE,
            accessibility = ClientNavRoutes.ACCESSIBILITY,
            devices = ClientNavRoutes.DEVICES,
            dataExport = ClientNavRoutes.DATA_EXPORT,
            deleteAccount = ClientNavRoutes.DELETE_ACCOUNT,
            paywall = ClientNavRoutes.PAYWALL,
        )

        val Artist = AccountRoutes(
            blockedAccounts = ArtistNavRoutes.BLOCKED_ACCOUNTS,
            privacy = ArtistNavRoutes.PRIVACY,
            safetyCentre = ArtistNavRoutes.SAFETY_CENTRE,
            helpCentre = ArtistNavRoutes.HELP_CENTRE,
            feedback = ArtistNavRoutes.FEEDBACK,
            activity = ArtistNavRoutes.ACTIVITY,
            notifications = ArtistNavRoutes.NOTIFICATIONS,
            language = ArtistNavRoutes.LANGUAGE,
            accessibility = ArtistNavRoutes.ACCESSIBILITY,
            devices = ArtistNavRoutes.DEVICES,
            dataExport = ArtistNavRoutes.DATA_EXPORT,
            deleteAccount = ArtistNavRoutes.DELETE_ACCOUNT,
            paywall = ArtistNavRoutes.PAYWALL,
        )
    }
}

/**
 * "One implementation, two hosts" — the wiring half.
 *
 * [AccountScreen] is one screen with the artist rows injected by role, and its
 * two call sites had drifted into two near-identical eighteen-line argument
 * lists in the scaffolds. That is the shape the design's own note on 47 / 69
 * warns about: the forked version is how the artist side ended up without a
 * delete row, because nobody remembered to copy it across. A new settings row
 * is wired once, here.
 *
 * [onManageAvailability] is the only genuinely role-specific tail — the client
 * graph has no availability editor to push.
 */
@Composable
internal fun AccountDestination(
    nav: NavHostController,
    routes: AccountRoutes,
    whatsNew: WhatsNewViewModel,
    ratePrompt: RatePromptViewModel,
    onManageAvailability: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    AccountScreen(
        onBack = { nav.popBackStack() },
        onBlockedAccounts = { nav.navigate(routes.blockedAccounts) },
        onPrivacy = { nav.navigate(routes.privacy) },
        onSafetyCentre = { nav.navigate(routes.safetyCentre) },
        onHelpCentre = { nav.navigate(routes.helpCentre) },
        onFeedback = { nav.navigate(routes.feedback) },
        // Single-top: this row and Discover's bell both push Activity, and
        // without it a double tap — or a tap on a graph that already has the
        // screen on top — stacks a second copy the user has to dismiss twice.
        onActivity = { nav.navigate(routes.activity) { launchSingleTop = true } },
        // Not a route: screen 137 is presented by the root's host, and this is
        // the root-scoped ViewModel that host draws — see [SystemRoutes].
        onWhatsNew = whatsNew::showOnDemand,
        onRateApp = {
            // Through the record, exactly as `RatePromptSheet` does. Somebody
            // who went to the listing from here is done being asked — the sheet
            // used to be the only path that knew that, so a user who rated from
            // settings still got the prompt after their next review.
            ratePrompt.rated()
            AppStore.openListing(context)
        },
        onNotifications = { nav.navigate(routes.notifications) },
        onLanguage = { nav.navigate(routes.language) },
        onAccessibility = { nav.navigate(routes.accessibility) },
        onDevices = { nav.navigate(routes.devices) },
        onDataExport = { nav.navigate(routes.dataExport) },
        onDeleteAccount = { nav.navigate(routes.deleteAccount) },
        onSubscription = { nav.navigate(routes.paywall) },
        onManageAvailability = onManageAvailability,
    )
}
