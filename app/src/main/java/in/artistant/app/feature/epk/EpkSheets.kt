package `in`.artistant.app.feature.epk

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import `in`.artistant.app.data.model.ArtistPrompt
import `in`.artistant.app.designsystem.component.AppTextField
import `in`.artistant.app.designsystem.component.Banner
import `in`.artistant.app.designsystem.component.BannerTone
import `in`.artistant.app.designsystem.component.Chip
import `in`.artistant.app.designsystem.component.IconCircle
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.component.SecondaryButton
import `in`.artistant.app.designsystem.component.pressScale
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.ArtistantTheme
import `in`.artistant.app.domain.artist.ArtistPrompts
import `in`.artistant.app.domain.artist.ServiceTags

/**
 * The press kit's sheets — design screens 65, 67, 68, 74, 75 and 66.
 *
 * Every one of them is the INSIDE of a `ModalBottomSheet`; the caller owns the
 * scrim and the dismissal contract (see `SheetScaffold`). They share one header
 * ([EpkSheetHeader]) because the design does: a leading word that is either a
 * dismissal or nothing, a centred 17/700 title, and a close disc on the trailing
 * edge.
 */

// ── Shared chrome ────────────────────────────────────────────────────────────

/**
 * The sheet header the design draws on all six: `Cancel` / `Skip` / nothing on
 * the left, the title in the middle, a close disc on the right.
 *
 * The leading slot reserves its width whether or not it holds a word, for the
 * same reason `BackHeader` mirrors its back circle — a title centred in the
 * remainder after an asymmetric pair of controls reads as almost-centred, which
 * is worse than either extreme.
 *
 * Two dismissals on one header is not a redundancy. The word is the *considered*
 * exit ("Cancel" throws the edit away, "Skip" declines the whole thing) and the
 * disc is the reflex one; they carry different meanings on 67 and 68, and where
 * they do not, the design still draws the disc because that is where a thumb goes.
 */
@Composable
fun EpkSheetHeader(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    leadingLabel: String? = null,
    onLeading: (() -> Unit)? = null,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = dimens.size.rowMin),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Both edges reserve the SAME width — the leading word's, not the
        // trailing disc's — for the reason `BackHeader` mirrors its back circle:
        // a title centred in the remainder after an asymmetric pair reads as
        // almost-centred. `avatarLg` is what "Cancel" needs at 13.5sp; sizing the
        // slot to the disc instead clipped it to "Canc".
        Box(Modifier.width(dimens.size.avatarLg), contentAlignment = Alignment.CenterStart) {
            if (leadingLabel != null && onLeading != null) {
                Text(
                    leadingLabel,
                    style = AppTheme.type.subtitle.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.ink4,
                    maxLines = 1,
                    modifier = Modifier
                        .heightIn(min = dimens.size.rowMin)
                        .clickable(role = Role.Button, onClick = onLeading)
                        .padding(vertical = dimens.space.md),
                )
            }
        }
        Text(
            title,
            style = AppTheme.type.sectionTitle,
            color = colors.ink,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = dimens.space.sm),
        )
        Box(Modifier.width(dimens.size.avatarLg), contentAlignment = Alignment.CenterEnd) {
            IconCircle(
                icon = Icons.Filled.Close,
                contentDescription = "Close",
                onClick = onClose,
                size = dimens.size.avatarSm,
            )
        }
    }
}

/**
 * A described option — icon, title, subtitle, chevron (65 and 75).
 *
 * The subtitle is the point of both screens ("every option is described"). A row
 * that says only "Record a video" makes the artist find out what the rule is by
 * opening the camera; one that says "5–10 seconds, captured live" lets them
 * decide before they have their phone up.
 */
