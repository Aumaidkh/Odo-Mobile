package com.hopcape.odo.core.data.record

import com.hopcape.odo.core.data.subscription.PurchaseCreditsLocalDataSource
import com.hopcape.odo.core.domain.record.entitlement.ExportCredits
import com.hopcape.odo.core.domain.subscription.CreditKind

/**
 * [ExportCredits] read off the owner's claims and spends.
 *
 * There is no `grant` here: a balance is granted by honouring a purchase, which is the same
 * write as the record that it was honoured.
 */
internal class LocalExportCredits(
    private val local: PurchaseCreditsLocalDataSource,
) : ExportCredits {

    override suspend fun available(): Int = local.available(CreditKind.RECORD_EXPORT)

    override suspend fun spend(): Boolean = local.spend(CreditKind.RECORD_EXPORT)
}
