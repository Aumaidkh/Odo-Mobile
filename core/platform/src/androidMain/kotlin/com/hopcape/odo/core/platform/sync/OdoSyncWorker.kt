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
 *  - a skipped run returns `success()` only when nothing about waiting could change the
 *    answer. "Not signed in" is that case: retrying with backoff would burn wakeups until
 *    someone signs in.
 *
 * **A retryable skip returns `retry()`, and that distinction is a bug fix.** Every skip used
 * to map to `success()`, so a run refused because a token would not refresh, or because the
 * backend was mid-maintenance, was filed as done and the job was dropped. When the refused
 * run was the one triggered by signing in, the initial pull went with it and the owner was
 * left on an app that showed them nothing (issue #312).
 */
internal class OdoSyncWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters), KoinComponent {

    private val engine: SyncEngine by inject()

    override suspend fun doWork(): Result =
        if (engine.sync().needsRetry()) Result.retry() else Result.success()
}

/**
 * Whether this outcome should come back.
 *
 * A separate function because it is the only decision the worker makes, and inside
 * `doWork` there is no way to check it without a WorkManager runtime — which is how the
 * skip-is-always-success bug survived as long as it did.
 */
internal fun SyncResult.needsRetry(): Boolean = when (this) {
    SyncResult.Success -> false
    is SyncResult.Partial -> true
    is SyncResult.Skipped -> retryable
}
