package com.hopcape.odo.core.data.scan

import com.hopcape.odo.core.data.subscription.PurchaseCreditsLocalDataSource
import com.hopcape.odo.core.domain.scan.entitlement.ScanCredits
import com.hopcape.odo.core.domain.subscription.CreditKind

/**
 * [ScanCredits] read off the owner's claims and spends.
 *
 * There is no `grant` here: a balance is granted by honouring a purchase, which is the same
 * write as the record that it was honoured.
 */
internal class LocalScanCredits(
    private val local: PurchaseCreditsLocalDataSource,
) : ScanCredits {

    override suspend fun available(): Int = local.available(CreditKind.BILL_CHECK)

    override suspend fun spend(): Boolean = local.spend(CreditKind.BILL_CHECK)
}
