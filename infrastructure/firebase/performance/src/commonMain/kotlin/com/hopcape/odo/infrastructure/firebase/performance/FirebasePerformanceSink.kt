package com.hopcape.odo.infrastructure.firebase.performance

import com.hopcape.performance.api.SpanSink

// ─────────────────────────────────────────────────────────────
// FirebasePerformanceSink — the SpanSink adapter registered as a
// destination via PerformanceConfig.destinations (see
// OdoApplication.configureApm). A span always finishes before this
// sink sees it, so the Firebase Trace it opens has a near-zero native
// duration — the real value is written as the `duration_ms` custom
// metric instead. Firebase's automatic instrumentation (app start,
// network calls) is the source of real-duration traces; this sink
// only carries the app's own custom spans.
//
// `build_type`/`is_error` are always added so the console can filter
// debug-session noise from a shared Firebase project (this app has no
// separate debug applicationId) and surface failures without a
// second dashboard query.
//
// Public, unlike the rest of this module: APM.init(...) runs in
// OdoApplication.onCreate before the Koin graph starts, so :androidApp
// constructs this directly rather than resolving it through DI — the
// same reason :infrastructure:firebase:analytics's FirebaseAnalyticsSink
// is public. Only the public constructor's params are part of that
// surface; the gateway/sanitizer seam stays internal for this module's
// own tests.
// ─────────────────────────────────────────────────────────────
class FirebasePerformanceSink internal constructor(
    private val onDiagnostic: (String) -> Unit = {},
    private val buildType: String = "release",
    private val gateway: FirebaseTraceGateway = createFirebaseTraceGateway(onDiagnostic),
    private val sanitizer: FirebaseTraceSanitizer = FirebaseTraceSanitizer(onDiagnostic),
) : SpanSink {

    constructor(onDiagnostic: (String) -> Unit = {}, buildType: String = "release") : this(
        onDiagnostic = onDiagnostic,
        buildType = buildType,
        gateway = createFirebaseTraceGateway(onDiagnostic),
        sanitizer = FirebaseTraceSanitizer(onDiagnostic),
    )

    override val name: String = "firebase"

    override fun export(
        name: String,
        traceId: String,
        spanId: String,
        parentSpanId: String?,
        startEpochMs: Long,
        durationMs: Long,
        isError: Boolean,
        attributes: Map<String, String>,
    ): Boolean {
        // An invalid name will never become valid on retry — handled, not retried.
        val sanitizedName = sanitizer.sanitizeName(name) ?: return true
        val enriched = attributes + mapOf("build_type" to buildType, "is_error" to isError.toString())
        return gateway.record(sanitizedName, durationMs, sanitizer.sanitizeAttributes(enriched))
    }

    override fun flush() {
        // The Firebase Performance SDK exposes no explicit flush; it batches and
        // uploads internally.
    }
}
