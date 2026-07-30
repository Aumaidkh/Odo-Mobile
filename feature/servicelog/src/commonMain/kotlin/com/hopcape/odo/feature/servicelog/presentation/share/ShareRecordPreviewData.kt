package com.hopcape.odo.feature.servicelog.presentation.share

import androidx.compose.runtime.Composable
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews

/**
 * Sample sheet state — stands in for the ViewModel until the route is wired to it.
 * [link] defaults to absent, which is the real state until the Resale Passport ships.
 */
internal fun sampleShareRecordState(
    link: PassportLinkUiState = PassportLinkUiState.Unavailable,
): ShareRecordUiState = ShareRecordUiState(
    content = ShareRecordUiState.Content.Loaded(carName = "Swift VXI", verifiedCount = 4, serviceCount = 6),
    link = link,
)

@OdoThemePreviews
@Composable
private fun ShareRecordSheetPreview() = OdoPreview {
    ShareRecordSheetContent(state = sampleShareRecordState(), onEvent = {})
}

/** How the sheet reads once a passport link exists (Phase 2). */
@OdoThemePreviews
@Composable
private fun ShareRecordSheetWithLinkPreview() = OdoPreview {
    ShareRecordSheetContent(
        state = sampleShareRecordState(PassportLinkUiState.Ready("odo.app/p/swift-9F2K")),
        onEvent = {},
    )
}
