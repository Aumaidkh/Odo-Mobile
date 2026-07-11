package com.hopcape.odo.feature.servicelog.presentation.report

import androidx.compose.runtime.Composable
import arrow.core.getOrElse
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.domain.shared.Amount
import kotlinx.datetime.LocalDate

/** Sample state (mirrors the mockup) — stands in for the ViewModel until it lands. */
internal fun sampleReportState(): ReportOverchargeUiState = ReportOverchargeUiState(
    content = ReportOverchargeUiState.Content.Loaded(
        ReportHeaderUiState(
            workshopName = "AutoCare Pune",
            amountOver = Amount.of(110_000).getOrElse { Amount.ZERO },
            workDone = "Front brake pads",
            serviceDate = LocalDate(2026, 3, 2),
        ),
    ),
    reason = OverchargeReason.ABOVE_MARKET_RATE,
)

@OdoThemePreviews
@Composable
private fun ReportOverchargePreview() = OdoPreview(padded = false) {
    ReportOverchargeScreen(
        state = sampleReportState(),
        onReasonSelect = {},
        onNoteChange = {},
        onSubmit = {},
        onDone = {},
        onBack = {},
    )
}
