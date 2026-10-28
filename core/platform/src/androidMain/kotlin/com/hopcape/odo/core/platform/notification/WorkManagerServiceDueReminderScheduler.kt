package com.hopcape.odo.core.platform.notification

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.hopcape.logging.api.Logger
import com.hopcape.odo.core.domain.car.ActiveCarProvider
import com.hopcape.odo.core.domain.servicelog.policy.ServiceReminder
import com.hopcape.odo.core.domain.servicelog.policy.ServiceReminderPlan
import com.hopcape.odo.core.domain.servicelog.repository.ServiceLogRepository
import com.hopcape.odo.core.domain.settings.repository.AppSettingsRepository
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import java.util.concurrent.TimeUnit
import kotlin.time.Clock

/** [ServiceDueReminderScheduler] on WorkManager: one job per planned day, rebuilt from scratch. */
internal class WorkManagerServiceDueReminderScheduler(
    context: Context,
    private val serviceLogs: ServiceLogRepository,
    private val activeCar: ActiveCarProvider,
    private val settings: AppSettingsRepository,
    private val clock: Clock,
    private val logger: Logger,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ServiceDueReminderScheduler {

    private val workManager = WorkManager.getInstance(context.applicationContext)

    override suspend fun refresh() {
        runCatching {
            val carId = withTimeoutOrNull(CAR_RESOLVE_TIMEOUT_MILLIS) {
                activeCar.activeCarId.filterNotNull().first()
            }
            workManager.cancelAllWorkByTag(TAG)
            if (carId == null) return

            val stored = settings.observe().first()
            val notifications = stored.notifications
            if (!notifications.push || !notifications.serviceDue) {
                logger.info(TAG_LOG, "service_reminders_muted")
                return
            }
            val now = clock.now()
            val today = now.toLocalDateTime(timeZone).date
            val plan = ServiceReminderPlan.from(serviceLogs.observe(carId).first(), today)
            plan.forEach { enqueue(it, stored.notificationSchedule.notifyAtHour, now) }
            logger.info(TAG_LOG, "service_reminders_scheduled", fields = mapOf("reminders" to plan.size))
        }.onFailure { cause ->
            logger.error(TAG_LOG, "service_reminders_schedule_failed", fields = mapOf("reason" to cause::class.simpleName))
        }
    }

    private fun enqueue(reminder: ServiceReminder, hour: Int, now: kotlin.time.Instant) {
        val delay = (reminder.on.atTime(hour, 0).toInstant(timeZone) - now).inWholeMilliseconds.coerceAtLeast(0)
        val request = OneTimeWorkRequestBuilder<ServiceDueReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .addTag(TAG)
            .setInputData(
                workDataOf(
                    ServiceDueReminderWorker.KEY_KIND to reminder.kind.name,
                    ServiceDueReminderWorker.KEY_DUE_ON to reminder.dueOn.toString(),
                ),
            )
            .build()
        workManager.enqueueUniqueWork("$TAG:${reminder.kind.name}", ExistingWorkPolicy.REPLACE, request)
    }

    private companion object {
        const val TAG = "OdoServiceReminder"
        const val TAG_LOG = "REMINDERS"
        const val CAR_RESOLVE_TIMEOUT_MILLIS = 5_000L
    }
}
