package `in`.artistant.app.feature.signup

import android.content.Context
import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.net.toUri
import `in`.artistant.app.designsystem.component.Banner
import `in`.artistant.app.designsystem.component.BannerTone
import `in`.artistant.app.designsystem.component.IconCircle
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.ArtistantTheme
import `in`.artistant.app.designsystem.theme.motion
import `in`.artistant.app.designsystem.theme.motionTween

/**
 * Which legal document the viewer is showing. Mirrors iOS `LegalDoc`.
 *
 * [updated] is the date the SHIPPED text below was written, not "today" — a policy screen that
 * always claims to be current is a policy screen nobody can tell has changed.
 */
enum class LegalDoc(val title: String, val tab: String, val updated: String, val url: String) {
    // The hosted canonical URLs (iOS AppEnvironment.{privacy,terms}PolicyURL). www. is the
    // canonical host per the iOS note; no BuildConfig field for these yet so they're constants.
    Terms("Terms of use", "Terms", "Updated 12 June 2026", "https://www.artistant.in/legal/terms"),
    Privacy("Privacy Policy", "Privacy", "Updated 12 June 2026", "https://www.artistant.in/legal/privacy"),
}

/**
 * The in-app text of each document.
 *
 * Rewritten to the redesign's copy (screens 31 and 114), which is shorter and — more
 * importantly — CORRECT for v1: the old eleven-section terms described platform fees,
 * refunds and dispute mediation on a product that takes no payment and holds no funds. A
 * summary that describes a different product is worse than no summary. What is here is the
 * plain-language version; the hosted document at [LegalDoc.url] is the authoritative one, which
 * is what the footer row says.
 */
private val termsSections = listOf(
    "1 · What Artistant is" to
        "Artistant is an introduction service. We list artists, carry the messages between you, " +
        "and record what was agreed. We are not a party to the booking itself and we do not " +
        "employ the artists on the platform.",
    "2 · Money" to
        "This version of Artistant takes no payment and holds no funds. Fees quoted in the app " +
        "are what you settle directly with the artist. Any future change to this will be " +
        "notified before it applies.",
    "3 · Your account" to
        "You must be 18 or older. One person, one account. Handles are yours while your account " +
        "is active and may be reclaimed after deletion.",
    "4 · Conduct" to
        "The community commitment you agreed to at signup forms part of these terms. Accounts " +
        "that break it can be suspended without notice.",
)

private val privacySections = listOf(
    "WHAT WE COLLECT" to
        "Your name, city, handle, and the contents of bookings and messages you send. We do not " +
        "collect your contacts, precise location, or advertising identifiers.",
    "WHO SEES YOUR NUMBER" to
        "No artist sees your phone number before a booking is confirmed. After that, both sides " +
        "see each other's so the night can actually happen.",
    "WHAT WE NEVER DO" to
        "We do not sell your data, we run no third-party ad trackers, and we do not read your " +
        "messages except when you report a conversation.",
    "YOUR RIGHTS UNDER THE DPDP ACT" to
        "You can export everything we hold, correct it, or delete your account outright. " +
        "Deletion removes mirrored calendar events and queued reports too.",
)

/**
 * Screens 31 and 114 — **one viewer, two documents**.
 *
 * The design's note for 114 says why they share a screen: "Terms and Privacy share a screen, so
 * the pair is always one tap apart." Anyone reading one of these is deciding whether to trust
 * the product, and making them back out and find the other one is the moment they stop.
 *
 * [doc] seeds the segment; the segment is then the user's, not the caller's. The trailing
 * circle and the footer row both open the hosted document, which the design calls "the
 * authoritative copy" — and the failure to open it is reported rather than swallowed, because
 * this screen is reachable before any account exists and an image with no browser is a real
 * device (kiosk builds, stripped emulators).
 */
