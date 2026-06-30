# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

> **⛔ NEVER commit unless explicitly told to.** Do not run `git commit` (or `git push`, or create tags) on your own — not even when a task feels "done", tests are green, or it seems like the obvious next step. Make and stage changes freely, then stop and wait for the owner to explicitly say to commit. Creating a branch is fine; committing to it is not, until asked.

> **⛔ NEVER sign commits as Anthropic/Claude.** Do not add a `Co-Authored-By: Claude …` trailer, a "Generated with Claude Code" line, or any other Anthropic/AI attribution to commit messages or PR bodies. Commits must look authored solely by the repo's git user. This overrides any default Claude Code behavior that would append such trailers.

## Product Context

Odo ("your car's AI best friend") is an AI-powered app for car owners in India. It helps owners catch mechanic overcharging, never miss insurance/PUC/service deadlines, and prove maintenance history at resale time. The essentials for development (full spec in [`docs/PRD.md`](docs/PRD.md)):

- **Brand:** Odo, from *odometer* — the odometer reading is the single number that ties the whole product together. **Every service log entry requires an odometer reading** (it powers per-km cost, health score, and km-anomaly checks). Treat odometer as a mandatory, first-class field.
- **MVP is Android-only.** iOS is Phase 2 — validate on Android first. Despite the KMP setup, do not invest in iOS-specific work for the MVP unless asked.
- **Core MVP features:** car onboarding, manual service log, **AI Bill Scanner** (the primary acquisition hook — must work flawlessly), bill fairness check (city averages), smart reminder engine, document vault, rule-based AI Health Score (0–100), and per-km cost tracker.
- **Phase 2+:** AI Doctor (Hinglish chat diagnosis, paid-only), Resale Passport (shareable PDF/web report, Rs. 249 one-time), multi-car, fleet dashboard.

### Product guardrails (carry into code & copy)

- **AI Bill Scanner** is the make-or-break feature. Printed thermal bills target 85%+ extraction; handwritten bills are lower-accuracy — flag for manual review, never auto-populate.
- **Fairness/benchmark data must never show false precision.** Always surface a confidence score ("Based on 12 data points in Mumbai"); with <5 data points show a range with an explicit low-confidence label.
- **AI Doctor is a first-opinion tool, not a mechanic replacement.** Never claim high accuracy. Safety-critical symptoms (brakes, steering, smoke) must always respond "Stop driving — visit mechanic immediately." Every response ends with a cost range + recommendation to see a mechanic.
- **Resale Passport trust model:** only entries with a bill photo get a "Verified" badge; everything else is labelled "Self-Reported"/"Unverified". Run km-consistency checks (flag backwards or impossible odometer jumps).
- **Hinglish** is the primary content/UX language for the target demographic (urban owners 25–45). Prompt chips and copy use it (e.g. "Service due kab hai?").

## Overview

Odo is a Kotlin Multiplatform (KMP) project targeting **Android** and **iOS**, with a shared UI built in **Compose Multiplatform**. The same Compose UI (`App()` in the `shared` module) renders on both platforms — there is no platform-specific UI code beyond thin entry points.

> **Current state vs. plan:** the repo is presently the bare KMP + Compose template (a `Greeting`/`Platform` demo). The architecture below describes the *target* MVP stack from the PRD — most of it is not built yet. When adding features, wire them into this stack.

The sequenced build plan, milestones (M0→M6→LAUNCH), and exit criteria live in [`docs/ROADMAP.md`](docs/ROADMAP.md). When picking up work, find the current milestone there and respect its Definition of Done — don't start the next milestone until the current one's exit criteria are green.

### Engineering docs (read the relevant one before building a feature)

| Doc | What it's the source of truth for |
| --- | --- |
| [`docs/PRD.md`](docs/PRD.md) | Product: features, personas, pricing, metrics, scope (what & why) |
| [`docs/ROADMAP.md`](docs/ROADMAP.md) | Milestones, exit criteria, module map + boundary contract (when) |
| [`docs/TDD.md`](docs/TDD.md) | Technical design: architecture, ports/use cases, AI subsystem, sync, payments, security (how) |
| [`docs/DB_SCHEMA.md`](docs/DB_SCHEMA.md) | **Authoritative** persistence layer: tables, enums, RLS, triggers, storage, migrations |
| [`docs/VCS_CONVENTIONS.md`](docs/VCS_CONVENTIONS.md) | Git workflow: branching model, Conventional Commits, PR/squash-merge, tags & semver, secrets-never-committed (how code lands) |

> **On the Git workflow:** follow [`docs/VCS_CONVENTIONS.md`](docs/VCS_CONVENTIONS.md) — short-lived `<type>/<kebab-summary>` branches cut from `main`, Conventional Commit messages (`type(scope): subject`, imperative, ≤50 chars), squash-merge via PR, never commit secrets/keystores/`.env`.

