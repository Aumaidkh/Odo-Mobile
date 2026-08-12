package com.hopcape.crashreporting.internal.store

import com.hopcape.crashreporting.internal.model.CrashReport

// ─────────────────────────────────────────────────────────────
// CrashFileStore — durable buffer for fatal crashes (DIP). The
// critical method is [writeSync]: at a fatal crash the process is
// dying, so the report MUST be flushed synchronously before the
// handler returns — an async/coroutine path can't be trusted to run.
// On the next launch [readPending] returns what was persisted so it
// can be uploaded, then [clear] removes each after successful
// delivery.
//
// The real Android implementation (DiskCrashFileStore, androidMain)
// writes plain files with java.io; InMemoryCrashFileStore is the
// test/dev default and the fallback when no crash dir is configured.
// ─────────────────────────────────────────────────────────────
internal interface CrashFileStore {

    /** Persists [report] synchronously. Must never throw — a crash-time failure is swallowed. */
    fun writeSync(report: CrashReport)

    /** Returns every persisted-but-not-yet-uploaded report (skipping any corrupt files). */
    fun readPending(): List<CrashReport>

    /** Removes the persisted report with [crashId] after it has been delivered. */
    fun clear(crashId: String)
}
