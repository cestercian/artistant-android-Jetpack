# FEATURE_CHECKLIST.md — Artistant Android

Every feature broken into implementation tasks, with **complexity**, **dependencies**,
**risks**, and **recommended order**. Use the checkboxes to track parity with iOS.

**Complexity:** S ≈ ½–1 day · M ≈ 1–3 days · L ≈ 3–7 days · XL ≈ >1 week (rough,
one experienced Android dev). **Independent** = can be built in parallel once its
deps are met. See IMPLEMENTATION_ROADMAP.md for the milestone sequencing.

Legend for dependencies: → means "needs". "Foundation" = the M0 items below.

---

## F0 — Foundation & scaffolding  *(blocks everything)* ✅ done (M0)

- [x] **Gradle project + version catalog** — `:app`, KTS, `libs.versions.toml`,
  Compose plugin, KSP. **S.** Deps: none. Risk: low.
- [x] **Product flavors + BuildConfig** — dev/staging/prod, `SUPABASE_URL`/`ANON_KEY`
  from `local.properties`/CI, flags (`REALTIME_ENABLED`, `SUBSCRIPTIONS_ENABLED`),
  product ids. **S.** Risk: secret handling — never commit keys.
- [x] **Hilt setup** — `@HiltAndroidApp`, `SupabaseModule`, `DispatcherModule`,
  `RepositoryModule` (empty binds to fill in). **S.** Deps: Gradle.
- [x] **supabase-kt client** — `createSupabaseClient` + installs; **tier guard**
  (prod host assertion). **M.** Risk: version/API drift vs supabase-swift.
- [x] **AppEnvironment** — `object` reading BuildConfig (port). **S.**
- [x] **Design system** — `ArtistantTheme`, `AppColors` (role-reactive),
  `AppType` (3 `FontFamily`s + ramp), `Dimens`, INR util, `PiiScrub`. **L.**
  Deps: fonts dropped in `res/font/`. Risk: role-accent switching; font metrics.
- [x] **Navigation shell** — `NavHost`, typed routes, `ArtistantRoot` auth-gate,
  Client/Artist tab scaffolds, `DeepLinkRouter`. **M.** Deps: theme.
- [x] **Core components (subset needed early)** — `PrimaryButton`, `Pill`,
  `CardView`, `HRule`, `KVRow`, `HeaderBar`, `EmptyState`, `Skeleton`, `Avatar`,
  `Toast`. **L.** Independent per-component once theme exists.
  *(Some browse components still land with M2 — gallery ships the early set.)*
- [x] **Result/error model** — sealed `AppError` + PostgREST code mapping. **S.**
- [x] **DataStore `AppPreferences`** — Persistence port (`artistant.state.*`,
  `wipeAll`). **S.**

*Foundation shipped in M0. Remaining polish (brand `.ttf`, full component set)
tracked under #15 / M2.*

---

## F1 — Auth & routing  *(gates the app)* ✅ done (M1a)

- [x] **SessionManager** — supabase-kt Auth wrapper; `sessionStatus` Flow;
  `signInGeneration`; expose `currentUser`, `effectiveUserId`. **M.** → Foundation.
- [x] **Google sign-in** — Credential Manager → ID token → supabase. **M.** Risk:
  Credential Manager setup, SHA-1 in console, nonce.
