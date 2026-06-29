# Odo — Build Plan & Roadmap

> The execution plan for **Odo**. This document turns the PRD's *what/why* and the ADR log's
> *how/why-this-way* into a sequenced, milestone-driven *when*. Follow it top to bottom: ship the
> MVP first, then improve in well-defined phases.

|  |  |
| --- | --- |
| **Project** | Odo — Your Car's AI Best Friend |
| **Owner** | Founder (solo) |
| **Companion docs** | `Odo_PRD_v1_0.md`, `Odo_ADR_log.md`, `Odo_TDD.md` (pending), `Odo_DB_Schema.md` (pending) |
| **Status** | Living document — revise at each milestone review |
| **Last updated** | June 2026 |
| **Cadence** | Solo founder, ~1-week sprints. Dates are relative (W1 = first build week). |

---

## How to use this document

1. **Part A** defines the module map and the boundaries between modules — read it once, refer back when unsure where code belongs.
2. **Part B** is the milestone plan. Each milestone has: **Goal → Task list → Exit Criteria (Definition of Done)**. Do not start the next milestone until the current one's exit criteria are green.
3. **Milestones M0–M6 are the MVP.** Everything after `LAUNCH` is iterative improvement, gated on the PRD's traction metrics.
4. **Checkbox discipline.** Tick tasks as you go. A milestone is "done" only when every exit criterion is met, not when the calendar runs out — but if a milestone slips badly, cut scope, don't move the gate.
5. Every architectural choice referenced here (KMP layout, offline-first, AI proxy, etc.) is justified in the ADR log; this doc assumes those decisions are settled.

---

# Part A — Module Architecture

## A.1 Architectural principles (from the ADRs)

- **KMP, common-first** (ADR-002): business logic in `commonMain`; platform code only where unavoidable.
- **Clean architecture, inward dependencies only** (ADR-012): `domain` depends on nothing; `data` and `platform` depend on `domain`; UI depends on `domain` + presentation. No platform types in `domain`/`commonMain`.
- **Offline-first** (ADR-004): local DB is the source of truth for the user's own data; sync is a background reconciler.
- **AI behind a proxy** (ADR-007): the app never holds the Anthropic key; all AI goes through a Supabase Edge Function that also enforces quotas.
- **Feature-sliced** within those layers: each product capability (scanner, reminders, score, passport…) is a vertical slice so it can be built, tested, and shipped independently.

## A.2 Module map

```
odo/
├─ :app                         # Android entrypoint: Application, DI graph wiring, navigation host, theming
│
├─ shared/                      # KMP — the brain of the product
│  ├─ :core:common              # Result/Either helpers, Clock, Logger, ID gen, Hinglish strings, money/units
│  ├─ :core:domain              # Entities, value objects, sealed DomainError, repository INTERFACES, use cases
│  ├─ :core:data                # Repository IMPLEMENTATIONS, local DB (SQLDelight), DTO<->domain mappers
│  ├─ :core:platform            # expect/actual: camera, secure storage, notifications, connectivity, file IO
│  ├─ :core:network             # Supabase client wrapper, Edge Function callers, retry/backoff, DTOs
│  │
│  ├─ :feature:onboarding       # Car setup, goal selection, first-scan hook
│  ├─ :feature:servicelog       # CRUD service entries, odometer history, attachments
│  ├─ :feature:billscanner      # Camera capture -> proxy call -> review/confirm -> log + fairness contribution
│  ├─ :feature:fairness         # City/locality benchmarking, confidence labels, overpay flagging
│  ├─ :feature:reminders        # Insurance/PUC/service triggers, scheduling, notification content
│  ├─ :feature:documents        # Document vault (RC/PUC/insurance), expiry parsing
│  ├─ :feature:healthscore      # Rule-based 0–100 scoring engine, breakdown, deltas
│  ├─ :feature:costtracker      # Per-km cost calc (km delta + estimated fuel)
│  ├─ :feature:doctor           # (Phase 2) Conversational diagnosis with history injection
│  ├─ :feature:passport         # (Phase 2) Resale Passport generation + PDF/web export
│  └─ :feature:paywall          # Tiers, entitlements, Razorpay flow, server-checked gating
│
└─ functions/                   # Supabase Edge Functions (Deno/TypeScript) — separate deploy unit
   ├─ ai-bill-scan              # Proxies Sonnet Vision, enforces scan quota, returns bill_extraction.v1 JSON
   ├─ ai-doctor                 # (Phase 2) Proxies Haiku, injects car history, returns doctor_response.v1
   ├─ fairness-aggregate        # Reads anonymized pool, returns benchmark + confidence for a query
   └─ fuel-prices               # Weekly cron: fetch & cache city fuel prices
```

