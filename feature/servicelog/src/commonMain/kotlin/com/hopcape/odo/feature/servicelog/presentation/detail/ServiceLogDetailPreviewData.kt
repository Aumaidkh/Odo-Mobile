package com.hopcape.odo.feature.servicelog.presentation.detail

import androidx.compose.runtime.Composable
import arrow.core.getOrElse
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.domain.fairness.model.FairnessConfidence
import com.hopcape.odo.core.domain.fairness.model.FairnessOutcome
import com.hopcape.odo.core.domain.fairness.model.FairnessVerdict
import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.servicelog.model.VerificationStatus
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.Distance
import com.hopcape.odo.feature.servicelog.presentation.state.WorkDone
import kotlinx.datetime.LocalDate

private fun rupees(paise: Long): Amount = Amount.of(paise).getOrElse { Amount.ZERO }
private fun km(value: Int): Distance = Distance.of(value).getOrElse { error("bad sample km=$value") }

/** The mockup entry — AutoCare Pune, verified but over the city average. */
private val sampleDetailEntry = ServiceEntryDetailUiState(
    id = ServiceLogId("2"),
    workshopName = "AutoCare Pune",
    serviceDate = LocalDate(2026, 3, 2),
    odometer = km(48_500),
    workDone = WorkDone.Described(listOf("Front brake pads")),
    verification = VerificationStatus.VERIFIED,
    totalPaid = rupees(480_000),
    lineItems = listOf(
        ServiceLineItemUiState("Front brake pads", ServiceCategory.BRAKES, rupees(330_000)),
        ServiceLineItemUiState("Labour", ServiceCategory.BRAKES, rupees(120_000)),
    ),
    fairness = EntryFairnessUiState.Assessed(
        overall = FairnessOutcome.Over(rupees(110_000)),
        city = "Pune",
        cityAverageTotal = rupees(370_000),
        sampleSize = 240,
        confidence = FairnessConfidence.HIGH,
        breakdown = listOf(
            FairnessBreakdownRow(
                label = "Front brake pads",
                category = ServiceCategory.BRAKES,
                paid = rupees(330_000),
                cityAverage = rupees(240_000),
                verdict = FairnessVerdict.Over(rupees(90_000)),
            ),
            FairnessBreakdownRow(
                label = "Labour",
                category = ServiceCategory.BRAKES,
                paid = rupees(120_000),
                cityAverage = rupees(100_000),
                verdict = FairnessVerdict.Over(rupees(20_000)),
            ),
        ),
    ),
    resale = ResaleProofUiState.Verified(scoreUplift = 4, fairPriceChecked = true),
    bill = BillAttachmentUiState(scanned = true, verified = true),
)

/** Sample state (stands in for the ViewModel until the route is wired to it). */
internal fun sampleDetailState(): ServiceLogDetailUiState =
    ServiceLogDetailUiState(content = ServiceLogDetailUiState.Content.Loaded(sampleDetailEntry))

@OdoThemePreviews
@Composable
private fun ServiceLogDetailPreview() = OdoPreview(padded = false) {
    ServiceLogDetailScreen(state = sampleDetailState(), onEvent = {})
}
