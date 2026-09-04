package `in`.artistant.app.feature.booking

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.data.model.Booking
import `in`.artistant.app.data.repository.ReviewRepositoryError
import `in`.artistant.app.data.repository.ReviewsRepository
import `in`.artistant.app.designsystem.component.Banner
import `in`.artistant.app.designsystem.component.BannerTone
import `in`.artistant.app.designsystem.component.Chip
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.component.SheetScaffold
import `in`.artistant.app.designsystem.rememberHaptics
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.feature.bookings.bareDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * One thing that stood out on the night.
 *
 * The design draws five chips ("Read the room", "On time", "Clean sound", "Easy
 * to plan with", "Would rebook"). `reviews.categories` is a jsonb map and the
 * iOS client writes exactly four keys into it — `punctuality`, `sound`, `crowd`,
 * `communication` — so these four are the tags that have somewhere to land. A
 * fifth chip would either write a key nothing reads or quietly write nothing at
 * all, which is worse than not offering it: the client would believe they had
 * said something.
 *
 * A picked tag writes [PICKED] against its key. iOS stores 1–5 per axis from a
 * detailed form; a chip is a binary, and the honest binary is "this was a
 * strength" — the absence of a key means nothing was said, not that it was bad.
 */
enum class ReviewTag(val key: String, val label: String) {
    Punctuality("punctuality", "On time"),
    Crowd("crowd", "Read the room"),
    Sound("sound", "Great sound"),
    Communication("communication", "Easy to plan with"),
}

/** What a picked chip writes against its axis. */
private const val PICKED = 5

data class ReviewSheetUiState(
    val rating: Int = 5,
    val body: String = "",
    val tags: Set<ReviewTag> = emptySet(),
    val isSubmitting: Boolean = false,
    val error: String? = null,
    /** One-shot: the insert landed. Consumed by the host, which then closes. */
    val submitted: Boolean = false,
)

/**
 * The review write, off the composition.
 *
 * It exists for one reason. [ReviewSheet] used to launch `insert` on
 * `rememberCoroutineScope()`, whose Job is the sheet's own slot in the
 * composition — and the sheet is a `ModalBottomSheet` its host dismisses on a
 * scrim tap or a downward drag, with no guard while the write is in flight. So a
 * client who tapped "Submit review" and swiped the sheet away during a slow
 * request had the insert cancelled mid-flight, silently: the catch never fired
 * (`catch (e: Exception)` would have swallowed the CancellationException
 * anyway), nothing was persisted, no error appeared, and the booking still
 * offered "Leave a review". They walked away believing they had reviewed the
 * artist.
 *
 * `viewModelScope` here belongs to the host destination's back-stack entry, not
 * to the sheet, so the request outlives a dismissal and only dies when the
 * booking screen itself is popped. It also puts the repository back behind a
 * ViewModel, where house rule 4 wants it.
 */
@HiltViewModel
class ReviewSheetViewModel @Inject constructor(
    private val reviewsRepository: ReviewsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ReviewSheetUiState())
    val state: StateFlow<ReviewSheetUiState> = _state.asStateFlow()

    fun setRating(value: Int) = _state.update { it.copy(rating = value.coerceIn(1, 5)) }

    fun setBody(value: String) = _state.update { it.copy(body = value) }

    fun toggleTag(tag: ReviewTag) = _state.update {
        it.copy(tags = if (tag in it.tags) it.tags - tag else it.tags + tag)
    }

    fun submit(bookingId: String) {
        if (_state.value.isSubmitting) return
        val rating = _state.value.rating
        val body = _state.value.body.ifBlank { null }
        // An empty map is sent as null, not as `{}`: the column means "what the
        // client said about each axis", and an empty object asserts they were
        // asked and answered nothing.
        val categories = _state.value.tags
            .associate { it.key to PICKED }
            .takeIf { it.isNotEmpty() }
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, error = null) }
            try {
                reviewsRepository.insert(
                    bookingId = bookingId,
                    rating = rating,
                    body = body,
                    categories = categories,
                )
                // Reset alongside the flag: this VM is scoped to the booking
                // screen, so it outlives the sheet, and re-opening it after a
                // submitted review must not re-offer the same stars and text.
                _state.update { ReviewSheetUiState(submitted = true) }
            } catch (e: ReviewRepositoryError) {
                _state.update { it.copy(isSubmitting = false, error = e.message) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isSubmitting = false, error = e.message ?: "Couldn't submit review.")
                }
            }
        }
    }

    fun consumeSubmitted() = _state.update { it.copy(submitted = false) }
}

