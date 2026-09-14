package com.hopcape.odo.core.data.subscription

import arrow.core.getOrElse
import com.hopcape.odo.core.common.runCatchingCancellableSuspend
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.hopcape.odo.core.domain.subscription.OneTimeGrant
import com.hopcape.odo.core.domain.subscription.OneTimePurchaser
import com.hopcape.odo.core.domain.subscription.PurchaseGrants
import com.hopcape.odo.core.domain.subscription.PurchaseReconciler
import com.hopcape.odo.core.data.observability.DataTelemetry

/**
 * Credits any one-time purchase the store has recorded and this device has not.
 *
 * The gap it closes is money already taken: a store sheet that finishes after the app is
 * killed, a card that needed a bank approval, a reinstall. Every one of those leaves an
 * owner who paid and got nothing, because until now the only thing that credited a balance
 * was the screen that started the purchase.
 *
 * **Claiming and crediting are one write.** The row that records a transaction as honoured
 * is what the owner was given, so two reconciles racing each other cannot both credit it and
 * a crash cannot leave a purchase marked honoured that credited nothing.
 *
 * The record is owner-scoped and synced, so a purchase honoured once stays honoured once for
 * the owner rather than once per install — a reinstall used to clear it while the store went
 * on reporting the purchase forever.
 *
 * **Nothing here throws at its caller.** Two of the three callers are `viewModelScope`
 * launches with nothing catching them, and the moment they call this is the moment after the
 * owner paid — a database failure there would be a crash on top of a charge.
 *
 * A failure to reach the store is not put in front of the owner: there is nothing for them to
 * do about it and nothing on screen waiting for it. It is logged, because a store that never
 * answers means nobody's purchases are being claimed and no screen would ever say so.
 */
internal class StorePurchaseReconciler(
    private val purchaser: OneTimePurchaser,
    private val grants: PurchaseGrants,
    private val telemetry: DataTelemetry,
) : PurchaseReconciler {

    /**
     * One pass at a time.
     *
     * Not for the record's sake — its insert already decides the winner. It is for the
     * callers that claim and then immediately spend what the claim credited: without this,
     * a pass running on the watcher could still be inside its write when the screen's own
     * call came back empty, and the screen would spend a balance that arrives a moment later.
     */
    private val pass = Mutex()

    override suspend fun claimOutstanding() = pass.withLock { claim() }

    private suspend fun claim() = telemetry.span(SOURCE, OP_CLAIM) {
        val purchases = runCatchingCancellableSuspend { purchaser.completedPurchases() }
            .getOrElse { throwable ->
                telemetry.crashed(SOURCE, OP_CLAIM, throwable)
                return@span
            }
            .getOrElse { error ->
                telemetry.failed(SOURCE, OP_CLAIM, error)
                return@span
            }

        purchases.forEach { purchase ->
            val grant = OneTimeGrant.of(purchase.productId)
            if (grant == null) {
                // A product the store sells and this build does not know. Reported because it
                // is a real coverage gap — someone paid for something nothing here can honour
                // — and it is otherwise indistinguishable from having nothing to claim.
                telemetry.missing(SOURCE, OP_CLAIM, purchase.productId)
                return@forEach
            }
            // Per purchase, so a database that fails on one does not stop the rest, and so a
            // throw never reaches the caller. Two of the three callers are `viewModelScope`
            // launches with nothing catching them, where it would be a crash moments after
            // the owner's money was taken.
            runCatchingCancellableSuspend {
                // The id, so a support question about one purchase has something to match on.
                telemetry.span(SOURCE, OP_AWARD, id = purchase.transactionId) {
                    grants.claim(purchase.transactionId, grant)
                }
            }.onFailure { telemetry.crashed(SOURCE, OP_AWARD, it, purchase.transactionId) }
        }
    }

    private companion object {
        const val SOURCE = "purchases"
        const val OP_CLAIM = "claim_outstanding"
        const val OP_AWARD = "award"
    }
}
