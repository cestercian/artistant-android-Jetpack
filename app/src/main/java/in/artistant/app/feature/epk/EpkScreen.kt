package `in`.artistant.app.feature.epk

import android.Manifest
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.artistant.app.designsystem.component.Banner
import `in`.artistant.app.designsystem.component.BannerTone
import `in`.artistant.app.designsystem.component.EmptyState
import `in`.artistant.app.designsystem.component.IconCircle
import `in`.artistant.app.designsystem.component.RevealOnAppear
import `in`.artistant.app.designsystem.component.ScreenHeader
import `in`.artistant.app.designsystem.component.SectionHeader
import `in`.artistant.app.designsystem.component.SheetScaffold
import `in`.artistant.app.designsystem.component.ToastHost
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.domain.artist.ArtistPrompts
import `in`.artistant.app.domain.artist.PackagePricing
import `in`.artistant.app.platform.media.WizardMediaCache
import `in`.artistant.app.platform.media.rememberSamplePlayer
import java.io.File
import java.util.UUID

/**
 * The press kit — design screens **23** (filled), **87** (empty), **76**
 * (uploading), with its sheets in `EpkSheets.kt` and its section editors in
 * `EpkPanes.kt`.
 *
 * **It is a hub now, not a form.** The editor used to be one nine-section scroll
 * that opened on a cover the artist could not edit and asked for a tech rider
 * before it had asked who they are. The redesign's argument — "completion with a
 * payoff", "empty rows are invitations" — only works if the page can be READ in
 * one screen: a meter, the two things a client sees first (cover, gallery), and
 * a list that states, per section, either the fact or what the gap costs.
 * Editing happens one section at a time, behind a row.
 *
 * **The sub-editors are panes, not destinations.** They are not in the NavHost
 * because they are all views of ONE ViewModel with one debounced write queue and
 * one upload queue; giving each a nav entry would mean either a second Hilt
 * scope per section or `hiltViewModel(parentEntry)` plumbing through the artist
 * graph — machinery bought for a back gesture that [BackHandler] already
 * provides. `rememberSaveable` keeps the open pane across a config change, which
 * is the other thing a destination would have given us.
 *
 * **Three load branches, three different screens** (§2): a spinner while the
 * profile is being read, a route into the wizard when there is no profile yet,
 * and a named failure with a retry when there was one and the read failed. They
 * are not the same empty page with different words.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpkScreen(
    onEditInWizard: () -> Unit,
    onOpenAccount: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EpkViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val context = LocalContext.current

    var pane by rememberSaveable { mutableStateOf<EpkPane?>(null) }
    var sheet by rememberSaveable { mutableStateOf<EpkSheetKind?>(null) }

    // ── Pickers ──────────────────────────────────────────────────────────────
    // The Uri alone. `uri.lastPathSegment` looks like a filename and is not one —
    // SAF returns a document id ("audio:1000000042"), which used to become the
    // clip's title on the artist's public profile. The ViewModel resolves the real
    // display name off the main thread instead.
    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.onSamplePicked(uri)
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.onPhotoPicked(uri)
    }
    // A capture needs its destination URI before the intent fires, so the file is
    // minted here and held across the permission round-trip.
    //
    // `rememberSaveable`, not `remember`: the camera is a separate full-screen
    // activity and ours can be recreated behind it (low memory, "don't keep
    // activities", a rotation while the shutter is up). The result registry
    // restores the pending launch and still reports ok=true, so a plain
    // `remember` comes back null and the capture is dropped without a word, with
    // the photo sitting on disk under a name nothing remembers.
    var cameraUri by rememberSaveable { mutableStateOf<String?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) cameraUri?.let { viewModel.onPhotoPicked(Uri.parse(it)) }
    }
    val launchCamera = {
        val file = File(context.cacheDir, "$CAMERA_DIR/${UUID.randomUUID()}.jpg")
            .also { it.parentFile?.mkdirs() }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        cameraUri = uri.toString()
        takePicture.launch(uri)
    }
    val requestCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // A refusal has to say something. After the second one the OS stops
        // showing the dialog at all, so without this the row is simply dead and
        // looks identical to the working one above it.
        if (granted) launchCamera() else viewModel.onCameraUnavailable()
    }

    // Leaving the app cashes in the debounce. Every edit here autosaves after a
    // 1.2s wait, and a wait being counted by a backgrounded process is a wait the
    // OS can end by reclaiming it — a kill calls no `onCleared`, so the last thing
    // typed before the home button would simply never reach the server, silently,
    // while the meter at the top of the screen had already counted it. ON_STOP is
    // the last thing we are told while the process is still ours.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.flushPendingSaves()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // A pane is a screen as far as the back gesture is concerned.
    BackHandler(enabled = pane != null) { pane = null }

    // The player is remembered at SCREEN scope, above every pane and every lazy
    // item. A lazy item's composition ends when it scrolls out of the viewport,
    // which released the ExoPlayer — and with it the only control that could stop
    // the clip — mid-listen. Scoped here it matches the player's documented
    // lifetime: leave the screen and the clip is done.
    //
    // Built unconditionally: an idle ExoPlayer holds no codec until `prepare`.
    val player = rememberSamplePlayer(state.samples)

    Box(modifier.fillMaxSize().background(colors.page)) {
        Column(
            Modifier
                .fillMaxSize()
                // The window is edge-to-edge, so nothing resizes for the keyboard
                // on its own, and the panes are all fields. `exclude(navigationBars)`
                // for the reason the chat composer does it: the tab scaffold has
                // already paid that inset once.
                .windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars)),
        ) {
            val open = pane
            if (open == null) {
                PressKitHeader(
                    state = state,
                    onOpenAccount = onOpenAccount,
                    modifier = Modifier
                        .padding(horizontal = dimens.component.gutter)
                        .padding(top = dimens.space.md, bottom = dimens.space.lg),
                )
                PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    when {
                        state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                            CircularProgressIndicator(color = colors.accentInk)
                        }
                        // Not published yet. The wizard is the correct destination
                        // HERE and only here: there is no server-side profile for a
                        // re-publish to overwrite, which stops being true the moment
                        // the artist has one.
                        state.artist == null && !state.setupComplete -> EmptyState(
                            title = "Your profile isn't live yet",
                            body = "Finish the setup wizard and your press kit appears here, ready to edit.",
                            actionLabel = "Finish your profile",
                            onAction = onEditInWizard,
                            modifier = Modifier.align(Alignment.Center),
                        )
                        state.artist == null -> EmptyState(
                            title = "Couldn't load your press kit",
                            body = state.loadError,
                            actionLabel = "Retry",
                            onAction = viewModel::refresh,
                            modifier = Modifier.align(Alignment.Center),
                        )
                        else -> RevealOnAppear {
                            PressKitBody(
                                state = state,
                                viewModel = viewModel,
                                onOpenPane = { pane = it },
                                onOpenSheet = { sheet = it },
                            )
                        }
                    }
                }
            } else {
                EpkSectionPane(
                    pane = open,
                    state = state,
                    viewModel = viewModel,
                    player = player,
                    onBack = { pane = null },
                    onAddPhoto = { pickPhoto.launch(PHOTO_MIME) },
                    onAddSample = { sheet = EpkSheetKind.AddAudio },
                )
            }
        }
        // Transient confirmations for writes with no visible result ("Pricing
        // saved."). The host clears itself, so nothing here has to remember to.
        ToastHost(message = state.statusNote, onDismiss = viewModel::consumeStatusNote)
    }

    // ── Sheets ───────────────────────────────────────────────────────────────
    when (sheet) {
        null -> Unit
        EpkSheetKind.AddCover -> EpkModalSheet(onDismiss = { sheet = null }) {
            AddCoverSheet(
                hasCover = state.photos.isNotEmpty(),
                onTakePhoto = {
                    sheet = null
                    requestCamera.launch(Manifest.permission.CAMERA)
                },
                onChooseFromLibrary = {
                    sheet = null
                    pickPhoto.launch(PHOTO_MIME)
                },
                onClose = { sheet = null },
            )
        }
        EpkSheetKind.EditBio -> EpkModalSheet(onDismiss = { sheet = null }) {
            EditBioSheet(
                bio = state.bioDraft,
                services = shownServiceTags(state.serviceTags, state.artist?.serviceTags.orEmpty()),
                canEdit = state.identityHydrated,
                onBio = viewModel::onBioChanged,
                onToggleService = viewModel::onServiceTagToggled,
                // Save ends the debounce and closes; it is not a second write
                // path. See the sheet's own KDoc.
                onSave = {
                    viewModel.flushPendingSaves()
                    sheet = null
                },
                onCancel = { sheet = null },
                onClose = { sheet = null },
            )
        }
        EpkSheetKind.Personality -> EpkModalSheet(onDismiss = { sheet = null }) {
            AddPersonalitySheet(
                drafts = state.promptDrafts,
                canEdit = state.identityHydrated,
                onAnswer = viewModel::onPromptAnswerChanged,
                onSave = {
                    viewModel.flushPendingSaves()
                    sheet = null
                },
                onSkip = { sheet = null },
                onClose = { sheet = null },
            )
        }
        EpkSheetKind.AddAudio -> EpkModalSheet(onDismiss = { sheet = null }) {
            AddAudioSheet(
                storedCount = state.samples.size,
                uploadingCount = state.samplesUploading + state.samplesStaging,
                onChooseFile = {
                    sheet = null
                    // Only what the samples bucket accepts (migration 0010).
                    // `audio/*` offered WAV and OGG, which stage and publish and
                    // then 400 in the upload queue — three retries later, on a
                    // failure set no screen used to read.
                    pickAudio.launch(WizardMediaCache.ACCEPTED_AUDIO_MIME_TYPES.toTypedArray())
                },
                onClose = { sheet = null },
            )
        }
        EpkSheetKind.Stalled -> EpkModalSheet(onDismiss = { sheet = null }) {
            StalledUploadsSheet(
                uploads = state.stalledUploads,
                onRetry = viewModel::retryStalledUpload,
                onDiscard = viewModel::discardStalledUpload,
                onRetryAll = {
                    viewModel.retryFailedUploads()
                    sheet = null
                },
                onClose = { sheet = null },
            )
        }
    }

    // The link sheet is driven by the ViewModel rather than by `sheet`, because
    // WHICH link is being edited is state the screen cannot hold — a config
    // change mid-edit has to come back to the same row with the same draft in it.
    val editor = state.linkEditor
    if (editor != null) {
        EpkModalSheet(onDismiss = viewModel::dismissLinkEditor) {
            EditLinkSheet(
                editor = editor,
                busy = state.busyLinks,
                onLabel = viewModel::onLinkEditorLabel,
                onUrl = viewModel::onLinkEditorUrl,
                onSave = viewModel::saveLinkEditor,
                onRemove = { editor.id?.let(viewModel::deleteLink) },
                onCancel = viewModel::dismissLinkEditor,
                onClose = viewModel::dismissLinkEditor,
            )
        }
    }

    // The stalled sheet has nothing to show once the last burned upload is
    // retried or discarded, and an empty sheet is a sheet that looks broken.
    if (sheet == EpkSheetKind.Stalled && state.stalledUploads.isEmpty()) sheet = null
}

/** Which sheet is up. Saveable, so a rotation mid-pick does not lose it. */
enum class EpkSheetKind { AddCover, EditBio, Personality, AddAudio, Stalled }

