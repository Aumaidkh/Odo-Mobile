# `:infrastructure:firebase:crashlytics`

> Firebase Crashlytics adapter for `:observability:crashreporting`'s `CrashSink` port — the only place in the app that imports the Firebase Crashlytics SDK.

| | |
|---|---|
| **Gradle path** | `:infrastructure:firebase:crashlytics` |
| **Package root** | `com.hopcape.odo.infrastructure.firebase.crashlytics` |
| **Targets** | `androidLibrary`, `iosArm64`, `iosSimulatorArm64` |
| **Depends on** | `:observability:crashreporting` only. Nothing in that module knows this one exists. |
| **Public surface** | `FirebaseCrashlyticsSink` (public — see [§2](#2-why-this-class-is-public)) |

---

## 1. What this module does

`:observability:crashreporting` defines `CrashSink`, a port an outside module implements to add a vendor destination without the crashreporting module depending on that vendor's SDK — the same shape as `:observability:analytics`'s `AnalyticsSink`. This module is that implementation for Crashlytics: `FirebaseCrashlyticsSink` forwards breadcrumbs, custom keys, and a reconstructed exception to gitlive's Kotlin Multiplatform Firebase wrapper, on both Android and iOS from one `commonMain` implementation.

Registered via `CrashConfig.destinations` in `OdoApplication.configureCrashReporting` — never resolved through Koin, for the reason in §2. (iOS has no `CrashReporter.init` call yet — crash reporting is Android-only for now, matching the rest of the crashreporting module's current state.)

## 2. Why this class is public

Every other type in this module is `internal`. `FirebaseCrashlyticsSink` is the one exception, and it's deliberate: `CrashReporter.init(...)` runs **before** the Koin graph starts (`OdoApplication.onCreate`), so the sink has to exist before there is a graph to resolve it from — the same reason `:infrastructure:firebase:analytics`'s `FirebaseAnalyticsSink` is public. Its public secondary constructor takes only `onDiagnostic`; the gateway seam behind it stays internal for this module's own tests.

## 3. Architecture

```
record(throwableType, throwableMessage, stackTrace, isFatal, breadcrumbs, customKeys)
    │
    ▼
FirebaseCrashlyticsSink          — the CrashSink implementation
    │  breadcrumbs → log(), customKeys → setCustomKey(), throwable → recordException()
    ▼
FirebaseCrashlyticsGateway        — the real Firebase.crashlytics calls, isolated for testability
    │  (RealFirebaseCrashlyticsGateway is the production implementation)
    ▼
Firebase Crashlytics SDK (gitlive wrapper)
```

`CrashSink` is deliberately narrower than `:observability:crashreporting`'s internal `CrashDestination`: it never sees the internal `CrashReport` model, only the resolved primitives it needs to forward (`SinkCrashDestination`, internal to that module, does the mapping). Registering this sink is what supersedes that module's old `CrashlyticsDestination` stub, which has been removed.

**`FirebaseCrashlyticsGateway`** exists because gitlive's `FirebaseCrashlytics` is a concrete SDK class (an `expect class`), not an interface — nothing about it can be faked directly in a test, so this is the fake-able boundary instead. `RealFirebaseCrashlyticsGateway` resolves `Firebase.crashlytics` **lazily**, not as a constructor default: accessing it throws when no `FirebaseApp` has been configured, and every call is wrapped in `runCatching` and reported through `onDiagnostic` rather than left to crash — a misconfigured Firebase project degrades this one destination, never the host app.

## 4. The throwable

A `CrashReport` never carries a live `Throwable` — it may be serialized to disk and re-read on the next launch (a fatal report), long after the original object (and its heap) is gone, so `:observability:crashreporting` flattens it into `throwableType`/`throwableMessage`/`stackTrace` strings at capture time, for both fatal and non-fatal reports. `FirebaseCrashlyticsSink.record()` reconstructs a synthetic `RuntimeException("$throwableType: $throwableMessage")` to hand to `recordException()` — its stack trace is where this call was made, not the original crash site. This is an existing limitation of the report model, not something this module introduces.

## 5. PII

Custom keys and user IDs reaching this sink are already PII-redacted by `:observability:crashreporting`'s `RedactingCrashDestination`, which wraps every destination (including this one, via `SinkCrashDestination`) before delivery. Diagnostics reported by this module are always the *type* of a failure (`onDiagnostic("crashlytics: recordException failed — IllegalStateException")`), never report content.

## 6. iOS

No Swift changes needed — same reasoning as `:infrastructure:firebase:analytics` §7: gitlive's `Firebase.initialize()` already wraps native setup and is `commonMain`-callable. `configureFirebaseForIos` (in that module) covers Firebase project init for both sinks; this module doesn't need its own copy. There is currently no `CrashReporter.init` call on iOS, so this sink isn't wired into `MainViewController.kt` yet — add it there, the same way `FirebaseAnalyticsSink` is, once crash reporting itself is brought to iOS.

## 7. Setup

Same as `:infrastructure:firebase:analytics` §8 — a real `google-services.json` (Android) / `GoogleService-Info.plist` (iOS) is required for `Firebase.crashlytics` to resolve. Until one is added, `RealFirebaseCrashlyticsGateway` degrades to reporting `"crashlytics: unavailable"` via `onDiagnostic` rather than crashing.

## 8. Known limitations

- **No custom-key/value length limits enforced client-side**, unlike `:infrastructure:firebase:analytics`'s `FirebaseEventSanitizer`. Crashlytics' own SDK truncates/caps these silently (1024-char values, 64 keys), so no equivalent sanitizer was added here — revisit if that turns out not to hold.
- **`setCrashlyticsCollectionEnabled`, `sendUnsentReports`, `deleteUnsentReports`, `didCrashOnPreviousExecution`** are not exposed. The gateway only wraps what `FirebaseCrashlyticsSink` currently needs (`recordException`, `log`, `setCustomKey`, `setUserId`).
