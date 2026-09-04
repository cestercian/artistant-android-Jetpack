package `in`.artistant.app.feature.epk

import `in`.artistant.app.common.util.formatInrShort
import `in`.artistant.app.platform.media.UploadQueue
import java.io.File
import java.util.Locale

/**
 * The press kit as the redesign describes it — design screens 23 / 87 / 76 / 66.
 *
 * Everything in this file is a pure function over plain data, for the reason
 * [EpkLogic]'s header gives: the test classpath is junit + coroutines-test, so a
 * decision that lives inside a `@Composable` cannot be covered. The three
 * decisions the redesign actually introduces are all decisions about *copy* —
 * what a gap costs the artist (23), what an empty row is inviting (87), and what
 * the upload queue is currently doing (76 / 66) — and copy that states a fact is
 * exactly the sort of thing that goes quietly wrong.
 *
 * `EpkLogic.kt` keeps the editor's arithmetic (caps, guards, whole-set replaces);
 * this file keeps the press kit's *narration*.
 */

// ── Sections ─────────────────────────────────────────────────────────────────

/**
 * One fillable block of the kit, and the surface behind its row.
 *
 * Cover and gallery are deliberately absent: screen 23 draws those as their own
 * blocks above the list, not as rows in it, because they are the two things a
 * client sees before they read a word.
 */
enum class EpkSectionKey { Bio, Personality, Samples, Packages, Tech, Links }

/**
 * A row on the press kit (23) or an invitation on the empty one (87).
 *
 * [detail] is the whole point of the screen. Filled, it states the fact — "140
 * words", "2 packages · from ₹26K" — so the artist can see what a client will
 * see without opening the section. Empty, it states the *effect*, which is the
 * design note's "each gap states its effect on enquiries, so finishing the kit
 * feels like earning".
 */
data class EpkSectionRow(
    val key: EpkSectionKey,
    val title: String,
    val filled: Boolean,
    val detail: String,
    /** The empty screen's second line (87) — what belongs in the section. */
    val invitation: String,
)

/**
 * What each gap costs, in the design's voice.
 *
 * **Only one of these carries a number, and that is deliberate.** Screen 23
 * spells out "Missing — adds about 14% to enquiries" for the live video, so that
 * figure is the design owner's and it ships verbatim. The other five gaps are
 * not drawn, and inventing five more percentages would be fabricating a metric
 * this app has never measured — REDESIGN_2026-09 §5.2. They state the effect
 * without pretending to have quantified it, which is the same promise the note
 * makes and one this client can keep.
 */
private fun gapPayoff(key: EpkSectionKey): String = when (key) {
    EpkSectionKey.Bio -> "Missing — hosts read this before anything else"
    EpkSectionKey.Personality -> "Missing — this is what gets you off a shortlist"
    EpkSectionKey.Samples -> "Missing — hosts book what they can hear"
    EpkSectionKey.Packages -> "Missing — a page with no price gets asked, not booked"
    EpkSectionKey.Tech -> "Missing — venues want this before they confirm"
    EpkSectionKey.Links -> "Missing — hosts check you elsewhere before they message"
}

/** What belongs in the section, for the empty screen's invitation rows (87). */
private fun invitation(key: EpkSectionKey): String = when (key) {
    EpkSectionKey.Bio -> "A line or two about your sound"
    EpkSectionKey.Personality -> "Dream venue, the song that never leaves your set"
    EpkSectionKey.Samples -> "30 seconds to 2 minutes works"
    EpkSectionKey.Packages -> "What you offer and what it costs"
    EpkSectionKey.Tech -> "What a venue has to have ready for you"
    EpkSectionKey.Links -> "Anywhere a client should land"
}

