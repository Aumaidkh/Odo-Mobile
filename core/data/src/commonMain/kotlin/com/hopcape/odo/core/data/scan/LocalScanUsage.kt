package com.hopcape.odo.core.data.scan

import com.hopcape.odo.core.domain.scan.entitlement.ScanUsage
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * The scan tally, counted on this device for the lifetime of the install.
 *
 * The rows stay keyed by month even though the cap is a lifetime one (#248), matching
 * `LocalRecordExportUsage`: the total is a SUM over every month, and keeping the months
 * means a per-month figure is still there if it is ever wanted. Writing still goes to the
 * current month, so the table needs no migration to change what the cap counts.
 *
 * The month is the device's, not UTC's — one convention across both tallies, so a phone that
 * crosses a timezone does not have them disagree.
 */
internal class LocalScanUsage(
    private val local: ScanUsageLocalDataSource,
    private val clock: Clock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ScanUsage {

    override suspend fun used(): Int = local.countAllTime()

    override suspend fun recordScan() = local.increment(currentMonth())

    /** `YYYY-MM`. Zero-padded so the keys sort, which is free and costs one format call. */
    private fun currentMonth(): String {
        val date = clock.now().toLocalDateTime(timeZone).date
        return "${date.year}-${date.monthNumber.toString().padStart(2, '0')}"
    }
}
