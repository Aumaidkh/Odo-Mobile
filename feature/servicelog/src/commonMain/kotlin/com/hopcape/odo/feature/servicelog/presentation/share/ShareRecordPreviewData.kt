package com.hopcape.odo.feature.servicelog.presentation.share

import androidx.compose.runtime.Composable
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews

/** Sample sheet state, in whichever [export] state is being looked at. */
internal fun sampleShareRecordState(
    export: ExportUiState = ExportUiState.Idle,
): ShareRecordUiState = ShareRecordUiState(
    content = ShareRecordUiState.Content.Loaded(carName = "Swift VXI", verifiedCount = 4, serviceCount = 6),
    export = export,
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