/** The row title, one place, so 23 and 87 cannot drift apart. */
private fun sectionTitle(key: EpkSectionKey): String = when (key) {
    EpkSectionKey.Bio -> "Bio and genres"
    EpkSectionKey.Personality -> "Personality prompts"
    EpkSectionKey.Samples -> "Audio samples"
    EpkSectionKey.Packages -> "Packages and pricing"
    EpkSectionKey.Tech -> "Tech rider"
    EpkSectionKey.Links -> "Links and socials"
}

/**
 * The short name a gap goes by in the summary line under the meter.
 *
 * Not [sectionTitle]: "Two things left: a bio and two audio clips" reads as a
 * sentence, and "Two things left: Bio and genres, Audio samples" reads as a
 * error log. The summary is the one place on the screen written as prose.
 */
private fun gapShortName(key: EpkSectionKey): String = when (key) {
    EpkSectionKey.Bio -> "a bio"
    EpkSectionKey.Personality -> "a prompt answer"
    EpkSectionKey.Samples -> "an audio clip"
    EpkSectionKey.Packages -> "a package"
    EpkSectionKey.Tech -> "a tech rider"
    EpkSectionKey.Links -> "a link"
}

/**
 * Every section row, in the order screen 23 lists them.
 *
 * Ordered by what a client meets first, not by the order the editor happens to
 * lay its blocks out — a host reads the bio, listens, and only then looks at
 * what it costs. That is also the order an artist can answer in.
 *
 * [fromPriceInr] is the minimum over the SAVABLE package rows, computed by the
 * caller through the shared `PackagePricing.fromPrice` so the row can never
 * quote a different "from" than the profile it describes. Null when there is no
 * tier to quote, which is the same condition as `packageCount == 0`.
 */
fun epkSectionRows(
    bio: String,
    serviceTagCount: Int,
    answeredPromptCount: Int,
    promptTotal: Int,
    sampleCount: Int,
    packageCount: Int,
    fromPriceInr: Int?,
    techCount: Int,
    linkCount: Int,
    socialCount: Int,
): List<EpkSectionRow> = listOf(
    row(
        key = EpkSectionKey.Bio,
        filled = bio.isNotBlank(),
        fact = {
            val words = bio.trim().split(WHITESPACE).count { it.isNotBlank() }
            val services = if (serviceTagCount > 0) " · ${plural(serviceTagCount, "service")}" else ""
            "${plural(words, "word")}$services"
        },
    ),
    row(
        key = EpkSectionKey.Personality,
        filled = answeredPromptCount > 0,
        fact = { "$answeredPromptCount of $promptTotal answered" },
    ),
    row(
        key = EpkSectionKey.Samples,
        filled = sampleCount > 0,
        fact = { plural(sampleCount, "clip") },
    ),
    row(
        key = EpkSectionKey.Packages,
        filled = packageCount > 0,
        fact = {
            val from = fromPriceInr?.let { " · from ${formatInrShort(it)}" }.orEmpty()
            "${plural(packageCount, "package")}$from"
        },
    ),
    row(
        key = EpkSectionKey.Tech,
        filled = techCount > 0,
        fact = { plural(techCount, "item") },
    ),
    row(
        key = EpkSectionKey.Links,
        filled = linkCount > 0 || socialCount > 0,
        fact = {
            listOfNotNull(
                plural(linkCount, "link").takeIf { linkCount > 0 },
                plural(socialCount, "account").takeIf { socialCount > 0 },
            ).joinToString(" · ")
        },
    ),
)

private fun row(key: EpkSectionKey, filled: Boolean, fact: () -> String) = EpkSectionRow(
    key = key,
    title = sectionTitle(key),
    filled = filled,
    detail = if (filled) fact() else gapPayoff(key),
    invitation = invitation(key),
)

// ── Completion ───────────────────────────────────────────────────────────────

/**
 * The meter and the sentence under it (screen 23).
 *
 * [percent] counts the cover alongside the six section rows, because the cover is
 * a block on the page and a gap on the profile like any other — a kit with every
 * section filled and no cover photo is not a finished kit.
 *
 * The sentence is not a restatement of the meter. "86% complete" says how far;
 * "Two things left: a bio and an audio clip" says what to do next, which is the
 * only half of the pair an artist can act on.
 */
