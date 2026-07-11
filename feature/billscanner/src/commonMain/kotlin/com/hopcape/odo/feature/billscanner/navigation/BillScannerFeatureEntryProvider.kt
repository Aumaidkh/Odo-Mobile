package com.hopcape.odo.feature.billscanner.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.core.navigation.NavigationManager
import com.hopcape.odo.core.navigation.OdoDestination
import com.hopcape.odo.core.designsystem.component.OdoDistanceUnit
import com.hopcape.odo.core.navigation.back
import com.hopcape.odo.core.navigation.navigateTo
import com.hopcape.odo.feature.billscanner.presentation.review.BillReviewScreen
import com.hopcape.odo.feature.billscanner.presentation.review.sampleBillReviewState
import com.hopcape.odo.feature.billscanner.presentation.scan.BillScanScreen
import com.hopcape.odo.feature.billscanner.presentation.scan.sampleBillScanState

/**
 * BillScanner's contribution to the navigation graph: the [OdoDestination.BillScanner]
 * flow — [OdoDestination.BillScanner.Capture] (camera viewfinder) routing into
 * [OdoDestination.BillScanner.Review] (confirm the extracted fields). The service-log
 * form + list deep-link to Capture through the shared [OdoDestination] registry — no
 * feature imports another feature.
 *
 * Collected by the `:app` host via `getAll<FeatureEntryProvider>()`, so wiring the
 * feature in is just listing [com.hopcape.odo.feature.billscanner.billScannerModule].
 */
internal class BillScannerFeatureEntryProvider(
    private val navigationManager: NavigationManager,
) : FeatureEntryProvider {
    override fun EntryProviderScope<NavKey>.registerEntries() {
        entry<OdoDestination.BillScanner.Capture> { BillScanRoute(navigationManager) }
        entry<OdoDestination.BillScanner.Review> { BillReviewRoute(navigationManager) }
    }
}

/**
 * The capture route host — bridges the (future) scan ViewModel to navigation. For now
 * a successful capture or gallery pick jumps straight to [OdoDestination.BillScanner.Review]
 * with sample extracted data; "Manual" pops back to the manual-entry form.
 */
@Composable
internal fun BillScanRoute(navigationManager: NavigationManager) {
    BillScanScreen(
        state = sampleBillScanState(),
        onClose = { navigationManager.back() },
        onCapture = { navigationManager.navigateTo(OdoDestination.BillScanner.Review) },
        onPickGallery = { navigationManager.navigateTo(OdoDestination.BillScanner.Review) },
        onManual = { navigationManager.back() },
    )
}

/**
 * The review route host — renders the AI-extracted fields for confirmation. Holds the
 * editable workshop name locally until the scan ViewModel lands; saving (which will
 * persist the entry + run the fairness check) and retaking are M2 stubs.
 */
@Composable
internal fun BillReviewRoute(navigationManager: NavigationManager) {
    val extracted = remember { sampleBillReviewState() }
    var workshop by remember { mutableStateOf(extracted.workshop) }
    var date by remember { mutableStateOf(extracted.serviceDate) }
    var odometer by remember { mutableStateOf(extracted.odometer) }
    var odometerUnit by remember { mutableStateOf(extracted.odometerUnit) }
    BillReviewScreen(
        state = extracted.copy(
            workshop = workshop,
            serviceDate = date,
            odometer = odometer,
            odometerUnit = odometerUnit,
        ),
        onWorkshopChange = { workshop = it },
        onDateChange = { date = it },
        onOdometerChange = { odometer = it },
        onOdometerUnitToggle = {
            odometerUnit = if (odometerUnit == OdoDistanceUnit.KM) OdoDistanceUnit.MILES else OdoDistanceUnit.KM
        },
        onSave = { /* TODO(M2): persist the entry -> run CheckFairness -> open fairness. */ navigationManager.back() },
        onRetake = { navigationManager.back() },
        onBack = { navigationManager.back() },
    )
}