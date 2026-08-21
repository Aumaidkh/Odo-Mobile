package com.hopcape.odo.core.platform.sync

import com.hopcape.odo.core.sync.SyncEntity
import com.hopcape.odo.core.sync.SyncResult
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The one decision [OdoSyncWorker] makes: come back, or do not.
 *
 * Worth its own test because getting it wrong is silent. A run recorded as done is a job
 * WorkManager drops, and when the dropped job is the one triggered by signing in, the
 * owner's first pull goes with it and they are left on an app showing them nothing
 * (issue #312).
 */
class SyncWorkerOutcomeTest {

    @Test
    fun `a completed run is done`() {
        assertFalse(SyncResult.Success.needsRetry())
    }

    @Test
    fun `a partial run comes back`() {
        // Whatever failed is still PENDING, and WorkManager's backoff decides when.
        assertTrue(SyncResult.Partial(SyncEntity.CARS, cause = null).needsRetry())
    }

    @Test
    fun `a skip that only signing in can change is done`() {
        // Retrying with backoff would burn wakeups until somebody signs in.
        assertFalse(SyncResult.Skipped("not signed in", retryable = false).needsRetry())
    }

    @Test
    fun `a skip about this moment comes back`() {
        // A token that would not refresh, a maintenance window. Both end on their own, and
        // filing them as done is what loses the run.
        assertTrue(SyncResult.Skipped("session held but no usable token", retryable = true).needsRetry())
    }
}