data class EpkCompletion(
    val percent: Int,
    val filled: Int,
    val total: Int,
    val summary: String,
) {
    val isComplete: Boolean get() = filled == total
    /** 0f…1f for the bar. Kept apart from [percent] so the bar never rounds. */
    val fraction: Float get() = if (total == 0) 0f else filled.toFloat() / total
}

fun epkCompletion(rows: List<EpkSectionRow>, hasCover: Boolean): EpkCompletion {
    val total = rows.size + 1
    val filled = rows.count { it.filled } + if (hasCover) 1 else 0
    val gaps = buildList {
        if (!hasCover) add("a cover photo")
        addAll(rows.filterNot { it.filled }.map { gapShortName(it.key) })
    }
    return EpkCompletion(
        // Rounds toward the artist's effort but never claims 100% while something
        // is missing: 6 of 7 is 85.7, which must not print as a finished kit.
        percent = if (total == 0) 0 else (filled * PERCENT / total),
        filled = filled,
        total = total,
        summary = completionSummary(gaps),
    )
}

/**
 * "Two things left: a bio and an audio clip."
 *
 * Long lists are truncated rather than run to six clauses — past three the
 * sentence stops being a next action and becomes a backlog, and the rows below
 * already itemise it.
 */
internal fun completionSummary(gaps: List<String>): String {
    if (gaps.isEmpty()) return "Your press kit is complete — clients see all of it."
    val shown = gaps.take(MAX_SUMMARY_GAPS)
    val truncated = gaps.size > shown.size
    // "a, b and c, and 2 more" has two conjunctions fighting each other. When
    // there is a tail, the shown items are a plain comma list and the tail
    // carries the only "and".
    val listed = when {
        shown.size == 1 -> shown[0]
        truncated -> shown.joinToString(", ")
        else -> shown.dropLast(1).joinToString(", ") + " and " + shown.last()
    }
    val tail = if (truncated) ", and ${gaps.size - shown.size} more" else ""
    val noun = if (gaps.size == 1) "thing" else "things"
    return "${countWord(gaps.size).replaceFirstChar { it.titlecase(Locale.US) }} $noun left: $listed$tail."
}

/** "One", "Two"… up to the seven checks; anything larger stays a numeral. */
internal fun countWord(n: Int): String = when (n) {
    1 -> "one"
    2 -> "two"
    3 -> "three"
    4 -> "four"
    5 -> "five"
    6 -> "six"
    7 -> "seven"
    else -> n.toString()
}

/** "1 word" / "2 words" — the fact lines are counts and half of them are one. */
internal fun plural(n: Int, noun: String): String = if (n == 1) "1 $noun" else "$n ${noun}s"

// ── Upload queue → banner ────────────────────────────────────────────────────

/**
 * What the upload banner says, or null when the queue has nothing to report.
 *
 * Three states, and the design draws two of them (76 working, 66 stalled). The
 * third is "nothing queued", which is the absence of a banner rather than a
 * banner that says so.
 *
 * **Failures outrank work in flight.** A queue can hold both at once — the
 * wizard's publish stages a cover and every clip in one loop, so one poison
 * asset lands in `failed` while the rest are still draining. The artist has
 * something to do about the failure and nothing to do about the progress, so the
 * failure is what the one banner slot carries.
 */
sealed interface EpkUploadBanner {
    /** Screen 76 — "Uploading 2 of 3", the current file, and the batch's progress. */
    data class Working(
        val title: String,
        val detail: String?,
        val fraction: Float,
    ) : EpkUploadBanner

    /** Screen 66's banner — the entry to the stalled sheet. */
    data class Stalled(
        val title: String,
        val detail: String,
        val count: Int,
    ) : EpkUploadBanner
}