- [x] **Apple sign-in** — Custom Tabs OAuth web flow + nonce. **M.** Risk: **no
  native SDK**; redirect + deep-link plumbing; test on device.
  *(#12 Apple deep-link error handling still open — blocks Apple go-live.)*
- [x] **Email/password** — sign-in/up + confirmationRequired. **S.**
- [x] **Deep-link intent-filter** (`in.artistant.app://login-callback`) + add to
  Supabase dashboard. **S.** Risk: dashboard step is operator-side.
- [x] **Returning-login router** — `routeIn/onboard/degrade` (pure, testable);
  fetch self-profile with retry; hydrate role + artist setup. **M.** Risk: the
  edge cases the iOS `RootView` documents (stranded step, same-UUID re-auth).

*Independent within F1: the three sign-in methods can be built in parallel after
SessionManager.*

---

## F2 — Signup onboarding  *(SHARED)* ✅ done (M1b)

- [x] **SignupViewModel** — step machine (signup/login orders), handle-availability
  debounce (`handle_is_available`, 350ms), returning-user hydration banners. **M.**
- [x] **Welcome / Role / Auth / EmailAuth / Profile / NotifPermission / Done /
  Legal** screens. **L** total. Deps: F1, components, `ScoreRing`. Risk: Role
  screen's tap-commit + appear-debounce; Auth screen's animated lineup background
  (motion-gated).
- [x] **Notif permission** wiring → `POST_NOTIFICATIONS` + FCM register. **S.** → F9.
  *(Permission UI shipped; FCM token register still M4.)*

---

## F3 — Artist wizard  *(ARTIST)*

- [ ] **WizardViewModel** — `flowOrder`, per-step validation, pending-media handoff.
  **L** (the 585-line store port). → Foundation, `WizardMediaCache`, `UploadQueue`.
- [ ] **11 step screens** (identity/location/pricing/tech/availability/cover/
  socials/bio/samples/preview/done). **XL** total. Deps: F8 media (cover/samples
  steps), F0 chips (`FlowRow`). Risk: the cover step (camera + trim + gallery) and
  preview/publish (multi-write + upload enqueue).
- [ ] **Publish flow** — upsert artist, parallel packages/tech write, flip
  published, enqueue media. **M.** → F8, `replace_*` RPCs.

*Independent: steps with no media (identity/location/pricing/tech/availability/
socials/bio) can be built before F8 lands.*

---

## F4 — Discover  *(CLIENT)*

- [ ] **DiscoverViewModel** — 6 rails via 4 concurrent `search_artists`. **M.**
  → SearchRepository, ArtistsRepository cache.
- [ ] **DiscoverScreen** — hero carousel (6s auto-rotate, reduce-motion) + `LazyRow`
  rails of `ArtistTile`. **M.** Deps: `ArtistTile`, `MediaContainer`. Risk:
  carousel timer lifecycle; image loading perf.

## F5 — Search + filters  *(CLIENT)*

- [ ] **SearchViewModel** — filters, debounced query (~280ms), keyset/offset
  pagination + generation guard, facets, recents (DataStore). **L.** → SearchRepo.
- [ ] **SearchScreen** — search bar, empty chips, 2-col grid, infinite scroll, sort
  menu, states. **M.**
- [ ] **SearchFilterSheet** — city/budget/score/category/occasion. **M.**

*F4 and F5 share `SearchRepository` (independent of each other after it exists).*

## F6 — Artist profile  *(CLIENT)*

- [ ] **ArtistProfileViewModel + ArtistRouteLoader** — fetch-on-miss `ensureFull`,
  4 parallel loads. **M.** → ArtistsRepository, media/samples/reviews/score repos.
- [ ] **ArtistProfileScreen** — hero (`MediaContainer`), about, **perforated ticket
  package cards** (`HorizontalPager` + `Canvas` tear line), reviews, disclosure
  rows (`SpotifyEmbed`, tech, social), glass dock (Message/Book). **L.** Risk:
  ticket-card custom drawing; hero sizing.

---

## F7 — Booking funnel + gig requests

- [x] **BookingViewModel + BookingScreen** — draft, date chips, time grid,
  venue/guests, summary. **M.** → BookingStore draft, ArtistsRepository.
- [x] **CheckoutScreen** — confirm → payment seam → create → Confirmed;
  quota gate → Paywall deferred. **M.** → F16 (billing seam, dormant).
- [x] **ConfirmedScreen** — celebration + actions (view booking / discover). **S.**
- [x] **RequestQuoteScreen** — custom quote → `RequestsRepository.create`. **S.**
- [ ] **GigRequestDetailScreen** (ARTIST) — accept/decline/counter, clash card. **M.**
  → CalendarSync clash read.
- [x] **Booking math** (`BookingMath.kt`) — 5%/18% (pure, unit-tested). **S.**

## F8 — Media pipeline  *(cross-cutting; unblocks wizard + EPK)*

- [ ] **Photo Picker + CameraX** integration. **M.** Risk: CameraX config.
- [ ] **SAF audio picker** (`OpenDocument`). **S.**
- [ ] **VideoTrimmer** (Media3 Transformer, ≤10s, first-frame). **L.** Risk:
  transcode reliability across devices/codecs — the single biggest media risk.
- [ ] **WizardMediaCache** (cacheDir staging, bitmap normalize, duration probe). **M.**
- [ ] **UploadQueue → WorkManager** — per-task workers, backoff, foreground info,
  foreign-user purge, publish gate. **L.** → SessionManager.
- [ ] **ArtistMedia/Samples repos** — storage upload + insert + rollback + retry. **M.**
- [ ] **AutoplayVideo (ExoPlayer)**, **SamplePlayer (ExoPlayer)**, **SpotifyEmbed
  (WebView)**, **MediaContainer**. **M** total.

---

## F9 — Push notifications  *(needs a backend change)*

- [ ] **FirebaseMessagingService** — `onNewToken` (upsert token), `onMessageReceived`
  (foreground), tap `PendingIntent` → `DeepLinkRouter` (map `artistant_*` keys). **M.**
- [ ] **FCM token table + `send-push` FCM path** (backend). **M.** Risk: **server
  work**; FCM HTTP v1 + service-account auth; operator secrets. Blocks real
  delivery (client can be built ahead).
- [ ] **POST_NOTIFICATIONS** permission (API 33+). **S.**

## F10 — Messages + chat  *(SHARED)*

- [x] **MessagesViewModel** — thread list, verbatim preview, real pair-scoped
  `findOrCreateThread`. **partial:** artist counterpart names + push deep links deferred.
- [x] **ChatViewModel** — paged load, optimistic send + Realtime INSERT reconcile,
  mark-read + best-effort 0072 receipts, tap-to-retry. **M.**
  *(report/details sheet still deferred.)*
- [x] **ChatScreen** — bubbles, system rows/action route, Airbnb trust banner + composer.
  **partial:** reverse auto-scroll, report/details, retry chip, and glass polish deferred.
- [x] ~~**Redaction** (`Redaction.kt`)~~ — **obsolete.** Scrapped Jul 2026 (mig
  `0071`); deleted from Android. Do not rebuild.

## F11 — Bookings list + detail  *(SHARED)*

- [x] **BookingsViewModel + BookingsScreen** (CLIENT) — `MonthCalendar` header + schedule;
  `pendingBookingDetail` deep link deferred. **M.** → `MonthCalendar` component.
- [ ] **ArtistGigsScreen** (ARTIST) — `MonthCalendar` of gigs. **S.**
- [x] **BookingDetailScreen** (SHARED) — timeline, KV, actions (cancel/accept/decline);
  message/review/calendar deferred. **M.**
- [ ] **MonthCalendar / MiniMonthCalendar / DateScroller** components. **partial** —
  `MonthCalendarHeader` + date chips shipped; full grid deferred. **L.**

---

## F12 — Artist home + EPK  *(ARTIST)*

- [ ] **ArtistHomeViewModel + Screen** — earnings `Sparkline`, bookability card,
  14-day availability strip, requests, upcoming, upload banner, subscribe banner.
  **L** (~1300-line screen). → Score/Bookings/Artists repos, RequestStore,
  CalendarSync busy-days, EntitlementStore.
- [ ] **EpkViewModel + EpkScreen** — cover, photos grid, samples, socials, bio,
  pricing (debounced replaceAll), tech, links CRUD, share-link. **L.** → F8.
- [ ] **ManageAvailabilityScreen** — days/times chips + save. **S.**

## F13 — Bookability score  *(mostly ARTIST)*

- [ ] **ScoreExplainerScreen** — hero ring, tiers, weighted metric bars. **M.**
- [ ] **ScoreBreakdownSheet** (CLIENT, on ArtistProfile) — metrics + reply-speed
  inversion. **S.**
- [ ] **ScoreHistorySheet** — `Sparkline` + delta. **S.**
- [ ] **ScoreRing + Sparkline** components. **M.** → Canvas.
- [ ] **ScoreBands** (`ScoreBands.kt`) — tier bands + <5-gig rule (pure, tested). **S.**

## F14 — Reviews  *(CLIENT write)*

- [ ] **ReviewSheet** — stars + text; `ReviewsRepository.insert` (gate: completed
  booking; `23505`→alreadyReviewed). **S.**

---

## F15 — Profile, settings, DPDP, calendar sync

- [x] **ProfileScreen** — identity header + settings rows (sign out, delete, export, privacy/help). **M.** Stats/saved carousel deferred.
- [x] **Sign out** — `SessionManager.signOut` + prefs wipe; RootViewModel routes to auth. **S.**
- [x] **Data export** — `data-export` EF → share sheet (inline) or browser (signed URL). **S.**
- [x] **Delete account** — confirm dialog → `delete-account` EF → signOut on success. **S.**
- [ ] **CalendarSyncService** — CalendarContract mirror (create/update/delete
  events + reminders), `event.url` marker, pure **SyncPlanner** (desired-vs-persisted
  diff), clash/busy reads. **L.** Risk: **big EventKit→Provider port**;
  `READ/WRITE_CALENDAR` perms; can't create a calendar under a Google account (offer
  existing ones, like iOS). → `AddToCalendar` intent (permission-free path).