/**
 * The M3 sheet with the design's container on it.
 *
 * `dragHandle = null` because `SheetScaffold` draws the grabber, and Material's
 * own is an M3-coloured pill. Everything visible comes from our side; what
 * Material keeps is the scrim, the drag gesture and the predictive-back
 * contract, which are worth having and not worth reimplementing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EpkModalSheet(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = AppTheme.colors.surface,
    ) {
        SheetScaffold { content() }
    }
}

/**
 * The page's masthead — screen 87's title and gear.
 *
 * The gear is the artist's ACCOUNT, and it is deliberately here: this is the
 * surface an artist opens to work on their own profile, which is where they come
 * looking for sign-out, data export and account deletion. It is outside the
 * load/empty/error branch for the same reason — a profile that failed to load
 * must not take the only route to account settings down with it.
 *
 * The subtitle is the completion percentage (screen 23's "86% complete") and it
 * is absent until there is a profile to measure, because "0% complete" over a
 * spinner is a number about nothing.
 */
@Composable
private fun PressKitHeader(
    state: EpkUiState,
    onOpenAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val artist = state.artist
    val subtitle = if (artist == null) {
        null
    } else {
        "${epkCompletion(state.sectionRows(), hasCover = state.photos.isNotEmpty()).percent}% complete"
    }
    ScreenHeader(
        title = "Press kit",
        subtitle = subtitle,
        modifier = modifier,
        trailing = {
            IconCircle(
                icon = Icons.Filled.Settings,
                contentDescription = "Account and settings",
                onClick = onOpenAccount,
                size = AppTheme.dimens.hero.avatarSize,
            )
        },
    )
}

