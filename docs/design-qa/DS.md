<!-- Appendix of docs/DESIGN_QA_2026-09.md — raw section report, verbatim from the section auditor; line numbers as of main@02ebeb2. -->

# DS — Discover & search (screens 02, 59, 14, 03, 57, 58, 15, 104, 53, 32, 112)

Source root: `app/src/main/java/in/artistant/app/`. Line numbers confirmed with `nl -ba` / `grep -n` on 2026-09-05.

## Coverage
| Screen | File(s) | Reviewed | Findings |
| 02 Discover | feature/discover/DiscoverScreen.kt, DiscoverViewModel.kt, DiscoverHeroLogic.kt, component/HeroCard.kt, Tile.kt | yes | F-DS-01, F-DS-05, F-DS-06, F-DS-14, F-DS-17, F-DS-18, F-DS-19, F-DS-23, F-DS-24 |
| 59 Discover loading | feature/discover/DiscoverScreen.kt:143-156 | yes | none (skeleton, header kept live, 2-up geometry asserted in comment) |
| 14 Search browse | feature/search/SearchScreen.kt:365-548 | yes | F-DS-02, F-DS-07, F-DS-17, F-DS-20, F-DS-22 |
| 03 Search results | feature/search/SearchScreen.kt:551-778 | yes | F-DS-01, F-DS-04, F-DS-05, F-DS-06, F-DS-10, F-DS-15, F-DS-16, F-DS-25 |
| 57 Search empty | feature/search/SearchScreen.kt:202-223, SearchLabels.kt:252-288 | yes | F-DS-19 |
| 58 Search failed | feature/search/SearchScreen.kt:175-196 | yes | F-DS-08, F-DS-18, F-DS-19 |
| 15 Filters sheet | feature/search/SearchFilterSheet.kt | yes | F-DS-03, F-DS-20, F-DS-21, F-DS-22 |
| 104 Filters, filters on | feature/search/SearchFilterSheet.kt:136-149, SearchScreen.kt:791-840 | yes | F-DS-03, F-DS-10, F-DS-20 |
| 53 Compare by service | feature/search/CompareByServiceSheet.kt | yes | F-DS-18, F-DS-20 |
| 32 Artist list | feature/profile/ArtistListScreen.kt, ArtistListKind.kt | yes | F-DS-01, F-DS-04, F-DS-05, F-DS-06, F-DS-07, F-DS-09, F-DS-11, F-DS-12, F-DS-16, F-DS-17, F-DS-19 |
| 112 Artist list, empty | feature/profile/ArtistListScreen.kt:165-184, ArtistListKind.kt:48-73 | yes | F-DS-19 |
| (component) | designsystem/component/ArtistTile.kt | yes | F-DS-13 |

Checked and NOT flagged: Search's 40dp filter circle (`iconCircleSm`, Dimens.kt:228-229 explains "where the title band is tighter"); `radii.card` = 20 on the result card (Dimens.kt:59-64 measured reason); `containerColor = Transparent` + `dragHandle = null` on both sheets (SheetScaffold draws the chrome, comment at SearchFilterSheet.kt:114-116); the doubled failure statement on 58 (design note "stated twice"); `skipPartiallyExpanded = true` on both sheets; SavedStore.kt has no user-facing strings.

## Findings

### F-DS-01 — Retired dark-palette gradient is the cover floor on every DS surface
- Screens: 02, 03, 32 (and 14 suggestion thumbs)
- Where: `feature/discover/DiscoverScreen.kt:408-423`; `feature/search/SearchScreen.kt:862-877`; `feature/profile/ArtistListScreen.kt:388-409`; palette `designsystem/theme/ArtistGradient.kt:25-30`; dead `placeholder` paint under it at `SearchScreen.kt:679`, `ArtistListScreen.kt:240`, `designsystem/component/Tile.kt:103`, `HeroCard.kt:85`
- Category: token
- Severity: P1
- Rule: (a) §2 `placeholder #ebece4` = "image slots before load"; §4 "violet retired"; §3 P1 "`ArtistGradient` stays only under photos"
- What: Every cover in the section is painted `Brush.verticalGradient(artist.gradient)` first and the photo over it, so an artist with no `coverUrl` — and every artist during load — shows a pink/violet/cyan-to-`#0F1014` block from the dark design (`#7C5CFF` is the retired violet accent, in 4 of the 6 palette rows). The light `placeholder` fill that Tile/HeroCard/ResultCard/ArtistListRow paint underneath is covered 100% and never visible. Three private copies of the same 12-line composable do this; `MediaSlot` (Tile.kt:134-145) already exists as the `placeholder`-filled slot.
- Evidence:
  ```kotlin
  // DiscoverScreen.kt:409-412
  Box(
      Modifier
          .fillMaxSize()
          .background(Brush.verticalGradient(artist.gradient)),
  // ArtistGradient.kt:25
  listOf(Color(0xFFFF6B9D), Color(0xFF7C5CFF), Color(0xFF0F1014)),
  ```
