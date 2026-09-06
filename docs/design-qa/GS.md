<!-- Appendix of docs/DESIGN_QA_2026-09.md — raw section report, verbatim from the section auditor; line numbers as of main@02ebeb2. -->

# GS — Getting started (screens 01, 11, 12, 13, 27, 28, 29, 30, 31, 62, 71, 90, 114, 118, 119)

Source root: `app/src/main/java/in/artistant/app/`. Compat-alias sweep (rubric §1): **0** compat
colour aliases and **0** compat type aliases in `feature/signup/*.kt` + `ui/ArtistantRoot.kt`
(`grep -n 'colors\.(bg|bgElev|…|accentSoft)'` and `type\.(footnote|…|tabLabel)` both empty).
`ui/ArtistantRoot.kt` is pure gate logic (no composables); its only sweep hit (`:114`) is KDoc.

## Coverage
| Screen | File(s) | Reviewed | Findings |
|---|---|---|---|
| 01 | feature/signup/SplashScreen.kt, ui/ArtistantRoot.kt | yes | F-GS-16, F-GS-19 |
| 11 | feature/signup/RoleScreen.kt, SignupChrome.kt | yes | F-GS-02, F-GS-03, F-GS-14, F-GS-19, F-GS-20, F-GS-21 |
| 12 | feature/signup/SignupAuthScreen.kt, ui/auth/AuthViewModel.kt | yes | F-GS-01, F-GS-02, F-GS-07, F-GS-09, F-GS-11, F-GS-13, F-GS-14, F-GS-18, F-GS-24 |
| 13 | feature/signup/NotifPermissionScreen.kt | yes | F-GS-03, F-GS-04, F-GS-12, F-GS-19, F-GS-20, F-GS-21 |
| 27 | feature/signup/CommunityCommitmentScreen.kt | yes | F-GS-02, F-GS-03, F-GS-19, F-GS-21 |
| 28 | feature/signup/EmailSignUpScreen.kt | yes | F-GS-02, F-GS-09, F-GS-10, F-GS-14, F-GS-15, F-GS-20 |
| 29 | feature/signup/ProfileScreen.kt, SignupStep.kt | yes | F-GS-02, F-GS-08, F-GS-09, F-GS-11, F-GS-14, F-GS-17, F-GS-19 |
| 30 | feature/signup/DoneScreen.kt | yes | F-GS-03, F-GS-04, F-GS-19, F-GS-20, F-GS-21, F-GS-22 |
| 31 | feature/signup/LegalScreen.kt | yes | F-GS-01, F-GS-02, F-GS-06, F-GS-15, F-GS-18 |
| 62 | feature/signup/PrivacyScreen.kt, PrivacyPreferences.kt | yes | F-GS-02, F-GS-05, F-GS-11, F-GS-14 |
| 71 | feature/signup/RoleScreen.kt (`hydrationError`), SignupChrome.kt (`HydrationErrorBanner`) | yes | F-GS-14 |
| 90 | feature/signup/ProfileScreen.kt (`HandleStatus.Taken`) | yes | F-GS-17, F-GS-19, F-GS-20 |
| 114 | feature/signup/LegalScreen.kt | yes | F-GS-01, F-GS-06, F-GS-18 |
| 118 | feature/signup/WelcomeScreen.kt | yes | F-GS-01, F-GS-04, F-GS-10, F-GS-12, F-GS-16, F-GS-21 |
| 119 | feature/signup/EnterCodeScreen.kt | yes | F-GS-02, F-GS-09, F-GS-10, F-GS-14, F-GS-19 |

## Findings

### F-GS-01 — Legal viewer ships in a raw M3 sheet: default grabber, status-bar pad, back arrow
- Screens: 31, 114 (opened from 118 and 12)
- Where: `feature/signup/WelcomeScreen.kt:211-220`; `feature/signup/SignupFlow.kt:205-214`; `feature/signup/SignupChrome.kt:86-90`; `feature/signup/LegalScreen.kt:126-143`
- Category: consistency
- Severity: P1
- Rule: (a) §5 rule 7 "never ship an M3 default"; (b) 13 other feature files pass `dragHandle` and 10 use `SheetScaffold` (grabber 36×4, title, hairline) — `feature/system/WhatsNewSheet.kt`, `feature/booking/ReviewSheet.kt`, `feature/search/SearchFilterSheet.kt`, …
- What: Both call sites build `ModalBottomSheet(onDismissRequest, sheetState, containerColor = surface)` and nothing else, so the sheet draws Material's default drag handle (`onSurfaceVariant` at 40 %, M3 geometry). Inside it, `LegalScreen` is a full `SignupScaffold`, which applies `fillMaxSize().background().statusBarsPadding()` — a status-bar-height blank band under the grabber — and then a header with a **Back** arrow circle that closes a sheet. The user sees: M3 grabber, empty strip, back arrow, then the title.
- Evidence:
  ```kotlin
  // WelcomeScreen.kt:212-218 (SignupFlow.kt:206-213 is identical)
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  ModalBottomSheet(
      onDismissRequest = { legalDoc = null },
      sheetState = sheetState,
      containerColor = colors.surface,
  ) {
      LegalScreen(doc = doc, onClose = { legalDoc = null })
  ```
- Fix: `dragHandle = null` and wrap the legal body in `SheetScaffold(title = selected.title)` (segments + sections, no `SignupScaffold`/status-bar inset/back circle), keeping `LegalScreen` as the pushed full-screen variant for the settings route.