/**
 * Leave a review — screens 20 and 98.
 *
 * 98 is not a different screen, it is this one with [artistName] null: "a
 * missing name never blocks the review — the booking id carries the identity
 * instead". So the heading degrades from "How was Kabir Sen?" to "How was the
 * set?", the subtitle becomes the booking reference, and a warm banner says why.
 * Nothing is disabled and nothing is hidden.
 *
 * Two things the design draws are deliberately absent, both because the backend
 * has no place to put them. There is no "Post publicly" switch: `reviews` has no
 * visibility column and its select policy is `using (true)`, so every review is
 * public the moment it lands, and a switch would be a promise the database
 * breaks. And the footnote does not claim reviews are double-blind or close
 * after 14 days: the only rule the server enforces is that the booking must be
 * `completed`, so that is what the footnote says.
 *
 * It does NOT watch `submitted` itself. The insert outlives a scrim dismiss (that
 * is the whole point of hoisting it into the ViewModel), so an effect living here
 * would not be in composition when a write landed after the sheet closed. The
 * host owns that effect — see `BookingDetailScreen`.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReviewSheet(
    bookingId: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    artistName: String? = null,
    subtitle: String? = null,
    viewModel: ReviewSheetViewModel = hiltViewModel(),
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    val named = !artistName.isNullOrBlank()

    SheetScaffold(modifier = modifier, title = "Leave a review") {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                Modifier
                    .size(dimens.component.emptyGlyphCircle)
                    .clip(CircleShape)
                    .background(colors.surface2),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Person,
                    contentDescription = null,
                    tint = colors.ink3,
                    modifier = Modifier.size(dimens.component.emptyGlyph),
                )
            }
            Text(
                if (named) "How was $artistName?" else "How was the set?",
                style = AppTheme.type.displaySub,
                color = colors.ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = dimens.space.md),
            )
            subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = AppTheme.type.subtitle,
                    color = colors.ink4,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = dimens.space.xs),
                )
            }
        }

        if (!named) {
            Spacer(Modifier.height(dimens.space.md))
            Banner(
                title = "We couldn't load the artist's name",
                detail = "The booking reference stands in for it. Your review still " +
                    "attaches to the right act.",
                tone = BannerTone.Attention,
            )
        }

        Spacer(Modifier.height(dimens.space.lg))
        StarRow(rating = ui.rating, onRate = {
            // Selection tick on the star row. The matching success buzz for a
            // landed insert is the HOST's — the write outlives this sheet, so an
            // effect here would not be in composition when it lands.
            haptics.select()
            viewModel.setRating(it)
        })
        Text(
            ratingWord(ui.rating),
            style = AppTheme.type.subtitle.copy(fontWeight = FontWeight.SemiBold),
            color = colors.ink2,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = dimens.space.sm),
        )

        Spacer(Modifier.height(dimens.space.lg))
        Text("What stood out?", style = AppTheme.type.sectionTitle, color = colors.ink)
        Spacer(Modifier.height(dimens.space.md))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(dimens.space.sm),
            verticalArrangement = Arrangement.spacedBy(dimens.space.sm),
        ) {
            ReviewTag.entries.forEach { tag ->
                Chip(
                    label = tag.label,
                    selected = tag in ui.tags,
                    onClick = {
                        haptics.select()
                        viewModel.toggleTag(tag)
                    },
                )
            }
        }

        Spacer(Modifier.height(dimens.space.lg))
        BasicTextField(
            value = ui.body,
            onValueChange = viewModel::setBody,
            textStyle = AppTheme.type.body.copy(color = colors.ink),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = dimens.component.cta)
                .clip(RoundedCornerShape(dimens.radii.buttonLg))
                .background(colors.surface3)
                .padding(dimens.space.lg),
            decorationBox = { inner ->
                if (ui.body.isEmpty()) {
                    Text(
                        "Tell other hosts what the night felt like (optional)",
                        style = AppTheme.type.body,
                        color = colors.ink4,
                    )
                }
                inner()
            },
        )

        ui.error?.let {
            Spacer(Modifier.height(dimens.space.md))
            Banner(title = it, tone = BannerTone.Failure)
        }

        Spacer(Modifier.height(dimens.space.md))
        Text(
            "You can review once the show is marked completed. Reviews show on the " +
                "artist's profile.",
            style = AppTheme.type.caption,
            color = colors.ink4,
        )

        Spacer(Modifier.height(dimens.space.lg))
        PrimaryButton(
            text = if (ui.isSubmitting) "Posting…" else "Post review",
            onClick = { viewModel.submit(bookingId) },
            fullWidth = true,
            enabled = !ui.isSubmitting,
        )
        Spacer(Modifier.height(dimens.space.sm))
        Text(
            "Not now",
            style = AppTheme.type.subtitle.copy(fontWeight = FontWeight.SemiBold),
            color = colors.ink2,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(dimens.radii.sm))
                .clickable(role = Role.Button, onClick = onDismiss)
                .padding(vertical = dimens.space.md),
        )
    }
}

/**
 * Five stars, filled up to [rating].
 *
 * Each star is drawn at [Components.reviewStar] inside a 44dp tap node: the
 * glyph is the size the design needs to make the rating the page's subject, and
 * the target is the size a thumb needs. An unfilled star takes `hairline` rather
 * than an outline — five outlines read as a decorative border, five flat greys
 * read as "not yet".
 */
