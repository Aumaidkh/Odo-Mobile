# App Status Gate — Maintenance Mode & Minimum Supported Version

**Status: implemented** on `feat/app-status-gate` (2026-08-09). This document is both the
design record and the operator runbook — read §10 first if you just need to open or close a
maintenance window.

## Context

Before this, Odo had no way to stop a running client. Every constant in the app was
compile-time (`TripTrackerConfig`, `AlwaysProEntitlement`, `FreeTierScanAllowance`). If a
backend migration needed a write freeze, or a shipped build turned out to corrupt data, the
only lever was publishing a new APK and waiting for organic adoption.

This is the one control that has to exist before launch: a remotely operated gate that can
(a) refuse to run builds below a minimum version, and (b) put the app into a maintenance
state during a backend window. It is deliberately the *only* switch built here — per-feature
kill switches (trip tracking, bill scanner, sync, log upload) are later work that will reuse
this plan's port (`AppStatusSource`) and adapter shape.

Outcome: an operator changes a value in the Firebase console and every client picks it up
within one fetch, with no release.

---

## §1 Locked decisions

| # | Decision | Consequence |
|---|----------|-------------|
| D1 | **Firebase Remote Config is the source of truth**, not Supabase. | The switch survives a total Supabase outage. The switch is inert on builds with no `google-services.json` (CI, fresh checkouts) — the same posture analytics/Crashlytics already have. |
| D2 | **Maintenance has two severities, chosen remotely: `DEGRADED` and `FULL_BLOCK`.** | `DEGRADED` = network work (sync) stops, the local app keeps working with a banner. `FULL_BLOCK` = a full-screen non-dismissable stop. Odo is offline-first, so a routine backend window should not lock an owner out of their own local data. |
| D3 | **Version check is one hard tier only** — `minSupportedVersionCode`. | Below it: a non-dismissable "update required" screen with a Play Store button. No soft/recommended-update nudge. |
| D4 | **Min-version blocks from cache; maintenance does not.** | The app's own version is a local fact, so an old build stays old offline and blocking it is always correct. A maintenance flag is only honoured while the snapshot behind it is recent (§4) — otherwise an owner who went offline during a window would be locked out with no way to ever learn it ended. |

Decided from convention:

- **Three flat Remote Config keys, not one JSON blob** — see §3.
- **Fail open, always.** Fetch failure, missing Firebase, unparsable value, missing key —
  all resolve to "app runs normally".
- **`min_supported_version_code` defaults to `0`**, which can never block anything.
- **Every blocked screen has a "Try again" button** that re-fetches.
- **Update-required outranks maintenance.** If both apply, the update screen shows.
- **No new persistence.** Freshness comes from the Remote Config SDK's own last-fetch
  timestamp; there is no extra file, table, or DataStore.

One call made during implementation, adjusted from the original design:

- **The session `SyncGate` is qualified in `coreDataModule` itself**, not in `supabaseModule`
  — the original plan assumed `supabaseModule` bound `SyncGate`; it never did. The only
  binding was `coreDataModule`'s own `SessionSyncGate`, so both the qualified session gate
  and the decorator that wraps it live in the same module, in order. No cross-module
  later-wins risk to manage.

Revisited after first shipping a flat interval (D5 below reverses that call):

| # | Decision | Consequence |
|---|----------|-------------|
| D5 | **`minimumFetchIntervalInSeconds` is 60s on debug builds, 3600s otherwise**, decided in Kotlin from one global `BuildKonfig.BUILD_TYPE`. | `:core:common` — the one module everything else already depends on — applies the `buildkonfig` Gradle plugin and generates a single public `BuildKonfig.BUILD_TYPE: String` (`"debug"` \| `"release"`, extensible to more). `firebaseRemoteConfigModule` reads it and branches in ordinary Kotlin; the *interval value itself* is never Gradle config, and no other module needs to repeat this setup to ask the same question. See §6. |
| D6 | **Android reads its local (pre-fetch) defaults from `res/xml/remote_config_defaults.xml`**, not the Kotlin `REMOTE_DEFAULTS` map. | Firebase's own documented convention for "what a fresh install answers before its first fetch" — `FirebaseRemoteConfig.setDefaultsAsync(int)` against a checked-in XML resource, discoverable by any Android engineer without reading this module's Kotlin. `REMOTE_DEFAULTS` remains the source for every other platform (iOS, and documentation) and must be kept in sync by hand — both files say so at the top. |

