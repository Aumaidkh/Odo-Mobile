package com.hopcape.odo.infrastructure.firebase.performance

// MVP is Android-only (see CLAUDE.md) and no gitlive KMP wrapper exists for
// Firebase Performance Monitoring, so iOS gets a no-op that reports every span
// as handled — nothing to record, nothing to retry.
internal actual fun createFirebaseTraceGateway(onDiagnostic: (String) -> Unit): FirebaseTraceGateway =
    NoOpFirebaseTraceGateway

internal object NoOpFirebaseTraceGateway : FirebaseTraceGateway {
    override fun record(traceName: String, durationMs: Long, attributes: Map<String, String>): Boolean = true
}
