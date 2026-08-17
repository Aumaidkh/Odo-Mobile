package com.hopcape.odo.core.data.scan

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Which month a scan lands in, which is the only decision [LocalScanUsage] makes. The
 * counting itself is SQL, and is tested against the real database in
 * `SqlDelightScanUsageLocalDataSourceTest`.
 */
class LocalScanUsageTest {

    private val store = FakeScanUsageStore()

    private fun usageAt(instant: String, timeZone: TimeZone = TimeZone.UTC) =
        LocalScanUsage(local = store, clock = FixedClock(Instant.parse(instant)), timeZone = timeZone)

    @Test
    fun scansAreCountedAgainstTheCalendarMonth() = runTest {
        usageAt("2026-08-14T09:00:00Z").recordScan()

        assertEquals(mapOf("2026-08" to 1), store.counts)
    }

    @Test
    fun theMonthIsZeroPaddedSoTheKeysSort() = runTest {
        usageAt("2026-01-05T09:00:00Z").recordScan()

        assertEquals(setOf("2026-01"), store.counts.keys)
    }

    /**
     * The cap is a lifetime one (#248), so a new month is not a fresh allowance — the rows
     * are still keyed by month, but the total sums across all of them.
     */
    @Test
    fun aNewMonthDoesNotResetTheCount() = runTest {
        repeat(3) { usageAt("2026-08-31T18:00:00Z").recordScan() }
        usageAt("2026-09-01T06:00:00Z").recordScan()

        assertEquals(4, usageAt("2026-09-01T06:00:00Z").used())
    }

    @Test
    fun theMonthIsTheDevicesNotUtcs() = runTest {
        // 20:00 UTC on the 31st is already the 1st in India, and the owner was told their
        // scans reset next month — theirs, not a server's.
        val instant = "2026-08-31T20:00:00Z"
        val india = TimeZone.of("Asia/Kolkata")

        usageAt(instant, india).recordScan()

        assertEquals(setOf("2026-09"), store.counts.keys)
    }
}

private class FakeScanUsageStore : ScanUsageLocalDataSource {
    val counts = mutableMapOf<String, Int>()

    override suspend fun countAllTime(): Int = counts.values.sum()

    override suspend fun countFor(month: String): Int = counts[month] ?: 0

    override suspend fun increment(month: String) {
        counts[month] = (counts[month] ?: 0) + 1
    }
}

private class FixedClock(private val instant: Instant) : Clock {
    override fun now(): Instant = instant
}
