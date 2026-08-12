package com.hopcape.odo.feature.profile.presentation

import arrow.core.Either
import arrow.core.EitherNel
import arrow.core.NonEmptyList
import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.logging.api.Logger
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.performance.api.PerformanceTracer
import com.hopcape.performance.api.Span
import com.hopcape.performance.api.currentTraceContext
import kotlin.coroutines.CoroutineContext
import com.hopcape.logging.api.TraceContext as LogTrace
import com.hopcape.performance.api.TraceContext as PerfTrace

/**
 * All observability for the profile, behind intent-named methods, so the ViewModels read as
 * their screens' logic instead of a wall of logger, analytics and tracer calls.
 *
 * One class owns the three ports, the taxonomy ([Event], [Trace], [Key]) and the trace
 * plumbing. [flowTrace] is minted per instance and the class is registered as a Koin
 * `factory`, so one instance covers one visit to the profile and every screen and sheet of
 * that visit shares one flow id.
 *
 * **No PII.** Booleans, counts, enum names and error *type* names only — never the owner's
 * name, email, city or the path of their photo. A city is the one that comes closest: it is
 * sent as "was one set", never as which one.
 *
 * Every method is fire-and-forget: nothing here returns a decision, and the wrapping methods
 * hand back their block's result untouched, so instrumentation cannot change what a screen
 * does.
 */
