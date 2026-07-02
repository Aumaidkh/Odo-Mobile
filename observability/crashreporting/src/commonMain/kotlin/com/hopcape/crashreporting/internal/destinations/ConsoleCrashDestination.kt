package com.hopcape.crashreporting.internal.destinations

import com.hopcape.crashreporting.internal.model.CrashReport

// ─────────────────────────────────────────────────────────────
// ConsoleCrashDestination — debug-only destination that prints a
// report as a short block (header + breadcrumb tail), so crashes are
// visible in logcat during development without a vendor backend.
// Added by the factory only when CrashConfig.isDebug is true.
// (Analog of the APM ConsoleSpanExporter.)
// ─────────────────────────────────────────────────────────────
internal class ConsoleCrashDestination(
    private val emit: (String) -> Unit = { kotlin.io.println(it) },
) : CrashDestination {

    override val name: String = "console"

    override fun record(report: CrashReport) {
        val kind = if (report.isFatal) "FATAL" else "non-fatal"
        emit("[Crash] $kind ${report.throwableType}: ${report.throwableMessage} (crash=${report.crashId})")
        report.breadcrumbs.takeLast(5).forEach { emit("[Crash]   • ${it.tag}: ${it.message}") }
    }

    override fun setCustomKey(key: String, value: Any?) {}
    override fun setUserId(userId: String?) {}
}
