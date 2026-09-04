package `in`.artistant.app.feature.profile

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.data.repository.ExportResult
import `in`.artistant.app.designsystem.component.AccentNote
import `in`.artistant.app.designsystem.component.BackHeader
import `in`.artistant.app.designsystem.component.Banner
import `in`.artistant.app.designsystem.component.BannerTone
import `in`.artistant.app.designsystem.component.CheckRow
import `in`.artistant.app.designsystem.component.EyebrowLabel
import `in`.artistant.app.designsystem.component.MarkState
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.component.SecondaryButton
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.ArtistantTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * The four states design screens 81 → 82 → 49 / 113 draw, and the only four this screen has.
 *
 * A sealed hierarchy rather than a bag of booleans: `isRequesting && !failed && file != null`
 * has sixteen combinations and twelve of them are nonsense, and the one that mattered most —
 * FAILED WITH A FILE — is the exact thing the design's note forbids ("a failed export produces
 * no file at all, so you never get half your data and assume it's everything"). Making it
 * unrepresentable is cheaper than testing for it.
 */
sealed interface ExportState {
    /** 81 — the right and the contents, before anyone commits to a 24-hour wait. */
    data object Idle : ExportState

    /** 82 — the job is on the server and does not depend on the app staying open. */
    data object Requested : ExportState

    /** 49 — a file exists, and it expires. */
    data class Ready(val result: ExportResult) : ExportState

    /** 113 — nothing was produced, and the screen says which step stopped. */
    data class Failed(val reason: String) : ExportState
}

data class DataExportUiState(
    val export: ExportState = ExportState.Idle,
    /** Set once, when a Ready file is handed to the share sheet. Cleared after the handoff. */
    val pendingShare: ExportResult? = null,
    /** A handoff the device could not complete — no browser, no share target. */
    val handoffError: String? = null,
)

/**
 * The screen's own transient state — the two facts that belong to THIS visit and no other.
 *
 * Everything about the request itself lives in [DataExportStore], because it outlives the
 * screen. A share handoff does not: it is one intent, launched from one composition, and
 * carrying it in the singleton would re-fire the chooser on the next visit.
 */
private data class ExportHandoff(
    val pendingShare: ExportResult? = null,
    val handoffError: String? = null,
)

@HiltViewModel
class DataExportViewModel @Inject constructor(
    private val store: DataExportStore,
) : ViewModel() {
    private val handoff = MutableStateFlow(ExportHandoff())

    val state: StateFlow<DataExportUiState> = combine(store.state, handoff) { export, local ->
        DataExportUiState(
            export = export,
            pendingShare = local.pendingShare,
            handoffError = local.handoffError,
        )
    }.stateIn(
        scope = viewModelScope,
        // Eagerly, so `state.value` is the store's answer the instant the screen is built —
        // including the Requested this ViewModel was created into after the user navigated
        // away and back. Lazily it would read Idle for one frame and flash screen 81 over a
        // request that is still running.
        started = SharingStarted.Eagerly,
        initialValue = DataExportUiState(export = store.state.value),
    )

    /** @see DataExportStore.request */
    fun request() {
        handoff.update { it.copy(handoffError = null) }
        store.request()
    }

    /** Hand the finished file to the system. Only reachable from [ExportState.Ready]. */
    fun share() {
        val ready = store.state.value as? ExportState.Ready ?: return
        handoff.value = ExportHandoff(pendingShare = ready.result)
    }

    fun clearPendingShare() = handoff.update { it.copy(pendingShare = null) }

    fun reportHandoffError(message: String) = handoff.update { it.copy(handoffError = message) }

    /** @see DataExportStore.stopWaiting */
    fun stopWaiting() = store.stopWaiting()
}

