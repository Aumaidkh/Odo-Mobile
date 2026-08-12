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
import kotlinx.datetime.LocalDate

private fun rupees(paise: Long): Amount = Amount.of(paise).getOrElse { Amount.ZERO }
private fun km(value: Int): Distance = Distance.of(value).getOrElse { error("bad sample km=$value") }

/** The mockup entry — AutoCare Pune, verified but over the city average. */
private val sampleDetailEntry = ServiceEntryDetailUiState(
    id = ServiceLogId("2"),
    workshopName = "AutoCare Pune",
    serviceDate = LocalDate(2026, 3, 2),
    odometer = km(48_500),
    verification = VerificationStatus.VERIFIED,
    totalPaid = rupees(480_000),
    lineItems = listOf(
        ServiceLineItemUiState(
            label = "Front brake pads",
            category = ServiceCategory.BRAKES,
            amount = rupees(330_000),
            cityAverage = rupees(240_000),
            verdict = FairnessVerdict.Over(rupees(90_000)),
        ),
        // Unbenchmarked on purpose: the preview has to show what a line with no city
        // average looks like, since that is the one the old screen used to drop.
        ServiceLineItemUiState(
            label = "Labour",
            category = ServiceCategory.BRAKES,
            amount = rupees(120_000),
        ),
    ),
    fairness = EntryFairnessUiState.Assessed(
        overall = FairnessOutcome.Over(rupees(110_000)),
        city = "Pune",
        cityAverageTotal = rupees(370_000),
        sampleSize = 240,
        confidence = FairnessConfidence.HIGH,
    ),
    resale = ResaleProofUiState.Verified(scoreUplift = 4, fairPriceChecked = true),
    bill = BillAttachmentUiState(scanned = true, verified = true, photoRef = "bills/c1/l1.jpg"),
)

/**
 * A hand-added entry: a bill was attached later, so it is verified, but nobody ever typed
 * the lines. This is the case the screen used to render as a card holding nothing but the
 * total (issue #109).
 */
private val sampleUnitemisedEntry = sampleDetailEntry.copy(
    id = ServiceLogId("3"),
    workshopName = "Sai Motor Works",
    totalPaid = rupees(8_142_000),
    lineItems = emptyList(),
    fairness = EntryFairnessUiState.NotAssessed,
    resale = ResaleProofUiState.Verified(scoreUplift = 6, fairPriceChecked = false),
)

/** Sample state (stands in for the ViewModel until the route is wired to it). */
internal fun sampleDetailState(): ServiceLogDetailUiState =
    ServiceLogDetailUiState(content = ServiceLogDetailUiState.Content.Loaded(sampleDetailEntry))

@OdoThemePreviews
@Composable
private fun ServiceLogDetailPreview() = OdoPreview(padded = false) {
    ServiceLogDetailScreen(state = sampleDetailState(), onEvent = {})
}

@OdoThemePreviews
@Composable
private fun ServiceLogDetailNoItemsPreview() = OdoPreview(padded = false) {
    ServiceLogDetailScreen(
        state = ServiceLogDetailUiState(content = ServiceLogDetailUiState.Content.Loaded(sampleUnitemisedEntry)),
        onEvent = {},
    )
}