- [ ] **ManageAvailabilityScreen** — full open-date editor. **M.** Stub row on Profile only.

---

## F16 — Payments / subscriptions  *(DORMANT in v1 — build the seam only)*

- [x] **Play Billing seam** — `EntitlementStore` stub (always not-subscribed when flag off). **M.**
- [x] **PaywallScreen** — role-aware pitch, price card, subscribe/restore (inert). **M.**
- [ ] **Google Play RTDN backend** — deferred to IAP go-live (see API_MAPPING §7).

---

## F17 — Observability & permissions  *(cross-cutting)*

- [ ] **Analytics (PostHog)** — event allowlist, non-PII props, identify/reset. **S.**
  Dark until key.
- [ ] **Crash (Sentry)** — init + `BeforeSend` PII scrub (shared regex). **S.**
  Dark until DSN.
- [ ] **PermissionsController** — camera/mic/location/notifications/calendar +
  photo/audio (pickerless). **M.**

---

## F18 — Testing  *(continuous)*

- [ ] **Unit** (JUnit + MockK + Turbine): BookingMath, ScoreBands,
  SyncPlanner, ReturningLoginRoute, ISO8601, INR, store-reset leakage. **M**, grows.
- [ ] **Repository tests** against `Fake*` impls. **M.**
- [ ] **ViewModel tests** — assert `StateFlow` emissions. **M.**
- [ ] **Compose UI tests** — the XCUITest-parity net; `Fake*` via a test Hilt module
  + launch args (the `-uitest-*` analogue). **L**, grows.
- [ ] **Screenshot tests** (optional, Roborazzi) for design-system components. **M.**

---

## Independent-work map (what parallelizes)

Once **F0 Foundation** is done, these tracks run in parallel with minimal contention:

- **Track A (auth/onboarding):** F1 → F2 → F3(non-media steps).
- **Track B (browse):** F4 + F5 + F6 (share only `SearchRepository`/`ArtistsRepository`).
- **Track C (media):** F8 (unblocks F3 cover/samples, F12 EPK) — start early, it's
  the long pole.
- **Track D (comms):** F9 (push) + F10 (chat/realtime) — F9 client can precede the
  backend FCM change.
- **Track E (bookings/score):** F7 + F11 + F13 + F14 (share calendar components).
- **Track F (artist surfaces):** F12 after F8 + F13.
- **Track G (settings/platform):** F15 (calendar sync is independent and sizeable),
  F16 (dormant), F17 (cross-cutting, drop in anytime).

**Highest-risk / start-early items:** F8 VideoTrimmer (device codec variance),
F15 CalendarSync (largest platform port), F10 ChatViewModel (realtime race),
F1 Apple sign-in (no native SDK). Front-load these to de-risk.
