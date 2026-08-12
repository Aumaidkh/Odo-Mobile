package com.hopcape.logging.internal.upload

import com.hopcape.logging.FakeLogUploadTarget
import com.hopcape.logging.RecordingLogger
import com.hopcape.logging.api.LogFileStats
import com.hopcape.logging.api.LogFileStore
import com.hopcape.logging.api.LogUploadOutcome
import com.hopcape.logging.api.LogUploadResult
import com.hopcape.logging.internal.file.InMemoryLogFileStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LogUploadCoordinatorTest {

    private val stats = LogFileStats(lineCount = 1, warnCount = 0, errorCount = 0, hadFatal = false)
    private var clock = 0L
    private val store = InMemoryLogFileStore(nowMs = { clock })

    private fun sealFile(content: String = "x") {
        clock += 2_000L // stays clear of LogFileNaming's 1-second name resolution
        store.appendToActive(listOf(content))
        checkNotNull(store.sealActive(stats))
    }

    @Test
    fun uploadPending_withNoTarget_isSkipped_andNeverTouchesTheStore() = runTest {
        sealFile()
        val logger = RecordingLogger()
        val coordinator = LogUploadCoordinator(logger, store, target = null)

        val outcome = coordinator.uploadPending(isManual = false)

        assertEquals(LogUploadOutcome.Skipped, outcome)
        assertEquals(1, store.listSealed().size, "nothing should have been deleted")
        assertEquals(0, logger.flushCount, "no point flushing for an upload that can't happen")
    }

    @Test
    fun uploadPending_nonManual_withoutConsent_isSkipped() = runTest {
        sealFile()
        val target = FakeLogUploadTarget()
        val coordinator = LogUploadCoordinator(RecordingLogger(), store, target)

        val outcome = coordinator.uploadPending(isManual = false)

        assertEquals(LogUploadOutcome.Skipped, outcome)
        assertTrue(target.uploaded.isEmpty())
        assertEquals(1, store.listSealed().size)
    }

    @Test
    fun uploadPending_nonManual_withConsentGranted_uploads() = runTest {
        sealFile()
        val target = FakeLogUploadTarget()
        val coordinator = LogUploadCoordinator(RecordingLogger(), store, target)
        coordinator.setAutoUploadConsent(true)

        val outcome = coordinator.uploadPending(isManual = false)

        assertEquals(LogUploadOutcome.Delivered(1), outcome)
        assertEquals(1, target.uploaded.size)
    }

    @Test
    fun uploadPending_manual_bypassesConsent_evenWhenNeverGranted() = runTest {
        sealFile()
        val target = FakeLogUploadTarget()
        val coordinator = LogUploadCoordinator(RecordingLogger(), store, target)
        // setAutoUploadConsent deliberately never called.

        val outcome = coordinator.uploadPending(isManual = true)

        assertEquals(LogUploadOutcome.Delivered(1), outcome)
    }

    @Test
    fun uploadPending_flushesTheLoggerFirst_soAnOpenFileGetsSealedBeforeListing() = runTest {
        val logger = RecordingLogger()
        val coordinator = LogUploadCoordinator(logger, store, FakeLogUploadTarget())

        coordinator.uploadPending(isManual = true)

        assertEquals(1, logger.flushCount)
    }

    @Test
    fun uploadPending_delivered_deletesTheFile() = runTest {
        sealFile()
        val coordinator = LogUploadCoordinator(RecordingLogger(), store, FakeLogUploadTarget { LogUploadResult.DELIVERED })

        coordinator.uploadPending(isManual = true)

        assertTrue(store.listSealed().isEmpty())
    }

    @Test
    fun uploadPending_rejected_deletesTheFileToo_soAPoisonedUploadCannotWedgeTheQueue() = runTest {
        sealFile()
        val coordinator = LogUploadCoordinator(RecordingLogger(), store, FakeLogUploadTarget { LogUploadResult.REJECTED })

        val outcome = coordinator.uploadPending(isManual = true)

        assertEquals(LogUploadOutcome.Delivered(1), outcome)
        assertTrue(store.listSealed().isEmpty())
    }

    @Test
    fun uploadPending_retry_leavesTheFileAndReportsPartial() = runTest {
        sealFile()
        val coordinator = LogUploadCoordinator(RecordingLogger(), store, FakeLogUploadTarget { LogUploadResult.RETRY })

        val outcome = coordinator.uploadPending(isManual = true)

        assertEquals(LogUploadOutcome.Partial, outcome)
        assertEquals(1, store.listSealed().size, "a RETRY file must not be deleted")
    }

    @Test
    fun uploadPending_mixedResults_partialWinsOverDelivered() = runTest {
        sealFile("first")
        sealFile("second")
        var call = 0
        val target = FakeLogUploadTarget {
            call++
            if (call == 1) LogUploadResult.DELIVERED else LogUploadResult.RETRY
        }
        val coordinator = LogUploadCoordinator(RecordingLogger(), store, target)

        val outcome = coordinator.uploadPending(isManual = true)

        assertEquals(LogUploadOutcome.Partial, outcome)
        assertEquals(1, store.listSealed().size, "only the RETRY file should survive")
    }

    @Test
    fun uploadPending_noSealedFiles_returnsDeliveredZero() = runTest {
        val coordinator = LogUploadCoordinator(RecordingLogger(), store, FakeLogUploadTarget())

        val outcome = coordinator.uploadPending(isManual = true)

        assertEquals(LogUploadOutcome.Delivered(0), outcome)
    }

    @Test
    fun uploadPending_readReturningNull_skipsThatFileWithoutCrashing() = runTest {
        // Simulates a file vanishing between listSealed() and read() — listSealed() still
        // reports it, but read() answers null, as it contractually may.
        sealFile()
        val vanishingStore = object : LogFileStore by store {
            override fun read(name: String): ByteArray? = null
        }
        val target = FakeLogUploadTarget()
        val coordinator = LogUploadCoordinator(RecordingLogger(), vanishingStore, target)

        val outcome = coordinator.uploadPending(isManual = true)

        assertEquals(LogUploadOutcome.Delivered(0), outcome)
        assertTrue(target.uploaded.isEmpty())
    }

    @Test
    fun uploadPending_logsItsOwnOutcome_soAPassNeverFinishesQuietly() = runTest {
        sealFile()
        val logger = RecordingLogger()
        val coordinator = LogUploadCoordinator(logger, store, FakeLogUploadTarget { LogUploadResult.RETRY })

        coordinator.uploadPending(isManual = true)

        val summary = logger.entries.last()
        assertEquals("upload_pass.done", summary.event)
        assertEquals(true, summary.fields["isManual"])
        assertEquals("Partial", summary.fields["outcome"])
    }

    @Test
    fun uploadPending_skipped_stillLogsSoAMissingConsentIsNotSilent() = runTest {
        val logger = RecordingLogger()
        val coordinator = LogUploadCoordinator(logger, store, FakeLogUploadTarget())

        coordinator.uploadPending(isManual = false) // no consent granted

        assertEquals("upload_pass.done", logger.entries.last().event)
        assertEquals("Skipped", logger.entries.last().fields["outcome"])
    }
}
