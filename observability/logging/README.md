# `:observability:logging`

> Structured, PII-aware, multiplatform logging for Odo — one small public contract, a hardened internal pipeline, and two interchangeable entry points (Koin DI **and** a static facade) that resolve to the **same** logger.

| | |
|---|---|
| **Gradle path** | `:observability:logging` |
| **Package root** | `com.hopcape.logging` |
| **Targets** | `androidLibrary`, `iosArm64`, `iosSimulatorArm64` (Kotlin Multiplatform, `commonMain` only) |
| **Plugins** | `odo.kmpLibrary`, `odo.koin` |
| **Runtime deps** | `kotlinx-datetime` (timestamps), `koin-core` (DI binding) |
| **Public entry points** | `HLogger` (facade) · `loggingModule` (Koin) · `Logger` (contract) |

---

## Table of contents

1. [Why this module](#1-why-this-module)
2. [Feature summary](#2-feature-summary)
3. [Architecture](#3-architecture)
   - [Package & layer layout](#31-package--layer-layout)
   - [Component diagram](#32-component-diagram)
   - [Log-call sequence](#33-log-call-sequence)
   - [Design patterns & SOLID map](#34-design-patterns--solid-map)
4. [The two entry points (and why they don't conflict)](#4-the-two-entry-points-and-why-they-dont-conflict)
5. [Getting started](#5-getting-started)
6. [Public API reference](#6-public-api-reference)
7. [Usage cookbook](#7-usage-cookbook)
8. [Configuration reference](#8-configuration-reference)
9. [Log levels](#9-log-levels)
10. [PII redaction](#10-pii-redaction)
11. [Concurrency & failure-safety guarantees](#11-concurrency--failure-safety-guarantees)
12. [Extending the pipeline](#12-extending-the-pipeline)
13. [Visibility policy](#13-visibility-policy)
14. [Testing](#14-testing)
15. [Internal component reference](#15-internal-component-reference)
16. [Known limitations & roadmap](#16-known-limitations--roadmap)
17. [FAQ](#17-faq)

---

## 1. Why this module

Application code (ViewModels, repositories, data/IO adapters) needs to emit diagnostics without:

- knowing **where** logs go (Logcat, file, remote…),
- caring **how** sensitive data is scrubbed,
- ever being able to **crash** because logging failed,
- coupling to any platform (`android.util.Log`) so the same code runs on iOS.

This module gives callers a **one-method contract** (`Logger`) plus an ergonomic **facade** (`HLogger`), and hides everything else (sinks, redaction, formatting, failure isolation) behind `internal` visibility. Swapping destinations or redaction rules is a change in **one** composition root — never in call sites.

---

## 2. Feature summary

- **Structured events** — every log is `level + tag + event + optional trace context + typed fields map`, not a flattened string.
- **Multi-sink fan-out** — one event goes to N destinations (console/Logcat, file, …) via the Strategy pattern.
- **PII redaction** — field values are scrubbed (email, phone) by a decorator before they reach any sink.
- **Crash-proof** — a misbehaving sink can never throw into caller code (`SafeSink`).
- **Trace correlation** — `sessionId` / `flowId` / `traceId` stitch related lines together across a user journey.
- **Scoped loggers** — bind a tag + trace once, then `logger.d("…")` without repeating context.
- **Runtime verbosity overrides** — bump a single tag to `VERBOSE` at runtime (e.g. remote-config field debugging) without a new build.
- **Two access styles, one logger** — inject `Logger` via Koin **or** call `HLogger` statically; both share the same configured pipeline.
- **KMP-native** — pure `commonMain`; no `expect/actual` required today.

---

## 3. Architecture

### 3.1 Package & layer layout

The package name encodes the visibility contract: **`api` = public surface, `internal` = implementation.**

```
com.hopcape.logging
├── api/                         ← PUBLIC. The only things callers may import.
│   ├── Logger.kt                    interface Logger            (the contract, ISP)
│   ├── LogLevel.kt                  enum LogLevel               (VERBOSE…FATAL)
│   ├── TraceContext.kt              data class TraceContext     (correlation ids)
│   ├── LoggerConfig.kt              data class + Builder + loggerConfig{} DSL
│   │                                + @StableLoggerApi marker
│   ├── ScopedLogger.kt              tag/trace-bound Logger (public, internal ctor)
│   ├── HLogger.kt                   object HLogger              (the Facade)
│   └── LoggingModule.kt             val loggingModule           (Koin binding)
│
└── internal/                    ← INTERNAL. Not visible outside this module.
    ├── LoggerFactory.kt             builds a Logger from a LoggerConfig (Factory)
    ├── LoggerImpl.kt                routes an event to every sink (SRP)
    ├── model/
    │   └── LogEvent.kt              immutable event + Builder
    ├── redactor/
    │   ├── PIIRedactor.kt           interface PiiRedactor
    │   └── RegexPIIRedactor.kt      regex email/phone masker
    ├── sinks/
    │   ├── LogSink.kt               interface LogSink           (Strategy)
    │   ├── LogcatSink.kt            console/Logcat destination
    │   ├── FileSink.kt              file destination (JSON lines)
    │   ├── RedactingSink.kt         PII-scrubbing decorator     (Decorator)
    │   └── SafeSink.kt              exception-swallowing decorator (Decorator)
    └── utils/
        └── SafeMap.kt               lock-free copy-on-write map (tag overrides)
```

**Dependency rule:** `internal` may depend on `api` (e.g. a sink references `LogLevel`); `api` never depends on the concrete `internal` types except at the two composition roots (`HLogger`, `LoggerFactory`), which is the whole point of a composition root.

### 3.2 Component diagram

```
                       ┌──────────────────────── PUBLIC (api) ────────────────────────┐
   caller code         │                                                              │
  (VM / repo / io)     │   ┌─────────────┐        ┌──────────────────────────────┐    │
        │              │   │  loggingModule│──────▶│           HLogger            │    │
        │  koinInject  │   │  (Koin val)  │ asLogger  (Facade + composition root) │    │
        ├─────────────▶│   └─────────────┘        │  init(config) / tag() / …     │    │
        │   Logger     │                          └───────────────┬──────────────┘    │
        │              │   ┌─────────────┐                         │ builds via        │
        │   HLogger.   │   │ ScopedLogger │◀── tag() returns        │                   │
        └─ tag("Car") ─│──▶│ (d/i/w/e)   │                         ▼                   │
                       │   └─────────────┘        ┌──────────────────────────────┐    │
                       └──────────────────────────│   internal composition root  │────┘
                                                  │        LoggerFactory          │
                                                  └───────────────┬──────────────┘
                                                                  │ create(config)
                       ┌──────────────────────── INTERNAL ────────▼──────────────────────┐
                       │                          ┌──────────────┐                        │
                       │                          │  LoggerImpl  │  (fan-out router)      │
                       │                          └──────┬───────┘                        │
                       │            ┌────────────────────┼────────────────────┐           │
                       │            ▼                    ▼                     ▼           │
                       │   ┌──────────────┐     ┌──────────────┐      ┌──────────────┐    │
                       │   │   SafeSink   │     │   SafeSink   │      │     …        │    │
                       │   │  (try/catch) │     │  (try/catch) │      └──────────────┘    │
                       │   └──────┬───────┘     └──────┬───────┘                          │
                       │          ▼                    ▼                                   │
                       │   ┌──────────────┐     ┌──────────────┐                          │
                       │   │ RedactingSink│     │ RedactingSink│   (PII scrub, Decorator) │
                       │   └──────┬───────┘     └──────┬───────┘                          │
                       │          ▼                    ▼                                   │
                       │   ┌──────────────┐     ┌──────────────┐                          │
                       │   │  LogcatSink  │     │   FileSink   │        (Strategy leaves) │
                       │   └──────────────┘     └──────────────┘                          │
                       └──────────────────────────────────────────────────────────────────┘

Each sink is wrapped:  SafeSink( RedactingSink( <real sink> ) )
                       └ never throws ┘└ scrubs PII ┘└ writes ┘
```

### 3.3 Log-call sequence

What happens on `HLogger.tag("Sync").d("pushed batch", mapOf("rows" to 12))`:

```
caller ─▶ ScopedLogger.d("pushed batch", {rows})
            └─▶ log(DEBUG, "Sync", "pushed batch", trace, {rows})
                  └─▶ HLogger.EffectiveLogger.log(...)          ① runtime tag-level override check
                        └─▶ delegate = LoggerImpl.log(...)      ② (delegate set by init())
                              └─▶ LogEvent.Builder … build()    ③ stamp timestamp (kotlinx-datetime)
                                    for each sink:
                                    └─▶ SafeSink.write(event)   ④ try { … } catch → swallow
                                          └─▶ RedactingSink.write(redactor.redact(event))  ⑤ scrub field values
                                                └─▶ LogcatSink/FileSink.write(event)       ⑥ minLevel filter, emit
```

- **①** If `setTagLevelOverride("Sync", …)` raised the bar above `DEBUG`, the call short-circuits here.
- **②** Before `init()`, `delegate` is a no-op — nothing is emitted, nothing crashes.
- **⑥** Each sink applies its own `minLevel` threshold, then formats & emits.

### 3.4 Design patterns & SOLID map

| Pattern / principle | Where | Payoff |
|---|---|---|
| **Facade** | `HLogger` | One import for 95% of call sites; hides init/config/routing. |
| **Factory + composition root** | `LoggerFactory.create(config)` | The single place that names concrete sinks/redactors. |
| **Strategy** | `LogSink` + `LogcatSink`/`FileSink` | Add a destination = new class, zero edits elsewhere (OCP). |
| **Decorator** | `RedactingSink`, `SafeSink` | Add redaction / crash-safety by wrapping, not modifying, sinks. |
| **Builder** | `LogEvent.Builder`, `LoggerConfig.Builder` | Readable, immutable construction of many-field objects. |
| **Adapter (router)** | `HLogger.EffectiveLogger` | Applies runtime overrides, then delegates. |
| **ISP** | `Logger` is one method (+ sugar) | Callers depend on the minimum. |
| **DIP** | callers → `Logger`; `LoggerImpl` → `LogSink` | Nothing depends on a concrete destination. |
| **SRP** | `LoggerImpl` only fans out; each sink does one thing | Small, testable units. |
| **LSP** | no-op logger, `ScopedLogger`, `EffectiveLogger` all *are* `Logger` | Substitutable everywhere a `Logger` is expected. |

---

## 4. The two entry points (and why they don't conflict)

There are two ways to obtain a logger, and they intentionally resolve to **one** underlying pipeline:

```
        HLogger.init(config)                    ← the ONE composition call
                 │  builds
                 ▼
        LoggerImpl(sinks)   ◀────────── HLogger.asLogger() ──────────┐
                 ▲                                                    │
                 │ delegate                                           │ single<Logger>
        HLogger.tag("X")   (static facade)          loggingModule (Koin) ─▶ koinInject<Logger>()
```

- **`HLogger` (facade)** — global, ergonomic, no injection needed. Ideal for leaf/utility code and platform bootstraps. Adds session context + scoped loggers + runtime overrides.
- **`loggingModule` (Koin)** — binds `single<Logger> { HLogger.asLogger() }`. Ideal for constructor-injected classes (ViewModels, repositories) that want the `Logger` port for testability.

Because `loggingModule` **republishes** `HLogger.asLogger()` rather than building its own logger, there is exactly **one** config, **one** set of sinks, and **one** file — no duplicate writers. Configuration therefore lives in a single `HLogger.init(...)` call at app startup.

> **Ordering:** call `HLogger.init(...)` before the Koin graph is *used*. The DI binding resolves a stable reference (`EffectiveLogger`) that reads the live `delegate` at log-time, so strict ordering isn't required for correctness — but initializing first means the very first injected log already hits real sinks.

---

## 5. Getting started

### 5.1 Add the dependency

```kotlin
// consumer module build.gradle.kts
dependencies {
    implementation(projects.observability.logging)
}
```

The `:shared` composition module already depends on it and lists `loggingModule` in `initKoin`.

### 5.2 Initialize once at startup

**Android** (`OdoApplication.onCreate`):

```kotlin
configureLogging(BuildConfig.DEBUG)   // HLogger.init(...) — see below
initKoin(platformModule = module { single { DriverFactory(androidContext()) } }) {
    androidLogger(Level.INFO)
    androidContext(this@OdoApplication)
}

private fun configureLogging(isDebugBuild: Boolean) = HLogger.init(
    loggerConfig {
        environment(if (isDebugBuild) LoggerConfig.Environment.DEBUG else LoggerConfig.Environment.PRODUCTION)
        filePath("app_logs.log")
        minLevel(if (isDebugBuild) LogLevel.VERBOSE else LogLevel.INFO)
        piiRedaction(true)
    }
)
```

**iOS** (`MainViewController`):

```kotlin
val isDebug = Platform.isDebugBinary
HLogger.init(
    LoggerConfig(
        environment = if (isDebug) LoggerConfig.Environment.DEBUG else LoggerConfig.Environment.PRODUCTION,
        filePath = "app_logs.log",
        minLevel = if (isDebug) LogLevel.VERBOSE else LogLevel.INFO,
    )
)
initKoin(platformModule = module { single { DriverFactory() } })
```

That's the entire wiring. Everything below is call-site usage.

---

## 6. Public API reference

Only these types are importable from outside the module. Everything else is `internal`.

### `interface Logger`
The core contract — the port every caller depends on.

```kotlin
fun log(level: LogLevel, tag: String, event: String,
        traceContext: TraceContext? = null, fields: Map<String, Any?> = emptyMap())

// level-specific sugar (all delegate to log):
fun verbose(tag, event, tc = null, fields = emptyMap())
fun debug  (tag, event, tc = null, fields = emptyMap())
fun info   (tag, event, tc = null, fields = emptyMap())
fun warn   (tag, event, tc = null, fields = emptyMap())
fun error  (tag, event, tc = null, fields = emptyMap())
fun flush()   // force buffered sinks to persist
```
> `FATAL` has no sugar method — emit it via `log(LogLevel.FATAL, …)`.

### `enum LogLevel`
`VERBOSE(0) · DEBUG(1) · INFO(2) · WARN(3) · ERROR(4) · FATAL(5)` — `priority` drives `minLevel` filtering.

### `data class TraceContext`
```kotlin
TraceContext(sessionId: String? = null, flowId: String? = null, traceId: String? = null)
fun withNewTrace(traceId: String): TraceContext                    // same session/flow, new trace
fun withNewFlow(flowId: String, traceId: String? = null): TraceContext
```
Correlation ids attached to events so related lines can be grouped (session → flow → trace).

### `data class LoggerConfig` + `loggerConfig { }` + `LoggerConfig.Builder`
Immutable configuration consumed by `HLogger.init`. See [§8](#8-configuration-reference). Build via the DSL or the `Builder`.

### `class ScopedLogger` *(public type, `internal` constructor)*
A `Logger` pre-bound to a tag + trace, returned by `HLogger.tag(...)`. You never construct it directly.
```kotlin
fun d/i/w/e(event: String, fields: Map<String, Any?> = emptyMap())
fun log(level: LogLevel, event: String, fields: Map<String, Any?> = emptyMap())
fun withTrace(traceId: String): ScopedLogger      // narrow to a new traceId
```

### `object HLogger` — the facade
```kotlin
fun init(config: LoggerConfig)                    // idempotent; safe to call twice (no-op + warning)
fun setSession(sessionId: String)                 // process-wide session id (e.g. post-login)
fun tag(tag: String, flowId: String? = null): ScopedLogger
fun setTagLevelOverride(tag: String, level: LogLevel)
fun clearTagLevelOverride(tag: String)
fun asLogger(): Logger                            // the plain Logger for DI (what loggingModule binds)
fun flush()
```

### `val loggingModule: Module`
Koin module binding `single<Logger> { HLogger.asLogger() }`. List it in your `startKoin { modules(...) }` (already done in `initKoin`).

### `annotation class StableLoggerApi`
Marks the supported, stable surface. Types **not** marked (and not `internal`) may change.

---

## 7. Usage cookbook

### 7.1 Inject the `Logger` port (Koin)

```kotlin
class CarRepositoryImpl(
    private val logger: Logger,          // resolved from loggingModule
) {
    fun save(car: Car) {
        logger.info("CarRepo", "saving car", fields = mapOf("carId" to car.id))
        // …
    }
}

// DI registration
val dataModule = module {
    single<CarRepository> { CarRepositoryImpl(logger = get()) }
}
```

### 7.2 Static facade (no injection)

```kotlin
HLogger.tag("Startup").i("app booted", mapOf("coldStart" to true))
```

### 7.3 Scoped logger — bind a tag once

```kotlin
class SyncEngine {
    private val log = HLogger.tag("Sync")

    fun onPushed(rows: Int) = log.d("pushed batch", mapOf("rows" to rows))
    fun onError(t: Throwable) = log.e("sync failed", mapOf("error" to t.message))
}
```

### 7.4 Trace / session correlation

```kotlin
// After login, stamp the session once — every subsequent scoped log carries it:
HLogger.setSession(sessionId = user.sessionId)

// A multi-step flow with its own flowId, and a per-attempt traceId:
val flow = HLogger.tag("BillScan", flowId = "scan-42")
flow.i("started")
val attempt = flow.withTrace(traceId = "ocr-try-1")
attempt.d("calling vision api")
attempt.w("low confidence, flagging for manual review")
```

### 7.5 Structured fields (prefer over string interpolation)

```kotlin
logger.warn(
    tag = "Fairness",
    event = "low sample size",
    fields = mapOf("city" to "Mumbai", "dataPoints" to 3, "confidence" to "low"),
)
// → queryable JSON, not a baked "…Mumbai has 3 points…" string
```

### 7.6 Runtime verbosity override (remote-config debugging)

```kotlin
// Turn on VERBOSE for ONE noisy subsystem for a single user session,
// e.g. driven by a remote-config flag — no app update, no global spam:
HLogger.setTagLevelOverride("Sync", LogLevel.VERBOSE)
// …reproduce the field issue…
HLogger.clearTagLevelOverride("Sync")
```

### 7.7 Force a flush (before a crash handler / process exit)

```kotlin
HLogger.flush()   // or logger.flush()
```

---

## 8. Configuration reference

`LoggerConfig` fields:

| Field | Type | Default | Effect |
|---|---|---|---|
| `environment` | `Environment` = `DEBUG`/`STAGING`/`PRODUCTION` | *(required)* | Semantic label for the build; carried for downstream use. |
| `filePath` | `String?` | `null` | When set, adds a `FileSink` writing JSON lines to this path. `null` → console only. |
| `remoteEndpoint` | `String?` | `null` | Reserved for a future `RemoteSink`; **currently not consumed** (see [roadmap](#16-known-limitations--roadmap)). |
| `minLevel` | `LogLevel` | `INFO` | Threshold applied by every sink — events below it are dropped. |
| `piiRedactionEnabled` | `Boolean` | `true` | When `true`, wraps each sink in a `RedactingSink`. |

Two equivalent ways to build it:

```kotlin
// DSL
val cfg = loggerConfig {
    environment(LoggerConfig.Environment.PRODUCTION)
    filePath("app_logs.log")
    minLevel(LogLevel.INFO)
    piiRedaction(true)
}

// Constructor
val cfg = LoggerConfig(
    environment = LoggerConfig.Environment.PRODUCTION,
    filePath = "app_logs.log",
    minLevel = LogLevel.INFO,
)
```

---

## 9. Log levels

| Level | Priority | Use for |
|---|---|---|
| `VERBOSE` | 0 | Firehose tracing, per-packet/per-frame detail. |
| `DEBUG` | 1 | Developer diagnostics during a build. |
| `INFO` | 2 | Notable lifecycle/business events (default prod floor). |
| `WARN` | 3 | Recoverable anomalies, degraded paths. |
| `ERROR` | 4 | Failures needing attention. |
| `FATAL` | 5 | Unrecoverable — emit via `log(LogLevel.FATAL, …)`. |

Filtering is `event.level.priority >= minLevel.priority`. Set a low `minLevel` in debug, `INFO` in production.

---

## 10. PII redaction

- Implemented by `RegexPiiRedactor` (default patterns: **email** and **10-digit phone**), applied by `RedactingSink` **before** any sink writes.
- **Scope today:** only the **values in the `fields` map** that are `String` are scrubbed. The free-text `event` message and the `tag` are **not** scrubbed — so keep sensitive data in `fields`, not in the message.
- Masked output looks like `***email_masked***` / `***phone_masked***`.
- Toggle globally with `LoggerConfig.piiRedaction(false)` (e.g. never disable in production).

```kotlin
logger.info("Auth", "login ok", fields = mapOf("email" to "a@b.com"))
// field value emitted as: "***email_masked***"
```

> Extend by supplying more patterns to `RegexPiiRedactor` (internal); expose a config hook when needed (roadmap).

---

## 11. Concurrency & failure-safety guarantees

- **Logging never crashes callers.** Every sink is wrapped in `SafeSink`, which `try/catch`es `write`/`flush` and swallows any `Throwable`. A full disk or a serializer OOM degrades logging, never the app.
- **Pre-init safety.** Before `HLogger.init`, the delegate is a no-op `Logger`; calls are silently dropped, never `NPE`.
- **Idempotent init.** `init()` is guarded by an `AtomicBoolean` (CAS) — a duplicate call (test setup, multi-process) is a no-op + internal warning, not a re-wire.
- **Runtime overrides are lock-free.** `SafeMap` is copy-on-write behind a `@Volatile` reference: reads (the hot path) never block; the rare write swaps the whole map (last-writer-wins). No coroutines, no `suspend` — logging stays synchronous.
- **Volatile delegate.** `HLogger.delegate` / `globalTraceContext` are `@Volatile`, so an `init()` on one thread is visible to loggers on others.

---

## 12. Extending the pipeline

**Add a new destination (e.g. Sentry, Elastic, Firebase):**

1. Implement `LogSink` in `internal/sinks/` (SRP — do one thing):
   ```kotlin
   internal class SentrySink(private val minLevel: LogLevel) : LogSink {
       override fun write(event: LogEvent) {
           if (event.level.priority < minLevel.priority) return
           // … ship event …
       }
   }
   ```
2. Add it to the list in `LoggerFactory.create(config)`. It is automatically wrapped in `RedactingSink` + `SafeSink` like the others.

No changes to `LoggerImpl`, existing sinks, or any call site — that's OCP + Decorator paying off.

**Add a new redaction rule:** extend the `patterns` map in `RegexPiiRedactor`, or implement a new `PiiRedactor` and use it in `LoggerFactory`.

---

## 13. Visibility policy

The rule is mechanical and enforced by package:

- **`com.hopcape.logging.api.*` → `public`** — the supported surface: `Logger`, `LogLevel`, `TraceContext`, `LoggerConfig` (+ `Builder`/`loggerConfig`/`StableLoggerApi`), `ScopedLogger` (public type, `internal` constructor), `HLogger`, `loggingModule`.
- **`com.hopcape.logging.internal.*` → `internal`** — every implementation type (`LoggerFactory`, `LoggerImpl`, `LogEvent`, all sinks, redactors, `SafeMap`). None are importable by consumers.

If you need to expose a new capability, put its stable type in `api` and mark it `@StableLoggerApi`; keep the machinery in `internal`.

---

## 14. Testing

The `Logger` port makes consumers trivially testable — no real sinks, no files, no tokens:

```kotlin
class RecordingLogger : Logger {
    val events = mutableListOf<Triple<LogLevel, String, String>>()
    override fun log(level: LogLevel, tag: String, event: String,
                     traceContext: TraceContext?, fields: Map<String, Any?>) {
        events += Triple(level, tag, event)
    }
    override fun flush() {}
}

@Test
fun repo_logs_on_save() {
    val log = RecordingLogger()
    CarRepositoryImpl(logger = log).save(car)
    assertTrue(log.events.any { it.second == "CarRepo" })
}
```

For code that calls `HLogger` statically, either inject `HLogger.asLogger()` behind a `Logger` param (preferred) or call `HLogger.init(loggerConfig { … })` in test setup (it's idempotent).

---

## 15. Internal component reference

| Component | Responsibility |
|---|---|
| `LoggerFactory` | The internal composition root: `create(config)` assembles `SafeSink(RedactingSink(sink))` layers into a `LoggerImpl`; also `createNoOpLogger()`. |
| `LoggerImpl` | Fan-out router — builds a `LogEvent` and calls `write` on every sink; `flush()` flushes all. Nothing else. |
| `LogEvent` (+ `Builder`) | Immutable event: `timestampMs` (via `kotlinx-datetime`), level, tag, event, trace ids, fields. |
| `LogSink` | Strategy interface — `write(event)` + optional `flush()`. |
| `LogcatSink` | Formats and emits to console/Logcat; applies its own `minLevel`. |
| `FileSink` | Serializes events to JSON lines for a file; applies its own `minLevel`. |
| `RedactingSink` | Decorator — runs `PiiRedactor.redact` then delegates. |
| `SafeSink` | Decorator — `try/catch`es the delegate so logging can't throw. |
| `PiiRedactor` / `RegexPiiRedactor` | Redaction abstraction + regex email/phone implementation. |
| `SafeMap` | Lock-free copy-on-write map backing runtime tag-level overrides. |

---

## 16. Known limitations & roadmap

Current state is honest about what's stubbed:

- **`FileSink` is not writing yet** — it formats the JSON line but the actual file append is a placeholder (`println`). Wire a buffered/rotating writer per platform (likely `expect/actual`) before relying on on-device log files.
- **`LogcatSink` uses `println`**, not `android.util.Log` — replace with a platform log via `expect/actual` for proper Logcat levels/tags on Android.
- **`remoteEndpoint` is accepted but unused** — reserved for a future `RemoteSink` (batch upload + retry/backoff). Configuring it today is a no-op.
- **Redaction covers `fields` values only** — the `event` message and `tag` are not scrubbed; extend if free-text messages may carry PII.
- **No log rotation / size cap** yet for the file destination.
- **`HLogger.internalLog` uses `println`** — swap for a platform diagnostic channel.

None of these affect the public API — they are internal upgrades behind the existing contract.

---

## 17. FAQ

**Q: Should I inject `Logger` or call `HLogger`?**
Inject `Logger` in classes that already use DI (ViewModels, repositories) — it keeps them unit-testable. Use `HLogger` in leaf/utility code and bootstraps where injection is awkward. Both hit the same pipeline.

**Q: Do I need to `HLogger.init` in tests?**
Only if the code under test calls `HLogger` statically. Prefer passing `HLogger.asLogger()` (or a fake) behind a `Logger` parameter so you don't touch global state.

**Q: Why is `ScopedLogger` public but its constructor `internal`?**
So callers can hold and pass the type returned by `HLogger.tag(...)`, but can't fabricate one outside the facade — the facade stays the single source of tag/trace binding.

**Q: Where does build-type (debug/release) selection happen?**
In the single `HLogger.init(...)` call at startup — the platform passes `BuildConfig.DEBUG` (Android) / `Platform.isDebugBinary` (iOS) and picks the `LoggerConfig`. The Koin module carries no build-type flag.

**Q: Is it safe to log from many threads?**
Yes — the delegate is `@Volatile`, sinks are exception-isolated, and tag-overrides are lock-free. (Individual sink write ordering across threads is not serialized; add synchronization inside a specific sink if it needs it.)
