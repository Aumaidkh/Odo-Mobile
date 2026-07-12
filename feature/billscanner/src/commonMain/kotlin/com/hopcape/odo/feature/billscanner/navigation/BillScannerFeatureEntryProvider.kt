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
import com.hopcape.odo.feature.billscanner.presentation.error.ScanErrorScreen
import com.hopcape.odo.feature.billscanner.presentation.fairness.FairnessScreen
import com.hopcape.odo.feature.billscanner.presentation.fairness.sampleFairnessOver
import com.hopcape.odo.core.domain.shared.formatDate
import com.hopcape.odo.feature.billscanner.presentation.result.ReportSuccessScreen
import com.hopcape.odo.feature.billscanner.presentation.result.SaveSuccessScreen
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
        entry<OdoDestination.BillScanner.Fairness> { FairnessRoute(navigationManager) }
        entry<OdoDestination.BillScanner.SaveSuccess> { SaveSuccessRoute(navigationManager) }
        entry<OdoDestination.BillScanner.ReportSuccess> { ReportSuccessRoute(navigationManager) }
        entry<OdoDestination.BillScanner.ScanError> { ScanErrorRoute(navigationManager) }
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
        onSave = { navigationManager.navigateTo(OdoDestination.BillScanner.Fairness) },
        onRetake = { navigationManager.back() },
        onBack = { navigationManager.back() },
    )
}

/**
 * The fairness route host — shows how the reviewed bill compares to the city average.
 * Defaults to the overcharge sample (the continuation of the review flow). Saving
 * (persist + log) and reporting are M2 stubs; "Go Pro" opens the paywall later.
 */
@Composable
internal fun FairnessRoute(navigationManager: NavigationManager) {
    FairnessScreen(
        state = sampleFairnessOver(),
        onSave = { navigationManager.navigateTo(OdoDestination.BillScanner.SaveSuccess) },
        onReport = {
            navigationManager.navigateTo(
                OdoDestination.ServiceLog.ReportOvercharge(logId = DEMO_LOG_ID, carId = DEMO_CAR_ID),
            )
        },
        onGoPro = { /* TODO: open the paywall. */ },
        onBack = { navigationManager.back() },
    )
}

/**
 * Terminal save-success host. "View in Service Log" / "Done" both exit the whole scan
 * flow and land back on the service log with a clean back stack.
 *
 * TODO(M2): thread the real car + saved-entry ids so we return to that car's log (the
 * demo uses the placeholder car the rest of the flow runs on).
 */
@Composable
internal fun SaveSuccessRoute(navigationManager: NavigationManager) {
    val sample = remember { sampleBillReviewState() }
    SaveSuccessScreen(
        workshop = sample.workshop,
        dateLabel = formatDate(sample.serviceDate),
        onViewLog = { navigationManager.backToServiceLog() },
        onDone = { navigationManager.backToServiceLog() },
    )
}

/** Terminal report-success host — "Back to Service Log" resets to the log. */
@Composable
internal fun ReportSuccessRoute(navigationManager: NavigationManager) {
    ReportSuccessScreen(
        city = sampleFairnessOver().city,
        reportCount = 37,
        onBackToLog = { navigationManager.backToServiceLog() },
    )
}

/**
 * Scan-error host — the AI couldn't read the bill. Retake returns to the camera;
 * "Enter manually" opens the manual form; back pops the flow.
 */
@Composable
internal fun ScanErrorRoute(navigationManager: NavigationManager) {
    ScanErrorScreen(
        onRetake = { navigationManager.back() },
        onEnterManually = { navigationManager.navigateTo(OdoDestination.ServiceLog.AddEdit(DEMO_CAR_ID)) },
        onBack = { navigationManager.back() },
    )
}

/** The placeholder car + saved-entry the demo scan flow runs on until real ids thread through. */
private const val DEMO_CAR_ID = "aaa"
private const val DEMO_LOG_ID = "aaa"

/** Exit the scan flow and reset to the car's service log with a clean back stack. */
private fun NavigationManager.backToServiceLog() {
    val log = OdoDestination.ServiceLog.List(DEMO_CAR_ID)
    navigateTo(log, popUpTo = log, inclusive = true)
}