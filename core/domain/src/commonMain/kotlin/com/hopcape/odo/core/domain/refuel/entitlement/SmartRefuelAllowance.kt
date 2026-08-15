package com.hopcape.odo.core.domain.refuel.entitlement

import com.hopcape.odo.core.domain.entitlement.Quota
import kotlinx.coroutines.flow.Flow

/**
 * Port answering how many automatically detected fills the owner's plan still permits.
 *
 * The free plan gets a fixed number of them and then stops; Pro is uncapped. The count is a
 * **lifetime** one, not a monthly one, and that is the whole point of the number: an owner
 * refuels a handful of times a month, so a monthly allowance of ten would never be reached
 * and the cap would exist without ever doing anything. Ten in total is a taste of the feature
 * that runs out.
 *
 * A port here rather than a use case inside `:feature:refuel` because two features need the
 * same answer — the dashboard decides whether to badge the card, and the detection worker
 * decides whether to keep reading notifications — and a feature may not import another
 * feature. It is the same shape as
 * [ScanAllowance][com.hopcape.odo.core.domain.scan.entitlement.ScanAllowance] for the same
 * reason.
 *
 * A [Flow] rather than a suspend read: the answer changes when a fill is written and when a
 * subscription starts or lapses, and both surfaces have to follow it without being told.
 */
fun interface SmartRefuelAllowance {

    /** The limit in force for the current owner, re-emitted whenever it moves. */
    fun observe(): Flow<SmartRefuelLimit>
}

/**
 * How many detected fills a plan permits, and how many have been used.
 *
 * [quota] is what the plan says and [used] is what the fill history says. They are kept apart
 * for the reason [Quota] documents: the plan cannot know the count, and the count cannot know
 * the plan.
 */
data class SmartRefuelLimit(
    val used: Int,
    val quota: Quota,
) {

    /** Whether one more fill may be detected. */
    val allowsAnother: Boolean get() = quota.allowsAnother(used)

    /** How many are left, or `null` on an uncapped plan. */
    val remaining: Int? get() = quota.remaining(used)

    /** The cap as a number for "3 of 10 used", or `null` when there isn't one. */
    val cap: Int? get() = quota.cap

    companion object {

        /**
         * What to assume when the allowance cannot be read.
         *
         * Denied. Refusing to sell is recoverable; giving a paid feature away by accident is
         * not, and this one costs the owner's notification access to run.
         */
        val Denied = SmartRefuelLimit(used = 0, quota = Quota.None)
    }
}
