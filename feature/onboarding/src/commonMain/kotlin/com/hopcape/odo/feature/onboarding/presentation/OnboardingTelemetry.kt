package com.hopcape.odo.feature.onboarding.presentation

import arrow.core.Either
import arrow.core.NonEmptyList
import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.logging.api.Logger
import com.hopcape.logging.api.TraceContext as LogTrace
import com.hopcape.performance.api.PerformanceTracer
import com.hopcape.performance.api.TraceContext
import com.hopcape.performance.api.currentTraceContext
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.owner.model.OnboardingGoal
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.feature.onboarding.presentation.contract.StartDestination
import com.hopcape.odo.feature.onboarding.presentation.state.OnboardingStep
import kotlin.coroutines.CoroutineContext

/**
 * All observability for the car-setup flow, behind intent-named methods — so the
 * ViewModel reads as business logic, never as a wall of `logger`/`analytics`/`tracer`
 * calls. This one class owns the three ports, the taxonomy (nested [Event]/[Span]/
 * [Key]/[Trace] — still reachable as `OnboardingTelemetry.Event.*`), and the trace
 * plumbing:
 *
 *  - [flowTrace] is minted once here (this instance's lifetime == one onboarding
 *    attempt — hence a Koin `factory`, not a `single`).
 *  - [op] hands back the child-trace [CoroutineContext] to launch an async op under;
 *    the `suspend` methods then read [currentTraceContext] so the span traceId and
 *    the log traceId match that op — the caller threads nothing.
 *  - synchronous methods use the parent [flowTrace].
 *
 * Only non-PII context is emitted — make/fuel/goal/counts and error *types*, never
 * registration numbers, nicknames, or odometer values. Instrumentation stays at this
 * presentation layer; `:core:domain` remains framework-free.
 */