- Fix: One shared cover composable (or `MediaSlot`) that lets the `placeholder` fill show until the photo lands and keeps the gradient, if at all, as the media scrim under text only; delete the three private `ArtistCover`/`ArtistThumb` copies.

### F-DS-02 — Search field never requests focus on entry
- Screens: 14
- Where: `feature/search/SearchScreen.kt:106`, `:323`; no `requestFocus` anywhere in `feature/search/`, `feature/discover/` or `designsystem/component/SearchBar.kt`
- Category: slow
- Severity: P2
- Rule: (c) fact — rubric §5 "a screen whose only field does not `requestFocus()` on entry (… Search …)"
- What: Discover's `SearchBarButton` is "a search bar that navigates rather than types" (DiscoverScreen.kt:58-59, :201-207); the user lands on Search and must tap the field a second time to type. A `FocusRequester` is created and attached to the field but never invoked — dead object, missing behaviour.
- Evidence:
  ```kotlin
  // SearchScreen.kt:106
  val focusRequester = remember { FocusRequester() }
  // SearchScreen.kt:321-324
  modifier = Modifier
      .weight(1f)
      .focusRequester(focusRequester)
      .onFocusChanged { if (it.isFocused) onFocused() }
  ```
- Fix: `LaunchedEffect(Unit) { if (!state.hasActiveQuery) focusRequester.requestFocus() }` (skip when a Discover seed arrives, which already sets `editing = false` at :112).