@Composable
private fun StarRow(rating: Int, onRate: (Int) -> Unit) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        (1..5).forEach { star ->
            val filled = star <= rating
            Icon(
                Icons.Filled.Star,
                contentDescription = null,
                tint = if (filled) colors.accent else colors.hairline,
                modifier = Modifier
                    .sizeIn(minWidth = dimens.size.rowMin, minHeight = dimens.size.rowMin)
                    .clip(CircleShape)
                    .clickable(role = Role.Button) { onRate(star) }
                    .wrapContentSize()
                    .size(dimens.component.reviewStar)
                    .semantics { contentDescription = "$star star${if (star == 1) "" else "s"}" },
            )
        }
    }
}

/**
 * The word under the stars.
 *
 * It is not a score restated — the number is already drawn — it is what the
 * number MEANS, so the client can tell whether the taps landed where they meant
 * them to before posting something permanent.
 */
private fun ratingWord(rating: Int): String = when (rating) {
    5 -> "Great night"
    4 -> "Good night"
    3 -> "It was fine"
    2 -> "Not great"
    else -> "Bad night"
}

/**
 * "Techno DJ · played 6 Sep", or the booking reference when there is no artist
 * to name (screen 98).
 *
 * The reference is the fallback rather than a blank line because it is the one
 * identity that always exists, and it is quotable at support — which is exactly
 * what a client who cannot see who they are reviewing might need.
 */
fun reviewSubtitleFor(booking: Booking, category: String?): String {
    val played = "played ${bareDate(booking.date)}"
    val head = category?.trim()?.takeIf { it.isNotEmpty() } ?: bookingReference(booking.id)
    return "$head · $played"
}
