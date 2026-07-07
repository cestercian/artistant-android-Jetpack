# RISKS_AND_DECISIONS.md — Artistant Android

Technical risks, iOS-only functionality needing Android solutions, the key
architectural decisions (with rationale), and the iOS-app issues found during
analysis with recommended Android improvements.

---

## 1. iOS-only functionality → Android solution

| iOS capability | iOS API | Android solution | Risk |
|---|---|---|---|
| Sign in with Apple | AuthenticationServices | **OAuth web flow** in Custom Tabs (no native SDK) + nonce | **High** — most fragile auth path; test on device early |
| Google sign-in | Supabase OAuth via Safari | Credential Manager + Google Identity → ID token | Med — console SHA-1, nonce setup |
| Push (APNs) | UserNotifications | **FCM** + backend `send-push` FCM path + token table | **High** — needs server change |
| Calendar mirror | EventKit / EventKitUI | **CalendarContract** (ContentResolver) + `ACTION_INSERT` | **High** — largest platform port |
| IAP (StoreKit 2) | StoreKit | **Play Billing** + Play RTDN backend | Med — dormant in v1 |
| Video trim/transcode | AVAssetExportSession | **Media3 Transformer** | **High** — device codec variance |
| Audio/video playback | AVAudioPlayer / AVPlayer | **Media3 ExoPlayer** | Low |
| Photo pick | PhotosUI (PHPicker) | **Photo Picker** (`PickVisualMedia`) | Low |
| Camera capture | UIImagePickerController | **CameraX** / capture intents | Med |
| Audio file pick | UIDocumentPicker | **SAF** `OpenDocument("audio/*")` | Low |
| Spotify embed | WKWebView | **WebView** | Low |
| Location | CoreLocation | **FusedLocationProvider** | Low |
| Session storage | Keychain (implicit) | supabase-kt encrypted session (DataStore/EncryptedSharedPrefs) | Low |
| Crypto (nonce) | CryptoKit | `SecureRandom` + `MessageDigest(SHA-256)` | Low |
| iOS-26 Liquid Glass | `.glassEffect`, minimizing tab bar, `.search`-role tab | translucent Material 3 surfaces (+ RenderEffect blur, API 31+) | Med — visual divergence |
| Dynamic Type | `relativeTo:` | Compose honors `fontScale` automatically | Low |
| Haptics | UIFeedbackGenerator | `HapticFeedback` / `performHapticFeedback` | Low |

---

## 2. Android-specific challenges

- **Process death & config changes.** SwiftUI has no real analogue. **Hoist all
  screen state into ViewModels**, use `SavedStateHandle` for the few values that
  must survive process death mid-flow (in-progress booking draft, wizard step),
  and drive one-shot navigation via events (not re-derived from state) so rotation
  doesn't re-fire it.
- **Back stack & predictive back.** iOS `NavigationStack` pop is a swipe; Android
  has the system back button + Android 14+ predictive-back. Wire proper back
  handling per destination (esp. the step-driven signup/wizard flows, which have no
  back stack — handle system back explicitly).
- **supabase-kt maturity.** It tracks supabase-swift but API shapes differ (e.g.
  `Columns.raw` for embeds, `postgresChangeFlow` for realtime). Budget time to
  reconcile each repo call; pin a version.
- **Fragmentation.** Test camera/trim/calendar across a matrix (an emulator +
  ≥2 physical devices, incl. one older/low-RAM). Media3 Transformer output varies
  by hardware codec.
- **Notification permission (API 33+).** Must be requested at runtime and can be
  permanently denied — degrade gracefully (in-app inbox still works).
- **Photo/media permissions churn.** API 33 split `READ_EXTERNAL_STORAGE` into
  `READ_MEDIA_IMAGES/VIDEO`; prefer the permission-less Photo Picker to sidestep it.
- **Background execution limits.** Uploads must use WorkManager (+ a foreground
  service for long batches) or Doze/limits will kill them — don't hand-roll the
  iOS queue's threading model.

---

## 3. Performance considerations

- **Compose recomposition.** Use `collectAsStateWithLifecycle`, stable/immutable
  state classes (`@Immutable` UI-state data classes), and `key`s in `LazyColumn`/
  `LazyRow`/grids. The Discover rails + Search grid + Chat list are the hot lists —
  keep item composables cheap and hoist derived values.
- **Image loading.** Coil with proper `size`/downsampling; the `ArtistTile` covers
  and hero media dominate memory. Preload rail images; cap decode size.
- **Video.** One ExoPlayer per visible autoplay tile is expensive — pool/reuse
  players and release off-screen (mirror the iOS pause-on-`didMoveToWindow`).
- **Startup.** Add a **Baseline Profile**; keep `Application.onCreate` lean
  (analytics/crash init is cheap/dark-until-key). Cold-launch reads a small
  DataStore snapshot (like iOS), not a DB.
- **Realtime.** One channel per open chat; tear down on dispose; don't hold
  channels for background threads.
- **Search debounce + pagination.** Preserve the ~280ms debounce + keyset
  pagination + generation guard, or you'll hammer `search_artists`.