/**
 * Design screens 49 / 81 / 82 / 113 — **"Idle → requested → ready", and a failure that ships
 * nothing**.
 *
 * One destination with four states, because they are four states of one request and splitting
 * them into four routes would mean a back stack that walks backwards through your own export.
 *
 * **What is real and what is stated.** The file, its expiry and its contents come from the
 * `data-export` Edge Function (`AccountRepository`), which answers either inline JSON or a
 * one-hour signed URL. The design writes "expires in 7 days" and "2.4 MB"; this screen prints
 * the expiry the SERVER gave it, and prints no size at all for an inline payload, because a
 * number invented for a progress line is still an invented number.
 *
 * **The contents list is the DPDP promise, not a manifest.** It names what the export covers.
 * The design's version counts them ("128 bookings with full terms"); counting them here would
 * need a second read of every table just to caption a list, so the rows name the categories and
 * the export itself carries the rows.
 *
 * **Failed produces nothing.** [ExportState] makes "failed with a file" unrepresentable, and
 * the failed screen says so out loud — the design's own note is that half an export that looks
 * whole is worse than none.
 */
@Composable
fun DataExportScreen(
    onBack: () -> Unit,
    /** The scripted support assistant (design 34) — a failed export is what it is for. */
    onContactSupport: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DataExportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(state.pendingShare) {
        val pending = state.pendingShare ?: return@LaunchedEffect
        val failure = when (pending) {
            is ExportResult.Inline -> handOff(
                context,
                Intent.createChooser(exportShareIntent(pending.json), "Share export"),
                "Couldn't open the share sheet — no app on this device can take the export.",
            )
            is ExportResult.SignedUrl -> handOff(
                context,
                exportViewIntent(pending.url),
                "Couldn't open a browser for the export link on this device.",
            )
        }
        failure?.let(viewModel::reportHandoffError)
        viewModel.clearPendingShare()
    }

    DataExportContent(
        state = state,
        onBack = onBack,
        onRequest = viewModel::request,
        onShare = viewModel::share,
        onStopWaiting = viewModel::stopWaiting,
        onContactSupport = onContactSupport,
        modifier = modifier,
    )
}

@Composable
private fun DataExportContent(
    state: DataExportUiState,
    onBack: () -> Unit,
    onRequest: () -> Unit,
    onShare: () -> Unit,
    onStopWaiting: () -> Unit,
    onContactSupport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val export = state.export

    AccountScaffold(
        modifier = modifier.semantics { testTag = "screen.dataExport" },
        header = {
            BackHeader(title = "Data export", onBack = onBack, subtitle = exportSubtitle(export))
        },
        footer = {
            state.handoffError?.let { message ->
                Text(
                    message,
                    style = AppTheme.type.caption,
                    color = colors.danger,
                    modifier = Modifier.semantics { testTag = "export.handoffError" },
                )
            }
            when (export) {
                ExportState.Idle -> PrimaryButton(
                    text = "Request my data",
                    onClick = onRequest,
                    fullWidth = true,
                    modifier = Modifier.semantics { testTag = "export.request" },
                )
                ExportState.Requested -> SecondaryButton(
                    text = "Stop waiting here",
                    onClick = onStopWaiting,
                    fullWidth = true,
                    modifier = Modifier.semantics { testTag = "export.stopWaiting" },
                )
                is ExportState.Ready -> PrimaryButton(
                    text = "Request a fresh export",
                    onClick = onRequest,
                    fullWidth = true,
                    modifier = Modifier.semantics { testTag = "export.refresh" },
                )
                is ExportState.Failed -> {
                    PrimaryButton(
                        text = "Try again",
                        onClick = onRequest,
                        fullWidth = true,
                        modifier = Modifier.semantics { testTag = "export.retry" },
                    )
                    SecondaryButton(
                        text = "Contact Support",
                        onClick = onContactSupport,
                        fullWidth = true,
                    )
                }
            }
        },
    ) {
        AccountGap()
        when (export) {
            ExportState.Idle -> ExportIdle()
            ExportState.Requested -> ExportRequested()
            is ExportState.Ready -> ExportReady(export.result, onShare)
            is ExportState.Failed -> ExportFailed(export.reason)
        }
        Spacer(Modifier.height(dimens.size.listTailroom))
    }
}