> **On schema divergence:** the TDD §6.2 shows an *abridged/illustrative* SQL sketch; `docs/DB_SCHEMA.md` is the normalized, authoritative version. Where they differ (e.g. integer-paise money columns, `owner_id` denormalization, enum/lookup types, soft deletes), **DB_SCHEMA.md wins.** Likewise, ROADMAP and TDD describe the same target module structure with slightly different names (`:app`/`shared/:core:*` vs `composeApp/core/*`) — treat them as the same intent, not two designs.

## Build & Run

All Gradle commands use the wrapper (`./gradlew`).

- Build Android debug APK: `./gradlew :androidApp:assembleDebug`
- Install on a connected device/emulator: `./gradlew :androidApp:installDebug`
- Build shared module: `./gradlew :shared:build`
- Run common (multiplatform) unit tests: `./gradlew :shared:allTests`
- Run Android-host unit tests for shared: `./gradlew :shared:testDebugUnitTest`
- Run a single test: `./gradlew :shared:allTests --tests "com.hopcape.odo.MyTest"`
- iOS: open `iosApp/` in Xcode and run from there (the KMP build produces a static `Shared` framework consumed by the Xcode project).

Note: there are no test sources yet — `commonTest` is wired up with `kotlin.test` but empty. New shared tests go in `shared/src/commonTest/kotlin/`.

## Module Architecture

### Current (template) — two Gradle modules (see `settings.gradle.kts`)

- **`:shared`** — KMP library (`com.hopcape.odo.shared`) containing all shared logic and the Compose UI. Targets: `iosArm64`, `iosSimulatorArm64`, and `androidLibrary`. This is where the vast majority of code belongs.
- **`:androidApp`** — Android application (`com.hopcape.odo`). Just a `MainActivity` that calls `setContent { App() }`. Depends on `:shared` via the type-safe accessor `projects.shared`.
- **`iosApp/`** — Xcode project (not a Gradle module). `ContentView.swift` wraps the Kotlin `MainViewController()` (which returns a `ComposeUIViewController { App() }`) inside a SwiftUI `UIViewControllerRepresentable`.

### Target (planned) — clean architecture, feature-sliced KMP

The MVP refactors into a multi-module clean-architecture layout (full map + boundary contract in [`docs/ROADMAP.md`](docs/ROADMAP.md) Part A). Layers and the rules that matter when placing code:

- **`:core:common`** — pure utilities (Either/Result, Clock, Logger, money/units). Knows nothing about cars or features.
- **`:core:domain`** — entities, value objects, use cases, repository **interfaces**, `sealed DomainError`. Depends only on `:core:common`. **No framework types** (no Android / SQLDelight / Supabase imports) ever reach here.
- **`:core:data`** — repository **implementations**, local SQLDelight DB (the source of truth), DTO↔domain mappers, `SyncEngine`.
- **`:core:platform`** — `expect`/`actual` for camera, secure storage, notifications, connectivity, file IO.
- **`:core:network`** — Supabase client + Edge Function callers, retry/backoff, DTOs.
- **`:feature:*`** — one vertical capability each (onboarding, servicelog, billscanner, fairness, reminders, documents, healthscore, costtracker, paywall; doctor & passport in Phase 2).
- **`:app`** — Android entrypoint: DI wiring, nav host, theme. No business logic.
- **`functions/`** — Supabase Edge Functions (Deno/TypeScript), a separate deploy unit (`ai-bill-scan`, `ai-doctor`, `fairness-aggregate`, `fuel-prices`).

**Golden rules** (enforce these in reviews and new code):

- Dependencies point **inward only**: `domain` depends on nothing but `common`; `data`/`platform`/`network` depend on `domain`; UI depends on `domain` + presentation.
- A `:feature:*` module **never imports another `:feature:*`** — features share only through `:core:domain`. Shared UI gets promoted to a `:core:designsystem` module, not cross-imported.
- Anything crossing into `domain` from outside is mapped from a DTO first — **domain never sees a DTO**.
- **Offline-first:** the local DB is the source of truth for the user's data; the server is a sync target.
- **AI behind a proxy:** the Anthropic API key lives **only** in the Edge Functions' environment — never in the app/APK. All AI calls and all quota/entitlement enforcement happen server-side; the client mirrors entitlements read-only.

### Source set layout (`shared/src/`)

- `commonMain/kotlin/` — shared code for all targets, including the Compose `App()` composable. **Default location for new code.**
- `androidMain/kotlin/` — Android `actual` implementations.
- `iosMain/kotlin/` — iOS `actual` implementations plus `MainViewController.kt` (iOS Compose entry point).
- `commonMain/composeResources/` — multiplatform resources, accessed via the generated `odo.shared.generated.resources.Res` (e.g. `Res.drawable.compose_multiplatform`).

