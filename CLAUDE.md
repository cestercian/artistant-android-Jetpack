# CLAUDE.md — Artistant Android (session bootstrap)

Read this first, every session. It's the maintainer's guide for porting
**Artistant** (the SwiftUI iOS app) to a **native Kotlin + Jetpack Compose**
Android app. Same product, same Supabase backend, same design language.

Maintainer voice for this repo is **Linus**: blunt, high standards, good taste,
allergic to over-engineering and to broken builds landing on `main`. We port like
we're upstreaming to the kernel — small reviewable series, green tree always,
clean history. If a change doesn't build, it doesn't merge. No exceptions.

---

## What this is

Artistant is a two-sided marketplace for booking live performers in India. v1 is a
**no-payments matchmaker** (match → chat → confirm). Hero: **Bookability Score™**.
Trust: **Airbnb-style** (safety banner + “always communicate through Artistant” +
report) — chat redaction was **scrapped Jul 2026** (mig `0071`). Booking =
**request → accept** (`pending_confirm`, “Request sent.”; artist Accept/Decline).
Dark-only, phone-only, portrait, INR.

- **iOS source of truth:** `~/Desktop/ios-swift` (145 Swift files). Read it when
  porting a screen — match its behaviour and design.
- **Shared backend:** the same Supabase project the iOS + web clients use. **We do
  not fork the schema.** The only server changes Android forces are an FCM push
  path and (later) Play billing notifications. See `docs/API_MAPPING.md`.
- **The plan lives in `docs/`** — nine documents. `ANDROID_MIGRATION_PLAN.md` is
  the index; others cover architecture, screens, API, features, roadmap,
  structure, risks, plus **`PARITY_CHECKLIST.md`** (iOS file → Android status).
  **Read the relevant doc before touching a layer.**

---

## Golden rules — don't break these

1. **Green tree gate.** Nothing merges unless `./gradlew :app:assembleDebug`
   compiles and `./gradlew :app:testDebugUnitTest` passes. That is *the* gate.
2. **Design parity with the "Artistant iOS Light" design (Sep 2026).** The
   product owner replaced the dark, dual-accent look with a light, single-accent
   design of 138 screens — `docs/REDESIGN_2026-09.md` is the plan and the token
   sheet; the extracted screens live outside the repo (path in that doc). Use the
   design tokens (`AppColors`/`AppType`/`Space`/`Size`/`Radii`) — **never** a raw
   hex/dp/sp. Plus Jakarta Sans + JetBrains Mono, hairlines, no card chrome, one
   lime accent (`#d6f84b`) per screen for both roles, honest loading/empty/failed
   states. Match the design screen; don't "improve" it.
3. **Same backend, respect its rules.** RLS is the whole authorization model.
   Lowercase every UUID. **Never `select("*")` on `messages`** (`body_raw` 403s —
   use explicit columns). Read denormalized `client_name` (never embed `users`).
   Honor booking guards (self-booking, no-overlap, status state-machine). Money
   math (5% platform + 18% GST) is client-side.
4. **Repository seam is the boundary.** ViewModels never touch supabase-kt
   directly. Every repo is an `interface` + `Supabase*` impl + `Fake*` twin.
5. **No over-engineering.** No KMP, no Retrofit, no Room (yet), no multi-module
   Gradle (yet), no base-classes-for-one-subclass. One interface with one
   implementation is not an abstraction. If it's speculative, don't build it.
6. **Clean history.** One issue → one branch → one PR → squash-or-rebase to a
   tidy series → merge. No "wip" commits on `main`, no force-push to `main`, no
   merge that leaves the tree red.

---

## Workflow — how work lands (the Linus loop)

Every phase is a **GitHub issue**. Every issue gets a **branch**, a **PR that
closes it**, a **green build**, a **review**, then a **merge**.

```
issue #N (a phase)  →  branch feature/<slug>  →  implement (subagents in worktrees)
   →  ./gradlew assembleDebug + testDebugUnitTest GREEN  →  PR "Closes #N"
   →  maintainer review  →  merge to main  →  next phase
```

- **Branch off `main`**, short-lived, one phase of work. Name `feature/<slug>` or
  `chore/<slug>`.
- **Never commit straight to `main`** except repo-bootstrap/docs. Features go
  through a PR.
- **Commit author:** `Cestercian <yashafaid@gmail.com>` with a
  `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>` trailer. Use:
  `git -c user.name=Cestercian -c user.email=yashafaid@gmail.com commit ...`
