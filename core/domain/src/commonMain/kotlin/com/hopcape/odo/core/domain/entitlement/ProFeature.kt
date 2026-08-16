package com.hopcape.odo.core.domain.entitlement

/**
 * Something a plan can grant. One entry per gate in the app.
 *
 * This is the whole list of things Odo Pro is. A feature that is not here is free, and a
 * feature that is here is answered in exactly one place — [PlanLimits] — so a new gate is an
 * entry here plus a row there, and no new port.
 *
 * Some entries are counted and some are on/off. The difference is not stated here: it is the
 * [Quota] the plan gives them. [DOCUMENTS] is `UpTo(3)` on the free plan and `Unlimited` on
 * Pro; [HEALTH_BREAKDOWN] is `None` on the free plan and `Unlimited` on Pro. Callers ask the
 * same question either way.
 */
enum class ProFeature {

    /** Documents kept in the vault. Counted — the free plan holds a few. */
    DOCUMENTS,

    /** Bill and document scans. Counted for the lifetime of the install, not per month. */
    BILL_SCANS,

    /** The factor breakdown behind the health score. On/off. */
    HEALTH_BREAKDOWN,

    /** Exporting the car's record as a PDF. On/off. */
    RECORD_EXPORT,
}