/** 81 — the right, then the contents, then the wait. Nothing has been asked for yet. */
@Composable
private fun ExportIdle() {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    AccountPageTitle(
        "Get a copy of everything",
        body = "Per India's Digital Personal Data Protection Act, you can request a " +
            "structured copy of every piece of information Artistant stores about you.",
    )
    AccountGap()
    EyebrowLabel("What's included", color = colors.ink4)
    Spacer(Modifier.height(dimens.space.sm))
    EXPORT_CONTENTS.forEachIndexed { index, item ->
        CheckRow(
            title = item,
            state = MarkState.Done,
            showHairline = index != EXPORT_CONTENTS.lastIndex,
        )
    }
    AccountGap()
    AccentNote(
        text = "One JSON file. Requests take up to 24 hours to assemble and we notify you " +
            "here — nothing is emailed unprompted.",
    )
}

/** 82 — three steps, and permission to walk away. */
@Composable
private fun ExportRequested() {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.surface3, RoundedCornerShape(dimens.radii.xl))
            .padding(dimens.space.lg)
            .semantics(mergeDescendants = true) { testTag = "export.assembling" },
        horizontalArrangement = Arrangement.spacedBy(dimens.space.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The screen's ONE spinner, and it is about the screen rather than about a step —
        // "narrated, not a spinner" (REDESIGN_2026-09 §2) applies to the step list below,
        // which says what is happening in words.
        CircularProgressIndicator(
            Modifier.size(dimens.size.avatarSm),
            strokeWidth = dimens.size.stroke,
            color = colors.accent,
            trackColor = colors.hairline,
        )
        Column(Modifier.weight(1f)) {
            Text("Assembling your file", style = AppTheme.type.sectionTitle, color = colors.ink)
            Text(
                "Usually under an hour, up to 24",
                style = AppTheme.type.subtitle,
                color = colors.ink4,
                modifier = Modifier.padding(top = dimens.space.xs / 2),
            )
        }
    }
    AccountGap()
    CheckRow("Request received", MarkState.Done, subtitle = "Just now")
    CheckRow("Collecting your records", MarkState.Active, subtitle = "In progress")
    CheckRow(
        "File ready to download",
        MarkState.Pending,
        subtitle = "We'll notify you here",
        dimWhenPending = true,
    )
    AccountGap()
    AccentNote(
        text = "You can leave this screen. The request keeps running on our side and does not " +
            "depend on the app staying open.",
    )
}

/** 49 — the file, its expiry, and the one thing to do with it. */
@Composable
private fun ExportReady(result: ExportResult, onShare: () -> Unit) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    AccountPageTitle(
        "Your copy is ready",
        body = "One JSON file with everything Artistant holds about you.",
    )
    AccountGap()
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.surface3, RoundedCornerShape(dimens.radii.xl))
            .padding(dimens.space.lg)
            .semantics { testTag = "export.readyCard" },
        verticalArrangement = Arrangement.spacedBy(dimens.space.md),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(dimens.size.avatarSm).background(colors.accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Description,
                    contentDescription = null,
                    tint = colors.onAccent,
                    modifier = Modifier.size(dimens.size.iconLg),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(EXPORT_FILE_NAME, style = AppTheme.type.rowTitle, color = colors.ink)
                Text(
                    exportReadyDetail(result),
                    style = AppTheme.type.caption,
                    color = colors.ink4,
                    modifier = Modifier.padding(top = dimens.space.xs / 2),
                )
            }
        }
        PrimaryButton(
            text = "Share the file",
            onClick = onShare,
            fullWidth = true,
            modifier = Modifier.semantics { testTag = "export.share" },
        )
    }
    AccountGap()
    EyebrowLabel("What's in it", color = colors.ink4)
    Spacer(Modifier.height(dimens.space.sm))
    EXPORT_CONTENTS.forEachIndexed { index, item ->
        CheckRow(
            title = item,
            state = MarkState.Done,
            showHairline = index != EXPORT_CONTENTS.lastIndex,
        )
    }
    AccountGap()
    AccentNote(
        text = "Requests take up to 24 hours to assemble. We'll notify you here — nothing is " +
            "emailed unprompted.",
    )
}

