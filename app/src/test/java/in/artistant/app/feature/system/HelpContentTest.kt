package `in`.artistant.app.feature.system

import `in`.artistant.app.data.model.SelfProfile
import `in`.artistant.app.designsystem.theme.AppRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The help centre's two pieces of logic (design 63): which FAQ set you see and
 * in what order, and what gets promoted above it.
 */
class HelpContentTest {

    private fun profile(name: String?) = SelfProfile(
        role = AppRole.Client,
        fullName = name,
        city = "Bengaluru",
        handle = "rhea",
        artistSetupComplete = null,
    )

    // ── the promoted item ────────────────────────────────────────────────────

    @Test
    fun `a blank name is promoted`() {
        val action = outstandingHelpItem(profile(""), AppRole.Client)
        assertNotNull(action)
        assertEquals("Add your name", action!!.title)
        assertEquals("Artists see it when you send a request", action.detail)
    }

    @Test
    fun `a whitespace-only name counts as blank`() {
        assertNotNull(outstandingHelpItem(profile("   "), AppRole.Client))
    }

    @Test
    fun `a null name is promoted`() {
        assertNotNull(outstandingHelpItem(profile(null), AppRole.Client))
    }

    @Test
    fun `nothing is promoted when the name is there`() {
        assertNull(outstandingHelpItem(profile("Rhea Menon"), AppRole.Client))
    }

    @Test
    fun `a failed profile read promotes nothing`() {
        // "We could not check" must never render as "you have a problem". A null
        // profile is a throw the ViewModel swallowed, not an empty one.
        assertNull(outstandingHelpItem(null, AppRole.Client))
    }

    @Test
    fun `the detail line is written for the reader's own role`() {
        assertEquals(
            "Clients see it on every request you answer",
            outstandingHelpItem(profile(null), AppRole.Artist)!!.detail,
        )
    }

    // ── the FAQ set ──────────────────────────────────────────────────────────

    @Test
    fun `the client set hides artist-only answers and keeps shared ones`() {
        val questions = helpArticles(HelpAudience.Clients).map { it.question }
        assertTrue(questions.contains("How do quotes work?"))
        assertTrue("shared answers belong to both sets", questions.contains("How do I report someone?"))
        assertTrue(questions.none { it == "When do I get paid?" })
    }

    @Test
    fun `the artist set is the mirror image`() {
        val questions = helpArticles(HelpAudience.Artists).map { it.question }
        assertTrue(questions.contains("When do I get paid?"))
        assertTrue(questions.contains("Why can't clients find me?"))
        assertTrue(questions.none { it == "How do quotes work?" })
    }

    @Test
    fun `a blank query keeps the authored order`() {
        val authored = HelpContent.articles
            .filter { it.audience == null || it.audience == HelpAudience.Clients }
        assertEquals(authored, helpArticles(HelpAudience.Clients, query = "   "))
    }

    @Test
    fun `a question hit outranks an answer hit`() {
        // "block" is in one QUESTION ("Can I block someone?") and in another
        // article's answer. The question the user was looking for goes first.
        val results = helpArticles(HelpAudience.Clients, query = "block")
        assertTrue(results.isNotEmpty())
        assertTrue(results.first().question.lowercase().contains("block"))
    }

    @Test
    fun `search is case-insensitive and reaches the answers`() {
        val results = helpArticles(HelpAudience.Clients, query = "ESCROW".lowercase())
        // Nothing in the copy promises escrow; the point is only that a miss
        // returns an empty list rather than the whole set.
        assertTrue(results.isEmpty())

        val bookability = helpArticles(HelpAudience.Clients, query = "bookability")
        assertTrue(bookability.any { it.question.contains("Bookability") })
    }

    @Test
    fun `ordering within a band is stable across keystrokes`() {
        // Two searches whose result SETS are the same must produce the same
        // order, or the list shuffles under the user's finger as they type.
        val once = helpArticles(HelpAudience.Clients, query = "a")
        val twice = helpArticles(HelpAudience.Clients, query = "a")
        assertEquals(once, twice)
    }

    @Test
    fun `no answer is empty`() {
        HelpContent.articles.forEach {
            assertTrue("${it.question} has no answer", it.answer.isNotBlank())
        }
    }

    @Test
    fun `audience defaults follow the role`() {
        assertEquals(HelpAudience.Clients, HelpAudience.forRole(AppRole.Client))
        assertEquals(HelpAudience.Artists, HelpAudience.forRole(AppRole.Artist))
    }
}
