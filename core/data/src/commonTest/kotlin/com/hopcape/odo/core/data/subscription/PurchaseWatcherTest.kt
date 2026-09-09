package com.hopcape.odo.core.data.subscription

import com.hopcape.odo.core.domain.subscription.PurchaseReconciler
import com.hopcape.odo.core.domain.subscription.PurchaseUpdates
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The collector that keeps claiming after launch.
 *
 * A purchase approved by a bank while the app is open has no screen waiting for it, so if
 * this stops collecting the owner waits until the next launch for what they paid for.
 */
class PurchaseWatcherTest {

    @Test
    fun `every store update claims`() = runTest {
        val updates = MutableSharedFlow<Unit>()
        val reconciler = CountingReconciler()
        PurchaseWatcher({ updates }, reconciler).start(backgroundScope)
        runCurrent()

        updates.emit(Unit)
        updates.emit(Unit)
        runCurrent()

        assertEquals(2, reconciler.claims)
    }

    /**
     * One bad pass must not end the collector. Everything after it would go unclaimed for the
     * rest of the session, and nothing on screen would say so.
     */
    @Test
    fun `a claim that throws does not stop the ones after it`() = runTest {
        val updates = MutableSharedFlow<Unit>()
        val reconciler = CountingReconciler(throwOn = 1)
        PurchaseWatcher({ updates }, reconciler).start(backgroundScope)
        runCurrent()

        updates.emit(Unit)
        runCurrent()
        updates.emit(Unit)
        runCurrent()

        assertEquals(2, reconciler.claims)
    }

    /** A build with no store pushes nothing, and nothing is what should happen. */
    @Test
    fun `no updates means no claims`() = runTest {
        val reconciler = CountingReconciler()

        PurchaseWatcher(PurchaseUpdates { MutableSharedFlow() }, reconciler).start(backgroundScope)
        runCurrent()

        assertEquals(0, reconciler.claims)
    }

    private class CountingReconciler(private val throwOn: Int? = null) : PurchaseReconciler {
        var claims = 0
            private set

        override suspend fun claimOutstanding() {
            claims++
            if (claims == throwOn) error("store exploded")
        }
    }
}
