# Artistant Android redesign — "Artistant iOS Light" (Sep 2026)

The product owner replaced the dark, dual-accent design with a **light, single-accent**
design authored in Claude Design: *Artistant iOS Light* — **138 screens in 11 sections**.
This document is the plan for re-implementing the Android app against it, screen by
screen. It supersedes the "UI direction is locked / dark-only" rule in CLAUDE.md.

## 1. Source of truth

Extracted design, outside the repo (persistent, not in git):

```
~/.claude/projects/-Users-yashaf-AndroidStudioProjects-artistant-android/design-2026-09/
  screens/NNN-slug.html      exact markup of one screen (inline CSS, 390×844 phone frame)
  shots/NNN-slug.png         headless-Chrome render of that screen, 390×844
  screen-index.json          [{label, section, title, note}] — the designer's intent note per screen
  Artistant-iOS-Light.dc.html the whole canvas; source-bundle.html the original export
  fonts/*.ttf                Plus Jakarta Sans (variable + italic), JetBrains Mono (variable)
```

`NNN` is the screen number in the design ("04 Artist profile" → `004-artist-profile.html`).
The **note** on each screen is a design decision — read it before the markup, it says *why*
the screen looks the way it does (e.g. "Failure ≠ empty — stated twice").

Behaviour (ViewModels, repositories, RLS rules, booking guards, money math) is unchanged
unless a screen genuinely needs new UI state. Backend is the shared Supabase project;
**no schema changes.**

## 2. Design language (measured from the markup)

**Palette** — one accent for both roles; role no longer changes colour.

| Token (new) | Hex | Used for |
|---|---|---|
| `page` | `#fafaf6` | canvas / page background |
| `surface` | `#ffffff` | screen background inside the phone, cards on page |
| `surface2` | `#f1f2ec` | pills, icon circles, unselected chips, input fields |
| `surface3` | `#f3f4ef` / `#f6f7f3` | search bar, grouped list backgrounds |
| `placeholder` | `#ebece4` | image slots before load |
| `hairline` | `#e6e8df` | dividers, card strokes (`#eceee7` softer) |
| `lineStrong` | `#c6c9be` / `#c9ccc1` | separators inside dark chrome, dots |
| `ink` | `#14150f` | primary text, icons |
| `ink2` | `#5c5f55` | secondary text, unselected chip label |
| `ink3` | `#6d7168` | body copy on light |
| `ink4` | `#8a8d82` | captions, meta ("Bengaluru · Sat 12 Oct") |
| `hint` | `#7e8274` | placeholder text, search icon |
| `accent` | `#d6f84b` | the one signal: primary CTA, selected chip, badges |
| `onAccent` | `#0b0b0c` | text on accent |
| `accentInk` | `#5e7307` | accent used *as text/icon* on light ("See all", stars); `#3f4d05` deeper |
| `dark` | `#16171a` | phone bezel, dark surfaces (splash, quote cards) |
| `darkest` | `#0f100c` | splash, notch |
| `onDark` / `onDarkSoft` | `#ffffff` / `#c3c7b8` | text on dark surfaces / media gradients |
| `danger` | `#a4402c` | destructive, failed |
| `dangerSoft` / `dangerLine` | `#f9efec` / `#f0e2de` | danger banners |
| `warm` | `#8a6a2a` | warnings, pending |
| `warmSoft` / `warmLine` | `#f7f3ea` / `#f2ead9` | warm banners |
| media scrim | `rgba(11,11,12,.95→0)` | gradient under text on photos |

**Type** — Plus Jakarta Sans everywhere; JetBrains Mono for eyebrow labels and numerals.

| Style | Spec |
|---|---|
| screenTitle | 26 / 700 / letter-spacing −0.03em |
| displayHero (onboarding) | 30 / 700 / −0.028em |
| sectionTitle | 17 / 700 / −0.02em |
| cardTitle | 18.5 / 700 / −0.02em (on media) |
| rowTitle | 14.5 / 600 |
| body | 15 / 400 / line-height 1.6 |
| subtitle / meta | 13.5 / 400, `ink4` |
| caption | 12.5 / 400, `ink4` |
| chip | 13.5 / 500 (700 when selected) |
| cta | 16.5 / 700 |
| badge | 11.5 / 700 / +0.02em |
| monoLabel | JetBrains Mono 11 / 500 / +0.12em / uppercase |
| monoPill | JetBrains Mono 11.5 / 600 / +0.06em |
| monoNumber | JetBrains Mono, size per context (18 for the logo "A") |

**Geometry**

| Thing | Value |
|---|---|
| page horizontal padding | 20 |
| CTA | height 54, radius 16, accent, `onAccent` text 16.5/700 |
| secondary button | same height, `surface2` |
| icon circle (header actions) | 42, `surface2` |
| search bar | height 48, radius 15, `surface3`, hint text |
| chip | padding 9×16, radius 999 |
| hero card | radius 24, height 262 |
| tile | radius 18 (image), name 14.5/600, meta 12.5 `ink4` |
| card | radius 16–18, `surface` on `page` or `surface2` on `surface`, hairline stroke |
| list row | 56–64 high, hairline separators, chevron `ink4` |
| tab bar | height 88 (incl. home-indicator zone), light, hairline top, 4 items |
| header | 56 tall: title 26 + subtitle, or centred 17/700 with a 42 back circle |
| phone frame / status bar | design-only chrome — do **not** draw it in the app |

**Principles that show up on every screen (from the notes):** loading, empty and failed
are three different screens and say which one they are; "narrated, not a spinner";
one accent per screen; every empty state carries an action; money is all-in and in ₹;
copy states the fact, not "success".