@Composable
fun LegalScreen(doc: LegalDoc, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val space = dimens.space
    val context = LocalContext.current
    var selected by remember { mutableStateOf(doc) }
    var linkError by remember { mutableStateOf<String?>(null) }
    val sections = if (selected == LegalDoc.Terms) termsSections else privacySections

    SignupScaffold(
        modifier = modifier.semantics { testTag = "screen.legal" },
        header = {
            SignupHeader(
                onBack = onClose,
                title = selected.title,
                subtitle = selected.updated,
                titleAtStart = true,
                trailing = {
                    IconCircle(
                        icon = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "Open ${selected.title} online",
                        onClick = { linkError = openLegalDoc(context, selected.url) },
                        size = dimens.component.iconCircleSm,
                    )
                },
            )
        },
        footer = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(dimens.radii.md))
                    .clickable(role = Role.Button) { linkError = openLegalDoc(context, selected.url) }
                    .padding(vertical = space.sm)
                    .semantics(mergeDescendants = true) { testTag = "legal.openOnline" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (selected == LegalDoc.Terms) {
                        "Read the full document online"
                    } else {
                        "Read the full policy online"
                    },
                    style = AppTheme.type.subtitle,
                    color = colors.ink3,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    tint = colors.ink4,
                    modifier = Modifier.size(dimens.size.iconMd),
                )
            }
            linkError?.let {
                Banner(title = it, tone = BannerTone.Failure)
            }
        },
    ) {
        Spacer(Modifier.height(space.md))
        LegalSegments(selected = selected, onSelect = { selected = it; linkError = null })
        Spacer(Modifier.height(space.lg))

        sections.forEach { (heading, body) ->
            SignupEyebrow(heading)
            Spacer(Modifier.height(space.sm))
            Text(body, style = AppTheme.type.body, color = colors.ink2)
            Spacer(Modifier.height(space.lg))
        }
        Spacer(Modifier.height(space.md))
    }
}

/**
 * The two-up segmented control (screen 114).
 *
 * Not `Chip`: a chip rail is a filter, where zero or many can be on and the selected one takes
 * the accent. This is a segmented control — exactly one is always on, and the design draws the
 * selected half in `hairline` rather than lime, because the accent on this screen belongs to
 * the links inside the copy.
 */
@Composable
private fun LegalSegments(selected: LegalDoc, onSelect: (LegalDoc) -> Unit) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.radii.control))
            .background(colors.surface3)
            .padding(dimens.space.xs),
        horizontalArrangement = Arrangement.spacedBy(dimens.space.xs),
    ) {
        LegalDoc.entries.forEach { entry ->
            val isOn = entry == selected
            val fill by animateColorAsState(
                targetValue = if (isOn) colors.hairline else Color.Transparent,
                animationSpec = motionTween<Color>(AppTheme.motion.tabSwitch),
                label = "legalSegment",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(dimens.radii.md))
                    .background(fill)
                    .clickable(role = Role.Tab) { onSelect(entry) }
                    .padding(vertical = dimens.space.sm)
                    .semantics(mergeDescendants = true) {
                        this.selected = isOn
                        contentDescription = entry.title
                        testTag = "legal.tab.${entry.name.lowercase()}"
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    entry.tab,
                    style = AppTheme.type.chip.copy(
                        fontWeight = if (isOn) FontWeight.Bold else FontWeight.Medium,
                    ),
                    color = if (isOn) colors.ink else colors.ink3,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * Hand the hosted document to the browser, reporting a failure instead of taking the app with it.
 *
 * `startActivity` throws `ActivityNotFoundException` on an image with no http(s) VIEW handler,
 * and this screen hangs off the welcome step — the first thing a new user sees, before any
 * session exists. Null on success, the message to show on failure.
 */
private fun openLegalDoc(context: Context, url: String): String? =
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())); null }
        .getOrElse { "Couldn't open a browser on this device." }

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, heightDp = 860)
@Composable
private fun TermsPreview() {
    ArtistantTheme { LegalScreen(doc = LegalDoc.Terms, onClose = {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, heightDp = 860)
@Composable
private fun PrivacyPolicyPreview() {
    ArtistantTheme { LegalScreen(doc = LegalDoc.Privacy, onClose = {}) }
}
