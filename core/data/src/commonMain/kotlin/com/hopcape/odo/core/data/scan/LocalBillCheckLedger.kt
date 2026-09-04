package com.hopcape.odo.core.data.scan

import com.hopcape.odo.core.domain.scan.entitlement.BillCheckLedger

/**
 * [BillCheckLedger] on the device's own rows. A thin pass-through, so the domain port stays
 * free of the data layer's storage interface.
 */
internal class LocalBillCheckLedger(
    private val local: BillCheckLedgerLocalDataSource,
) : BillCheckLedger {

    override suspend fun claim(billId: String): Boolean = local.claim(billId)
}