### F-GS-02 — Section hand-rolls its pushed header at 15/700 with a 40 circle; `BackHeader` is 17/700 + 42
- Screens: 27, 11, 71, 12, 119, 28, 29, 90, 31, 114, 62
- Where: `feature/signup/SignupChrome.kt:154-158`, `:172-178`, `:93`; peer `designsystem/component/Headers.kt:114-128`; `feature/signup/PrivacyScreen.kt:139-144` vs `feature/profile/NotificationSettingsScreen.kt:152`
- Category: token
- Severity: P2
- Rule: (a) §2 "header 56 tall: … centred 17/700 with a 42 back circle"; (b) `BackHeader` uses `IconCircle` at its default 42 and `AppTheme.type.sectionTitle` (17/700); `NotificationSettingsScreen.kt:152` uses `BackHeader` while its sibling settings screen `PrivacyScreen` uses `SignupHeader`
- What: `SignupHeader` draws the back circle at `dimens.component.iconCircleSm` (40 dp, `Dimens.kt:229`) and the title as `sectionTitle.copy(fontSize = body.fontSize)` = 15/700, in a band padded `space.sm` vertically (≈ 48 dp, not 56). Every pushed screen in this section is therefore 2 sp smaller and 2 dp lighter than every pushed screen outside it; Privacy (62) and Notification settings sit side by side in account settings with two different headers.
- Evidence:
  ```kotlin
  // SignupChrome.kt:154-158, 172-174
  IconCircle(icon = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back",
      onClick = onBack, size = dimens.component.iconCircleSm)
  …
  Text(title, style = AppTheme.type.sectionTitle.copy(fontSize = AppTheme.type.body.fontSize),
  // Headers.kt:114-127
  IconCircle(icon = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = backLabel, onClick = onBack)
  Text(text = title, style = AppTheme.type.sectionTitle,
  ```
- Fix: Make `SignupHeader` delegate to `BackHeader(centered = !titleAtStart, trailing = …)` (adding a `middle` slot there for the 29/90 strip) and drop the `iconCircleSm`/15 sp overrides.

### F-GS-03 — Two to five accent-filled surfaces compete on 27, 11, 30 and 13
- Screens: 27, 11, 30, 13
- Where: `feature/signup/CommunityCommitmentScreen.kt:119-131` (+ CTA `:81-87`); `feature/signup/RoleScreen.kt:201`, `:217` (+ CTA `:84-89`); `feature/signup/DoneScreen.kt:95-100`, `:143-147` (+ CTA `:86-91`); `feature/signup/NotifPermissionScreen.kt:105-109` (+ CTA `:170-176`)
- Category: hierarchy
- Severity: P2
- Rule: (a) §2 "accent — the one signal", Principles "one accent per screen"
- What: Accent-filled objects visible at once — 27: four numbered accent squares + the CTA (**5**); 11 (a role selected): accent icon box + accent radio disc + the 26 % accent card tint + the CTA (**3 fills + a tint**; the KDoc at `:141-145` justifies tint + ring + radio but not the icon box); 30: 72 dp accent check disc + accent "86" disc + CTA (**3**); 13: 72 dp accent bell square + CTA (**2**). On 27 the lime CTA is the fifth lime object on the page.
- Evidence:
  ```kotlin
  // CommunityCommitmentScreen.kt:119-123 (×4 via forEachIndexed)
  Box(Modifier.size(dimens.size.iconXl).clip(RoundedCornerShape(dimens.radii.sm)).background(colors.accent),
  // RoleScreen.kt:201 and :217
  .background(if (selected) colors.accent else colors.hairline),
  Modifier.background(colors.accent)
  // DoneScreen.kt:100 and :147
  .background(colors.accent),
  ```
