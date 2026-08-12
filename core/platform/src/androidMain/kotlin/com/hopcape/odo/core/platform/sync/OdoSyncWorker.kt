package com.hopcape.odo.core.platform.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hopcape.odo.core.sync.SyncEngine
import com.hopcape.odo.core.sync.SyncResult
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * The worker WorkManager runs. It owns no logic — it resolves the engine and reports what
 * happened in WorkManager's vocabulary.
 *
 * Resolved through [KoinComponent] rather than constructor injection because WorkManager
 * constructs its own workers; a custom `WorkerFactory` would be a second wiring path for
 * one class.
 *
 * The mapping to [Result] is the whole point of the class:
 *
 *  - a partial run returns `retry()`, which hands pacing to WorkManager's exponential
 *    backoff — the one retry policy, in one place (SYNC_DESIGN §10);
 *  - a skipped run returns `success()`, because "not signed in" is not a failure and
 *    retrying with backoff would just burn wakeups until someone signs in.
 */
internal class OdoSyncWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters), KoinComponent {

    private val engine: SyncEngine by inject()

    override suspend fun doWork(): Result = when (engine.sync()) {
        SyncResult.Success -> Result.success()
        is SyncResult.Partial -> Result.retry()
        is SyncResult.Skipped -> Result.success()
    }
}