- **Never** `--force`/`--amend`/`--no-verify` on shared history. Append-only.
- **PR body** links the issue (`Closes #N`), says what was verified (build + unit
  tests — **note that emulator/instrumented runs are NOT done in this
  environment**, see below), and lists any follow-ups.

---

## Build & test — exact commands

The build machine has **no `java`/`gradle`/`adb` on PATH**. Use the Android Studio
JBR (JDK 21) + the local SDK. Set these once per shell:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
```

`local.properties` (gitignored) pins `sdk.dir=/Users/yashaf/Library/Android/sdk`.

```bash
# compile-check (the green-tree gate — run before every commit/PR)
./gradlew :app:assembleDebug

# fast unit tests (pure logic: money math, score bands, planner)
./gradlew :app:testDebugUnitTest

# lint (run before a PR; don't let it rot). Flavors exist (dev/prod/staging),
# so bare lintDebug is ambiguous — lint the dev variant.
./gradlew :app:lintDevDebug
```

**Environment boundary — be honest about it.** This machine can **compile + run
JVM unit tests**. It has **no AVD** and **no `cmdline-tools`** to create one, so we
**cannot run the app on an emulator or execute instrumented (androidTest) UI tests
here**. "Verified" on a PR means *builds green + unit tests pass*. Instrumented/
device verification is deferred to a machine with an emulator (operator step). Say
so on every PR; don't claim a screen "works" when it has only been compiled.

**Toolchain (installed):** JDK 21 (JBR), Android SDK platform `android-36.1`,
build-tools `36.1.0`/`37.0.0`, platform-tools. compileSdk/targetSdk = **36**,
minSdk = **26**.

---

## Stack (locked)

Kotlin · Jetpack Compose (Material 3) · MVVM + StateFlow · Hilt · Navigation-Compose
(type-safe routes) · **supabase-kt** (Auth/Postgrest/Realtime/Storage/Functions) ·
Coroutines/Flow · kotlinx.serialization · Coil 3 · Media3 (ExoPlayer + Transformer)
· CameraX · WorkManager · DataStore · FCM · Play Billing (dormant) · PostHog +
Sentry (dark-until-key) · Timber. Single `:app` module, package-by-feature (see
`docs/PROJECT_STRUCTURE.md`). Version catalog in `gradle/libs.versions.toml`.

---

## Phase plan (GitHub issues track the truth)

Milestones M0–M8 (see `docs/IMPLEMENTATION_ROADMAP.md`). Each is an issue.

- **M0 Foundation** — Gradle/Compose skeleton, theme/design system, DI, nav shell,
  supabase client, DataStore. *Green build is M0's definition of done.*
- **M1 Auth & onboarding** · **M2 Browse** · **M3 Booking funnel** ·
  **M4 Messaging & push** · **M5 Artist authoring** · **M6 Platform & DPDP** ·
  **M7 Payments seam & polish** · **M8 Hardening & release**.

Update this section's "current state" line as phases land.

**Current state:** M0–M7 on `main` (PR #44). Follow-up **`feature/parity-polish`**
closes remaining partials: BookingDetail Message/Getting there/calendar/review;
Artist Home earnings + busy strip + quote requests; Messages All/Bookings/
Inquiries filters; MonthDayGrid on Bookings/Gigs; Help/Feedback → `app_feedback`;
SearchRecents; checkout entitlement gate; dead DeepLinkRouter removed (TabRouter
is the live push path). Product truth: redaction retired, request→accept,
Airbnb chat trust. **Unit tests green**.

**Aug 5, 2026 — post-merge hardening wave (PRs #47–#50, all merged, bot-reviewed):**
#47 unknown booking statuses decode to a non-actionable `Unknown` ("Unavailable",
iOS #111 parity) + calendar retracts a mirrored event when its booking turns
Unknown; #48 R8 re-enabled for release (minify + shrink; #44 had regressed the
flag AND left the DTO keep rule pointing at the old `data.model` package —
repointed); #49 rebuilt the unit coverage the #44 prefer-ours merge deleted
(suite 129 → 277, including a mutation-tested chat realtime in-flight race test);
#50 month grouping on Bookings/Gigs no longer renders one header per row
(`monthLabelFromDateLabel` could never match its own parse). **277 unit tests,
0 failures.**

**Aug 12, 2026 — blocking became reversible (`feat/blocked-accounts`).** PR #71
shipped block with its only exit inside the conversation the block hides, so an
accidental block was permanent from the UI. A **Blocked accounts** screen now
hangs off the account settings list on both roles (`feature/profile/`), lists who
you've blocked and unblocks them through the same `BlockedUsersStore` the inbox
observes, so the thread returns immediately. `BlockedUsersStore.refresh()` now
returns whether the SERVER copy was read, because loaded-and-empty and
couldn't-load are the same empty list and the opposite meaning; a failed read
renders as "couldn't load" with a retry, never as "no one is blocked". Names are
inferred from the conversation (artist cache / `client_name`, mig 0080) and left
null when nothing can answer — `blocked_users` (0087) stores no name. New
debug-only harness flags: `seed-blocked-user`, `block-list-unavailable`. Device-
walked on the emulator. **Suite 737, 0 failures.**

**Sep 3, 2026 — "why is Android worse than iOS" series (PRs #133–#137).** The
honest answer was that every phone build had been the **debug build type**: the
release build type had no signing config, so nothing else was installable, and
debug Compose (no R8, no AOT, debug composer) is a different, slower app than
anything TestFlight ships. #133 wires `keystore.properties` signing with a
debug-key fallback for dev/staging release only (prod stays unsigned) and adds
`profileinstaller` so the Compose/AndroidX baseline profiles actually install.
#135 moves to Compose 1.10 / Material3 1.4 (BOM 2025.12.01 — newest that builds
on AGP 8.9 + compileSdk 36). #136 ports `Haptic.swift` as `designsystem/Haptics.kt`
and fires it at 22 of iOS's 26 non-signup sites. #134 closes #15 (signup raw
sp/dp → tokens). #137 closes #18 (gallery strip + Spotify embed; sample playback
had shipped in #82). The Aug-16 audit series was already fully merged (#84–#118);
#113 and #26 were stale and closed. **To judge performance, install
`assembleDevRelease`, never the debug APK** (RELEASE.md §3). **Suite 1252, 0
failures** on the five branches combined; none device-walked.

**Still operator / follow-ups:** upload keystore + `keystore.properties`,
`google-services.json` + `send-push` FCM (#24), OAuth dashboard config, flip
`subscriptionsEnabled`, PROF-10 hero pager, an app-specific baseline profile
(needs a device), AGP 9.1 + compileSdk 37 to unlock Compose 1.11+, M8
instrumented UI / Play upload.
(Profile stats + community pledge shipped on `feature/parity-polish`.)
The Aug-5 parked findings are **closed**: the audit series had already given
`BookingStatusTimeline` two call sites (ConfirmedScreen + BookingDetail's
Progress section) and already keyed `bookingsCount` on `liveBookingsCount`
(cancelled excluded); the push pair is fixed here — `PushPayloadRouter` now
reads a blank id as an absent one (a blank `booking_id` used to reach
`nav.navigate("booking_detail/")`, which matches no destination) and
`TabRouter.apply(Ignore)` is inert, so an unroutable payload no longer wipes
the deep link an earlier tap armed. Still open: ship `mapping.txt` with every
Play release (see RELEASE.md §0).

---

## Where to learn more

| File | What |
|---|---|
| `docs/ANDROID_MIGRATION_PLAN.md` | Index + executive summary + scope |
| `docs/ARCHITECTURE.md` | App structure, decisions, library rationale |
| `docs/PROJECT_STRUCTURE.md` | Complete package tree |
| `docs/SCREEN_INVENTORY.md` | 45 screens + 27 components → Compose; design tokens |
| `docs/API_MAPPING.md` | Supabase contract + the FCM change |
| `docs/FEATURE_CHECKLIST.md` | Features → tasks (complexity/deps/risk) |
| `docs/IMPLEMENTATION_ROADMAP.md` | Milestones M0–M8 |
| `docs/RISKS_AND_DECISIONS.md` | iOS-only APIs, risks, security, a11y, ADRs |
| `docs/REDESIGN_2026-09.md` | **The Sep-2026 light redesign: design language, token map, phases, agent rules** |

## The one thing I'd tell my replacement

Match the iOS app's taste — the user has strong, specific UI opinions and will
catch drift. When porting a screen, open the Swift file and mirror it. Don't
gold-plate, don't add abstractions "for later," don't let a red build merge. Small
series, green tree, clean history. That's the whole job.
