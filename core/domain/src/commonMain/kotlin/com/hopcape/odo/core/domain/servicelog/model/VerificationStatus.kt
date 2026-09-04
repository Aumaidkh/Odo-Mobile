package com.hopcape.odo.core.domain.servicelog.model

/**
 * Whether a service entry is backed by a bill photo (the Odo trust model).
 *
 * PRD guardrail: only entries with a bill photo earn a **Verified** badge; everything
 * else is **Self-Reported**. Derived, not stored — see [ServiceLogEntry.verification].
 */
enum class VerificationStatus { VERIFIED, SELF_REPORTED }

/**
 * An entry with a bill attached — a manually-uploaded photo ([ServiceLogEntry.billPhotoRef])
 * or a linked bill record ([ServiceLogEntry.billId]) — is [VerificationStatus.VERIFIED];
 * everything else is [VerificationStatus.SELF_REPORTED]. Keeping this rule in the domain
 * means every surface (list badge, timeline dot, resale report) reads verification the
 * same way.
 */
val ServiceLogEntry.verification: VerificationStatus
    get() = if (billPhotoRef != null || billId != null) VerificationStatus.VERIFIED else VerificationStatus.SELF_REPORTED

/**
 * Whether this entry belongs in a "how much of the record is proven" ratio.
 *
 * A [LogSource.DECLARED] entry is the owner remembering that a service happened. It can
 * never carry a bill and never carry a fairness verdict, so counting it in the denominator
 * of either ratio lowers the result without any way to raise it back — answering an
 * optional setup question would cost the owner points they could not recover.
 *
 * It still counts everywhere the question is *when* the car was last serviced: that is the
 * reason the row exists.
 */
val ServiceLogEntry.isProvable: Boolean get() = source != LogSource.DECLARED

/** The entries a proven-share ratio may be computed over. See [isProvable]. */
fun List<ServiceLogEntry>.provable(): List<ServiceLogEntry> = filter { it.isProvable }
