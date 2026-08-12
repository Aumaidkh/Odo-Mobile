package com.hopcape.odo.core.platform.logging

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hopcape.logging.api.LogUploadOutcome
import com.hopcape.logging.api.LogUploadRunner
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * The worker WorkManager runs for both the periodic and the "now" upload request — one class,
 * distinguished only by [KEY_IS_MANUAL] in its input data (docs/LOGGING_PLAN.md §7.2). It owns
 * no logic, same shape as `OdoSyncWorker`: resolve the runner, report what happened.
 *
 *  - [LogUploadOutcome.Partial] returns `retry()`, handing pacing to WorkManager's backoff;
 *  - [LogUploadOutcome.Delivered] and [LogUploadOutcome.Skipped] both return `success()` — a
 *    skip (no consent, no target configured) is not a failure, and retrying with backoff would
 *    just burn wakeups until either changes.
 */
internal class LogUploadWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters), KoinComponent {

    private val runner: LogUploadRunner by inject()

    override suspend fun doWork(): Result {
        val isManual = inputData.getBoolean(KEY_IS_MANUAL, false)
        return when (runner.uploadPending(isManual)) {
            is LogUploadOutcome.Delivered -> Result.success()
            LogUploadOutcome.Partial -> Result.retry()
            LogUploadOutcome.Skipped -> Result.success()
        }
    }

    companion object {
        const val KEY_IS_MANUAL = "isManual"
    }
}
