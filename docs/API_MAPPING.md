# API_MAPPING.md — Artistant Android ↔ Supabase

The complete backend contract the Android client implements against. **The backend
is shared with iOS and reused as-is** — same Postgres schema, RLS, storage,
realtime, Edge Functions, and Auth. This document is the source of truth for every
Supabase call the Android app must make, plus the **two server-side changes**
Android forces (FCM push, Play billing notifications).

**Projects:** `artistant-dev` (`wzctkcrereiqasarrbxm`) · `artistant-prod`
(`ouikzcxtetxjuxrygkur.supabase.co`). **All authorization is RLS** — the anon
(publishable) key + a user JWT; `auth.uid()` is the signed-in user. There is no
privileged app-side path. **Model `artists.id == users.id`.**

Access via **supabase-kt**: `Postgrest` (`/rest/v1`), `Realtime` (WS), `Storage`,
`Functions` (`/functions/v1`), `Auth` (GoTrue).

---

## 1. Auth contract

- **Providers:** Google OAuth, Apple Sign-In (both brokered through Supabase
  GoTrue), and email/password. **Anonymous is disabled.** JWT expiry 1h
  (auto-refreshed by supabase-kt).
- **Google (Android):** Credential Manager / Google Identity → Google **ID token**
  → `auth.signInWith(IDToken){ idToken=…; provider=Google; nonce=… }`.
- **Apple (Android):** no native SDK → Apple **OAuth web flow** in a Custom Tab;
  Supabase `auth.signInWith(Apple)` (external browser) → deep-link return. The
  nonce dance (SHA-256 of a `SecureRandom` nonce) still applies.
- **Email:** `auth.signInWith(Email)` / `signUpWith(Email)`; handle
  `confirmationRequired`.
- **Deep-link / redirect:** register **`in.artistant.app://login-callback`** as an
  Android `intent-filter` **and add it to the Supabase dashboard → URL
  Configuration → Additional Redirect URLs** (a new Android-scheme entry). On
  return, supabase-kt completes the session from the URL.
- **New-user provisioning:** a DB trigger auto-creates the `public.users` row on
  `auth.users` insert. Write `full_name` into user metadata on first Apple
  sign-in (Apple only returns name once) so downstream denormalizations
  (`threads.client_name`, `reviews.client_name`) populate.
- **Session persistence:** supabase-kt encrypted session store + auto-refresh
  (the iOS Keychain analogue). Expose sign-in as `auth.sessionStatus: Flow`.
- **Sign-out (DPDP §11):** drop analytics identity (`Analytics.reset()`),
  `SentryConfig.setUser(null)`, wipe local prefs.
- **Preserve `signInGeneration`:** a monotonic counter bumped on every completed
  sign-in so the router re-fires even when a returning user re-auths into the same
  UUID (the iOS `authAdvanceKey` fix). Without it, same-account re-auth wedges the
  flow.
- **Tier guard:** a `prod` build MUST point at the prod host, non-prod MUST NOT —
  assert at startup, crash on mismatch (port `assertBackendMatchesTier`).

---

## 2. Database tables

