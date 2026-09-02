package com.hopcape.odo.feature.questionnaire.firstrun.presentation

import arrow.core.Either
import arrow.core.EitherNel
import arrow.core.NonEmptyList
import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.logging.api.Logger
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.domain.car.catalog.CarModel
import com.hopcape.odo.core.domain.car.lookup.RegisteredVehicle
import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.owner.model.OnboardingGoal
import com.hopcape.odo.core.domain.owner.model.OwnerProfile
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.state.CatalogOptions
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.state.OnboardingStep
import com.hopcape.performance.api.PerformanceTracer
import com.hopcape.performance.api.Span
import com.hopcape.performance.api.currentTraceContext
import kotlin.coroutines.CoroutineContext
import com.hopcape.logging.api.TraceContext as LogTrace
import com.hopcape.performance.api.TraceContext as PerfTrace

/**
 * All observability for first-run setup, behind intent-named methods — so the ViewModels read
 * as the flow's logic rather than as a wall of `logger`/`analytics`/`tracer` calls.
 *
 * This one class owns the three ports, the whole taxonomy ([Event]/[Trace]/[Key], reachable as
 * `SetupTelemetry.Event.*`), and the trace plumbing:
 *
 *  - [flowTrace] is minted per instance. A Koin `factory`, so one instance covers one attempt
 *    at the journey; both first-run ViewModels share the same [FLOW] id, which is what stitches
 *    the welcome pitch and the setup steps into one funnel even though they are two
 *    destinations with two traces.
 *  - [op] hands back the child-trace [CoroutineContext] to launch an async op under. The
 *    `suspend` methods then read [currentTraceContext] themselves, so a span's traceId and its
 *    log lines match that op without the caller threading anything.
 *
 * **Only non-PII context is emitted** — make, fuel, goal, step, counts, and error *type* names.
 * Never a registration number, an owner's name, or an odometer reading: a plate identifies a
 * person's car and a reading is resale-relevant, and neither belongs in an analytics warehouse.
 * Instrumentation stays at this presentation layer; `:core:domain` remains framework-free.
 *
 * Every method is fire-and-forget by contract: nothing here returns a decision, and the
 * wrapping methods hand back their block's result untouched, so instrumentation can never
 * change what the flow does.
 */