@Composable
fun EpkOptionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(dimens.radii.buttonLg)
    Row(
        modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.pressScale(interaction) else Modifier)
            .clip(shape)
            .background(colors.surface3)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = ripple(),
                role = Role.Button,
                onClick = onClick,
            )
            .padding(dimens.space.md)
            .semantics(mergeDescendants = true) { contentDescription = "$title. $subtitle" },
        horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(dimens.hero.avatarSize)
                .clip(RoundedCornerShape(dimens.radii.md))
                .background(colors.hairline),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (enabled) colors.ink else colors.ink4,
                modifier = Modifier.size(dimens.size.iconLg),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = AppTheme.type.rowTitle.copy(fontWeight = FontWeight.Bold),
                color = if (enabled) colors.ink else colors.ink4,
            )
            Text(
                subtitle,
                style = AppTheme.type.caption,
                color = colors.ink4,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (enabled) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.lineStrong,
                modifier = Modifier.size(dimens.size.iconMd),
            )
        }
    }
}

// ── 65 · Add cover photo ─────────────────────────────────────────────────────

/**
 * Screen 65 — how the cover gets picked.
 *
 * **The title changes and nothing else does**, which is the design's own note:
 * "Replace cover photo" once one exists. Two sheets for the same three options
 * would be two places to keep the copy right.
 *
 * **The video row ships disabled, and that is the honest state.** There is no
 * video write path on Android: `ArtistMediaRepository` uploads a normalised JPEG
 * and nothing else, `ArtistMediaKind.video` has no writer, and Media3
 * Transformer's trim is not wired. So the row states that instead of stating a
 * length rule this client does not enforce — REDESIGN_2026-09 §5.2 and the PK
 * brief both say a rule only goes on screen if it is real. Shown rather than
 * hidden because a cover VIDEO is what the empty state above it offers ("Add a
 * cover photo or video"), and an offer that silently has two thirds of itself
 * missing is worse than one that says which third.
 */
@Composable
fun AddCoverSheet(
    hasCover: Boolean,
    onTakePhoto: () -> Unit,
    onChooseFromLibrary: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(dimens.space.md),
    ) {
        EpkSheetHeader(
            title = if (hasCover) "Replace cover photo" else "Add cover photo",
            onClose = onClose,
        )
        Spacer(Modifier.height(dimens.space.xs))
        EpkOptionRow(
            icon = Icons.Filled.PhotoCamera,
            title = "Take a photo",
            subtitle = "Capture a new shot with your camera",
            onClick = onTakePhoto,
        )
        EpkOptionRow(
            icon = Icons.Filled.PhotoLibrary,
            title = "Choose from library",
            subtitle = "Pick an existing photo",
            onClick = onChooseFromLibrary,
        )
        EpkOptionRow(
            icon = Icons.Filled.Videocam,
            title = "Record a video",
            subtitle = "Not on Android yet — covers are photos for now",
            onClick = {},
            enabled = false,
        )
        Text(
            if (hasCover) {
                "The new photo becomes your cover; the old one stays in your gallery."
            } else {
                "The first photo becomes your cover — it is the only thing most clients see before they tap."
            },
            style = AppTheme.type.caption,
            color = colors.ink4,
            modifier = Modifier.padding(top = dimens.space.xs),
        )
    }
}

// ── 67 · Edit bio ────────────────────────────────────────────────────────────

