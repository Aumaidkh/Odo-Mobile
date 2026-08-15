package com.hopcape.odo.core.data.record

import com.hopcape.odo.core.domain.record.entitlement.RecordExportUsage
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * The record-export tally, counted on this device.
 *
 * [used] sums every month, because the cap in force is a lifetime one — an export is a rare
 * act, so a monthly allowance would never be reached. Writes are still keyed by month, which
 * costs nothing and leaves the door open to a monthly cap later without a migration.
 *
 * The month is the device's, not UTC's, matching
 * [LocalScanUsage][com.hopcape.odo.core.data.scan.LocalScanUsage] — one convention for both
 * tallies, so a phone that crosses a timezone does not have them disagree.
 */
internal class LocalRecordExportUsage(
    private val local: RecordExportUsageLocalDataSource,
    private val clock: Clock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : RecordExportUsage {

    override suspend fun used(): Int = local.countAllTime()

    override suspend fun recordExport() = local.increment(currentMonth())

    /** `YYYY-MM`. Zero-padded so the keys sort, which is free and costs one format call. */
    private fun currentMonth(): String {
        val date = clock.now().toLocalDateTime(timeZone).date
        return "${date.year}-${date.monthNumber.toString().padStart(2, '0')}"
    }
}
