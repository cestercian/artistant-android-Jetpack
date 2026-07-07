package `in`.artistant.app.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import `in`.artistant.app.designsystem.theme.AppRole
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.ArtistantTheme

/**
 * Renders the design-system primitives together — proves the theme + tokens +
 * components compile and lay out. Two @Previews exercise both role accents.
 */
@Composable
fun ComponentGallery() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.colors.bg)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Component gallery", style = AppTheme.type.displaySub, color = AppTheme.colors.ink)
        PrimaryButton(text = "Filled", onClick = {}, fullWidth = true)
        PrimaryButton(text = "Ghost", onClick = {}, variant = ButtonVariant.Ghost, fullWidth = true)
        PrimaryButton(text = "Subtle", onClick = {}, variant = ButtonVariant.Subtle, fullWidth = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("NEUTRAL")
            Pill("BRAND", tone = PillTone.Brand)
            Pill("ELITE", tone = PillTone.Good)
            Pill("BUSY", tone = PillTone.Hot)
        }
        HRule()
        CardView {
            Text("Card title", style = AppTheme.type.headline, color = AppTheme.colors.ink)
            Text("Hairline card, no chrome.", style = AppTheme.type.footnote, color = AppTheme.colors.ink2)
        }
    }
}

@Preview(name = "Client (lime)", backgroundColor = 0xFF0A0A0A, showBackground = true)
@Composable
private fun GalleryClientPreview() {
    ArtistantTheme(role = AppRole.Client) { ComponentGallery() }
}

@Preview(name = "Artist (violet)", backgroundColor = 0xFF0A0A0A, showBackground = true)
@Composable
private fun GalleryArtistPreview() {
    ArtistantTheme(role = AppRole.Artist) { ComponentGallery() }
}