internal class OnboardingTelemetry(
    private val logger: Logger,
    private val analytics: AnalyticsTracker,
    private val tracer: PerformanceTracer,
    private val ids: IdGenerator,
) {

    private val flowTraceId: String = "onboarding_flow_${ids.newId()}"
    private val flowTrace = TraceContext(flowId = FLOW, traceId = flowTraceId)

    /** The child-trace context to `launch(...)` an async op under (`"<name>_<id>"`). */
    fun op(name: String): CoroutineContext = flowTrace.withNewTrace("${name}_${ids.newId()}")

    // ── Synchronous funnel (parent flow trace) ───────────────────────────────

    fun started() {
        analytics.track(Event.STARTED)
        logger.info(TAG, "onboarding_started", tc = flowTrace.toLog())
    }

    fun carDetailsSubmitted(make: String?, fuel: String?) {
        analytics.track(Event.CAR_DETAILS_SUBMITTED, mapOf(Key.MAKE to make, Key.FUEL_TYPE to fuel))
        logger.info(TAG, "car_details_submitted", tc = flowTrace.toLog(), fields = mapOf(Key.MAKE to make))
    }

    fun carDetailsInvalid(errorTypes: String, count: Int) {
        analytics.track(Event.CAR_DETAILS_INVALID, mapOf(Key.ERRORS to errorTypes, Key.ERROR_COUNT to count))
        logger.warn(TAG, "car_details_invalid", tc = flowTrace.toLog(), fields = mapOf(Key.ERRORS to errorTypes))
    }

    fun goalSelected(goal: OnboardingGoal) {
        analytics.track(Event.GOAL_SELECTED, mapOf(Key.GOAL to goal.name))
        logger.debug(TAG, "goal_selected", tc = flowTrace.toLog(), fields = mapOf(Key.GOAL to goal.name))
    }

    fun historySkipped() {
        analytics.track(Event.HISTORY_SKIPPED)
        logger.info(TAG, "history_skipped", tc = flowTrace.toLog())
    }

    fun scanClicked() {
        analytics.track(Event.HISTORY_SCAN_CLICKED)
        logger.info(TAG, "history_scan_clicked", tc = flowTrace.toLog())
    }

    fun abandoned(step: OnboardingStep) {
        analytics.track(Event.ABANDONED, mapOf(Key.STEP to step.name))
        logger.info(TAG, "onboarding_abandoned", tc = flowTrace.toLog(), fields = mapOf(Key.STEP to step.name))
    }

    fun stepBack(step: OnboardingStep) {
        logger.debug(TAG, "step_back", tc = flowTrace.toLog(), fields = mapOf(Key.STEP to step.name))
    }

    // ── Async ops (child trace via currentTraceContext, span wraps the duration) ──

    /** Times [read] under a catalog-load span and logs the outcome. */
    suspend fun catalogLoaded(read: suspend () -> List<String>): List<String> {
        val trace = currentTraceContext()
        val span = tracer.startSpan(Span.CATALOG_LOAD, trace.traceId ?: flowTraceId)
        val makes = read()
        span.setAttribute(Key.MAKE_COUNT, makes.size)
        if (makes.isEmpty()) span.setAttribute(Key.ERROR, "catalog_empty")
        tracer.endSpan(span)
        logger.info(TAG, "catalog_loaded", tc = trace.toLog(), fields = mapOf(Key.MAKE_COUNT to makes.size))
        return makes
    }

    /** Times [read] under a models-load span for [make] and logs the outcome. */
    suspend fun modelsLoaded(make: String, read: suspend () -> List<String>): List<String> {
        val trace = currentTraceContext()
        val span = tracer.startSpan(Span.MODELS_LOAD, trace.traceId ?: flowTraceId).setAttribute(Key.MAKE, make)
        val models = read()
        span.setAttribute(Key.COUNT, models.size)
        tracer.endSpan(span)
        logger.debug(TAG, "models_loaded", tc = trace.toLog(), fields = mapOf(Key.MAKE to make, Key.COUNT to models.size))
        return models
    }

    /**
     * Times [write] (the persistence) under an add-car span, stamping the outcome
     * (`car_id` on success, `error` reason on failure) onto the span. Returns the
     * result unchanged — the caller branches on it for state + [completed]/[failed].
     */
    suspend fun addCarWrite(
        goal: OnboardingGoal,
        write: suspend () -> Either<NonEmptyList<DomainError>, Car>,
    ): Either<NonEmptyList<DomainError>, Car> {
        val trace = currentTraceContext()
        val span = tracer.startSpan(Span.ADD_CAR, trace.traceId ?: flowTraceId).setAttribute(Key.GOAL, goal.name)
        val result = write()
        result.fold(
            ifLeft = { errors ->
                span.setAttribute(Key.ERROR, if (errors.all { it is DomainError.PersistenceFailure }) "persistence" else "validation")
            },
            ifRight = { car -> span.setAttribute(Key.CAR_ID, car.id.value) },
        )
        tracer.endSpan(span)
        return result
    }

    suspend fun completed(goal: OnboardingGoal, destination: StartDestination, carId: String) {
        analytics.track(
            Event.COMPLETED,
            mapOf(Key.GOAL to goal.name, Key.DESTINATION to destination.name, Key.CAR_ID to carId),
        )
        logger.info(
            TAG, "onboarding_completed",
            tc = currentTraceContext().toLog(),
            fields = mapOf(Key.GOAL to goal.name, Key.DESTINATION to destination.name),
        )
    }

    suspend fun failed(goal: OnboardingGoal, reason: String, errorTypes: String) {
        analytics.track(Event.FAILED, mapOf(Key.REASON to reason, Key.GOAL to goal.name))
        logger.error(
            TAG, "onboarding_failed",
            tc = currentTraceContext().toLog(),
            fields = mapOf(Key.REASON to reason, Key.ERRORS to errorTypes),
        )
    }

    suspend fun scanUnavailable() {
        logger.info(TAG, "history_scan_unavailable", tc = currentTraceContext().toLog())
    }

    suspend fun catalogReadFailed(message: String?) {
        logger.warn(TAG, "catalog_read_failed", tc = currentTraceContext().toLog(), fields = mapOf(Key.ERROR to (message ?: "unknown")))
    }

    /** Bridge the coroutine-propagating performance trace onto the logging DTO. */
    private fun TraceContext.toLog(): LogTrace = LogTrace(sessionId = sessionId, flowId = flowId, traceId = traceId)

    /**
     * The feature's observability taxonomy — the single source of truth for every
     * event/span/field/trace name. Reachable as `OnboardingTelemetry.Event.*` etc.
     */
    companion object {
        const val TAG: String = "ONBOARDING"
        const val FLOW: String = "onboarding"
    }

    /** Analytics event names — the acquisition funnel, welcome carousel → car setup. */
    object Event {
        const val WELCOME_SHOWN = "onboarding_welcome_shown"
        const val WELCOME_SLIDE_VIEWED = "onboarding_welcome_slide_viewed"
        const val WELCOME_SKIPPED = "onboarding_welcome_skipped"
        const val WELCOME_COMPLETED = "onboarding_welcome_completed"

        const val STARTED = "onboarding_started"
        const val CAR_DETAILS_SUBMITTED = "onboarding_car_details_submitted"
        const val CAR_DETAILS_INVALID = "onboarding_car_details_invalid"
        const val HISTORY_SCAN_CLICKED = "onboarding_history_scan_clicked"
        const val HISTORY_SKIPPED = "onboarding_history_skipped"
        const val GOAL_SELECTED = "onboarding_goal_selected"
        const val COMPLETED = "onboarding_completed"
        const val FAILED = "onboarding_failed"
        const val ABANDONED = "onboarding_abandoned"
    }

    /** Performance span names — only genuine *durations* (async catalog/DB work). */
    object Span {
        const val CATALOG_LOAD = "onboarding_catalog_load"
        const val MODELS_LOAD = "onboarding_models_load"
        const val ADD_CAR = "onboarding_add_car"
    }

    /** Structured field / property keys, shared across logs, events, and spans. */
    object Key {
        const val SLIDE = "slide"
        const val SLIDE_INDEX = "slide_index"
        const val FROM_SLIDE = "from_slide"
        const val STEP = "step"
        const val GOAL = "goal"
        const val DESTINATION = "destination"
        const val CAR_ID = "car_id"
        const val MAKE = "make"
        const val FUEL_TYPE = "fuel_type"
        const val MAKE_COUNT = "make_count"
        const val COUNT = "count"
        const val ERRORS = "errors"
        const val ERROR_COUNT = "error_count"
        const val REASON = "reason"
        const val ERROR = "error"
    }

    /** Child-trace id prefixes for each async op (were string literals in the VM). */
    object Trace {
        const val CATALOG_LOAD = "load_catalog"
        const val MODELS_LOAD = "load_models"
        const val ADD_CAR = "add_car"
        const val SCAN = "scan"
    }
}
