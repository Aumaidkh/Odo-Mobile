package com.hopcape.odo.feature.refuel.presentation

import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.logging.api.Logger
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.performance.api.PerformanceTracer
import com.hopcape.logging.api.TraceContext as LogTrace
import com.hopcape.performance.api.TraceContext as PerfTrace
import kotlin.coroutines.CoroutineContext

/**
 * All observability for refuel, behind intent-named methods, so the ViewModels read as their
 * screens' logic rather than a wall of logger, analytics and tracer calls.
 *
 * The question this telemetry exists to answer is which capture channel owners actually
 * finish with. The whole feature is a bet that a fill logged in one tap gets logged at all,
 * and the only way to check it is to count starts and completions per channel.
 *
 * **No PII.** Channel names, counts, booleans and whether a field was corrected — never a
 * station name, an amount, an odometer reading or a merchant. What the owner paid at their
 * pump is not something a dashboard needs.
 *
 * Every method is fire-and-forget: nothing here returns a decision, so instrumentation
 * cannot change what a screen does.
 */
internal class RefuelTelemetry(
    private val logger: Logger,
    private val analytics: AnalyticsTracker,
    @Suppress("unused") private val tracer: PerformanceTracer,
    private val ids: IdGenerator,
) {

    private val flowTraceId: String = "${FLOW}_${ids.newId()}"
    private val flowTrace = PerfTrace(flowId = FLOW, traceId = flowTraceId)

    /** The child-trace context to `launch(...)` an async op under. */
    fun op(name: String): CoroutineContext = flowTrace.withNewTrace("${name}_${ids.newId()}")

    /**
     * A draft reached the confirm step, with how much of it was already filled in.
     *
     * [prefilledFields] is the feature's own success measure. A confirm step that opens with
     * one field known is barely better than the form it replaced; the point of every channel
     * is to arrive with three or four.
     */
    fun confirmOpened(source: String, prefilledFields: Int, odometerPredicted: Boolean) {
        val fields = mapOf(
            Key.SOURCE to source,
            Key.PREFILLED_FIELDS to prefilledFields,
            Key.ODOMETER_PREDICTED to odometerPredicted,
        )
        analytics.track(Event.CONFIRM_OPENED, fields)
        logger.info(TAG, Event.CONFIRM_OPENED, tc = flowTrace.toLog(), fields = fields)
    }

    /**
     * A fill was written, and whether the owner had to change anything on the way.
     *
     * [corrected] is what says whether a channel's numbers can be trusted. A detected fill
     * the owner edits every time is a detection that is not working, and it looks identical
     * to a good one without this.
     */
    fun fillLogged(source: String, corrected: Boolean) {
        val fields = mapOf(Key.SOURCE to source, Key.CORRECTED to corrected)
        analytics.track(Event.FILL_LOGGED, fields)
        logger.info(TAG, Event.FILL_LOGGED, tc = flowTrace.toLog(), fields = fields)
    }

    /** The write was refused. [reason] is the domain error's name, never its contents. */
    fun fillRefused(source: String, reason: String) {
        val fields = mapOf(Key.SOURCE to source, Key.REASON to reason)
        analytics.track(Event.FILL_REFUSED, fields)
        logger.warn(TAG, Event.FILL_REFUSED, tc = flowTrace.toLog(), fields = fields)
    }

    /**
     * The owner said a captured fill was not fuel at all.
     *
     * The counterweight to [fillLogged]: a channel that is often rejected is one that is
     * costing the owner taps instead of saving them.
     */
    fun captureRejected(source: String) {
        val fields = mapOf(Key.SOURCE to source)
        analytics.track(Event.CAPTURE_REJECTED, fields)
        logger.info(TAG, Event.CAPTURE_REJECTED, tc = flowTrace.toLog(), fields = fields)
    }

    /** A measured mileage was shown after logging — the payoff line on the success screen. */
    fun tankInsightShown(comparison: String) {
        val fields = mapOf(Key.COMPARISON to comparison)
        analytics.track(Event.TANK_INSIGHT_SHOWN, fields)
        logger.debug(TAG, Event.TANK_INSIGHT_SHOWN, tc = flowTrace.toLog(), fields = fields)
    }

    /** The owner turned payment-notification detection on or off. */
    fun detectionToggled(enabled: Boolean) {
        val fields = mapOf(Key.ENABLED to enabled)
        analytics.track(Event.DETECTION_TOGGLED, fields)
        logger.info(TAG, Event.DETECTION_TOGGLED, tc = flowTrace.toLog(), fields = fields)
    }

    /**
     * The owner advanced the opt-in's setup chain.
     *
     * [step] is the step the tap was *on*, so the drop-off between the two permission asks is
     * readable. That gap is the one worth watching: the second ask's system dialog warns about
     * reading all notifications, and an owner who stops there has a working notification
     * permission and no detection at all.
     */
    fun setupStepTaken(step: String) {
        val fields = mapOf(Key.STEP to step)
        analytics.track(Event.SETUP_STEP_TAKEN, fields)
        logger.info(TAG, Event.SETUP_STEP_TAKEN, tc = flowTrace.toLog(), fields = fields)
    }

    private fun PerfTrace.toLog(): LogTrace =
        LogTrace(sessionId = sessionId, flowId = flowId, traceId = traceId)

    object Event {
        const val CONFIRM_OPENED = "refuel_confirm_opened"
        const val FILL_LOGGED = "refuel_fill_logged"
        const val FILL_REFUSED = "refuel_fill_refused"
        const val CAPTURE_REJECTED = "refuel_capture_rejected"
        const val TANK_INSIGHT_SHOWN = "refuel_tank_insight_shown"
        const val DETECTION_TOGGLED = "refuel_detection_toggled"
        const val SETUP_STEP_TAKEN = "refuel_setup_step_taken"
    }

    object Key {
        const val SOURCE = "source"
        const val PREFILLED_FIELDS = "prefilled_fields"
        const val ODOMETER_PREDICTED = "odometer_predicted"
        const val CORRECTED = "corrected"
        const val REASON = "reason"
        const val COMPARISON = "comparison"
        const val ENABLED = "enabled"
        const val STEP = "step"
    }

    private companion object {
        const val TAG = "Refuel"
        const val FLOW = "refuel"
    }
}
