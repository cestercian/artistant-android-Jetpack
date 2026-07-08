# RELEASE.md — Artistant Android release & operator runbook

The end-to-end path from this checkout to a live Play Store listing. This is the
**M8** deliverable: it consolidates every step that a build environment **cannot**
perform (signing, a real device, Play Console, Firebase, third-party dashboards)
into one operator checklist, and states plainly what has already been verified in
CI-shape builds versus what only a device or the operator can confirm.

Shared-backend note: Android is a **second client of the same Supabase backend**
the iOS app owns. The schema, Edge Functions, and RLS are **not** re-created here —
they are already live (iOS repo owns migrations). Android's job at release is to
point at the right project per flavor and satisfy the client-side prerequisites
below. **The one backend change Android forces is the FCM push branch** — tracked
separately in [`PUSH_SETUP.md`](PUSH_SETUP.md), operator-gated.

---

## 0. What is verified vs what is pending

The build agent runs on a headless machine with **no emulator, no device, no
cmdline-tools/AVD, and no signing identity**. So:

### ✅ Verified in this repo (compile + JVM unit only)
- `./gradlew :app:assembleDebug` — green, all flavors (dev/staging/prod).
- `./gradlew :app:testDevDebugUnitTest` — green, **210 unit tests, 0 failures**
  (repositories, ViewModels, stores, calendar planner, observability allowlist,
  billing/entitlement).
- `./gradlew :app:assembleProdRelease` — green **with R8 on** (minify +
  resource-shrink). R8's static pass succeeds; the mapping `seeds.txt` shows
  **895 `$$serializer` classes kept, 0 removed** — the serialization + type-safe
  nav surface survives shrinking (see §5).

### ⏳ Pending — requires a device or the operator (CANNOT be done from CI)
- **Any on-device / emulator run.** No screen has been rendered on a real Android.
- **Instrumented / Compose-UI tests** (`connectedAndroidTest`) — need a device/AVD.
- **Baseline / startup profile** — needs Macrobenchmark on a physical device.
- **Real auth round-trips** (Google, Apple/OIDC, email) against live GoTrue.
- **Push delivery**, **calendar mirror** (CalendarContract), **Play Billing**
  purchase flow — all device + external-service gated.
- **Signing, Play Console, Firebase, PostHog/Sentry keys** — operator dashboards.

Treat everything in §0-Pending as an explicit release gate, not an assumption.

---

## 1. Local prerequisites (one-time)

| Need | Value |
|---|---|
| JDK | 21 (bundled: `/Applications/Android Studio.app/Contents/jbr/Contents/Home`) |
| Android SDK | `~/Library/Android/sdk`; only **android-36.1** is installed → `compileSdk`/`targetSdk = 36` |
| minSdk | 26 |
| AGP / Gradle | 8.9.1 / 8.13 |
| Open in Studio | **File → Open** the repo root (a Gradle project). Do **not** "Import from Sources" — that triggers `SymbolicIdAlreadyExistsException`. Avoid trailing spaces in the path. |

