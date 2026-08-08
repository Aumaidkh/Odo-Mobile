package com.hopcape.odo.infrastructure.firebase.performance

// ─────────────────────────────────────────────────────────────
// FirebaseTraceGateway — the real Firebase SDK surface this module
// calls, narrowed to what FirebasePerformanceSink needs. The whole
// newTrace/start/putMetric/putAttribute/stop sequence stays inside
// the gateway, so the sink itself has no Firebase-shaped logic and is
// fully testable with a fake — the seam exists because the SDK's
// Trace is a concrete class, not an interface. Mirrors
// :infrastructure:firebase:analytics's FirebaseAnalyticsGateway.
//
// Unlike the analytics/crashlytics gateways (built on gitlive, so one
// commonMain implementation covers every KMP target), this one is a
// genuine expect/actual: it wraps the NATIVE Android SDK, which has no
// gitlive KMP wrapper on this repo's dependency version (see the
// version catalog comment on libs.firebase.perf).
// ─────────────────────────────────────────────────────────────
internal interface FirebaseTraceGateway {
    /**
     * Records one already-finished span as a Firebase custom trace. Returns whether
     * the SDK accepted the call — false only for a genuinely transient failure; a
     * permanently unconfigured/unavailable Firebase project returns true (handled,
     * not retried) so a missing google-services.json can't wedge the dispatcher into
     * retrying every span forever.
     */
    fun record(traceName: String, durationMs: Long, attributes: Map<String, String>): Boolean
}

/** Platform factory — see the androidMain/iosMain actuals for what each target constructs. */
internal expect fun createFirebaseTraceGateway(onDiagnostic: (String) -> Unit): FirebaseTraceGateway
