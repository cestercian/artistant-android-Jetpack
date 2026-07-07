package `in`.artistant.app.feature.signup

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.core.net.toUri
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.theme.AppTheme

/** Which legal doc to render. Mirrors iOS `LegalDoc`. */
enum class LegalDoc(val title: String, val subtitle: String, val url: String) {
    // The hosted canonical URLs (iOS AppEnvironment.{privacy,terms}PolicyURL). www. is the
    // canonical host per the iOS note; no BuildConfig field for these yet so they're constants.
    Terms("Terms of Service", "Updated May 2026", "https://www.artistant.in/legal/terms"),
    Privacy("Privacy Policy", "Updated May 2026", "https://www.artistant.in/legal/privacy"),
}

private val termsSections = listOf(
    "1. Acceptance" to "By using Artistant you agree to these Terms. If you do not agree, do not use the app. You must be 18 or older to book or perform.",
    "2. The Service" to "Artistant is a marketplace connecting artists and event organizers. We facilitate discovery, booking, and reviews. We are not a party to any performance contract between you and another user.",
    "3. Bookings" to "Bookings are requested and confirmed in-app between you and the artist. Cancellations are handled as described in §7.",
    "4. Artist obligations" to "Artists agree to honour confirmed bookings, arrive on time, and meet the technical rider agreed at booking. Repeat cancellations reduce your Bookability Score and may lead to suspension.",
    "5. Client obligations" to "Clients agree to provide accurate venue, time, and audience details, and not coordinate bookings off-platform once introduced through Artistant.",
    "6. Platform fees" to "If Artistant charges a platform fee or applicable taxes on a booking, the amount will be shown before you confirm.",
    "7. Cancellations" to "If an artist cancels a confirmed booking, we will help you find a replacement of equivalent quality on the same date where possible.",
    "8. Disputes" to "Disputes must be raised in-app within 7 days of the performance. Our team will mediate; resolutions may include partial refunds or replacement bookings.",
    "9. Limitation of liability" to "Artistant is not liable for indirect or consequential damages arising from a performance. Our maximum liability is the total amount paid through the booking in question.",
    "10. Termination" to "We may suspend or terminate accounts that violate these Terms, applicable law, or our community guidelines.",
    "11. Governing law" to "These Terms are governed by the laws of India. Disputes will be resolved in the courts of Bangalore, Karnataka.",
)

private val privacySections = listOf(
    "Data we collect" to "Phone number, name, city, profile photo, booking history, messages, device information.",
    "How we use it" to "To run the marketplace: matching artists with clients, computing the Bookability Score, sending booking confirmations, and providing customer support.",
    "Data we share" to "Artists and clients see each other's name, city, and Bookability Score. We never sell your data.",
    "Phone numbers" to "Your phone number is never shown on your public profile. Booking communication happens in-app.",
    "Your rights (DPDP Act)" to "You can request a copy of your data, correct inaccuracies, withdraw consent, or delete your account at any time from Profile → Settings.",
    "Retention" to "We keep your data while your account is active and for up to 7 years after deletion, as required by Indian tax and consumer-protection law.",
    "Cookies & analytics" to "We use first-party analytics only — PostHog, self-hosted. No third-party advertising trackers.",
    "Contact" to "privacy@artistant.in",
)

/**
 * Terms / privacy modal content (iOS `LegalView`). Presented in a bottom sheet from the welcome
 * screen. Scrolling title + section list + a footer "View online" link to the hosted URL, and a
 * Close button since the sheet's own scrim tap is the other dismiss path.
 */
@Composable
fun LegalScreen(doc: LegalDoc, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val context = LocalContext.current
    val sections = if (doc == LegalDoc.Terms) termsSections else privacySections

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = space.xl)
            .padding(bottom = space.xxl),
        verticalArrangement = Arrangement.spacedBy(space.xl),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(space.xs)) {
            Text(doc.title, style = AppTheme.type.displaySub, color = colors.ink)
            Text(doc.subtitle, style = AppTheme.type.footnote, color = colors.ink3)
        }

        sections.forEach { (heading, body) ->
            Column(verticalArrangement = Arrangement.spacedBy(space.sm)) {
                Text(heading, style = AppTheme.type.callout.copy(fontWeight = FontWeight.Bold), color = colors.ink)
                Text(body, style = AppTheme.type.footnote, color = colors.ink2)
            }
        }

        // Footer link out to the hosted version — the operator repoints the URL if needed.
        Text(
            "View online at ${doc.url.removePrefix("https://").substringBefore('/')}",
            style = AppTheme.type.caption,
            color = colors.brand,
            modifier = Modifier.clickable {
                context.startActivity(Intent(Intent.ACTION_VIEW, doc.url.toUri()))
            },
        )

        PrimaryButton(text = "Close", onClick = onClose, fullWidth = true)
    }
}
