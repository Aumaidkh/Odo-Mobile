package com.hopcape.logging.api

/**
 * Durable storage for log files — the port an outside module implements to give the file
 * sink somewhere real to write (`:core:platform`'s `AndroidLogFileStore`, `java.io`-backed).
 * The analog of `CrashSink`/`AnalyticsSink`: public and deliberately small, so this module's
 * sink chain never depends on a concrete platform.
 *
 * A store tracks at most one **active** file at a time, opened lazily on the first
 * [appendToActive] after start or after the previous active file was sealed. Naming for
 * both halves comes from [LogFileNaming], so every implementation agrees on it without
 * sharing code.
 *
 * Calls are expected to be serialized by the caller — in this module, `AsyncSink`'s single
 * writer coroutine. A store does not need its own locking.
 */
@StableLoggerApi
interface LogFileStore {

    /** Appends [lines] to the active file, opening a new one first if none is open. */
    fun appendToActive(lines: List<String>)

    /** The active file's name, or `null` if none is open — nothing has logged yet in this
     *  session, or the writer is between rotations right after a seal. Unlike [read], this
     *  file is plain text, never gzip'd: only a *sealed* file is compressed. */
    fun activeFileName(): String?

    /**
     * Seals the active file: flush, rename to its sealed name (`LogFileNaming.sealedFileName`),
     * persist [stats] as its `.meta` sidecar, and forget it as "active". Returns `null` when
     * nothing was ever written, so there is no file to seal.
     */
    fun sealActive(stats: LogFileStats): LogFileHandle?

    /**
     * Recovers any `.active` file left behind by a process that died without sealing it —
     * called once, at startup, before the first [appendToActive] of the new session. Their
     * [LogFileHandle.stats] is `null`: the live counters that would have produced it never ran.
     */
    fun sealOrphans(): List<LogFileHandle>

    /** Every sealed file currently on disk, including ones recovered by [sealOrphans]. */
    fun listSealed(): List<LogFileHandle>

    /** The sealed file's bytes (gzip-compressed), or `null` if [name] is not a sealed file here. */
    fun read(name: String): ByteArray?

    /** Removes a sealed file and its `.meta` sidecar together. A no-op if [name] is unknown. */
    fun delete(name: String)

    /** Sum of [LogFileHandle.sizeBytes] across sealed files. Never counts the active file — the
     *  retention policy this feeds never touches it either. */
    fun totalBytes(): Long
}