/**
 * The scroll: banners, cover, gallery, sections — screens 23 / 87 / 76.
 *
 * One lazy container with semantic keys, so an offscreen block is not composed
 * and reordering keeps each item's identity.
 *
 * **`bare` is what switches between 23 and 87.** Not "the artist is new" — a kit
 * that has been emptied is in exactly the same position as one that was never
 * filled, and both need the invitations rather than a meter reading zero.
 */
@Composable
private fun PressKitBody(
    state: EpkUiState,
    viewModel: EpkViewModel,
    onOpenPane: (EpkPane) -> Unit,
    onOpenSheet: (EpkSheetKind) -> Unit,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val gutter = Modifier.padding(horizontal = dimens.component.gutter)
    val rows = state.sectionRows()
    val hasCover = state.photos.isNotEmpty()
    val completion = epkCompletion(rows, hasCover = hasCover)
    val bare = completion.filled == 0

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = dimens.hero.scrollTailroom),
        verticalArrangement = Arrangement.spacedBy(dimens.space.lg),
    ) {
        state.uploadBanner?.let { banner ->
            item(key = "upload") {
                EpkQueueBanner(
                    banner = banner,
                    // Working uploads have no detail screen of their own — the
                    // gallery is where the cell for the in-flight photo is, and
                    // the samples pane is where a clip lands. A stalled queue has
                    // the sheet that can retry or discard it.
                    onDetails = {
                        if (banner is EpkUploadBanner.Stalled) {
                            onOpenSheet(EpkSheetKind.Stalled)
                        } else {
                            onOpenPane(EpkPane.Gallery)
                        }
                    },
                    modifier = gutter,
                )
            }
        }
        state.loadError?.let { message ->
            item(key = "loadError") {
                Banner(
                    title = "Couldn't refresh your press kit",
                    tone = BannerTone.Failure,
                    detail = message,
                    actionLabel = "Retry",
                    onAction = viewModel::refresh,
                    modifier = gutter,
                )
            }
        }
        state.saveError?.let { message ->
            item(key = "saveError") {
                Banner(
                    title = "That edit didn't save",
                    tone = BannerTone.Failure,
                    detail = message,
                    actionLabel = "Dismiss",
                    onAction = viewModel::dismissSaveError,
                    modifier = gutter,
                )
            }
        }
        if (bare) {
            item(key = "bare") {
                Banner(
                    title = if (state.setupComplete) {
                        "Your profile is live but bare. Hosts can book you — they just can't see or hear anything yet."
                    } else {
                        "Nothing here yet. Everything you add goes live with your profile."
                    },
                    tone = BannerTone.Note,
                    modifier = gutter,
                )
            }
        } else {
            item(key = "completion") {
                EpkCompletionMeter(completion, modifier = gutter)
            }
        }
        item(key = "cover") {
            Column(gutter, verticalArrangement = Arrangement.spacedBy(dimens.space.md)) {
                if (!bare) SectionHeader("Cover")
                EpkCoverBlock(
                    coverUrl = state.photos.firstOrNull()?.publicUrl,
                    onAdd = { onOpenSheet(EpkSheetKind.AddCover) },
                    onOpen = { onOpenPane(EpkPane.Gallery) },
                )
                if (bare) {
                    Text(
                        "Add a cover photo above to start your gallery.",
                        style = AppTheme.type.caption,
                        color = colors.ink4,
                    )
                }
            }
        }
        // The gallery is the cover's overflow, so there is nothing to draw until
        // there is a cover — which is exactly what screen 87's caption says.
        if (hasCover) {
            item(key = "gallery") {
                Column(gutter, verticalArrangement = Arrangement.spacedBy(dimens.space.md)) {
                    // The count is META, not an action — the design draws it as
                    // quiet grey text on the section's baseline (23 / 76). A
                    // `SectionHeader` action would tint it `accentInk` and spend
                    // the screen's one accent on a number nobody taps; the strip
                    // under it is the tap target.
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        SectionHeader("Gallery", modifier = Modifier.weight(1f))
                        Text(
                            plural(state.photos.size, "photo"),
                            style = AppTheme.type.caption,
                            color = colors.ink4,
                        )
                    }
                    EpkGalleryStrip(
                        photos = state.photos,
                        uploading = state.uploadingPhoto,
                        canAdd = canAddPhoto(state.photos.size, state.uploadingPhoto),
                        onOpen = { onOpenPane(EpkPane.Gallery) },
                        onAdd = { onOpenSheet(EpkSheetKind.AddCover) },
                    )
                }
            }
        }
        // ONE item, not one per row. The filled list is a hairline-separated
        // stack whose pitch IS the row height (23), and a lazy item per row would
        // insert the column's 16dp arrangement gap between every pair — turning a
        // contiguous list into six floating cards with rules under them. Six rows
        // is nothing to virtualise anyway.
        item(key = "sections") {
            Column(gutter) {
                if (!bare) {
                    SectionHeader("Sections", modifier = Modifier.padding(bottom = dimens.space.xs))
                }
                rows.forEach { row ->
                    val open = { openSection(row.key, onOpenPane, onOpenSheet) }
                    if (bare) {
                        EpkInvitationRow(
                            row = row,
                            onClick = open,
                            modifier = Modifier.padding(bottom = dimens.space.sm),
                        )
                    } else {
                        EpkSectionListRow(
                            row = row,
                            onClick = open,
                            showHairline = row.key != rows.last().key,
                        )
                    }
                }
            }
        }
        item(key = "share") {
            Column(gutter, verticalArrangement = Arrangement.spacedBy(dimens.space.md)) {
                Spacer(Modifier.height(dimens.space.sm))
                ShareLinkSection(handle = state.artist?.handle.orEmpty())
            }
        }
    }
}

