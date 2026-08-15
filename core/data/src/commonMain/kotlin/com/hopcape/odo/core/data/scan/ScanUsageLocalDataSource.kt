package com.hopcape.odo.core.data.scan

/**
 * Local persistence for the monthly scan tally. Hides the SQLDelight database from
 * [LocalScanUsage], which owns the only thing this does not: which month it is.
 *
 * The month arrives as an opaque key rather than a date, so storage never has an opinion
 * about calendars or timezones. It stores counts against strings.
 */
interface ScanUsageLocalDataSource {

    /** Scans counted against [month]; zero when the month has no row yet. */
    suspend fun countFor(month: String): Int

    /** Count one more against [month], starting the month's row if this is its first. */
    suspend fun increment(month: String)
}
