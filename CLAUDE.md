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
Moat: **anti-leakage chat redaction** (contact info hidden until a booking is
confirmed). Dark-only, phone-only, portrait, INR.

- **iOS source of truth:** `~/Desktop/ios-swift` (145 Swift files). Read it when
  porting a screen — match its behaviour and design.
- **Shared backend:** the same Supabase project the iOS + web clients use. **We do
  not fork the schema.** The only server changes Android forces are an FCM push
  path and (later) Play billing notifications. See `docs/API_MAPPING.md`.
- **The plan lives in `docs/`** — eight documents. `ANDROID_MIGRATION_PLAN.md` is
  the index; the others are architecture, screens, API contract, feature
  checklist, roadmap, structure, risks. **Read the relevant doc before touching a
  layer.**

---

## Golden rules — don't break these

1. **Green tree gate.** Nothing merges unless `./gradlew :app:assembleDebug`
   compiles and `./gradlew :app:testDebugUnitTest` passes. That is *the* gate.
2. **Design parity with iOS.** Match the iOS screen/flow/feel. Use the design
   tokens (`AppColors`/`AppType`/`Space`/`Size`/`Radii`) — **never** a raw
   hex/dp/sp. Hairlines, no card chrome, mono numerals, editorial serif headlines,
   **accent = one signal per screen** (client lime `#C8FF00` / artist violet
   `#7C5CFF`). The UI direction is locked; don't "improve" it.
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

# fast unit tests (pure logic: money math, score bands, redaction, planner)
./gradlew :app:testDebugUnitTest

# lint (run before a PR; don't let it rot)
./gradlew :app:lintDebug
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

**Current state:** **M0–M5 complete — the app is FUNCTIONALLY COMPLETE for both
roles**, all merged, green, adversarially reviewed, runs on an emulator. A client
signs up → browses → books → realtime-chats; an artist onboards (11-step wizard) →
publishes → manages the ArtistHome dashboard + EPK editor. Over the live shared
`artistant-dev` backend. Stack (see `gradle/libs.versions.toml`): AGP 8.9.1, Gradle
8.13, Kotlin 2.1.0, Compose BOM 2024.12.01, Hilt 2.54, supabase-kt 3.0.3, Ktor 3.0.1.
Shipped: M1 auth+signup · M2 browse · M3 booking funnel+calendar · M4 messaging
(realtime chat + redaction; push is operator-gated, runbook in `docs/PUSH_SETUP.md`)
· M5 artist authoring (write repos + media pipeline on WorkManager/Media3, wizard,
dashboard, EPK). **188 unit tests, 0 failures**, warning-free. Dev creds wired
(gitignored). **M6 (Platform, settings & DPDP) next** — Profile/settings, data-export,
delete-account, CalendarSync (CalendarContract), analytics/crash. Then M7 (payments
seam + polish) · M8 (hardening + release).

Canonical checkout is **`~/AndroidStudioProjects/artistant-android`** (the old
`~/Desktop/artistant-android` is abandoned — macOS blocked tool access to Desktop).

Open tracked issues: #12 (Apple-OAuth deep-link error handling — blocks Apple
go-live), #15 (signup design-token polish + brand assets), #18 (gallery strip /
Spotify embed / audio playback — M5/M7), #24 (Push/FCM activation — operator +
backend), #26 (wizard draft persistence), #28 (M5c ArtistHome/EPK cosmetic parity).
Batch the cosmetic/token/font ones into the M7 polish pass. Operator: no emulator in the agent
env (compile + unit-test only — but the user CAN run it); Google needs
`GOOGLE_WEB_CLIENT_ID` + SHA-1; Supabase dashboard needs the Android redirect +
Apple provider; drop brand `.ttf` into `res/font/`; real launcher icon. Backend
unchanged (shared with iOS).

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

## The one thing I'd tell my replacement

Match the iOS app's taste — the user has strong, specific UI opinions and will
catch drift. When porting a screen, open the Swift file and mirror it. Don't
gold-plate, don't add abstractions "for later," don't let a red build merge. Small
series, green tree, clean history. That's the whole job.
