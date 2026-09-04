package com.hopcape.odo.core.data.scan

import com.hopcape.odo.core.domain.scan.entitlement.ScanCredits

/**
 * [ScanCredits] on the device's own row.
 *
 * A thin pass-through: unlike the tally beside it, a credit has no period to decide, so
 * there is nothing here for this layer to own. It exists so the domain port stays free of
 * the data layer's storage interface — the same shape [LocalScanUsage] has.
 */
internal class LocalScanCredits(
    private val local: ScanCreditsLocalDataSource,
) : ScanCredits {

    override suspend fun available(): Int = local.remaining()

    override suspend fun grant(count: Int) = local.grant(count)

    override suspend fun spend(): Boolean = local.spend()
}
