package com.hopcape.crashreporting.internal.platform

import com.hopcape.crashreporting.internal.store.CrashFileStore
import com.hopcape.crashreporting.internal.store.InMemoryCrashFileStore

// ─────────────────────────────────────────────────────────────
// iOS actuals — documented no-ops. iOS is Phase 2 for Odo (the MVP
// is Android-only), so these satisfy the expect contract and keep the
// module compiling for the iOS targets without investing in
// platform work yet.
//
// A real implementation would install NSSetUncaughtExceptionHandler
// (plus POSIX signal handlers for the crashes Objective-C exceptions
// don't cover) and write to NSFileManager's caches directory. Both
// are Phase-2 work.
// ─────────────────────────────────────────────────────────────

internal actual fun installUncaughtCrashHandler(onFatal: (Throwable) -> Unit) {
    // no-op (Phase 2): would use NSSetUncaughtExceptionHandler + signal handlers.
}

internal actual fun createCrashFileStore(dirPath: String): CrashFileStore =
    InMemoryCrashFileStore() // Phase 2: NSFileManager-backed synchronous store.
