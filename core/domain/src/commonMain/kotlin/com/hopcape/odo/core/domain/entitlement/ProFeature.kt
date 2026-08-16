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

    /** Bill and document scans. Counted per calendar month. */
    BILL_SCANS,

    /** The factor breakdown behind the health score. On/off. */
    HEALTH_BREAKDOWN,

    /** Exporting the car's record as a PDF. On/off. */
    RECORD_EXPORT,

    /**
     * Automatic fuel logging — Odo reading a pump payment and drafting the fill. On/off.
     *
     * Only the *automatic* channel. Logging a fill by hand, from a photo of the pump display,
     * or from the owner's own history stays free and stays on the dashboard, because a fill
     * that goes unlogged costs the owner the running-cost figure they came for. What Pro sells
     * is not being asked at all.
     */
    SMART_REFUEL_DETECT,
}
