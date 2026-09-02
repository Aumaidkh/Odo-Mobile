package com.hopcape.odo.feature.challan.domain.usecase

import com.hopcape.odo.core.domain.car.model.RegistrationNumber
import com.hopcape.odo.core.domain.challan.model.Challan
import com.hopcape.odo.core.domain.challan.model.ChallanStatus
import com.hopcape.odo.core.domain.challan.repository.ChallanRepository
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.sum
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The challans screen's one fact: everything the cache knows about a plate, already
 * split the way the screen argues — court cases apart from the payable ones, the payable
 * total that excludes them, and the cleared-this-year line the clean state shows.
 *
 * The split lives here, not in the ViewModel, because it *is* the domain rule
 * ([Challan.isPayableOnline]): a court case in the payable total would promise a payment
 * that does not exist.
 */
internal class ObserveChallanOverviewUseCase(
    private val challans: ChallanRepository,
    private val clock: Clock = Clock.System,
) {

    operator fun invoke(regNo: RegistrationNumber): Flow<ChallanOverview> = combine(
        challans.observe(regNo),
        challans.observeLastChecked(regNo),
    ) { all, lastChecked ->
        val year = clock.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.year
        val payable = all.filter { it.isPayableOnline }
        val cleared = all.filter { it.status == ChallanStatus.PAID && it.issuedOn.year == year }
        ChallanOverview(
            regNo = regNo,
            courtCases = all.filter { it.status == ChallanStatus.IN_COURT },
            payable = payable,
            payableTotal = payable.map { it.amount }.sum(),
            clearedThisYearCount = cleared.size,
            clearedThisYearTotal = cleared.map { it.amount }.sum(),
            lastCheckedAt = lastChecked,
        )
    }
}

/** What [ObserveChallanOverviewUseCase] answers — the list screen's whole content. */
data class ChallanOverview(
    val regNo: RegistrationNumber,
    /** `IN_COURT` — pinned above the payable ones, never in a total or a pay CTA. */
    val courtCases: List<Challan>,
    /** `PENDING`, newest first — what "total pending" and the pay CTA mean. */
    val payable: List<Challan>,
    val payableTotal: Amount,
    val clearedThisYearCount: Int,
    val clearedThisYearTotal: Amount,
    /** `null` until the records have been asked at least once. */
    val lastCheckedAt: Instant?,
) {
    val isClean: Boolean get() = courtCases.isEmpty() && payable.isEmpty()
}