internal class SetupTelemetry(
    private val logger: Logger,
    private val analytics: AnalyticsTracker,
    private val tracer: PerformanceTracer,
    private val ids: IdGenerator,
) {

    private val flowTraceId: String = "${FLOW}_${ids.newId()}"
    private val flowTrace = PerfTrace(flowId = FLOW, traceId = flowTraceId)

    /** The child-trace context to `launch(...)` an async op under (`"<name>_<id>"`). */
    fun op(name: String): CoroutineContext = flowTrace.withNewTrace("${name}_${ids.newId()}")

    /* ------------------------------ Funnel ------------------------------ */

    fun started() {
        analytics.track(Event.STARTED)
        logger.info(TAG, Event.STARTED, tc = flowTrace.toLog())
    }

    fun stepAdvanced(from: OnboardingStep) {
        analytics.track(Event.STEP_ADVANCED, mapOf(Key.STEP to from.name))
        logger.info(TAG, Event.STEP_ADVANCED, tc = flowTrace.toLog(), fields = mapOf(Key.STEP to from.name))
    }

    fun stepBack(from: OnboardingStep) {
        logger.debug(TAG, Event.STEP_BACK, tc = flowTrace.toLog(), fields = mapOf(Key.STEP to from.name))
    }

    /**
     * The owner left setup from [step]. Tracked because where people give up is the single most
     * actionable thing about a first-run flow.
     */
    fun abandoned(step: OnboardingStep) {
        analytics.track(Event.ABANDONED, mapOf(Key.STEP to step.name))
        logger.info(TAG, Event.ABANDONED, tc = flowTrace.toLog(), fields = mapOf(Key.STEP to step.name))
    }

    /**
     * The owner chose to type the car in by hand. [hadMatch] separates "the registry was wrong"
     * from "the registry gave us nothing" — the two say very different things about whether a
     * lookup integration is worth having.
     */
    fun manualEntryChosen(hadMatch: Boolean) {
        analytics.track(Event.MANUAL_ENTRY_CHOSEN, mapOf(Key.HAD_MATCH to hadMatch))
        logger.info(TAG, Event.MANUAL_ENTRY_CHOSEN, tc = flowTrace.toLog(), fields = mapOf(Key.HAD_MATCH to hadMatch))
    }

    fun goalSelected(goal: OnboardingGoal) {
        analytics.track(Event.GOAL_SELECTED, mapOf(Key.GOAL to goal.name))
        logger.debug(TAG, Event.GOAL_SELECTED, tc = flowTrace.toLog(), fields = mapOf(Key.GOAL to goal.name))
    }

    fun firstScanClicked() {
        analytics.track(Event.FIRST_SCAN_CLICKED)
        logger.info(TAG, Event.FIRST_SCAN_CLICKED, tc = flowTrace.toLog())
    }

    fun firstScanSkipped() {
        analytics.track(Event.FIRST_SCAN_SKIPPED)
        logger.info(TAG, Event.FIRST_SCAN_SKIPPED, tc = flowTrace.toLog())
    }

    // `destination` used to be a field here. It is gone with goal-based routing, which sent
    // every goal to the same screen — the property was a constant and told a dashboard nothing.
    fun completed(goal: OnboardingGoal?, signInOffered: Boolean) {
        val fields = mapOf(
            Key.GOAL to (goal?.name ?: UNSET),
            Key.SIGN_IN_OFFERED to signInOffered,
        )
        analytics.track(Event.COMPLETED, fields)
        logger.info(TAG, Event.COMPLETED, tc = flowTrace.toLog(), fields = fields)
    }

    /* ------------------------------ Async ops ------------------------------ */

    /** Times the catalog read; a `null` result is the read having failed. */
    suspend fun catalogLoad(read: suspend () -> CatalogOptions?): CatalogOptions? =
        traced(Trace.CATALOG_LOAD) { span ->
            val options = read()
            if (options == null) {
                span.setAttribute(Key.OUTCOME, Outcome.FAILED)
                logger.error(TAG, Event.CATALOG_LOAD_FAILED, tc = currentTraceContext().toLog())
            } else {
                span.setAttribute(Key.MAKE_COUNT, options.makes.size)
                if (options.makes.isEmpty()) {
                    // An empty catalog renders as a form with nothing to pick, which looks like
                    // a bug to the owner and is one to us — the seed data didn't land.
                    logger.warn(TAG, Event.CATALOG_EMPTY, tc = currentTraceContext().toLog())
                }
            }
            options
        }

    /** Times the per-make model read. */
    suspend fun modelsLoad(make: String, read: suspend () -> List<CarModel>): List<CarModel> =
        traced(Trace.MODELS_LOAD, Key.MAKE to make) { span ->
            val models = read()
            span.setAttribute(Key.COUNT, models.size)
            if (models.isEmpty()) {
                logger.warn(
                    TAG,
                    Event.MODELS_EMPTY,
                    tc = currentTraceContext().toLog(),
                    fields = mapOf(Key.MAKE to make),
                )
            }
            models
        }

    /**
     * Times the registry lookup and records which way it went. Worth measuring even while the
     * MVP's adapter always answers unavailable: this is the number that says whether a real
     * integration would earn its keep.
     */
    suspend fun plateLookup(
        read: suspend () -> Either<DomainError, RegisteredVehicle>,
    ): Either<DomainError, RegisteredVehicle> = traced(Trace.PLATE_LOOKUP) { span ->
        val result = read()
        val outcome = result.fold(
            ifLeft = { it::class.simpleName ?: Outcome.FAILED },
            ifRight = { Outcome.MATCHED },
        )
        span.setAttribute(Key.OUTCOME, outcome)
        analytics.track(Event.PLATE_LOOKUP, mapOf(Key.OUTCOME to outcome))
        logger.info(TAG, Event.PLATE_LOOKUP, tc = currentTraceContext().toLog(), fields = mapOf(Key.OUTCOME to outcome))
        result
    }

    /**
     * Times the car write and records the outcome. [edit] separates a first save from the owner
     * stepping back to fix something — a flow with many edits is a flow whose lookup or pickers
     * are getting it wrong.
     */
    suspend fun carSave(
        edit: Boolean,
        make: String?,
        fuel: String?,
        write: suspend () -> EitherNel<DomainError, Car>,
    ): EitherNel<DomainError, Car> = traced(Trace.SAVE_CAR, Key.EDIT to edit) { span ->
        val result = write()
        result.fold(
            ifLeft = { errors -> span.setAttribute(Key.OUTCOME, Outcome.FAILED); logSaveFailure(OnboardingStep.CAR, errors) },
            ifRight = { car ->
                span.setAttribute(Key.CAR_ID, car.id.value)
                val fields = mapOf(Key.EDIT to edit, Key.MAKE to make, Key.FUEL_TYPE to fuel)
                analytics.track(Event.CAR_SAVED, fields)
                logger.info(TAG, Event.CAR_SAVED, tc = currentTraceContext().toLog(), fields = fields)
            },
        )
        result
    }

    /** Times the profile write. The owner's name is never emitted — only whether it stored. */
    suspend fun profileSave(
        write: suspend () -> EitherNel<DomainError, OwnerProfile>,
    ): EitherNel<DomainError, OwnerProfile> = traced(Trace.SAVE_PROFILE) { span ->
        val result = write()
        result.fold(
            ifLeft = { errors ->
                span.setAttribute(Key.OUTCOME, Outcome.FAILED)
                logSaveFailure(OnboardingStep.PROFILE, errors)
            },
            ifRight = {
                analytics.track(Event.PROFILE_SAVED)
                logger.info(TAG, Event.PROFILE_SAVED, tc = currentTraceContext().toLog())
            },
        )
        result
    }

    /* ------------------------------ Plumbing ------------------------------ */

    /**
     * Open a span on the calling op's trace, run [block], and close the span whichever way it
     * goes. A cancelled or throwing block still ends its span — an unclosed span is a duration
     * that never arrives, which reads on a dashboard as an operation that never happened.
     */
    private suspend fun <T> traced(
        name: String,
        vararg attributes: Pair<String, Any?>,
        block: suspend (Span) -> T,
    ): T {
        val trace = currentTraceContext()
        val span = tracer.startSpan(name, trace.traceId ?: flowTraceId)
        attributes.forEach { (key, value) -> span.setAttribute(key, value) }
        return try {
            block(span)
        } finally {
            tracer.endSpan(span)
        }
    }

    /**
     * A failed write, named by the *types* of the errors that caused it — never by the values
     * that failed validation, which are the owner's answers.
     */
    private suspend fun logSaveFailure(step: OnboardingStep, errors: NonEmptyList<DomainError>) {
        val fields = mapOf(Key.STEP to step.name, Key.ERRORS to errors.errorTypes())
        analytics.track(Event.SAVE_FAILED, fields)
        logger.error(TAG, Event.SAVE_FAILED, tc = currentTraceContext().toLog(), fields = fields)
    }

    /** Bridge the coroutine-propagating performance trace onto the logging DTO. */
    private fun PerfTrace.toLog(): LogTrace =
        LogTrace(sessionId = sessionId, flowId = flowId, traceId = traceId)

    private companion object {
        const val TAG = "ONBOARDING"
        const val FLOW = "onboarding"

        /** Stands in for an absent enum value, so a property is never silently missing. */
        const val UNSET = "unset"
    }

    /*
     * The feature's observability taxonomy below — the single source of truth for every event,
     * span and field name. These names are what a dashboard queries, so treat them as shipped
     * contracts: reuse one rather than inventing a synonym, and don't rename one without
     * knowing you are breaking its history.
     */

    /** Analytics event names — the first-run funnel, welcome pitch → completed setup. */
    object Event {

        const val STARTED = "onboarding_started"
        const val STEP_ADVANCED = "onboarding_step_advanced"
        const val STEP_BACK = "onboarding_step_back"
        const val ABANDONED = "onboarding_abandoned"
        const val MANUAL_ENTRY_CHOSEN = "onboarding_manual_entry_chosen"
        const val PLATE_LOOKUP = "onboarding_plate_lookup"
        const val GOAL_SELECTED = "onboarding_goal_selected"
        const val CAR_SAVED = "onboarding_car_saved"
        const val PROFILE_SAVED = "onboarding_profile_saved"
        const val SAVE_FAILED = "onboarding_save_failed"
        const val FIRST_SCAN_CLICKED = "onboarding_first_scan_clicked"
        const val FIRST_SCAN_SKIPPED = "onboarding_first_scan_skipped"
        const val COMPLETED = "onboarding_completed"

        const val CATALOG_LOAD_FAILED = "onboarding_catalog_load_failed"
        const val CATALOG_EMPTY = "onboarding_catalog_empty"
        const val MODELS_EMPTY = "onboarding_models_empty"
    }

    /** Span / child-trace names — only genuine durations (catalog, registry, DB writes). */
    object Trace {
        /** One Continue tap, whichever step's write it turns out to run. */
        const val STEP_SUBMIT = "onboarding_step_submit"
        const val CATALOG_LOAD = "onboarding_catalog_load"
        const val MODELS_LOAD = "onboarding_models_load"
        const val PLATE_LOOKUP = "onboarding_plate_lookup"
        const val SAVE_CAR = "onboarding_save_car"
        const val SAVE_PROFILE = "onboarding_save_profile"
    }

    /** Structured field / property keys, shared across logs, events and spans. */
    object Key {
        const val STEP = "step"
        const val GOAL = "goal"
        const val SIGN_IN_OFFERED = "sign_in_offered"
        const val HAD_MATCH = "had_match"
        const val EDIT = "edit"
        const val CAR_ID = "car_id"
        const val MAKE = "make"
        const val FUEL_TYPE = "fuel_type"
        const val MAKE_COUNT = "make_count"
        const val COUNT = "count"
        const val OUTCOME = "outcome"
        const val ERRORS = "errors"
    }

    /** The values [Key.OUTCOME] takes, beyond a `DomainError`'s own type name. */
    object Outcome {
        const val MATCHED = "matched"
        const val FAILED = "failed"
    }
}

/**
 * A comma-joined list of the accumulated errors' *type* names — safe to emit (no user input, no
 * PII) and enough for a dashboard to see which rule failed.
 */
private fun NonEmptyList<DomainError>.errorTypes(): String =
    joinToString(",") { it::class.simpleName ?: "Unknown" }