## A.3 Module boundaries (the contract table)

| Module | Owns (responsibility) | May depend on | Must NOT do |
| --- | --- | --- | --- |
| `:app` | App startup, DI wiring, nav host, theme | All feature + core modules | Contain business logic or talk to network/DB directly |
| `:core:common` | Pure utilities, no domain meaning | Nothing (stdlib only) | Know about cars, bills, or any feature |
| `:core:domain` | Entities, value objects, use cases, repo **interfaces**, `DomainError` | `:core:common` only | Import Android, SQLDelight, Supabase, or any framework type |
| `:core:data` | Repo **implementations**, local SQLDelight DB, mappers, sync engine | `:core:domain`, `:core:network`, `:core:platform`, `:core:common` | Expose DTOs upward; leak DB/network types into domain |
| `:core:platform` | `expect/actual` for camera, storage, notifications, connectivity | `:core:common` | Hold business rules |
| `:core:network` | Supabase + Edge Function calls, retry, DTOs | `:core:common` | Decide business logic; persist data |
| `:feature:*` | One vertical capability: its UI state, use-case orchestration, screens | `:core:domain` (+ presentation libs); platform via interfaces | Depend on **another** `:feature:*` module directly |
| `functions/*` | Server-side AI proxy, quota enforcement, aggregation, cron | Supabase + Anthropic API | Trust client-supplied entitlement claims |

**The golden rules**

- Features talk to each other **only through `:core:domain`** (shared entities/use cases), never by importing one another. If two features need to share UI, that shared piece is promoted into a small `:core:designsystem` (added when first needed), not cross-imported.
- The **only** place the Anthropic key exists is the Edge Functions' environment (ADR-007).
- The **only** source of truth for the user's data is the local DB; the server is a sync target (ADR-004).
- Anything entering `:core:domain` from outside is mapped from a DTO first — domain never sees a DTO.

## A.4 Cross-cutting concerns

| Concern | Where it lives | Notes |
| --- | --- | --- |
| Dependency injection | `:app` wires it; modules expose factories/Koin modules | Keep graph composition out of features |
| Error model | `sealed DomainError` in `:core:domain`; `Either<DomainError, T>` returns | ADR-012 |
| Sync | `SyncEngine` in `:core:data` | `sync_status` per row, idempotent, last-write-wins |
| Entitlements/quotas | Enforced server-side in `functions/`; mirrored read-only in `:feature:paywall` | Client never the source of truth |
| Analytics | Thin interface in `:core:domain`, PostHog impl in `:core:data`/`:app` | Track North Star: bills scanned/month |
| Config/secrets | Edge Function env + Supabase RLS | No secrets in the APK |

---

# Part B — Build Roadmap (Milestones)

**Legend:** ✅ Exit criterion · ☐ Task · *(P2)* = Phase 2 · *(P3)* = Phase 3

> **MVP = M0 → M6 → LAUNCH.** Target: ~8 build weeks for a focused solo founder. Treat week estimates as effort, not a promise; the gates are what matter.

---

## M0 — Foundation & Scaffolding *(W1)*

**Goal:** A running, empty-but-correct skeleton: KMP modules wired, DI in place, Supabase connected, CI building. No product features yet — just the rails everything else rides on.

**Tasks**

- ☐ Create KMP project with the module structure from A.2 (`:app`, `shared/*`, stub `iosMain`).
- ☐ Set up DI (Koin), navigation host, base theme/design tokens in `:app`.
- ☐ Stand up Supabase project: auth, Postgres, Storage buckets, empty Edge Function deploy pipeline.
- ☐ Wire `:core:network` Supabase client + a `ping` Edge Function to prove the client→function path.
- ☐ Add SQLDelight to `:core:data` with a throwaway table to prove local DB works.
- ☐ Configure CI (build + lint + unit test on push); set up signed debug build + internal-testing track on Play Console.
- ☐ Establish `:core:common` (Either/Result helpers, Clock, Logger, money/unit value types).
- ☐ Define the `sealed DomainError` hierarchy and the repository-interface conventions in `:core:domain`.

**Exit criteria — Definition of Done**