---

## 4. Areas requiring redesign (not 1:1)

- **Liquid Glass.** No Android equivalent for `.glassEffect` / the minimizing glass
  tab bar / the floating `.search`-role search circle. **Decision:** standard
  Material 3 `NavigationBar` (5 client destinations incl. Search as a normal tab or
  a Discover top-bar search icon), translucent surfaces for docks/composers. Accept
  the visual divergence; don't chase a pixel-match.
- **Perforated ticket package cards.** iOS draws dashed tear-lines + notch cutouts.
  Portable via Compose `Canvas` + a custom `Shape` — budget design time.
- **Month calendar.** Rebuild once as a Compose custom layout (drop the iOS
  `MiniMonthCalendar` duplicate — it exists on iOS only for a legacy test).
- **Two `Route` enums split across screen files** → centralize in
  `navigation/Routes.kt` (see §7 issue).
- **Editorial "italic accent word" headline** → `AnnotatedString` builder; verify
  Instrument Serif italic renders correctly on Android.

---

## 5. Security considerations

- **RLS is the entire authorization model** — there is no privileged app path.
  Treat the client as untrusted (it is): every guarded write (`score`, `escrow`,
  terminal booking status, `deleted_at`) is server-rejected. Surface those errors;
  never assume a write succeeded.
- **`messages.body_raw` lockdown.** Un-redacted contact PII is column-revoked;
  `select("*")` 403s. Use explicit column lists everywhere (enforced by the API,
  but bake it into the repo from day 1). This is the **anti-leakage moat** — chat
  contact info stays redacted until a booking is confirmed for the exact
  (client,artist) pair.
- **Client-side redaction** mirrors the DB trigger regexes for display safety —
  keep the two in sync (shared pure module, tested against the same fixtures).
- **Secrets.** The Supabase **anon key is publishable** (safe in `BuildConfig`),
  but keep it out of VCS via `local.properties`/CI. **Never** ship a service-role
  key. Edge-function shared secrets + APNs/FCM keys live server-side only.
- **IAP token binding.** Write the `{app_account_token→user_id}` row **before**
  purchase and pass `obfuscatedAccountId`; the webhook trusts the binding, never a
  raw client token (anti-poisoning). Use a stable per-user random token in
  DataStore, **not** the user's UUID.
- **Deep links / OAuth callback.** Validate the callback scheme/host
  (`in.artistant.app://login-callback`) and let supabase-kt verify the session;
  don't act on arbitrary deep-link params.
- **TLS only.** The app uses HTTPS to Supabase (no custom crypto — matches the iOS
  `ITSAppUsesNonExemptEncryption=false` posture). Certificate pinning is not used
  on iOS; not required, but an option if threat model warrants.
- **PII in telemetry.** PostHog event allowlist + non-PII props; Sentry `BeforeSend`
  scrub — port both. No PII in Timber logs.
- **DPDP compliance.** Data export + account deletion (anonymize) are first-class;
  sign-out must drop analytics identity and wipe local state (the store-reset
  leakage invariant — a new account must never see prior data).

---

## 6. Accessibility considerations

- **Font scaling.** Compose honors `fontScale` (the `relativeTo:` analogue) — don't
  hardcode `sp` off; use the `AppType` ramp so all text scales through the largest
  accessibility sizes. Test at 200%.
- **TalkBack.** Add `contentDescription`/`semantics` to icon buttons (save/share/
  back, score ring, calendar cells, star rating, unread dots). The iOS app uses
  `accessibilityIdentifier`s heavily for its test harness — mirror with test tags
  **and** real semantics.
- **Touch targets.** The tokens already floor at 48dp (button/input) / 44dp (row) —
  keep them; ensure calendar cells and chips meet 48dp.
- **Reduce motion.** The iOS app gates the Discover carousel, lineup background, and
  various springs on Reduce Motion. On Android, check
  `Settings.Global.ANIMATOR_DURATION_SCALE == 0` (or accessibility settings) and
  disable auto-scroll/decorative animation accordingly.
- **Color contrast.** Dark theme with lime/violet accents — verify WCAG AA for text
  on surfaces (esp. `ink3 #6E6E6E` on `bg #0A0A0A` for secondary text, and accent
  text on `brandSoft`). Adjust muted-text tokens if any pair fails.
- **State, not just color.** Availability dots, tier colors, and status use color
  — pair with text/shape so colorblind users aren't excluded (mostly already true;
  audit the calendar dots).

---

## 7. Key decisions (ADRs, condensed)

Each is "decision → why → consequence." The ponytail-flagged ones are deliberate
simplifications with a named upgrade path.

- **D1 Native Compose, not KMP/Flutter.** User requirement. Consequence: two
  codebases share only the backend; parity maintained by these docs + the shared
  wire contract.
- **D2 MVVM + StateFlow, not a literal store port.** Android-idiomatic; maps
  cleanly from `ObservableObject`/`@Published`. Consequence: one `StateFlow<UiState>`
  per screen + event channel for one-shots.