### F-DS-03 — Filter sliders keep M3's default thumb/track and a white thumb on a white sheet
- Screens: 15, 104
- Where: `feature/search/SearchFilterSheet.kt:360-375` (RangeSlider), `:405-416` (Slider); sheet fill `designsystem/component/SheetScaffold.kt:66`
- Category: token
- Severity: P1
- Rule: (a) §5 rule 7 "restyle M3 with the tokens; never ship an M3 default"; (c) fact — `thumbColor = colors.surface` (#ffffff) on a `colors.surface` (#ffffff) sheet
- What: Only `SliderDefaults.colors(...)` is overridden; both sliders draw M3 1.4's default `thumb`/`track` composables (the tall handle bar with the track gap either side, the trailing stop-indicator). The thumb is `surface` and the sheet is `surface`, so the handle has zero contrast against the sheet — the user sees a break in the track, not a control. The histogram/ink-track reasoning at :366-369 is sound; the thumb is not covered by it.
- Evidence:
  ```kotlin
  // SearchFilterSheet.kt:364-371
  colors = SliderDefaults.colors(
      thumbColor = colors.surface,
      activeTrackColor = if (hasBars) colors.ink else colors.accent,
      inactiveTrackColor = colors.hairline,
  // SheetScaffold.kt:66
  .background(colors.surface)
  ```
- Fix: Pass a `thumb = {}` (a `surface` disc with a `hairline` stroke — or `ink`) and a `track = {}` of the design's height with no gap/stop indicator, in both sliders.

### F-DS-04 — Raw `Throwable.message` is shown as body copy on Search and Artist list
- Screens: 03, 32
- Where: `feature/search/SearchViewModel.kt:574` → `feature/search/SearchScreen.kt:637-645`; `feature/profile/ArtistListViewModel.kt:179` → `feature/profile/ArtistListScreen.kt:157`; peer that maps it: `feature/discover/DiscoverViewModel.kt:226`, `:386-393`
- Category: copy
- Severity: P2
- Rule: (b) Discover maps the throwable to user copy (`messageFor(t)`); (c) fact — rubric §4 engineering text in user-facing strings
- What: A failed page-2 fetch on results shows a Failure banner whose detail is `t.message` verbatim (Ktor/Postgrest text such as host-resolution or HTTP errors), and a failed list load puts `e.message` under "Couldn't load your saved acts". Discover is the only one of the three that translates.
- Evidence:
  ```kotlin
  // SearchViewModel.kt:574
  loadError = t.message ?: "Search failed.",
  // ArtistListViewModel.kt:179
  it.copy(isLoading = false, error = e.message ?: "Couldn't load list")
  // SearchScreen.kt:640-641
  title = "Couldn't load more",
  detail = message,
  ```
- Fix: Map throwables to two fixed sentences (offline / other) in both ViewModels as Discover does; keep the raw message in the log only.

### F-DS-05 — The price line is set four ways, none of them mono
- Screens: 02, 03, 32
- Where: `designsystem/component/HeroCard.kt:207-211` (hero "₹42,000"); `feature/discover/DiscoverHeroLogic.kt:144-149` + `designsystem/component/Tile.kt:119` (tile "· from ₹28,000"); `feature/search/SearchScreen.kt:733-739` ("₹28,000 from"); `feature/profile/ArtistListScreen.kt:323-330` ("from ₹26,000")
- Category: token
- Severity: P2
- Rule: (a) §2 "JetBrains Mono for … numerals", rubric "mono for money"; (b) four peers, four steps
- What: The same fact — the artist's from-price — is `subtitle`/700 sans on the hero, `caption` `ink4` on the rail tile, `rowTitle`/700 sans with "from" AFTER the number on the result card (reads "₹28,000 from"), and `caption`/600 `ink` with "from" before it on the list row. None is mono; the important number in each block is never its mono object.
- Evidence:
  ```kotlin
  // SearchScreen.kt:734-739
  Text(text = formatInr(price), style = AppTheme.type.rowTitle.copy(fontWeight = FontWeight.Bold), color = colors.ink)
  Text("from", style = AppTheme.type.caption, color = colors.ink4)
  // ArtistListScreen.kt:325-326
  text = "from ${formatInr(price)}",
  style = AppTheme.type.caption.copy(fontWeight = FontWeight.SemiBold),
  // HeroCard.kt:211
  style = AppTheme.type.subtitle.copy(fontWeight = FontWeight.Bold),
  ```
- Fix: One `PriceLine(amount, prefix = "from")` in `designsystem/component/` set in `monoPill`/`monoNumber` with the "from" in `caption`, used by all four.

### F-DS-06 — Artist name uses three different steps across list surfaces
- Screens: 02, 03, 14, 32
- Where: `designsystem/component/Tile.kt:110` (`rowTitle`); `feature/search/SearchScreen.kt:481` (`rowTitle`); `feature/search/SearchScreen.kt:690` (`cardTitle`); `feature/profile/ArtistListScreen.kt:251` (`rowTitle.copy(Bold)`)
- Category: token
- Severity: P2
- Rule: (a) §2 `rowTitle` 14.5/600 = row/tile name, `cardTitle` 18.5/700 "(on media)"; (b) peers differ
- What: Tile and the suggestion row use `rowTitle` as specified; the result card sets the name in `cardTitle`, the on-media step, on a light `surface3` card; the list row invents a 14.5/700 by `.copy(fontWeight = Bold)`. The same artist reads as three different sizes/weights on three consecutive screens.
- Evidence:
  ```kotlin
  // SearchScreen.kt:688-691
  Text(text = artist.name, style = AppTheme.type.cardTitle, color = colors.ink,
  // ArtistListScreen.kt:249-251
  Text(text = artist?.name ?: row.fallbackTitle,
       style = AppTheme.type.rowTitle.copy(fontWeight = FontWeight.Bold),
  ```
- Fix: `rowTitle` unmodified on all four.

### F-DS-07 — Chevrons are `lineStrong` and `ink3` here; the token and `ListRow` say `ink4`
- Screens: 14, 15, 32
- Where: `feature/search/SearchScreen.kt:508-513`; `feature/profile/ArtistListScreen.kt:283-288`; `feature/search/SearchFilterSheet.kt:579-587`, `:619-626`; reference `designsystem/component/ListRow.kt:115-117`
- Category: consistency
- Severity: P2
- Rule: (a) §2 "list row … chevron `ink4`"; (b) `ListRow.kt:117` tints `ink4`
- What: Suggestion rows and Artist-list rows tint the trailing chevron `lineStrong` (#c6c9be, a separator colour); the filter sheet's disclosure and nav rows tint it `ink3`; the design-system row tints it `ink4`. Three chevron greys in one section. `SuggestionRow` (:442-522) is also exactly `ListRow`'s shape (leading 32dp slot, title, subtitle, chevron, hairline) hand-rolled.
- Evidence:
  ```kotlin
  // SearchScreen.kt:508-511
  Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = colors.lineStrong,
  // ArtistListScreen.kt:286
  tint = colors.lineStrong,
  // SearchFilterSheet.kt:622
  tint = colors.ink3,
  ```
- Fix: `ink4` at all four sites; build `SuggestionRow` on `ListRow(leading = …, subtitle = …)`.

### F-DS-08 — Search's failed state is drawn as a warning, Discover's and its own paging failure as a failure
- Screens: 58
- Where: `feature/search/SearchScreen.kt:177-185` (`BannerTone.Attention`), `:186-194` (`Icons.Filled.Refresh`); peers `feature/discover/DiscoverScreen.kt:228-233` (`BannerTone.Failure`), `SearchScreen.kt:639-645` (`BannerTone.Failure`), `DiscoverScreen.kt:158-166` (failed = `EmptyState` + heart)
- Category: consistency
- Severity: P2
- Rule: (a) §2 `danger` = "destructive, failed", `warm` = "warnings, pending"; (b) three peers, three shapes
- What: The 58 branch pairs a `warm` (Attention) banner with an `EmptyState` whose glyph is a refresh arrow; Discover's failed feed is a bare `EmptyState` with the Saved heart; a failed refresh/paging on either screen is a `Failure` (danger) banner. The same class of event ("couldn't reach the server") gets a different colour and a different composition on each surface.
- Evidence:
  ```kotlin
  // SearchScreen.kt:177-180
  Banner(title = "We couldn't reach search.",
         detail = "This is a connection problem — it is not that no artists match.",
         tone = BannerTone.Attention,
  // DiscoverScreen.kt:231
  tone = BannerTone.Failure,
  ```
- Fix: `BannerTone.Failure` on 58, and one failed-state composition (banner + `EmptyState` with the same glyph and "Try again") shared by Discover, Search and Artist list.

### F-DS-09 — Artist list stacks two identical Chip rails: navigation and filter look the same
- Screens: 32
- Where: `feature/profile/ArtistListScreen.kt:106-118` (kind switcher), `:120-145` (category filter)
- Category: hierarchy
- Severity: P2
- Rule: (a) §2 "one accent per screen"; (b) the file's own comment (`:103-105`, `:62-66`) calls the first rail "the screen's navigation" and the second "filters within the list", yet both are `Chip` with the accent-selected style
- What: Two accent-filled chips ("Saved" and "All") sit one above the other on entry; a reader cannot tell that the top row changes the screen and the bottom row narrows it. Tapping "Bookings" re-enters the destination (a navigation) with the same visual as tapping "DJ" (a filter).
- Evidence:
  ```kotlin
  // ArtistListScreen.kt:112-116
  Chip(label = entry.chipLabel, selected = entry == kind, onClick = { if (entry != kind) onSelectKind(entry) })
  // ArtistListScreen.kt:127-131
  Chip(label = "All", selected = state.selectedCategory == null, onClick = { viewModel.selectCategory(null) })
  ```
- Fix: Move the kind switch into the header band (a text/segmented control in `ink`/`ink4`) and keep `Chip` for the filter rail only — one accent-selected object.

### F-DS-10 — Results spend accent on every active-filter chip AND the count badge
- Screens: 03, 104
- Where: `feature/search/SearchScreen.kt:348-358` (badge), `:609-617` + `:809-837` (accent chips); comment `:604-608` says the design's 03 row is four canned quick-filters, not accent chips
- Category: hierarchy
- Severity: P2
- Rule: (a) §2 "one accent per screen" — count of accent-filled objects; (c) fact — the chips on 03 are the app's own transplant from 104
- What: With three filters on, the results page shows three accent chips and, above them, an accent badge reading "3" — the same fact twice, four accent-filled surfaces. The badge is a hand-rolled `Text` (no `Badge` component exists) with horizontal-only `space.xs` padding, so a single digit renders as a squat oval rather than a disc.
- Evidence:
  ```kotlin
  // SearchScreen.kt:353-357
  modifier = Modifier
      .align(Alignment.TopEnd)
      .clip(CircleShape)
      .background(colors.accent)
      .padding(horizontal = dimens.space.xs),
  // SearchScreen.kt:811-812
  .clip(CircleShape)
  .background(colors.accent)
  ```
- Fix: On 03 draw active filters as unselected `Chip`s with a trailing × (accent stays on the badge), or drop the badge while chips are visible; give the badge `defaultMinSize(minWidth = height)`.

### F-DS-11 — Artist list's BackHeader inset is `space.sm`; every other BackHeader uses the gutter
- Screens: 32
- Where: `feature/profile/ArtistListScreen.kt:91-101`; the other six `BackHeader(` call sites in `feature/` pass `padding(horizontal = dimens.component.gutter)` (5) or `gutter` (1)
- Category: spacing
- Severity: P2
- Rule: (b) peer pushed screens; (a) §2 page horizontal padding 20
- What: The back circle and title sit 8dp from the edge here and 20dp everywhere else, so this pushed screen's header is visibly out of register with the ones before and after it in the stack.
- Evidence:
  ```kotlin
  // ArtistListScreen.kt:100
  modifier = Modifier.padding(horizontal = dimens.space.sm),
  ```
- Fix: `Modifier.padding(horizontal = gutter)`.

### F-DS-12 — One list row draws two pill styles; the score pill sits on a compat alias
- Screens: 32
- Where: `feature/profile/ArtistListScreen.kt:312-322` (hand-rolled score pill) vs `:274-281` (`Pill(text, tone)`)
- Category: token
- Severity: P2
- Rule: (a) rubric compat colour `brandSoft` on a redesigned screen (count in this file: 1, `:319`; none elsewhere in the section's screens); (b) rubric "Chip vs Pill vs StatusPill vs hand-rolled — same concept must be the same component"
- What: The score is a `monoPill` `Text` clipped to `radii.sm` on `brandSoft`, while the booking pills on the same row are the `Pill` component — two pill radii/fills side by side.
- Evidence:
  ```kotlin
  // ArtistListScreen.kt:313-320
  Text(text = artist.score.toString(), style = AppTheme.type.monoPill, color = colors.accentDeep,
       modifier = Modifier.clip(RoundedCornerShape(dimens.radii.sm)).background(colors.brandSoft)
           .padding(horizontal = dimens.space.sm, vertical = dimens.space.xs / 2))
  ```
- Fix: `Pill(score, tone = <accent tone>)` on the soft-accent token `Pill` already uses; retire `brandSoft` here.

### F-DS-13 — `ArtistTile.kt` is dead and carries the section's raw dp, compat type steps and `Color.White`
- Screens: (component; none)
- Where: `designsystem/component/ArtistTile.kt:51-52` (192.dp / 252.dp), `:65` (`radii.md`, not the tile's 18), `:116` (`type.headline`), `:127` (`type.footnote`), `:117`, `:155`, `:243` (`Color.White`); no call site anywhere in the app (only a comment at `designsystem/theme/Dimens.kt:474`); `hero.gridTileHeight` (`Dimens.kt:477`) is equally unused
- Category: slop
- Severity: P3
- Rule: (a) raw units / compat aliases; rubric §4 "dead code"
- What: Discover's rails use `Tile` (`DiscoverScreen.kt:372`, r18, `rowTitle`/`caption`); Search results are cards, not a grid; nothing uses `ArtistTile` or `gridTileHeight`. The dead file disagrees with `Tile` on radius (12 vs 18), name step (`headline` vs `rowTitle`) and meta step (`footnote` vs `caption`), and is the only place in the section with hard-coded dp and white text.
- Evidence:
  ```kotlin
  // ArtistTile.kt:51-52
  width: Dp = 192.dp,
  height: Dp = 252.dp,
  // ArtistTile.kt:116-117
  style = AppTheme.type.headline,
  color = Color.White,
  ```
- Fix: Delete `ArtistTile.kt` and the `gridTileHeight` token.

### F-DS-14 — Discover's pull-to-refresh ships M3's default indicator; the gesture exists on one of three lists
- Screens: 02
- Where: `feature/discover/DiscoverScreen.kt:137-141`; no `indicator =` or `PullToRefreshDefaults` anywhere under `feature/` (5 `PullToRefreshBox` users); scheme mapping `designsystem/theme/ArtistantTheme.kt:63`, `:71`
- Category: consistency
- Severity: P3
- Rule: (a) §5 rule 7 (M3 default look); rubric "no shadows/elevation on light surfaces"; (b) Search (03) and Artist list (32) have no pull-to-refresh
- What: The default `PullToRefreshDefaults.Indicator` is an elevated disc with an arc spinner; its colours resolve to `surface2`/`ink2` through the scheme, its shadow and shape do not. Search and Artist list only offer a Retry once a load has failed.
- Evidence:
  ```kotlin
  // DiscoverScreen.kt:137-141
  PullToRefreshBox(
      isRefreshing = state.isLoading && !state.isEmpty,
      onRefresh = viewModel::refresh,
      modifier = Modifier.fillMaxSize(),
  ) {
  ```
- Fix: A shared flat `indicator` slot (no elevation, `surface2` disc, `ink` arc) passed by all five users, and pull-to-refresh on Search results and Artist list too — or drop it from Discover.

### F-DS-15 — Page 2 loads behind a spinner; page 1 behind a skeleton
- Screens: 03
- Where: `feature/search/SearchScreen.kt:627-633` vs `:170` + `:754-778`
- Category: consistency
- Severity: P3
- Rule: (a) §2 principles "narrated, not a spinner"; (b) same screen, same list, two loading vocabularies
- What: The first page draws three skeleton cards at the real geometry; scrolling to the end draws a centred `CircularProgressIndicator`.
- Evidence:
  ```kotlin
  // SearchScreen.kt:629-631
  Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
      CircularProgressIndicator(color = colors.accentInk)
  }
  ```
- Fix: One `SkeletonBlock` card (the `ResultsSkeleton` card) as the `more` item.

### F-DS-16 — Skeletons don't match the list they stand in (Discover states the rule; peers break it)
- Screens: 03, 32
- Where: `feature/profile/ArtistListScreen.kt:336-369` (`top = lg`, `spacedBy(lg)`) vs `:186-194` (`top = md`) and `:227-231` (rows `padding(vertical = md)` + hairline); `feature/search/SearchScreen.kt:754-778` (`top = lg`, no chip-row block) vs `:577-585` (`top = md`); rule `feature/discover/DiscoverScreen.kt:344-348`
- Category: spacing
- Severity: P3
- Rule: (b) Discover's own comment: a skeleton whose geometry differs "reflows the page at the moment the content arrives, which is the one thing screen 59's note exists to prevent"; (c) fact
- What: Artist list's skeleton rows sit 4dp higher and 8dp closer together than the real rows (pitch content+16 vs content+24), so the list jumps on load; the results skeleton also starts 4dp higher than the list it is replaced by.
- Evidence:
  ```kotlin
  // ArtistListScreen.kt:342-343
  .padding(top = dimens.space.lg),
  verticalArrangement = Arrangement.spacedBy(dimens.space.lg),
  // ArtistListScreen.kt:191 / :231
  top = dimens.space.md,
  .padding(vertical = dimens.space.md)
  ```
- Fix: Same top inset and row pitch as the list in both skeletons.

### F-DS-17 — Peer insets drift: header top and list tailroom
- Screens: 02, 03, 14, 32
- Where: `feature/discover/DiscoverScreen.kt:116-118` (`top = space.sm`), `:195-198` (bottom `listTailroom` = 56); `feature/search/SearchScreen.kt:138-140` (`top = space.md`), `:381-386`, `:580-583` (bottom `contentTailroom + listTailroom` = 16 + 56); `feature/profile/ArtistListScreen.kt:100` (no top), `:188-193` (72dp tailroom on a pushed screen with no tab bar); tokens `designsystem/theme/Dimens.kt:134`, `:426`
- Category: spacing
- Severity: P3
- Rule: (b) two tab roots under the same tab bar use different tailroom; a pushed screen inherits the tab-root sum
- What: Discover's last rail clears the bar by 56, Search's last card by 72; Artist list, which has no tab bar, also pads 72. The two tab-root headers start 8 vs 12dp below the inset.
- Evidence:
  ```kotlin
  // DiscoverScreen.kt:197
  bottom = dimens.size.listTailroom,
  // SearchScreen.kt:582
  bottom = dimens.chrome.contentTailroom + dimens.size.listTailroom,
  // ArtistListScreen.kt:192
  bottom = dimens.chrome.contentTailroom + dimens.size.listTailroom,
  ```
- Fix: One header-top token for tab roots; tab roots share one tailroom expression; pushed lists take `listTailroom` alone.

### F-DS-18 — Copy: first person, "Something went wrong", a period on a banner title, a restating intro
- Screens: 02, 58, 53
- Where: `feature/discover/DiscoverScreen.kt:183`; `feature/discover/DiscoverViewModel.kt:391`, `:393`; `feature/search/SearchScreen.kt:178`, `:189`; `feature/search/CompareByServiceSheet.kt:95-101`
- Category: copy
- Severity: P3
- Rule: (b) rubric copy conventions — second person, "Couldn't", no "Something went wrong", no period on one-line titles; (a) §2 body = 15/400 `ink3`
- What: "We're onboarding the first artists right now — check back in a moment." / "We couldn't load the roster right now." / "Something went wrong loading the roster." / "We couldn't reach search." (a `Banner` title ending in a period) / "Pull back and try again in a moment." ("pull back" is not a gesture the screen has). The compare sheet's intro is two sentences that say the same thing, set in `subtitle` `ink2` rather than `body` `ink3`.
- Evidence:
  ```kotlin
  // DiscoverViewModel.kt:391-393
  return "We couldn't load the roster right now. Try again in a moment."
  }
  return "Something went wrong loading the roster."
  // SearchScreen.kt:189
  body = "Pull back and try again in a moment.",
  // CompareByServiceSheet.kt:96-98
  text = "Narrow the whole feed to one service type. " + "Pick one to see only acts who offer it.",
  style = AppTheme.type.subtitle,
  ```
- Fix: "Couldn't load the roster" / "Couldn't reach search" (no period), "Try again in a moment", one sentence in `body`/`ink3` on the sheet.

### F-DS-19 — Every empty/failed state wears a glyph circle; four of six wear the Saved heart
- Screens: 02, 57, 58, 32, 112
- Where: `feature/discover/DiscoverScreen.kt:161`, `:184` (`Icons.Filled.FavoriteBorder` for "Couldn't load Discover" and "No artists yet"); `feature/profile/ArtistListScreen.kt:158`, `:171` (the heart for Bookings and Completed too); `feature/search/SearchScreen.kt:190` (`Refresh`), `:208` (`SearchOff`); `EmptyState.icon` is nullable (`designsystem/component/EmptyState.kt:51`). Icon family: the bell is `Icons.Outlined.Notifications` (`DiscoverScreen.kt:126`) — the section's only outlined glyph — beside `Icons.Filled.Tune` (`SearchScreen.kt:342`), `Filled.Close` (`SearchFilterSheet.kt:507`), `Filled.Search` (`SearchScreen.kt:463`); `IconCircle`'s own preview uses `Icons.Filled.Notifications` (`IconCircle.kt:129`)
- Category: slop
- Severity: P3
- Rule: rubric §4 "a glyph-in-a-circle above every empty state"; §2 rubric icon family for the same concept
- What: A heart — the Saved affordance — is the emblem of a failed Discover load, an empty Discover feed, and the empty Bookings and Completed lists. The header circles mix one outlined glyph with filled ones.
- Evidence:
  ```kotlin
  // DiscoverScreen.kt:159-161
  title = "Couldn't load Discover",
  body = state.loadError,
  icon = Icons.Filled.FavoriteBorder,
  ```
- Fix: `icon = null` on failed states and on "No artists yet"; a kind-specific glyph on Artist list; one family (Outlined) for header-circle glyphs.

### F-DS-20 — Ten `.copy(fontWeight = …)` sites invent steps the ramp does not have
- Screens: 14, 03, 15, 104, 53, 32
- Where: `feature/search/SearchScreen.kt:542` (`rowTitle`→700 occasion card), `:827` (`chip`→700 in a hand-rolled chip), `:736` (see F-DS-05); `feature/profile/ArtistListScreen.kt:251`, `:326` (see F-DS-05/06); `feature/search/SearchFilterSheet.kt:342` (`subtitle`→700 range readout), `:489` (`subtitle`→600 sheet action), `:568`, `:608` (`rowTitle`→700 row titles); `feature/search/CompareByServiceSheet.kt:179-181` (`body`→600/700 radio label)
- Category: token
- Severity: P3
- Rule: (a) §2 type ramp; rubric "`.copy(fontWeight=…)` inventing a new step"
- What: Row titles in the sheet are 14.5/700 where `rowTitle` is 14.5/600; the compare sheet's radio labels are 15/600 that turn 15/700 when picked; the range readout is `subtitle` at 700.
- Evidence:
  ```kotlin
  // SearchFilterSheet.kt:568
  style = AppTheme.type.rowTitle.copy(fontWeight = FontWeight.Bold),
  // CompareByServiceSheet.kt:179-181
  style = AppTheme.type.body.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold),
  ```
- Fix: Named steps only (`rowTitle` as-is for row titles; `Chip` supplies the selected weight; `monoPill` for the range readout); selection on the radio row is the tick, not a weight change.

### F-DS-21 — Sheet header's text action is a 40dp box with no overflow handling and no touch target
- Screens: 15, 104, 53
- Where: `feature/search/SearchFilterSheet.kt:487-495` (shared with `CompareByServiceSheet.kt:88-94`)
- Category: slow
- Severity: P3
- Rule: (c) fact — rubric §5 "touch targets under 44/48 dp"
- What: "Reset"/"Clear" at `subtitle`/600 is forced into `Modifier.width(iconCircleSm)` = 40dp with `maxLines = 1` and default clip overflow, so the word clips at larger font scales; the clickable is the text's own bounds (~40 × 18dp).
- Evidence:
  ```kotlin
  // SearchFilterSheet.kt:492-494
  modifier = Modifier
      .width(dimens.component.iconCircleSm)
      .then(if (leadingEnabled) Modifier.clickable(onClick = onLeading) else Modifier),
  ```
- Fix: `widthIn(min = iconCircleSm)` + `minimumInteractiveComponentSize()` (or a row of the close circle's height); centre the title against measured widths.

### F-DS-22 — Work in composition, and an animated rotation read in composition
- Screens: 14, 15
- Where: `feature/search/SearchScreen.kt:377` (`searchSuggestions(...)` — filters facets and interleaves on every recomposition of `BrowseSurface`, not only when the query changes), `:419` (`eventTypes.chunked(2)` allocated per recomposition); `feature/search/SearchFilterSheet.kt:555-558`, `:586` (`animateFloatAsState` read through `Modifier.rotate(rotation)`)
- Category: slow
- Severity: P3
- Rule: (c) rubric §5 "work in composition without `remember`"; "state read in composition that should be read in a draw lambda"
- What: `BrowseSurface` recomposes on every `state` change (loading flags, recents, facets) and recomputes the suggestion list each time; the chevron rotation invalidates composition on every animation frame.
- Evidence:
  ```kotlin
  // SearchScreen.kt:377
  val suggestions = searchSuggestions(state.query, state.facets, state.results)
  // SearchFilterSheet.kt:586
  .rotate(rotation),
  ```
- Fix: `remember(state.query, state.facets, state.results) { … }`; `remember { eventTypes.chunked(2) }`; `Modifier.graphicsLayer { rotationZ = rotation }`.

### F-DS-23 — "See all" on every rail regardless of size
- Screens: 02
- Where: `feature/discover/DiscoverScreen.kt:360-365`; `feature/discover/DiscoverViewModel.kt:368-370`
- Category: slop
- Severity: P3
- Rule: rubric §4 "'See all' on a rail with ≤2 items"; (c) fact — a rail is built from any non-empty list
- What: A rail with one or two artists (a thin city, "Comedy" with one act) still carries an accentInk "See all" that opens a Search page showing the same one or two tiles.
- Evidence:
  ```kotlin
  // DiscoverViewModel.kt:368-370
  artists.take(RAIL_LIMIT)
      .takeIf { it.isNotEmpty() }
      ?.let { DiscoverRail(id = id, title = title, artists = it, seed = seed) }
  ```
- Fix: `actionLabel = "See all".takeIf { rail.artists.size > 2 }` (or a minimum rail size of 3 in the ViewModel).

### F-DS-24 — Discover's first screenful has four accent-filled objects, each documented as "the one accent"
- Screens: 02
- Where: `feature/discover/DiscoverScreen.kt:125-134` (bell dot — `designsystem/component/IconCircle.kt:114`, comment `:35-36` "the only accent on a header"); `:287-295` (selected "For you" `Chip`); `:320-338` (hero badge — `designsystem/component/HeroCard.kt:130`, comment `:54` "the badge is the screen's one accent"); tab bar raised circle `designsystem/component/LightTabBar.kt:245` (comment `:96` "carries the app's one accent")
- Category: hierarchy
- Severity: P3
- Rule: (a) §2 "one accent per screen" — count of accent-filled surfaces; (b) three components each claim the slot
- What: With an unread notification, the page opens on an accent dot, an accent chip, an accent badge on the hero and the accent tab circle at once. The comments assert the design draws each; nothing decides which wins.
- Evidence:
  ```kotlin
  // HeroCard.kt:54
   * The badge is the screen's one accent; the save circle is deliberately NOT
  // IconCircle.kt:35-36
   * The optional [dot] is the accent pip screen 02 puts on the bell: it says
   * "something is waiting" without a count, and it is the only accent on a header
  ```
- Fix: Keep the selected chip (state) and the tab action (chrome); set the bell dot in `ink` and the hero badge on `dark`/`onDark`, and reconcile the three comments.

### F-DS-25 — The result card is `surface3` on `page` with no hairline; the only boxed card in the section
- Screens: 03
- Where: `feature/search/SearchScreen.kt:666-672`
- Category: token
- Severity: P3
- Rule: (a) §2 card = "`surface` on `page` or `surface2` on `surface`, hairline stroke"; `surface3` is listed for "search bar, grouped list backgrounds"; (b) Discover tiles have no chrome, Artist-list rows are hairline-separated
- What: Results are tinted boxes with a 20dp radius and no stroke, between two peer surfaces that draw artists without a box. `radii.card` = 20 is explained in Dimens.kt:59-64 and is not flagged; the fill and the missing stroke are.
- Evidence:
  ```kotlin
  // SearchScreen.kt:667-672
  modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(dimens.radii.card))
      .background(colors.surface3)
      .clickable(onClick = onClick)
      .padding(dimens.space.md),
  ```
- Fix: `surface` fill with a 1dp `hairline` border, or the Artist-list row anatomy (hairline-separated rows, no box).

## Section-wide observations
- Four list anatomies for one object (an artist): `Tile` on 02, a boxed `surface3` card on 03, a hand-rolled suggestion row on 14, a hairline row on 32 — none on `ListRow`; name step (3 variants), price line (4 variants, none mono), chevron tint (3 greys) and cover radius (18 / 15 / 12) all differ across them (F-DS-05/06/07/25).
- Three private copies of the cover composable (`ArtistCover`, `ArtistThumb` ×2) paint the dark design's gradient palette over a `placeholder` fill that can never show; `MediaSlot` in Tile.kt is the design-system slot and is unused here (F-DS-01).
- Loading is done right three times (page skeleton, results skeleton, list skeleton — F-DS-16 is only drift); failure is done three different ways (heart `EmptyState`; warm banner + refresh `EmptyState`; heart `EmptyState` with a raw throwable body) — F-DS-04/08/19.
- Type debt: 10 `.copy(fontWeight)` sites; compat aliases in the section's screens: 1 colour (`brandSoft`, ArtistListScreen.kt:319) and 2 type steps in dead `ArtistTile.kt` (`headline`, `footnote`).
- Sheets: both DS sheets retire the M3 container, handle and scrim correctly and use `skipPartiallyExpanded = true`; they are the only two `scrimColor` overrides in the whole app (alpha 0.36 vs 0.40, each "measured"), which implies every other sheet ships M3's default scrim — a shared `colors.scrim` token would fix both. The sliders are the one M3-default surface left (F-DS-03).
- Accent budget: Discover up to 4 accent-filled objects, results N+1, Artist list 2 — no screen in the section holds §2's "one accent" (F-DS-09/10/24).
- Pull-to-refresh on Discover only, with M3's default indicator; the seed handoff from rails to Search is clean (`seedSearch` + tab switch), but the Search field does not take focus on arrival (F-DS-02/14).
- Copy is mostly in-house style (sentence case, "·", "₹1,20,000", "Couldn't" on headlines); the slips are first person ×4, one "Something went wrong", and "from" in three word orders (F-DS-05/18).