/**
 * Screen 67 — the bio and the service chips, in one sheet.
 *
 * The two are together because the design puts them together, and the design is
 * right: "what you sound like" and "what you play" are the same answer given
 * twice, and an artist who has just written a sentence about their sets is
 * exactly the person who can tick the sets.
 *
 * **The counter and the line beside it do different jobs** (the design note):
 * the number is a fact and stays quiet until the cap, where it turns `warm`
 * because that is the moment it explains keystrokes going missing. The line is
 * the advice, and it is advice rather than a rule — nothing rejects a one-word
 * bio.
 *
 * Save flushes rather than writes. Every field here autosaves on the editor's
 * shared debounce, so the button's job is to end the wait and close, not to
 * introduce a second, competing write path.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditBioSheet(
    bio: String,
    services: List<String>,
    canEdit: Boolean,
    onBio: (String) -> Unit,
    onToggleService: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(dimens.space.md),
    ) {
        EpkSheetHeader(
            title = "Edit bio",
            onClose = onClose,
            leadingLabel = "Cancel",
            onLeading = onCancel,
        )
        AppTextField(
            value = bio,
            onValueChange = onBio,
            hint = "Clients read this before anything else on your profile.",
            enabled = canEdit,
            singleLine = false,
            minHeight = dimens.size.coverPreview,
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "A line or two is plenty.",
                style = AppTheme.type.caption,
                color = colors.ink4,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${bio.length} / $MAX_BIO_CHARS",
                style = AppTheme.type.caption,
                color = if (bioIsAtCap(bio.length)) colors.warm else colors.ink4,
            )
        }
        if (!canEdit) {
            Banner(
                title = "Couldn't read your profile, so editing is off",
                tone = BannerTone.Attention,
                detail = "Pull to refresh the press kit and try again.",
            )
        }
        Text(
            "What you offer",
            style = AppTheme.type.caption.copy(fontWeight = FontWeight.SemiBold),
            color = colors.ink4,
            modifier = Modifier.padding(top = dimens.space.sm),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(dimens.space.sm),
            verticalArrangement = Arrangement.spacedBy(dimens.space.sm),
        ) {
            ServiceTags.catalog.forEach { (slug, label) ->
                Chip(
                    label = label,
                    selected = slug in services,
                    enabled = canEdit,
                    onClick = { onToggleService(slug) },
                )
            }
            // A tag this build's taxonomy does not know — written by the other
            // client or an admin backfill. Shown selected so the artist can
            // withdraw a claim their profile is making rather than having it
            // silently absent from the editor that is supposed to show it all.
            services.filterNot { it in ServiceTags.slugs }.forEach { slug ->
                Chip(
                    label = ServiceTags.label(slug),
                    selected = true,
                    enabled = canEdit,
                    onClick = { onToggleService(slug) },
                )
            }
        }
        Spacer(Modifier.height(dimens.space.sm))
        PrimaryButton("Save", onSave, fullWidth = true, enabled = canEdit)
    }
}

// ── 68 · Add personality ─────────────────────────────────────────────────────

/**
 * Screen 68 — the prompt deck, answered against unanswered.
 *
 * The design's note is the layout rule: one filled card shows the shape, so the
 * empty ones read as invitations rather than as chores. That means answered
 * prompts sort to the TOP — a deck that kept the canonical order would bury the
 * only card demonstrating what an answer looks like under three empty ones.
 *
 * An unanswered card is a row with a "+"; tapping it turns that card into the
 * filled shape with the field focused, so the transition is the demonstration.
 * Only one card is open at a time because the sheet is not tall enough for two
 * and because a deck of four open fields is a form, which is what "answer a
 * prompt or two" is trying not to be.
 */