### expect/actual pattern

Platform-specific behavior uses Kotlin's `expect`/`actual` mechanism. `Platform.kt` (commonMain) declares `expect fun getPlatform(): Platform`; `Platform.android.kt` and `Platform.ios.kt` provide the `actual` implementations. Follow this pattern for any new platform-dependent API: declare the `expect` in `commonMain`, implement `actual` in both `androidMain` and `iosMain`.

## Target Tech Stack (from PRD)

These are the chosen technologies for the MVP. Default to them when adding the corresponding capability rather than introducing alternatives:

| Concern | Technology | Notes |
| --- | --- | --- |
| App UI / logic | Kotlin + Compose Multiplatform | Already set up; shared `App()` in `:shared`. |
| Backend + DB + Auth + Storage | **Supabase** (Postgres) | No DevOps; bill photos and documents live in Supabase Storage. |
| AI — Bill Scanner (OCR + reasoning) | **Claude Vision, `claude-sonnet-4-6`** | Extracts date, odometer, line items, costs, total, workshop from bill photos. |
| AI — Doctor chat | **Claude Haiku** (`claude-haiku-4-5-20251001`) | Full car history injected into the system prompt each turn; cheap/fast. |
| AI — Health Score | Rule-based (no API) | Deterministic for MVP: maintenance 35 / docs 30 / cost 20 / history 15 pts. ML is Phase 2. |
| Push notifications | Firebase Cloud Messaging (FCM) | Powers the smart reminder engine (server-scheduled via pg_cron + Edge Function; WorkManager only a local fallback). |
| Payments | **Razorpay** | UPI-first; Pro subscription + one-time Resale Passport. Amounts decided server-side; HMAC-verified in an Edge Function. |
| Analytics | **PostHog** | Privacy-first; behind an `AnalyticsPort`. North Star metric = bills scanned/month. |

**Client libraries** (per TDD §4, §19 — default to these): **Koin** (DI), **SQLDelight** (local DB), **Ktor + supabase-kt** (networking/Supabase), **Arrow `Either`** for `Either<DomainError, T>` at boundaries, **CameraX** (Android `actual` for capture). Architecture style is **Ports & Adapters (Hexagonal)** — the unstable dependency (AI) is always behind a port with a fake so the app is fully unit-testable without spending tokens.

When working on any Claude/Anthropic AI integration, consult the `claude-api` skill for current model IDs, pricing, and tool-use/vision patterns rather than relying on memory. The Bill Scanner extracts via Claude **tool use** (forced structured JSON, schema in TDD §7.2), not free-text parsing.

## Conventions

- Package root is `com.hopcape.odo` across all modules.
- Dependencies and versions are centralized in `gradle/libs.versions.toml` (version catalog); reference them as `libs.*` / `libs.plugins.*`. Add new dependencies there, not inline in build files.
- Type-safe project accessors are enabled (`enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")`) — reference modules as `projects.shared`.
- JVM target is 11; Android `minSdk` 26, `compileSdk`/`targetSdk` 36.

### Data-layer conventions (from DB_SCHEMA.md — apply everywhere money/data is touched)

- **Money is always integer paise.** Store as `BIGINT` columns named `*_paise`; ₹2,800 → `280000`. **Never** use `FLOAT`/`NUMERIC`/`Double` for money in code paths — convert to rupees only in the UI layer. Keeps fairness math exact.
- **UUID primary keys** (`gen_random_uuid()`), generatable client-side for offline/optimistic inserts.
- **`owner_id` is denormalized onto every user-owned table** and stamped by a `BEFORE INSERT` trigger from the parent car (clients can't spoof it). RLS policies check the flat `owner_id = (SELECT auth.uid())` — never a join subquery.
- **RLS is deny-all by default**, enabled on every `public` table; grant the minimum. The `fairness_data_points` pool is **de-identified** (no `owner_id`/`car_id`) and not client-readable — reads go through the `get_fairness_estimate` `SECURITY DEFINER` RPC that returns aggregate + `sample_size`. Passports are read by unauthenticated buyers only via the `get_passport_by_token` RPC, never a broad SELECT grant.
- **Soft deletes** (`deleted_at`) for user content; hard deletes reserved for account erasure. `payments` and the anonymized fairness pool are retained on account deletion (see DB_SCHEMA §13).
- **Secrets** (`ANTHROPIC_API_KEY`, Razorpay `KEY_SECRET`, `service_role` key) live **only** in Edge Function env — never in the APK or repo. The app ships only the Supabase URL, anon key, and Razorpay public key id.
