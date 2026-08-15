package com.hopcape.odo.core.data.entitlement

import com.hopcape.odo.core.domain.car.ActiveCarProvider
import com.hopcape.odo.core.domain.cost.model.FillEntrySource
import com.hopcape.odo.core.domain.cost.repository.FuelFillRepository
import com.hopcape.odo.core.domain.entitlement.EntitlementSource
import com.hopcape.odo.core.domain.entitlement.ProFeature
import com.hopcape.odo.core.domain.entitlement.Quota
import com.hopcape.odo.core.domain.refuel.entitlement.SmartRefuelAllowance
import com.hopcape.odo.core.domain.refuel.entitlement.SmartRefuelLimit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * The free plan's allowance of automatically detected fills, read from the owner's plan and
 * their own fill history.
 *
 * Two sources, because they are two different facts. The cap comes from `PlanLimits`; the
 * tally comes from the fills already written, counted by [FillEntrySource.DETECTED]. Nothing
 * new is stored to track this — a detected fill is already a row that says how it arrived, so
 * counting them is reading what is there rather than keeping a second number that could
 * disagree with it.
 *
 * Counted for the active car. Odo is single-car, so that is every fill the owner has; if a
 * second car ever arrives, this is the line that has to decide whether the allowance is per
 * car or per owner.
 *
 * Unlike the scanner's device-local tally, this one survives a reinstall: the fills come back
 * with the account, and so does the count. That is the right way round for a cap on a feature
 * that reads the owner's notifications.
 */
internal class EntitlementSmartRefuelAllowance(
    private val entitlements: EntitlementSource,
    private val activeCar: ActiveCarProvider,
    private val fills: FuelFillRepository,
) : SmartRefuelAllowance {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observe(): Flow<SmartRefuelLimit> =
        combine(
            entitlements.observe().map { it.quotaFor(ProFeature.SMART_REFUEL_DETECT) },
            detectedFillCount(),
        ) { quota, used -> SmartRefuelLimit(used = used, quota = quota) }
            // A history that cannot be read is not a licence to keep detecting. Denied is the
            // safe direction, and the flow stays alive so a later read can recover it.
            .catch { emit(SmartRefuelLimit.Denied) }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun detectedFillCount(): Flow<Int> =
        activeCar.activeCarId.flatMapLatest { id ->
            // No car means no fills, which is a count of zero rather than a failure — the
            // allowance is whole, there has just been nothing to spend it on.
            if (id == null) {
                flowOf(0)
            } else {
                fills.observeForCar(id).map { list ->
                    list.count { it.entrySource == FillEntrySource.DETECTED }
                }
            }
        }
}
