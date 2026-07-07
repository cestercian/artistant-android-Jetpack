# ANDROID_MIGRATION_PLAN.md — Artistant

The master plan for building a **native Android** app (Kotlin + Jetpack Compose)
that reaches feature parity with the existing SwiftUI iOS app, sharing the same
Supabase backend unchanged. This is the entry point; the details live in the seven
companion documents linked below.

---

## 1. Executive summary

**Artistant** is a two-sided marketplace for booking live performers in India
("Zomato/UrbanCompany for live artists"). Both sides — clients booking artists,
artists managing bookings — live in one app, gated by a role pick at signup. v1 is
a **no-payments matchmaker**: match, negotiate in chat, confirm. Hero feature is
the **Bookability Score™** (0–100). Moat: **anti-leakage chat redaction** (contact
info stripped until a booking is confirmed). Dark-only, phone-only, India, INR.

The iOS app is **145 Swift files**: 45 screens, 27 reusable components, 12 state
stores, 14 repositories, 18 services, over a Supabase backend of **~24 tables, 16
Edge Functions, 4 storage buckets, 6 client RPCs, and 1 realtime channel**.

**The plan:** rebuild it natively on Android, copying the *architecture shape*
(repository seam, role-scoped tabs, tokenized design system) but adopting
**Android-native idioms** (ViewModel + StateFlow, Hilt, Navigation-Compose,
WorkManager, supabase-kt). The **backend is reused as-is** except two additions
Android forces: an **FCM push path** and (later) **Play billing notifications**.

**Effort:** ~14–18 weeks solo to full parity; core matchmaker (browse/book/chat)
demoable at ~6–7 weeks with a 2–3 dev team.

---

## 2. Scope

**In scope (feature parity):** auth (Google/Apple/email) · client onboarding ·
artist onboarding wizard · Discover · Search + filters · Artist profile · booking
funnel · gig requests · bookings + calendar · realtime chat with redaction · push ·
artist dashboard · EPK editor · Bookability Score · reviews · profile/settings ·
data export + account deletion (DPDP) · device calendar sync · media
(camera/photo/audio/video-trim/playback) · observability · payments **seam**
(dormant).

**Out of scope (v1):** live payments/escrow (matchmaker model — built as an inert
seam, same as iOS) · KYC UI (dormant) · light theme · tablet layout · offline-first
database · any backend rewrite.

**Explicit non-goals:** Kotlin Multiplatform / shared code (fully native, separate
app) · Retrofit (supabase-kt only) · Room up front · multi-module Gradle up front ·
Google Maps (the app has no map — location is a city string). See
[ARCHITECTURE.md](ARCHITECTURE.md) §13.

---

## 3. Technology decisions (headline)

| | Choice |
|---|---|
| Language / UI | Kotlin + Jetpack Compose (Material 3), dark-only, phone/portrait |
| Architecture | MVVM + UDF; repository interfaces + `Fake*` twins; thin domain layer |
| DI | Hilt |
| Navigation | Navigation-Compose (type-safe routes), two role-scoped graphs |
| Backend SDK | **supabase-kt** (Auth, Postgrest, Realtime, Storage, Functions) |
| Async / data | Coroutines + Flow; kotlinx.serialization; DataStore (no Room initially) |
| Media | Media3 (ExoPlayer + Transformer), CameraX, Photo Picker, SAF |
| Background | WorkManager (upload queue) |
| Push / billing | FCM; Play Billing (dormant) |
| Calendar / auth | CalendarContract; Credential Manager (Google) + Custom Tabs (Apple) |
| Observability | PostHog + Sentry (dark-until-key), Timber |

Full rationale + versions: [ARCHITECTURE.md](ARCHITECTURE.md) §1, §14.

---

## 4. How the documents fit together

| Document | Answers | Use when |
|---|---|---|
| **ANDROID_MIGRATION_PLAN.md** (this) | What are we building and why; scope; how it fits | Start here / onboarding |
| [ARCHITECTURE.md](ARCHITECTURE.md) | How the app is structured; every cross-cutting decision; library stack | Setting up patterns, DI, theme, error handling |
| [PROJECT_STRUCTURE.md](PROJECT_STRUCTURE.md) | The complete package/directory tree | Creating files, finding where code goes |
| [SCREEN_INVENTORY.md](SCREEN_INVENTORY.md) | Every screen + component → Compose; design tokens; SwiftUI→Compose API map | Building any screen or component |
| [API_MAPPING.md](API_MAPPING.md) | The Supabase contract: tables, RLS, storage, realtime, Edge Functions, RPCs, auth; the FCM change | Wiring any repository or backend call |
| [FEATURE_CHECKLIST.md](FEATURE_CHECKLIST.md) | Every feature → tasks with complexity/deps/risk; independence map | Planning sprints, tracking parity |
| [IMPLEMENTATION_ROADMAP.md](IMPLEMENTATION_ROADMAP.md) | Milestones M0–M8, exit criteria, critical path | Sequencing the build |
| [RISKS_AND_DECISIONS.md](RISKS_AND_DECISIONS.md) | iOS-only functionality, Android challenges, perf, security, a11y, ADRs, iOS issues found | De-risking, reviews, "why did we…" |

---

## 5. Architecture in one screen

Four layers, top-down (mirrors iOS Screens → State → Repositories → Services →
Supabase):

