package `in`.artistant.app.feature.booking

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.artistant.app.common.util.formatInr
import `in`.artistant.app.designsystem.component.CardView
import `in`.artistant.app.designsystem.component.DateScroller
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.theme.AppTheme
import java.time.LocalDate

/**
 * Client "Request a quote" compose screen (port of iOS `RequestQuoteView`). Shares
 * BookingView's vocabulary (section labels, hairline cards, [DateScroller]). The
 * budget is the one required field; on send it writes a gig request and flips to a
 * success state. [onDone] dismisses.
 */
@Composable
fun RequestQuoteScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: RequestQuoteViewModel = hiltViewModel(),
) {
    val artist by viewModel.artist.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space

    var date by rememberSaveable { mutableStateOf(LocalDate.now()) }
    var amountText by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf("") }
    var venue by rememberSaveable { mutableStateOf("") }
    var guests by rememberSaveable { mutableStateOf(100) }

    val amount = amountText.filter { it.isDigit() }.toIntOrNull() ?: 0
    val canSubmit = amount > 0 && !state.submitting

    if (state.sent) {
        SentConfirmation(artistName = artist?.name, onDone = onDone)
        return
    }

    Column(Modifier.fillMaxSize().background(colors.bg)) {
        FunnelHeader("Request a quote", onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(space.lg),
            verticalArrangement = Arrangement.spacedBy(space.xl),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(space.xs)) {
                Text("Propose your booking", style = AppTheme.type.title, color = colors.ink)
                Text(
                    "Name your date and budget. ${artist?.name ?: "The artist"} will accept, decline, or counter with their own number.",
                    style = AppTheme.type.footnote,
                    color = colors.ink3,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(space.md)) {
                SectionLabel("Pick a date")
                DateScroller(
                    selected = date,
                    onSelect = { date = it },
                    daysAvailable = artist?.daysAvailable,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(space.md)) {
                SectionLabel("Your budget")
                CardView {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                        Text("₹", style = AppTheme.type.monoLarge, color = colors.ink3)
                        OutlinedTextField(
                            value = amountText,
                            onValueChange = { amountText = it.filter(Char::isDigit) },
                            placeholder = { Text("0", color = colors.ink4) },
                            singleLine = true,
                            textStyle = AppTheme.type.monoLarge,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = fieldColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(space.md)) {
                SectionLabel("Details")
                CardView {
                    OutlinedTextField(
                        value = message,
                        onValueChange = { message = it },
                        label = { Text("Message", color = colors.ink3) },
                        placeholder = { Text("What's the occasion?", color = colors.ink4) },
                        minLines = 3,
                        colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(space.md))
                    OutlinedTextField(
                        value = venue,
                        onValueChange = { venue = it },
                        label = { Text("Venue", color = colors.ink3) },
                        singleLine = true,
                        colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(space.md))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            SectionLabel("Guests")
                            Text("$guests", style = AppTheme.type.monoLarge, color = colors.ink)
                        }
                        GuestsStepper(guests) { guests = it.coerceIn(10, 5000) }
                    }
                }
            }

            state.error?.let { msg ->
                Text(msg, style = AppTheme.type.footnote, color = colors.warm)
            }
            Spacer(Modifier.height(space.lg))
        }

        CtaBar {
            PrimaryButton(
                text = if (state.submitting) "Sending…" else "Send request →",
                onClick = { viewModel.submit(amount, date, message, venue, guests) },
                fullWidth = true,
                enabled = canSubmit,
            )
        }
    }
}

@Composable
private fun SentConfirmation(artistName: String?, onDone: () -> Unit) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Column(
        Modifier.fillMaxSize().background(colors.bg).padding(space.xl),
        verticalArrangement = Arrangement.spacedBy(space.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = colors.brand, modifier = Modifier.size(28.dp))
        Text("Request sent", style = AppTheme.type.title, color = colors.ink)
        Text(
            "${artistName ?: "The artist"} will reply with an accept, decline, or counter-offer. You'll see it in your requests.",
            style = AppTheme.type.footnote,
            color = colors.ink3,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))
        PrimaryButton(text = "Done", onClick = onDone, fullWidth = true)
    }
}

@Composable
private fun GuestsStepper(value: Int, onChange: (Int) -> Unit) {
    val colors = AppTheme.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(AppTheme.dimens.space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf("−" to value - 10, "+" to value + 10).forEach { (label, next) ->
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(colors.bgSoft)
                    .clickable { onChange(next) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = AppTheme.type.title, color = colors.ink)
            }
        }
    }
}