internal class ProfileTelemetry(
    private val logger: Logger,
    private val analytics: AnalyticsTracker,
    private val tracer: PerformanceTracer,
    private val ids: IdGenerator,
) {

    private val flowTraceId: String = "${FLOW}_${ids.newId()}"
    private val flowTrace = PerfTrace(flowId = FLOW, traceId = flowTraceId)

    /** The child-trace context to `launch(...)` an async op under. */
    fun op(name: String): CoroutineContext = flowTrace.withNewTrace("${name}_${ids.newId()}")

    /* ------------------------------ Home ------------------------------ */

    /**
     * The profile was opened, with the three facts that decide what it shows: the plan, the
     * session, and whether a benchmark city is set.
     *
     * [hasCity] is the one to watch. Onboarding never asks for a city and this screen is the
     * only place to set one, so owners without it get no fairness verdicts at all.
     */
    fun profileOpened(isPro: Boolean, isSignedIn: Boolean, hasCity: Boolean) {
        val fields = mapOf(Key.IS_PRO to isPro, Key.SIGNED_IN to isSignedIn, Key.HAS_CITY to hasCity)
        analytics.track(Event.PROFILE_OPENED, fields)
        logger.info(TAG, Event.PROFILE_OPENED, tc = flowTrace.toLog(), fields = fields)
    }

    /**
     * A read of the local DB failed, on the screen named by [source].
     *
     * Otherwise invisible twice over: the screen sits on whatever it was showing, and an
     * unhandled failure inside a `collect` takes the ViewModel's scope down.
     */
    fun readFailed(source: String, cause: Throwable) {
        val fields = mapOf(Key.SOURCE to source, Key.REASON to (cause::class.simpleName ?: UNKNOWN))
        analytics.track(Event.READ_FAILED, fields)
        logger.error(TAG, Event.READ_FAILED, tc = flowTrace.toLog(), fields = fields)
    }

    /** A settings screen or sheet was opened. */
    fun settingsOpened(source: String) {
        val fields = mapOf(Key.SOURCE to source)
        analytics.track(Event.SETTINGS_OPENED, fields)
        logger.debug(TAG, Event.SETTINGS_OPENED, tc = flowTrace.toLog(), fields = fields)
    }

    /* ------------------------------ Details ------------------------------ */

    /**
     * Times the profile write and records the outcome. [cityChanged] is what turns fairness
     * on for an owner who had no city, which is the outcome this screen exists for.
     */
    suspend fun <T> detailsSave(
        cityChanged: Boolean,
        write: suspend () -> EitherNel<DomainError, T>,
    ): EitherNel<DomainError, T> = traced(Trace.SAVE_DETAILS) { span ->
        val result = write()
        result.fold(
            ifLeft = { errors ->
                span.setAttribute(Key.OUTCOME, Outcome.FAILED)
                // The error *types*, never the values that failed validation.
                val fields = mapOf(Key.ERRORS to errors.typeNames())
                analytics.track(Event.DETAILS_SAVE_FAILED, fields)
                logger.error(TAG, Event.DETAILS_SAVE_FAILED, tc = currentTraceContext().toLog(), fields = fields)
            },
            ifRight = {
                val fields = mapOf(Key.CITY_CHANGED to cityChanged)
                analytics.track(Event.DETAILS_SAVED, fields)
                logger.info(TAG, Event.DETAILS_SAVED, tc = currentTraceContext().toLog(), fields = fields)
            },
        )
        result
    }

    /** Times the photo copy-and-save and records the outcome. */
    suspend fun <T> avatarSave(
        write: suspend () -> Either<DomainError, T>,
    ): Either<DomainError, T> = traced(Trace.SAVE_AVATAR) { span ->
        val result = write()
        result.fold(
            ifLeft = { error ->
                span.setAttribute(Key.OUTCOME, Outcome.FAILED)
                val fields = mapOf(Key.ERRORS to error.typeName())
                analytics.track(Event.AVATAR_FAILED, fields)
                logger.error(TAG, Event.AVATAR_FAILED, tc = currentTraceContext().toLog(), fields = fields)
            },
            ifRight = {
                analytics.track(Event.AVATAR_SAVED)
                logger.info(TAG, Event.AVATAR_SAVED, tc = currentTraceContext().toLog())
            },
        )
        result
    }

    /* ------------------------------ Settings ------------------------------ */

    /**
     * Times a settings write and records what it was. [setting] names the slice and [value]
     * describes the choice — an enum name or a count, never anything the owner typed.
     */
    suspend fun <T> settingSave(
        setting: String,
        value: String,
        write: suspend () -> Either<DomainError, T>,
    ): Either<DomainError, T> = traced(Trace.SAVE_SETTING, Key.SETTING to setting) { span ->
        val result = write()
        result.fold(
            ifLeft = { error ->
                span.setAttribute(Key.OUTCOME, Outcome.FAILED)
                val fields = mapOf(Key.SETTING to setting, Key.ERRORS to error.typeName())
                analytics.track(Event.SETTING_FAILED, fields)
                logger.error(TAG, Event.SETTING_FAILED, tc = currentTraceContext().toLog(), fields = fields)
            },
            ifRight = {
                val fields = mapOf(Key.SETTING to setting, Key.VALUE to value)
                analytics.track(Event.SETTING_CHANGED, fields)
                logger.info(TAG, Event.SETTING_CHANGED, tc = currentTraceContext().toLog(), fields = fields)
            },
        )
        result
    }

    /* ------------------------------ Account ------------------------------ */

    /**
     * Export was asked for, which today means the paywall. Counted because it is demand:
     * how often owners reach for their record decides what the Resale Passport is worth.
     */
    fun exportRequested(target: String) {
        val fields = mapOf(Key.TARGET to target)
        analytics.track(Event.EXPORT_REQUESTED, fields)
        logger.info(TAG, Event.EXPORT_REQUESTED, tc = flowTrace.toLog(), fields = fields)
    }

    /** The sign-in prompt on the profile was taken. */
    fun signInStarted() {
        analytics.track(Event.SIGN_IN_STARTED)
        logger.info(TAG, Event.SIGN_IN_STARTED, tc = flowTrace.toLog())
    }

    /** Sign-out finished: the session is gone and the local copy has been wiped. */
    fun signedOut() {
        analytics.track(Event.SIGNED_OUT)
        logger.info(TAG, Event.SIGNED_OUT, tc = flowTrace.toLog())
    }

    /**
     * Times the local wipe and records the outcome. The most destructive thing the app can
     * do, and the one an owner cannot undo, so it is logged whichever way it goes.
     */
    suspend fun <T> dataDeleted(
        write: suspend () -> Either<DomainError, T>,
    ): Either<DomainError, T> = traced(Trace.DELETE_DATA) { span ->
        val result = write()
        result.fold(
            ifLeft = { error ->
                span.setAttribute(Key.OUTCOME, Outcome.FAILED)
                val fields = mapOf(Key.ERRORS to error.typeName())
                analytics.track(Event.DELETE_FAILED, fields)
                logger.error(TAG, Event.DELETE_FAILED, tc = currentTraceContext().toLog(), fields = fields)
            },
            ifRight = {
                analytics.track(Event.DATA_DELETED)
                logger.warn(TAG, Event.DATA_DELETED, tc = currentTraceContext().toLog())
            },
        )
        result
    }

    /**
     * The owner committed to erasing their account.
     *
     * Counted at the confirmation rather than at the end, because the gap between this and
     * [Event.ACCOUNT_ERASED] is the number worth watching: someone who starts a deletion and
     * never finishes it hit something, and the OTP round trip is the likeliest place.
     *
     * [hasAccount] separates the two shapes of the flow — a server erase plus a wipe, or a
     * wipe alone.
     */
    fun accountDeletionStarted(hasAccount: Boolean) {
        val fields = mapOf(Key.HAS_ACCOUNT to hasAccount)
        analytics.track(Event.ACCOUNT_DELETION_STARTED, fields)
        logger.warn(TAG, Event.ACCOUNT_DELETION_STARTED, tc = flowTrace.toLog(), fields = fields)
    }

    /**
     * Times the erase and records the outcome.
     *
     * The most destructive thing the app can do, and unlike the local wipe it reaches a
     * server, so both outcomes are logged at warn or error — this is the trail someone reads
     * when an owner says their account is still there.
     */
    suspend fun <T> accountErase(
        write: suspend () -> Either<DomainError, T>,
    ): Either<DomainError, T> = traced(Trace.DELETE_ACCOUNT) { span ->
        val result = write()
        result.fold(
            ifLeft = { error ->
                span.setAttribute(Key.OUTCOME, Outcome.FAILED)
                val fields = mapOf(Key.ERRORS to error.typeName())
                analytics.track(Event.ACCOUNT_ERASE_FAILED, fields)
                logger.error(TAG, Event.ACCOUNT_ERASE_FAILED, tc = currentTraceContext().toLog(), fields = fields)
            },
            ifRight = {
                analytics.track(Event.ACCOUNT_ERASED)
                logger.warn(TAG, Event.ACCOUNT_ERASED, tc = currentTraceContext().toLog())
            },
        )
        result
    }

    /* ------------------------------ Plumbing ------------------------------ */

    /**
     * Open a span on the calling op's trace, run [block], and close the span whichever way
     * it goes. A cancelled or throwing block still ends its span; an unclosed span reads on
     * a dashboard as an operation that never happened.
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

    /** Bridge the coroutine-propagating performance trace onto the logging DTO. */
    private fun PerfTrace.toLog(): LogTrace =
        LogTrace(sessionId = sessionId, flowId = flowId, traceId = traceId)

    private fun DomainError.typeName(): String = this::class.simpleName ?: UNKNOWN

    private fun NonEmptyList<DomainError>.typeNames(): String = joinToString(",") { it.typeName() }

    private companion object {
        const val TAG = "PROFILE"
        const val FLOW = "profile"
        const val UNKNOWN = "Unknown"
    }

    /*
     * The feature's observability taxonomy. These names are what a dashboard queries, so
     * they are shipped contracts: reuse one rather than inventing a synonym, and do not
     * rename one without knowing you are breaking its history.
     */

    /** Analytics event names. Declared for the tracker in `profileAnalyticsEvents`. */
    object Event {
        const val PROFILE_OPENED = "profile_opened"
        const val READ_FAILED = "profile_read_failed"
        const val SETTINGS_OPENED = "profile_settings_opened"
        const val DETAILS_SAVED = "profile_details_saved"
        const val DETAILS_SAVE_FAILED = "profile_details_save_failed"
        const val AVATAR_SAVED = "profile_avatar_saved"
        const val AVATAR_FAILED = "profile_avatar_failed"
        const val SETTING_CHANGED = "profile_setting_changed"
        const val SETTING_FAILED = "profile_setting_failed"
        const val EXPORT_REQUESTED = "profile_export_requested"
        const val SIGN_IN_STARTED = "profile_sign_in_started"
        const val SIGNED_OUT = "profile_signed_out"
        const val DATA_DELETED = "profile_data_deleted"
        const val DELETE_FAILED = "profile_delete_failed"
        const val ACCOUNT_DELETION_STARTED = "profile_account_deletion_started"
        const val ACCOUNT_ERASED = "profile_account_erased"
        const val ACCOUNT_ERASE_FAILED = "profile_account_erase_failed"
    }

    /** Span names for the feature's async operations. */
    object Trace {
        const val SAVE_DETAILS = "profile_save_details"
        const val SAVE_AVATAR = "profile_save_avatar"
        const val SAVE_SETTING = "profile_save_setting"
        const val DELETE_DATA = "profile_delete_data"
        const val DELETE_ACCOUNT = "profile_delete_account"
        const val SIGN_OUT = "profile_sign_out"
    }

    /** Property names carried by the events and spans above. */
    object Key {
        const val SOURCE = "source"
        const val REASON = "reason"
        const val ERRORS = "errors"
        const val OUTCOME = "outcome"
        const val IS_PRO = "is_pro"
        const val SIGNED_IN = "signed_in"
        const val HAS_CITY = "has_city"
        const val CITY_CHANGED = "city_changed"
        const val SETTING = "setting"
        const val VALUE = "value"
        const val TARGET = "target"

        /** Whether a deletion has a server account behind it, or is a local wipe alone. */
        const val HAS_ACCOUNT = "has_account"
    }

    /** Values for [Key.OUTCOME]. */
    object Outcome {
        const val FAILED = "failed"
    }

    /** Values for [Key.SOURCE]. */
    object Screen {
        const val HOME = "home"
        const val EDIT = "edit"
        const val NOTIFICATIONS = "notifications"
        const val UNITS = "units"
        const val APPEARANCE = "appearance"
        const val SIGN_OUT = "sign_out"
        const val PRIVACY = "privacy"
        const val DELETE_ACCOUNT = "delete_account"
    }

    /** Values for [Key.SETTING] — which slice of the settings a write touched. */
    object Setting {
        const val THEME = "theme"
        const val TEXT_SIZE = "text_size"
        const val DISTANCE_UNIT = "distance_unit"
        const val FUEL_EFFICIENCY_UNIT = "fuel_efficiency_unit"
        const val NOTIFICATIONS = "notifications"

        /*
         * The three privacy switches. Worth counting separately rather than as one
         * "privacy" slice: how many owners keep price sharing on decides whether the city
         * benchmark has enough behind it to be worth showing at all, and an analytics
         * opt-out rate is the number that says whether the rest of the dashboard can be
         * trusted as a sample.
         */
        const val SHARE_PRICES = "share_prices"
        const val KEEP_TRIP_ROUTES = "keep_trip_routes"
        const val USAGE_ANALYTICS = "usage_analytics"
    }

    /** Values for [Key.TARGET] on an export request. */
    object ExportTarget {
        const val PDF = "pdf"
        const val FULL = "full"
    }
}