@Composable
fun AddPersonalitySheet(
    drafts: List<ArtistPrompt>,
    canEdit: Boolean,
    onAnswer: (question: String, answer: String) -> Unit,
    onSave: () -> Unit,
    onSkip: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    var openQuestion by rememberSaveable { mutableStateOf<String?>(null) }
    val answered = ArtistPrompts.questions.filter { ArtistPrompts.answerFor(drafts, it).isNotBlank() }
    val unanswered = ArtistPrompts.questions - answered.toSet()

    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(dimens.space.md),
    ) {
        EpkSheetHeader(
            title = "Add personality",
            onClose = onClose,
            leadingLabel = "Skip",
            onLeading = onSkip,
        )
        Text(
            "Answer a prompt or two so clients get a feel for you — dream venue, the song that never leaves your set, that kind of thing.",
            style = AppTheme.type.subtitle,
            color = colors.ink2,
        )
        Column(
            Modifier
                .heightIn(max = dimens.hero.frameHeight)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(dimens.space.md),
        ) {
            (answered + unanswered).forEach { question ->
                val answer = ArtistPrompts.answerFor(drafts, question)
                val open = answer.isNotBlank() || openQuestion == question
                if (open) {
                    PromptAnswerCard(
                        question = question,
                        answer = answer,
                        canEdit = canEdit,
                        onAnswer = { onAnswer(question, it) },
                    )
                } else {
                    PromptInviteCard(
                        question = question,
                        enabled = canEdit,
                        onClick = { openQuestion = question },
                    )
                }
            }
        }
        Spacer(Modifier.height(dimens.space.xs))
        PrimaryButton(
            text = if (answered.isEmpty()) {
                "Save"
            } else {
                "Save ${plural(answered.size, "answer")}"
            },
            onClick = onSave,
            fullWidth = true,
            enabled = canEdit,
        )
        Text(
            "Skip any you like",
            style = AppTheme.type.caption,
            color = colors.ink4,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** An answered prompt — the accent-tinted card that shows the filled shape (68). */
@Composable
private fun PromptAnswerCard(
    question: String,
    answer: String,
    canEdit: Boolean,
    onAnswer: (String) -> Unit,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val shape = RoundedCornerShape(dimens.radii.lg)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.accent.copy(alpha = PROMPT_FILL))
            .border(dimens.size.strokeEmphasis, colors.accent, shape)
            .padding(dimens.space.md),
        verticalArrangement = Arrangement.spacedBy(dimens.space.sm),
    ) {
        Text(
            question,
            style = AppTheme.type.caption.copy(fontWeight = FontWeight.Bold),
            color = colors.accentDeep,
        )
        AppTextField(
            value = answer,
            onValueChange = onAnswer,
            hint = "Your answer",
            enabled = canEdit,
            singleLine = false,
            minHeight = dimens.component.cta,
        )
        Text(
            "${answer.length} / ${ArtistPrompts.MAX_ANSWER_LENGTH}",
            style = AppTheme.type.caption,
            color = colors.ink2,
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** An unanswered prompt — the quiet card with a "+" (68). */
@Composable
private fun PromptInviteCard(question: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(dimens.radii.lg)
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.pressScale(interaction) else Modifier)
            .clip(shape)
            .background(colors.surface3)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = ripple(),
                role = Role.Button,
                onClick = onClick,
            )
            .padding(dimens.space.md)
            .semantics(mergeDescendants = true) { contentDescription = "Answer: $question" },
        horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                question,
                style = AppTheme.type.rowTitle.copy(fontWeight = FontWeight.Bold),
                color = colors.ink,
            )
            Text("Answer this prompt", style = AppTheme.type.caption, color = colors.ink4)
        }
        Icon(
            Icons.Filled.Add,
            contentDescription = null,
            tint = colors.ink,
            modifier = Modifier.size(dimens.size.iconLg),
        )
    }
}

// ── 74 · Add / edit link ─────────────────────────────────────────────────────

/**
 * Screen 74 — one component, two titles, and Remove only in edit mode.
 *
 * That last part is the design's note and it is a correctness rule, not a
 * cosmetic one: a Remove control in Add mode has nothing to remove, so it is
 * either dead or it means Cancel, and a destructive-red row that means Cancel is
 * how a link gets deleted by someone who meant to back out.
 *
 * Both fields validate, and they say WHY under themselves rather than only
 * dimming Save. The old sheet accepted any non-blank string, so "banccamp" saved
 * and then rendered on the artist's public profile as a tap target that goes
 * nowhere — see `linkUrlProblem`. The message appears once the field has been
 * typed in and left non-empty, never against an untouched blank one.
 */
