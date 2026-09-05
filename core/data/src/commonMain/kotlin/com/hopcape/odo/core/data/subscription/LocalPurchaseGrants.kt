package com.hopcape.odo.core.data.subscription

import com.hopcape.odo.core.domain.subscription.OneTimeGrant
import com.hopcape.odo.core.domain.subscription.PurchaseGrants

/**
 * [PurchaseGrants] on the owner's own rows. A thin pass-through, so the domain port stays
 * free of the data layer's storage interface.
 */
internal class LocalPurchaseGrants(
    private val local: PurchaseCreditsLocalDataSource,
) : PurchaseGrants {

    override suspend fun claim(transactionId: String, grant: OneTimeGrant): Boolean =
        local.claim(
            transactionId = transactionId,
            scanChecks = grant.scanChecks,
            recordExports = grant.recordExports,
        )
}
