package com.hopcape.odo.core.domain.insight.model

import com.hopcape.odo.core.domain.document.model.DocumentType

/**
 * Something worth telling the owner about their car that is not a deadline.
 *
 * Deadlines are [CarAttention][com.hopcape.odo.core.domain.alerts.model.CarAttention]'s
 * job. This is the other half of the dashboard: a pattern in the record that the owner
 * would not spot themselves — a record good enough to sell on, a cost that has moved, a
 * gap that is costing them.
 *
 * Shared kernel, next to
 * [InsightPicker][com.hopcape.odo.core.domain.insight.analysis.InsightPicker] which chooses
 * one. Home renders it today; the Resale Passport and the reminder engine can read the same
 * rules rather than inventing a second set.
 *
 * Rule-based and deterministic, not AI-written. The PRD's AI Doctor is a separate, paid,
 * Phase 2 feature, and a dashboard line that costs tokens every time the tab opens is not
 * one the MVP can afford. No copy here — each surface writes its own line from the numbers.
 */
sealed interface CarInsight {

    /**
     * Every logged service has a bill behind it, over a record long enough to mean it.
     *
     * The product's resale pitch in one line: a buyer checks bills, and this is the car
     * that has them.
     */
    data class ResaleReady(val serviceCount: Int) : CarInsight

    /**
     * Running cost has moved against the window before by enough to be worth saying.
     * Positive means the car got costlier.
     */
    data class CostMoved(val percentChange: Int) : CarInsight

    /**
     * The car has a service history, but not one bill is attached to it.
     *
     * The gap that costs the owner twice: no fairness check can run without a bill, and
     * every entry stays "self-reported" at resale.
     */
    data class NoBillsAttached(val serviceCount: Int) : CarInsight

    /** A paper the health score counts is not in the vault at all. */
    data class DocumentMissing(val type: DocumentType) : CarInsight
}