/** Where a section row goes — a sheet for the short answers, a pane for the sets. */
private fun openSection(
    key: EpkSectionKey,
    onOpenPane: (EpkPane) -> Unit,
    onOpenSheet: (EpkSheetKind) -> Unit,
) = when (key) {
    EpkSectionKey.Bio -> onOpenSheet(EpkSheetKind.EditBio)
    EpkSectionKey.Personality -> onOpenSheet(EpkSheetKind.Personality)
    EpkSectionKey.Samples -> onOpenPane(EpkPane.Samples)
    EpkSectionKey.Packages -> onOpenPane(EpkPane.Packages)
    EpkSectionKey.Tech -> onOpenPane(EpkPane.Tech)
    EpkSectionKey.Links -> onOpenPane(EpkPane.Links)
}

/**
 * The section rows for the state on screen.
 *
 * An extension on the UI state rather than a call site argument list, because
 * three places need the same six rows (the header's percentage, the meter, the
 * list) and three copies of a ten-argument call is three chances to feed one of
 * them a stale field.
 *
 * Every count reads the DRAFT where there is one — the bio being typed, the
 * services just ticked — so the meter answers the edit in progress rather than
 * the last debounced write. The "from" price goes through the shared
 * [PackagePricing.fromPrice] so the row can never quote a different minimum than
 * the profile it describes.
 */
internal fun EpkUiState.sectionRows(): List<EpkSectionRow> {
    val savable = packageRows.filter(::packageRowIsSavable)
    val preview = previewPackages(packageRows)
    return epkSectionRows(
        bio = bioDraft,
        serviceTagCount = shownServiceTags(serviceTags, artist?.serviceTags.orEmpty()).size,
        answeredPromptCount = ArtistPrompts.questions.count {
            ArtistPrompts.answerFor(promptDrafts, it).isNotBlank()
        },
        promptTotal = ArtistPrompts.questions.size,
        sampleCount = samples.size,
        packageCount = savable.size,
        fromPriceInr = if (preview.isEmpty()) {
            null
        } else {
            PackagePricing.fromPrice(preview, fallback = artist?.price ?: 0)
        },
        techCount = techItems.size,
        linkCount = links.size,
        socialCount = socialDraft.linkedCount,
    )
}

// ── Constants ────────────────────────────────────────────────────────────────

/** Where a camera capture lands before it is adopted into the media cache. */
private const val CAMERA_DIR = "artist-epk"

/** What the library picker offers for a cover or a gallery photo. */
private const val PHOTO_MIME = "image/*"