@Composable
fun EditLinkSheet(
    editor: LinkEditorState,
    busy: Boolean,
    onLabel: (String) -> Unit,
    onUrl: (String) -> Unit,
    onSave: () -> Unit,
    onRemove: () -> Unit,
    onCancel: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    // Errors only after the artist has put something in the field. A sheet that
    // opens shouting "add an address" at an empty Add form is scolding someone
    // for not having typed yet.
    val urlError = editor.url.takeIf { it.isNotBlank() }?.let(::linkUrlProblem)
    val labelError = editor.label.takeIf { editor.url.isNotBlank() }?.let(::linkLabelProblem)

    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(dimens.space.md),
    ) {
        EpkSheetHeader(
            title = if (editor.isExisting) "Edit link" else "Add link",
            onClose = onClose,
            leadingLabel = "Cancel",
            onLeading = onCancel,
        )
        AppTextField(
            value = editor.label,
            onValueChange = onLabel,
            label = "Label",
            hint = "Bandcamp",
            error = labelError,
            enabled = !busy,
        )
        AppTextField(
            value = editor.url,
            onValueChange = onUrl,
            label = "URL",
            hint = "tiltcollective.bandcamp.com",
            error = urlError,
            enabled = !busy,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = KeyboardType.Uri,
            ),
            leading = {
                Icon(
                    Icons.Filled.Link,
                    contentDescription = null,
                    tint = colors.ink4,
                    modifier = Modifier.size(dimens.size.iconMd),
                )
            },
        )
        Text(
            "Bandcamp, SoundCloud, your personal site — anywhere a client should land. No https:// needed, we'll add it.",
            style = AppTheme.type.caption,
            color = colors.ink4,
        )
        if (editor.isExisting) {
            Spacer(Modifier.height(dimens.space.xs))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(dimens.radii.sm))
                    .clickable(enabled = !busy, role = Role.Button, onClick = onRemove)
                    .padding(vertical = dimens.space.md),
                horizontalArrangement = Arrangement.spacedBy(dimens.space.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = null,
                    tint = colors.danger,
                    modifier = Modifier.size(dimens.size.iconLg),
                )
                Text(
                    "Remove this link",
                    style = AppTheme.type.body.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.danger,
                )
            }
        }
        Spacer(Modifier.height(dimens.space.xs))
        PrimaryButton(
            text = if (busy) "Saving…" else "Save",
            onClick = onSave,
            fullWidth = true,
            enabled = !busy && linkIsSavable(editor.label.trim(), editor.url.trim()),
        )
    }
}

// ── 75 · Add audio ───────────────────────────────────────────────────────────

/**
 * Screen 75 — the clip picker, constrained before it opens.
 *
 * The design's note is the whole reason this sheet exists rather than the picker
 * firing straight off a "+": filtering the formats up front is why an upload
 * cannot fail for a reason the artist could not see. The MIME array handed to the
 * picker is `artist-samples`' own `allowed_mime_types` (migration 0010), so the
 * list on screen and the list Storage enforces are literally the same value.
 *
 * **One option, not the design's two.** iOS offers Files and the music library as
 * separate pickers; Android's document picker already spans Drive, Downloads and
 * every provider on the device, so a second row would open the same sheet with a
 * different label on it.
 *
 * The slots line counts staged clips as well as stored ones, matching the cap the
 * "+" is gated on — so "0 of 6 slots free" and a disabled control never disagree.
 */
@Composable
fun AddAudioSheet(
    storedCount: Int,
    uploadingCount: Int,
    onChooseFile: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val used = storedCount + uploadingCount
    val free = (MAX_SAMPLES - used).coerceAtLeast(0)
    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(dimens.space.md),
    ) {
        EpkSheetHeader(title = "Add a clip", onClose = onClose)
        Spacer(Modifier.height(dimens.space.xs))
        EpkOptionRow(
            icon = Icons.AutoMirrored.Filled.InsertDriveFile,
            title = "Choose a file",
            subtitle = "From Files, Drive or anywhere on this phone",
            onClick = onChooseFile,
            enabled = free > 0,
        )
        Banner(
            title = "Only MP3, M4A and AAC are offered — the picker lists what storage actually accepts, so a file can't fail after you pick it.",
            tone = BannerTone.Note,
        )
        Text(
            if (free > 0) {
                "30 seconds to 2 minutes works best · under 10 MB each · $free of $MAX_SAMPLES slots free"
            } else {
                "All $MAX_SAMPLES slots are full — remove a clip to add another."
            },
            style = AppTheme.type.caption,
            color = colors.ink4,
        )
    }
}

