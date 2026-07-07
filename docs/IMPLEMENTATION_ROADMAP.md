# IMPLEMENTATION_ROADMAP.md — Artistant Android

Milestones from empty repo to **feature parity with iOS**. Each milestone has a
goal, the feature tasks it pulls in (F-numbers from FEATURE_CHECKLIST.md), an
**exit criterion** (how you know it's done), and a rough duration for **one
experienced Android engineer** (parallelize across a team by the independent-work
map at the end).

**Sequencing principle:** get a **thin vertical slice demoable early** (sign in →
browse → book → chat), then fill breadth. The matchmaker product *is* browse +
chat, so those land before the artist-authoring surfaces.

Rough total solo: **~14–18 weeks** to full parity. A 2–3 dev team lands the core
(M0–M4) in ~6–7 weeks by running the tracks in parallel.

---

## M0 — Foundation *(≈2 weeks)*
**Goal:** the app compiles, themes correctly, navigates between empty screens, and
can reach Supabase.
**Pulls in:** F0 (all), plus a bare `SessionManager` stub.
**Tasks:** Gradle + version catalog · flavors + BuildConfig + tier guard ·
supabase-kt client · Hilt · **design system** (theme, 3 font families, tokens,
INR) · navigation shell + auth-gate + tab scaffolds · core components subset ·
DataStore prefs · error model.
**Exit:** dark-themed app launches to a placeholder tabbed shell in both role
graphs; a smoke call to `search_facets` succeeds against dev; design-system
components render in a `@Preview` gallery.

## M1 — Authentication & onboarding *(≈2 weeks)*
**Goal:** a real user can sign in (all three methods) and land in the correct
role's tabs; a returning user routes straight in.
**Pulls in:** F1 (auth + routing), F2 (signup flow), F17 permissions (notifications).
**Tasks:** SessionManager (sessionStatus, signInGeneration) · Google (Credential
Manager) · Apple (Custom Tabs OAuth) · email/password · deep-link intent-filter ·
returning-login router (routeIn/onboard/degrade) · 8 signup screens · handle-
availability check.
**Exit:** sign in with Google + Apple + email on a **real device**; new user
completes profile and reaches tabs; kill/relaunch routes a returning user straight
in; sign-out wipes local state.
**Risk gate:** Apple sign-in has no native SDK — validate the web flow + deep-link
round-trip here, don't defer.

## M2 — Browse (the "home feed") *(≈2 weeks)*
**Goal:** the read-only marketplace works — Discover rails, Search, and a full
Artist profile.
**Pulls in:** F4 Discover, F5 Search + filters, F6 Artist profile, plus the
**media *display*** slice of F8 (Coil images, `MediaContainer`, `AutoplayVideo`,
`SpotifyEmbed`, `ArtistTile`).
**Tasks:** ArtistsRepository (5-table fan-out + id-cache) · SearchRepository
(`search_artists`/`search_facets`) · DiscoverViewModel (6 rails) · SearchViewModel
(debounce + pagination) · ArtistProfile (hero, ticket cards, reviews, dock).
**Exit:** browse real dev artists, search with filters + pagination, open a profile
with cover media/samples/reviews/score, tap Message/Book (stubbed nav).

## M3 — Booking funnel & calendar surfaces *(≈2 weeks)*
**Goal:** a client books end-to-end (mock payment) and sees it on a calendar; can
review a completed booking.
**Pulls in:** F7 (booking/checkout/confirmed/request-quote), F11 (bookings list +
detail + calendar components), F14 reviews, F13 score sheets (breakdown on profile),
booking math.
**Tasks:** BookingViewModel + DateScroller/time grid · CheckoutScreen (mock
payment seam) · ConfirmedScreen · BookingsScreen + `MonthCalendar` ·
BookingDetail · ReviewSheet.
**Exit:** create a booking (respecting no-overlap/self-booking guards), see it in
Bookings calendar + detail, leave a review on a completed one. `MonthCalendar`
+ `DateScroller` shipped.

## M4 — Messaging & push *(≈2 weeks)*
**Goal:** the matchmaker core — realtime chat with redaction, plus notifications.
**Pulls in:** F10 (messages + chat + realtime + redaction), F9 (FCM client +
backend send-push change), gig-request create (F7) end-to-end.
**Tasks:** MessagesViewModel · ChatViewModel (optimistic send + realtime reconcile
+ redaction) · ChatScreen · **FCM service + backend FCM path + token table** ·
deep-link routing.
**Exit:** two accounts chat in realtime; contact info is redacted pre-confirmation
and lifts after; a push deep-links to the right thread/request on tap.
**Risk gate:** the realtime-vs-optimistic-send dedup race and the backend FCM
change both live here.

*→ End of M4 = a usable client-side matchmaker app (browse, book, chat, get
notified). Good internal-demo / alpha checkpoint.*

## M5 — Artist authoring *(≈3 weeks)*
**Goal:** artist-side parity — onboarding wizard, dashboard, EPK editor, gig-request
handling.
**Pulls in:** F3 wizard, F12 artist home + EPK + manage-availability, F13 score
explainer/history, gig-request detail (F7), plus the **media *capture/upload***
slice of F8 (CameraX, VideoTrimmer, WizardMediaCache, UploadQueue/WorkManager,
ArtistMedia/Samples repos).
**Tasks:** WizardViewModel + 11 steps · publish flow (multi-write + upload enqueue)
· ArtistHome (sparkline, bookability card, availability strip, requests) · EPK
editor · gig-request accept/decline/counter with clash card · score screens.
**Exit:** a new artist completes the wizard (with cover photo/video + audio samples
uploaded), goes live, appears in Discover/Search, receives + accepts a gig request,
edits their EPK.
**Risk gate:** VideoTrimmer (Media3 Transformer) device-codec reliability.

## M6 — Platform, settings & DPDP *(≈1.5 weeks)*
**Goal:** account management + calendar integration + observability.
**Pulls in:** F15 (profile/settings, sign-out, data-export, delete-account,
**CalendarSyncService**), F17 (analytics + crash).
**Tasks:** ProfileScreen + settings rows · DataExport (share sheet) · Delete
account · **CalendarSync** (CalendarContract mirror + clash/busy + AddToCalendar
intent) · PostHog + Sentry wrappers (dark-until-key, PII scrub).
**Exit:** export + delete account work end-to-end; enabling calendar sync mirrors
confirmed gigs to the device calendar and reflects reschedule/cancel; analytics
allowlist + crash scrub verified with keys set locally.
**Risk gate:** CalendarSync is the largest single platform port.

## M7 — Payments seam & polish *(≈1.5 weeks)*
**Goal:** the dormant billing seam + visual/interaction polish.
**Pulls in:** F16 (Play Billing seam + Paywall, inert), animation/haptics pass,
Liquid-Glass approximation, accessibility pass, empty/error/skeleton states audit.
**Tasks:** SubscriptionService mock seam · EntitlementStore (obfuscatedAccountId
binding) · PaywallScreen (inert) · translucent-surface glass approximation ·
haptics · TalkBack + content descriptions + touch targets.
**Exit:** paywall renders (flag off = no behavior change); animations/haptics match
the iOS feel where the platform allows; a11y audit passes (see RISKS §accessibility).

## M8 — Hardening & release *(≈2 weeks)*
**Goal:** test net green, performant, shippable to Play.
**Pulls in:** F18 (full testing), performance, release prep.
**Tasks:** unit + repository + ViewModel + Compose-UI test suites (XCUITest-parity)
· baseline profile / startup + scroll perf · Play Console app, signing (Play App
Signing), per-flavor `google-services.json`, ProGuard/R8 rules · internal-testing
track upload · backend activation checklist (FCM service account, secrets, cron —
operator).
**Exit:** CI green (unit + UI), no P0 bugs, internal-testing build on Play, backend
push/score live on prod, feature-parity checklist (FEATURE_CHECKLIST.md) fully
ticked.

---

## Critical path & parallelization

**Critical path (must be sequential):** M0 Foundation → M1 SessionManager/auth →
(M2 browse ∥ M4 chat need repositories) → M5 needs M0 media + M3 booking model.

**Run in parallel across a team** (after M0):
- **Dev A:** M1 auth/onboarding → M5 wizard.
- **Dev B:** M2 browse → M3 booking → M6 calendar sync.
- **Dev C:** F8 media pipeline (start day 1 of M2, the long pole) → M4 chat/push →
  M12 artist home/EPK.

**Front-load the four risk gates** regardless of milestone order: Apple sign-in
(M1), FCM backend change (M4), VideoTrimmer (M5), CalendarSync (M6). Prototype each
in a spike during M0–M1 so no milestone discovers a blocker late.

**Operator/back-end prerequisites** (not client work, but gate go-live — schedule
early): add the Android redirect URL to Supabase; create the Firebase project +
`google-services.json` + FCM service account; add the FCM path to `send-push`;
Play Console app + signing; (later) Play billing products + RTDN. See
API_MAPPING.md §7 and RISKS_AND_DECISIONS.md.
