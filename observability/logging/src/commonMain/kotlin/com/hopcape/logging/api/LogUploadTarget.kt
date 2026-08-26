package com.hopcape.logging.api

/**
 * The port an outside module implements to ship a sealed log file somewhere — the analog of
 * `CrashSink`/`AnalyticsSink`. `:infrastructure:supabase`'s `SupabaseLogUploader` is the real
 * one; there is no built-in target, unlike Logcat for logging itself, because "where do logs
 * go" is entirely a backend decision this module has no opinion on.
 */
@StableLoggerApi
interface LogUploadTarget {
    val name: String

    /**
     * Uploads one sealed file. [file] carries its name, size, open/seal times and level
     * counts (§6.4) — enough for the target to build a server-side index row without
     * inspecting [bytes] itself (see `log_uploads`, plan §7.3).
     *
     * [reference] is the diagnostics code the owner was shown, when this file is part of a
     * request they made ([DiagnosticRequests]). It is stored beside the file so support can
     * find it from the code in the email. Null for the background pass, which nobody asked
     * for and nobody is waiting on.
     */
    suspend fun upload(file: LogFileHandle, bytes: ByteArray, reference: String? = null): LogUploadResult
}

/**
 * What happened, and what the caller should do about it:
 * - [DELIVERED] — succeeded; delete the local file.
 * - [RETRY] — a transient failure (network, 5xx); leave the file, try again later.
 * - [REJECTED] — a permanent failure (4xx, corrupt payload); delete the file anyway, so one
 *   poisoned upload can't wedge the queue forever (same reasoning as analytics' dead-letter).
 */
@StableLoggerApi
enum class LogUploadResult { DELIVERED, RETRY, REJECTED }
