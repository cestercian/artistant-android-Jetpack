package `in`.artistant.app.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import `in`.artistant.app.designsystem.theme.AppTheme

/** Centered empty / error copy with an optional retry CTA — iOS EmptyStateView port. */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val space = AppTheme.dimens.space
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(space.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(space.md),
    ) {
        Text(
            text = title,
            style = AppTheme.type.headline,
            color = AppTheme.colors.ink,
            textAlign = TextAlign.Center,
        )
        if (!body.isNullOrBlank()) {
            Text(
                text = body,
                style = AppTheme.type.body,
                color = AppTheme.colors.ink2,
                textAlign = TextAlign.Center,
            )
        }
        if (actionLabel != null && onAction != null) {
            PrimaryButton(text = actionLabel, onClick = onAction)
        }
    }
}
