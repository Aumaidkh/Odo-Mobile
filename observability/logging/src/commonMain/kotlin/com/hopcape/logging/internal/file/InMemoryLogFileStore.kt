package com.hopcape.logging.internal.file

import com.hopcape.logging.api.LogFileHandle
import com.hopcape.logging.api.LogFileNaming
import com.hopcape.logging.api.LogFileStats
import com.hopcape.logging.api.LogFileStore

/**
 * The default [LogFileStore] and the test double for everything above it — nothing here
 * survives a process restart, so [sealOrphans] always finds nothing (there is no "left
 * behind by a killed process" for memory that died with the process). It is also what iOS
 * gets: the MVP ships no on-disk store or upload path for that platform.
 *
 * [nowMs] is a seam rather than a direct `kotlin.time.Clock.System` call so tests can drive
 * rotation/retention deterministically without sleeping.
 */
internal class InMemoryLogFileStore(
    private val nowMs: () -> Long,
) : LogFileStore {

    private var active: ActiveFile? = null
    private val sealed = linkedMapOf<String, SealedFile>()

    override fun appendToActive(lines: List<String>) {
        val file = active ?: ActiveFile(openedAtMs = nowMs()).also { active = it }
        file.lines += lines
    }

    override fun sealActive(stats: LogFileStats): LogFileHandle? {
        val file = active ?: return null
        active = null
        return seal(file, stats)
    }

    override fun sealOrphans(): List<LogFileHandle> = emptyList()

    override fun listSealed(): List<LogFileHandle> = sealed.values.map { it.handle }

    override fun read(name: String): ByteArray? = sealed[name]?.bytes

    override fun delete(name: String) {
        sealed.remove(name)
    }

    override fun totalBytes(): Long = sealed.values.sumOf { it.handle.sizeBytes }

    private fun seal(file: ActiveFile, stats: LogFileStats): LogFileHandle {
        val activeName = LogFileNaming.activeFileName(file.openedAtMs)
        val sealedName = LogFileNaming.sealedFileName(activeName)
        val bytes = file.lines.joinToString("\n").encodeToByteArray()
        val handle = LogFileHandle(
            name = sealedName,
            sizeBytes = bytes.size.toLong(),
            openedAtMs = file.openedAtMs,
            sealedAtMs = nowMs(),
            stats = stats,
        )
        sealed[sealedName] = SealedFile(handle, bytes)
        return handle
    }

    private class ActiveFile(val openedAtMs: Long) {
        val lines = mutableListOf<String>()
    }

    private class SealedFile(val handle: LogFileHandle, val bytes: ByteArray)
}
