package `in`.artistant.app.domain.artist

import `in`.artistant.app.data.model.SearchCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The service-tag taxonomy and its set arithmetic.
 *
 * The single most valuable assertion in this file is
 * [catalog_isTheSameVocabularyTheSearchFilterSends]. `artists.service_tags` is
 * matched server-side with an `&&` overlap, which is exact: if the editor ever
 * writes a vocabulary the filter does not send, every services search returns
 * nothing and looks exactly like a quiet marketplace. Nothing at runtime would
 * report it, so it is pinned here.
 */
class ServiceTagsTest {

    // ── Vocabulary ───────────────────────────────────────────────────────────

    @Test
    fun catalog_isTheSameVocabularyTheSearchFilterSends() {
        // Not merely "equal to a copy" — the SAME list, so a future edit cannot
        // fork the write vocabulary from the read one.
        assertSame(SearchCatalog.services, ServiceTags.catalog)
    }

    @Test
    fun slugs_areLowercaseKebabAndUnique() {
        val slugs = ServiceTags.catalog.map { it.first }
        assertEquals(slugs.size, slugs.toSet().size)
        assertTrue(slugs.all { it == it.lowercase() })
        assertTrue(slugs.none { it.contains(' ') })
    }

    @Test
    fun label_rendersTheCuratedTextForAKnownSlug() {
        assertEquals("DJ set", ServiceTags.label("dj-set"))
        assertEquals("Full band", ServiceTags.label("full-band"))
    }

    @Test
    fun label_echoesAnUnknownSlugRatherThanBlanking() {
        // A tag from another client or an admin backfill still describes the
        // artist; rendering nothing would hide a claim their profile is making.
        assertEquals("qawwali-night", ServiceTags.label("qawwali-night"))
    }

    @Test
    fun labels_preserveTheStoredOrder() {
        assertEquals(
            listOf("DJ set", "Acoustic duo"),
            ServiceTags.labels(listOf("dj-set", "acoustic-duo")),
        )
    }

    // ── Toggle ───────────────────────────────────────────────────────────────

    @Test
    fun toggle_addsWhenAbsent() {
        assertEquals(listOf("dj-set"), ServiceTags.toggle(emptyList(), "dj-set"))
    }

    @Test
    fun toggle_removesWhenPresent() {
        assertEquals(
            listOf("full-band"),
            ServiceTags.toggle(listOf("dj-set", "full-band"), "dj-set"),
        )
    }

    @Test
    fun toggle_appendsSoTheArtistsOrderSurvives() {
        assertEquals(
            listOf("dj-set", "full-band"),
            ServiceTags.toggle(listOf("dj-set"), "full-band"),
        )
    }

    @Test
    fun toggle_refusesToExceedTheCap() {
        val full = ServiceTags.catalog.take(ServiceTags.MAX_TAGS).map { it.first }
        val overflow = ServiceTags.catalog[ServiceTags.MAX_TAGS].first
        // Returned unchanged, NOT truncated — the caller detects the refusal by
        // identity and tells the artist, instead of a chip silently not lighting.
        assertEquals(full, ServiceTags.toggle(full, overflow))
    }

    @Test
    fun toggle_alwaysAllowsRemovalEvenFromAnOverCapSet() {
        // A set written elsewhere can exceed our cap. Withdrawing a claim must
        // never be the thing that is refused.
        val over = ServiceTags.catalog.map { it.first }
        val out = ServiceTags.toggle(over, over.first())
        assertEquals(over.size - 1, out.size)
        assertTrue(over.first() !in out)
    }

    // ── Normalize ────────────────────────────────────────────────────────────

    @Test
    fun normalize_trimsAndDropsBlanks() {
        assertEquals(
            listOf("dj-set", "full-band"),
            ServiceTags.normalize(listOf(" dj-set ", "", "   ", "full-band")),
        )
    }

    @Test
    fun normalize_deduplicatesKeepingTheFirstOccurrence() {
        assertEquals(
            listOf("dj-set", "full-band"),
            ServiceTags.normalize(listOf("dj-set", "full-band", "dj-set")),
        )
    }

    @Test
    fun normalize_clampsToTheCap() {
        val all = ServiceTags.catalog.map { it.first }
        assertEquals(ServiceTags.MAX_TAGS, ServiceTags.normalize(all).size)
    }

    @Test
    fun normalize_leavesCaseAloneSoAnAdminTagIsNotRewritten() {
        // Case-folding would rewrite a value this client did not author — and an
        // overlap match is case-sensitive, so "fixing" the case would un-match it.
        assertEquals(listOf("Live Band"), ServiceTags.normalize(listOf("Live Band")))
    }

    @Test
    fun normalize_ofAnEmptySetIsEmpty() {
        // The un-publish case: an artist unticking their last service.
        assertTrue(ServiceTags.normalize(emptyList()).isEmpty())
    }
}
