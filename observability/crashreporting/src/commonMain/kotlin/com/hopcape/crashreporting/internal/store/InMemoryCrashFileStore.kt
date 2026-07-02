@file:OptIn(ExperimentalAtomicApi::class)

package com.hopcape.crashreporting.internal.store

import com.hopcape.crashreporting.internal.model.CrashReport
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

// ─────────────────────────────────────────────────────────────
// InMemoryCrashFileStore — the KMP-native default. Holds reports in
// a lock-free copy-on-write map (same pattern as InMemorySpanStore),
// so it's safe to touch from a crashing thread and a startup upload.
//
// It obviously does NOT survive process death — so a real fatal
// crash written here is lost. It exists for tests, for the iOS
// Phase-2 stub, and as the fallback when no crashDirPath is
// configured; production Android uses DiskCrashFileStore. To keep it
// honest, it serializes through the same CrashReportSerializer the
// disk store uses (round-tripping every write).
// ─────────────────────────────────────────────────────────────
internal class InMemoryCrashFileStore : CrashFileStore {

    private val files = AtomicReference<Map<String, String>>(emptyMap())

    override fun writeSync(report: CrashReport) {
        val json = CrashReportSerializer.toJson(report)
        mutate { it + (report.crashId to json) }
    }

    override fun readPending(): List<CrashReport> =
        files.load().values.mapNotNull { CrashReportSerializer.fromJson(it) }

    override fun clear(crashId: String) = mutate { it - crashId }

    private inline fun mutate(transform: (Map<String, String>) -> Map<String, String>) {
        while (true) {
            val current = files.load()
            if (files.compareAndSet(current, transform(current))) return
        }
    }
}