Legend: **C** = client reads/writes directly · **I** = internal (service-role/
trigger only; client can't write, sometimes reads). Full column lists in the
backend inventory; the load-bearing shape:

| Table | Access | Client use | Key columns / notes |
|---|---|---|---|
| `users` | C | self only | `id, phone, full_name, avatar_url, city, role(client\|artist), handle(^[a-z0-9_]{3,24}$), terms_accepted_at`. **Only `users_select_self`** → other users' names are **denormalized** onto reviews/threads. |
| `artists` | C | published or own | `id(==users.id), handle, stage_name, category(enum7), genre, base_city, bio, cover_image_url, cover_gradient_index, days_available[], default_time_slots[], event_types[], min_price(auto), search_doc(tsvector), published, setup_complete, spotify/instagram/youtube`. **System-only:** `score, rating, total_gigs, on_time_rate, metric_*, razorpay_account_id, deleted_at`. |
| `packages` | C | via `replace_packages` RPC | `artist_id, position, name, duration_label, price_inr, includes[], popular`. |
| `tech_rider` | C | via `replace_tech_rider` RPC | `artist_id, position, item`. |
| `samples` | C | insert + `replace_samples` | `artist_id, position, title, duration_label, audio_url, spotify_track_url, cover_art_url`. UNIQUE(artist_id,position). |
| `artist_media` | C | insert/delete + list | `artist_id, kind(photo\|video), aspect(square\|portrait\|landscape), position, storage_path, mime_type, width, height, duration_seconds(1–10)`. **Cap: 6 photos / 1 video** (trigger). |
| `artist_links` | C | CRUD | `artist_id, label(≤32), url(https?), position`. |
| `bookings` | C | participant | see §3. Money + escrow + terminal-status are **system-only**. |
| `gig_requests` | C | participant | `artist_id, client_id, message, package_id, proposed_amount_inr, date_label, date_iso, venue, crowd_size, status(open\|countered\|accepted\|declined\|expired), counter_amount_inr, expires_at`. **Accept = PATCH `status='accepted'`** (trigger opens thread). |
| `threads` | C | participant | `client_id, artist_id, booking_id?, client_unread_count, artist_unread_count, last_message_at, client_name(denormalized)`. UNIQUE per (client,artist,booking). Auto-created on booking-confirm / gig-accept / client find-or-create. |
| `messages` | C | in-thread, **column-scoped** | see §4. **`body_raw` is revoked — `select(*)` 403s.** |
| `reviews` | C | public read; write once/booking | `booking_id(UNIQUE), artist_id, client_id, rating(1–5), body, client_org, client_name(denormalized), gig_date`. Trigger overwrites `artist_id` from the booking. |
| `saved_artists` | C | self | PK `(client_id, artist_id)`, `saved_at, deleted_at`(soft unsave). |
| `payouts` | I | artist reads own | escrow ledger; service-role writes. Dormant v1. |
| `score_history` | I | public read | `artist_id, score, metric_*, computed_at`. compute-score inserts. |
| `kyc_records` | C | self (dormant) | PAN/bank; review verdict columns system-only. Not in shipping UI. |
| `device_tokens` | C | self | `user_id, apns_token(UNIQUE), device_model, os_version, app_version`. **Android needs an FCM path — see §7.** |
| `subscriptions` | C | read-self (dormant) | Apple IAP entitlement; ASSN webhook writes. |
| `subscription_account_tokens` | C | insert-self | `app_account_token(uuid) → user_id`. Written **before** purchase (§7). |
| `social_snapshots` | I | owner reads | follower audit trail; social-sync inserts. |
| `connections` | C | self | social OAuth cache (mostly unused; paste-based flow). |
| `account_deletions`, `app_settings`, `pending_jobs` | I | none | deny-all; service-role/cron only. |

**Enums to model in Kotlin:** `booking_status`, `escrow_status`, `payment_method`,
`cancelled_by_party`, `gig_request_status`, `message_sender_role`, `payout_status`,
`kyc_status`, `media_kind`, `media_aspect`. Plus client-domain `AppRole`,
`SearchSort`, `SocialPlatform`, `MessageSender`, `MessageDelivery`.

---

## 3. `bookings` — constraints the client MUST honor

The DB enforces these; inserts/updates **throw** — catch and surface:

- **`bookings_no_self_booking`** CHECK: `client_id <> artist_id`.
- **`bookings_no_overlap`** GiST EXCLUDE: two active
  (`pending_confirm`/`confirmed`) bookings for one artist can't overlap
  `[start,end)`. Overlapping insert throws.
- **Status state machine:** participants may only walk `pending_confirm→confirmed`,
  `pending_confirm→cancelled`, `confirmed→cancelled`. `completed`/`disputed` and
  all other jumps are **service-role only** (cron/Edge Functions).
- **Insert:** `status ∈ {pending_confirm, confirmed}`; `escrow_status` clamped to
  `held`.
- **Immutable for participants:** `fee_inr, platform_fee_inr, gst_inr, total_inr,
  escrow_status, deleted_at` (guard triggers raise `insufficient_privilege`).
- **Money math (client-side, port verbatim):** `platform = round(fee*0.05)`,
  `gst = round((fee+platform)*0.18)`, `total = fee+platform+gst`.
- **Cancel** goes through the **`cancel-booking` Edge Function** (not a direct
  PATCH) — it applies refund-window policy and writes escrow/payout.

**Repository calls** (`BookingsRepository`):
- create → `from("bookings").insert(row).select().single()` (or `create-booking`
  function when payments go live)
- listForClient → `.select().eq("client_id",uid).order("start_datetime",desc)`
- listForArtist → `.select("*, client:users!client_id(full_name)").eq("artist_id",uid)…`
- cancel → `functions.invoke("cancel-booking", {booking_id, cancelled_by:"client",
  reason:null})` then refetch.

---

## 4. `messages` — the column lockdown (critical)

Migration **0061** column-scopes the anon/authenticated SELECT grant. **You may
select only** `id, thread_id, sender_id, sender_role, body, redacted, sent_at`.
Selecting `body_raw` — or `select("*")` — **returns 403**.

- list → `from("messages").select("id,thread_id,sender_id,body,sent_at")
  .eq("thread_id",tid).order("sent_at")`
- send → `from("messages").insert({thread_id, sender_id, body})
  .select("id,thread_id,sender_id,body,sent_at").single()` — triggers fill
  `body_raw`/`sender_role` and **redact `body` inline**; read back the redacted row.
- Messages are immutable (no UPDATE/DELETE).
- **Redaction lift:** `body` un-redacts only when the thread's booking is
  confirmed/completed **and** belongs to the thread's exact (client_id, artist_id)
  pair. The client also runs its **own** redaction for display safety (same
  regexes) — port them (`domain/chat/Redaction.kt`).

---

## 5. Realtime

**One channel only.** Publication `supabase_realtime` contains exactly
`public.messages`, published with an **explicit column list omitting `body_raw`**.

```kotlin
val flow = supabase.realtime
    .channel("messages:$threadId")
    .postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
        table = "messages"; filter("thread_id", FilterOperator.EQ, threadId)
    }
// collect in a coroutine; decode record → Message; Mutex-gate the post-cancel race
```

Threads/bookings/gig-requests are **polled/refetched**, not streamed. Gate realtime
behind a config flag (iOS `realtimeEnabled`, default on); fall back to
poll-on-send. Tear down channel on screen dispose; bump a generation token to
prevent leaks (port the iOS `OSAllocatedUnfairLock` → `Mutex`).

---

## 6. Storage buckets

Path convention: **`<artist_id>/<…>`**; writes gated by a `storage.objects` policy
(`split_part(name,'/',1)::uuid → owns_artist`). Public buckets have **no SELECT
policy on purpose** (serve via direct public URL; don't LIST).

| Bucket | Public | Limit | MIME | Path |
|---|---|---|---|---|
| `artist-media` | public | 50 MiB | jpeg/png/webp, mp4/quicktime | `<artistId>/<photo\|video>/<uuid>.<jpg\|mp4>` |
| `artist-samples` | public | 10 MiB | audio mpeg/mp4/aac/m4a | `<artistId>/<sampleId>.<ext>` |
| `kyc-documents` | private | 10 MiB | jpeg/png/pdf | `<artistId>/<file>` (dormant) |
| `exports` | private | 50 MiB | json | `<userId>/<ts>.json` (data-export) |

**Client:** upload to your owned path via `storage.from(bucket).upload(path,
bytes/file, upsert=false)`; public URL is hand-built:
`{SUPABASE_URL}/storage/v1/object/public/artist-media/<path>`. Private → signed
URL. **Port the iOS discipline:** pre-write `MAX(position)+1` query, best-effort
storage rollback on the DB-insert failure, `23505` position-collision retry.

---

## 7. Edge Functions (16) + the Android-forced changes

Base: `https://<project>.functions.supabase.co/<name>`.

### User-facing (client calls directly, `Authorization: Bearer <user JWT>`)
| Function | Purpose | Request → Response |
|---|---|---|
| `create-booking` | server-trusted booking (payments-live path; mock today) | `{artist_id, package_index, date, date_raw_iso, time, venue, guests, payment_method, protection_enabled?}` → `{booking_id, fee, platform_fee, gst, total, order_id}` |
| `cancel-booking` | cancel + refund-window policy | `{booking_id, cancelled_by, reason?}` → `{cancelled, refund_inr, refund_percentage, window_used}` |
| `data-export` | DPDP §11 portability (JSON dump) | POST (no body) → inline JSON or `{mode:"signed_url", url, expires_in_seconds:3600}` |
| `delete-account` | DPDP §11 erasure (anonymize + ban) | POST (no body) → `{deleted:true, user_id}` |

### Internal (client never calls; listed so Android knows the triggers)
`payment-webhook` (Razorpay HMAC, dormant), `app-store-notifications` (Apple ASSN,
JWS-verified), `send-push` (**APNs** — see below), `compute-score`,
`compute-score-nightly`, `release-escrow`, `cron-show-reminder`, `process-jobs`,
`social-sync-{spotify,youtube,instagram,nightly}`. These fire from DB triggers /
pg_cron and are **inert until the operator sets secrets + base URL + cron**.

### ⚠️ Two server-side changes Android forces

1. **Push: `send-push` is APNs-only.** It signs an APNs JWT and POSTs to
   `api.push.apple.com`. For Android you must:
   - Add an **FCM-token table** (or an `fcm_token` column on `device_tokens`), and
     upsert from `FirebaseMessagingService.onNewToken`.
   - Add an **FCM sender path** in `send-push` (HTTP v1 API,
     `https://fcm.googleapis.com/v1/projects/<id>/messages:send`, OAuth2 service
     account) alongside APNs, keyed by which token the recipient has.
   - **The event contract is reusable** — the trigger payloads carry
     `artistant_event`, `artistant_booking_id`, `artistant_thread_id`,
     `artistant_request_id`; map them to FCM `data` for deep-linking. Events:
     `message`, `gig_request`, `booking_confirmed_client/artist`,
     `booking_reminder_24h`, `booking_review_request`.

2. **Billing: `app-store-notifications` is Apple-only.** When IAP goes live on
   Android, add a **Google Play RTDN** sibling (Pub/Sub push → an Edge Function),
   verify via the Play Developer API, and upsert `subscriptions`. The
   `subscription_account_tokens` binding pattern is reused: write
   `{app_account_token→user_id}` **before** purchase, then set
   `obfuscatedAccountId` in the billing flow (never trust the raw token
   server-side). Dormant in v1.

Everything else (RLS, PostgREST, Storage, Realtime, the DPDP delete/export
functions, all scoring/reminder cron) is **client-agnostic and reused unchanged**.

---

## 8. RPC functions the client calls (`/rest/v1/rpc/<name>`)

| RPC | Signature | Use |
|---|---|---|
| `search_artists` | `(p_q, p_city, p_categories[], p_min_price, p_max_price, p_min_score, p_event_type, p_sort, p_limit, p_offset, p_after_score, p_after_id, p_after_price)` → rows incl. `rank` | **Primary Discover + Search.** SECURITY INVOKER (RLS → published only). FTS + trigram fallback; keyset pagination on bookability/price, offset on new/relevance. |
| `search_facets` | `()` → `(kind, label, n)` | category + city counts for the browse grid. |
| `handle_is_available` | `(target_handle text)` → bool | signup/wizard handle check (normalizes). |
| `replace_packages` | `(target_artist_id, packages_json)` | atomic packages replace (`owns_artist`). |
| `replace_tech_rider` | `(target_artist_id, items text[])` | atomic tech-rider replace. |
| `replace_samples` | `(target_artist_id, samples_json)` | atomic samples replace. |

Not client-called (service-role/internal): `anonymize_account`, `soft_delete`,
`enqueue_job`, `claim_jobs`, `app_setting`, and all `tg_*` trigger fns. RLS-predicate
helpers (`owns_artist`, `artist_visible`, `in_thread`, `has_active_subscription`)
are used inside policies, not called by the app.

---

## 9. Repository → Supabase call map (client side)

Every repo's exact calls (mirror these in `data/repository/Supabase*`):

- **ArtistsRepository** — 5-table fan-out: `artists`(published, order score) +
  `packages`/`tech_rider`/`samples`/`artist_media(kind=photo)` by `artist_id`;
  writes `artists.upsert(onConflict=id)` + targeted `update`s; `fetchSelfArtistRow`.
  In-memory id-keyed cache (`ensureFull` fetch-on-miss).
- **BookingsRepository** — §3. Calls `CalendarSyncService.ingest` after each op.
- **UsersRepository** — `rpc(handle_is_available)`; `users.select(role,full_name,
  city,handle)`; `users.upsert(onConflict=id)`.
- **MessagesRepository** — §4 + §5; threads select/find-or-create; markThreadRead
  (parallel unread-zero updates).
- **RequestsRepository** — `gig_requests` list (embed `client:users!client_id(full_name)`),
  create, updateStatus (accept/decline/counter); `PGRST116`→notFoundOrUnauthorized.
- **ReviewsRepository** — booking gate read (status=completed) → `reviews.insert`
  (`23505`→alreadyReviewed); list reads denormalized `client_name`.
- **ScoreRepository** — `artists.select(score,metric_*,total_gigs)`;
  `score_history.select(score,computed_at)` last 12mo.
- **ArtistMediaRepository** — `storage.artist-media` upload + `artist_media`
  insert/delete; `nextPosition` query; rollback on `cap_reached`.
- **SamplesRepository** — `storage.artist-samples` upload + insert / `replace_samples`.
- **SearchRepository** — `rpc(search_artists)` + cover batch from `artist_media` +
  `rpc(search_facets)`; caches partials into ArtistsRepository.
- **ArtistLinksRepository / PackagesRepository / TechRiderRepository /
  SavedArtistsRepository** — straightforward CRUD / RPC as tabled above.

---

## 10. "Will bite the Android team" checklist

1. `messages`: **never `select("*")`** — `body_raw` 403s. Explicit columns on read
   and insert-returning.
2. **System-only columns** — don't write `artists.score/metric_*/total_gigs/
   razorpay_account_id`, `bookings.escrow_status/fees/terminal-status/deleted_at`,
   `kyc.status`, any `deleted_at`; guards raise `insufficient_privilege`.
3. **Booking guards** — self-booking CHECK, no-overlap GiST, status state machine.
4. **Push** — `send-push` is APNs-only; add FCM token table + sender (§7).
5. **Denormalized names** — read `reviews.client_name` / `threads.client_name`;
   don't embed `users` (RLS blocks it for non-owners).
6. **Gig-request accept** = a plain PATCH to `status='accepted'` (no RPC); a
   trigger opens the thread.
7. **Payments/IAP dormant** — `escrow_status`, `razorpay_*`, subscriptions inert;
   the **mock booking path is what runs today**.
8. **Lowercase UUIDs**, ISO8601 timestamps (fractional-seconds parser), `en_IN`
   money math client-side.
9. **Operator prerequisites** to activate push/score on prod: set
   `app_settings.edge_function_base_url`, the shared secrets, FCM service account,
   and pg_cron — none of which the client can do.