### `secrets.properties` (gitignored — never commit)
Create at repo root. `app/build.gradle.kts` reads it via the `secret()` helper and
falls back to `REPLACE` placeholders when absent (so the repo still builds without
secrets, it just can't reach a backend).

```properties
# Supabase publishable ANON keys (RLS-protected; safe in a client, still kept out of git)
DEV_SUPABASE_URL=https://wzctkcrereiqasarrbxm.supabase.co
DEV_SUPABASE_ANON_KEY=<dev anon publishable key>
STAGING_SUPABASE_URL=https://<staging-ref>.supabase.co
STAGING_SUPABASE_ANON_KEY=<staging anon key>
PROD_SUPABASE_URL=https://<prod-ref>.supabase.co
PROD_SUPABASE_ANON_KEY=<prod anon key>
```

**Never** put a `service_role` key here or anywhere in the client — RLS is the only
authorization model and the client is untrusted. Rotate any key that lands in chat.

---

## 2. Flavors & build types

Three `env` flavors, each pinned to a Supabase project via `BuildConfig`:

| Flavor | Backend | Use |
|---|---|---|
| `dev` | `artistant-dev` | day-to-day dev, anonymous sign-in allowed |
| `staging` | staging project | pre-prod QA |
| `prod` | `artistant-prod` | **the Play release** |

Release AAB for Play:
```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:bundleProdRelease      # → app/build/outputs/bundle/prodRelease/*.aab
```

---

## 3. Signing (operator — required for any Play upload)

The release build is currently **unsigned** (CI has no keystore). Before upload:

1. Create an **upload keystore** (once):
   ```bash
   keytool -genkey -v -keystore artistant-upload.jks -keyalg RSA -keysize 2048 \
     -validity 10000 -alias artistant
   ```
2. Add to `secrets.properties` (gitignored) and wire a `signingConfigs { release { … } }`
   block reading those props, then `signingConfig = signingConfigs.getByName("release")`
   in the `release` build type:
   ```properties
   RELEASE_STORE_FILE=/abs/path/artistant-upload.jks
   RELEASE_STORE_PASSWORD=…
   RELEASE_KEY_ALIAS=artistant
   RELEASE_KEY_PASSWORD=…
   ```
3. Enroll in **Play App Signing** (Play generates the app signing key; you keep the
   upload key). This is the default for new apps.

> Left as an operator step, not committed, because the keystore + passwords must
> never enter git and the build must succeed without them for CI.

---

## 4. Firebase / FCM (operator — for push)

Full steps in [`PUSH_SETUP.md`](PUSH_SETUP.md). Summary of what release needs:

1. Firebase project(s); add an Android app per flavor applicationId
   (`in.artistant.app`, `.dev`, `.staging` suffixes if you split them).
2. Drop the per-flavor **`google-services.json`** into `app/src/<flavor>/`
   (gitignored — never commit).
3. Add the FCM branch to the shared `send-push` Edge Function + a
   `device_tokens.fcm_token` column (the one backend change Android forces —
   see PUSH_SETUP.md; iOS repo owns the migration).
4. FCM push is otherwise **inert** until the token registers — no crash without it.

---

## 5. R8 / release shrinking (done in-repo; device-verify before submit)

`release` has **`isMinifyEnabled = true` + `isShrinkResources = true`**. Keep rules
live in [`app/proguard-rules.pro`](../app/proguard-rules.pro) and protect the
kotlinx.serialization surface R8 would otherwise strip — our `@Serializable` models,
the DataStore-persisted calendar/billing state, and the **type-safe Navigation-Compose
routes** (a stripped route serializer crashes on the first navigation).

- Verified: `assembleProdRelease` is green and `seeds.txt` keeps all 895 serializers.
- **Gate:** a green build does NOT prove a serializer survives at *runtime*. Install
  the release AAB/APK on a device (`bundletool` or an internal-testing track) and
  **smoke-test navigation between every tab + a booking + a chat send + (if flipped
  on) the paywall** before promoting to production. If anything crashes with a
  `SerializationException` / `NoSuchMethod`, add a `-keep` for that class and rebuild.
- Keep the `mapping.txt` from `app/build/outputs/mapping/prodRelease/` for each
  release — upload it to Play so crash stacktraces de-obfuscate.

---

## 6. Play Console (operator)

1. Create the app (package `in.artistant.app`), fill the store listing, content
   rating, data-safety form (mirror the iOS privacy manifest: analytics is a
   5-event allowlist, no PII; chat bodies never leave the device via analytics).
2. Upload the signed `prodRelease` AAB to **Internal testing** first.
3. **Billing products** (only needed to un-dorm the subscription seam — see §7):
   create two auto-renewing subscriptions matching the IDs in `AppEnvironment`:
   - `in.artistant.subscription.artist.monthly`
   - `in.artistant.subscription.client.monthly`
   Configure Real-time developer notifications (RTDN) → the shared
   `app-store-notifications`-equivalent path if/when server-side entitlement
   verification is added (currently client-side only, dormant).

---

## 7. Feature flags & operator assets

| Flag / asset | Where | Default | Flip when |
|---|---|---|---|
| `SUBSCRIPTIONS_ENABLED` | `app/build.gradle.kts` buildConfigField | **false** | Play billing products live + entitlement server verified. Turning it on lights the paywall gates (checkout + artist-home banner). |
| `POSTHOG_API_KEY` | buildConfigField / secrets | blank (no-op) | Analytics wanted. Blank = guarded no-op, SDK not linked. |
| `SENTRY_DSN` | buildConfigField / secrets | blank (no-op) | Crash reporting wanted. |
| Brand fonts | `designsystem/theme/Type.kt` (TODO) | platform fallback | Real Instrument Serif / Geist `.ttf` dropped into `res/font/` (tracked #15/#18). |
| Spotify embed, audio playback, gallery strip | EPK/artist screens | omitted | Deferred (#18) — operator assets + SDK. |

---

## 8. Release smoke test (the device gate — run before every production promotion)

Minimum manual pass on a physical device, prod flavor, **release build**:

1. Cold start → onboarding → **sign up (email)** → role pick → lands on the tabs.
2. Client: Discover loads real photos → open an artist → **Message** (thread
   creates) → send a message (redaction holds) → start a booking → Confirmed.
3. Artist: Home dashboard loads (score/availability/requests, no silent-fail
   banner) → accept a gig request → EPK add/remove a sample.
4. Calendar toggle on (grant perms) → a confirmed booking mirrors to the OS calendar.
5. Delete account → data actually anonymized, calendar events removed.
6. If `SUBSCRIPTIONS_ENABLED`: paywall renders + a sandbox purchase entitles.
7. No `SerializationException` / R8-stripping crashes anywhere (see §5).

Anything that fails here blocks the release — none of it is visible from a
compile-only build.

---

## 9. Deferred / tracked (not release blockers, but the honest backlog)

- **#18** — gallery strip, Spotify embed, audio/sample playback (operator assets + SDK).
- **#15** — signup polish tokens + real brand fonts.
- **#24** — FCM push activation (PUSH_SETUP.md).
- **#26** — wizard draft persistence.
- **Instrumented/Compose-UI test suite + baseline profile** — need a device/AVD;
  the JVM unit net (210) is the current regression guard.
