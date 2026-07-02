# `:observability:performance`

> Span-based Application Performance Monitoring (APM) for Odo — trace-correlated, nesting-aware, adaptively-sampled timing for the operations that actually have a duration. One small public contract, a hardened internal pipeline, and two interchangeable entry points (Koin DI **and** a static facade) that resolve to the **same** tracer.

| | |
|---|---|
| **Gradle path** | `:observability:performance` |
| **Package root** | `com.hopcape.performance` |
| **Targets** | `androidLibrary`, `iosArm64`, `iosSimulatorArm64` (Kotlin Multiplatform, `commonMain` only) |
| **Plugins** | `odo.kmpLibrary`, `odo.koin`, `odo.kmpTest` |
| **Runtime deps** | `kotlinx-datetime` (timestamps), `kotlinx-coroutines-core` (dispatch loop + trace propagation), `koin-core` (DI binding) |
| **Public entry points** | `APM` / `Perf` (facade) · `performanceModule` (Koin) · `PerformanceTracer` (contract) · `Span`, `TraceContext` |

> Sibling of [`:observability:logging`](../logging/README.md) and [`:observability:analytics`](../analytics/README.md) — the three observability modules deliberately share shape (`api`/`internal` split, facade + Koin dual entry, fail-safe decorators, factory composition root, buffered + retried delivery) so they feel like one system.

---

## 1. Why this module

Logs tell you *what happened*; analytics tells you *what the user did*; **APM tells you *how long it took*** — and where the time went. This module answers questions like:

- How long is cold start, from process create to first drawn frame?
- Is `login_flow` slow because of the network (`login_api_call`), or because of client-side validation/UI?
- Which checkout steps fail, and how often (`inventory_check_api` → `OUT_OF_STOCK`)?

It does that with **spans** (a named, timed operation) grouped into **traces** (one user-initiated operation), rendered on a dashboard as a nested tree.

## 2. Core concepts

| Concept | What it is | Rule of thumb |
|---|---|---|
| **Span** | One timed operation (`login_api_call`). Has a start, an end, and attributes. | Create a span **only for things with a meaningful duration** — network call, DB query, render, cold start. |
| **traceId** | Correlates all spans of one user-initiated operation. | **A new `traceId` per user-initiated operation** (cold start, login attempt, checkout) — they are independent. |
| **parentSpanId** | Nests a child span under a parent, **within the same trace**. | `login_api_call` is a child of `login_flow`; both share the login `traceId`. |
| **attribute** | A key/value tag on a span (`http_status=200`, `error=OUT_OF_STOCK`). | Setting an `error` attribute **guarantees the span is captured** (see sampling). |

> **Don't span instant events.** A button tap / item selection has no meaningful duration — log it (`HLogger`) or track it (`HAnalytics`) instead. Spanning zero-duration events floods the APM data with noise and no insight.

## 3. Architecture

```
api/                         ← the only surface callers see
  APM.kt                     ← facade (object) + `Perf` typealias
  PerformanceTracer.kt       ← minimal contract (startSpan/endSpan/flush)
  Span.kt                    ← public handle: spanId/traceId/parentSpanId + setAttribute
  TraceContext.kt            ← coroutine-propagatable correlation ids + currentTraceContext()
  PerformanceConfig.kt       ← immutable config consumed by APM.init()
  PerformanceModule.kt       ← Koin: republishes APM.asTracer()
internal/                    ← everything below is `internal` to the module
  PerformanceFactory.kt      ← composition root (the only place naming concrete types)
  PerformanceTracerImpl.kt   ← orchestrates start→end→sample→enqueue→dispatch
  RecordingSpan.kt           ← the real mutable Span (monotonic timing + attributes)
  model/{CompletedSpan,SpanContext}.kt
  sampling/{Sampler,AdaptiveSampler}.kt
  store/{SpanStore,InMemorySpanStore}.kt
  export/{SpanExporter,RemoteSpanExporter,ConsoleSpanExporter,SafeSpanExporter}.kt
  dispatch/{BatchSpanDispatcher,RetryPolicy}.kt
```