- **D3 Repository interfaces + Fake twins** (copy the iOS seam). Consequence: clean
  test doubles + a future Room cache can slot behind the interface with zero
  ViewModel changes.
- **D4 supabase-kt only, no Retrofit.** One backend surface; the SDK covers it.
  Consequence: fewer deps; accept supabase-kt's API for embeds/realtime.
- **D5 No local database (DataStore + in-memory).** *`ponytail:`* iOS shipped with
  no offline DB and no complaints; Room would be infra for a non-requirement.
  **Upgrade path:** add Room as a read-through cache behind repositories if offline
  becomes real.
- **D6 Single Gradle module, package-by-feature.** *`ponytail:`* faster for a
  small team; boundaries drawn so core/designsystem/data extract to modules
  mechanically when build times justify it.
- **D7 Hilt for DI.** Standard; replaces `.environmentObject` + `.shared`.
- **D8 WorkManager for uploads** (replaces the hand-rolled queue). *`ponytail:`*
  deletes ~300 lines of manual persistence/retry; port only the policy.
- **D9 Thin domain layer** — pure logic only where iOS itself isolated it
  (booking math, score bands, redaction, calendar planner, returning-login route).
  No use-case-per-action ceremony.
- **D10 Payments dormant in v1** — Play Billing is a built-but-inert seam behind a
  flag, mirroring the iOS StoreKit seam. No payment code path runs.
- **D11 Dark-only, portrait, phone-first** — matches iOS; don't build a light
  theme or tablet layout speculatively.
- **D12 Standard Material 3 for glass surfaces** — accept visual divergence from
  iOS-26 Liquid Glass rather than chase an unavailable effect.

---

## 8. iOS architectural issues found → Android improvements

Documented per the brief. These are observations from the analysis, not blockers.

- **I1 — Backend bug shared by both clients.** `create_thread_on_booking_confirm`
  (migration 0015) references a non-existent constraint name
  (`threads_unique_per_pair_booking` is a unique *index*, not a constraint), so
  confirmed-booking thread-creation errors on dev (flagged in the iOS `CLAUDE.md`).
  **Since Android shares the backend, this affects Android too.** Recommend the
  backend team fix the `ON CONFLICT` target before Android's booking→chat path is
  exercised in M4. Not an Android-code issue.
- **I2 — Artist-side thread rows show the wrong name.** The iOS `Thread` model
  lacks a client name in some paths, so artist thread rows can render the artist's
  own name (a known iOS follow-up). **Android improvement:** resolve the counterpart
  name consistently in `MessagesViewModel` (read `threads.client_name` for the
  artist viewer) so both roles always see the other party.
- **I3 — Routes split across screen files.** `Route` lives in `DiscoverView.swift`,
  `ArtistRoute` in `ArtistHomeView.swift`. **Android improvement:** centralize all
  routes in `navigation/Routes.kt` — one place to read the nav graph.
- **I4 — Duplicate calendar component.** iOS keeps `MiniMonthCalendar` only to feed
  a legacy test. **Android improvement:** build one `MonthCalendar`; don't port the
  duplicate.
- **I5 — Client/artist tab APIs diverge** (iOS-26 `Tab` vs legacy `.tabItem`),
  purely historical. **Android improvement:** both role graphs use the same
  `NavigationBar` pattern.
- **I6 — Client + server redaction duplicated.** Defense-in-depth, but a drift risk
  if regexes change on one side. **Android improvement:** keep the client copy in a
  single pure `Redaction.kt`, unit-tested against the same fixtures as the DB
  triggers; note the coupling so a backend regex change updates both.
- **I7 — Denormalized names as an RLS workaround.** `threads.client_name` /
  `reviews.client_name` exist because `users` is self-read-only. This is a sound
  trade; **Android just needs to read the denormalized columns** (never attempt a
  `users` embed for a counterpart — it 403s). Documented so no one "fixes" it into a
  broken embed.
- **I8 — Near-zero offline.** Not a bug — a product choice. Reads are server-direct
  with retry. **Android:** match it (D5); revisit only if offline becomes a
  requirement.

---

## 9. Risk register (top items to front-load)

| Risk | Impact | Mitigation | Owner/when |
|---|---|---|---|
| Apple sign-in (no native SDK) | Blocks a signup path | Spike the web OAuth + deep-link in M0–M1 | Dev A, M1 |
| Backend `send-push` is APNs-only | No Android push | Add FCM path + token table; operator sets service account | Backend, M4 |
| VideoTrimmer codec variance | Wizard/EPK cover fails on some devices | Media3 Transformer + device matrix test + preset fallback | Dev C, M5 |
| CalendarSync port size | Slips M6 | Treat as its own track; reuse the pure planner logic | Dev B, M6 |
| Realtime dedup race | Duplicate/echoed messages | Port the iOS content-match dedup + Mutex gate; test | Dev C, M4 |
| supabase-kt API drift | Rework across repos | Pin version; validate each call in M0 spike | All, M0 |
| I1 backend thread bug | Booking→chat errors | Backend fix before M4 | Backend, pre-M4 |