- Fix: Keep the accent on the CTA only — number badges in `surface2`/`ink`, the role glyph box in `surface2` with the ring + radio carrying selection, and the hero glyph discs on 13/30 in `dark` or `surface2` (the palette's own reserved surfaces).

### F-GS-04 — Body paragraphs set in `ink4` (meta colour) on 118, 13 and 30
- Screens: 118, 13, 30
- Where: `feature/signup/WelcomeScreen.kt:124-128`; `feature/signup/NotifPermissionScreen.kt:127-131`; `feature/signup/DoneScreen.kt:124-128`
- Category: token
- Severity: P2
- Rule: (a) §2 `ink3` = "body copy on light", `ink4` = "captions, meta"; `body` = 15/400
- What: The one paragraph under each hero headline is `AppTheme.type.body` in `colors.ink4` (#8a8d82). Peers that use the meta colour correctly pair it with `subtitle` (11 `:104-108`, 27 `:100-104`, 12 `:142-146`). The three hero screens read as 15 sp captions.
- Evidence:
  ```kotlin
  // WelcomeScreen.kt:124-128
  Text("Transparent pricing. Verified talent. Book with confidence.",
      style = AppTheme.type.body, color = colors.ink4)
  // NotifPermissionScreen.kt:127-131
  Text("Artists reply in about an hour and a hold lasts 48. Alerts are how you keep the date.",
      style = AppTheme.type.body, color = colors.ink4)
  ```
- Fix: `color = colors.ink3` at the three sites (or `subtitle` + `ink4` if the design draws them as sub-lines).

### F-GS-05 — Privacy hand-rolls its switch row and re-passes the chevron `ListRow` already draws
- Screens: 62
- Where: `feature/signup/PrivacyScreen.kt:224-265`, `:169-176`, `:187-194`; peers `designsystem/component/SwitchRow.kt:39-77` (used ×8 in `feature/profile/NotificationSettingsScreen.kt:179-236`), `designsystem/component/ListRow.kt:114-119`
- Category: consistency
- Severity: P2
- Rule: (b) `SwitchRow` is "ListRow's sibling — same title/subtitle typography, same hairline, same 56dp floor"; `ListRow` draws `KeyboardArrowRight` at `iconLg`/`ink4` whenever `onClick != null`
- What: `PrivacyToggle` re-implements `SwitchRow` with `padding(vertical = space.lg)` (16 dp, vs `space.sm` in `SwitchRow.kt:59`) and no 56 dp floor, so the read-receipts row is taller than every switch row on Notification settings. The two `ListRow`s pass a `trailing` chevron that is byte-for-byte what the component draws itself (`ListRow.kt:114-118`), so the `trailing` slot now shadows the built-in one for no visible difference.
- Evidence:
  ```kotlin
  // PrivacyScreen.kt:234-238
  Row(modifier = Modifier.fillMaxWidth().hairlineBottom().padding(vertical = dimens.space.lg)
  // PrivacyScreen.kt:166-176
  ListRow(title = "Privacy policy", onClick = { onOpenLegal(LegalDoc.Privacy) },
      trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, …, tint = colors.ink4,
          modifier = Modifier.size(dimens.size.iconLg)) },
  ```
- Fix: Replace `PrivacyToggle` with `SwitchRow(title, subtitle = detail, checked, onCheckedChange)` and delete the two `trailing = { Icon(…) }` blocks.

### F-GS-06 — `LegalSegments` duplicates the design-system `SegmentedControl`
- Screens: 31, 114
- Where: `feature/signup/LegalScreen.kt:198-242`; peer `designsystem/component/SegmentedControl.kt:48-88`
- Category: consistency
- Severity: P2
- Rule: (b) `SegmentedControl` already draws "surface3 track, selected segment a raised hairline fill rather than lime" (`SegmentedControl.kt:37-44`, `:61`, `:88`) — the exact rationale `LegalScreen.kt:193-196` gives for not using `Chip`
- What: 45 lines re-create the component that exists for this: `surface3` track (`:206`), `hairline` selected fill animated with `tabSwitch` (`:212-216`), `chip` type at Bold/Medium (`:233-235`). Any later change to the shared control (padding, radius, a11y) will miss this one.
- Evidence:
  ```kotlin
  // LegalScreen.kt:202-207, 212-213
  Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(dimens.radii.control))
      .background(colors.surface3).padding(dimens.space.xs),
  …
  val fill by animateColorAsState(
      targetValue = if (isOn) colors.hairline else Color.Transparent,
  ```
- Fix: `SegmentedControl(options = LegalDoc.entries, selected = selected, onSelect = …, label = { it.tab })` and delete `LegalSegments`.

### F-GS-07 — Sign-in overlays an M3-default spinner on the provider rows while the CTA already narrates
- Screens: 12
- Where: `feature/signup/SignupAuthScreen.kt:270-275` (vs `:203`); peers `feature/signup/EnterCodeScreen.kt:95`, `feature/signup/EmailSignUpScreen.kt:133`
- Category: slow
- Severity: P2
- Rule: (a) Principles "narrated, not a spinner"; §5 rule 7 (M3 default size 40 dp / 4 dp stroke, nothing but colour restyled); (b) 119 and 28 narrate the same wait on the button alone ("Verifying…", "Checking…")
- What: While `isSendingCode`/`isAuthenticating`, a 40 dp `CircularProgressIndicator` is drawn centred over the three provider buttons — physically on top of "Continue with Google" — while the Send button above it already reads "Sending…". The user sees two loading signals, one of them sitting on an unrelated control.
- Evidence:
  ```kotlin
  // SignupAuthScreen.kt:270-275
  if (busy) {
      CircularProgressIndicator(
          color = colors.accentInk,
          modifier = Modifier.align(Alignment.Center),
      )
  }
  ```
- Fix: Delete the overlay; the disabled rows (`enabled = !busy`, already ink4) and the "Sending…" label carry the state, as on 119/28.

### F-GS-08 — City picker is a default M3 `DropdownMenu` (shadow, 4 dp corners) on a no-chrome design
- Screens: 29, 90
- Where: `feature/signup/ProfileScreen.kt:253-279`
- Category: consistency
- Severity: P2
- Rule: (a) §2 "no card chrome (no shadows/elevation on light surfaces)"; §5 rule 7 "never ship an M3 default"; (b) every other picker in the app is a `SheetScaffold` sheet (10 files)
- What: Only `containerColor` is set; `shape` (M3 extraSmall = 4 dp), `shadowElevation` (3 dp drop shadow), `tonalElevation` and `DropdownMenuItem` padding/height are Material defaults. The eight cities appear in a floating shadowed 4 dp-cornered menu anchored to the field — the one shadow in the whole signup flow.
- Evidence:
  ```kotlin
  // ProfileScreen.kt:253-257
  DropdownMenu(
      expanded = cityOpen,
      onDismissRequest = { cityOpen = false },
      containerColor = colors.surface,
  ) {
  ```
- Fix: Open a `SheetScaffold(title = "City")` with eight `ListRow`s (tick in `accentInk` on the chosen one), or at minimum pass `shape = RoundedCornerShape(radii.lg)`, `shadowElevation = 0.dp`, `tonalElevation = 0.dp` and a hairline border.

### F-GS-09 — Enter code (and the email/handle forms) never request focus on entry
- Screens: 119, 28, 29
- Where: `feature/signup/EnterCodeScreen.kt:119-126`; `feature/signup/EmailSignUpScreen.kt:154-165`; `feature/signup/ProfileScreen.kt:144-160`; `designsystem/component/OtpField.kt` and `designsystem/component/AppTextField.kt` contain no `FocusRequester`/`requestFocus` (grep)
- Category: slow
- Severity: P2
- Rule: (c) fact — the screen's only control needs a tap before the keyboard appears; rubric §5 lists "Enter code" and "Email" explicitly
- What: `OtpField` is the single control on 119 and `onFilled = onVerify` auto-submits, yet arriving from "Send code" leaves the keyboard down until the user taps the boxes; the SMS autofill suggestion the banner at `:168-171` advertises only appears once the field is focused. 28 and 29 land the same way on their first field.
- Evidence:
  ```kotlin
  // EnterCodeScreen.kt:119-126
  OtpField(
      value = state.code,
      onValueChange = onCodeChange,
      enabled = !state.isVerifying,
      isError = state.codeError != null,
      onFilled = onVerify,
      modifier = Modifier.semantics { testTag = "code.field" },
  )
  ```
- Fix: Add an `autoFocus: Boolean` (or `focusRequester`) parameter to `OtpField`/`AppTextField` with `LaunchedEffect(Unit) { requester.requestFocus() }`, and set it on 119's field, 28's Name and 29's Handle.

### F-GS-10 — Inline text actions have ~36 dp tap targets
- Screens: 119, 28, 118
- Where: `feature/signup/SignupChrome.kt:353-361`; call sites `feature/signup/EnterCodeScreen.kt:147`, `:160`, `:184`; `feature/signup/EmailSignUpScreen.kt:120`, `:195`, `:260`; `feature/signup/WelcomeScreen.kt:101-112`
- Category: slow
- Severity: P2
- Rule: (c) fact — `Modifier.clickable` on a bare `Text` gets no Material minimum-size enforcement; `size.rowMin` = 44 dp exists (`Dimens.kt:74`)
- What: `InlineLink` pads `vertical = space.sm` (8) + `horizontal = space.xs` (4) around a 13.5 sp line ≈ 36 × (text + 8) dp; the password "Show"/"Hide" passes `caption` (12.5 sp) so it is ≈ 33 dp. "Resend code", "Change number", "Use email and a password instead", "Cancel", "Forgot password?" and "I already have an account" are all under the 44/48 dp floor.
- Evidence:
  ```kotlin
  // SignupChrome.kt:357-360
  modifier = modifier
      .clip(RoundedCornerShape(AppTheme.dimens.radii.sm))
      .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
      .padding(vertical = AppTheme.dimens.space.sm, horizontal = AppTheme.dimens.space.xs),
  ```
- Fix: Add `.defaultMinSize(minHeight = dimens.size.rowMin)` (or `minimumInteractiveComponentSize()`) inside `InlineLink` and on Welcome's login text.

### F-GS-11 — Engineering vocabulary and "Please" in user-facing copy
- Screens: 29, 62, 12 (VM strings)
- Where: `feature/signup/ProfileScreen.kt:298-302`; `feature/signup/PrivacyScreen.kt:200-204`, `:160-164`; `ui/auth/AuthViewModel.kt:502`; `feature/signup/SignupViewModel.kt:430`
- Category: copy
- Severity: P2
- Rule: rubric §4 — no "server/server-side/this version" in user-facing strings, state the fact; no "Please …"; house style "Couldn't"
- What: The handle step's permanent Note banner reads "Checked live against the server. If we can't reach it we'll say so rather than let you pick a handle that's taken." (mechanism, not fact). Privacy's banner: "This switch is saved on this device. Artistant has no server-side privacy setting, so it doesn't follow you to another phone." and the city row: "… Not adjustable in this version." Auth: "Couldn't reach the server. Check your connection and try again."; profile save: "Please sign in again to save your profile."
- Evidence:
  ```kotlin
  // ProfileScreen.kt:298-301
  Banner(title = "Checked live against the server. If we can't reach it we'll say so rather " +
      "than let you pick a handle that's taken.", tone = BannerTone.Note)
  // PrivacyScreen.kt:201-202
  title = "This switch is saved on this device. Artistant has no server-side privacy " +
      "setting, so it doesn't follow you to another phone.",
  ```
- Fix: "Saved on this phone only." / "Shown to everyone who opens your profile." / drop the handle banner (the four indicator states already say it) / "Couldn't connect. Check your connection and try again." / "Sign in again to save your profile."

### F-GS-12 — The secondary action under the CTA is styled two ways on 118 and 13
- Screens: 118, 13
- Where: `feature/signup/WelcomeScreen.kt:101-112`; `feature/signup/NotifPermissionScreen.kt:178-192`
- Category: consistency
- Severity: P2
- Rule: (b) same role (text action directly under `PrimaryButton`), different step/colour/shape/padding; (a) §2 "secondary button: same height, `surface2`" — `SecondaryButton` exists (`PrimaryButton.kt:122`) and neither uses it
- What: 118: `subtitle` (13.5) SemiBold, `accentInk`, `radii.sm` clip, `vertical = sm` (8). 13: `rowTitle.copy(fontSize = body, SemiBold)` (15) in `ink4`, `radii.buttonLg` clip, `vertical = md` (12). Two consecutive onboarding screens give the "other" action a different size, colour and hit area.
- Evidence:
  ```kotlin
  // WelcomeScreen.kt:103-104, 108-110
  style = AppTheme.type.subtitle.copy(fontWeight = FontWeight.SemiBold), color = colors.accentInk,
  .clip(RoundedCornerShape(dimens.radii.sm)).clickable(role = Role.Button, onClick = onLogin).padding(vertical = space.sm)
  // NotifPermissionScreen.kt:180-184, 188-190
  style = AppTheme.type.rowTitle.copy(fontSize = AppTheme.type.body.fontSize, fontWeight = FontWeight.SemiBold), color = colors.ink4,
  .clip(RoundedCornerShape(dimens.radii.buttonLg)).clickable(role = Role.Button, onClick = onSkip).padding(vertical = space.md)
  ```
- Fix: One `TextAction` (or `SecondaryButton`) used at both sites, `rowMin`-tall, `accentInk`.

### F-GS-13 — Provider rows are 52 dp beside a 54 dp CTA
- Screens: 12
- Where: `feature/signup/SignupAuthScreen.kt:322` (`dimens.size.ctaTall` = 52, `Dimens.kt:187`); `designsystem/component/PrimaryButton.kt:86` (`dimens.component.cta` = 54, `Dimens.kt:223`)
- Category: token
- Severity: P3
- Rule: (a) §2 "secondary button — same height" as the CTA (54)
- What: "Send code" is 54 dp; the three outlined rows under the "or" rule are 52 dp, so the stack steps down by 2 dp — enough to read as a different control family rather than a quieter one.
- Evidence:
  ```kotlin
  // SignupAuthScreen.kt:320-322
  Box(modifier = Modifier.fillMaxWidth().height(dimens.size.ctaTall)
  ```
- Fix: `.height(dimens.component.cta)`.

### F-GS-14 — Banners stack (up to three) and five screens carry a permanent explanatory Note
- Screens: 12, 28, 29, 71, 11, 119, 62
- Where: `feature/signup/SignupAuthScreen.kt:150`, `:215`, `:228`; `feature/signup/EmailSignUpScreen.kt:234`, `:242`, `:251`; `feature/signup/ProfileScreen.kt:132`, `:290`, `:298`; `feature/signup/RoleScreen.kt:94`, `:130`; `feature/signup/EnterCodeScreen.kt:168-171`; `feature/signup/PrivacyScreen.kt:200-204`
- Category: hierarchy
- Severity: P3
- Rule: rubric §3 "banners stacked (two `Banner`s visible at once)"; §4 "helper text under every field"
- What: 12 can show `authNotice` (Note) + `error` (Failure) + `noAccountFor` (Note) together; 28 always shows the "Already have an account…" Note and adds Info + Failure; 29 can show hydration Failure + save Failure + its permanent Note; 71 shows the Failure banner at the top and the "Agencies…" Note at the bottom. Permanent Notes explain mechanism on 11, 119 ("Autofill works — …tap the keyboard suggestion."), 28, 29, 62.
- Evidence:
  ```kotlin
  // EmailSignUpScreen.kt:234-238, 242, 251
  Banner(title = "Already have an account with this email? We'll sign you in instead of " +
      "creating a second one.", tone = BannerTone.Note)
  … Banner(title = "Check your inbox", tone = BannerTone.Info, …)
  … Banner(title = message, tone = BannerTone.Failure, …)
  ```
- Fix: One banner slot per screen (Failure > Info > Note precedence); demote the permanent Notes to a `caption` line or delete them.

### F-GS-15 — Two controls for one action on 28 and 31/114; 28's footer tells the user to go back but offers no way
- Screens: 28, 31, 114
- Where: `feature/signup/EmailSignUpScreen.kt:116-127`, `:139-145`; `feature/signup/LegalScreen.kt:134-141`, `:145-170`
- Category: consistency
- Severity: P3
- Rule: (b) one back affordance per pushed screen (every other pushed screen has only the circle); rubric §4 "a 'Learn more' that opens nothing"
- What: 28's header has the back circle (`onBack = onCancel`) AND a trailing "Cancel" link that calls the same `onCancel`; its footer caption "Or go back for Apple and Google" is plain `ink4` text with no click. 31/114 has an `OpenInNew` circle in the header and a footer row "Read the full document online" — both call `openLegalDoc(context, selected.url)`.
- Evidence:
  ```kotlin
  // EmailSignUpScreen.kt:117-121, 139-142
  SignupHeader(onBack = onCancel, trailing = { InlineLink("Cancel", onCancel, …
  Text("Or go back for Apple and Google", style = AppTheme.type.caption, color = colors.ink4,
  // LegalScreen.kt:138 and :149
  onClick = { linkError = openLegalDoc(context, selected.url) },
  .clickable(role = Role.Button) { linkError = openLegalDoc(context, selected.url) }
  ```
- Fix: Drop the trailing "Cancel" and make the footer line an `InlineLink("Back to Apple and Google", onCancel)`; on Legal keep the footer row and drop the header circle.

### F-GS-16 — Splash and Welcome carry different taglines although the KDoc says they share one
- Screens: 01, 118
- Where: `feature/signup/SplashScreen.kt:47`, `:123`, `:130`; `feature/signup/WelcomeScreen.kt:119`, `:125`
- Category: copy
- Severity: P3
- Rule: (b) two consecutive screens, one product, two positioning lines; `SplashScreen.kt:47` states "the same headline and the same pair of CTAs, in daylight"
- What: 01 reads "Book the artist. / Make the night." + "Bands, DJs, comics and classical acts — for brands, weddings and house shows."; 118, which replaces it a moment later, reads "Book the act, / not the agency." + "Transparent pricing. Verified talent. Book with confidence." (three fragments, marketing register).
- Evidence:
  ```kotlin
  // SplashScreen.kt:123 / WelcomeScreen.kt:119
  "Book the artist.\nMake the night.",
  "Book the act,\nnot the agency.",
  ```
- Fix: Use one headline + one sub-line on both (the design's 118 copy), so the black-to-daylight hand-off changes the light, not the words.

### F-GS-17 — Handle rule stated three times; the Name helper sits under City
- Screens: 29, 90
- Where: `feature/signup/ProfileScreen.kt:138`, `:353`, `:377`; `:282-286` vs `:205-216`
- Category: copy
- Severity: P3
- Rule: rubric §4 "helper text under every field; a subtitle that restates"
- What: The subtitle says "Lowercase, numbers and underscores.", the helper under the field says "3–24 characters. Letters, numbers and underscores.", the trailing indicator says "3–24 · a–z 0–9 _". Separately, "Name is what artists see when you book." is rendered after the City picker, two controls below the Name field it describes.
- Evidence:
  ```kotlin
  // ProfileScreen.kt:138, 353, 377
  "Your address on Artistant. Lowercase, numbers and underscores.",
  "3–24 characters. Letters, numbers and underscores."
  "3–24 · a–z 0–9 _",
  // ProfileScreen.kt:282-283 (after the City row at :219-280)
  Text("Name is what artists see when you book.",
  ```
- Fix: Keep the rule in the helper only (subtitle → "Your address on Artistant."), and move the Name caption directly under the Name field.

### F-GS-18 — Legal headings and document names follow two casing conventions
- Screens: 31, 114, 118, 12, 62
- Where: `feature/signup/LegalScreen.kt:72-101`, `:57-58`; `feature/signup/WelcomeScreen.kt:175`; `feature/signup/SignupAuthScreen.kt:369`; `feature/signup/PrivacyScreen.kt:167`
- Category: copy
- Severity: P3
- Rule: rubric §2 "sentence case everywhere, never Title Case"; (b) the two documents share one viewer and one `SignupEyebrow`
- What: Terms headings are numbered sentence case ("1 · What Artistant is"); Privacy headings are shouted ("WHAT WE COLLECT", "YOUR RIGHTS UNDER THE DPDP ACT") in the same eyebrow style. The document is "Terms of use" but "Privacy Policy" (Title Case) in the enum, the Welcome consent line and the sign-in legal line — and "Privacy policy" on the settings row.
- Evidence:
  ```kotlin
  // LegalScreen.kt:57-58
  Terms("Terms of use", "Terms", …),
  Privacy("Privacy Policy", "Privacy", …),
  // LegalScreen.kt:72 vs :89
  "1 · What Artistant is" to
  "WHAT WE COLLECT" to
  ```
- Fix: "Privacy policy" everywhere; one heading convention for both documents (numbered sentence case).

### F-GS-19 — 22 invented type steps via `.copy(fontSize/fontWeight)`, incl. numerals in the sans and two error weights
- Screens: 01, 11, 13, 27, 29, 30, 90, 119, 12, 62, 118
- Where (per file, `grep -c '\.copy\((fontWeight|fontSize)'`): `ProfileScreen.kt` 6 (`:110`, `:242`, `:345`, `:406`, `:422`, `:439`); `SignupChrome.kt` 4 (`:174`, `:246`, `:264`, `:355`); `CommunityCommitmentScreen.kt` 2 (`:128`, `:135`); `SignupAuthScreen.kt` 2 (`:176`, `:346`); `WelcomeScreen.kt` 2 (`:103`, `:203`); `DoneScreen.kt:152`; `EnterCodeScreen.kt:132`; `PrivacyScreen.kt:246`; `RoleScreen.kt:237`; `SplashScreen.kt:77`; `NotifPermissionScreen.kt:180-183`
- Category: token
- Severity: P3
- Rule: (a) §2 ramp; rubric §1 "`.copy(fontWeight=…/fontSize=…)` inventing a new step", "mono for numerals"
- What: Recurring inventions: `sectionTitle.copy(fontSize = cardTitle.fontSize)` when `cardTitle` exists (`RoleScreen.kt:237`); `rowTitle.copy(fontSize = body.fontSize)` = 15/600 at three sites (also `SwitchRow.kt:72` — a house step with no name); `caption.copy(Bold|SemiBold)` ×9 for labels/status/errors. Numerals in the sans: the step strip label "04 / 06" (`ProfileScreen.kt:108-112`, `SignupStep.kt:147`) and the pledge numbers "1"–"4" at `caption` Black (`CommunityCommitmentScreen.kt:126-130`). Error text is `caption` SemiBold `danger` under the OTP (`EnterCodeScreen.kt:132`) and handle (`ProfileScreen.kt:345`) but regular `caption` inside `AppTextField` (`AppTextField.kt:183-184`).
- Evidence:
  ```kotlin
  // RoleScreen.kt:237
  style = AppTheme.type.sectionTitle.copy(fontSize = AppTheme.type.cardTitle.fontSize),
  // ProfileScreen.kt:108-111 — "04 / 06" in the sans
  Text(bar.label, style = AppTheme.type.caption.copy(fontWeight = FontWeight.SemiBold), color = colors.ink4)
  // EnterCodeScreen.kt:132 vs AppTextField.kt:183
  style = AppTheme.type.caption.copy(fontWeight = FontWeight.SemiBold),
  style = AppTheme.type.caption,
  ```
- Fix: Name the recurring steps in `Type.kt` (`rowTitleLg` 15/600, `label` 12.5/600, `fieldError`) and use `cardTitle`/`monoLabel`/`monoPill` where the ramp already has the step.

### F-GS-20 — Glyph wells differ in shape, fill token and glyph family across peers
- Screens: 13, 30, 11, 28, 90
- Where: `feature/signup/NotifPermissionScreen.kt:105-109`, `:146-150`; `feature/signup/DoneScreen.kt:95-100`; `feature/signup/RoleScreen.kt:197-201`; `feature/signup/EmailSignUpScreen.kt:210-214`; `feature/signup/ProfileScreen.kt:415`, `:432`
- Category: consistency
- Severity: P3
- Rule: (a) §2 "icon circle … `surface2`", `hairline` = "dividers, card strokes"; (b) same concept, different geometry/glyph set
- What: The 72 dp hero glyph is a `radii.xl` rounded square on 13 and a `CircleShape` on 30. Row icon wells are 42 dp `radii.md` squares on 11 and 40 dp circles on 13. Unselected wells and the password tick are filled with `colors.hairline` (a stroke token) on 11 `:201`, 13 `:150`, 28 `:214`. On the handle indicator an outline glyph (`Filled.ErrorOutline`, `:415`) sits beside a solid one (`Filled.WarningAmber`, `:432`) for sibling states. The section's row/hero glyphs are all `Icons.Filled` (Equalizer, Mic, NotificationsActive, RequestQuote, EventAvailable, Update) — heavy against the hairline design.
- Evidence:
  ```kotlin
  // NotifPermissionScreen.kt:107-109 vs DoneScreen.kt:97-100
  .size(dimens.component.emptyGlyphCircle).clip(RoundedCornerShape(dimens.radii.xl)).background(colors.accent)
  .size(dimens.component.emptyGlyphCircle).scale(scale).clip(CircleShape).background(colors.accent)
  // RoleScreen.kt:201 / NotifPermissionScreen.kt:150
  .background(if (selected) colors.accent else colors.hairline),
  .background(colors.hairline),
  ```
- Fix: One hero-glyph shape, one well shape, `surface2` fills, and `Icons.Outlined` for the row glyphs with `Outlined.ErrorOutline`/`Outlined.WarningAmber` paired.

### F-GS-21 — Peer `surface3` cards use four radii (16 / 18 / 20 / 24)
- Screens: 118, 27, 13, 30, 11
- Where: `feature/signup/WelcomeScreen.kt:140`; `feature/signup/CommunityCommitmentScreen.kt:112`; `feature/signup/NotifPermissionScreen.kt:139`; `feature/signup/DoneScreen.kt:134`; `feature/signup/RoleScreen.kt:159`
- Category: spacing
- Severity: P3
- Rule: (a) §2 "card: radius 16–18"; (b) same object (a `surface3` content card in the signup body) on five peer screens
- What: Welcome consent + pledge rules use `radii.lg` (18); Notif rows `radii.buttonLg` (16); the Done primer `radii.card` (20); the role doors `radii.xl` (24). Two of the five sit outside the 16–18 band.
- Evidence:
  ```kotlin
  // NotifPermissionScreen.kt:139 / DoneScreen.kt:134 / RoleScreen.kt:159
  .clip(RoundedCornerShape(dimens.radii.buttonLg))
  .clip(RoundedCornerShape(dimens.radii.card))
  val shape = RoundedCornerShape(dimens.radii.xl)
  ```
- Fix: `radii.lg` on all five (keep `xl` on 11 only if the markup measures 24).

### F-GS-22 — Done's pop-in reads an animated value in composition through `Modifier.scale`
- Screens: 30
- Where: `feature/signup/DoneScreen.kt:73-78`, `:98`
- Category: slow
- Severity: P3
- Rule: (c) fact — `Modifier.scale(animated)` recomposes the subtree every frame of the spring; rubric §5 "`Modifier.scale(animated)` instead of `graphicsLayer {}`"
- What: `animateFloatAsState` (StiffnessLow spring, ~1 s) is read as `scale` in the composable body and passed to `Modifier.scale(scale)`, so the Box recomposes each frame on the first screen after signup.
- Evidence:
  ```kotlin
  // DoneScreen.kt:95-98
  Box(Modifier.size(dimens.component.emptyGlyphCircle)
      .scale(scale)
  ```
- Fix: `.graphicsLayer { scaleX = scale; scaleY = scale }` (read the state inside the lambda).

### F-GS-23 — Dead code: `AuthScreen.kt`, `EditorialHeadline.kt`, `SignupBackButton`, `SignupInputRow`
- Screens: 12 (legacy)
- Where: `ui/auth/AuthScreen.kt` (0 references to `AuthScreen(`; M3 `TextButton` at `:145`, default `ModalBottomSheet` at `:55`); `feature/signup/EditorialHeadline.kt:19-20` (0 call sites); `feature/signup/SignupChrome.kt:420` `SignupBackButton` (0 call sites); `feature/signup/SignupChrome.kt:446-448` `SignupInputRow` (`@Deprecated`, 0 call sites — the KDoc at `:434-435` still claims "six call sites" in `feature/wizard`)
- Category: slop
- Severity: P3
- Rule: rubric §4 "dead code screens"; (c) grep facts above
- What: A pre-redesign sign-in sheet with an M3 `TextButton` and an un-restyled sheet still compiles into the app, as do three unused signup helpers whose comments describe callers that no longer exist.
- Evidence:
  ```kotlin
  // SignupChrome.kt:446-448
  @Deprecated("The light design's input is AppTextField (REDESIGN_2026-09 §P1).")
  @Composable
  fun SignupInputRow(
  ```
- Fix: Delete `ui/auth/AuthScreen.kt`, `EditorialHeadline.kt`, `SignupBackButton` and `SignupInputRow`.

### F-GS-24 — The dial code is shown twice on the phone field
- Screens: 12
- Where: `feature/signup/SignupAuthScreen.kt:173-182`
- Category: copy
- Severity: P3
- Rule: rubric §2 copy conventions (no restated meta); (b) no other field in the section repeats its own prefix
- What: The field renders "+91" as the leading slot and "IN +91" as the trailing slot, so a user sees `+91  98450 12345  IN +91` on one line.
- Evidence:
  ```kotlin
  // SignupAuthScreen.kt:173-182
  leading = { Text(PhoneRules.DIAL_CODE, style = AppTheme.type.body.copy(fontWeight = FontWeight.Medium), color = colors.ink2) },
  trailing = { Text("IN ${PhoneRules.DIAL_CODE}", style = AppTheme.type.caption, color = colors.ink4) },
  ```
- Fix: Keep the leading "+91" and drop the trailing slot (or show just "IN").

### F-GS-25 — `SignupScaffold`'s footer re-implements `BottomActionBar`
- Screens: 27, 11, 119, 28, 29, 30, 31
- Where: `feature/signup/SignupChrome.kt:105-122`; peer `designsystem/component/BottomActionBar.kt:35-53`
- Category: consistency
- Severity: P3
- Rule: (b) same object — a pinned bar with `hairlineTop()`, navigation-bar inset, `spacedBy(space.md)` — built twice
- What: The section's pinned CTA bar is a private `Column` with `.hairlineTop().navigationBarsPadding().imePadding().padding(gutter).padding(top = lg, bottom = xl)`; the design-system bar does the same job (hairline top, nav-bar bottom inset, `spacedBy(md)`). Any tailroom change to one will not reach the other.
- Evidence:
  ```kotlin
  // SignupChrome.kt:106-119
  Column(modifier = Modifier.fillMaxWidth().background(AppTheme.colors.surface).hairlineTop()
      .navigationBarsPadding().imePadding().padding(horizontal = gutter)
      .padding(top = dimens.space.lg, bottom = dimens.space.xl),
      verticalArrangement = Arrangement.spacedBy(dimens.space.md),
  ```
- Fix: Have the footer slot render `BottomActionBar(modifier = Modifier.imePadding()) { footer() }` and let one component own the tailroom.

## Section-wide observations
- Every one of the 11 pushed screens in this section uses the private `SignupHeader` (15/700, 40 dp circle) instead of `BackHeader` (17/700, 42 dp); the drift is invisible inside the section and visible the moment 62 sits next to Notification settings (F-GS-02).
- The "one accent" principle is broken on 4 of 15 screens by decoration (number badges, glyph discs, a selected-state icon box) rather than by a second CTA (F-GS-03); the design-reserved dark surfaces (`dark`/`darkest`) are used only on 01 and the `AppMark`.
- Zero compat colour/type aliases and zero raw dp/sp in the section — the token discipline is good; the debt is in 22 `.copy()` inventions and `hairline` used as a fill (F-GS-19, F-GS-20).
- Three design-system components are re-implemented locally: `SwitchRow` (62), `SegmentedControl` (31/114), `BottomActionBar` (`SignupScaffold`), plus a manual chevron on `ListRow` (F-GS-05, 06, 25).
- Every M3 substrate in the section is restyled except three: the two legal `ModalBottomSheet`s (default grabber), the city `DropdownMenu` (default shadow/shape), and the sign-in `CircularProgressIndicator` (default size/stroke) (F-GS-01, 07, 08).
- Loading/failed states are otherwise honest and narrated: "Sending…/Verifying…/Checking…/Saving…" on the CTA, `HydrationErrorBanner` with Retry on 71, the four handle states on 29/90, `Reconnecting` on 01 — no scrim, no `RevealOnAppear`, no `delay()`; the 30 s resend cooldown (`SignupRules.kt:120`) is the design's own timer.
- Permanent explanatory `Banner(Note)`s appear on 5 screens (11, 119, 28, 29, 62) and up to three banners can stack on 12/28/29 (F-GS-14); two of the Notes carry engineering vocabulary ("server", "server-side", "this version") (F-GS-11).
- Icon family is uniformly `Icons.Filled` across the section (12 glyphs) — internally consistent, but heavy against the hairline design, with one filled/outline mismatch inside a single indicator (F-GS-20).