The `endSpan` pipeline: **finalize → sample → enqueue → batch-dispatch → export (retry / dead-letter)**. Design patterns map: Facade (`APM`), Factory/Composition-root (`PerformanceFactory`), Strategy (`Sampler`), Decorator (`SafeSpanExporter`), Ports & Adapters (`SpanStore` / `SpanExporter` interfaces). Dependencies point inward; concrete types are named only in the factory (DIP).

## 4. The two entry points (and why they don't conflict)

Configuration happens **once**, in the app's single `APM.init(...)` at startup. Both entry points resolve to the *same* tracer:

- **Static facade** — `APM.startSpan(...)` / `APM.endSpan(...)`. Best for framework seams that aren't DI-managed (the `Application`, an `Activity`).
- **Koin** — `koinInject<PerformanceTracer>()` / constructor injection. `performanceModule` **republishes** `APM.asTracer()`, so it is not a second configuration — it's the same pipeline behind an interface.

Before `init()` the facade routes to a no-op tracer that hands back inert spans, so a misordered call records nothing instead of crashing. (Corollary: call `APM.init()` **before** the first span you actually want captured.)

## 5. Getting started

```kotlin
// Application.onCreate — once per process.
APM.init(
    PerformanceConfig(
        appVersion = BuildConfig.VERSION_NAME,
        deviceModel = Build.MODEL,
        osVersion = "Android ${Build.VERSION.RELEASE}",
        locale = Locale.getDefault().toLanguageTag(),
        isDebug = BuildConfig.DEBUG,          // debug keeps every span + prints the tree
        onDiagnostic = { Log.w("APM", it) },  // dropped spans / exporter failures
    )
)
// Convenience when you only have the flag: APM.init(isDebug = BuildConfig.DEBUG)
```

Add `performanceModule` to your Koin graph (already wired in `:shared` `initKoin`) and inject `PerformanceTracer` where you prefer DI.

## 6. Lifecycle cookbook

The full journey — process launch → checkout — with span nesting and `traceId` correlation.

### 6.1 Cold start (wired in `:androidApp`)

Cold start gets its **own** `traceId` (`appSessionId`) — the user isn't authenticated yet, so it's independent of any later login/session trace. It starts in `Application.onCreate()` and ends only when the first frame is actually drawn.

```kotlin
class OdoApplication : Application() {
    val appSessionId: String = UUID.randomUUID().toString()
    lateinit var coldStartSpan: Span; private set

    override fun onCreate() {
        super.onCreate()
        configureLogging(BuildConfig.DEBUG)
        configureApm(BuildConfig.DEBUG)                 // APM up first, so the span is real
        coldStartSpan = APM.startSpan("app_cold_start", traceId = appSessionId)
            .setAttribute("launch_type", "cold")
        initKoin(/* ... */)                             // the heavy wiring runs inside the span
    }
}

// MainActivity — end the span only once the UI is truly interactive.
window.decorView.post {
    reportFullyDrawn()                                  // Android's "UI usable now" signal
    APM.endSpan((application as OdoApplication).coldStartSpan)
}
```

### 6.2 Login — a new trace, a nested API span

```kotlin
// ViewModel: one traceId per login attempt; install it on the coroutine so the
// repository can open a nested span on the SAME trace without threading the id.
fun onLoginClicked(email: String, password: String) {
    val trace = loginFlow.withNewTrace("trace-login-${UUID.randomUUID()}")
    val loginSpan = APM.startSpan("login_flow", trace.traceId!!)
    viewModelScope.launch(trace) {
        loginUseCase(email, password, loginSpan)
        APM.endSpan(loginSpan)
    }
}

// Repository: nested span = just the network call, inside login_flow.
suspend fun login(email: String, password: String, parent: Span): Result<Unit> {
    val trace = currentTraceContext()                  // read the installed trace back
    val apiSpan = APM.startSpan("login_api_call", trace.traceId!!, parentSpanId = parent.spanId)
    return try {
        val res = authApi.login(email, password)
        apiSpan.setAttribute("http_status", 200); Result.success(Unit)
    } catch (e: HttpException) {
        apiSpan.setAttribute("http_status", e.code()).setAttribute("error", "INVALID_CREDENTIALS")
        Result.failure(e)
    } finally {
        APM.endSpan(apiSpan)
    }
}
```

