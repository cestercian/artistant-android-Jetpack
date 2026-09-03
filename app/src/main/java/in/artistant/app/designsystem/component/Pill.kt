package `in`.artistant.app.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.data.model.BookingStatus

/**
 * Tone drives the capsule fill + text color. Neutral is the default chrome-free chip.
 *
 * [Brand] and [BrandSolid] are two different jobs, not two weights of one. Brand
 * is the washed chip the status ladder uses, where lime means "live" alongside a
 * warm, a good and a hot sibling and all four have to read as the same kind of
 * object. BrandSolid is the reference build's brand pill: lime fill, dark ink,
 * used where the chip is the only signal on the screen and is stating a fact
 * rather than a state — a Bookability score beside the artist you are about to
 * request. Washed, that badge lost against the row it sits in.
 */
enum class PillTone { Neutral, Brand, BrandSolid, Good, Warm, Hot }

/** Small capsule label — the Pill port. Mono-ish caption text, no border. */
@Composable
fun Pill(text: String, modifier: Modifier = Modifier, tone: PillTone = PillTone.Neutral) {
    val colors = AppTheme.colors
    val (bg, fg) = when (tone) {
        PillTone.Neutral -> colors.surface2 to colors.ink2
        // Lime wash with lime TEXT was legible on near-black and is not on
        // near-white — `#d6f84b` on `#f5fbda` is about 1.2:1. The wash keeps the
        // accent hue; the label drops to `accentDeep`, which is the same hue at a
        // weight you can read, and is what the design draws inside its own
        // accent-tinted pills (screens 04 and 19).
        PillTone.Brand -> colors.brandSoft to colors.accentDeep
        PillTone.BrandSolid -> colors.accent to colors.onAccent
        // The status trio has no `*Soft` token of its own, so the wash is derived
        // from the status colour rather than hand-mixed — retune `good`/`warm`/
        // `hot` and the fills follow. On a light page a 10% wash is faint, which
        // is correct: the label carries the state, the fill only groups it.
        PillTone.Good -> colors.good.copy(alpha = 0.12f) to colors.good
        PillTone.Warm -> colors.warm.copy(alpha = 0.12f) to colors.warm
        PillTone.Hot -> colors.hot.copy(alpha = 0.12f) to colors.hot
    }
    Text(
        text = text,
        style = AppTheme.type.caption,
        color = fg,
        // A capsule that breaks onto a second line is not a capsule — it is a
        // lozenge with a fold in it. Every label we put in one is a chip-length
        // noun (a category, a status, a weekday, a score), so when a caller hands
        // the pill less room than it wants the honest answer is to elide, not to
        // grow taller than the row it sits in.
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(CircleShape)
            .background(bg)
            .padding(horizontal = AppTheme.dimens.space.md, vertical = AppTheme.dimens.space.xs),
    )
}

/**
 * The one status→tone mapping for the whole app.
 *
 * Three surfaces each decided this independently and disagreed: the bookings
 * list painted EVERY status with the brand accent (so "Confirmed" and "Awaiting
 * confirm" were the same lime and the label carried no signal at all), booking
 * detail painted every status Neutral, and only the artist-list drill-down
 * actually varied by status. Spotted on-device.
 *
 * Semantics, not decoration: Warm = the reader has something to do, Brand = the
 * happy live state, Good = finished well, Hot = ended badly. `Unknown` stays
 * Neutral deliberately — it is the decode fallback for a status this build does
 * not recognise, and any colour would assert a meaning we do not have.
 */
fun bookingStatusTone(status: BookingStatus): PillTone = when (status) {
    BookingStatus.PendingConfirm -> PillTone.Warm
    BookingStatus.Confirmed -> PillTone.Brand
    BookingStatus.Completed -> PillTone.Good
    BookingStatus.Cancelled, BookingStatus.Disputed -> PillTone.Hot
    BookingStatus.Unknown -> PillTone.Neutral
}
