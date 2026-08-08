package com.hopcape.logging.internal.sinks

import com.hopcape.logging.api.FileLoggingConfig
import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.internal.file.InMemoryLogFileStore
import com.hopcape.logging.internal.file.LogRetentionPruner
import com.hopcape.logging.internal.file.RotationPolicy
import com.hopcape.logging.internal.model.LogEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileSinkTest {

    private var clock = 0L
    private val store = InMemoryLogFileStore(nowMs = { clock })

    private fun sink(rotation: RotationPolicy, minLevel: LogLevel = LogLevel.VERBOSE) = FileSink(
        store = store,
        rotation = rotation,
        retentionPruner = LogRetentionPruner(store, FileLoggingConfig.RetentionPolicy(), nowMs = { clock }),
        nowMs = { clock },
        minLevel = minLevel,
    )

    private fun event(level: LogLevel = LogLevel.INFO, tag: String = "T", name: String = "e") =
        LogEvent.Builder(tag, name).level(level).build()

    @Test
    fun write_belowMinLevel_neverReachesTheStore() {
        val fileSink = sink(RotationPolicy.maxSize(1L), minLevel = LogLevel.INFO)

        fileSink.write(event(LogLevel.VERBOSE))
        // Force whatever might have been written to seal, to make the assertion observable.
        fileSink.write(event(LogLevel.INFO)) // opens the file
        fileSink.write(event(LogLevel.INFO)) // rotates, sealing the first

        val sealed = store.listSealed().single()
        assertEquals(1, sealed.stats?.lineCount, "the VERBOSE event must not have been written")
    }

    @Test
    fun write_producesTheSameJsonShapeAsBefore() {
        val fileSink = sink(RotationPolicy.maxSize(1L))

        fileSink.write(
            LogEvent.Builder("Auth", "login")
                .level(LogLevel.WARN)
                .sessionId("s1")
                .field("userType", "returning")
                .build()
        )
        fileSink.write(event()) // rotates, sealing the line above

        val sealed = store.listSealed().single()
        val line = store.read(sealed.name)!!.decodeToString()

        assertTrue(line.startsWith("{\"ts\":"))
        assertTrue(line.contains("\"level\":\"WARN\""))
        assertTrue(line.contains("\"tag\":\"Auth\""))
        assertTrue(line.contains("\"event\":\"login\""))
        assertTrue(line.contains("\"sessionId\":\"s1\""))
        assertTrue(line.contains("\"flowId\":null"))
        assertTrue(line.contains("\"userType\":\"returning\""))
    }

    @Test
    fun rotation_sealsTheOldFileAndStartsAFreshOneRatherThanAppending() {
        val fileSink = sink(RotationPolicy.maxSize(1L))

        fileSink.write(event(name = "first"))
        fileSink.write(event(name = "second")) // rotates

        assertEquals(1, store.listSealed().size)
        val sealedLine = store.read(store.listSealed().single().name)!!.decodeToString()
        assertTrue(sealedLine.contains("\"event\":\"first\""))
    }

    @Test
    fun rotation_byUtcMidnight_sealsAcrossTheDayBoundary() {
        val fileSink = sink(RotationPolicy.utcMidnight())
        clock = DAY_ONE
        fileSink.write(event(name = "day-one"))

        clock = DAY_TWO
        fileSink.write(event(name = "day-two"))

        assertEquals(1, store.listSealed().size)
        val sealedLine = store.read(store.listSealed().single().name)!!.decodeToString()
        assertTrue(sealedLine.contains("\"event\":\"day-one\""))
    }

    @Test
    fun stats_countWarnErrorFatal_separatelyAndCorrectly() {
        // maxSize(1L) rotates on every single write (any non-empty line exceeds 1 byte), so
        // it can't hold five events open at once — utcMidnight lets several writes land in
        // one file, sealed only when the clock is deliberately pushed to the next day.
        val fileSink = sink(RotationPolicy.utcMidnight())

        clock = DAY_ONE
        fileSink.write(event(LogLevel.INFO))
        fileSink.write(event(LogLevel.WARN))
        fileSink.write(event(LogLevel.WARN))
        fileSink.write(event(LogLevel.ERROR))
        fileSink.write(event(LogLevel.FATAL))
        clock = DAY_TWO
        fileSink.write(event(LogLevel.INFO)) // rotates, sealing the five above

        val stats = store.listSealed().single().stats!!
        assertEquals(5, stats.lineCount)
        assertEquals(2, stats.warnCount)
        assertEquals(1, stats.errorCount, "FATAL must not double-count into errorCount")
        assertTrue(stats.hadFatal)
    }

    @Test
    fun stats_resetAfterEachSeal_dontLeakIntoTheNextFile() {
        val fileSink = sink(RotationPolicy.utcMidnight())

        clock = DAY_ONE
        fileSink.write(event(LogLevel.ERROR))
        clock = DAY_TWO
        fileSink.write(event(LogLevel.INFO)) // rotates: seals the ERROR-only file, opens a new one
        clock = DAY_THREE
        fileSink.write(event(LogLevel.INFO)) // rotates again: seals the INFO-only file

        val sealedByOpenTime = store.listSealed().sortedBy { it.openedAtMs }
        assertEquals(1, sealedByOpenTime[0].stats!!.errorCount)
        assertEquals(0, sealedByOpenTime[1].stats!!.errorCount)
        assertFalse(sealedByOpenTime[1].stats!!.hadFatal)
    }

    private companion object {
        const val DAY_ONE = 1_609_459_200_000L // 2021-01-01T00:00:00Z
        const val DAY_TWO = 1_609_545_600_000L // 2021-01-02T00:00:00Z
        const val DAY_THREE = 1_609_632_000_000L // 2021-01-03T00:00:00Z
    }
}
