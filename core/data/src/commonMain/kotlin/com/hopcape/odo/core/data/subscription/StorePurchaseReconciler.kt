package com.hopcape.odo.core.data.subscription

import arrow.core.getOrElse
import com.hopcape.odo.core.common.runCatchingCancellableSuspend
import com.hopcape.odo.core.domain.subscription.OneTimeGrant
import com.hopcape.odo.core.domain.subscription.OneTimeGrants
import com.hopcape.odo.core.domain.subscription.OneTimePurchaser
import com.hopcape.odo.core.domain.subscription.PurchaseLedger
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
 * **Claim first, then award.** The ledger's insert is what decides who owns a transaction,
 * so two reconciles racing each other cannot both credit it. The cost of that order is a
 * purchase claimed but not awarded if the process dies in between — one lost check against
 * the alternative of unlimited duplicated ones, which is the right way round.
 *
 * A failure to reach the store is not put in front of the owner: there is nothing for them to
 * do about it and nothing on screen waiting for it. It is logged, because a store that never
 * answers means nobody's purchases are being claimed and no screen would ever say so.
 */
internal class StorePurchaseReconciler(
    private val purchaser: OneTimePurchaser,
    private val ledger: PurchaseLedger,
    private val grants: OneTimeGrants,
    private val telemetry: DataTelemetry,
) : PurchaseReconciler {

    override suspend fun claimOutstanding() = telemetry.span(SOURCE, OP_CLAIM) {
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
            if (!ledger.claim(purchase.transactionId)) return@forEach
            // The id, so a support question about one purchase has something to match on.
            telemetry.span(SOURCE, OP_AWARD, id = purchase.transactionId) { grants.award(grant) }
        }
    }

    private companion object {
        const val SOURCE = "purchases"
        const val OP_CLAIM = "claim_outstanding"
        const val OP_AWARD = "award"
    }
}
