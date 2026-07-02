package com.hopcape.crashreporting.internal.platform

import com.hopcape.crashreporting.internal.store.CrashFileStore

// ─────────────────────────────────────────────────────────────
// PlatformCrash — the two genuinely platform-specific seams, behind
// expect/actual (top-level funs, so no -Xexpect-actual-classes flag
// is needed). Everything else in the module is common. Follows the
// core/data DriverFactory precedent: declare the contract in
// commonMain, provide real behaviour in androidMain and a documented
// no-op in iosMain (iOS is Phase 2).
// ─────────────────────────────────────────────────────────────

/**
 * Installs a process-wide uncaught-exception handler that invokes [onFatal] with
 * the throwable. The Android actual CHAINS the previously-installed handler
 * afterwards (so a vendor SDK's own handler still fires); iOS is a no-op stub.
 */
internal expect fun installUncaughtCrashHandler(onFatal: (Throwable) -> Unit)

/**
 * Builds the platform [CrashFileStore] rooted at [dirPath]. Android returns a
 * java.io-backed store that writes synchronously; iOS returns an in-memory store
 * for now. A blank [dirPath] yields an in-memory store on every platform.
 */
internal expect fun createCrashFileStore(dirPath: String): CrashFileStore
