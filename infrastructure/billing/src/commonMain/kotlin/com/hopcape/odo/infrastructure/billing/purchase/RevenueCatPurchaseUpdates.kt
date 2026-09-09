package com.hopcape.odo.infrastructure.billing.purchase

import com.hopcape.odo.core.domain.subscription.PurchaseUpdates
import com.hopcape.odo.infrastructure.billing.CustomerInfoStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * [PurchaseUpdates] on the customer info the SDK already pushes.
 *
 * Reads the shared [CustomerInfoStream] rather than taking the SDK's delegate, because the
 * SDK allows only one and that stream owns it. Every push lands here — a purchase completing,
 * a pending one being approved, a restore on a new phone.
 *
 * A failed read emits too. It is still a change of answer, and asking the store again costs a
 * cached call.
 */
internal class RevenueCatPurchaseUpdates(
    private val stream: CustomerInfoStream,
) : PurchaseUpdates {

    override fun changes(): Flow<Unit> = stream.resolved.map { }
}
