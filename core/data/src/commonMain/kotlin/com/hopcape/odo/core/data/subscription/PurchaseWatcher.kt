package com.hopcape.odo.core.data.subscription

import com.hopcape.odo.core.common.runCatchingCancellableSuspend
import com.hopcape.odo.core.domain.subscription.PurchaseReconciler
import com.hopcape.odo.core.domain.subscription.PurchaseUpdates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Claims outstanding purchases for the app's lifetime, not just at launch.
 *
 * A launch-time claim misses the case this is built for: a UPI mandate or a cash payment sits
 * pending until a bank approves it, and the approval lands while the app is open and the
 * screen that started the purchase is gone. The store pushes an update when it does, and that
 * is what [PurchaseUpdates] carries.
 *
 * It also covers the launch itself — the store's first answer is a change like any other — so
 * this replaces the one-shot claim rather than running beside it. Claiming is idempotent, so
 * an extra pass costs a cached read and nothing else.
 *
 * Failures are swallowed by the reconciler. There is no screen behind this and nothing for
 * the owner to do, and a collector that dies on one bad pass would stop claiming for the rest
 * of the session.
 *
 * Public only so the app bootstrap can [start] it; the constructor stays internal.
 */
class PurchaseWatcher internal constructor(
    private val updates: PurchaseUpdates,
    private val reconciler: PurchaseReconciler,
) {

    /**
     * Collect for as long as [scope] lives.
     *
     * One claim at a time, and deliberately not `collectLatest`: a claim records the
     * transaction before it awards it, so cancelling one halfway loses that purchase for
     * good. A burst of updates asks the store the same question a few times over, which is a
     * cached read, and that is the cheaper side of the trade.
     */
    fun start(scope: CoroutineScope) {
        scope.launch {
            updates.changes().collect {
                runCatchingCancellableSuspend { reconciler.claimOutstanding() }
            }
        }
    }
}
