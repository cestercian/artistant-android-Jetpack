# Push notifications (FCM) — activation runbook

Status: **not active.** The messaging core (realtime chat) ships without it. The
Android client half is **written and merged** (section B) and soft-fails until it
is configured; what's missing is a Firebase project + an FCM service account only
the operator can create (A), plus a backend change in the **iOS/Supabase repo**
(C — it owns the canonical schema + Edge Functions). This runbook has the exact
steps to turn it on, with B as the map of what already exists. It mirrors how iOS
shipped APNs code ahead of the P8 key.

The event/deep-link **payload contract is already defined by the existing
`send-push` Edge Function** and is reused verbatim — Android only adds an FCM
*transport* alongside APNs. Keys: `artistant_event`, `artistant_thread_id`,
`artistant_request_id`, `artistant_booking_id`. Events: `message`, `gig_request`,
`booking_confirmed_client/artist`, `booking_reminder_24h`, `booking_review_request`.

---

## A. Firebase project (operator, ~10 min)

1. Firebase console → **Add project** (or reuse one) → add an **Android app** with
   package name **`in.artistant.app`**.
2. Download **`google-services.json`** → drop it in `app/`. It's **gitignored**
   (secrets) — distribute out-of-band / via CI secret.
3. Project settings → **Service accounts** → generate a **new private key** (JSON).
   This is the **FCM v1 service account** the backend uses to send. Keep it secret.
4. (SHA-1 is only needed for Google *sign-in*, already tracked — not for FCM.)

---

## B. Android client (**already shipped** — verify it, don't rewrite it)

