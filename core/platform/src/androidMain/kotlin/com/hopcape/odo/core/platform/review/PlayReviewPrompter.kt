package com.hopcape.odo.core.platform.review

import android.content.Context
import androidx.core.content.edit
import com.google.android.play.core.review.ReviewManagerFactory
import com.hopcape.logging.api.Logger
import com.hopcape.odo.core.domain.car.ActiveCarProvider
import com.hopcape.odo.core.domain.trip.repository.TripRepository
import com.hopcape.odo.core.platform.app.CurrentActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.concurrent.Volatile
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * [ReviewPrompter] on Play In-App Review. With no resumed Activity nothing is asked or
 * recorded, so the moment is tried again later rather than used up.
 */
internal class PlayReviewPrompter(
    private val context: Context,
    private val currentActivity: CurrentActivity,
    private val trips: TripRepository,
    private val activeCar: ActiveCarProvider,
    private val clock: Clock,
    private val telemetry: ReviewTelemetry,
    private val logger: Logger,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ReviewPrompter {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Volatile
    private var paywallSeen = false

    override fun paywallShown() {
        paywallSeen = true
    }

    override suspend fun maybeAsk(trigger: ReviewTrigger) {
        try {
            val history = readHistory()
            val now = clock.now()
            when (val decision = ReviewPromptPolicy.decide(history, trigger, now, paywallSeen)) {
                is ReviewDecision.Skip -> if (decision.reason != REASON_USED) telemetry.skipped(trigger, decision.reason)
                ReviewDecision.Ask -> {
                    val activity = currentActivity.get() ?: return
                    val manager = ReviewManagerFactory.create(context)
                    val info = manager.requestReviewFlow().await()
                    manager.launchReviewFlow(activity, info).await()
                    writeHistory(ReviewPromptPolicy.afterAsking(history, trigger, now))
                    telemetry.requested(trigger)
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (e: Exception) {
            logger.warn(TAG, "review_prompt_failed", fields = mapOf("trigger" to trigger.value, "error" to e::class.simpleName))
        }
    }

    override suspend fun maybeAskForTripDays() {
        if (ReviewTrigger.TRIP_DAY_7 in readHistory().usedTriggers) return
        val carId = withTimeoutOrNull(CAR_TIMEOUT_MILLIS) { activeCar.activeCarId.filterNotNull().first() } ?: return
        val days = trips.countedSince(carId, Instant.DISTANT_PAST)
            .map { it.startedAt.toLocalDateTime(timeZone).date }
            .distinct()
            .size
        if (days >= TRIP_DAYS) maybeAsk(ReviewTrigger.TRIP_DAY_7)
    }

    private fun readHistory() = ReviewHistory(
        lastAskedAt = prefs.getString(KEY_LAST, null)?.let { runCatching { Instant.parse(it) }.getOrNull() },
        timesAsked = prefs.getInt(KEY_TIMES, 0),
        usedTriggers = prefs.getStringSet(KEY_USED, emptySet()).orEmpty()
            .mapNotNull { name -> ReviewTrigger.entries.firstOrNull { it.name == name } }
            .toSet(),
    )

    private fun writeHistory(history: ReviewHistory) {
        prefs.edit {
            putString(KEY_LAST, history.lastAskedAt?.toString())
            putInt(KEY_TIMES, history.timesAsked)
            putStringSet(KEY_USED, history.usedTriggers.map { it.name }.toSet())
        }
    }

    private companion object {
        const val TAG = "REVIEW"
        const val PREFS_NAME = "odo_review_prompt"
        const val KEY_LAST = "last_asked_at"
        const val KEY_TIMES = "times_asked"
        const val KEY_USED = "used_triggers"
        const val REASON_USED = "used"
        const val TRIP_DAYS = 7
        const val CAR_TIMEOUT_MILLIS = 5_000L
    }
}
