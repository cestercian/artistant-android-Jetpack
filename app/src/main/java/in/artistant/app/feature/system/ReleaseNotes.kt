package `in`.artistant.app.feature.system

/**
 * One headline change in a release — the icon, the name, the sentence.
 *
 * [icon] is an enum rather than an `ImageVector` so this table stays a plain
 * data object a unit test can read without Compose.
 */
data class ReleaseHighlight(
    val icon: ReleaseIcon,
    val title: String,
    val body: String,
)

/** The four glyphs the notes sheet can draw. Mapped to Material icons by the sheet. */
enum class ReleaseIcon { Booking, Score, Chat, Shield }

/**
 * What one version shipped.
 *
 * [fixes] is allowed to be empty and usually is on a first release — the sheet
 * omits its "ALSO FIXED" block rather than drawing an empty heading, which is
 * the difference between release notes and a form.
 */
data class ReleaseNote(
    val version: String,
    /** "AUGUST 2026" — the mono eyebrow's second half. */
    val released: String,
    val highlights: List<ReleaseHighlight>,
    val fixes: List<String> = emptyList(),
)

/**
 * The in-app release-notes table (design screen 137).
 *
 * **Authored here, not fetched.** There is no releases table in the shared
 * schema and no Edge Function that serves one, so the alternative to a compiled
 * list is no screen at all. A compiled list is also the correct shape for what
 * this is: notes describe the binary the user is running, so they ship with the
 * binary and cannot disagree with it.
 *
 * The rule for adding an entry is the design's own note — *three features and
 * the real fixes*. Three, because a list of nine is a changelog and nobody reads
 * a changelog; real, because a fix nobody noticed does not belong on a screen
 * that interrupts the app. **Append**, never prepend: [mostRecent] reads the end
 * of the list.
 *
 * A version with no entry shows nothing (see [decideWhatsNew]). That is the
 * normal case for a patch release, and it is why the table is keyed rather than
 * ordered.
 */
object ReleaseNotes {

    private val notes: List<ReleaseNote> = listOf(
        ReleaseNote(
            version = "0.1.0",
            released = "September 2026",
            highlights = listOf(
                ReleaseHighlight(
                    icon = ReleaseIcon.Booking,
                    title = "Ask, then agree",
                    body = "Send a request with your date and venue. The artist accepts or " +
                        "declines, and only then is the date held.",
                ),
                ReleaseHighlight(
                    icon = ReleaseIcon.Score,
                    title = "Bookability Score",
                    body = "One number per artist for how reliably they turn a request into " +
                        "a show — with the history of every point that moved.",
                ),
                ReleaseHighlight(
                    icon = ReleaseIcon.Shield,
                    title = "Everything stays in the thread",
                    body = "Chat, the brief and the booking live in one place, so there is a " +
                        "record if anything goes wrong.",
                ),
            ),
        ),
    )

    /** The entry for [version], or null when that release shipped without notes. */
    fun forVersion(version: String): ReleaseNote? = notes.firstOrNull { it.version == version }

    /**
     * The newest release that has notes at all — what the account list's
     * "What's new" row shows when the running build shipped without an entry.
     *
     * The table is keyed for [forVersion] and **appended in release order** for
     * this: the last entry is the newest. Null only if nobody has ever authored
     * a note, which `ReleaseNotesTableTest` refuses — a settings row that opens
     * nothing is the silent tap the redesign's notes keep ruling out.
     */
    fun mostRecent(): ReleaseNote? = notes.lastOrNull()
}

/** What the app should do about the What's-new sheet on this launch. */
enum class WhatsNewDecision {
    /** Present the sheet, then record the version. */
    Show,

    /** Record the version WITHOUT presenting — see [decideWhatsNew]. */
    RecordSilently,

    /** Already handled. Do nothing at all. */
    Nothing,
}

/**
 * Once per version, and never to somebody who has no "before" (design 137).
 *
 * The interesting case is [seenVersion] == null, which is BOTH a first install
 * and an upgrade from a build that predates this bookkeeping. The two are
 * indistinguishable — nothing was written down — so the decision has to be the
 * one whose failure mode is smaller. Showing means a brand-new user's first act
 * in the app is dismissing a list of things that are not new to them, which is
 * the worse error and by some distance; not showing costs an upgrading user one
 * round of notes, once, ever. So a null records silently and the next release is
 * the first one they see.
 *
 * A version with no [ReleaseNote] also records silently rather than doing
 * nothing: leaving it unrecorded means the decision is re-evaluated on every
 * launch of that build, which is work with no possible outcome.
 */
fun decideWhatsNew(
    seenVersion: String?,
    currentVersion: String,
    hasNotes: Boolean,
): WhatsNewDecision = when {
    currentVersion.isBlank() -> WhatsNewDecision.Nothing
    seenVersion == currentVersion -> WhatsNewDecision.Nothing
    seenVersion == null -> WhatsNewDecision.RecordSilently
    !hasNotes -> WhatsNewDecision.RecordSilently
    else -> WhatsNewDecision.Show
}
