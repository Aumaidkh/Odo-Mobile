package com.hopcape.odo.infrastructure.firebase.performance

import com.google.firebase.perf.FirebasePerformance
import com.hopcape.odo.core.common.runCatchingCancellable

internal actual fun createFirebaseTraceGateway(onDiagnostic: (String) -> Unit): FirebaseTraceGateway =
    RealFirebaseTraceGateway(onDiagnostic)

/**
 * `FirebasePerformance.getInstance()` throws when no `FirebaseApp` has been configured — a
 * missing `google-services.json`, which this repo's own config is (see the crashlytics/analytics
 * gateways' KDoc for the same situation). Resolution is lazy (not a constructor default) and
 * every call is caught via [runCatchingCancellable] (not `runCatching` — that would also swallow
 * a coroutine's cancellation), so a misconfigured Firebase project degrades this one sink instead
 * of crashing app launch.
 *
 * [provider] exists only so a test can inject a throwing lookup without a real Firebase project —
 * production always uses the default.
 */
internal class RealFirebaseTraceGateway(
    private val onDiagnostic: (String) -> Unit = {},
    private val provider: () -> FirebasePerformance = { FirebasePerformance.getInstance() },
) : FirebaseTraceGateway {

    private val performance: FirebasePerformance? by lazy {
        runCatchingCancellable(provider)
            .onFailure { onDiagnostic("firebase-perf: unavailable — ${it::class.simpleName}") }
            .getOrNull()
    }

    override fun record(traceName: String, durationMs: Long, attributes: Map<String, String>): Boolean {
        // No diagnostic here on a null instance — the lazy above already reported
        // "unavailable" once at first access; repeating it per span would spam the channel.
        val current = performance ?: return true // permanently unavailable — handled, not retried
        return runCatchingCancellable {
            val trace = current.newTrace(traceName)
            trace.start()
            trace.putMetric(DURATION_METRIC, durationMs)
            attributes.forEach { (key, value) -> trace.putAttribute(key, value) }
            trace.stop()
        }.onFailure { onDiagnostic("firebase-perf: record failed — ${it::class.simpleName}") }
            .isSuccess
    }

    private companion object {
        const val DURATION_METRIC = "duration_ms"
    }
}
