package com.hopcape.odo.feature.dashboard.domain.model

import com.hopcape.odo.core.domain.activity.model.ActivityEvent
import com.hopcape.odo.core.domain.alerts.model.CarAttention
import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.cost.model.CostTrend
import com.hopcape.odo.core.domain.cost.model.RunningCost
import com.hopcape.odo.core.domain.fairness.model.FairnessSavings
import com.hopcape.odo.core.domain.health.model.HealthScore
import com.hopcape.odo.core.domain.insight.model.CarInsight
import com.hopcape.odo.core.domain.shared.Distance

/**
 * Everything Home shows, computed together from one read of the car's whole record.
 *
 * One snapshot rather than a stream per card, because every figure on the screen is a view
 * of the same car at the same moment. A score read separately from the cost would let the
 * dial and the ₹/km land on different frames, and a card that says "PUC expired" above a
 * score that already counted the renewal is how a dashboard loses the owner's trust.
 *
 * Nothing here is a display string — the cards carry numbers and typed values, and the
 * screen writes its own copy from them.
 */
internal data class HomeSnapshot(
    /** The owner's first name for the greeting; `null` before they have set one. */
    val ownerName: String?,
    /** `null` while the car is still loading, or if setup never stored one. */
    val car: Car?,
    /** The newest odometer reading on record, logs included. `null` only when [car] is. */
    val odometer: Distance?,
    val score: HealthScore,
    /**
     * Points gained or lost against the score from a month ago. `null` when there is no
     * snapshot that old — the card hides the line rather than comparing against last
     * week's number and calling it a month.
     */
    val scoreDelta: Int?,
    val cost: RunningCost,
    /** How ₹/km moved against the window before; `null` when either window has no rate. */
    val costTrend: CostTrend?,
    val savings: FairnessSavings,
    /** The single thing that needs acting on, or `null` when nothing does. */
    val attention: CarAttention?,
    /**
     * Whether the car is inside its service window, on time or distance.
     *
     * Carried beside [attention] rather than read off it: the picker returns one item, and a
     * lapsed PUC outranks a service that is a week away — which would hide the checklist
     * exactly when the owner is about to book one.
     */
    val serviceDue: Boolean,
    /** Something worth knowing that is not a deadline, or `null` when there is nothing. */
    val insight: CarInsight?,
    /** The newest thing that happened to the car; `null` on a record with no events. */
    val recent: ActivityEvent?,
    /** How far the car has run since its last fill, and what that fill was. */
    val tank: TankStatus,
    val setup: SetupProgress,
) {
    /**
     * Nothing has been logged or filed yet, so there is no score worth showing.
     *
     * The gate is one service log **or** one document, which is exactly when the score
     * stops being zero for want of evidence. Below it Home shows the setup checklist
     * instead: a dial reading 4 out of 100 is not a verdict on the car, it is a verdict on
     * an empty app.
     */
    val isNewUser: Boolean get() = !setup.hasServiceLogs && !setup.hasDocuments
}

/**
 * How far the owner has got through setting Odo up — the new-user checklist.
 *
 * Three steps, in the order the checklist lists them. Each is a fact about the record, not
 * a stored flag, so a step cannot stay ticked after the thing it counted was deleted.
 */
internal data class SetupProgress(
    /** Setup has stored a car. */
    val carAdded: Boolean,
    /** At least one entry has a bill behind it, which is what unlocks fairness checks. */
    val billScanned: Boolean,
    /** At least one paper is in the vault. */
    val documentsFiled: Boolean,
    /** Any service log at all, bill or not — half of the "is there a score yet" gate. */
    val hasServiceLogs: Boolean,
) {
    /** The other half of that gate. Same fact as [documentsFiled], named for its other use. */
    val hasDocuments: Boolean get() = documentsFiled

    val stepCount: Int get() = 3

    val doneCount: Int
        get() = listOf(carAdded, billScanned, documentsFiled).count { it }

    val isComplete: Boolean get() = doneCount == stepCount
}
