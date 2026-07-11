package com.hopcape.odo.feature.billscanner.presentation.scan

/**
 * Display state for the "Scan bill" camera screen.
 *
 * For now it only carries the free-scan quota shown in the top-bar pill (mirrored
 * read-only from the server entitlement). The live camera frame and the AI
 * edge-detection status will join here when CameraX capture + the `ai-bill-scan`
 * pipeline land (M2) — the screen is built so those are additive.
 */
internal data class BillScanUiState(
    val freeRemaining: Int = 3,
    val freeTotal: Int = 3,
)

/** Sample state for previews and the pre-ViewModel route host. */
internal fun sampleBillScanState(): BillScanUiState =
    BillScanUiState(freeRemaining = 2, freeTotal = 3)
