package com.hopcape.odo.core.platform.logging

import com.hopcape.logging.api.LogFileNaming
import com.hopcape.logging.api.LogFileStats
import java.io.File
import java.nio.file.Files
import java.util.zip.GZIPInputStream
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AndroidLogFileStoreTest {

    private lateinit var dir: File
    private val stats = LogFileStats(lineCount = 2, warnCount = 0, errorCount = 0, hadFatal = false)

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("android-log-file-store-test").toFile()
    }

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun decompress(bytes: ByteArray): String =
        GZIPInputStream(bytes.inputStream()).use { it.readBytes().decodeToString() }

    @Test
    fun sealActive_withNothingWritten_returnsNull() {
        val store = AndroidLogFileStore(dir)
        assertNull(store.sealActive(stats))
    }

    @Test
    fun appendThenSeal_producesAGzippedFileThatDecompressesToWhatWasWritten() {
        val store = AndroidLogFileStore(dir)
        store.appendToActive(listOf("line one", "line two"))
        val handle = checkNotNull(store.sealActive(stats))

        assertTrue(handle.name.endsWith(LogFileNaming.SEALED_SUFFIX))
        val bytes = checkNotNull(store.read(handle.name))
        assertEquals("line one\nline two\n", decompress(bytes))
        assertEquals(stats, handle.stats)
    }

    @Test
    fun metaSidecar_survivesANewStoreInstance_overTheSameDirectory() {
        // Stands in for a process restart: the first store dies, a fresh one reads what's
        // on disk — the same shape as InMemoryLogFileStoreTest's in-memory equivalent, but
        // this is the one that actually has to cross a process boundary for real.
        val firstProcess = AndroidLogFileStore(dir)
        firstProcess.appendToActive(listOf("during the first run"))
        val sealedName = checkNotNull(firstProcess.sealActive(stats)).name

        val secondProcess = AndroidLogFileStore(dir)
        val recovered = secondProcess.listSealed().single()

        assertEquals(sealedName, recovered.name)
        assertEquals(stats, recovered.stats)
    }

    @Test
    fun sealOrphans_recoversAnActiveFileLeftByAKilledProcess_withNullStats() {
        val killedProcess = AndroidLogFileStore(dir)
        killedProcess.appendToActive(listOf("never got sealed"))
        // No sealActive() call here — this is the crash: the process dies mid-session.

        val nextLaunch = AndroidLogFileStore(dir)
        val recovered = nextLaunch.sealOrphans().single()

        assertNull(recovered.stats, "an orphan's live counters never ran — must not be reported as zero")
        assertEquals("never got sealed\n", decompress(checkNotNull(nextLaunch.read(recovered.name))))
        assertEquals(0, nextLaunch.sealOrphans().size, "the orphan must not be recovered a second time")
    }

    @Test
    fun sealOrphans_doesNotTouchTheCallersOwnCurrentlyActiveFile() {
        val store = AndroidLogFileStore(dir)
        store.appendToActive(listOf("still being written by me"))

        val orphans = store.sealOrphans()

        assertTrue(orphans.isEmpty(), "a store's own in-flight active file is not an orphan")
        // Still writable/sealable afterwards — proves it wasn't sealed out from under itself.
        val handle = checkNotNull(store.sealActive(stats))
        assertEquals("still being written by me\n", decompress(checkNotNull(store.read(handle.name))))
    }

    @Test
    fun delete_removesTheSealedFileAndItsMetaSidecarTogether() {
        val store = AndroidLogFileStore(dir)
        store.appendToActive(listOf("x"))
        val handle = checkNotNull(store.sealActive(stats))
        val metaFile = File(dir, LogFileNaming.metaFileName(handle.name))
        assertTrue(metaFile.exists())

        store.delete(handle.name)

        assertNull(store.read(handle.name))
        assertTrue(store.listSealed().isEmpty())
        assertTrue(!metaFile.exists(), "the .meta sidecar must be deleted alongside its .log.gz")
    }

    @Test
    fun totalBytes_sumsOnlySealedFiles_neverTheActiveOne() {
        val store = AndroidLogFileStore(dir)
        store.appendToActive(listOf("a somewhat long line to have a nonzero size"))
        val handle = checkNotNull(store.sealActive(stats))

        // A second, still-open active file must not count toward totalBytes.
        store.appendToActive(listOf("this one is still active"))

        assertEquals(handle.sizeBytes, store.totalBytes())
    }

    @Test
    fun activeFileName_isNullUntilSomethingIsWritten() {
        val store = AndroidLogFileStore(dir)
        assertNull(store.activeFileName())
    }

    @Test
    fun activeFileName_namesTheOpenFile_andReadReturnsItsPlainUncompressedBytes() {
        val store = AndroidLogFileStore(dir)
        store.appendToActive(listOf("still being written"))

        val name = checkNotNull(store.activeFileName())
        assertTrue(name.endsWith(LogFileNaming.ACTIVE_SUFFIX), "the active file is never gzip'd")
        assertEquals("still being written\n", checkNotNull(store.read(name)).decodeToString())
    }

    @Test
    fun activeFileName_isNullAgainAfterSealing() {
        val store = AndroidLogFileStore(dir)
        store.appendToActive(listOf("x"))
        store.sealActive(stats)

        assertNull(store.activeFileName())
    }

    @Test
    fun activeFileName_findsAnotherInstancesActiveFileInTheSameDirectory() {
        // The production shape: OdoApplication constructs one AndroidLogFileStore directly
        // for HLogger, and a second is Koin-bound for readers like the Logs screen. The
        // reader's instance never calls appendToActive itself, so `activeFile` alone would
        // never see what the writer's instance is doing — it has to fall back to a scan.
        val writer = AndroidLogFileStore(dir)
        writer.appendToActive(listOf("written by the other instance"))

        val reader = AndroidLogFileStore(dir)

        val name = checkNotNull(reader.activeFileName())
        assertEquals(writer.activeFileName(), name)
        assertEquals("written by the other instance\n", checkNotNull(reader.read(name)).decodeToString())
    }

    @Test
    fun listSealed_reconstructsOpenedAtMsFromTheFileName() {
        val store = AndroidLogFileStore(dir)
        store.appendToActive(listOf("x"))
        val sealed = checkNotNull(store.sealActive(stats))

        val expectedOpenedAtMs = checkNotNull(LogFileNaming.parseOpenedAtMs(sealed.name))
        assertEquals(expectedOpenedAtMs, sealed.openedAtMs)
        assertEquals(expectedOpenedAtMs, store.listSealed().single().openedAtMs)
    }
}
