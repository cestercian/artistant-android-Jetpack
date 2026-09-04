package `in`.artistant.app.platform.preferences

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One row of design screen 130's "APP LANGUAGE" list.
 *
 * [native] is the name in its own script — which is the only name that helps someone who
 * cannot read the current UI language find their own. [english] is the endonym's English
 * gloss, printed under it exactly as the design draws.
 */
enum class AppLanguage(val tag: String, val native: String, val english: String) {
    English("en", "English", "Default"),
    Hindi("hi", "हिन्दी", "Hindi"),
    Kannada("kn", "ಕನ್ನಡ", "Kannada"),
    Tamil("ta", "தமிழ்", "Tamil"),
    Bengali("bn", "বাংলা", "Bengali"),
    Marathi("mr", "मराठी", "Marathi"),
}

/**
 * The app languages, and the honest answer to "can I pick this one yet".
 *
 * **[TRANSLATED_TAGS] is the whole point of this object.** `app/src/main/res` carries exactly
 * one `values/` directory — no `values-hi`, no `values-kn`, none of the other four. Offering a
 * language whose strings do not exist would set the per-app locale, hand the user an English
 * UI, and leave them believing the app is broken rather than untranslated. So the design's six
 * rows all render, and the five without resources render as NOT YET AVAILABLE with the reason
 * on the row — the same shape as the design's own "Blocked, and says why" (screen 118).
 *
 * Add a language here in the same commit that adds its `values-<tag>` directory, never before.
 */
object AppLanguages {
    /** The tags `res/values-<tag>` actually exists for. Grow this WITH the resources. */
    val TRANSLATED_TAGS: Set<String> = setOf(AppLanguage.English.tag)

    /** Every row the screen draws, in design order. */
    val all: List<AppLanguage> = AppLanguage.entries

    /** Whether picking [language] would actually change any word on any screen. */
    fun isTranslated(language: AppLanguage): Boolean = language.tag in TRANSLATED_TAGS

    /**
     * Which row is ticked for a platform locale list, as a BCP-47 tag string.
     *
     * Matches on the language subtag only: the platform can hand back `en-IN` or `hi-IN`, and
     * a row keyed on `en` must still tick for `en-IN`. An empty list means "follow the system",
     * which for this app resolves to [AppLanguage.English] — the only translation that exists,
     * and therefore the only thing any system language can currently resolve to.
     */
    fun selected(tags: String?): AppLanguage {
        val first = tags?.split(',')?.firstOrNull()?.trim().orEmpty()
        if (first.isEmpty()) return AppLanguage.English
        val language = first.substringBefore('-').lowercase()
        return all.firstOrNull { it.tag == language } ?: AppLanguage.English
    }

    /**
     * The line under the picker that says how far the choice reaches.
     *
     * Two different facts depending on the OS, and both matter. On API 33+ the platform owns a
     * real per-app locale and the choice is durable. Below that there is no per-app locale API
     * at all — `androidx.appcompat` is not a dependency of this app (checked in
     * `gradle/libs.versions.toml`) and adding one to back-port `AppCompatDelegate`'s
     * locale storage is a new dependency the redesign forbids — so the app follows the system
     * language, and the screen has to say so instead of showing a picker that silently does
     * nothing.
     */
    fun availabilityNote(sdkInt: Int): String = if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
        "Your choice is saved by Android for this app only, and survives reinstalling."
    } else {
        "Android 12 and older have no per-app language, so Artistant follows your system " +
            "language on this device. Changing it here won't stick."
    }

    /** Whether the picker can be operated at all on [sdkInt]. */
    fun isSelectable(sdkInt: Int): Boolean = sdkInt >= Build.VERSION_CODES.TIRAMISU
}

/**
 * The per-app locale, read and written through the PLATFORM's `LocaleManager` (API 33+).
 *
 * No `androidx.appcompat`: it is not a dependency and the redesign adds none. Which means
 * there is no storage of our own either — the platform holds the value, survives a reinstall
 * with it, and shows it in Android's own per-app language settings. A DataStore copy would be
 * a second answer to the same question, and the two would disagree the first time someone
 * changed it from system settings.
 *
 * Below API 33 both calls are no-ops: [current] answers empty (i.e. "system") and [apply]
 * declines. The screen renders [AppLanguages.availabilityNote] rather than a control that
 * silently fails.
 */
@Singleton
class AppLocaleController @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** The current per-app locale list as a BCP-47 tag string, or "" for "follow the system". */
    fun current(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return ""
        val manager = context.getSystemService(LocaleManager::class.java) ?: return ""
        return runCatching { manager.applicationLocales.toLanguageTags() }.getOrDefault("")
    }

    /**
     * Set the per-app locale. Returns whether the platform accepted it.
     *
     * `runCatching` because this is a system-service call on a device build we do not control:
     * a service that is missing or throwing must not take the settings screen with it, and the
     * caller renders the false as "couldn't change the language" rather than as success.
     */
    fun apply(language: AppLanguage): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        val manager = context.getSystemService(LocaleManager::class.java) ?: return false
        return runCatching {
            manager.applicationLocales = LocaleList.forLanguageTags(language.tag)
        }.isSuccess
    }
}
