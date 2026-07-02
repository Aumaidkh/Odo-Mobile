@file:OptIn(ExperimentalAtomicApi::class)

package com.hopcape.analytics.api

import com.hopcape.analytics.internal.AnalyticsFactory
import com.hopcape.analytics.internal.model.GlobalContext
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

// ─────────────────────────────────────────────────────────────
// HAnalytics — the Facade. Mirrors HLogger's shape deliberately so
// the two observability systems feel consistent to engineers using
// both. This is the ONE composition call and the entry point most
// call sites use.
//
// Design choices (same rationale as HLogger):
// - init() must be called once (Application.onCreate). Enforced via
//   an AtomicBoolean CAS — a duplicate call is a no-op + diagnostic,
//   never a crash or a re-wire (fail-safe applied to the API itself).
// - Before init() the delegate is a no-op tracker — a misordered
//   call logs nothing instead of throwing in production.
// - The facade owns the mutable GlobalContext ("super properties");
//   the delegate reads it live through a provider, so session/user
//   updates land on every subsequent event without re-wiring.
// ─────────────────────────────────────────────────────────────
object HAnalytics {

    private val initialized = AtomicBoolean(false)

    @Volatile
    private var delegate: AnalyticsTracker = NoOpTracker

    @Volatile
    private var globalContext = GlobalContext(
        appVersion = "", deviceModel = "", osVersion = "", locale = "",
    )

    /**
     * Must be called exactly once, typically from `Application.onCreate()`.
     * Safe to call again (idempotent no-op + diagnostic) — it never throws,
     * so a duplicate init in test setup or a multi-process app can't crash
     * the host.
     */
    @JvmStatic
    fun init(config: AnalyticsConfig) {
        if (!initialized.compareAndSet(false, true)) {
            config.onDiagnostic("HAnalytics already initialized — ignoring duplicate init()")
            return
        }
        globalContext = GlobalContext(
            appVersion = config.appVersion,
            deviceModel = config.deviceModel,
            osVersion = config.osVersion,
            locale = config.locale,
        )
        delegate = AnalyticsFactory.create(config) { globalContext }
    }

    /** Sets the process-wide session id (e.g. per app-open), stamped onto subsequent events. */
    @JvmStatic
    fun setSession(sessionId: String) {
        globalContext = globalContext.copy(sessionId = sessionId)
    }

    /**
     * Records the user identity: stamps [userId] onto subsequent events' context
     * and forwards the traits to every destination's `identify`.
     */
    @JvmStatic
    @JvmOverloads
    fun identify(userId: String, traits: Map<String, Any?> = emptyMap()) {
        globalContext = globalContext.copy(userId = userId)
        delegate.identify(UserTraits(userId, traits))
    }

    @JvmStatic
    fun setConsent(status: ConsentStatus) = delegate.setConsent(status)

    @JvmStatic
    @JvmOverloads
    fun track(eventName: String, properties: Map<String, Any?> = emptyMap()) =
        delegate.track(eventName, properties)

    @JvmStatic
    fun flush() = delegate.flush()

    /**
     * Exposes the facade's underlying [AnalyticsTracker] for DI graphs, so a
     * `koinInject<AnalyticsTracker>()` and a static `HAnalytics.track(...)` call
     * share ONE configuration and ONE set of destinations. This is what
     * [analyticsModule] binds.
     *
     * Reflects [init] live: before init() it routes to the no-op fallback;
     * after, to the configured pipeline. Injectors therefore never need to
     * sequence against init().
     */
    @JvmStatic
    fun asTracker(): AnalyticsTracker = DelegatingTracker

    /** Stable reference for DI that always forwards to the live [delegate]. */
    private object DelegatingTracker : AnalyticsTracker {
        override fun identify(traits: UserTraits) = delegate.identify(traits)
        override fun track(eventName: String, properties: Map<String, Any?>) =
            delegate.track(eventName, properties)

        override fun setConsent(status: ConsentStatus) = delegate.setConsent(status)
        override fun flush() = delegate.flush()
    }

    /** Pre-init fallback — satisfies the contract (LSP) while dropping everything. */
    private object NoOpTracker : AnalyticsTracker {
        override fun identify(traits: UserTraits) {}
        override fun track(eventName: String, properties: Map<String, Any?>) {}
        override fun setConsent(status: ConsentStatus) {}
        override fun flush() {}
    }
}

/**
 * Brandable, ergonomic alias for the [HAnalytics] facade — the preferred name at
 * call sites (`Track.track("bill_scanned", ...)`). It is exactly [HAnalytics];
 * both names refer to the same object, so there is no second entry point.
 */
typealias Track = HAnalytics
