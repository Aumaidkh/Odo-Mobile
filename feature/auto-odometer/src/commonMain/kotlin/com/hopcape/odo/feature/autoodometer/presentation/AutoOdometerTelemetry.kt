package com.hopcape.odo.feature.autoodometer.presentation

import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.crashreporting.api.CrashRecorder
import com.hopcape.logging.api.Logger
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.performance.api.PerformanceTracer
import com.hopcape.logging.api.TraceContext as LogTrace
import com.hopcape.performance.api.TraceContext as PerfTrace

/**
 * All observability for the auto-odometer feature, behind intent-named methods, so the
 * ViewModels built in F3-F8 read as their screens' logic instead of a wall of logger,
 * analytics and tracer calls. One class owns the four ports and the taxonomy
 * ([Event]/[Key]) so an event name is never duplicated or scattered.
 *
 * The funnel this feature lives and dies by is: card shown -> education viewed -> device
 * selected -> permissions answered -> setup completed -> first trip logged. That funnel is
 * this class's north-star instrumentation (docs/AUTO_ODOMETER_PLAN.md §4.3).
 *
 * [flowTrace] is minted per instance. Registered as a Koin `factory`, so one instance
 * covers one visit to the feature and every screen of that visit shares one flow id.
 *
 * **No PII, ever.** Device *category*, step names, booleans and error *type* names only —
 * never a device name, a Bluetooth MAC, or a coordinate. [deviceSelected] takes a category,
 * not a name, so the rule cannot be broken at a call site.
 *
 * Every method is fire-and-forget: nothing here returns a decision, so instrumentation can
 * never change what a screen does.
 */
