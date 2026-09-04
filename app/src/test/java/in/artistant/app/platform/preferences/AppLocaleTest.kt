package `in`.artistant.app.platform.preferences

import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The per-app language catalogue (design 130).
 *
 * Two honesty rules are pinned here, and both are the kind that rot silently.
 *
 * **A language you cannot read is not on offer.** `app/src/main/res` carries one `values/`
 * directory. Letting somebody pick Kannada and handing them an English screen reads as a broken
 * app rather than an untranslated one, so [AppLanguages.TRANSLATED_TAGS] gates selection and
 * this suite fails the moment a language is offered without its resources.
 *
 * **The reach of the choice depends on the OS.** There is no per-app locale below API 33 and no
 * `androidx.appcompat` in this project to back-port one, so the note under the picker has to
 * say something different on an older device instead of showing a control that does nothing.
 */
class AppLocaleTest {

    @Test
    fun `six languages ship in the design's order`() {
        assertEquals(
            listOf("en", "hi", "kn", "ta", "bn", "mr"),
            AppLanguages.all.map { it.tag },
        )
    }

    @Test
    fun `only languages with resources are selectable`() {
        // The gate. If a `values-hi` directory lands without this set growing, this fails —
        // and if the set grows without the directory, the next test fails.
        assertTrue(AppLanguages.isTranslated(AppLanguage.English))
        listOf(
            AppLanguage.Hindi,
            AppLanguage.Kannada,
            AppLanguage.Tamil,
            AppLanguage.Bengali,
            AppLanguage.Marathi,
        ).forEach { assertFalse("$it claims a translation it does not have", AppLanguages.isTranslated(it)) }
    }

    @Test
    fun `an untranslated row says why it cannot be picked`() {
        assertEquals("Default", languageSubtitleFor(AppLanguage.English))
        assertEquals("Hindi — not translated yet", languageSubtitleFor(AppLanguage.Hindi))
    }

    private fun languageSubtitleFor(language: AppLanguage) =
        `in`.artistant.app.feature.profile.languageSubtitle(
            language,
            AppLanguages.isTranslated(language),
        )

    // ── Reading the platform's answer back ────────────────────────────────────────────

    @Test
    fun `an empty locale list means follow the system, which resolves to English`() {
        assertEquals(AppLanguage.English, AppLanguages.selected(null))
        assertEquals(AppLanguage.English, AppLanguages.selected(""))
        assertEquals(AppLanguage.English, AppLanguages.selected("   "))
    }

    @Test
    fun `a region-qualified tag still ticks its language row`() {
        // The platform can hand back `en-IN` or `hi-IN`; a row keyed on `en` has to match.
        assertEquals(AppLanguage.English, AppLanguages.selected("en-IN"))
        assertEquals(AppLanguage.Hindi, AppLanguages.selected("hi-IN"))
        assertEquals(AppLanguage.Kannada, AppLanguages.selected("KN"))
    }

    @Test
    fun `the first tag in the list wins`() {
        assertEquals(AppLanguage.Tamil, AppLanguages.selected("ta-IN,en-IN"))
    }

    @Test
    fun `a language we do not ship falls back to English rather than ticking nothing`() {
        assertEquals(AppLanguage.English, AppLanguages.selected("fr-FR"))
    }

    // ── The note under the picker ─────────────────────────────────────────────────────

    @Test
    fun `the note says the choice sticks on API 33 and above`() {
        val note = AppLanguages.availabilityNote(Build.VERSION_CODES.TIRAMISU)
        assertTrue(note.contains("Android"))
        assertFalse("must not warn about a limitation this OS doesn't have", note.contains("won't stick"))
    }

    @Test
    fun `the note admits there is no per-app language below API 33`() {
        val note = AppLanguages.availabilityNote(Build.VERSION_CODES.S_V2)
        assertTrue(note.contains("follows your system"))
        assertTrue(note.contains("won't stick"))
    }

    @Test
    fun `the two notes are different, or one of them is a lie`() {
        assertFalse(
            AppLanguages.availabilityNote(Build.VERSION_CODES.TIRAMISU) ==
                AppLanguages.availabilityNote(Build.VERSION_CODES.S_V2),
        )
    }

    @Test
    fun `the picker is only operable where a per-app locale exists`() {
        assertTrue(AppLanguages.isSelectable(Build.VERSION_CODES.TIRAMISU))
        assertTrue(AppLanguages.isSelectable(Build.VERSION_CODES.UPSIDE_DOWN_CAKE))
        assertFalse(AppLanguages.isSelectable(Build.VERSION_CODES.S_V2))
        assertFalse(AppLanguages.isSelectable(Build.VERSION_CODES.O))
    }
}
