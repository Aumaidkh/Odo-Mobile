package com.hopcape.crashreporting.internal.platform

import com.hopcape.crashreporting.internal.store.CrashFileStore
import com.hopcape.crashreporting.internal.store.DiskCrashFileStore
import com.hopcape.crashreporting.internal.store.InMemoryCrashFileStore
import java.io.File

// ─────────────────────────────────────────────────────────────
// Android actuals for the two platform seams.
// ─────────────────────────────────────────────────────────────

/**
 * Installs our handler on top of whatever was already registered, then CHAINS to
 * it. Chaining is the critical detail: Crashlytics/Sentry SDKs install their own
 * default handler, and if we replace it without calling it, their dashboards go
 * empty. We record first, then hand off so their handling still runs.
 */
internal actual fun installUncaughtCrashHandler(onFatal: (Throwable) -> Unit) {
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        runCatching { onFatal(throwable) } // never let our capture mask the original crash
        previous?.uncaughtException(thread, throwable) // chain — don't swallow OS/SDK handling
    }
}

/** Java.io-backed synchronous store; falls back to in-memory when no dir is configured. */
internal actual fun createCrashFileStore(dirPath: String): CrashFileStore =
    if (dirPath.isBlank()) InMemoryCrashFileStore() else DiskCrashFileStore(File(dirPath).apply { mkdirs() })
