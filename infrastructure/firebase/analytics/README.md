# `:infrastructure:firebase:analytics`

> Firebase Analytics adapter for `:observability:analytics`'s `AnalyticsSink` port — the only place in the app that imports the Firebase SDK.

| | |
|---|---|
| **Gradle path** | `:infrastructure:firebase:analytics` |
| **Package root** | `com.hopcape.odo.infrastructure.firebase.analytics` |
| **Targets** | `androidLibrary`, `iosArm64`, `iosSimulatorArm64` |
| **Depends on** | `:observability:analytics` only. Nothing in that module knows this one exists. |
| **Public surface** | `FirebaseAnalyticsSink` (public — see [§2](#2-why-this-class-is-public)) |

---

## 1. What this module does

`:observability:analytics` defines `AnalyticsSink`, a port an outside module implements to add a vendor destination without the analytics module depending on that vendor's SDK. This module is that implementation for Firebase: `FirebaseAnalyticsSink` sanitizes an event against Firebase's own constraints, then hands it to gitlive's Kotlin Multiplatform Firebase wrapper, on both Android and iOS from one `commonMain` implementation.

Registered via `AnalyticsConfig.destinations` in both app bootstraps (`OdoApplication.kt`, `MainViewController.kt`) — never resolved through Koin, for the reason in §2.

## 2. Why this class is public

Every other type in this module is `internal`. `FirebaseAnalyticsSink` is the one exception, and it's deliberate: `HAnalytics.init(...)` runs **before** the Koin graph starts in both app bootstraps, so the sink has to exist before there is a graph to resolve it from — the same reason `:infrastructure:database`'s `DriverFactory` is public. Its public secondary constructor takes only `onDiagnostic`; the gateway/sanitizer seam behind it stays internal for this module's own tests.

## 3. Architecture

```
track(name, properties, timestampMs)
    │
    ▼
FirebaseAnalyticsSink            — the AnalyticsSink implementation
    │  sanitizes via
    ▼
FirebaseEventSanitizer            — pure, SDK-free; every drop/coercion reported via onDiagnostic
    │  hands the result to
    ▼
FirebaseAnalyticsGateway          — the real Firebase.analytics calls, isolated for testability
    │  (RealFirebaseAnalyticsGateway is the production implementation)
    ▼
Firebase Analytics SDK (gitlive wrapper)
```

**`FirebaseAnalyticsGateway`** exists because gitlive's `FirebaseAnalytics` is a concrete SDK class (an `expect class`), not an interface — nothing about it can be faked directly in a test, so this is the fake-able boundary instead. `RealFirebaseAnalyticsGateway` resolves `Firebase.analytics` **lazily**, not as a constructor default: accessing it throws when no `FirebaseApp` has been configured, and every call is wrapped in `runCatching` and reported through `onDiagnostic` rather than left to crash — a misconfigured Firebase project degrades this one destination, never the host app.

## 4. The sanitizer

Firebase enforces constraints the client SDK does not itself validate. `FirebaseEventSanitizer` enforces them before anything reaches the SDK, so a malformed event is dropped and reported through `onDiagnostic` instead of failing silently deep inside Firebase's own pipeline:

| Rule | Limit |
|---|---|
| Event / param name pattern | `[A-Za-z][A-Za-z0-9_]*` |
| Reserved name prefixes (rejected) | `firebase_`, `google_`, `ga_` |
| Event name length | ≤ 40 chars |
| Param name length | ≤ 40 chars |
| Params per event | ≤ 25 |
| Param string value length | ≤ 100 chars (truncated) |
| User-property name length | ≤ 24 chars |
| User-property value length | ≤ 36 chars (truncated) |
| Value types accepted | `String`, `Long`, `Double`, `Boolean` — `Int`→`Long`, `Float`→`Double`, everything else `toString()`'d |

**An event the sanitizer rejects (invalid name) reports `track()` returning `true`** — handled, not retried — because retrying can never make an invalid name valid. This is the same distinction the sync engine draws between a permanent rejection and a transient one, applied to a vendor SDK instead of a server. A genuine delivery failure (unconfigured Firebase, the SDK throwing) returns `false` and asks the durable queue to retry.

## 5. The consent decision

**Firebase's own auto-collected events are not gated by Odo's consent state.** `HAnalytics`'s pipeline already drops every `track()`/`identify()` call unless consent is `GRANTED` — so nothing this app explicitly tracks reaches Firebase before the user has decided. But Firebase Analytics auto-logs its own events regardless (`first_open`, `session_start`, `screen_view`, `app_update`, `os_update`, …), and this module does not call `setAnalyticsCollectionEnabled(false)` to suppress them.

This was a deliberate choice, not an oversight: the alternative — toggling Firebase's own collection flag on `setConsent` — adds a second consent mechanism alongside the pipeline's own gate, for events this app never explicitly asked to send. Revisit this if Odo's DPDP consent flow needs to cover Firebase's auto-collected events specifically, not just the ones this app tracks.

## 6. PII

Firebase user properties must never carry a plate number, owner name, phone number, or address — the same rule the rest of the app's observability layer follows. `FirebaseAnalyticsSink.identify()` forwards whatever `UserTraits` it's given verbatim (after sanitizing names/lengths); it is the caller's job to never put PII in traits in the first place. Diagnostics reported by this module are always the *type* of a failure (`onDiagnostic("firebase: logEvent failed — IllegalStateException")`), never event payload content.

## 7. iOS: no Swift changes needed

Firebase has no auto-init on iOS the way Android's `FirebaseInitProvider` gives it — normally this means a `FirebaseApp.configure()` call in `AppDelegate`/`iOSApp.swift`. Odo doesn't need one: gitlive's `Firebase.initialize()` already wraps `FIRApp.configure()` under the hood, and it's a `commonMain`-callable function, so `configureFirebaseForIos` (in `iosMain` of this module) calls it directly from Kotlin. `MainViewController.kt` calls it before constructing `FirebaseAnalyticsSink`.

It's guarded on `GoogleService-Info.plist` actually being in the app bundle (`NSBundle.mainBundle.pathForResource`) — with no plist (this repo's state until one is added), `configureFirebaseForIos` returns `false` and the sink is **not** added to `AnalyticsConfig.destinations` at all, rather than being added and left to call an unconfigured SDK. `RealFirebaseAnalyticsGateway`'s lazy, fail-safe lookup is the fallback for any *other* way Firebase might fail to initialize — not the first line of defense against this specific, expected "no plist yet" state.

Kotlin's own SwiftPM support resolves the Firebase iOS SDK automatically (declared by gitlive's package, not by this repo) — no manual Xcode/SPM package addition is needed either.

## 8. Setup

- **Android** — drop a real `google-services.json` into `androidApp/` (gitignored; see that directory's note). Until then, `:androidApp:assembleDebug` fails at `processDebugGoogleServices` — that's the `google-services` Gradle plugin, not this module.
- **iOS** — drop a real `GoogleService-Info.plist` into the app bundle. Until then the app still launches (§7) — the destination is simply absent from `AnalyticsConfig.destinations`.

## 9. Known limitations

- **PostHog, not Firebase, is Odo's primary vendor** (North Star: bills scanned/month) — Firebase is a secondary destination. See `:observability:analytics`'s README for PostHog's own status.
- **No `resetAnalyticsData()`/`setDefaultEventParameters()` wiring.** The gateway only exposes what the sink currently needs (`logEvent`, `setUserId`, `setUserProperty`); gitlive's wrapper has more surface if a future need calls for it.
- **`timestampMs` is not forwarded to Firebase.** The client SDK has no way to backdate an event, so a retried or delayed delivery (from the durable queue) is stamped at the moment it actually reaches the SDK, not when it originally happened.
