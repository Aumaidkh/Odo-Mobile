package com.hopcape.odo.feature.refuel.domain

import com.hopcape.odo.core.common.BuildInfo
import com.hopcape.odo.core.common.BuildVariant
import com.hopcape.logging.api.HLogger
import com.hopcape.odo.core.common.FeatureFlags
import com.hopcape.odo.core.common.runCatchingCancellableSuspend
import com.hopcape.odo.core.domain.car.ActiveCarProvider
import com.hopcape.odo.core.domain.cost.model.FuelFillDraft
import com.hopcape.odo.core.domain.refuel.PaymentApps
import com.hopcape.odo.core.domain.refuel.PaymentNotice
import com.hopcape.odo.core.domain.refuel.PaymentNoticeSource
import com.hopcape.odo.core.domain.refuel.PendingFill
import com.hopcape.odo.core.domain.refuel.PendingFillStore
import com.hopcape.odo.core.domain.refuel.RefuelDetectionStore
import com.hopcape.odo.core.platform.notification.DetectedFillNotice
import com.hopcape.odo.core.platform.notification.DetectedFillNotifier
import com.hopcape.odo.core.platform.notification.NotificationAccess
import com.hopcape.odo.core.platform.notification.PaymentNotices
import com.hopcape.odo.feature.refuel.domain.usecase.DetectFillFromNoticeUseCase
import com.hopcape.odo.feature.refuel.domain.usecase.LogRefuelUseCase
import com.hopcape.odo.feature.refuel.domain.usecase.ResolvePendingFillUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * The long-lived half of detection: it watches for payments and decides what the owner sees.
 *
 * Started once by the app bootstrap and never restarted. Two things run here for the app's
 * lifetime — keeping the listener's allow-list in step with the owner's settings, and turning
 * each notice into either a notification or, when the owner has asked for that, a written
 * fill.
 *
 * Nothing here runs while `FeatureFlags.SMART_REFUEL_DETECT_ENABLED` is false. [start] returns
 * before subscribing to anything, so a listener service that somehow got bound would publish
 * into a flow with no collector.
 *
 * Failures are swallowed per notice. This is a background collector with no screen behind it:
 * one payment that could not be classified must not take the collector down and stop every
 * later fill from being detected.
 *
 * Public only so the app bootstrap can start it; the constructor stays internal, so the one
 * thing outside this module can do is [start] the instance Koin built.
 */
