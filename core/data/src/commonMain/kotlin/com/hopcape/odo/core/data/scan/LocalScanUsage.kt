package com.hopcape.odo.core.data.scan

import com.hopcape.odo.core.domain.scan.entitlement.ScanUsage
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * The scan tally, counted on this device against the calendar month.
 *
 * The month is the device's, not UTC's. A cap the owner is told resets "next month" has to
 * reset when their calendar says so, not when a server in another timezone agrees.
 *
 * There is no reset to run. A new month is a different key, so the count starts at zero
 * because nothing has been written under it yet — nothing has to fire at midnight on the 1st,
 * and a phone that was switched off over the month boundary is not a special case.
 */
internal class LocalScanUsage(
    private val local: ScanUsageLocalDataSource,
    private val clock: Clock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ScanUsage {

    override suspend fun usedThisMonth(): Int = local.countFor(currentMonth())

    override suspend fun recordScan() = local.increment(currentMonth())

    /** `YYYY-MM`. Zero-padded so the keys sort, which is free and costs one format call. */
    private fun currentMonth(): String {
        val date = clock.now().toLocalDateTime(timeZone).date
        return "${date.year}-${date.monthNumber.toString().padStart(2, '0')}"
    }
}