---

## §2 Where things sit

```
:core:domain                     Pure kernel. No framework, no observability.
  appstatus/AppStatus.kt              MaintenanceSeverity, AppStatus
  appstatus/AppAvailability.kt        sealed: Allowed | DegradedByMaintenance | Blocked
  appstatus/AppStatusSource.kt        port — fetch one snapshot
  appstatus/AppStatusProvider.kt      port — observe the current verdict, refresh on demand
  appstatus/AppAvailabilityPolicy.kt  evaluateAvailability() — the one place rules live

:core:common                     One global build-type identity, for every module (D5)
  build.gradle.kts                    buildkonfig {} — BUILD_TYPE per flavor, heuristic below
  (generated) BuildKonfig.BUILD_TYPE  "debug" | "release" — public, commonMain

:core:platform                   AppInfo gains versionCode
  commonMain/app/AppInfo.kt           + val versionCode: Long
  androidMain/app/AndroidAppInfo.kt   longVersionCode (API 28+) / versionCode (below)
  iosMain/app/IosAppInfo.kt           CFBundleVersion, parsed to Long

:core:data
  appstatus/DefaultAppStatusProvider.kt          StateFlow + refresh + self-decaying recheck
  appstatus/AlwaysAvailableAppStatusSource.kt    dev stub — blocks nothing
  appstatus/MaintenanceAwareSyncGate.kt          SyncGate decorator (§5)
  appstatus/observability/AppStatusTelemetry.kt + AppStatusAnalyticsEvents.kt
  CoreDataModule.kt                  qualified session SyncGate + the decorator above it

:infrastructure:firebase:remoteconfig        NEW module, mirrors :infrastructure:firebase:analytics
  FirebaseRemoteConfigGateway.kt       narrowed SDK seam + RealFirebaseRemoteConfigGateway
  RemoteConfigAppStatusSource.kt       the three keys → AppStatus
  FirebaseRemoteConfigModule.kt        firebaseRemoteConfigModule (public Koin val)
  LocalRemoteConfigDefaults.kt         expect — applies the pre-fetch defaults (D6)
    androidMain: .android.kt             actual — res/xml/remote_config_defaults.xml
    iosMain: .ios.kt                     actual — REMOTE_DEFAULTS directly
  androidMain/res/xml/remote_config_defaults.xml   Android's local defaults resource (D6)

:core:designsystem
  component/OdoBanner.kt               the slim top strip DEGRADED renders

:shared                          The app shell decides what to render.
  AppGate.kt                           shouldBlock() + AppBlockedScreen
  App.kt                               wraps OdoAppContent in the gate, renders the banner
  di/KoinInit.kt                       firebaseRemoteConfigModule + cold-start refresh
  di/OdoAnalyticsEvents.kt             + appStatusAnalyticsEvents
  commonMain/composeResources/values/strings.xml   as_* — the gate's copy

:androidApp
  OdoApplication                       AppStatusProvider.refresh() on every foreground
```