## 3. Phases

### P1 — Foundation (one PR, lands first)
- Fonts: drop the three TTFs into `res/font/`, `SansFamily` = Plus Jakarta Sans, `MonoFamily`
  = JetBrains Mono. The serif is gone; `SerifFamily` aliases the sans so nothing breaks.
- `AppColors`: new light values on the existing names (mapping in §4) **plus** the new
  tokens above. `AppRole` no longer changes colour (`withRole` becomes identity; keep the
  enum — navigation still depends on it).
- `ArtistantTheme`: `lightColorScheme`, light status bar icons, `page` window background.
- Type ramp re-cut to §2 (keep old names as aliases so screens compile; add the new names).
- Splash: dark (`darkest`) per screen 01 — "the one dark room" — then the app is light.
- Launcher icon: black rounded square, lime "A" in JetBrains Mono (adaptive icon, vector).
- Component library v2 in `designsystem/component/`: `PrimaryButton`, `SecondaryButton`,
  `IconCircle`, `SearchBar`, `Chip`, `SectionHeader`, `Tile`, `HeroCard`, `ListRow`,
  `Banner` (info / warm / danger), `StatusPill`, `EmptyState`, `Skeleton`, `Toast`,
  `LightTabBar`, `ScreenHeader` (title+subtitle) and `BackHeader` (centred), `Sheet`
  scaffold, `OtpField`, `AppTextField`.
- Retire dark-only chrome: `AmbientRoleWash`, `BottomDarkenScrim` on light surfaces,
  `ArtistGradient` stays only under photos.
- **Gate:** tree green; every existing screen renders on the light palette (rough is fine —
  the sections fix layout).

### P2 — Sections (parallel PRs, one per section; screens listed in `screen-index.json`)

| # | Section | Screens | Android surface |
|---|---|---|---|
| GS | Getting started | 15 | `feature/signup/**` |
| DS | Discover & search | 12 | `feature/discover`, `feature/search`, `feature/saved` |
| AP | The artist profile | 14 | `feature/artist`, `feature/score` |
| BC | Book & confirm | 8 | `feature/booking` (Request quote, Booking, Checkout, Confirmed) |
| MS | Messaging & safety | 12 | `feature/messages`, blocked accounts, support |
| BN | The booking & the night | 14 | `feature/bookings`, `BookingDetail`, calendar, review |
| WZ | Artist setup wizard | 12 | `feature/wizard` |
| PK | Press kit & media | 10 | `feature/epk` |
| AS | Artist studio | 13 | `feature/artisthome`, `feature/gigs`, `feature/availability` |
| AC | Account & settings | 20 | `feature/profile`, `feature/paywall`, account/export/delete |
| SH | System & housekeeping | 8 | app-level: toast, activity, update/outage gates, what's new, rate, help, feedback |

### P3 — Integration
Routes for every new screen, `SCREEN_INVENTORY.md` / `PARITY_CHECKLIST.md` / `CLAUDE.md`
updated, emulator walk of every section, release APK.

## 4. Old → new token mapping (P1)

| Old `AppColors` | New value |
|---|---|
| `bg` | `#fafaf6` |
| `bgElev` | `#ffffff` |
| `bgCard` | `#f6f7f3` |
| `bgSoft` | `#f1f2ec` |
| `line` | `#e6e8df` |
| `lineSoft` | `#eceee7` |
| `ink` / `ink2` / `ink3` / `ink4` | `#14150f` / `#5c5f55` / `#6d7168` / `#8a8d82` |
| `hot` | `#a4402c` |
| `warm` | `#8a6a2a` |
| `good` | `#5e7307` |
| `brand` / `brandInk` | `#d6f84b` / `#0b0b0c` |
| `brandSoft` | `#f5fbda` (accent at ~12% on white) |
| `accent` / `accentInk` / `accentSoft` | same as `brand` / `#5e7307` / `brandSoft` (violet retired) |
| `glass*`, `chipOnMedia*`, `inkOnMedia*` | unchanged — they sit on photos, which stay dark |

## 5. Rules for section agents

1. **Read first:** every screen of your section — note, then PNG, then HTML. Implement all
   of them, including loading / empty / failed / blocked variants. A state the app cannot
   reach today still gets its UI, driven by the ViewModel state that would produce it.
2. **Data honesty.** Real data → a local preference (DataStore) when the screen is a
   setting → the design's own "unavailable" state. Never fabricate server data, never show
   a fake number. If a screen needs data the backend does not have, build the UI, wire what
   exists, and list the gap in the PR body.
3. **Tokens only** (`AppTheme.colors / type / dimens`). A shared component used by two or
   more of your screens goes in `designsystem/component/`; one-offs stay in your feature
   package. Do not draw the phone bezel, notch or fake status bar.
4. **Navigation:** add routes in `navigation/Routes.kt` and destinations in the NavHost
   inside your section's block. Every new screen is reachable from where the design says.
5. **Keep the seam.** ViewModels never touch supabase-kt; repository interface + Supabase
   impl + Fake twin. Add UI state to ViewModels, not new network paths, unless the section
   truly needs one (then: explicit columns, lowercase UUIDs, RLS-aware).
6. **Gate before commit:** `assembleDebug` + `testDebugUnitTest` + `lintDevDebug`. Unit-test
   pure logic you add (formatters, state reducers). Say in the PR what was not device-walked.
7. Material 3 is the substrate, not the look: use M3 components where they carry behaviour
   (sheets, text fields, switches, snackbar host) and restyle them with the tokens; never
   ship an M3 default colour.
