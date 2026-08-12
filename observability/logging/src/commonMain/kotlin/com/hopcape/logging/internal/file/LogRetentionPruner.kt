package com.hopcape.logging.internal.file

import com.hopcape.logging.api.FileLoggingConfig
import com.hopcape.logging.api.LogFileHandle
import com.hopcape.logging.api.LogFileStore

/**
 * Applies [FileLoggingConfig.RetentionPolicy] to a store's sealed files, in the fixed order
 * the config documents: age, then total size, then count. Run after every seal, so a store
 * that never crosses the thresholds does nothing here. Never touches the active file — the
 * store itself doesn't offer a way to delete one, and rotation is what ends its life.
 */
internal class LogRetentionPruner(
    private val store: LogFileStore,
    private val retention: FileLoggingConfig.RetentionPolicy,
    private val nowMs: () -> Long,
) {
    fun prune() {
        pruneOlderThanMaxAge()
        pruneOverTotalSize()
        pruneOverCount()
    }

    private fun pruneOlderThanMaxAge() {
        val cutoffMs = nowMs() - retention.maxAgeDays * MILLIS_PER_DAY
        store.listSealed()
            .filter { it.sealedAtMs < cutoffMs }
            .forEach { store.delete(it.name) }
    }

    private fun pruneOverTotalSize() {
        val oldestFirst = store.listSealed().sortedBy { it.sealedAtMs }
        var remainingBytes = oldestFirst.sumOf { it.sizeBytes }
        for (file in oldestFirst) {
            if (remainingBytes <= retention.maxTotalBytes) break
            store.delete(file.name)
            remainingBytes -= file.sizeBytes
        }
    }

    private fun pruneOverCount() {
        val oldestFirst = store.listSealed().sortedBy { it.sealedAtMs }
        val excess = oldestFirst.size - retention.maxFileCount
        if (excess <= 0) return
        oldestFirst.take(excess).forEach { store.delete(it.name) }
    }

    private companion object {
        const val MILLIS_PER_DAY = 86_400_000L
    }
}
