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
