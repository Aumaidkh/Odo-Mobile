package com.hopcape.odo.feature.servicelog.presentation.share

import androidx.compose.runtime.Composable
import arrow.core.getOrElse
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.domain.shared.Amount
import kotlinx.datetime.LocalDate

/** Sample sheet state, in whichever [export] state is being looked at. */
internal fun sampleShareRecordState(
    export: ExportUiState = ExportUiState.Idle,
): ShareRecordUiState = ShareRecordUiState(
    content = ShareRecordUiState.Content.Loaded(carName = "Swift VXI", verifiedCount = 4, serviceCount = 6),
    export = export,
)

/** The same sheet opened on one entry — the subtitle names the bill instead of the counts. */
internal fun sampleShareBillState(): ShareRecordUiState = ShareRecordUiState(
    content = ShareRecordUiState.Content.LoadedBill(
        carName = "Swift VXI",
        serviceDate = LocalDate(2026, 7, 12),
        amount = Amount.of(320_000L).getOrElse { Amount.ZERO },
    ),
)

@OdoThemePreviews
@Composable
private fun ShareRecordSheetPreview() = OdoPreview {
    ShareRecordSheetContent(state = sampleShareRecordState(), onEvent = {})
}

/** Mid-render: the tapped target spins and every other one goes flat. */
@OdoThemePreviews
@Composable
private fun ShareRecordSheetRenderingPreview() = OdoPreview {
    ShareRecordSheetContent(
        state = sampleShareRecordState(ExportUiState.Rendering(ShareTarget.WHATSAPP)),
        onEvent = {},
    )
}

/** The document could not be produced. */
@OdoThemePreviews
@Composable
private fun ShareRecordSheetFailedPreview() = OdoPreview {
    ShareRecordSheetContent(state = sampleShareRecordState(ExportUiState.Failed), onEvent = {})
}

/** Opened on one entry: the bill share. */
@OdoThemePreviews
@Composable
private fun ShareBillSheetPreview() = OdoPreview {
    ShareRecordSheetContent(state = sampleShareBillState(), onEvent = {})
}
