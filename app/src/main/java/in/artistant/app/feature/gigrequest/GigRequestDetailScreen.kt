package `in`.artistant.app.feature.gigrequest

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import `in`.artistant.app.common.util.formatInr
import `in`.artistant.app.data.model.GigRequestStatus
import `in`.artistant.app.data.model.StoredRequest
import `in`.artistant.app.designsystem.component.CardView
import `in`.artistant.app.designsystem.component.HRule
import `in`.artistant.app.designsystem.component.Pill
import `in`.artistant.app.designsystem.component.PillTone
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.feature.booking.CtaBar
import `in`.artistant.app.feature.booking.FunnelHeader
import `in`.artistant.app.feature.booking.InitialAvatar
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.state.RequestStore
import `in`.artistant.app.ui.rememberHaptics
import javax.inject.Inject

/**
 * Reads the gig request from the shared [RequestStore] and forwards the artist's
 * accept / decline / counter (all optimistic in the store). ponytail: the
 * calendar-clash read (M6) is omitted; the ArtistHome entry that seeds/navigates
 * here is M5.
 */
@HiltViewModel
class GigRequestViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val store: RequestStore,
) : ViewModel() {
    private val id: String = savedStateHandle.get<String>("id").orEmpty()
    val requests = store.requests

    // Surface the store's optimistic-rollback message (e.g. "That request was
    // withdrawn or already handled.") — without this collector an accept/decline/
    // counter that the server rejects silently snaps the row back with no
    // explanation (iOS shows it via requestStore.lastRefreshError).
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init { viewModelScope.launch { store.errors.collect { _error.value = it } } }

    fun dismissError() { _error.value = null }

    fun find(): StoredRequest? = store.requests.value.firstOrNull { it.id == id }
    fun accept() { store.accept(id) }
    fun decline() { store.decline(id) }
    fun counter(amount: Int) { store.counter(id, amount) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GigRequestDetailScreen(
    onBack: () -> Unit,
    viewModel: GigRequestViewModel = hiltViewModel(),
) {
    viewModel.requests.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val stored = viewModel.find()
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val haptics = rememberHaptics()

    var confirmingDecline by remember { mutableStateOf(false) }
    var showCounter by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(colors.bg)) {
        FunnelHeader("Gig request", onBack)

        // Optimistic-rollback / load failures from the store surface here.
        error?.let { msg ->
            Row(
                Modifier.fillMaxWidth().background(colors.bgCard).padding(horizontal = space.xl, vertical = space.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(space.sm),
            ) {
                Text(
                    msg,
                    style = AppTheme.type.caption.copy(fontWeight = FontWeight.Bold),
                    color = colors.hot,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "Dismiss",
                    style = AppTheme.type.caption,
                    color = colors.ink3,
                    modifier = Modifier.clickable { viewModel.dismissError() }.padding(space.xs),
                )
            }
        }

        if (stored == null) {
            Column(Modifier.fillMaxSize().padding(space.xl), verticalArrangement = Arrangement.spacedBy(space.sm)) {
                Text("Request not found", style = AppTheme.type.displaySmall, color = colors.ink)
                Text("This request may have expired or been withdrawn.", style = AppTheme.type.footnote, color = colors.ink3)
            }
        } else {
            val r = stored.raw
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(space.lg),
                verticalArrangement = Arrangement.spacedBy(space.lg),
            ) {
                // Client card.
                Row(horizontalArrangement = Arrangement.spacedBy(space.md), verticalAlignment = Alignment.CenterVertically) {
                    InitialAvatar(r.client, size = 56)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(r.client, style = AppTheme.type.body.copy(fontWeight = FontWeight.Bold), color = colors.ink)
                        Text(r.date, style = AppTheme.type.footnote, color = colors.ink3)
                        Pill(r.`package`, tone = PillTone.Accent)
                    }
                }

                // Message.
                CardView {
                    Text("MESSAGE", style = AppTheme.type.caption.copy(fontWeight = FontWeight.Bold), color = colors.ink3)
                    Spacer(Modifier.height(space.sm))
                    Text(r.message.ifEmpty { "—" }, style = AppTheme.type.callout, color = colors.ink2)
                }

                // Offer.
                CardView {
                    Text("OFFER", style = AppTheme.type.caption.copy(fontWeight = FontWeight.Bold), color = colors.ink3)
                    Spacer(Modifier.height(space.md))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Their offer", style = AppTheme.type.footnote, color = colors.ink2, modifier = Modifier.weight(1f))
                        Text(formatInr(r.amount), style = AppTheme.type.monoMedium.copy(fontWeight = FontWeight.Bold), color = colors.ink)
                    }
                    stored.counterAmount?.let { c ->
                        Spacer(Modifier.height(space.sm)); HRule(); Spacer(Modifier.height(space.sm))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Your counter", style = AppTheme.type.footnote, color = colors.ink2, modifier = Modifier.weight(1f))
                            Text(formatInr(c), style = AppTheme.type.monoMedium.copy(fontWeight = FontWeight.Bold), color = colors.brand)
                        }
                    }
                    if (stored.status != GigRequestStatus.Open) {
                        Spacer(Modifier.height(space.sm)); HRule(); Spacer(Modifier.height(space.sm))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Status", style = AppTheme.type.footnote, color = colors.ink2, modifier = Modifier.weight(1f))
                            Pill(stored.status.label, tone = statusTone(stored.status))
                        }
                    }
                }
            }

            // Action bar — only when the request is still open.
            if (stored.status == GigRequestStatus.Open) {
                CtaBar {
                    Row(horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                        PrimaryButton(
                            text = "Decline",
                            onClick = { confirmingDecline = true },
                            variant = `in`.artistant.app.designsystem.component.ButtonVariant.Ghost,
                            modifier = Modifier.weight(1f),
                        )
                        PrimaryButton(
                            text = "Counter",
                            onClick = { showCounter = true },
                            variant = `in`.artistant.app.designsystem.component.ButtonVariant.Subtle,
                            modifier = Modifier.weight(1f),
                        )
                        PrimaryButton(
                            text = "Accept",
                            onClick = { haptics.success(); viewModel.accept() },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }

    if (confirmingDecline) {
        AlertDialog(
            onDismissRequest = { confirmingDecline = false },
            title = { Text("Decline this request?") },
            text = { Text("The client is notified and this request closes. You can't reopen it.") },
            confirmButton = {
                TextButton(onClick = { confirmingDecline = false; viewModel.decline() }) {
                    Text("Decline request", color = colors.hot)
                }
            },
            dismissButton = { TextButton(onClick = { confirmingDecline = false }) { Text("Keep open") } },
            containerColor = colors.bgElev,
        )
    }

    if (showCounter) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var counterText by remember { mutableStateOf(stored?.raw?.amount?.toString() ?: "") }
        ModalBottomSheet(onDismissRequest = { showCounter = false }, sheetState = sheetState, containerColor = colors.bgElev) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = space.xl).padding(bottom = space.xl),
                verticalArrangement = Arrangement.spacedBy(space.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Counter offer", style = AppTheme.type.headline.copy(fontWeight = FontWeight.Bold), color = colors.ink)
                OutlinedTextField(
                    value = counterText,
                    onValueChange = { counterText = it.filter(Char::isDigit) },
                    label = { Text("₹ amount", color = colors.ink3) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                PrimaryButton(
                    text = "Send counter",
                    onClick = {
                        val amt = counterText.toIntOrNull() ?: 0
                        if (amt > 0) viewModel.counter(amt)
                        showCounter = false
                    },
                    fullWidth = true,
                    enabled = (counterText.toIntOrNull() ?: 0) > 0,
                )
            }
        }
    }
}

private fun statusTone(status: GigRequestStatus): PillTone = when (status) {
    GigRequestStatus.Accepted -> PillTone.Good
    GigRequestStatus.Countered -> PillTone.Brand
    GigRequestStatus.Open -> PillTone.Warm
    GigRequestStatus.Declined, GigRequestStatus.Expired -> PillTone.Neutral
}
