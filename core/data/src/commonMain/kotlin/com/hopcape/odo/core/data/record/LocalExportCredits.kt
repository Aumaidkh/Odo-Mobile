package com.hopcape.odo.core.data.record

import com.hopcape.odo.core.domain.record.entitlement.ExportCredits

/**
 * [ExportCredits] on the device's own row.
 *
 * A thin pass-through: unlike the tally beside it, a credit has no period to decide, so
 * there is nothing here for this layer to own. It exists so the domain port stays free of
 * the data layer's storage interface, the same shape [LocalRecordExportUsage] has.
 */
internal class LocalExportCredits(
    private val local: ExportCreditsLocalDataSource,
) : ExportCredits {

    override suspend fun available(): Int = local.remaining()

    override suspend fun grant() = local.grant()

    override suspend fun spend(): Boolean = local.spend()
}