class RefuelDetectionWorker internal constructor(
    private val notices: PaymentNoticeSource,
    private val detection: RefuelDetectionStore,
    private val detectFill: DetectFillFromNoticeUseCase,
    private val logRefuel: LogRefuelUseCase,
    private val resolvePending: ResolvePendingFillUseCase,
    private val notifier: DetectedFillNotifier,
    private val activeCar: ActiveCarProvider,
    private val pending: PendingFillStore,
    private val access: NotificationAccess,
    private val clock: Clock,
    private val copy: DetectionCopy,
) {
    fun start(scope: CoroutineScope) {
        if (!FeatureFlags.SMART_REFUEL_DETECT_ENABLED) return

        // Granted is not the same as bound: the OS unbinds the listener on every app update,
        // and some builds unbind it again whenever they reclaim the process. Nothing reports
        // that, so every launch asks — it is idempotent, and without it detection can look
        // switched on for weeks while delivering nothing.
        access.requestRebind()

        // If that was not enough, repair it — but only after giving the system time to bind on
        // its own. The repair rewrites the component's enabled state, which tears down a
        // working binding on the way to rebuilding one, so doing it unconditionally on every
        // launch breaks the very thing it is meant to fix.
        scope.launch {
            delay(BIND_GRACE)
            if (!access.isBound()) access.repairBinding()
        }
        // And keep asking after this process is gone. The launch-time rebind above only helps
        // an owner who opened the app, which is the one thing detection exists to save them.
        access.keepBound()

        // Answered questions are kept only long enough to stop the same notification being
        // offered again while it sits in the shade. A fortnight is far longer than any shade
        // holds a row, and short enough that the table never becomes history nobody reads.
        scope.launch {
            runCatchingCancellableSuspend {
                pending.pruneResolved(clock.now() - RESOLVED_RETENTION)
            }
        }

        // Put the payment apps on the roster before anything reads it. Nothing else writes
        // this table — the settings screen only toggles rows that already exist — so without
        // this the allow-list stays empty forever and the listener drops every notification
        // before looking at it, however the owner has set the switches.
        //
        // `registerApp` only inserts, so a package the owner turned off stays off across
        // every launch.
        scope.launch {
            PaymentApps.defaultsFor(includeShell = BuildInfo.variant == BuildVariant.DEBUG)
                .forEach { packageName ->
                    runCatchingCancellableSuspend {
                        detection.registerApp(packageName, enabledByDefault = true)
                    }
                }
        }

        // The service reads this list inside a callback that cannot suspend, so it is pushed
        // to the platform whenever the settings change rather than read on demand.
        scope.launch {
            // The owner's own switch is the only thing that decides this now (#251).
            // Detection used to be capped on the free plan and the cap was enforced right
            // here, by releasing the binding once the tenth detected fill landed — an owner
            // who granted notification access quietly stopped getting what they granted it
            // for, with nothing in the opt-in copy having said so. Growth Plan v3 forbids
            // gating refuel logging at all: it is a habit engine, it costs nothing to run,
            // and capping it stops the habit forming for exactly the owners who have not
            // paid yet.
            detection.observeApps()
                .collectLatest { apps ->
                    val enabled = apps.filter { it.enabled }.map { it.packageName }.toSet()
                    val settings = detection.settings()
                    val on = settings.detectEnabled
                    val watched = if (on) enabled else emptySet()
                    HLogger.tag(TAG).d(
                        "detect_roster",
                        mapOf(
                            "enabled" to settings.detectEnabled,
                            "count" to watched.size,
                        ),
                    )
                    PaymentNotices.setWatchedPackages(watched)
                    // Detection off means the connection goes back, not just that its results
                    // are ignored. An empty allow-list already stops anything being read;
                    // releasing the binding stops it being delivered at all, which is the
                    // difference between holding a permission and using one.
                    if (!on) access.releaseBinding() else access.requestRebind()
                }
        }

        scope.launch {
            notices.notices().collectLatest { notice ->
                runCatchingCancellableSuspend { handle(notice) }
            }
        }
    }

    private suspend fun handle(notice: PaymentNotice) {
        val carId = activeCar.activeCarId.value
        if (carId == null) {
            trace("no_active_car")
            return
        }
        val detected = detectFill(carId, notice)
        if (detected == null) {
            // Every reason lives in DetectFillFromNoticeUseCase and they are all deliberate:
            // detection off, an app the owner did not enable, a merchant they rejected, a
            // merchant that does not read as fuel, or a payment with no amount in it.
            trace("not_a_fill")
            return
        }

        // Silent logging is only ever taken when the owner asked for it *and* the amount is
        // ordinary for them. A payment far below their usual tank is the case this feature is
        // most likely to be wrong about, so it always gets asked rather than written.
        val confirmFirst = detection.settings().confirmBeforeLog || detected.unusuallySmall
        if (!confirmFirst) {
            logRefuel(carId, detected.draft)
            return
        }

        trace("notifying")
        val key = notice.pendingKey()
        val payload = DraftPayload.encode(detected.draft)
        val oneTap = detected.draft.isComplete

        // Written down *before* the notification is posted, and this is the whole point. A
        // notification is not storage: it can be swiped away, cleared with the shade, or never
        // seen at all, and Android keeps no record of what was dismissed. Once this row exists
        // the notification is only a shortcut to it, and losing the notification loses nothing.
        //
        // It also makes the notification safe to re-post. `remember` ignores a key it already
        // holds, so a payment read twice — which happens every time the listener reconnects
        // while it is still in the shade — stays one question.
        pending.remember(
            PendingFill(
                id = key,
                draftPayload = payload,
                merchant = notice.merchant,
                amount = notice.amount,
                detectedAt = notice.postedAt,
            ),
        )

        notifier.show(
            notice = DetectedFillNotice(
                id = key,
                title = copy.title,
                body = copy.body(detected.draft),
                // A draft still missing its quantity cannot be written by anybody, so the
                // primary action asks the owner to finish it rather than claiming it can log
                // it. That happens when they have no fuel price set: nothing can turn what
                // they paid into litres, and Odo will not guess a rate.
                confirmLabel = if (oneTap) copy.confirmLabel else copy.reviewLabel,
                editLabel = copy.editLabel,
                draftPayload = payload,
                oneTapConfirm = oneTap,
            ),
            onConfirm = { confirmed ->
                // Write first, claim second. The other order lost fills outright: the pending
                // row was marked answered and the notification dismissed, and if the write
                // then failed there was nothing left — no row, no question, no message. The
                // owner tapped Confirm and their fill silently ceased to exist.
                //
                // Claiming afterwards reopens the double-log window this ordering was meant to
                // close, so `resolvePending` still guards it: it returns false if the sheet
                // already answered this question, and the write below is skipped.
                val draft = DraftPayload.decode(confirmed)
                if (draft != null && logRefuel(carId, draft).isRight()) {
                    resolvePending(key)
                }
            },
        )
    }

    /**
     * Why a detection did or did not reach the owner, without any of its contents.
     *
     * The counterpart to the listener's own trace, and there for the same reason: this path
     * refuses far more often than it accepts, every refusal is a bare `return`, and from
     * outside they are indistinguishable from the feature being broken. No merchant, no
     * amount, no package — only which branch was taken.
     */
    private fun trace(outcome: String) {
        HLogger.tag(TAG).d("detect_$outcome")
    }

    private companion object {
        const val TAG = "RefuelDetect"

        /** How long an answered question is remembered, so it is not asked twice. */
        val RESOLVED_RETENTION = kotlin.time.Duration.parse("14d")

        /**
         * How long the system is given to bind the listener by itself before the repair runs.
         *
         * Long enough that an ordinary cold start binds well within it, short enough that an
         * owner who opened the app *because* detection stopped is not left waiting.
         */
        val BIND_GRACE = kotlin.time.Duration.parse("8s")
    }
}

/**
 * A key that is the same every time the same payment is read, and different for two payments
 * that only look alike.
 *
 * The merchant, the amount and the minute it was posted. Seconds are dropped because a
 * notification re-read after a reconnect can carry a slightly different post time on some
 * builds, and two genuinely separate fills at the same pump for the same amount inside one
 * minute is not a thing that happens.
 */
internal fun PaymentNotice.pendingKey(): String {
    val minute = postedAt.epochSeconds / 60
    return "${merchant.trim().lowercase()}|${amount?.paise ?: 0}|$minute"
}

/**
 * The notification's words, resolved before they reach the platform.
 *
 * A notification is drawn by the system from Android resources, and this feature's copy lives
 * in Compose resources the system cannot reach — so whoever builds this has already turned
 * the strings into text.
 */
internal data class DetectionCopy(
    val title: String,
    val confirmLabel: String,
    /** Stands in for [confirmLabel] when the draft still needs something from the owner. */
    val reviewLabel: String,
    val editLabel: String,
    val body: (FuelFillDraft) -> String,
)