- ✅ App launches to a blank home screen on a device, built by CI.
- ✅ A round-trip works: app calls the `ping` Edge Function and renders the response.
- ✅ A row can be written to and read from the local SQLDelight DB.
- ✅ Module dependency rules compile-enforced (domain has zero framework imports).
- ✅ Internal-testing build uploadable to Play Console.

---

## M1 — Car Onboarding & Service Log Core *(W2)*

**Goal:** A user can add their car and manually log a service entry, fully offline. This is the data spine every other feature reads from.

**Tasks**

- ☐ `:core:domain`: `Car`, `ServiceLogEntry`, `OdometerReading` entities + value objects; `CarRepository`, `ServiceLogRepository` interfaces; `AddCarUseCase`, `AddServiceLogUseCase` (with odometer-monotonicity validation returning `Either`).
- ☐ `:core:data`: SQLDelight tables for cars + service logs; repo implementations; DTO↔domain mappers; `sync_status` column groundwork.
- ☐ `:feature:onboarding`: 3-screen flow (car details dropdowns, optional history skip, goal selection) per PRD 5.1.
- ☐ `:feature:servicelog`: list + add/edit entry screens; odometer + cost + service-type fields; attachment placeholder.
- ☐ Seed make/model/year dropdown data (top brands first).
- ☐ Unit tests for domain validators (odometer can't go backwards, mandatory fields).

**Exit criteria — Definition of Done**

- ✅ New user completes onboarding in under ~90 seconds and lands on home with their car set up.
- ✅ User can create, view, and edit a service log entry offline; it persists across app restarts.
- ✅ Invalid odometer progression is rejected with a clear message (covered by tests).
- ✅ Goal selection routes to the correct starting surface (PRD 5.1).

---

## M2 — Bill Scanner (the hook) + AI proxy *(W3–W4, the riskiest milestone)*

**Goal:** The core WOW moment — photograph a bill, AI extracts it, user confirms in one tap, entry auto-populates. This is the make-or-break feature (ADR-005); budget extra time here.

**Tasks**

- ☐ `functions/ai-bill-scan`: proxy to Claude Sonnet Vision; system prompt; enforce `bill_extraction.v1` JSON-only output (ADR-006); per-user scan-quota enforcement (ADR-007); usage logging + hard spend cap + alert.
- ☐ Define and document the `bill_extraction.v1` schema (date, odometer, line items[], total, workshop, per-field confidence).
- ☐ `:core:platform`: camera capture (Android `actual`), image compression before upload.
- ☐ `:core:network`: typed caller for `ai-bill-scan`; retry-once-on-bad-JSON then fail gracefully.
- ☐ `:feature:billscanner`: capture → loading ("scanning…") → editable review screen pre-filled from extraction → one-tap confirm → writes service log entry.
- ☐ Confidence gate: handwritten/low-confidence → flag for manual review, never silent auto-commit.
- ☐ Offline behaviour: "scan queued, will process when online."
- ☐ Manual-entry fallback always reachable.

**Exit criteria — Definition of Done**

- ✅ A printed thermal bill scans and pre-fills a reviewable service entry; confirm creates the log in one tap.
- ✅ Extraction returns valid `bill_extraction.v1` JSON; malformed responses degrade to manual entry without crashing.
- ✅ Anthropic key is provably **not** in the APK; all calls go through the Edge Function.
- ✅ Free-tier scan quota is enforced **server-side** (bypass attempt from a tampered client fails).
- ✅ Measured extraction accuracy on a 20-bill test set is recorded as a baseline (target ≥85% printed).
- ✅ A spend cap + alert is live on the function.

---

## M3 — Fairness Engine + Health Score *(W5)*

**Goal:** Turn logged/scanned data into the two signals users care about: "did I overpay?" and "how healthy is my car?"

**Tasks**

- ☐ `functions/fairness-aggregate`: read the anonymized, append-only pool (ADR-013); return benchmark + `confidence (N data points)` for {service_type, city}.
- ☐ Seed fairness pool with scraped/manual city averages for top 10 cities (PRD 5.2 cold-start).
- ☐ Bill scanner writes an anonymized data point (service_type, normalized_amount, city) on each confirmed scan — no user id.
- ☐ `:feature:fairness`: overpay overlay ("You paid ₹2,800 · Mumbai avg ₹2,100 · possible overpay ₹700") with honest confidence label; suppress precision below min-sample threshold (PRD Open Q1 — set the number).
- ☐ `:feature:healthscore`: rule-based 0–100 engine (maintenance 35 / docs 30 / cost 20 / history 15) behind a `HealthScoreCalculator` interface (ADR-008); breakdown UI; band labels.
- ☐ Tests for the scoring function (deterministic, explainable per component).

**Exit criteria — Definition of Done**

- ✅ After a scan, a fairness verdict shows with a visible confidence label; low-data cities show a range, not false precision.
- ✅ Each confirmed scan contributes exactly one anonymized pool row (verified: no user id present).
- ✅ Health Score renders a number + full breakdown; recomputing on the same data yields the identical score.
- ✅ Score breakdown names the specific gap ("Documentation 24/30 — PUC missing").

---

## M4 — Reminders, Document Vault & Cost Tracker *(W6)*

**Goal:** The between-events retention loop — never miss insurance/PUC, store key docs, and see true cost-per-km with zero extra logging.

**Tasks**

- ☐ `:feature:documents`: vault for RC/PUC/insurance (3-doc MVP limit); store in Supabase Storage; parse/capture expiry dates.
- ☐ `:feature:reminders`: trigger engine for insurance/PUC/service-due (km + time) per PRD 5.3; schedule via platform notifications; FCM setup for push.
- ☐ `functions/fuel-prices`: weekly cron caching city fuel prices.
- ☐ `:feature:costtracker`: per-km calc from odometer delta + estimated fuel (ADR-011); "estimated fuel" clearly labelled; comparison-to-city-average line.
- ☐ Reminder content/copy in Hinglish; lead-time windows (30/7/1 day, etc.).
- ☐ Inactivity reminder (7-day) wired to analytics.

**Exit criteria — Definition of Done**

- ✅ A document with an expiry date generates push reminders at the correct lead times (verified by fast-forwarding the clock in tests).
- ✅ Service-due (km threshold) and time-based reminders fire correctly.
- ✅ Cost tracker shows ₹/km (maintenance + estimated fuel) with the estimate clearly labelled, plus a city-average comparison.
- ✅ Document vault stores and retrieves files; enforces the 3-doc free limit.

---

## M5 — Sync, Auth, Paywall & Entitlements *(W7)*

**Goal:** Make it a real multi-device, monetizable product: accounts, reliable background sync, and server-checked tiers.

**Tasks**

- ☐ Auth: Supabase auth (phone/email) + onboarding integration; design RLS policies so users read only their own rows (ADR-003).
- ☐ `SyncEngine` in `:core:data`: idempotent push/pull, `sync_status` reconciliation, last-write-wins, retry/backoff (ADR-004); conflict + offline-edit handling.
- ☐ `:feature:paywall`: Free/Pro tiers + entitlement model; gate Bill Scanner at 3 free scans, 20 logs/month — **enforced in the Edge Functions**, mirrored read-only in client.
- ☐ Razorpay integration for Pro ₹149/mo (ADR-010) — **after** resolving the Play Billing policy question (blocking; see Risks).
- ☐ Paywall placement: trigger on 3rd scan when overpay detected (PRD 6.3).
- ☐ Analytics events for funnel + North Star (bills scanned, scan→paywall→convert).

**Exit criteria — Definition of Done**

- ✅ A user signs in, and data syncs across a reinstall/second device with no loss or duplication.
- ✅ RLS verified: a user cannot read another user's rows (tested).
- ✅ Free limits enforced server-side; a tampered client cannot exceed them.
- ✅ A test payment upgrades the account to Pro and unlocks gated features.
- ✅ Play Billing policy decision is resolved and reflected (ADR-010 confirmed or superseded).

---

## M6 — Hardening, QA & Store Readiness *(W8)*

**Goal:** Ship-quality. Fix the rough edges, satisfy Play Store mandates, and get the listing live for internal/closed testing.

**Tasks**

- ☐ End-to-end manual QA pass across all flows on 3–4 real devices (varied Android versions/screen sizes).
- ☐ Crash/ANR pass; handle empty states, no-network states, permission denials.
- ☐ Tighten Bill Scanner accuracy/edge cases from the M2 baseline; improve confidence prompts.
- ☐ **Play Store mandates**: Privacy Policy, Terms + AI/data disclaimers, Data Safety form, account/data-deletion flow (Tier-2 docs).
- ☐ Store listing assets: icon, screenshots, "Mechanic ne loot liya" hook copy, description.
- ☐ Onboarding polish to hit the sub-90-second target reliably.
- ☐ Cost monitoring dashboard for AI spend; verify spend cap behaviour.
- ☐ Seed 10 real test users (PRD GTM Month 3).

**Exit criteria — Definition of Done**

- ✅ No known crashes in core flows; all primary flows pass on the device matrix.
- ✅ Privacy Policy, ToS, Data Safety, and data-deletion flow are live and compliant.
- ✅ Store listing complete; app published to closed/internal testing track.
- ✅ 10 real users onboarded and actively logging/scanning.
- ✅ AI spend cap + monitoring confirmed working under real usage.

---

## 🚀 LAUNCH GATE — End of MVP

Before any paid marketing, confirm:

- ✅ Bill Scanner works flawlessly for the common case (the one feature that must not fail).
- ✅ The full loop works: onboard → scan → fairness verdict → health score → reminder → (paywall).
- ✅ Legal/store compliance done.
- ✅ Analytics capturing the North Star (bills scanned/month) and the conversion funnel.

**Then begin PRD GTM: content (W-of-mouth, Reels), service-center seeding, and only after early retention signal, paid ads.**

---

## Post-MVP Roadmap (iterative improvement)

These phases are **gated on the PRD's traction metrics**, not the calendar. Do not build them if the Month-3 decision gate (Day-30 retention ≥25%, bill scans growing) isn't green — fix retention first.

### Phase 2A — AI Doctor *(P2, ~Month 4, after history accumulates)*

- **Goal:** Conversational diagnosis that uses each user's real logged history.
- **Tasks:** `functions/ai-doctor` (Haiku proxy, history injection, `doctor_response.v1`, safety guardrails: brakes/smoke/steering → "stop driving, see mechanic"); `:feature:doctor` chat UI with Hinglish quick-prompt chips; Pro-gated.
- **Exit:** ✅ Doctor answers reference the user's actual service history; every safety-critical query redirects to a mechanic; responses end with a cost range + disclaimer; Pro-gated and within AI spend cap.

### Phase 2B — Resale Passport *(P2, ~Month 4–5)*

- **Goal:** Shareable, trust-bearing maintenance proof — the highest-value one-time purchase.
- **Tasks:** `:feature:passport` report assembly (car details, health score, Verified vs Self-Reported timeline, km-consistency/anomaly check, doc validity, honest disclaimer); PDF + web-link export; Razorpay ₹249 one-time unlock (ADR-009); run the one-time-vs-bundle pricing experiment (PRD Open Q2).
- **Exit:** ✅ A Passport generates as PDF + shareable web link viewable without installing the app; Verified badge requires a bill photo; km anomalies are flagged; ₹249 unlock works; pricing experiment instrumented.

### Phase 2C — Multi-car support *(P2)*

- **Goal:** Let an owner manage more than one vehicle.
- **Exit:** ✅ A user adds/switches between multiple cars; all features scope correctly per selected car.

### Phase 2D — iOS app *(P2, Month 5+, only on Android traction)*

- **Goal:** Reach iOS users by reusing `shared/*`.
- **Tasks:** implement `iosMain` `actual`s (camera, storage, notifications); SwiftUI (or CMP) UI over the existing KMP domain/data.
- **Exit:** ✅ Feature parity with Android MVP on iOS, with no changes required to `:core:domain`.

### Phase 3 — Scale & ecosystem *(P3)*

- Locality-level (not just city) fairness data; ML-enhanced Health Score (swap behind the existing interface, ADR-008); **Fleet dashboard** (₹999/mo, Vikram persona) with per-car P&L; workshop directory (supply-side onboarding); affiliate integrations live (insurance/service/loan). Each ships as its own milestone with its own task list + exit criteria appended here.

---

## Risk register (carry into every milestone review)

| Risk | Surfaces in | Mitigation / gate |
| --- | --- | --- |
| **Play Billing vs Razorpay** for in-app subscriptions (ADR-010) | M5 | **Blocking** — resolve before building the payment flow; supersede ADR-010 if needed |
| Bill Scanner accuracy on messy bills (ADR-005) | M2 | Confidence gate + manual fallback; track accuracy from a real test set; this is the milestone to over-invest in |
| Fairness data sparse at launch (ADR-013) | M3 | Seed top-10 cities; always show confidence; range-not-precision below threshold |
| Logging fatigue | M1–M4 | Scanner = near-zero-effort entry; only odometer mandatory |
| AI cost runaway | M2, M5 | Hard spend cap + alert in Edge Functions; quotas enforced server-side |
| Solo-founder scope creep | every milestone | Don't move gates — cut scope; post-MVP phases gated on traction, not eagerness |

---

*Odo Build Plan & Roadmap — revise the relevant milestone (or append a Phase-3 milestone) at each review; keep exit criteria honest.*
