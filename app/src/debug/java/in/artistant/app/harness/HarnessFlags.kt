package `in`.artistant.app.harness

import `in`.artistant.app.designsystem.theme.AppRole

/**
 * Parsed launch-argument set for the DEBUG-only screen-testing harness.
 *
 * WHY THIS EXISTS
 * ---------------
 * Every screen behind the auth gate needs a real Supabase session + real rows to render,
 * so visually checking a screen meant signing in with real credentials and hand-building
 * data. This harness lets a debug build boot straight into any tab shell with deterministic
 * in-memory fixtures and no network at all.
 *
 * WHY IT CANNOT REACH A RELEASE BUILD
 * -----------------------------------
 * This file lives in `app/src/debug/`, which Gradle only merges into `debug` build types.
 * A release variant compiles `main + release` and never sees this class, the fixtures, the
 * fakes, or the debug DI module that binds them. There is no runtime flag to flip — the
 * code is physically absent from a release artifact rather than merely disabled.
 *
 * FLAG NAMES mirror the iOS XCUITest harness one-for-one so the two stay learnable together.
 * They arrive as a single comma-separated `am start -e uitest "<tokens>"` string extra,
 * because adb has no argv equivalent to iOS launch arguments.
 *
 *   reset ....................... start from a clean slate (no cached harness state)
 *   skip-signup-as-client ....... boot signed-in as a client, land on the client tabs
 *   skip-signup-as-artist ....... boot signed-in as an artist, land on the artist tabs
 *   seed-fixture-data ........... swap the Supabase repositories for seeded in-memory fakes
 *   seed-pending-request ........ additionally seed one pending_confirm booking (artist side)
 *   land-in-wizard-at-<step> .... boot an artist into the EPK wizard at the named step
 *
 * Tokens are accepted bare (`seed-fixture-data`) or in the iOS spelling
 * (`-uitest-seed-fixture-data`) so a command copied from the iOS suite still works.
 */
data class HarnessFlags(
    /** True when at least one recognised token was present — the master "harness on" switch. */
    val active: Boolean = false,
    val reset: Boolean = false,
    /** Role to boot into, or null to leave the real auth gate alone. */
    val skipSignupAs: AppRole? = null,
    val seedFixtureData: Boolean = false,
    val seedPendingRequest: Boolean = false,
    /** Bare wizard step name (e.g. "identity"); the wizard maps it to its own step enum. */
    val landInWizardAt: String? = null,
) {
    companion object {
        /** `adb shell am start ... -e uitest "skip-signup-as-artist,seed-fixture-data"` */
        const val EXTRA = "uitest"

        /** No flags — what a normal debug launch (no `-e uitest`) resolves to. */
        val NONE = HarnessFlags()

        private const val WIZARD_PREFIX = "land-in-wizard-at-"

        /**
         * Pure parse of the raw extra. Null/blank/garbage yields [NONE], so a normal debug
         * launch is completely unaffected — the harness is strictly opt-in.
         *
         * Separators are commas or whitespace, so both `"a,b"` and `"a b"` work (adb quoting
         * mangles one or the other depending on the shell).
         */
        fun parse(raw: String?): HarnessFlags {
            if (raw.isNullOrBlank()) return NONE

            var seenAny = false
            var reset = false
            var seedFixtureData = false
            var seedPendingRequest = false
            var wizardStep: String? = null
            // Track the two role tokens separately: iOS resolves client-before-artist when
            // both are passed, and treats a wizard flag as implying artist. Recording them
            // independently keeps that precedence explicit rather than order-of-parse luck.
            var sawClient = false
            var sawArtist = false

            for (token in raw.split(',', ' ', '\t', '\n')) {
                when (val t = normalize(token) ?: continue) {
                    // The bare iOS `-uitest` marker: turns the harness on without asking for
                    // any specific behaviour. Useful to force the fakes without a role skip.
                    "uitest" -> seenAny = true
                    "reset" -> { reset = true; seenAny = true }
                    "skip-signup-as-client" -> { sawClient = true; seenAny = true }
                    "skip-signup-as-artist" -> { sawArtist = true; seenAny = true }
                    "seed-fixture-data" -> { seedFixtureData = true; seenAny = true }
                    "seed-pending-request" -> { seedPendingRequest = true; seenAny = true }
                    else ->
                        if (t.startsWith(WIZARD_PREFIX) && t.length > WIZARD_PREFIX.length) {
                            wizardStep = t.removePrefix(WIZARD_PREFIX)
                            seenAny = true
                        }
                        // Anything else is ignored rather than fatal: an unknown token must not
                        // crash a device the operator is mid-demo on.
                }
            }

            if (!seenAny) return NONE

            val role = when {
                sawClient -> AppRole.Client
                sawArtist -> AppRole.Artist
                // A wizard landing is artist-side by definition, so it implies the role.
                wizardStep != null -> AppRole.Artist
                else -> null
            }

            return HarnessFlags(
                active = true,
                reset = reset,
                skipSignupAs = role,
                seedFixtureData = seedFixtureData,
                seedPendingRequest = seedPendingRequest,
                landInWizardAt = wizardStep,
            )
        }

        /**
         * Reduce one raw token to its canonical bare form, or null if it carries no content.
         * Strips leading dashes and an optional `uitest-` prefix so the iOS spelling
         * (`-uitest-seed-fixture-data`) and the bare Android spelling both land on the same
         * key.
         */
        private fun normalize(token: String): String? {
            val trimmed = token.trim().lowercase().trimStart('-')
            if (trimmed.isEmpty()) return null
            return if (trimmed != "uitest" && trimmed.startsWith("uitest-")) {
                trimmed.removePrefix("uitest-").takeIf { it.isNotEmpty() }
            } else {
                trimmed
            }
        }
    }
}

/**
 * Process-wide holder for the parsed flags.
 *
 * Written exactly once, from [HarnessInstaller]'s `onActivityPreCreated` hook, which the
 * framework dispatches *before* `MainActivity.onCreate` runs — and therefore before Hilt
 * field-injects the activity and provisions any repository. That ordering is what lets the
 * debug DI module make a simple provision-time choice between a fake and the real Supabase
 * repository without needing a per-interface delegate.
 *
 * `@Volatile` because the write happens on the main thread while repository provisioning can
 * be requested from a ViewModel's coroutine on another thread.
 */
object HarnessState {
    @Volatile
    var flags: HarnessFlags = HarnessFlags.NONE
        private set

    /** True when this process was launched with harness flags. */
    val active: Boolean get() = flags.active

    /** True when the seeded in-memory repositories should replace the Supabase ones. */
    val useFakes: Boolean get() = flags.active && (flags.seedFixtureData || flags.skipSignupAs != null)

    fun install(parsed: HarnessFlags) {
        flags = parsed
    }
}
