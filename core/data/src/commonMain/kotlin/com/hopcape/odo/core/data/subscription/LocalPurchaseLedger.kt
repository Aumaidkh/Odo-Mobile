package com.hopcape.odo.core.data.subscription

import com.hopcape.odo.core.domain.subscription.PurchaseLedger

/**
 * [PurchaseLedger] on the device's own rows. A thin pass-through, so the domain port stays
 * free of the data layer's storage interface.
 */
internal class LocalPurchaseLedger(
    private val local: PurchaseLedgerLocalDataSource,
) : PurchaseLedger {

    override suspend fun claim(transactionId: String): Boolean = local.claim(transactionId)
}
