package `in`.artistant.app.feature.signup

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import `in`.artistant.app.designsystem.theme.AppTheme

/**
 * The vanguard signature — an editorial headline with one italic, brand-lime accent word (iOS
 * `AppType.editorialHeadline(lead, accent, tail)`). The accent is the screen's single signal;
 * the surrounding runs stay ink. Built as one AnnotatedString so line-wrapping treats it as one
 * paragraph (three separate Texts would break mid-phrase awkwardly).
 */
@Composable
fun EditorialHeadline(
    lead: String,
    accent: String,
    tail: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val text = buildAnnotatedString {
        withStyle(SpanStyle(color = colors.ink)) { append(lead) }
        withStyle(SpanStyle(color = colors.brand, fontStyle = FontStyle.Italic)) { append(accent) }
        withStyle(SpanStyle(color = colors.ink)) { append(tail) }
    }
    Text(text = text, style = style, modifier = modifier)
}
