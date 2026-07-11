package com.hopcape.odo.feature.billscanner.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.core.navigation.NavigationManager
import com.hopcape.odo.core.navigation.OdoDestination
import com.hopcape.odo.core.navigation.back
import com.hopcape.odo.feature.billscanner.presentation.scan.BillScanScreen
import com.hopcape.odo.feature.billscanner.presentation.scan.sampleBillScanState

/**
 * BillScanner's contribution to the navigation graph: registers the
 * [OdoDestination.BillScanner] capture screen. The service-log form (and the list
 * empty state) deep-link here through the shared [OdoDestination] registry — no
 * feature imports another feature.
 *
 * Collected by the `:app` host via `getAll<FeatureEntryProvider>()`, so wiring the
 * feature in is just listing [com.hopcape.odo.feature.billscanner.billScannerModule].
 */
internal class BillScannerFeatureEntryProvider(
    private val navigationManager: NavigationManager,
) : FeatureEntryProvider {
    override fun EntryProviderScope<NavKey>.registerEntries() {
        entry<OdoDestination.BillScanner> { BillScanRoute(navigationManager) }
    }
}

/**
 * The scan route host — bridges the (future) scan ViewModel to navigation. For now
 * it renders sample state; capture and gallery are M2 stubs, and "Manual" pops back
 * to the manual-entry form the scan was launched from.
 */
@Composable
internal fun BillScanRoute(navigationManager: NavigationManager) {
    BillScanScreen(
        state = sampleBillScanState(),
        onClose = { navigationManager.back() },
        onCapture = { /* TODO(M2): CameraX capture -> ai-bill-scan -> confirm the extracted entry. */ },
        onPickGallery = { /* TODO(M2): pick an image from the gallery -> ai-bill-scan. */ },
        onManual = { navigationManager.back() },
    )
}
