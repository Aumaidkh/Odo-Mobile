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

    /**
     * The running-cost analysis: the Costs tab's per-category breakdown, the spend chart
     * and the mileage trend. On/off.
     *
     * **Not the running-cost figure itself.** The ₹/km tile on Home stays free (#247): it
     * has been on the owner's home screen since install, and taking a number away that
     * someone has read every week is the one move in this re-shape that would read as a
     * bait-and-switch. What Pro sells is the arithmetic behind it, not the answer.
     */
    COST_ANALYSIS,

    /**
     * How the health score has moved over time — the trend against a month ago. On/off.
     *
     * The score itself is never gated, and neither is the timeline's record of what changed
     * it: the timeline is the car's history, not an analysis of it. This is the derived
     * comparison, which is an output in the plan's sense.
     */
    SCORE_HISTORY,
}
