package `in`.artistant.app.feature.profile

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.designsystem.component.AccentNote
import `in`.artistant.app.designsystem.component.BackHeader
import `in`.artistant.app.designsystem.component.CheckRow
import `in`.artistant.app.designsystem.component.EyebrowLabel
import `in`.artistant.app.designsystem.component.MarkState
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.ArtistantTheme
import `in`.artistant.app.platform.preferences.AppLanguage
import `in`.artistant.app.platform.preferences.AppLanguages
import `in`.artistant.app.platform.preferences.AppLocaleController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/** Screen 130's state. [applied] is the locale in force; [picked] is what the list is showing. */
data class LanguageUiState(
    val applied: AppLanguage = AppLanguage.English,
    val picked: AppLanguage = AppLanguage.English,
    val selectable: Boolean = true,
    val note: String = "",
    val failure: String? = null,
) {
    /** Save is only a real action while the pick differs from what is already in force. */
    val canSave: Boolean get() = selectable && picked != applied
}

@HiltViewModel
class LanguageViewModel @Inject constructor(
    private val locales: AppLocaleController,
) : ViewModel() {
    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<LanguageUiState> = _state

    private fun initialState(): LanguageUiState {
        val current = AppLanguages.selected(locales.current())
        return LanguageUiState(
            applied = current,
            picked = current,
            selectable = AppLanguages.isSelectable(Build.VERSION.SDK_INT),
            note = AppLanguages.availabilityNote(Build.VERSION.SDK_INT),
        )
    }

    fun pick(language: AppLanguage) {
        if (!AppLanguages.isTranslated(language)) return
        _state.update { it.copy(picked = language, failure = null) }
    }

    /**
     * Apply the pick, and only claim it worked if the platform said so.
     *
     * `LocaleManager` is a system service on a device build we do not control, so
     * [AppLocaleController.apply] can answer false. Writing `applied = picked` regardless would
     * leave the tick on a language the app is not running in.
     */
    fun save() {
        val picked = _state.value.picked
        if (locales.apply(picked)) {
            _state.update { it.copy(applied = picked, failure = null) }
        } else {
            _state.update {
                it.copy(failure = "Android wouldn't change the language for Artistant.")
            }
        }
    }
}

/**
 * Design screen 130 — **"Multilingual India, honestly"**.
 *
 * Six languages and a region block, and two pieces of honesty the design's note is really
 * about.
 *
 * **The first is the design's own**: artist bios and reviews stay in the language they were
 * written in. That is a policy, not a setting, so it is a note at the bottom and not a switch.
 *
 * **The second is ours.** `app/src/main/res` carries exactly one `values/` directory — English.
 * Picking Kannada and getting an English screen would read as a broken app rather than an
 * untranslated one, so the five rows without resources render with the reason ON the row and
 * are not selectable. They stay VISIBLE because the design ships them and because a list that
 * quietly drops five of six languages tells an Indian user less than one that says "not yet".
 * [AppLanguages.TRANSLATED_TAGS] is the single place that changes when a translation lands.
 *
 * **Region is three facts, not three pickers.** v1 is India-only and INR-only — every price in
 * the app is an `int` of rupees and the money math (5% + 18% GST) is hard-coded to it. A
 * country picker here would be a control over a product that does not exist yet.
 */
@Composable
fun LanguageScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LanguageViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LanguageContent(
        state = state,
        onBack = onBack,
        onPick = viewModel::pick,
        onSave = viewModel::save,
        modifier = modifier,
    )
}

@Composable
private fun LanguageContent(
    state: LanguageUiState,
    onBack: () -> Unit,
    onPick: (AppLanguage) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens

    AccountScaffold(
        modifier = modifier.semantics { testTag = "screen.language" },
        header = { BackHeader(title = "Language & region", onBack = onBack) },
        footer = {
            state.failure?.let { message ->
                Text(
                    message,
                    style = AppTheme.type.caption,
                    color = colors.danger,
                    modifier = Modifier.semantics { testTag = "language.failure" },
                )
            }
            PrimaryButton(
                text = "Save",
                onClick = onSave,
                fullWidth = true,
                enabled = state.canSave,
                modifier = Modifier.semantics { testTag = "language.save" },
            )
        },
    ) {
        AccountGap()
        EyebrowLabel("App language", color = colors.ink4)
        Spacer(Modifier.height(dimens.space.sm))
        AppLanguages.all.forEach { language ->
            val translated = AppLanguages.isTranslated(language)
            CheckRow(
                title = language.native,
                subtitle = languageSubtitle(language, translated),
                state = if (language == state.picked) MarkState.Done else MarkState.Pending,
                onClick = if (translated && state.selectable) {
                    { onPick(language) }
                } else {
                    null
                },
                showHairline = language != AppLanguages.all.last(),
                dimWhenPending = !translated,
                modifier = Modifier.semantics { testTag = "language.${language.tag}" },
            )
        }

        AccountGap()
        EyebrowLabel("Region", color = colors.ink4)
        Spacer(Modifier.height(dimens.space.sm))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
        ) {
            RegionFact("Currency", "₹ INR", Modifier.weight(1f))
            RegionFact("Dates", "12 Oct 2026", Modifier.weight(1f))
        }
        Spacer(Modifier.height(dimens.space.md))
        RegionFact("Country", "India", Modifier.fillMaxWidth())
        Spacer(Modifier.height(dimens.space.sm))
        Text(
            "Artistant only operates in India in this version, so the country and currency " +
                "aren't adjustable.",
            style = AppTheme.type.caption,
            color = colors.ink4,
        )

        AccountGap()
        AccentNote(
            text = "Artist bios and reviews stay in the language they were written in — we " +
                "don't machine-translate someone's own words about their act.",
        )
        Spacer(Modifier.height(dimens.space.md))
        Text(
            state.note,
            style = AppTheme.type.caption,
            color = colors.ink4,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { testTag = "language.note" },
        )
        Spacer(Modifier.height(dimens.size.listTailroom))
    }
}

/**
 * The second line of one language row.
 *
 * English says "Default" because it is; the rest say why they cannot be picked. The reason is
 * on the ROW rather than in a footnote so it is answerable at the moment of the tap that does
 * nothing — the design's own "Blocked, and says why" rule (screen 118).
 */
internal fun languageSubtitle(language: AppLanguage, translated: Boolean): String =
    if (translated) language.english else "${language.english} — not translated yet"

/**
 * One read-only region field: a label over a filled well, drawn like an [AppTextField] that
 * has nothing to type into.
 *
 * A disabled text field would state the same fact and invite the tap anyway; this has no
 * interaction to disable.
 */
@Composable
private fun RegionFact(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val shape = RoundedCornerShape(dimens.radii.control)
    Column(modifier) {
        Text(
            label,
            style = AppTheme.type.caption,
            color = colors.ink4,
            modifier = Modifier.padding(bottom = dimens.space.sm),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = dimens.component.control)
                .background(colors.surface3, shape)
                .border(dimens.size.hairline, colors.hairline, shape)
                .padding(horizontal = dimens.space.lg, vertical = dimens.space.md),
        ) {
            Text(value, style = AppTheme.type.body, color = colors.ink)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, heightDp = 900)
@Composable
private fun LanguagePreview() {
    ArtistantTheme {
        LanguageContent(
            state = LanguageUiState(note = AppLanguages.availabilityNote(Build.VERSION_CODES.TIRAMISU)),
            onBack = {},
            onPick = {},
            onSave = {},
        )
    }
}
