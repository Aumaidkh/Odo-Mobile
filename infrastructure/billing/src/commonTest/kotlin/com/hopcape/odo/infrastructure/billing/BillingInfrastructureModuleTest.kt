package com.hopcape.odo.infrastructure.billing

import com.hopcape.crashreporting.api.CrashRecorder
import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.api.Logger as OdoLogger
import com.hopcape.logging.api.TraceContext
import com.hopcape.odo.infrastructure.billing.observability.BillingTelemetry
import org.koin.core.context.stopKoin
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The one thing this module decides today: whether a build has a key to configure the store
 * with.
 *
 * Only the unconfigured branch is exercised. Configuring for real reaches Play Billing, which
 * a host test has no way to satisfy — and that branch is what an internal-track build is for
 * (docs/PAYWALL_PLAN.md, risks). What is worth guarding here is the other one: a checkout
 * with no credentials, which is the state of every fresh clone and every CI run, must start
 * the graph without touching the store.
 */
class BillingInfrastructureModuleTest {

    private val recorded = RecordingLogger()

    @AfterTest
    fun tearDown() = stopKoin()

    @Test
    fun anUnconfiguredBuildStartsWithoutTouchingTheStore() {
        graph(BillingEnvironment(apiKey = ""))

        assertEquals(
            listOf("${BillingTelemetry.CONFIGURE}.skipped"),
            recorded.events,
            "an unconfigured build says why, once, and does nothing else",
        )
    }

    @Test
    fun theReasonIsRecordedSoTheQuietPaywallIsExplainable() {
        graph(BillingEnvironment(apiKey = ""))

        assertEquals(BillingTelemetry.NO_API_KEY, recorded.fields.single()[BillingTelemetry.Key.REASON])
    }

    @Test
    fun aBlankKeyIsNotConfiguredAndAKeyIs() {
        assertTrue(!BillingEnvironment(apiKey = "").isConfigured)
        assertTrue(BillingEnvironment(apiKey = "goog_test").isConfigured)
    }

    /** Starts the graph, which resolves the bootstrap eagerly — that is what runs configure. */
    private fun graph(environment: BillingEnvironment) = koinApplication {
        modules(
            module {
                single<OdoLogger> { recorded }
                single<CrashRecorder> { NoopCrashRecorder }
            },
            billingInfrastructureModule(environment),
        )
    }.also { it.createEagerInstances() }
}

private class RecordingLogger : OdoLogger {
    val events = mutableListOf<String>()
    val fields = mutableListOf<Map<String, Any?>>()

    override fun log(
        level: LogLevel,
        tag: String,
        event: String,
        traceContext: TraceContext?,
        fields: Map<String, Any?>,
    ) {
        events += event
        this.fields += fields
    }

    override fun flush() = Unit
}

private object NoopCrashRecorder : CrashRecorder {
    override fun recordNonFatal(throwable: Throwable, customKeys: Map<String, Any?>) = Unit
    override fun leaveBreadcrumb(tag: String, message: String) = Unit
    override fun setCustomKey(key: String, value: Any?) = Unit
    override fun setUserId(userId: String?) = Unit
}
