package `in`.artistant.app.feature.system

import `in`.artistant.app.data.model.SelfProfile
import `in`.artistant.app.designsystem.theme.AppRole

/** Which FAQ set the segmented control is showing (design screen 63). */
enum class HelpAudience(val label: String) {
    Clients("For clients"),
    Artists("For artists"),
    ;

    companion object {
        fun forRole(role: AppRole): HelpAudience =
            if (role == AppRole.Artist) Artists else Clients
    }
}

/**
 * One FAQ entry.
 *
 * [audience] null means "both" — the trust and safety answers are identical
 * whichever end of a booking you are on, and duplicating them would mean two
 * copies to keep in step.
 */
data class HelpArticle(
    val question: String,
    val answer: String,
    val audience: HelpAudience? = null,
)

/**
 * The thing standing between the user and using the app, promoted above the FAQ
 * (design screen 63: *outstanding item goes first*).
 *
 * There is exactly one of these, deliberately. A list of outstanding items is a
 * to-do list, and a to-do list at the top of a help screen is what the user came
 * here to escape.
 */
data class HelpAction(
    val title: String,
    val detail: String,
    val actionLabel: String = "Fix",
)

/**
 * The one blocking item the app can honestly detect.
 *
 * **Only a missing display name**, and the reason the list is that short is the
 * gate above it: [in.artistant.app.ui.RootGate] will not let a user reach the
 * tabs with no role or no handle, and an artist whose EPK is unfinished is
 * routed into the wizard rather than to this screen. So every other "incomplete
 * profile" state is unreachable from here, and inventing a card for it would be
 * a warning nobody can ever see.
 *
 * `users.full_name` is genuinely reachable while blank: it is not part of
 * [SelfProfile.isComplete], and the artist's request list reads the denormalized
 * `client_name` — which is where a blank one shows up, as the design's own
 * detail line says.
 *
 * A null [profile] returns null: the read FAILED, and "we could not check" must
 * never render as "you have a problem".
 */
fun outstandingHelpItem(profile: SelfProfile?, role: AppRole): HelpAction? {
    if (profile == null) return null
    if (!profile.fullName.isNullOrBlank()) return null
    return HelpAction(
        title = "Add your name",
        detail = if (role == AppRole.Artist) {
            "Clients see it on every request you answer"
        } else {
            "Artists see it when you send a request"
        },
    )
}

/**
 * The FAQ table.
 *
 * Authored, not fetched — there is no articles table in the shared schema, and
 * an "article" here is two sentences that ship with the build they describe.
 * Every answer states what the product actually does in v1: no payments, no
 * escrow, request → accept, and "keep it on Artistant" as the trust rule (the
 * redaction experiment was retired in mig 0071 and the answers must not imply
 * it).
 */
object HelpContent {

    val articles: List<HelpArticle> = listOf(
        HelpArticle(
            question = "How do quotes work?",
            answer = "You send a request with your date, venue and budget. The artist " +
                "accepts, declines, or comes back with a counter offer. Nothing is held " +
                "until one of you accepts.",
            audience = HelpAudience.Clients,
        ),
        HelpArticle(
            question = "How do requests reach me?",
            answer = "A client sends a request with a date, a venue and a budget. Accept " +
                "it, decline it, or send a counter offer — the date is only held once it " +
                "is accepted.",
            audience = HelpAudience.Artists,
        ),
        HelpArticle(
            question = "What is the Bookability Score?",
            answer = "One number for how reliably an artist turns a request into a show: " +
                "how fast they answer, how often they accept, how many gigs they have " +
                "completed and what clients said afterwards. Every point that moved is " +
                "listed in the score history.",
        ),
        HelpArticle(
            question = "Can I cancel a booking?",
            answer = "Yes, from the booking itself. The other side is told straight away " +
                "and the date is freed.",
        ),
        HelpArticle(
            question = "Does Artistant take a payment?",
            answer = "No. This version matches you and keeps the conversation; you settle " +
                "the fee with the artist directly. The totals shown are what you have " +
                "agreed, not a charge.",
            audience = HelpAudience.Clients,
        ),
        HelpArticle(
            question = "When do I get paid?",
            answer = "Artistant does not handle money in this version. You settle the fee " +
                "with the client directly — the totals in the app are the agreement, not " +
                "a payout.",
            audience = HelpAudience.Artists,
        ),
        HelpArticle(
            question = "Someone asked me to settle off Artistant",
            answer = "Keep the conversation here and report it. If anything goes wrong " +
                "with something agreed elsewhere, there is no record of it for us to act " +
                "on.",
        ),
        HelpArticle(
            question = "How do I report someone?",
            answer = "Open the conversation or their profile and choose Report. Reports " +
                "come to us and are never shown to the person reported.",
        ),
        HelpArticle(
            question = "Can I block someone?",
            answer = "Yes, from inside the conversation. Everyone you have blocked is " +
                "listed under Blocked accounts in settings, and you can unblock from " +
                "there.",
        ),
        HelpArticle(
            question = "Why can't clients find me?",
            answer = "Your press kit has to be published. An unpublished profile does not " +
                "appear in search or on Discover, however complete it is.",
            audience = HelpAudience.Artists,
        ),
    )
}

/**
 * The list screen 63 shows: the chosen audience's articles, narrowed by the
 * search box.
 *
 * **Ordering is the interesting part.** A blank query keeps the authored order,
 * which runs from "how does this work" to "something is wrong" — the order
 * somebody reads in when they do not yet know what to ask. A query re-orders,
 * because then they DO know: a hit in the question outranks a hit in the answer,
 * since a question that matches is the article they were looking for and an
 * answer that merely mentions the word is a hint. Within each of those two
 * bands the authored order survives, so results never shuffle between
 * keystrokes.
 */
fun helpArticles(
    audience: HelpAudience,
    query: String = "",
    articles: List<HelpArticle> = HelpContent.articles,
): List<HelpArticle> {
    val forAudience = articles.filter { it.audience == null || it.audience == audience }
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) return forAudience

    val byQuestion = forAudience.filter { it.question.lowercase().contains(needle) }
    val byAnswer = forAudience.filter {
        !it.question.lowercase().contains(needle) && it.answer.lowercase().contains(needle)
    }
    return byQuestion + byAnswer
}
