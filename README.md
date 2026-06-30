<div align="center">

# 🚗 Odo

### *Your car's AI best friend*

An AI-powered companion for car owners in India — catch mechanic overcharging, never miss insurance / PUC / service deadlines, and prove your maintenance history at resale time.

[![Kotlin Multiplatform](https://img.shields.io/badge/Kotlin-Multiplatform-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/docs/multiplatform.html)
[![Compose Multiplatform](https://img.shields.io/badge/UI-Compose%20Multiplatform-4285F4?logo=jetpackcompose&logoColor=white)](https://www.jetbrains.com/compose-multiplatform/)
[![Platform](https://img.shields.io/badge/Platform-Android%20(MVP)%20%C2%B7%20iOS%20(Phase%202)-3DDC84?logo=android&logoColor=white)](#)
[![Backend](https://img.shields.io/badge/Backend-Supabase-3FCF8E?logo=supabase&logoColor=white)](https://supabase.com/)
[![AI](https://img.shields.io/badge/AI-Claude-D97757?logo=anthropic&logoColor=white)](https://www.anthropic.com/)

</div>

---

## Why "Odo"?

The name comes from **odometer** — the one number that ties the whole product together. Every service log entry requires an odometer reading, because it powers per-km cost tracking, the health score, and km-anomaly checks. Odometer is a mandatory, first-class field everywhere in Odo.

## The problem

Car owners in India routinely:

- 💸 **Overpay** mechanics with no way to know the fair price.
- 📅 **Miss deadlines** for insurance, PUC, and scheduled service.
- 📂 **Lose their service history**, which kills resale value and trust.

Odo fixes all three with an offline-first app and a Claude-powered AI core.

## Core features (MVP)

| Feature | What it does |
| --- | --- |
| 📷 **AI Bill Scanner** | The primary hook. Snap a service bill — Claude Vision extracts date, odometer, line items, costs, total, and workshop into structured data. |
| 🧾 **Manual Service Log** | Log every service with a mandatory odometer reading. |
| ⚖️ **Bill Fairness Check** | Compare what you paid against city averages, always with an honest confidence score (never false precision). |
| 🔔 **Smart Reminders** | Server-scheduled push for insurance, PUC, and service due dates. |
| 🗄️ **Document Vault** | Keep RC, insurance, PUC, and bills in one secure place. |
| 💯 **AI Health Score** | A deterministic, rule-based 0–100 score (maintenance / docs / cost / history). |
| ⛽ **Per-km Cost Tracker** | Know exactly what your car costs you per kilometre. |

**Phase 2+:** AI Doctor (Hinglish chat diagnosis), Resale Passport (shareable verified PDF/web report), multi-car, and a fleet dashboard.

> **Language:** Odo speaks **Hinglish** — the natural language of its target users (urban owners, 25–45). e.g. *"Service due kab hai?"*

## Tech stack

| Concern | Technology |
| --- | --- |
| UI & app logic | **Kotlin + Compose Multiplatform** (shared UI) |
| Backend · DB · Auth · Storage | **Supabase** (Postgres) |
| AI — Bill Scanner (OCR + reasoning) | **Claude Vision** (`claude-sonnet-4-6`) via forced tool-use |
| AI — Doctor chat (Phase 2) | **Claude Haiku** (`claude-haiku-4-5`) |
| AI — Health Score | Rule-based, deterministic (no API) |
| Push | Firebase Cloud Messaging |
| Payments | **Razorpay** (UPI-first) |
| Analytics | **PostHog** |
| DI · Local DB · Networking | **Koin** · **SQLDelight** · **Ktor + supabase-kt** |

> 🔒 **AI keys never ship in the app.** All Claude calls and quota/entitlement enforcement run server-side in Supabase Edge Functions; the client only mirrors entitlements read-only.

## Architecture

Odo targets a **clean, feature-sliced Kotlin Multiplatform** layout following **Ports & Adapters (Hexagonal)**. Dependencies point **inward only**, and `:feature:*` modules never import each other — they share only through `:core:domain`.

```
:app                 Android entrypoint — DI, nav host, theme. No business logic.
:core
  :common            Pure utilities (Result, Clock, money/units).
  :domain            Entities, use cases, repository interfaces, DomainError. No framework types.
  :data              Repository impls, SQLDelight DB (source of truth), mappers, SyncEngine.
  :platform          expect/actual for camera, secure storage, notifications, file IO.
  :network           Supabase client + Edge Function callers.
  :navigation        Navigation 3 wrapper.
:feature
  :onboarding  :servicelog  :billscanner  :fairness
  :reminders   :documents   :healthscore  :costtracker  :paywall
functions/           Supabase Edge Functions (Deno/TypeScript) — separate deploy unit.
```

**Principles:**
- **Offline-first** — the local SQLDelight DB is the source of truth; the server is a sync target.
- **Money is always integer paise** (`*_paise`, `BIGINT`) — never floats — converted to rupees only in the UI.
- **AI behind a port** with a fake, so the app is fully unit-testable without spending tokens.

> ⚠️ **Project status:** the repo is being built out from a KMP + Compose template toward the MVP architecture above. Module scaffolding is landing milestone by milestone — most feature modules are not implemented yet.

## Getting started

All commands use the Gradle wrapper.

```bash
# Build the Android debug APK
./gradlew :androidApp:assembleDebug

# Install on a connected device / emulator
./gradlew :androidApp:installDebug

# Build the shared module
./gradlew :shared:build

# Run shared multiplatform unit tests
./gradlew :shared:allTests
```

**iOS:** open `iosApp/` in Xcode and run from there (the KMP build produces a `Shared` framework consumed by the Xcode project). *iOS is Phase 2 — the MVP validates on Android first.*

**Requirements:** JDK 11+, Android `minSdk` 26 / `compileSdk` 36, and Android Studio (latest stable) with the Kotlin Multiplatform plugin.

## Documentation

The source-of-truth engineering docs live in [`docs/`](docs/). Read the relevant one before building a feature:

| Doc | Source of truth for |
| --- | --- |
| [`PRD.md`](docs/PRD.md) | Product — features, personas, pricing, metrics, scope |
| [`ROADMAP.md`](docs/ROADMAP.md) | Milestones, exit criteria, module map |
| [`TDD.md`](docs/TDD.md) | Technical design — architecture, ports, AI subsystem, sync, payments |
| [`DB_SCHEMA.md`](docs/DB_SCHEMA.md) | Authoritative persistence layer — tables, enums, RLS, storage |
| [`VCS_CONVENTIONS.md`](docs/VCS_CONVENTIONS.md) | Git workflow — branching, Conventional Commits, PR/squash-merge |

## Contributing

This project follows the conventions in [`VCS_CONVENTIONS.md`](docs/VCS_CONVENTIONS.md):

- Short-lived `<type>/<kebab-summary>` branches cut from `main`.
- **Conventional Commits** (`type(scope): subject`, imperative, ≤50 chars).
- Squash-merge via PR.
- **Never** commit secrets, keystores, or `.env` files.

---

<div align="center">
<sub>Built with Kotlin Multiplatform · Compose · Supabase · Claude</sub>
</div>