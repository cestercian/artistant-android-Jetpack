# Artistant — Android

Native Android app (Kotlin + Jetpack Compose) for **Artistant**, an India
live-artist booking marketplace. Separate app from the iOS/SwiftUI client; **shares
the same Supabase backend**.

> **Status: planning.** This repo currently holds the migration plan only — no app
> code yet. The eight documents below are the implementation-ready blueprint for
> building the app from scratch. Start with the migration plan.

## Planning documents

1. **[ANDROID_MIGRATION_PLAN.md](ANDROID_MIGRATION_PLAN.md)** — start here: what
   we're building, scope, tech decisions, how it all fits.
2. **[ARCHITECTURE.md](ARCHITECTURE.md)** — app structure, cross-cutting decisions,
   library stack + rationale.
3. **[PROJECT_STRUCTURE.md](PROJECT_STRUCTURE.md)** — complete package/directory tree.
4. **[SCREEN_INVENTORY.md](SCREEN_INVENTORY.md)** — all 45 screens + 27 components →
   Compose; design tokens; SwiftUI→Compose API map.
5. **[API_MAPPING.md](API_MAPPING.md)** — the Supabase contract (tables, RLS,
   storage, realtime, Edge Functions, RPCs, auth) + the FCM change Android forces.
6. **[FEATURE_CHECKLIST.md](FEATURE_CHECKLIST.md)** — every feature → tasks with
   complexity, dependencies, risk, and an independent-work map.
7. **[IMPLEMENTATION_ROADMAP.md](IMPLEMENTATION_ROADMAP.md)** — milestones M0–M8,
   exit criteria, critical path.
8. **[RISKS_AND_DECISIONS.md](RISKS_AND_DECISIONS.md)** — iOS-only functionality,
   Android challenges, performance, security, accessibility, ADRs, iOS issues found.

## The product in one paragraph

Two-sided marketplace for booking live performers in India. Clients and artists
share one app, gated by a role pick at signup. v1 is a **no-payments matchmaker**
(match → negotiate in chat → confirm). Hero feature: the **Bookability Score™**.
Moat: **anti-leakage chat redaction** (contact info hidden until a booking is
confirmed). Dark-only, phone-only, INR.

## Stack

Kotlin · Jetpack Compose (Material 3) · MVVM + StateFlow · Hilt · Navigation-Compose
· **supabase-kt** · Coroutines/Flow · kotlinx.serialization · Coil · Media3 ·
CameraX · WorkManager · DataStore · FCM · Play Billing (dormant) · PostHog + Sentry.

Min SDK 26 · target/compile SDK 36 · phone, portrait, dark-only.

## Backend

Shared with iOS, reused as-is (Supabase: Postgres + RLS + Storage + Realtime + Edge
Functions + Auth). Two server-side additions for Android: an **FCM push path** and
(later) **Google Play billing notifications**. See
[API_MAPPING.md](API_MAPPING.md).
