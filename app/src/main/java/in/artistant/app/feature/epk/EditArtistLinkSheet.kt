package `in`.artistant.app.feature.epk

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.theme.AppTheme

/**
 * Add / edit one `public.artist_links` row (port of iOS `EditArtistLinkSheet`). Stays
 * decoupled from the network — the parent hands in [onSave]/[onDelete] callbacks so the
 * sheet only owns its own fields + the client-side validation
 * ([EpkViewModel.validateLink], the same rule set the unit test exercises). The DB CHECK
 * constraints are the real backstop; this just spares a round-trip on obvious mistakes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditArtistLinkSheet(
    initialLabel: String = "",
    initialUrl: String = "",
    isExisting: Boolean = false,
    onSave: (label: String, url: String) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var label by remember { mutableStateOf(initialLabel) }
    var url by remember { mutableStateOf(initialUrl) }
    var error by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = colors.bgElev) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = space.xl).padding(bottom = space.xl),
            verticalArrangement = Arrangement.spacedBy(space.lg),
        ) {
            Text(
                if (isExisting) "Edit link" else "Add link",
                style = AppTheme.type.displayMedium,
                color = colors.ink,
            )

            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("Label", color = colors.ink3) },
                placeholder = { Text("Bandcamp, Personal site, …", color = colors.ink4) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("URL", color = colors.ink3) },
                placeholder = { Text("https://…", color = colors.ink4) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )

            error?.let { Text(it, style = AppTheme.type.footnote, color = colors.hot) }

            PrimaryButton(
                text = "Save",
                onClick = {
                    val msg = EpkViewModel.validateLink(label, url)
                    if (msg != null) {
                        error = msg
                    } else {
                        onSave(label.trim(), url.trim())
                        onDismiss()
                    }
                },
                fullWidth = true,
            )

            if (isExisting && onDelete != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onDelete(); onDismiss() }
                        .padding(vertical = space.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(space.sm),
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, tint = colors.hot)
                    Text(
                        "Remove this link",
                        style = AppTheme.type.callout.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.hot,
                    )
                }
            }
        }
    }
}
