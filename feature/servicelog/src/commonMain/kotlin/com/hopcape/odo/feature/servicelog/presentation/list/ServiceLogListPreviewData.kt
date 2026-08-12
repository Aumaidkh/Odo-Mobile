package com.hopcape.odo.feature.servicelog.presentation.list

import androidx.compose.runtime.Composable
import arrow.core.getOrElse
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.domain.fairness.model.FairnessEstimate
import com.hopcape.odo.core.domain.fairness.model.FairnessSavings
import com.hopcape.odo.core.domain.servicelog.model.RecordScore
import com.hopcape.odo.core.domain.servicelog.model.RecordStrength
import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.servicelog.model.ServiceRecordSummary
import com.hopcape.odo.core.domain.servicelog.model.VerificationStatus
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.AmountRange
import com.hopcape.odo.core.domain.shared.Distance
import com.hopcape.odo.feature.servicelog.presentation.list.model.ServiceLogDirection
import com.hopcape.odo.feature.servicelog.presentation.state.WorkDone
import kotlinx.datetime.LocalDate

private fun rupees(paise: Long): Amount = Amount.of(paise).getOrElse { Amount.ZERO }
private fun km(value: Int): Distance = Distance.of(value).getOrElse { error("bad sample km=$value") }

/**
 * Canned list state mirroring the mockup, for `@Preview`s and — until the ViewModel is
 * wired into the route — the running screen. One data set drives both directions.
 */
private val sampleCards: List<ServiceLogCardUiState> = listOf(
    ServiceLogCardUiState(
        id = ServiceLogId("1"),
        workshopName = "Sharma Motors",
        serviceDate = LocalDate(2026, 6, 12),
        odometer = km(54_000),
        amount = rupees(320_000),
        verification = VerificationStatus.VERIFIED,
        workDone = WorkDone.Described(listOf("Oil change", "oil filter")),
        fairness = ServiceLogFairnessBadge.FairPrice,
    ),
    ServiceLogCardUiState(
        id = ServiceLogId("2"),
        workshopName = "AutoCare Pune",
        serviceDate = LocalDate(2026, 3, 2),
        odometer = km(48_500),
        amount = rupees(480_000),
        verification = VerificationStatus.VERIFIED,
        workDone = WorkDone.Described(listOf("Front brake pads")),
        fairness = ServiceLogFairnessBadge.Overcharged(by = rupees(110_000)),
    ),
    ServiceLogCardUiState(
        id = ServiceLogId("3"),
        workshopName = "Speed Garage",
        serviceDate = LocalDate(2025, 12, 18),
        odometer = km(43_200),
        amount = rupees(640_000),
        verification = VerificationStatus.SELF_REPORTED,
        workDone = WorkDone.Tagged(listOf(ServiceCategory.GENERAL_SERVICE)),
        fairness = ServiceLogFairnessBadge.AddBillToVerify,
    ),
    ServiceLogCardUiState(
        id = ServiceLogId("4"),
        workshopName = "Sharma Motors",
        serviceDate = LocalDate(2025, 8, 20),
        odometer = km(38_000),
        amount = rupees(240_000),
        verification = VerificationStatus.VERIFIED,
        workDone = WorkDone.Tagged(listOf(ServiceCategory.AC)),
        fairness = ServiceLogFairnessBadge.FairPrice,
    ),
)

private val sampleSummary = ServiceRecordSummary(
    serviceCount = 6,
    verifiedCount = 4,
    totalSpent = rupees(2_860_000),
    latestOdometer = km(54_000),
    verifiedRatio = 4f / 6f,
    strength = RecordStrength.STRONG,
    score = RecordScore.of(74),
    resaleUplift = AmountRange.ofPaise(2_000_000, 4_000_000),
)

private val sampleSavings = FairnessSavings(overchargeTotal = rupees(140_000), overchargesCaught = 1)

/** One sample of every fairness verdict — for the badge/verdict component previews. */
internal val sampleFairnessBadges: List<ServiceLogFairnessBadge> = listOf(
    ServiceLogFairnessBadge.FairPrice,
    ServiceLogFairnessBadge.Overcharged(by = rupees(110_000)),
    ServiceLogFairnessBadge.AddBillToVerify,
    ServiceLogFairnessBadge.NotEnoughData(
        FairnessEstimate(ServiceCategory.AC, city = "Pune", cityAverage = rupees(210_000), sampleSize = 3),
    ),
    ServiceLogFairnessBadge.NotYetChecked,
)

/** A populated list body with [filter] applied to the visible cards. */
internal fun sampleLoadedContent(
    filter: ServiceLogFilter = ServiceLogFilter.ALL,
): ServiceLogListUiState.Content.Loaded = ServiceLogListUiState.Content.Loaded(
    cards = when (filter) {
        ServiceLogFilter.ALL -> sampleCards
        ServiceLogFilter.VERIFIED -> sampleCards.filter { it.verification == VerificationStatus.VERIFIED }
        ServiceLogFilter.FLAGGED -> sampleCards.filter { it.fairness is ServiceLogFairnessBadge.Overcharged }
    },
    summary = sampleSummary,
    savings = sampleSavings,
    counts = ServiceLogFilterCounts(all = sampleCards.size, verified = 3, flagged = 1),
)

/** Sample state (stands in for the ViewModel until the route is wired to it). */
internal fun sampleServiceLogListState(
    filter: ServiceLogFilter = ServiceLogFilter.ALL,
    direction: ServiceLogDirection = ServiceLogDirection.LEDGER,
): ServiceLogListUiState = ServiceLogListUiState(
    content = sampleLoadedContent(filter),
    filter = filter,
    direction = direction,
)

@OdoThemePreviews
@Composable
private fun LedgerListPreview() = OdoPreview(padded = false) {
    ServiceLogListScreen(state = sampleServiceLogListState(), onEvent = {})
}

@OdoThemePreviews
@Composable
private fun TimelineListPreview() = OdoPreview(padded = false) {
    ServiceLogListScreen(
        state = sampleServiceLogListState(direction = ServiceLogDirection.TIMELINE),
        onEvent = {},
    )
}

@OdoThemePreviews
@Composable
private fun EmptyLedgerPreview() = OdoPreview(padded = false) {
    ServiceLogListScreen(
        state = ServiceLogListUiState(content = ServiceLogListUiState.Content.Empty),
        onEvent = {},
    )
}
