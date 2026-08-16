package com.hopcape.odo.core.data.record

/**
 * Storage for the record-export tally.
 *
 * The month arrives as an opaque key rather than a date, so storage never has an opinion about
 * calendars or timezones. It stores counts against strings — the same split as
 * [ScanUsageLocalDataSource][com.hopcape.odo.core.data.scan.ScanUsageLocalDataSource].
 */
interface RecordExportUsageLocalDataSource {

    /** Every month's count summed — the lifetime tally. Zero when nothing has been exported. */
    suspend fun countAllTime(): Int

    /** Count one more against [month], starting the month's row if this is its first. */
    suspend fun increment(month: String)
}
