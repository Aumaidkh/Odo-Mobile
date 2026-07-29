package com.hopcape.odo.feature.timeline.presentation

import androidx.compose.runtime.Composable
import arrow.core.getOrElse
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.domain.servicelog.model.RecordScore
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.servicelog.model.VerificationStatus
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.Distance
import com.hopcape.odo.core.domain.shared.WorkshopName
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.feature.timeline.domain.model.TimelineEvent
import kotlinx.datetime.LocalDate

private fun rupees(paise: Long): Amount = Amount.of(paise).getOrElse { Amount.ZERO }
private fun km(value: Int): Distance = Distance.of(value).getOrElse { error("bad sample km=$value") }
private fun workshop(name: String): WorkshopName? = WorkshopName.of(name).getOrElse { null }

/**
 * Canned feed mirroring the mockup, for `@Preview`s and — until the aggregation use
 * case lands — the running route. Newest first; the screen groups it by month.
 */
internal fun sampleTimeline(): TimelineUiState = TimelineUiState(
    carName = "Swift VXI",
    isLoading = false,
    events = listOf(
        TimelineEvent.Service(
            id = ServiceLogId("l1"),
            workDone = "Oil change + filter",
            workshop = workshop("Sharma Motors"),
            odometer = km(54_000),
            amount = rupees(320_000),
            verification = VerificationStatus.VERIFIED,
            overchargedBy = null,
            date = LocalDate(2026, 7, 12),
        ),
        TimelineEvent.Service(
            id = ServiceLogId("l2"),
            workDone = "Front brake pads",
            workshop = workshop("AutoCare Pune"),
            odometer = km(48_500),
            amount = rupees(480_000),
            verification = VerificationStatus.VERIFIED,
            overchargedBy = rupees(70_000),
            date = LocalDate(2026, 7, 8),
        ),
        TimelineEvent.HealthScoreChanged(
            from = RecordScore.of(70),
            to = RecordScore.of(74),
            date = LocalDate(2026, 7, 8),
        ),
        TimelineEvent.Service(
            id = ServiceLogId("l3"),
            workDone = "Wheel alignment",
            workshop = workshop("Speed Garage"),
            odometer = km(52_100),
            amount = rupees(90_000),
            verification = VerificationStatus.SELF_REPORTED,
            overchargedBy = null,
            date = LocalDate(2026, 6, 21),
        ),
        TimelineEvent.Service(
            id = ServiceLogId("l4"),
            workDone = "AC re-gas",
            workshop = workshop("Sharma Motors"),
            odometer = km(51_400),
            amount = rupees(240_000),
            verification = VerificationStatus.VERIFIED,
            overchargedBy = null,
            date = LocalDate(2026, 6, 4),
        ),
        TimelineEvent.DocumentRenewed(
            document = DocumentType.PUC,
            validTill = LocalDate(2026, 11, 30),
            date = LocalDate(2026, 6, 2),
        ),
        TimelineEvent.DocumentRenewed(
            document = DocumentType.INSURANCE,
            validTill = LocalDate(2027, 6, 1),
            date = LocalDate(2026, 6, 1),
        ),
    ),
)

/** Sample new-user feed — a single milestone + the empty call-to-action. */
internal fun sampleEmptyTimeline(): TimelineUiState = TimelineUiState(
    carName = "Swift VXI",
    isLoading = false,
    events = listOf(
        TimelineEvent.CarAdded(carName = "Swift VXI", date = LocalDate(2026, 7, 28)),
    ),
)

@OdoThemePreviews
@Composable
private fun TimelinePreview() = OdoPreview(padded = false) {
    TimelineScreen(
        state = sampleTimeline(),
        onFilter = {},
        onShare = {},
        onOpenService = {},
        onAddBill = {},
        onScanFirst = {},
    )
}

@OdoThemePreviews
@Composable
private fun TimelineNewUserPreview() = OdoPreview(padded = false) {
    TimelineScreen(
        state = sampleEmptyTimeline(),
        onFilter = {},
        onShare = {},
        onOpenService = {},
        onAddBill = {},
        onScanFirst = {},
    )
}

/** The default state a ViewModel emits first — no car, no events, still loading. */
@OdoThemePreviews
@Composable
private fun TimelineLoadingPreview() = OdoPreview(padded = false) {
    TimelineScreen(
        state = TimelineUiState(),
        onFilter = {},
        onShare = {},
        onOpenService = {},
        onAddBill = {},
        onScanFirst = {},
    )
}