```
Compose UI  ──observes──▶  ViewModel (StateFlow<UiState> + event Channel)
                                │ calls
                     (thin) Domain — pure logic: booking math, score bands,
                                │        redaction, calendar planner
                     Repository interface ──▶ Supabase* impl  (+ Fake* for tests)
                                │ calls
              Platform services: Auth, Push(FCM), Calendar, Upload(WorkManager),
                     Media(Media3), Permissions, Billing, Observability
                                │
                        Supabase (shared, unchanged)
```

- **Role-reactive theme:** client → lime `#C8FF00`, artist → violet `#7C5CFF`;
  one flag re-themes every brand surface.
- **Server-is-truth, no local DB:** in-memory state + DataStore mirror (matches
  iOS; Room is a documented upgrade path, not built now).
- **Navigation:** auth-gate → Signup / Wizard / role tabs (client: Discover ·
  Bookings · Messages · Profile · Search; artist: Home · Gigs · Messages · EPK).

---

## 6. Backend reuse (Phase 8 summary)

**Reused unchanged:** Supabase Auth (Google/Apple/email; anonymous disabled), the
entire Postgres schema + RLS, all 4 storage buckets, the `messages` realtime
channel, the 6 client RPCs (`search_artists`, `search_facets`,
`handle_is_available`, `replace_{packages,samples,tech_rider}`), and the 4
user-facing Edge Functions (`create-booking`, `cancel-booking`, `data-export`,
`delete-account`) plus all internal scoring/reminder cron.

**Must change for Android (server-side):**
1. **Push** — `send-push` is APNs-only; add an **FCM sender path** + an FCM-token
   table/column. The event/deep-link payload contract is reusable.
2. **Billing (later)** — add a **Google Play RTDN** sibling to
   `app-store-notifications` when IAP goes live; the `subscription_account_tokens`
   binding pattern carries over.

Everything else is client-agnostic. Full contract + the "will bite you" list:
[API_MAPPING.md](API_MAPPING.md).

**Critical client rules carried from iOS (correctness, not style):** lowercase all
UUIDs · explicit column lists on `messages` (a `select("*")` 403s) · read
denormalized `client_name` (never embed `users`) · honor booking guards
(self-booking, no-overlap, status state-machine) · client-side money math (5% + 18%)
· gig-request accept = a plain status PATCH.

---

## 7. Milestones (Phase 6 summary)

| M | Goal | Exit |
|---|---|---|
| **M0** Foundation | compiles, themes, navigates, reaches Supabase | tabbed shell + design-system gallery |
| **M1** Auth & onboarding | sign in (3 ways), role routing | real-device sign-in; returning user routes in |
| **M2** Browse ("home feed") | Discover + Search + Artist profile | browse real artists, open a full profile |
| **M3** Booking funnel | book end-to-end + calendar + reviews | booking respects guards, shows on calendar |
| **M4** Messaging & push | realtime chat + redaction + FCM | two accounts chat live; push deep-links |
| **M5** Artist authoring | wizard + dashboard + EPK + gig requests | new artist goes live, handles a request |
| **M6** Platform & DPDP | settings + export/delete + calendar sync + observability | export/delete work; gigs mirror to calendar |
| **M7** Payments seam & polish | dormant billing + glass approx + a11y | paywall renders inert; a11y passes |
| **M8** Hardening & release | test net + perf + Play upload | CI green; internal-testing build live |

*→ End of M4 is a usable client matchmaker (alpha). Details + critical path:
[IMPLEMENTATION_ROADMAP.md](IMPLEMENTATION_ROADMAP.md).*

---

## 8. Top risks (front-load these)

Apple sign-in (no native SDK) · backend FCM change · Media3 video trim (device
codec variance) · CalendarContract port (largest platform piece) · realtime dedup
race · supabase-kt API drift. Spike each during M0–M1. Full register:
[RISKS_AND_DECISIONS.md](RISKS_AND_DECISIONS.md) §9.

**Operator/back-end prerequisites (not client code, schedule early):** add the
Android redirect URL to Supabase; create the Firebase project +
`google-services.json` + FCM service account; add the FCM path to `send-push`; Play
Console app + Play App Signing; (later) Play billing products + RTDN.

---

## 9. Success criteria

- Every item in [FEATURE_CHECKLIST.md](FEATURE_CHECKLIST.md) ticked (iOS parity).
- Sign-in (Google/Apple/email) works on real devices; DPDP export + delete work.
- Realtime chat with redaction; push deep-links; calendar sync mirrors gigs.
- Backend reused with only the FCM addition live on prod; no schema regressions.
- Unit + Compose-UI test suites green (the XCUITest-parity net); a11y audit passes;
  cold-start + scroll perf acceptable (baseline profile in place).
- Internal-testing build on Play; dark-only, phone, INR, feature-matched to iOS.

---

## 10. Working rules for the build (carry-over from iOS house style)

- **Use the design tokens** — never a raw dp/sp/hex; go through `AppType`/`Space`/
  `Size`/`Radii`/`AppColors`.
- **Hairlines, no card chrome, mono numerals, editorial serif headlines, accent =
  one signal per screen.** The UI direction is locked; don't drift.
- **Repository seam is the boundary** — ViewModels never touch supabase-kt directly.
- **Surface backend guard errors truthfully** — never swallow a rejected write.
- **Keep the client redaction regexes in sync** with the DB triggers (shared pure
  module, same test fixtures).
- **Commit small, keep it buildable** — the Debug build compiling is the green-tree
  gate (mirrors the iOS simulator-Debug rule).
