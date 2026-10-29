package com.hopcape.odo.core.platform.notification

import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.DayOfWeek
import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.hopcape.logging.api.Logger
import com.hopcape.odo.core.domain.settings.repository.AppSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import java.util.concurrent.TimeUnit
import kotlin.time.Clock

/**
 * [DriveSummaryNotifier] on WorkManager: the daily summary at 21:00 and the weekly recap on
 * Sunday at 19:00, each one job re-queued by its own run.
 */
internal class WorkManagerDriveSummaryNotifier(
    context: Context,
    private val settings: AppSettingsRepository,
    private val clock: Clock,
    private val logger: Logger,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : DriveSummaryNotifier {

    private val workManager = WorkManager.getInstance(context.applicationContext)

    override suspend fun rearm() {
        schedule(ExistingWorkPolicy.REPLACE)
        scheduleWeekly(ExistingWorkPolicy.REPLACE)
    }

    /** From inside a run: append, because replacing would cancel the run doing the asking. */
    suspend fun rearmFromRun() = schedule(ExistingWorkPolicy.APPEND_OR_REPLACE)

    suspend fun rearmWeeklyFromRun() = scheduleWeekly(ExistingWorkPolicy.APPEND_OR_REPLACE)

    private suspend fun scheduleWeekly(policy: ExistingWorkPolicy) {
        runCatching {
            val notifications = settings.observe().first().notifications
            if (!notifications.push || !notifications.monthlySummary) {
                workManager.cancelUniqueWork(WEEKLY_WORK_NAME)
                return
            }
            val now = clock.now()
            val today = now.toLocalDateTime(timeZone).date
            val daysToSunday = (DayOfWeek.SUNDAY.isoDayNumber - today.dayOfWeek.isoDayNumber + 7) % 7
            var next = today.plus(daysToSunday, DateTimeUnit.DAY).atTime(WEEKLY_HOUR, 0).toInstant(timeZone)
            if (next <= now) next = today.plus(daysToSunday + 7, DateTimeUnit.DAY).atTime(WEEKLY_HOUR, 0).toInstant(timeZone)
            val request = OneTimeWorkRequestBuilder<WeeklySummaryWorker>()
                .setInitialDelay((next - now).inWholeMilliseconds, TimeUnit.MILLISECONDS)
                .build()
            workManager.enqueueUniqueWork(WEEKLY_WORK_NAME, policy, request)
        }.onFailure { cause ->
            logger.error(TAG, "weekly_summary_schedule_failed", fields = mapOf("reason" to cause::class.simpleName))
        }
    }

    private suspend fun schedule(policy: ExistingWorkPolicy) {
        runCatching {
            val notifications = settings.observe().first().notifications
            if (!notifications.push || !notifications.driveSummary) {
                workManager.cancelUniqueWork(WORK_NAME)
                return
            }
            val now = clock.now()
            val today = now.toLocalDateTime(timeZone).date
            val tonight = today.atTime(HOUR, 0).toInstant(timeZone)
            val next = if (now < tonight) tonight else today.plus(1, DateTimeUnit.DAY).atTime(HOUR, 0).toInstant(timeZone)
            val request = OneTimeWorkRequestBuilder<DriveSummaryWorker>()
                .setInitialDelay((next - now).inWholeMilliseconds, TimeUnit.MILLISECONDS)
                .build()
            workManager.enqueueUniqueWork(WORK_NAME, policy, request)
        }.onFailure { cause ->
            logger.error(TAG, "drive_summary_schedule_failed", fields = mapOf("reason" to cause::class.simpleName))
        }
    }

    companion object {
        const val HOUR = 21
        const val WEEKLY_HOUR = 19
        private const val WORK_NAME = "odo_drive_summary"
        private const val WEEKLY_WORK_NAME = "odo_weekly_summary"
        private const val TAG = "DRIVE_SUMMARY"
    }
}