internal class AutoOdometerTelemetry(
    private val logger: Logger,
    private val analytics: AnalyticsTracker,
    private val tracer: PerformanceTracer,
    private val crash: CrashRecorder,
    private val ids: IdGenerator,
) {

    private val flowTraceId: String = "${FLOW}_${ids.newId()}"
    private val flowTrace = PerfTrace(flowId = FLOW, traceId = flowTraceId)

    /** The child-trace context to `launch(...)` an async op under (F3's use-case calls). */
    fun op(name: String): kotlin.coroutines.CoroutineContext = flowTrace.withNewTrace("${name}_${ids.newId()}")

    /* ------------------------------ Funnel ------------------------------ */

    /** The garage card was shown (>=2 manual readings, auto-odometer not yet set up). */
    fun cardShown() = analytics.track(Event.CARD_SHOWN)

    /** The garage card's CTA ("See how it works") was tapped. */
    fun cardCta() = analytics.track(Event.CARD_CTA)

    /** The education screen was viewed, on the [mode] it opened with (STEREO/NO_STEREO). */
    fun educationViewed(mode: String) = analytics.track(Event.EDUCATION_VIEWED, mapOf(Key.MODE to mode))

    /** "My car has no Bluetooth" was tapped, switching the flow to the NO_STEREO branch. */
    fun noBtPathTaken() = analytics.track(Event.NO_BT_PATH_TAKEN)

    /**
     * A trigger device was picked. [category] only (CAR_AUDIO/HEADSET/WEARABLE/OTHER),
     * never a device name or MAC — see the class KDoc's PII rule.
     */
    fun deviceSelected(category: String, wasConnected: Boolean) = analytics.track(
        Event.DEVICE_SELECTED,
        mapOf(Key.CATEGORY to category, Key.WAS_CONNECTED to wasConnected),
    )

    /** One step of the permission checklist (§5) was answered. */
    fun permissionAnswered(step: String, status: String) = analytics.track(
        Event.PERMISSION_ANSWERED,
        mapOf(Key.STEP to step, Key.STATUS to status),
    )

    /** The checklist went all-green and tracking was enabled for [mode]. */
    fun setupCompleted(mode: String) = analytics.track(Event.SETUP_COMPLETED, mapOf(Key.MODE to mode))

    /** The very first auto-logged trip was recorded — the funnel's terminal event. */
    fun firstTripLogged() = analytics.track(Event.FIRST_TRIP_LOGGED)

    /** The trip-logged screen was opened (every time, not just the first). */
    fun tripLoggedViewed() = analytics.track(Event.TRIP_LOGGED_VIEWED)

    /** "That wasn't me driving" — the owner rejected an auto-logged trip. */
    fun tripRejected() = analytics.track(Event.TRIP_REJECTED)

    /** The one-time background-location upgrade card (trip-logged screen, §5 step 5) was answered. */
    fun backgroundUpgradeAnswered(granted: Boolean) =
        analytics.track(Event.BACKGROUND_UPGRADE_ANSWERED, mapOf(Key.GRANTED to granted))

    /* ------------------------------ Settings ------------------------------ */

    /** Settings' tracking toggle was flipped. */
    fun trackingToggled(on: Boolean) = analytics.track(Event.TRACKING_TOGGLED, mapOf(Key.ON to on))

    /** "Pause for a week" was tapped from settings. */
    fun pausedWeek() = analytics.track(Event.PAUSED_WEEK)

    /** "Delete all trip data" was confirmed. */
    fun tripDataDeleted() = analytics.track(Event.TRIP_DATA_DELETED)

    /**
     * A screen tried to act on the active car (enroll a trigger device, save a bond) before
     * one was resolved. Mirrors `GarageTelemetry.noActiveCar` — the same "should never
     * happen once onboarding is done, but say so instead of silently no-op-ing" signal.
     */
    fun noActiveCar() {
        analytics.track(Event.NO_ACTIVE_CAR)
        logger.warn(TAG, Event.NO_ACTIVE_CAR, tc = flowTrace.toLog())
    }

    /* ------------------------------ Readiness ------------------------------ */

    /**
     * A tracking precondition (a permission, the trigger bond) was lost outside the app —
     * revoked in system settings, or an OEM autostart kill. The dashboard signal for silent
     * breakage in an offline, background feature (docs/AUTO_ODOMETER_PLAN.md §5).
     */
    fun preconditionLost(which: String) {
        val fields = mapOf(Key.WHICH to which)
        analytics.track(Event.PRECONDITION_LOST, fields)
        logger.info(TAG, Event.PRECONDITION_LOST, tc = flowTrace.toLog(), fields = fields)
    }

    /** A caught exception during enrollment or a Bluetooth-catalog read that means something is broken. */
    fun nonFatal(throwable: Throwable, stage: String) {
        logger.error(TAG, EVENT_ERROR, tc = flowTrace.toLog(), fields = mapOf(Key.STAGE to stage))
        crash.recordNonFatal(throwable, mapOf(Key.STAGE to stage))
    }

    /** Bridge the coroutine-propagating performance trace onto the logging DTO. */
    private fun PerfTrace.toLog(): LogTrace = LogTrace(sessionId = sessionId, flowId = flowId, traceId = traceId)

    private companion object {
        const val TAG = "AUTO_ODOMETER"
        const val FLOW = "auto_odometer"
        const val EVENT_ERROR = "auto_odometer_error"
    }

    /*
     * The feature's observability taxonomy. These names are what a dashboard queries once
     * shipped — reuse one rather than inventing a synonym.
     */

    /** Analytics event names. Declared for the tracker in `autoOdometerAnalyticsEvents`. */
    object Event {
        const val CARD_SHOWN = "ao_card_shown"
        const val CARD_CTA = "ao_card_cta"
        const val EDUCATION_VIEWED = "ao_education_viewed"
        const val NO_BT_PATH_TAKEN = "ao_no_bt_path_taken"
        const val DEVICE_SELECTED = "ao_device_selected"
        const val PERMISSION_ANSWERED = "ao_permission_answered"
        const val SETUP_COMPLETED = "ao_setup_completed"
        const val FIRST_TRIP_LOGGED = "ao_first_trip_logged"
        const val TRIP_LOGGED_VIEWED = "ao_trip_logged_viewed"
        const val TRIP_REJECTED = "ao_trip_rejected"
        const val BACKGROUND_UPGRADE_ANSWERED = "ao_background_upgrade_answered"
        const val TRACKING_TOGGLED = "ao_tracking_toggled"
        const val PAUSED_WEEK = "ao_paused_week"
        const val TRIP_DATA_DELETED = "ao_trip_data_deleted"
        const val PRECONDITION_LOST = "ao_precondition_lost"
        const val NO_ACTIVE_CAR = "ao_no_active_car"
    }

    /** Field keys — kept here so a dashboard query never breaks on a renamed literal. */
    object Key {
        const val MODE = "mode"
        const val CATEGORY = "category"
        const val WAS_CONNECTED = "was_connected"
        const val STEP = "step"
        const val STATUS = "status"
        const val GRANTED = "granted"
        const val ON = "on"
        const val WHICH = "which"
        const val STAGE = "stage"
    }
}