fun uploadBannerFor(state: UploadQueue.State): EpkUploadBanner? = when {
    state.failed.isNotEmpty() -> EpkUploadBanner.Stalled(
        title = if (state.failed.size == 1) {
            "One upload couldn't finish"
        } else {
            "${countWord(state.failed.size).replaceFirstChar { it.titlecase(Locale.US) }} uploads couldn't finish"
        },
        detail = "They're saved on this device — retry or discard each.",
        count = state.failed.size,
    )

    state.pending.isNotEmpty() -> {
        // `batchTotal` counts everything enqueued since the queue was last empty
        // and `batchCompleted` how many of those landed, so "k of n" is a real
        // pair rather than a guess. A restored snapshot can hand us a total the
        // completed count has already passed if a drain finished between
        // snapshots, so the position is clamped into the batch.
        val total = maxOf(state.batchTotal, state.batchCompleted + state.pending.size)
        val position = (state.batchCompleted + 1).coerceIn(1, total)
        EpkUploadBanner.Working(
            title = "Uploading $position of $total",
            detail = state.pending.firstOrNull()?.let(::uploadTaskLabel),
            // The BATCH's progress, which is the only progress this client can
            // measure: supabase-kt's storage upload reports no byte counter, so a
            // per-file percentage would be an animation pretending to be a number.
            fraction = if (total == 0) 0f else state.batchCompleted.toFloat() / total,
        )
    }

    else -> null
}

/**
 * What one queued task is called on screen.
 *
 * A clip goes by the title it will publish under, because that is the name the
 * artist chose and will see on their profile. A cover photo has no title — the
 * queue only holds its staged path — so it goes by what it is.
 */
fun uploadTaskLabel(task: UploadQueue.Task): String = when (task) {
    is UploadQueue.Task.CoverPhoto -> "Cover photo"
    is UploadQueue.Task.AudioSample -> task.title.trim().ifBlank { DEFAULT_SAMPLE_TITLE }
}

/**
 * One row on the stalled sheet (66).
 *
 * Built in the ViewModel rather than the Composable because [bytes] is a `stat`
 * on the staged file, and a screen that measured its own files would do file IO
 * inside composition.
 */
data class StalledUpload(
    val id: String,
    val label: String,
    val detail: String,
)

/**
 * "8.4 MB · stopped after 3 tries", or just the tries when the file is gone.
 *
 * The design's line is "8.4 MB · stopped at 64%". The percentage is not
 * available — see [uploadBannerFor] — and a made-up one on a failure screen is
 * the worst place to put a number the app cannot stand behind, so the sentence
 * states what IS known: how big the file is, and how many attempts it burned.
 *
 * A zero or negative size means the staged file has been evicted from the cache,
 * which is also why the upload keeps failing; saying "0 B" would read as an empty
 * file the artist picked.
 */
fun stalledUploadDetail(bytes: Long, attempts: Int): String {
    val n = attempts.coerceAtLeast(1)
    val tries = "stopped after $n ${if (n == 1) "try" else "tries"}"
    return if (bytes > 0) "${formatFileSize(bytes)} · $tries" else tries
}

/**
 * A byte count as a person reads it.
 *
 * One decimal in MB and none below, matching the design's "8.4 MB" / "3.1 MB":
 * "8.43 MB" is precision nobody asked for, and "8 MB" loses the difference
 * between a clip that nearly fits and one that does not. Powers of 1024 because
 * that is what the bucket's own 10 MiB cap is expressed in — quoting 10.5 MB
 * beside a "keep it under 10 MB" rule is how a size limit stops making sense.
 */
fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
        kb >= 1.0 -> String.format(Locale.US, "%.0f KB", kb)
        else -> "$bytes B"
    }
}

/** The staged file's own name, for a task that has no better label. */
fun stagedFileName(path: String): String = File(path).name

