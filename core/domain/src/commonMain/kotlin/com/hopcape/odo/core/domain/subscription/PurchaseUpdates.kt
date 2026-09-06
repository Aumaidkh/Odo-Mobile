package com.hopcape.odo.core.domain.subscription

import kotlinx.coroutines.flow.Flow

/**
 * Emits whenever the store's view of this owner may have changed.
 *
 * The case it exists for is a purchase that completes minutes after the tap. UPI mandates and
 * cash payments leave a purchase pending until a bank approves it, which is common in the
 * market Odo sells to, and the screen that started it is long gone by then.
 *
 * It carries no payload on purpose: what changed is [OneTimePurchaser.completedPurchases]'s
 * answer, and a second version of it here would be a second thing to keep true.
 */
fun interface PurchaseUpdates {

    /** One emission per change. Live for as long as it is collected. */
    fun changes(): Flow<Unit>
}
