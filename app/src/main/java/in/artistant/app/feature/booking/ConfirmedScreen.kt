package `in`.artistant.app.feature.booking

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.common.util.formatInr
import `in`.artistant.app.data.model.Artist
import `in`.artistant.app.data.model.Booking
import `in`.artistant.app.data.repository.ArtistsRepository
import `in`.artistant.app.designsystem.component.AddToCalendarButton
import `in`.artistant.app.designsystem.component.BookingStatusTimeline
import `in`.artistant.app.designsystem.component.HRule
import `in`.artistant.app.designsystem.component.KVRow
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.feature.signup.EditorialHeadline
import `in`.artistant.app.state.BookingStore
import javax.inject.Inject

/** Resolves the just-confirmed booking + its artist from the shared store. */
@HiltViewModel
class ConfirmedViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val store: BookingStore,
    private val artists: ArtistsRepository,
) : ViewModel() {
    private val bookingId: String = savedStateHandle.get<String>("bookingId").orEmpty()
    val booking = store.bookingsFlow // observe so the row appears once written
    fun find(): Booking? = store.booking(bookingId)
    fun artist(id: String): Artist? = artists.find(id)
}

/**
 * Post-confirm celebration (port of iOS `ConfirmedView`). Spring-in checkmark
 * halo, "Match confirmed", the details card + [BookingStatusTimeline], then the
 * actions: View booking, Add to calendar, Back to discover.
 */
@Composable
fun ConfirmedScreen(
    onViewBooking: (String) -> Unit,
    onBackToDiscover: () -> Unit,
    viewModel: ConfirmedViewModel = hiltViewModel(),
) {
    // Observe the store so a booking written a beat before this screen mounts still resolves.
    viewModel.booking.collectAsStateWithLifecycle()
    val booking = viewModel.find()
    val artist = booking?.let { viewModel.artist(it.artistId) }
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space

    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val scale by animateFloatAsState(
        if (appeared) 1f else 0.6f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "confirmScale",
    )

    Column(
        Modifier.fillMaxSize().background(colors.bg).verticalScroll(rememberScrollState()).padding(space.xl),
        verticalArrangement = Arrangement.spacedBy(space.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(space.xl))
        // Halo.
        Box(Modifier.size(140.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(140.dp).clip(CircleShape).background(colors.brand.copy(alpha = 0.07f)))
            Box(Modifier.size(92.dp).clip(CircleShape).background(colors.brand.copy(alpha = 0.15f)))
            Box(
                Modifier.size(52.dp).scale(scale).clip(CircleShape).background(colors.brand),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = colors.brandInk, modifier = Modifier.size(24.dp))
            }
        }
        // Editorial two-tone: "Match " + "." in ink, only "confirmed" italic brand
        // (iOS ConfirmedView's AppType.editorialHeadline("Match ", "confirmed", ".")).
        EditorialHeadline(
            lead = "Match ",
            accent = "confirmed",
            tail = ".",
            style = AppTheme.type.displayHero,
        )
        if (artist != null) {
            Text("${artist.name} · ${booking.dateLabel}", style = AppTheme.type.callout, color = colors.ink3)
        }

        if (booking != null) {
            Column(Modifier.fillMaxWidth()) {
                HRule()
                KVRow("Booking ID", booking.id)
                HRule()
                KVRow("Artist fee", formatInr(booking.fee))
                HRule()
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(AppTheme.dimens.radii.md))
                    .background(colors.bgCard)
                    .padding(space.lg),
            ) {
                BookingStatusTimeline(booking.status)
            }
        }

        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(space.md)) {
            PrimaryButton(
                text = "View booking",
                onClick = { booking?.let { onViewBooking(it.id) } },
                fullWidth = true,
                enabled = booking != null,
            )
            booking?.let { AddToCalendarButton(it) }
            PrimaryButton(
                text = "Back to discover",
                onClick = onBackToDiscover,
                variant = `in`.artistant.app.designsystem.component.ButtonVariant.Subtle,
                fullWidth = true,
            )
        }
        Spacer(Modifier.height(space.lg))
    }
}
