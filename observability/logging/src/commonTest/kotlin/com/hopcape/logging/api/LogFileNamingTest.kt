package com.hopcape.logging.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LogFileNamingTest {

    @Test
    fun stemFor_formatsUtcDateTime_colonsStrippedAndZuluSuffixed() {
        // 2021-01-01T00:00:00Z
        assertEquals("2021-01-01T00-00-00Z", LogFileNaming.stemFor(1_609_459_200_000L))
        // 2021-01-01T14:32:05Z
        assertEquals("2021-01-01T14-32-05Z", LogFileNaming.stemFor(1_609_511_525_000L))
    }

    @Test
    fun stemFor_handlesLeapDay() {
        // 2024-02-29T00:00:00Z — 2024 is a leap year, this date only exists because of it.
        assertEquals("2024-02-29T00-00-00Z", LogFileNaming.stemFor(1_709_164_800_000L))
    }

    @Test
    fun stemFor_handlesTimestampsBeforeEpoch() {
        // 1969-12-31T23:59:00Z — one minute before the epoch.
        assertEquals("1969-12-31T23-59-00Z", LogFileNaming.stemFor(-60_000L))
    }

    @Test
    fun activeThenSealedThenMeta_shareOneStemAcrossAllThreeNames() {
        val active = LogFileNaming.activeFileName(1_609_459_200_000L)
        val sealed = LogFileNaming.sealedFileName(active)
        val meta = LogFileNaming.metaFileName(sealed)

        assertEquals("2021-01-01T00-00-00Z.log.active", active)
        assertEquals("2021-01-01T00-00-00Z.log.gz", sealed)
        assertEquals("2021-01-01T00-00-00Z.log.gz.meta", meta)
    }

    @Test
    fun isActive_isSealed_areMutuallyExclusive() {
        val active = LogFileNaming.activeFileName(0L)
        val sealed = LogFileNaming.sealedFileName(active)

        assertTrue(LogFileNaming.isActive(active))
        assertFalse(LogFileNaming.isSealed(active))

        assertTrue(LogFileNaming.isSealed(sealed))
        assertFalse(LogFileNaming.isActive(sealed))
    }

    @Test
    fun parseOpenedAtMs_roundTripsWithStemFor_forAWideRangeOfTimestamps() {
        val samples = listOf(
            0L, // 1970-01-01T00:00:00Z
            1_609_459_200_000L, // 2021-01-01T00:00:00Z
            1_609_511_525_000L, // 2021-01-01T14:32:05Z
            1_709_164_800_000L, // 2024-02-29T00:00:00Z (leap day)
            -60_000L, // 1969-12-31T23:59:00Z (before epoch)
        )
        for (openedAtMs in samples) {
            assertEquals(openedAtMs, LogFileNaming.parseOpenedAtMs(LogFileNaming.stemFor(openedAtMs)))
        }
    }

    @Test
    fun parseOpenedAtMs_acceptsActiveAndSealedNames_notJustABareStem() {
        val openedAtMs = 1_609_511_525_000L
        val active = LogFileNaming.activeFileName(openedAtMs)
        val sealed = LogFileNaming.sealedFileName(active)

        assertEquals(openedAtMs, LogFileNaming.parseOpenedAtMs(active))
        assertEquals(openedAtMs, LogFileNaming.parseOpenedAtMs(sealed))
    }

    @Test
    fun parseOpenedAtMs_returnsNull_forAnythingNotAWellFormedStem() {
        assertNull(LogFileNaming.parseOpenedAtMs("not-a-log-file.txt"))
        assertNull(LogFileNaming.parseOpenedAtMs(""))
        assertNull(LogFileNaming.parseOpenedAtMs("2021-01-01T00-00-00Z.meta")) // a bare .meta, no .log.gz
    }
}
