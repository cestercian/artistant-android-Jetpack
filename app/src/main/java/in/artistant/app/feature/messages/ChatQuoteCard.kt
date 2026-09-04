package `in`.artistant.app.feature.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.KeyboardOptions
import `in`.artistant.app.common.util.formatInr
import `in`.artistant.app.data.model.GigRequestStatus
import `in`.artistant.app.designsystem.component.AppTextField
import `in`.artistant.app.designsystem.component.ButtonVariant
import `in`.artistant.app.designsystem.component.EyebrowLabel
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.component.SheetScaffold
import `in`.artistant.app.designsystem.theme.AppTheme

/**
 * The quote, drawn as an object in the transcript (design 08).
 *
 * It sits on the viewer's own side of the thread when the viewer is the one who
 * made the offer and on the counterparty's side when they did — the same rule as
 * a bubble — because a card floating in the middle of a two-column transcript
 * loses the one piece of information every other row carries: who said it.
 *
 * Three states, and the difference between them is what stops the card lying:
 *  - **actionable** — Accept and Counter, because the row is genuinely the
 *    viewer's to answer.
 *  - **waiting** — the same terms with no buttons and a line saying whose move
 *    it is. Rendering the buttons here would offer a decision RLS will refuse.
 *  - **frozen** — accepted. The card becomes the record, which is design 08's
 *    whole point: "the record of what was agreed lives in the thread, not in a
 *    screenshot."
 */
@Composable
fun QuoteCard(
    quote: ThreadQuote,
    /** Formatted expiry, e.g. "Fri 6 pm". Null when the row carried no deadline. */
    validUntil: String?,
    counterpartName: String,
    onAccept: () -> Unit,
    onCounter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val shape = RoundedCornerShape(dimens.radii.card)

    Column(
        modifier = modifier
            .fillMaxWidth(QUOTE_CARD_WIDTH)
            .clip(shape)
            .background(colors.surface3)
            .border(dimens.component.focusStroke, colors.accent.copy(alpha = RIM), shape)
            .padding(dimens.space.lg)
            .semantics {
                testTag = "chat.quoteCard"
                contentDescription = quote.spoken(validUntil, counterpartName)
            },
    ) {
        EyebrowLabel(text = quote.eyebrow, color = colors.accentInk)
        Spacer(Modifier.height(dimens.space.sm))
        Text(
            formatInr(quote.amountInr),
            style = AppTheme.type.displaySmall,
            color = colors.ink,
        )
        Spacer(Modifier.height(dimens.space.xs))
        Text(
            listOfNotNull(
                quote.terms.takeIf { it.isNotBlank() },
                validUntil?.let { "Valid until $it" },
            ).joinToString("\n"),
            style = AppTheme.type.subtitle,
            color = colors.ink4,
        )
        Spacer(Modifier.height(dimens.space.md))
        when {
            quote.actionable -> Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(dimens.space.sm),
            ) {
                PrimaryButton(
                    text = "Accept",
                    onClick = onAccept,
                    modifier = Modifier.weight(1f).semantics { testTag = "chat.quoteAccept" },
                )
                PrimaryButton(
                    text = "Counter",
                    onClick = onCounter,
                    variant = ButtonVariant.Subtle,
                    modifier = Modifier.weight(1f).semantics { testTag = "chat.quoteCounter" },
                )
            }
            // Not a button and not silence: the card says which side is holding
            // it, so nobody sits waiting on a screen that looks finished.
            else -> Text(
                quote.standing(counterpartName),
                style = AppTheme.type.caption,
                color = if (quote.expired) colors.warm else colors.ink3,
            )
        }
    }
}

/** "QUOTE" / "COUNTER OFFER" / "AGREED" — what this card IS, in one word. */
private val ThreadQuote.eyebrow: String
    get() = when {
        frozen -> "AGREED"
        countered -> "COUNTER OFFER"
        else -> "QUOTE"
    }

/** The line under a card nobody can act on. */
private fun ThreadQuote.standing(counterpartName: String): String = when {
    frozen -> "Accepted — these terms are frozen."
    expired -> "This offer has lapsed."
    status == GigRequestStatus.Open -> "Waiting on $counterpartName to answer."
    else -> "Waiting on $counterpartName."
}

/** One sentence for a screen reader, so the card isn't read out as four fragments. */
private fun ThreadQuote.spoken(validUntil: String?, counterpartName: String): String =
    listOfNotNull(
        "${eyebrow.lowercase().replaceFirstChar { it.uppercase() }}, ${formatInr(amountInr)}",
        terms.takeIf { it.isNotBlank() },
        validUntil?.let { "valid until $it" },
        if (actionable) "Accept or counter" else standing(counterpartName),
    ).joinToString(ThreadContext.SEPARATOR)

/**
 * Counter-offer entry.
 *
 * A sheet rather than an inline field: countering replaces the number both sides
 * are looking at, so it deserves the same deliberate step accepting does. The
 * amount is the ONLY input — a counter is a price, and anything else said about
 * it belongs in the conversation the sheet is sitting on.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CounterQuoteSheet(
    currentAmountInr: Int,
    onSubmit: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val dimens = AppTheme.dimens
    var raw by remember { mutableStateOf("") }
    val amount = raw.toIntOrNull()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = null,
        containerColor = AppTheme.colors.surface,
    ) {
        SheetScaffold(title = "Counter this quote") {
            Text(
                "They asked for ${formatInr(currentAmountInr)}. Send the number you can do.",
                style = AppTheme.type.body,
                color = AppTheme.colors.ink3,
            )
            Spacer(Modifier.height(dimens.space.lg))
            AppTextField(
                value = raw,
                // Digits only, at the source. A currency field that accepts
                // letters and rejects them on submit teaches the wrong lesson
                // one keystroke too late.
                onValueChange = { raw = it.filter(Char::isDigit).take(AMOUNT_DIGITS) },
                label = "Your amount (\u20b9)",
                hint = "e.g. 40000",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.semantics { testTag = "chat.counterAmount" },
            )
            Spacer(Modifier.height(dimens.space.lg))
            PrimaryButton(
                text = "Send counter",
                onClick = { amount?.let(onSubmit) },
                enabled = amount != null && amount > 0,
                fullWidth = true,
                modifier = Modifier.semantics { testTag = "chat.counterSubmit" },
            )
            Spacer(Modifier.height(dimens.space.md))
            Text(
                "The other side sees the new number and answers it \u2014 you can't accept your " +
                    "own counter.",
                style = AppTheme.type.caption,
                color = AppTheme.colors.ink4,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * The card's share of the thread width (design draws 82%).
 *
 * Not full width: it is one side of a two-column transcript and has to read as
 * belonging to a speaker, the same as every bubble around it.
 */
private const val QUOTE_CARD_WIDTH = 0.86f

/** The accent, softened to a rim. Full strength around a `surface3` fill reads as a button. */
private const val RIM = 0.65f

/** ₹99,99,999 is nine digits more than this product will ever quote. */
private const val AMOUNT_DIGITS = 8
