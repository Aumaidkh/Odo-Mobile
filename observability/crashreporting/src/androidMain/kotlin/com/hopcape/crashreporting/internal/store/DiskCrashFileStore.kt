package com.hopcape.crashreporting.internal.store

import com.hopcape.crashreporting.internal.model.CrashReport
import java.io.File

// ─────────────────────────────────────────────────────────────
// DiskCrashFileStore — the real Android store. One file per crash
// ("<crashId>.crash") under [dir], holding the MiniJson-serialized
// report (shared with the in-memory store via CrashReportSerializer).
//
// writeSync is DELIBERATELY blocking, plain java.io: at a fatal crash
// the process is about to die, so it can't wait on a coroutine
// dispatcher to schedule the write. This is the one place the module
// steps outside its "async everywhere" design — because the context
// is fundamentally different. Any failure here is swallowed: we're
// already on the worst-case path and must not throw a SECOND
// exception out of the crash handler.
// ─────────────────────────────────────────────────────────────
internal class DiskCrashFileStore(private val dir: File) : CrashFileStore {

    override fun writeSync(report: CrashReport) {
        try {
            if (!dir.exists()) dir.mkdirs()
            File(dir, "${report.crashId}$SUFFIX").writeText(CrashReportSerializer.toJson(report))
        } catch (_: Throwable) {
            // Last-resort path (e.g. disk full). Swallow — never throw from here.
        }
    }

    override fun readPending(): List<CrashReport> =
        dir.listFiles { file -> file.isFile && file.name.endsWith(SUFFIX) }
            ?.mapNotNull { file -> runCatching { file.readText() }.getOrNull()?.let(CrashReportSerializer::fromJson) }
            ?: emptyList()

    override fun clear(crashId: String) {
        runCatching { File(dir, "$crashId$SUFFIX").delete() }
    }

    private companion object {
        const val SUFFIX = ".crash"
    }
}