Because `login_api_call` nests inside `login_flow`, you can compare them: if the flow is slow but the API is fast, the problem is client-side validation/UI, not the server.

### 6.3 Product click — no span for instant events

```kotlin
fun onItemSelected(itemId: String) {
    // Instant UI event → NO span. Log + analytics only.
    HLogger.tag("CATALOG").i("item_selected", mapOf("itemId" to itemId))
    HAnalytics.track("item_selected", mapOf("item_id" to itemId))
}

fun onProductDetailsOpened(itemId: String) {
    // Duration-worthy (image/API load) → span.
    val span = APM.startSpan("product_details_load", trace.traceId!!)
    // ...load... then:
    APM.endSpan(span)
}
```

### 6.4 Checkout — nested spans + error attribute = guaranteed capture

```kotlin
val checkoutSpan = APM.startSpan("checkout_flow", trace.traceId!!)
val inventorySpan = APM.startSpan("inventory_check_api", trace.traceId!!, checkoutSpan.spanId)
try {
    // ... API returns 409 Out of Stock ...
    inventorySpan.setAttribute("error", "OUT_OF_STOCK").setAttribute("http_status", 409)
    return Result.failure(Exception("Out of stock"))
} finally {
    APM.endSpan(inventorySpan)   // the `error` attribute makes AdaptiveSampler ALWAYS keep it
    APM.endSpan(checkoutSpan)
}
```

What the dashboard shows:

```
trace-checkout-001
└─ span: checkout_flow (620ms)
   └─ span: inventory_check_api (580ms) ── error=OUT_OF_STOCK, http_status=409
```

## 7. Sampling — why your errors always survive

`AdaptiveSampler` samples by how *interesting* a span is, not blindly by rate:

1. **Debug builds** keep everything (`isDebug = true`).
2. **Errored spans** (any span with an `error` attribute) are **always** kept.
3. **Slow spans** (≥ `slowSpanThreshold`, default 2s) are **always** kept — the latency tail is the point.
4. Everything else — the fast, successful majority — is kept at `sampleRate` (default 20%), so the healthy common case doesn't flood the backend.

## 8. Delivery pipeline (batching, retry, dead-letter)

Kept spans are enqueued in a `SpanStore` and drained by `BatchSpanDispatcher` — size-triggered (`batchSize`) or time-triggered (`flushInterval`). Each span is exported to **every** `SpanExporter`; a failure is retried with exponential backoff (`RetryPolicy`) and **dead-lettered** (surfaced via `onDiagnostic`, then removed) after max attempts so it can't wedge the queue. A span is removed from the store only once delivery is confirmed. `SafeSpanExporter` isolates each backend but **re-throws** on `export` (unlike analytics' `SafeDestination`) so the dispatcher sees the failure and can retry.

> The default `InMemorySpanStore` and stub `RemoteSpanExporter` are the dev/test defaults; production swaps in a SQLDelight-backed store (survives process death) and a real OTLP/HTTP exporter — behind the same interfaces, no call-site change.

## 9. Testing

`./gradlew :observability:performance:allTests` (Android host + iOS sim). The suite exercises the real pipeline through the internal ports (`SpanStore` / `SpanExporter`) with test doubles in `TestFakes.kt`, plus trace-context coroutine propagation, adaptive sampling boundaries, the double-end guard, nesting, and the DI binding.

## 10. Design rules (carry into new code)

- **A span only for real durations.** Instant events get a log/analytics event, never a span.
- **New `traceId` per user-initiated operation; nest with `parentSpanId` only within one trace.**
- **`error` attribute ⇒ guaranteed capture** — set it on failure paths.
- **Everything except the `api/` package stays `internal`.** New backends implement `SpanExporter`; new buffers implement `SpanStore`; the factory is the only place that names concrete types.
- **No `java.util.concurrent` / `synchronized`** — stay KMP-native (atomics + coroutine `Mutex`), like the sibling modules.