// ── 66 · Stalled uploads ─────────────────────────────────────────────────────

/**
 * Screen 66 — per-item first, then bulk.
 *
 * Both granularities, because a stalled queue needs both: two uploads stall for
 * the same reason often enough to want "Retry all", and for different reasons
 * often enough that spending the network on the one that will fail again is
 * wrong. The per-item pair comes first on the page for that reason.
 *
 * The caption states that the queue is **on disk**, which is the sheet's other
 * job. An artist looking at two failed uploads with no way to know they survive a
 * force-quit will force-quit, and then pick the files again.
 */
@Composable
fun StalledUploadsSheet(
    uploads: List<StalledUpload>,
    onRetry: (String) -> Unit,
    onDiscard: (String) -> Unit,
    onRetryAll: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(dimens.space.md),
    ) {
        EpkSheetHeader(title = "Stalled uploads", onClose = onClose)
        Text(
            if (uploads.size == 1) {
                "One upload couldn't finish. It's saved on this device — retry or discard it."
            } else {
                "${countWord(uploads.size).replaceFirstChar { it.uppercase() }} uploads couldn't finish. They're saved on this device — retry or discard each."
            },
            style = AppTheme.type.subtitle,
            color = colors.ink2,
        )
        Column(
            Modifier
                .heightIn(max = dimens.hero.frameHeight)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(dimens.space.md),
        ) {
            uploads.forEach { upload ->
                StalledUploadCard(
                    upload = upload,
                    onRetry = { onRetry(upload.id) },
                    onDiscard = { onDiscard(upload.id) },
                )
            }
        }
        Spacer(Modifier.height(dimens.space.xs))
        // Bulk is offered only when there is a bulk — one stalled upload already
        // has its own Retry two rows up, and a full-width duplicate of it is a
        // second button for the same tap.
        if (uploads.size > 1) {
            PrimaryButton("Retry all", onRetryAll, fullWidth = true)
        }
        Text(
            "The queue survives an app kill and resumes on next launch.",
            style = AppTheme.type.caption,
            color = colors.ink4,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun StalledUploadCard(
    upload: StalledUpload,
    onRetry: () -> Unit,
    onDiscard: () -> Unit,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.radii.buttonLg))
            .background(colors.surface3)
            .padding(dimens.space.md),
        verticalArrangement = Arrangement.spacedBy(dimens.space.md),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(dimens.size.ringXs)
                    .clip(RoundedCornerShape(dimens.radii.md))
                    .background(colors.dangerLine),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.ArrowUpward,
                    contentDescription = null,
                    tint = colors.danger,
                    modifier = Modifier.size(dimens.size.iconMd),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    upload.label,
                    style = AppTheme.type.rowTitle,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(upload.detail, style = AppTheme.type.caption, color = colors.ink4)
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(dimens.space.sm),
        ) {
            PrimaryButton("Retry", onRetry, modifier = Modifier.weight(1f))
            SecondaryButton("Discard", onDiscard, modifier = Modifier.weight(1f))
        }
    }
}

// ── Constants ────────────────────────────────────────────────────────────────

/** The answered prompt card's tint — `rgba(214,248,75,.26)` on screen 68. */
private const val PROMPT_FILL = 0.26f

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun EpkSheetsPreview() {
    ArtistantTheme {
        Column(
            Modifier.padding(AppTheme.dimens.component.gutter),
            verticalArrangement = Arrangement.spacedBy(AppTheme.dimens.space.xl),
        ) {
            AddCoverSheet(hasCover = false, onTakePhoto = {}, onChooseFromLibrary = {}, onClose = {})
            AddAudioSheet(storedCount = 2, uploadingCount = 0, onChooseFile = {}, onClose = {})
        }
    }
}
