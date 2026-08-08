package com.hopcape.odo.core.platform.logging

import com.hopcape.logging.api.LogFileHandle
import com.hopcape.logging.api.LogFileNaming
import com.hopcape.logging.api.LogFileStats
import com.hopcape.logging.api.LogFileStore
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.GZIPOutputStream

/**
 * The real, on-disk [LogFileStore] — one `.log.active` file at a time under [dir], sealed to
 * gzip on rotation, each sealed file's [LogFileStats] persisted as a `.meta` sidecar so it
 * survives a process restart (docs/LOGGING_PLAN.md §6.4).
 *
 * Lives in `:core:platform`, not `:observability:logging`: `:infrastructure:supabase`
 * already depends on `:observability:logging` for its `Logger`, so a real disk store there
 * would be a cycle the moment it needed uploading — the same reason the upload port is a
 * port at all (plan §2, D5).
 *
 * Blocking, plain `java.io` — same choice as `crashreporting`'s `DiskCrashFileStore`. The
 * `LogFileStore` contract is deliberately non-`suspend` (mirrors `CrashFileStore`), and calls
 * are expected to already be serialized by the caller (`AsyncSink`'s single writer coroutine)
 * — so this needs no locking of its own, and every `appendToActive` flushes so an app kill
 * loses at most what the caller was still batching, not what already reached this store.
 *
 * Public, unlike most of this module's Android adapters: `HLogger.init(...)` runs in
 * `OdoApplication.onCreate` before the Koin graph starts (so the logger is ready the moment
 * Koin wiring itself needs to log), which means `:androidApp` constructs this directly
 * rather than resolving it through DI — the same reason
 * `:infrastructure:firebase:crashlytics`'s `FirebaseCrashlyticsSink` is public.
 * `corePlatformAndroidModule`'s own `single<LogFileStore>` binding exists for everything
 * downstream of Koin (the upload coordinator, `:feature:support`'s "send diagnostics").
 */
class AndroidLogFileStore(private val dir: File) : LogFileStore {

    private var activeFile: File? = null
    private var activeStream: BufferedOutputStream? = null

    override fun appendToActive(lines: List<String>) {
        val stream = activeStream ?: openNewActiveFile()
        for (line in lines) {
            stream.write(line.encodeToByteArray())
            stream.write(NEWLINE)
        }
        stream.flush()
    }

    override fun sealActive(stats: LogFileStats): LogFileHandle? {
        val file = activeFile ?: return null
        activeStream?.close()
        activeStream = null
        activeFile = null
        return seal(file, stats)
    }

    override fun sealOrphans(): List<LogFileHandle> {
        if (!dir.exists()) return emptyList()
        val currentActiveName = activeFile?.name
        val orphans = dir.listFiles { f -> f.isFile && LogFileNaming.isActive(f.name) && f.name != currentActiveName }
            ?: return emptyList()
        return orphans.mapNotNull { seal(it, stats = null) }
    }

    override fun listSealed(): List<LogFileHandle> {
        if (!dir.exists()) return emptyList()
        val sealedFiles = dir.listFiles { f -> f.isFile && LogFileNaming.isSealed(f.name) } ?: return emptyList()
        return sealedFiles.mapNotNull(::handleFor)
    }

    override fun read(name: String): ByteArray? {
        val file = File(dir, name)
        return if (file.isFile) file.readBytes() else null
    }

    override fun delete(name: String) {
        File(dir, name).delete()
        File(dir, LogFileNaming.metaFileName(name)).delete()
    }

    override fun totalBytes(): Long = listSealed().sumOf { it.sizeBytes }

    private fun openNewActiveFile(): BufferedOutputStream {
        dir.mkdirs()
        val file = File(dir, LogFileNaming.activeFileName(System.currentTimeMillis()))
        val stream = BufferedOutputStream(FileOutputStream(file, /* append = */ true))
        activeFile = file
        activeStream = stream
        return stream
    }

    /** Seals whatever is at [activeOrOrphanFile] into a `.log.gz` (+ `.meta` if [stats] is
     *  known). Returns `null` if the file has already vanished from under us. */
    private fun seal(activeOrOrphanFile: File, stats: LogFileStats?): LogFileHandle? {
        if (!activeOrOrphanFile.exists()) return null
        val openedAtMs = LogFileNaming.parseOpenedAtMs(activeOrOrphanFile.name) ?: return null

        val sealedFile = File(dir, LogFileNaming.sealedFileName(activeOrOrphanFile.name))
        GZIPOutputStream(FileOutputStream(sealedFile)).use { out -> activeOrOrphanFile.inputStream().use { it.copyTo(out) } }
        activeOrOrphanFile.delete()
        stats?.let { writeMeta(sealedFile, it) }

        return LogFileHandle(
            name = sealedFile.name,
            sizeBytes = sealedFile.length(),
            openedAtMs = openedAtMs,
            sealedAtMs = System.currentTimeMillis(),
            stats = stats,
        )
    }

    private fun handleFor(sealedFile: File): LogFileHandle? {
        val openedAtMs = LogFileNaming.parseOpenedAtMs(sealedFile.name) ?: return null
        return LogFileHandle(
            name = sealedFile.name,
            sizeBytes = sealedFile.length(),
            openedAtMs = openedAtMs,
            sealedAtMs = sealedFile.lastModified(),
            stats = readMeta(sealedFile),
        )
    }

    /** `lineCount|warnCount|errorCount|hadFatal` — four known fields, so a real serializer
     *  buys nothing this module doesn't already have to hand-roll for one line of text. */
    private fun writeMeta(sealedFile: File, stats: LogFileStats) {
        val metaFile = File(dir, LogFileNaming.metaFileName(sealedFile.name))
        metaFile.writeText("${stats.lineCount}$META_DELIMITER${stats.warnCount}$META_DELIMITER${stats.errorCount}$META_DELIMITER${stats.hadFatal}")
    }

    private fun readMeta(sealedFile: File): LogFileStats? {
        val metaFile = File(dir, LogFileNaming.metaFileName(sealedFile.name))
        if (!metaFile.isFile) return null
        val parts = metaFile.readText().trim().split(META_DELIMITER)
        if (parts.size != 4) return null
        return runCatching {
            LogFileStats(
                lineCount = parts[0].toInt(),
                warnCount = parts[1].toInt(),
                errorCount = parts[2].toInt(),
                hadFatal = parts[3].toBooleanStrict(),
            )
        }.getOrNull()
    }

    private companion object {
        val NEWLINE = "\n".encodeToByteArray()
        const val META_DELIMITER = "|"
    }
}
