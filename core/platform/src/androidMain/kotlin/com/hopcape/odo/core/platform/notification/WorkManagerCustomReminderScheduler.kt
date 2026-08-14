package com.hopcape.odo.core.platform.notification

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.hopcape.logging.api.Logger
import com.hopcape.odo.core.domain.car.ActiveCarProvider
import com.hopcape.odo.core.domain.reminder.model.CustomReminder
import com.hopcape.odo.core.domain.reminder.policy.CadencePlanner
import com.hopcape.odo.core.domain.reminder.policy.ReminderOccurrence
import com.hopcape.odo.core.domain.reminder.repository.ReminderRepository
import com.hopcape.odo.core.domain.settings.repository.AppSettingsRepository
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import java.util.concurrent.TimeUnit
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The owner's own reminders, on WorkManager — the same machinery as
 * [WorkManagerDocumentReminderScheduler], for the same reasons: the day matters and the
 * minute does not, no exact-alarm permission is needed, and the schedule survives a reboot.
 *
 * **Only the next occurrence of each reminder is enqueued.** A fortnightly check has no last
 * date, so queueing the whole series would mean guessing how far ahead to go and leaving jobs
 * behind whenever the cadence changed. The worker asks for a refresh once it has posted, which
 * is what puts the one after it on the schedule — so a repeating reminder keeps going on a
 * device the owner never opens, which is the whole point of setting one.
 *
 * Distance targets ("every 5,000 km") are skipped here on purpose: nothing about a date says
 * when an odometer will reach a number, so those stay in the feed until the trip tracker can
 * say the reading has been passed.
 */
internal class WorkManagerCustomReminderScheduler(
    context: Context,
    private val reminders: ReminderRepository,
    private val activeCar: ActiveCarProvider,
    private val settings: AppSettingsRepository,
    private val clock: Clock,
    private val logger: Logger,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : CustomReminderScheduler {

    private val workManager = WorkManager.getInstance(context.applicationContext)

    override suspend fun refresh() {
        runCatching {
            val carId = withTimeoutOrNull(CAR_RESOLVE_TIMEOUT_MILLIS) {
                activeCar.activeCarId.filterNotNull().first()
            }
            // Rebuilt from nothing every time: a paused, edited or deleted reminder cannot
            // leave a nudge waiting behind it.
            workManager.cancelAllWorkByTag(TAG)
            if (carId == null) {
                logger.info(TAG_LOG, "custom_reminders_no_car")
                return
            }

            val stored = settings.observe().first().notifications
            if (!stored.push || !stored.customReminders) {
                logger.info(
                    TAG_LOG,
                    "custom_reminders_muted",
                    fields = mapOf("push" to stored.push, "topic" to stored.customReminders),
                )
                return
            }

            val today = clock.now().toLocalDateTime(timeZone).date
            val now = clock.now()
            val onFile = reminders.observeCustom(carId).first()
            var scheduled = 0
            onFile.forEach { reminder ->
                // Paused reminders answer null here, which is why nothing filters them out
                // separately — the planner already owns what "next" means.
                val next = nextUnfired(reminder, today, now)
                if (next != null) {
                    enqueue(reminder, next, now)
                    scheduled++
                }
            }
            logger.info(
                TAG_LOG,
                "custom_reminders_scheduled",
                fields = mapOf("reminders" to onFile.size, "scheduled" to scheduled),
            )
        }.onFailure { cause ->
            logger.error(
                TAG_LOG,
                "custom_reminders_schedule_failed",
                fields = mapOf("reason" to (cause::class.simpleName ?: "Unknown")),
            )
        }
    }

    /**
     * The next occurrence that has not already happened.
     *
     * The planner answers from a *day*, so on the day a reminder fires it keeps answering
     * "today" — and this runs again right after the worker posts. Stepping past a day whose
     * time has gone is what turns that into the one after it instead of the same nudge over
     * and over. A missed occurrence is dropped rather than fired late, which is the rule the
     * planner already applies to the ones in between.
     */
    private fun nextUnfired(
        reminder: CustomReminder,
        today: LocalDate,
        now: Instant,
    ): ReminderOccurrence.OnDate? {
        val next = CadencePlanner.nextOccurrence(reminder, today) as? ReminderOccurrence.OnDate ?: return null
        if (next.date.atTime(reminder.at).toInstant(timeZone) > now) return next
        return CadencePlanner.nextOccurrence(
            cadence = reminder.cadence,
            startsOn = reminder.startsOn,
            from = next.date.plus(1, DateTimeUnit.DAY),
            anchorKm = reminder.anchorKm,
        ) as? ReminderOccurrence.OnDate
    }

    private fun enqueue(reminder: CustomReminder, occurrence: ReminderOccurrence.OnDate, now: Instant) {
        // The reminder's own time of day, not the app-wide hour: an owner who set "every
        // morning at 7" said when they wanted it, and that answer outranks the default.
        val fireAt = occurrence.date.atTime(reminder.at).toInstant(timeZone)
        val delay = (fireAt - now).inWholeMilliseconds.coerceAtLeast(0)

        val request = OneTimeWorkRequestBuilder<CustomReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .addTag(TAG)
            .setInputData(
                workDataOf(
                    CustomReminderWorker.KEY_REMINDER_ID to reminder.id.value,
                    CustomReminderWorker.KEY_TITLE to reminder.title.value,
                ),
            )
            .build()

        workManager.enqueueUniqueWork("$TAG:${reminder.id.value}", ExistingWorkPolicy.REPLACE, request)
    }

    private companion object {
        const val TAG = "OdoCustomReminder"
        const val TAG_LOG = "REMINDERS"

        /** How long to wait for the active car before deciding there is none. */
        const val CAR_RESOLVE_TIMEOUT_MILLIS = 5_000L
    }
}