/** 113 — the step that stopped, and the promise that nothing partial went out. */
@Composable
private fun ExportFailed(reason: String) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Banner(
        title = "Couldn't build your export",
        tone = BannerTone.Failure,
        detail = reason,
        modifier = Modifier.semantics { testTag = "export.failedBanner" },
    )
    AccountGap()
    CheckRow("Request received", MarkState.Done, subtitle = "Sent to the server")
    CheckRow("Collecting your records", MarkState.Failed, subtitle = reason)
    CheckRow("File ready", MarkState.Pending, subtitle = "Not reached", dimWhenPending = true)
    AccountGap()
    Banner(
        title = "Nothing partial is sent",
        tone = BannerTone.Attention,
        detail = "A failed export produces no file at all, so you never get half your data " +
            "and assume it's everything.",
    )
    AccountGap()
    EyebrowLabel("Your right is unaffected", color = colors.ink4)
    Spacer(Modifier.height(dimens.space.sm))
    Text(
        "Under the DPDP Act we owe you this file. If it fails twice, Support can raise it " +
            "manually — the request is logged either way.",
        style = AppTheme.type.body,
        color = colors.ink3,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** The header's second line — where in the flow you are, in the design's own words. */
internal fun exportSubtitle(export: ExportState): String = when (export) {
    ExportState.Idle -> "Your right under the DPDP Act"
    ExportState.Requested -> "Requested just now"
    is ExportState.Ready -> "Ready to share"
    is ExportState.Failed -> "Request failed"
}

/**
 * The line under the file name.
 *
 * A signed URL carries a real expiry from the server, so that expiry is printed. Inline JSON
 * has none — it is already in memory and there is nothing to expire — so the line says what is
 * actually true about it rather than borrowing the URL's seven days. Neither prints a SIZE: the
 * design draws "2.4 MB" and the function does not report one, and a plausible number on a
 * privacy export is still a made-up number.
 */
internal fun exportReadyDetail(result: ExportResult): String = when (result) {
    is ExportResult.Inline -> "Ready · in this app until you leave the screen"
    is ExportResult.SignedUrl -> "Ready · link expires in ${expiryLabel(result.expiresInSeconds)}"
}

/** "1 hour", "45 minutes", "7 days" — a duration a person would say out loud. */
internal fun expiryLabel(seconds: Int): String {
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    return when {
        days >= 1 -> if (days == 1) "1 day" else "$days days"
        hours >= 1 -> if (hours == 1) "1 hour" else "$hours hours"
        minutes >= 1 -> if (minutes == 1) "1 minute" else "$minutes minutes"
        else -> "under a minute"
    }
}

/** What the export covers. The categories, in the design's order — not a row count. */
private val EXPORT_CONTENTS = listOf(
    "Profile and account details",
    "Bookings with full terms",
    "Every message you have sent",
    "Saved artists",
    "Reviews you wrote",
    "Artist profile and score history",
)

private const val EXPORT_FILE_NAME = "artistant-export.json"

/**
 * Hand an intent to the system, or return the line to show when nothing on the device can take
 * it.
 *
 * `startActivity` throws ActivityNotFoundException on the main thread straight out of a click
 * handler, which is an app crash from tapping Share. Both handoffs here are genuinely
 * unresolvable in the field: a share target is not guaranteed, and an `ACTION_VIEW` on an https
 * URL needs a browser, which a locked-down or work-profile device may not offer.
 */
private fun handOff(context: Context, intent: Intent, failure: String): String? =
    runCatching { context.startActivity(intent); null }.getOrElse { failure }

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, heightDp = 900)
@Composable
private fun DataExportIdlePreview() {
    ArtistantTheme {
        DataExportContent(
            state = DataExportUiState(),
            onBack = {},
            onRequest = {},
            onShare = {},
            onStopWaiting = {},
            onContactSupport = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, heightDp = 900)
@Composable
private fun DataExportFailedPreview() {
    ArtistantTheme {
        DataExportContent(
            state = DataExportUiState(export = ExportState.Failed("Stopped at bookings")),
            onBack = {},
            onRequest = {},
            onShare = {},
            onStopWaiting = {},
            onContactSupport = {},
        )
    }
}
