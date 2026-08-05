package `in`.artistant.app.feature.booking

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import `in`.artistant.app.data.repository.ReviewRepositoryError
import `in`.artistant.app.data.repository.ReviewsRepository
import `in`.artistant.app.designsystem.component.ButtonVariant
import `in`.artistant.app.designsystem.component.dockSurface
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.theme.AppTheme
import kotlinx.coroutines.launch

/**
 * Thin review submit sheet — port of iOS `ReviewSheet`.
 * Calls [ReviewsRepository.insert] with rating + optional body.
 */
@Composable
fun ReviewSheet(
    bookingId: String,
    reviewsRepository: ReviewsRepository,
    onDismiss: () -> Unit,
    onSubmitted: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    var rating by remember { mutableIntStateOf(5) }
    var body by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Rounded top only — this is a bottom sheet, so its lower edge is the screen
    // edge and has no corner to soften.
    Column(
        modifier
            .dockSurface()
            .padding(space.lg),
    ) {
        Text("Leave a review", style = AppTheme.type.headline, color = colors.ink)
        Spacer(Modifier.height(space.md))
        Row(horizontalArrangement = Arrangement.spacedBy(space.sm)) {
            (1..5).forEach { star ->
                Text(
                    if (star <= rating) "★" else "☆",
                    style = AppTheme.type.title,
                    color = if (star <= rating) colors.warm else colors.ink3,
                    modifier = Modifier
                        .clip(RoundedCornerShape(AppTheme.dimens.radii.sm))
                        .background(colors.bgSoft)
                        .clickable { rating = star }
                        .padding(horizontal = space.sm, vertical = space.xs),
                )
            }
        }
        Spacer(Modifier.height(space.lg))
        BasicTextField(
            value = body,
            onValueChange = { body = it },
            textStyle = AppTheme.type.body.copy(color = colors.ink),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(AppTheme.dimens.radii.sm))
                .background(colors.bgSoft)
                .padding(space.md),
            decorationBox = { inner ->
                if (body.isEmpty()) {
                    Text("Share how the show went…", style = AppTheme.type.body, color = colors.ink3)
                }
                inner()
            },
        )
        error?.let {
            Spacer(Modifier.height(space.sm))
            Text(it, style = AppTheme.type.footnote, color = colors.hot)
        }
        Spacer(Modifier.height(space.lg))
        PrimaryButton(
            text = if (submitting) "Submitting…" else "Submit review",
            onClick = {
                if (submitting) return@PrimaryButton
                scope.launch {
                    submitting = true
                    error = null
                    try {
                        reviewsRepository.insert(
                            bookingId = bookingId,
                            rating = rating,
                            body = body.ifBlank { null },
                            categories = null,
                        )
                        onSubmitted()
                        onDismiss()
                    } catch (e: ReviewRepositoryError) {
                        error = e.message
                    } catch (e: Exception) {
                        error = e.message ?: "Couldn't submit review."
                    } finally {
                        submitting = false
                    }
                }
            },
            fullWidth = true,
            enabled = !submitting,
        )
        Spacer(Modifier.height(space.sm))
        PrimaryButton(text = "Cancel", onClick = onDismiss, variant = ButtonVariant.Ghost, fullWidth = true)
    }
}
