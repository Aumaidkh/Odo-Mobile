package com.hopcape.odo.feature.timeline.presentation

import androidx.compose.runtime.Composable
import arrow.core.getOrElse
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.activity.model.ActivityEvent
import com.hopcape.odo.core.domain.document.model.DocumentId
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.servicelog.model.RecordScore
import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.servicelog.model.VerificationStatus
import com.hopcape.odo.core.domain.servicelog.model.WorkDone
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.Distance
import com.hopcape.odo.core.domain.shared.WorkshopName
import com.hopcape.odo.feature.timeline.presentation.state.Loadable
import com.hopcape.odo.feature.timeline.resources.Res
import com.hopcape.odo.feature.timeline.resources.tl_error_load_failed
import kotlinx.datetime.LocalDate

private fun rupees(paise: Long): Amount = Amount.of(paise).getOrElse { Amount.ZERO }
private fun km(value: Int): Distance = Distance.of(value).getOrElse { error("bad sample km=$value") }
private fun workshop(name: String): WorkshopName? = WorkshopName.of(name).getOrElse { null }

/**
 * A canned feed mirroring the mockup, for `@Preview`s. Built out of the same
 * [ActivityEvent]s the builder produces, so a preview cannot show a row the real feed could
 * never assemble.
 */
internal fun sampleTimeline(): TimelineContent = TimelineContent(
    carName = "Swift VXI",
    totalEvents = 7,
    events = listOf(
        ActivityEvent.Service(
            id = ServiceLogId("l1"),
            workDone = WorkDone.Described(listOf("Oil change", "filter")),
            workshop = workshop("Sharma Motors"),
            odometer = km(54_000),
            amount = rupees(320_000),
            verification = VerificationStatus.VERIFIED,
            overchargedBy = null,
            date = LocalDate(2026, 7, 12),
        ),
        ActivityEvent.Service(
            id = ServiceLogId("l2"),
            workDone = WorkDone.Described(listOf("Front brake pads")),
            workshop = workshop("AutoCare Pune"),
            odometer = km(48_500),
            amount = rupees(480_000),
            verification = VerificationStatus.VERIFIED,
            overchargedBy = rupees(70_000),
            date = LocalDate(2026, 7, 8),
        ),
        ActivityEvent.ScoreChanged(
            from = RecordScore.of(70),
            to = RecordScore.of(74),
            date = LocalDate(2026, 7, 8),
        ),
        ActivityEvent.Service(
            id = ServiceLogId("l3"),
            workDone = WorkDone.Tagged(listOf(ServiceCategory.TYRES)),
            workshop = workshop("Speed Garage"),
            odometer = km(52_100),
            amount = rupees(90_000),
            verification = VerificationStatus.SELF_REPORTED,
            overchargedBy = null,
            date = LocalDate(2026, 6, 21),
        ),
        ActivityEvent.DocumentFiled(
            id = DocumentId("d1"),
            document = DocumentType.PUC,
            isRenewal = true,
            validTill = LocalDate(2026, 11, 30),
            date = LocalDate(2026, 6, 2),
        ),
        ActivityEvent.DocumentFiled(
            id = DocumentId("d2"),
            document = DocumentType.INSURANCE,
            isRenewal = false,
            validTill = LocalDate(2027, 6, 1),
            date = LocalDate(2026, 6, 1),
        ),
        ActivityEvent.CarAdded(carName = "Swift VXI", date = LocalDate(2026, 1, 5)),
    ),
)

/** A new user's feed — a single milestone and the call to action. */
internal fun sampleEmptyTimeline(): TimelineContent = TimelineContent(
    carName = "Swift VXI",
    totalEvents = 1,
    events = listOf(ActivityEvent.CarAdded(carName = "Swift VXI", date = LocalDate(2026, 7, 28))),
)

@OdoThemePreviews
@Composable
private fun TimelinePreview() = OdoPreview(padded = false) {
    TimelineScreen(state = TimelineUiState(content = Loadable.Ready(sampleTimeline())), onEvent = {})
}

@OdoThemePreviews
@Composable
private fun TimelineNewUserPreview() = OdoPreview(padded = false) {
    TimelineScreen(state = TimelineUiState(content = Loadable.Ready(sampleEmptyTimeline())), onEvent = {})
}

/** Everything hidden by the filter — a different message from a car with no history. */
@OdoThemePreviews
@Composable
private fun TimelineFilteredEmptyPreview() = OdoPreview(padded = false) {
    TimelineScreen(
        state = TimelineUiState(
            content = Loadable.Ready(
                TimelineContent(carName = "Swift VXI", events = emptyList(), totalEvents = 7, isFiltered = true),
            ),
        ),
        onEvent = {},
    )
}

/** The default state a ViewModel emits first — no car, no events, still loading. */
@OdoThemePreviews
@Composable
private fun TimelineLoadingPreview() = OdoPreview(padded = false) {
    TimelineScreen(state = TimelineUiState(), onEvent = {})
}

@OdoThemePreviews
@Composable
private fun TimelineFailedPreview() = OdoPreview(padded = false) {
    TimelineScreen(
        state = TimelineUiState(content = Loadable.Failed(UiText(Res.string.tl_error_load_failed))),
        onEvent = {},
    )
}
