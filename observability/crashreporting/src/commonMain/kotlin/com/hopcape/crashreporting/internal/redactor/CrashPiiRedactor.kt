package com.hopcape.crashreporting.internal.redactor

import com.hopcape.crashreporting.internal.model.CrashReport

// ─────────────────────────────────────────────────────────────
// CrashPiiRedactor — separate abstraction (ISP: not part of the
// CrashDestination contract) so redaction can be tested and swapped
// independently. Mirrors the logging module's PiiRedactor.
//
// A crash reporter is a prime PII leak: an exception message can
// contain an email, a phone number, a token. Everything user-visible
// in a report is scrubbed before it reaches a vendor backend.
// ─────────────────────────────────────────────────────────────
internal interface CrashPiiRedactor {
    fun redact(report: CrashReport): CrashReport
}
