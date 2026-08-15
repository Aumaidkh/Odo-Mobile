package com.hopcape.odo.core.platform.notification

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.hopcape.logging.api.Logger
import com.hopcape.odo.core.domain.car.ActiveCarProvider
import com.hopcape.odo.core.domain.document.model.Document
import com.hopcape.odo.core.domain.document.policy.DocumentReminder
import com.hopcape.odo.core.domain.document.policy.DocumentReminderPolicy
import com.hopcape.odo.core.domain.document.repository.DocumentRepository
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
import kotlin.time.Instant

/**
 * Android's scheduler, on WorkManager.
 *
 * WorkManager rather than `AlarmManager`: a document reminder is a nudge whose day matters and
 * whose minute does not, so it needs neither an exact alarm nor the permission Android 12+
 * demands for one. WorkManager also survives a reboot, which an owner who set a reminder in
 * March and reboots in April very much depends on.
 *
 * Every refresh cancels the whole tag and re-enqueues from the database. That looks wasteful
 * and is the point: there is no bookkeeping to get wrong, so a renewed policy or a deleted
 * document cannot leave a notification waiting to fire about a paper that no longer exists.
 *
 * Nothing here throws. A scheduler that took a screen down over an unwritable job queue would
 * be worse than one that silently misses a nudge, and the failure is logged either way.
 *
 * What is scheduled comes from the owner's settings, read on every refresh: their lead days
 * per kind of paper, the hour they asked to be told at, and whether they want this topic at
 * all. A topic they turned off schedules nothing, which is what the switch has always looked
 * like it did.
 */
internal class WorkManagerDocumentReminderScheduler(
    context: Context,
    private val documents: DocumentRepository,
    private val activeCar: ActiveCarProvider,
    private val settings: AppSettingsRepository,
    private val clock: Clock,
    private val logger: Logger,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : DocumentReminderScheduler {

    private val workManager = WorkManager.getInstance(context.applicationContext)

    override suspend fun refresh() {
        runCatching {
            // Waited for, not read: the active car resolves from the database a moment after
            // the app starts, and a launch-time refresh that read `null` would cancel every
            // reminder the owner has. A garage that is genuinely empty times out instead.
            val carId = withTimeoutOrNull(CAR_RESOLVE_TIMEOUT_MILLIS) {
                activeCar.activeCarId.filterNotNull().first()
            }
            // Rebuilt from scratch every time, so nothing scheduled earlier can survive a
            // renewal or a delete. With no car there is nothing to chase, which is also the
            // state after the owner's data is wiped.
            workManager.cancelAllWorkByTag(TAG)
            if (carId == null) {
                logger.info(TAG_LOG, "document_reminders_no_car")
                return
            }

            // Read after the cancel, so turning the topic off takes effect on the next
            // write rather than only on the one after it. Both the topic and the channel
            // have to be on: an owner who turned notifications off entirely means it for
            // every topic, and one who turned this topic off means it for this one.
            val stored = settings.observe().first()
            val notifications = stored.notifications
            if (!notifications.push || !notifications.documentExpiry) {
                logger.info(
                    TAG_LOG,
                    "document_reminders_muted",
                    fields = mapOf("push" to notifications.push, "topic" to notifications.documentExpiry),
                )
                return
            }
            val schedule = stored.notificationSchedule

            val today = clock.now().toLocalDateTime(timeZone).date
            val now = clock.now()
            val onFile = documents.observe(carId).first()
            var scheduled = 0
            onFile.forEach { document ->
                // The owner's leads, not the product's: what fires has to be what the vault
                // promised them, and both read the same schedule.
                val leads = schedule.leadDaysFor(document.type)
                DocumentReminderPolicy.scheduleFor(document, today, leads).forEach { reminder ->
                    enqueue(document, reminder, now, schedule.notifyAtHour)
                    scheduled++
                }
            }
            logger.info(
                TAG_LOG,
                "document_reminders_scheduled",
                fields = mapOf("documents" to onFile.size, "reminders" to scheduled),
            )
        }.onFailure { cause ->
            logger.error(
                TAG_LOG,
                "document_reminders_schedule_failed",
                fields = mapOf("reason" to (cause::class.simpleName ?: "Unknown")),
            )
        }
    }

    private fun enqueue(document: Document, reminder: DocumentReminder, now: Instant, hour: Int) {
        val expiresOn = document.expiresOn ?: return
        val fireAt = reminder.on.atTime(hour, 0).toInstant(timeZone)
        // A nudge whose hour has already passed today still fires: the owner added the
        // document this afternoon, and telling them tomorrow about "expires tomorrow" is late.
        val delay = (fireAt - now).inWholeMilliseconds.coerceAtLeast(0)

        val request = OneTimeWorkRequestBuilder<DocumentReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .addTag(TAG)
            .setInputData(
                workDataOf(
                    DocumentReminderWorker.KEY_DOCUMENT_ID to document.id.value,
                    DocumentReminderWorker.KEY_TYPE to document.type.name,
                    DocumentReminderWorker.KEY_DAYS_BEFORE to reminder.daysBefore,
                    DocumentReminderWorker.KEY_EXPIRES_ON to expiresOn.toString(),
                ),
            )
            .build()

        workManager.enqueueUniqueWork(
            workNameFor(document, reminder),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    /** One job per document and lead, so re-running a refresh replaces rather than doubles. */
    private fun workNameFor(document: Document, reminder: DocumentReminder): String =
        "$TAG:${document.id.value}:${reminder.daysBefore}"

    private companion object {
        const val TAG = "OdoDocumentReminder"
        const val TAG_LOG = "REMINDERS"

        /** How long to wait for the active car before deciding there is none. */
        const val CAR_RESOLVE_TIMEOUT_MILLIS = 5_000L
    }
}
