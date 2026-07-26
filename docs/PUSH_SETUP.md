# Push notifications (FCM) — activation runbook

Status: **not active.** The messaging core (realtime chat) ships without it. Push
delivery needs a Firebase project + an FCM service account that only the operator
can create, plus a backend change in the **iOS/Supabase repo** (which owns the
canonical schema + Edge Functions). This runbook has the exact steps + code to
turn it on. It mirrors how iOS shipped APNs code ahead of the P8 key.

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

## B. Android client (code — apply once `google-services.json` exists)

### B1. Gradle
`gradle/libs.versions.toml` — add:
```toml
firebase-bom = "33.7.0"
google-services = "4.4.2"
[libraries]
firebase-messaging = { module = "com.google.firebase:firebase-messaging" }
firebase-bom = { module = "com.google.firebase:firebase-bom", version.ref = "firebase-bom" }
[plugins]
google-services = { id = "com.google.gms.google-services", version.ref = "google-services" }
```
Root `build.gradle.kts` plugins: `alias(libs.plugins.google.services) apply false`.
`app/build.gradle.kts`: `alias(libs.plugins.google.services)` + `implementation(platform(libs.firebase.bom))` + `implementation(libs.firebase.messaging)`.
> The `google-services` **plugin fails the build if `google-services.json` is
> absent** — that's why this isn't wired yet. Add the plugin line only together
> with the file.

### B2. Device-token repo (add to `platform/push/`)
```kotlin
@Singleton
class DeviceTokenRepository @Inject constructor(private val client: SupabaseClient) {
    // Upsert the FCM token for the signed-in user. onConflict = fcm_token so a
    // token reassigned to a new account moves ownership (mirrors iOS apns upsert).
    suspend fun register(fcmToken: String) {
        val uid = client.auth.currentSessionOrNull()?.user?.id?.lowercaseUuid() ?: return
        client.postgrest.from("device_tokens").upsert(
            DeviceTokenRow(user_id = uid, fcm_token = fcmToken,
                device_model = Build.MODEL, os_version = Build.VERSION.RELEASE),
        ) { onConflict = "fcm_token" }
    }
}
```

### B3. `ArtistantMessagingService` (`platform/push/`)
```kotlin
@AndroidEntryPoint
class ArtistantMessagingService : FirebaseMessagingService() {
    @Inject lateinit var tokens: DeviceTokenRepository
    @Inject lateinit var session: SessionManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        // Only register if signed in; else it's registered post-login (see below).
        scope.launch { runCatching { tokens.register(token) } }
    }

    override fun onMessageReceived(msg: RemoteMessage) {
        val d = msg.data
        val event = d["artistant_event"] ?: return
        showNotification(event, d)   // NotificationCompat + a channel; PendingIntent below
    }
    // Tap → MainActivity with the artistant_* extras → DeepLinkRouter (see B4).
}
```
Manifest (`<application>`): the service with
`<intent-filter><action android:name="com.google.firebase.MESSAGING_EVENT"/></intent-filter>`
and a default notification channel meta-data. `POST_NOTIFICATIONS` is already
handled (M1).

### B4. Deep-link routing (producer for the existing `DeepLinkRouter`)
The consumer already exists (`state/DeepLinkRouter.pendingThreadId`, consumed by
MessagesScreen). The FCM tap is the **producer**:
```kotlin
// In MainActivity.onCreate / onNewIntent:
intent.extras?.getString("artistant_thread_id")?.let { deepLinkRouter.openThread(it) }
intent.extras?.getString("artistant_request_id")?.let { deepLinkRouter.openRequest(it) }
intent.extras?.getString("artistant_booking_id")?.let { deepLinkRouter.openBooking(it) }
```
Add the matching `pendingRequestId` / `pendingBookingId` channels to `DeepLinkRouter`
(one line each) + consumers in ArtistHome (M5) / Bookings.

### B5. Register the token after login
In `RootViewModel`/`SessionManager` post-sign-in (and on cold launch if signed in):
`FirebaseMessaging.getInstance().token.await().let { tokens.register(it) }` — guarded
so it no-ops when Firebase isn't configured (wrap in `runCatching`).

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
    "notification": { "title": "...", "body": "<redacted preview>" },
    "data": { "artistant_event": "message", "artistant_thread_id": "<uuid>" },
    "android": { "priority": "high" } } }
```
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
Client plumbing (B) + backend (C) + operator (A) are tracked in the **Push (FCM)
activation** GitHub issue. Until A is done, everything here stays inert — the app
works fully over realtime chat; users just don't get background notifications.