`:core:common` gained one addition used by the Firebase adapter:
`runCatchingCancellableSuspend` — the `suspend`-block sibling of `runCatchingCancellable`
(a distinct name, not an overload — a lambda with no suspend call inside it type-checks
against either signature, which the compiler can't resolve).

No module depends outward. `:core:domain` holds the types and ports only.

---

## §3 The remote contract

Three Remote Config parameters, set in the Firebase console (Remote Config → your project).
**These names are a shipped contract — renaming one silently disarms every installed client.**

| Key | Type | Default | Meaning |
|-----|------|---------|---------|
| `min_supported_version_code` | Number | `0` | Builds with `versionCode` **below** this are blocked. `0` blocks nothing. |
| `maintenance_mode` | String | `off` | `off` \| `degraded` \| `full_block`. Anything unrecognised (including a typo) is treated as `off` — fail open. |
| `maintenance_message` | String | `""` | Optional operator copy shown instead of the bundled default. Blank means use the bundled string. |

These are also the client's SDK defaults (`RemoteConfigAppStatusSource.REMOTE_DEFAULTS`), so
a fresh install already has a safe, non-blocking answer before its first network call.

---

## §4 The decision

`evaluateAvailability(status, currentVersionCode, now, maintenanceTrustWindow = 30.minutes)`
in `AppAvailabilityPolicy.kt` is the whole rule set:

1. `currentVersionCode < minSupportedVersionCode` → `Blocked.UpdateRequired`. Applies
   regardless of freshness (D4) — a stale snapshot cannot make an old build new.
2. Otherwise, if the snapshot's fetch time is null or older than 30 minutes → `Allowed`.
   This is D4's other half: an unconfirmed maintenance flag cannot hold an offline owner
   hostage.
3. `FULL_BLOCK` → `Blocked.Maintenance`; `DEGRADED` → `DegradedByMaintenance`; `OFF` →
   `Allowed`.

`DefaultAppStatusProvider` re-applies this on its own 1-minute cadence (not just on
`refresh()`), so a stale block clears itself within about a minute of going stale even with
no further trigger — the case a full-screen block sitting untouched would otherwise never
release from.

---

## §5 The seams that consume it

- **App shell (`:shared/App.kt`, `AppGate.kt`).** `shouldBlock()` gates the whole
  composition above the nav host — nothing renders that could be navigated past a block.
  `DegradedByMaintenance` instead renders `OdoBanner` above the normal content (a
  `Column` + weighted `Box`, so the tree shape never changes between the banner and no-banner
  cases — only its content does).
- **Sync (`:core:sync` `SyncGate`).** `MaintenanceAwareSyncGate` wraps the session gate:
  `DEGRADED` and `FULL_BLOCK` both close it *before* the session gate's own adoption side
  effect runs, so nothing pushes into a backend mid-migration. Local writes keep stamping
  `updated_at` and stay `PENDING`; the queue drains once the window ends. Auth/sign-in is
  **not** gated — it fails with an ordinary network error during a window, which is
  acceptable; gating it is a separate seam with its own copy, not built here.
- **Refresh triggers**: cold start (`initKoin`'s startup coroutine), every foreground
  (`OdoApplication`'s `ProcessLifecycleOwner` observer), the 1-minute self-recheck (§4), and
  the blocked screen's "Try again" button.

---

## §6 The Firebase adapter

`RealFirebaseRemoteConfigGateway` mirrors `RealFirebaseAnalyticsGateway` /
`RealFirebaseCrashlyticsGateway` exactly: lazy resolution, every call wrapped (never a throw
into the caller), one diagnostic on first failure rather than one per call. A build with no
`google-services.json` degrades to "unavailable" and the app runs normally.

Freshness (`lastFetchAt`) reads the SDK's own `FirebaseRemoteConfig.info.fetchTime` —
the timestamp of the last *successful* network fetch, which still applies even when the most
recent `fetchAndActivate()` call was throttled or served from cache. This is more correct
than stamping the local clock at call time, which would overstate freshness on a throttled
call.

**The fetch interval (D5).** `:core:common/build.gradle.kts` applies the `buildkonfig`
plugin once, for the whole app, and generates one public `BuildKonfig.BUILD_TYPE: String`
(`"release"` by default, `"debug"` on the `debug` flavor) — not a per-module value, and not
the interval itself. `firebaseRemoteConfigModule` (`:infrastructure:firebase:remoteconfig`)
is just a consumer: it reads `BuildKonfig.BUILD_TYPE` and picks `60L`/`3_600L` in ordinary
Kotlin. Any other module that ever needs to know debug-vs-release asks the same one object,
rather than repeating this Gradle setup.

Nothing in `:core:common`'s own build has an Android build type to read directly
(`com.android.kotlin.multiplatform.library` — the KMP androidLibrary target every module
here uses — has no `buildTypes` block; only `:androidApp`, a classic
`com.android.application` module, has a real debug/release distinction). Its build script
picks the buildkonfig flavor with a **heuristic**, not a guarantee: if nothing was passed via
`-Pbuildkonfig.flavor=…`, it checks whether any requested Gradle task name contains
`"Debug"` (`assembleDebug`, `installDebug`, `testAndroidHostTest`'s siblings, …) and defaults
to `debug` only then — anything else, including an unrecognised task name, falls to the safe
direction: `release`. A pipeline that genuinely wants the debug flavor for something the
heuristic doesn't catch must pass `-Pbuildkonfig.flavor=debug` explicitly.

**Local defaults (D6).** On Android, `configured()` calls `applyLocalDefaults`, whose
Android `actual` reaches through gitlive's `FirebaseRemoteConfig.android` escape hatch to
the real SDK instance and calls `setDefaultsAsync(R.xml.remote_config_defaults)` — Firebase's
own documented convention, and discoverable by anyone who knows Android's Remote Config docs
without reading this module's Kotlin. The XML resource lives in *this* module's own
`androidMain/res/xml/`, not `:androidApp`'s — `:androidApp` depends on
`:infrastructure:firebase:remoteconfig`, never the reverse, so the resource has to live on
the side that already owns the dependency direction. iOS (and anything else) falls back to
`RemoteConfigAppStatusSource.REMOTE_DEFAULTS`, the same three values — kept in sync by hand,
which both files say at the top of themselves.

---

## §7 Observability

`AppStatusTelemetry` (`:core:data`): `blocked(reason)` / `released()` fire only on a
transition into or out of a `Blocked` state — the number that answers "how many installs did
a maintenance window actually stop". `DegradedByMaintenance` is deliberately silent here;
its product effect is `SyncTelemetry`'s own `skipped` event one layer up, so counting it
again here would double the same signal. `fetchFailed()` is a log line, not an analytics
event — a failed fetch is an expected offline-first outcome, not a broken invariant.

Getting nothing, on purpose: the domain types and the policy function (pure), `OdoBanner`
and `AppBlockedScreen` (Compose UI), the gateway (already reports through `onDiagnostic`).

---

## §8 Slices (all landed on `feat/app-status-gate`)

S1 domain + policy · S2 `AppInfo.versionCode` · S3 provider + fake + telemetry · S4 Firebase
adapter module · S5 blocking UI · S6 sync seam · S7 refresh triggers · S8 this document.

---

## §9 Verification

**Unit**, targeted per module (never the whole-project build task):

```
./gradlew :core:common:testAndroidHostTest :core:domain:testAndroidHostTest \
  :core:data:testAndroidHostTest :infrastructure:firebase:remoteconfig:testAndroidHostTest \
  :core:designsystem:testAndroidHostTest :shared:testAndroidHostTest
```

The policy test table (`AppAvailabilityPolicyTest`) covers: below-min-version blocks
regardless of freshness (including never-fetched); update-required outranks a fresh
full-block; a fresh full-block/degraded maintenance resolve correctly; a *stale* full-block
decays to `Allowed`; an unrecognised `maintenance_mode` string fails open; `min = 0` never
blocks. `DefaultAppStatusProviderTest` additionally covers: fail-open before any refresh; a
failed fetch keeps the previous verdict; `blocked`/`released` fire exactly once per
transition and never for a degraded change; the self-recheck loop decays a stale block with
no further `refresh()` call (exercised with `TestScope.backgroundScope` — the provider's
recheck loop never completes, so a plain scope would hang `runTest`).
`MaintenanceAwareSyncGateTest` covers: `Allowed` defers to the session gate; any
non-`Allowed` state refuses *without ever calling* the session gate (its adoption side
effect must not run mid-maintenance).

**On device** (`./gradlew :androidApp:installDebug`, against a Firebase project carrying the
three keys):

1. Baseline — all defaults, app behaves exactly as before this change.
2. `maintenance_mode = degraded` → banner appears, local writes still work, sync stops (rows
   stay `PENDING`). Back to `off` → banner clears, queue drains.
3. `maintenance_mode = full_block` → full-screen stop, no back-button escape, "Try again"
   re-fetches. Back to `off`, tap "Try again" → released without a restart.
4. `min_supported_version_code` above the installed build → update screen, its button opens
   the Play listing. Kill the network and relaunch → still blocked (D4 — cache counts for
   version).
5. With `full_block` active and fresh, airplane-mode the device and wait past 30 minutes →
   the app releases itself to `Allowed` (D4's other half — the one easy to get wrong and
   invisible in production without this check).
6. Remove `google-services.json`, clean build → app runs normally, one "remote config
   unavailable" diagnostic logged.
7. `./gradlew :core:common:testAndroidHostTest` (no flavor flag), then check the generated
   `core/common/build/generated/source/buildkonfig/commonMain/…/BuildKonfig.kt` reads
   `BUILD_TYPE = "release"` (the heuristic's default). `./gradlew :androidApp:assembleDebug`
   → regenerates to `"debug"`, and a fresh `assembleRelease` back to `"release"`. This
   confirms D5's heuristic, not just its intent.

Not covered by automated tests, by design: no instrumented E2E for the gate — it would need
a live Remote Config value to drive it, coupling the test to a console someone can change.
Same reason `applyLocalDefaults`'s Android `actual` (D6) has no unit test either — it calls
the real native SDK through gitlive's `.android` escape hatch, which needs a real
`FirebaseApp`, the same thing that makes `RealFirebaseRemoteConfigGateway`'s "configured"
success path untestable in isolation (§6's KDoc). The on-device steps above are the check
for both.

---

## §10 Operator runbook

**Opening a maintenance window:**

1. Firebase console → your project → Remote Config.
2. Set `maintenance_mode` to `degraded` (routine window, local app stays usable, sync pauses)
   or `full_block` (dangerous migration — no client writes, anywhere, for anyone).
3. Optionally set `maintenance_message` to owner-facing copy. Leave blank to use the bundled
   Hinglish default.
4. Publish. Clients pick it up within an hour in production (the 3600s
   `minimumFetchIntervalInSeconds`), or immediately on their next cold start / foreground.

**Closing it:** set `maintenance_mode` back to `off` and publish. Clients release on their
next fetch, or on their own within 30 minutes even with no fetch at all (§4's self-decay) —
so a window closes itself even if the console is never touched again, which is the safety
net, not the primary path.

**Raising the minimum supported version:** set `min_supported_version_code` to the lowest
`versionCode` still allowed to run, publish.

> **Standing rule: `min_supported_version_code` must never be set above the `versionCode`
> currently live on the Play Store.** Setting it above the live build locks out every user
> who cannot yet get the newer version — including users on a slow staged rollout, and users
> in regions where the Play Store update hasn't propagated. Raise it only after confirming
> the target version is 100% rolled out.

**Rollback:** every change above is reversible by publishing the previous value — there is
no migration, no local state to undo, and no version of the app that needs reinstalling.
`AppStatus.Unknown` (the fail-open default) is always one bad value away in the *safe*
direction.

**Manual QA note:** a debug build already fetches every 60 seconds (D5) — foreground the app
twice a minute apart and a console change is visible without touching code. If even that is
too slow (e.g. scripted testing), remember the SDK only throttles a fetch that follows a
prior *successful* one for the same install: a fresh install, or `adb shell pm clear
com.hopcape.odo`, always gets an untouched fetch regardless of the interval.