Everything in this section is on `main` and soft-fails until `google-services.json`
exists. It stays here as the map an operator checks the wiring against after
dropping the file in — and as the record of *why* the shipped shape is what it is,
because two parts of it (B2's RPC, B3's show-don't-route) look like extra work and
are not. Each subsection names the file that owns the behaviour.

### B1. Gradle — wired
`gradle/libs.versions.toml` pins `firebaseBom` + the `google-services` plugin; the root
`build.gradle.kts` declares `alias(libs.plugins.google.services) apply false`; `app/build.gradle.kts`
takes `implementation(platform(libs.firebase.bom))` + `implementation(libs.firebase.messaging)`.
> The `google-services` **plugin fails the build if `google-services.json` is absent**, so
> `app/build.gradle.kts` applies it **conditionally** rather than by `alias(...)`:
> ```kotlin
> if (file("google-services.json").exists()) { apply(plugin = "com.google.gms.google-services") }
> ```
> That's what keeps the tree green without the secret. **No Gradle edit is needed on
> activation** — drop the file in `app/` and the next build applies the plugin itself.

### B2. Device tokens — `platform/push/DeviceTokenRepository.kt`
An `interface` + `SupabaseDeviceTokenRepository` + `FakeDeviceTokenRepository` twin, bound in
the per-build-type `core/di/RepositoryModule`. This device's `device_tokens` row is claimed
through the **`claim_device_token` RPC** (migration `0075`), never through a `device_tokens`
upsert:
```kotlin
override suspend fun register(fcmToken: String) {
    // No session → the RPC derives the owner from auth.uid(); nothing to claim the row TO.
    if (client.auth.currentSessionOrNull()?.user == null) return
    client.postgrest.rpc(
        "claim_device_token",
        buildJsonObject {
            put("p_apns", JsonNull)   // exactly one transport (0069's XOR) — Android is always FCM
            put("p_fcm", fcmToken)
            put("p_device_model", Build.MODEL)
            put("p_os_version", Build.VERSION.RELEASE)
        },
    )
}
```
> **Do not "simplify" this back to `upsert(...) { onConflict = "fcm_token" }`.** Under the
> `device_tokens_owner` policy (mig `0002`: `USING (auth.uid() = user_id)`) an
> `ON CONFLICT DO UPDATE` landing on a row still owned by the **prior** user fails the UPDATE
> `USING` check → Postgres **42501**, swallowed by the caller's `runCatching`. On an account
> switch the arriving user's registration silently failed and they got **no pushes at all**,
> while the stale row kept addressing the account that left. `claim_device_token` is
> `SECURITY DEFINER` and bypasses that check on purpose: possession of the opaque, unguessable
> push token *is* the proof of device ownership. iOS `PushService.persistToken` calls the same
> RPC with `p_apns` set and `p_fcm` nil — one path, two transports.

Release is the other half, and it *is* a plain delete: `device_tokens_owner` scopes it to the
caller's own row, which is exactly what we want (one account can't unregister another's phone)
and exactly why it has to run **before** `auth.signOut()`, while the session still exists.
```kotlin
override suspend fun unregister(fcmToken: String) {
    client.postgrest.from("device_tokens").delete { filter { eq("fcm_token", fcmToken) } }
}
```
The claim/release orderings are unit-tested against the fake in
`app/src/test/.../push/DeviceTokenLifecycleTest.kt` — an account switch on one device is the
case that matters.

### B3. Lifecycle + arrival — `platform/push/PushService.kt`, `ArtistantMessagingService.kt`
`PushService` owns the token lifecycle and is the only caller of the repo; the
`FirebaseMessagingService` is a thin entry point that never touches it:

- **cold launch** → `PushService.registerIfPermitted()` from `ArtistantApplication.onCreate`
- **every completed sign-in** → `registerIfPermitted()` from `SessionManager.completedSignIn()`
  (no sign-in path passes through `Application.onCreate`, so without this a returning user gets
  nothing and an in-process account switch leaves the row with the account that left)
- **token rotation** → `PushService.handleNewToken(token)` from `onNewToken`, which caches the
  token in prefs (`push.lastKnownFcmToken`) and re-claims
- **sign-out** → `PushService.onSigningOut()` from `SessionManager.signOut()`, **first**, before
  `client.auth.signOut()` and before `prefs.wipeAll()` clears the cached token

Arrival only *shows*:
```kotlin
override fun onMessageReceived(message: RemoteMessage) {
    val plan = pushNotificationPlan(message.data) ?: return  // no artistant_event → nothing honest to show
    if (!isNotificationPermissionGranted(this)) return       // notify() is a silent no-op without it
    NotificationManagerCompat.from(this).notify(plan.notificationId, build(plan, message.data))
}
```
> **Receipt may only SHOW; the tap routes.** `send-push` sends data-only (C2), so this fires on
> every arrival, foreground *and* background. Handing the payload straight to
> `PushService.handleNotificationPayload` here — it ends in `TabRouter.apply` — flipped a client
> out of a half-finished booking form into a thread they never asked for, latched a pending id
> so the next cold launch landed on a conversation nobody tapped, and posted **nothing**, which
> is the one thing receipt owed the user. Same seam iOS uses: `willPresent` presents,
> `didReceive` routes.

Channels are created in code by `NotificationChannels.register()` from
`ArtistantApplication.onCreate` (messages / bookings / gigs, so muting chat doesn't mute a gig
reminder) and `pushNotificationPlan` picks one per `artistant_event`. There is **no default-channel
manifest meta-data** and none should be added — it only takes effect for FCM-posted notifications,
which data-only means we never have. The manifest declares the service with the
`com.google.firebase.MESSAGING_EVENT` intent filter; `POST_NOTIFICATIONS` is handled in M1.

### B4. Tap → routing (`navigation/TabRouter.kt`)
The tap's `PendingIntent` opens `MainActivity` (`singleTop`) carrying only the `artistant_*`
extras. `MainActivity.handlePushIntent` ignores any intent without an `artistant_event`, hands
the rest to `PushService.handleNotificationPayload`, which reads the persisted role and maps the
payload through `PushPayloadRouter.route(...)` to a `PushDeepLinkAction`; `TabRouter.apply(action)`
then arms the one-shot pending channels the two tab scaffolds consume.
> The `DeepLinkRouter` this section used to describe **no longer exists** — it was a second,
> dead copy of the live `TabRouter` and was deleted. Don't re-add it, and don't read intent
> extras straight into a router from `MainActivity`: the id-only reads it used to prescribe
> skipped the event, so a `booking_review_request` and a `booking_confirmed_client` for the same
> booking were indistinguishable.

Two routing rules worth knowing before you test:
- **A blank id is an absent id.** `PushPayloadRouter` trims every value, so `""` reads as
  missing — same rule `pushNotificationPlan` uses when it picks a channel. A `message` with no
  usable thread id lands on the **inbox** (iOS does the same); a booking event with no booking
  id is ignored rather than navigated to a route with an empty argument.
- **`Ignore` is inert.** An unknown event (one newer than the installed build) neither navigates
  nor clears a deep link an earlier tap already armed and nothing has consumed yet.

### B5. Soft-fail while `google-services.json` is missing
`PushService` returns early when `FirebaseApp.getApps(context).isEmpty()`, logging
`FCM skipped — Firebase not initialised`; `SupabaseDeviceTokenRepository.register` no-ops
without a session; and a failed claim is logged, never thrown. So today the app builds, runs and
signs in with no Firebase config and simply has no token. Once the file is in `app/`, the same
paths produce one on the next launch or sign-in — **no code change on activation.**

---

## C. Backend (iOS/Supabase repo — the canonical owner)

### C1. Migration — add the FCM token column
```sql
-- device_tokens already has apns_token unique. Add fcm_token (nullable, unique).
alter table public.device_tokens add column if not exists fcm_token text unique;
-- Keep apns_token nullable so a row is EITHER an APNs or an FCM registration.
alter table public.device_tokens alter column apns_token drop not null;
```
Apply to dev, then prod (after the iOS clients tolerate a nullable apns_token —
they already select explicit columns).

### C2. `send-push` FCM branch
The function currently signs an APNs JWT and POSTs to `api.push.apple.com`. Add,
**alongside** (not replacing) the APNs path:
- Look up the recipient's `device_tokens`: send via APNs for rows with `apns_token`,
  via **FCM HTTP v1** for rows with `fcm_token`.
- FCM v1: mint an OAuth2 access token from the **service-account JSON** (`FCM_SERVICE_ACCOUNT`
  secret) for scope `https://www.googleapis.com/auth/firebase.messaging`, then
  `POST https://fcm.googleapis.com/v1/projects/<project-id>/messages:send` with:
```json
{ "message": {
    "token": "<fcm_token>",
    "data": { "artistant_event": "message", "title": "...", "body": "<preview>",
              "artistant_thread_id": "<uuid>" },
    "android": { "priority": "high" } } }
```
- **DATA-ONLY — no `notification` block.** This is what the shipped `sendOneFCMPush`
  does, and the client depends on it: with a `notification` block FCM posts the
  notification ITSELF on a background arrival, on the channel named by
  `android_channel_id` / the manifest default (neither exists) — i.e. an auto-created
  "Miscellaneous" channel, which throws away the messages/bookings/gigs split
  `NotificationChannels` registers and the per-category muting that split exists for.
  Data-only means `onMessageReceived` fires for every arrival, so
  `pushNotificationPlan` picks the channel and `ArtistantMessagingService` posts it.
  Title and body ride inside `data`.
- Return 503 if `FCM_SERVICE_ACCOUNT` is unset (so triggers retry) — same pattern as
  the APNs-missing 503. Leave APNs untouched so iOS is unaffected.

### C3. Secrets (Supabase → Edge Function secrets, both projects)
- `FCM_SERVICE_ACCOUNT` = the service-account JSON from A.3.
- `FCM_PROJECT_ID` = the Firebase project id.

---

## D. Verify (needs a real device + Firebase configured)
1. Install a signed build on a device (emulators can register FCM but a physical
   device is the real test).
2. Sign in → confirm a `device_tokens` row with an `fcm_token` appears.
3. Send a chat message from another account → the recipient device gets a push;
   tapping it deep-links to the thread.
4. Repeat for a gig request + a booking confirm.

---

## E. Tracking
Backend (C) + operator (A) are tracked in the **Push (FCM) activation** GitHub
issue; client plumbing (B) is done. Until A is done, everything here stays inert —
the app works fully over realtime chat; users just don't get background
notifications.