/** Where a queued task's staged bytes live — the one place the kinds converge. */
fun stagedPathOf(task: UploadQueue.Task): String = when (task) {
    is UploadQueue.Task.CoverPhoto -> task.filePath
    is UploadQueue.Task.AudioSample -> task.filePath
}

/**
 * The stalled sheet's rows (66), built from the queue's burned tasks.
 *
 * [sizeOf] is injected so the mapping can be tested without staging real files,
 * and so the caller decides which dispatcher the `stat` runs on — the ViewModel
 * runs this on IO. A missing file measures 0 and [stalledUploadDetail] says so by
 * omission rather than printing "0 B".
 *
 * A clip goes by its title and a cover by its name, so the two rows on the design
 * are the two shapes here: "Cover video" is a titled asset, "encore-bengaluru.m4a"
 * is a file. This client's cover task has no title, so it falls back to the
 * staged file name — which is the honest answer, and the one that lets an artist
 * with two stalled clips tell them apart.
 */
fun stalledRowsFor(
    state: UploadQueue.State,
    sizeOf: (String) -> Long = { path -> runCatching { File(path).length() }.getOrDefault(0L) },
): List<StalledUpload> = state.failed.map { task ->
    val path = stagedPathOf(task)
    StalledUpload(
        id = task.id,
        label = when (task) {
            is UploadQueue.Task.CoverPhoto -> stagedFileName(path).ifBlank { "Cover photo" }
            is UploadQueue.Task.AudioSample -> uploadTaskLabel(task)
        },
        detail = stalledUploadDetail(bytes = sizeOf(path), attempts = task.attempts),
    )
}

// ── Link validation ──────────────────────────────────────────────────────────

/**
 * Why this address cannot be saved, or null when it can.
 *
 * The link sheet used to accept any non-blank string, which meant "banccamp" and
 * "my page" both saved and then rendered on the artist's PUBLIC profile as a tap
 * target that goes nowhere. A client who taps a dead link does not conclude the
 * link is broken; they conclude the act is.
 *
 * The check runs on the NORMALIZED value, because that is what gets stored —
 * `normalizeLinkUrl` has already prefixed the scheme an artist did not type, so
 * validating the raw string would reject exactly the input the normaliser exists
 * to accept.
 *
 * Deliberately not a `Patterns.WEB_URL` match: that is an Android framework
 * class, unreachable from this classpath, and permissive enough to accept
 * "a.b" anyway. What actually goes wrong is a typo'd host with no dot in it and
 * a pasted string with a space in it, so those are what this names.
 */
fun linkUrlProblem(raw: String): String? {
    val url = normalizeLinkUrl(raw)
    if (url.isBlank()) return "Add the address a client should land on."
    if (url.any { it.isWhitespace() }) return "A web address can't contain spaces."
    if (url.startsWith("mailto:") || url.startsWith("tel:")) {
        return if (url.substringAfter(':').isBlank()) "Add an address after the colon." else null
    }
    val scheme = url.substringBefore("://", missingDelimiterValue = "")
    if (scheme != "http" && scheme != "https") {
        return "Use a web address starting with https://"
    }
    val host = url.substringAfter("://").substringBefore('/').substringBefore('?')
    if (host.isBlank()) return "Add the site this should open."
    // A host with no dot is a hostname on somebody's LAN, not a site a client can
    // reach — and in practice it is a half-typed domain.
    if (!host.contains('.') || host.startsWith('.') || host.endsWith('.')) {
        return "That doesn't look like a web address — check the spelling."
    }
    return null
}

/** The label half. A link with no label has nothing to render in the list. */
fun linkLabelProblem(raw: String): String? =
    if (raw.isBlank()) "Give the link a name a client will recognise." else null

// ── Constants ────────────────────────────────────────────────────────────────

private const val PERCENT = 100
private const val MAX_SUMMARY_GAPS = 3
private val WHITESPACE = Regex("\\s+")
