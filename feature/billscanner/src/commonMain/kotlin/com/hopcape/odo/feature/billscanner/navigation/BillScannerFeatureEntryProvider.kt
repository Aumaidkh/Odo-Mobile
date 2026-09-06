package com.hopcape.odo.feature.billscanner.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.hopcape.odo.core.domain.car.ActiveCarProvider
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.navigation.CollectEffects
import com.hopcape.odo.core.navigation.DocumentOrigin
import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.core.navigation.NavigationManager
import com.hopcape.odo.core.navigation.OdoDestination
import com.hopcape.odo.core.navigation.back
import com.hopcape.odo.core.navigation.finishFlow
import com.hopcape.odo.core.navigation.isAddDocumentFlowStep
import com.hopcape.odo.core.navigation.navigateTo
import com.hopcape.odo.core.platform.camera.CameraEvent
import com.hopcape.odo.core.platform.camera.rememberOdoCameraState
import com.hopcape.odo.core.platform.file.FileTypes
import com.hopcape.odo.core.platform.file.rememberFilePicker
import com.hopcape.odo.core.platform.permission.CameraPermissionStatus
import com.hopcape.odo.core.platform.permission.rememberCameraPermissionController
import com.hopcape.odo.feature.billscanner.domain.usecase.CaptureOrigin
import com.hopcape.odo.feature.billscanner.presentation.document.DocumentReviewEffect
import com.hopcape.odo.feature.billscanner.presentation.document.DocumentReviewEvent
import com.hopcape.odo.feature.billscanner.presentation.document.DocumentReviewScreen
import com.hopcape.odo.feature.billscanner.presentation.document.DocumentReviewViewModel
import com.hopcape.odo.feature.billscanner.presentation.error.ScanErrorScreen
import com.hopcape.odo.feature.billscanner.presentation.permission.CameraRationaleScreen
import com.hopcape.odo.feature.billscanner.presentation.result.ReportSuccessScreen
import com.hopcape.odo.feature.billscanner.presentation.result.SaveSuccessScreen
import com.hopcape.odo.feature.billscanner.presentation.review.BillReviewEffect
import com.hopcape.odo.feature.billscanner.presentation.review.BillReviewEvent
import com.hopcape.odo.feature.billscanner.presentation.review.BillReviewScreen
import com.hopcape.odo.feature.billscanner.presentation.review.BillReviewViewModel
import com.hopcape.odo.feature.billscanner.presentation.scan.BillScanEffect
import com.hopcape.odo.feature.billscanner.presentation.scan.BillScanEvent
import com.hopcape.odo.feature.billscanner.presentation.scan.BillScanScreen
import com.hopcape.odo.feature.billscanner.presentation.scan.BillScanViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * BillScanner's contribution to the navigation graph: the [OdoDestination.BillScanner] flow.
 *
 * [OdoDestination.BillScanner.Capture] is the one camera surface for every target; where a
 * capture goes next is the only thing the target changes — a bill to
 * [OdoDestination.BillScanner.Review], a paper to [OdoDestination.BillScanner.DocumentReview],
 * a pump display to refuel's own confirm step. Every other feature reaches this flow through
 * the shared [OdoDestination] registry, so none of them imports this module.
 */
internal class BillScannerFeatureEntryProvider(
    private val navigationManager: NavigationManager,
) : FeatureEntryProvider {
    override fun EntryProviderScope<NavKey>.registerEntries() {
        entry<OdoDestination.BillScanner.Capture> { key -> BillScanRoute(navigationManager, key) }
        entry<OdoDestination.BillScanner.Review> { key ->
            BillReviewRoute(navigationManager, photoKey = key.photoKey)
        }
        entry<OdoDestination.BillScanner.DocumentReview> { key ->
            DocumentReviewRoute(
                navigationManager,
                photoKey = key.photoKey,
                documentType = key.documentType,
                origin = key.origin.toCaptureOrigin(),
            )
        }
        entry<OdoDestination.BillScanner.SaveSuccess> { SaveSuccessRoute(navigationManager) }
        entry<OdoDestination.BillScanner.ReportSuccess> { ReportSuccessRoute(navigationManager) }
        entry<OdoDestination.BillScanner.ScanError> { ScanErrorRoute(navigationManager) }
    }
}

/**
 * The capture route host — the camera permission gate, the camera, and where a photo goes.
 *
 * The rationale is shown **before** the system prompt, not after a refusal: Android gives one
 * second chance and iOS gives none, so the single prompt an owner sees should follow an
 * explanation. "Not now" and the X both leave the flow. Without the camera there is no
 * viewfinder to show, so dropping through to the scan screen would only ask for the permission
 * a second time on a screen that cannot work without it.
 */
@Composable
internal fun BillScanRoute(
    navigationManager: NavigationManager,
    key: OdoDestination.BillScanner.Capture,
) {
    val viewModel: BillScanViewModel = koinViewModel { parametersOf(key.target) }
    val state by viewModel.state.collectAsState()
    val carId by koinInject<ActiveCarProvider>().activeCarId.collectAsState()
    val permission = rememberCameraPermissionController()
    val cameraState = rememberOdoCameraState()
    // Pictures only: a PDF has no frame to read a code or an expiry date off.
    val pickFromGallery = rememberFilePicker(mimeTypes = FileTypes.PHOTOS) { pickedRef ->
        viewModel.onEvent(BillScanEvent.GalleryPicked(pickedRef))
    }

    // The controller is Compose state of its own; this is what folds it into the ViewModel's.
    LaunchedEffect(permission.status) {
        viewModel.onEvent(BillScanEvent.PermissionChanged(permission.status))
    }

    val grant = {
        if (permission.status == CameraPermissionStatus.Blocked) {
            permission.openAppSettings()
        } else {
            permission.request()
        }
    }

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is BillScanEffect.OpenBillReview ->
                navigationManager.navigateTo(OdoDestination.BillScanner.Review(effect.photoKey))
            is BillScanEffect.OpenDocumentReview -> navigationManager.navigateTo(
                OdoDestination.BillScanner.DocumentReview(effect.photoKey, key.documentType),
            )
            // The scanner's only handoff to another feature's flow. It navigates rather than
            // replacing itself: backing out of the confirm step should land on the viewfinder,
            // because the owner is still standing at the pump and the display is still lit.
            is BillScanEffect.OpenPumpConfirm ->
                navigationManager.navigateTo(OdoDestination.Refuel.Confirm(effect.draft))
            BillScanEffect.PickFromGallery -> pickFromGallery()
            // "Manual" opens the log form in the viewfinder's place, not on top of it. The
            // form's save pops back, and that should land on whatever opened the scanner
            // rather than on a live camera. When the form itself opened the scanner, popping
            // the capture leaves that form on top and `singleTop` reuses it — the same place
            // this used to go. With no active car there is no form to open, so nothing moves:
            // opening someone else's car is worse than opening none.
            BillScanEffect.OpenManualEntry -> carId?.let {
                navigationManager.navigateTo(
                    OdoDestination.ServiceLog.AddEdit(it.value),
                    popUpTo = key,
                    inclusive = true,
                )
            }
            is BillScanEffect.OpenPaywall ->
                navigationManager.navigateTo(
                    OdoDestination.Paywall.Plans(trigger = PAYWALL_TRIGGER_SCANS, freeScans = effect.freeScans),
                )

            BillScanEffect.NavigateBack -> navigationManager.back()
        }
    }

    if (state.showRationale) {
        CameraRationaleScreen(
            blocked = state.cameraBlocked,
            onAllow = grant,
            onNotNow = { viewModel.onEvent(BillScanEvent.PermissionDeclined) },
            onClose = { viewModel.onEvent(BillScanEvent.CloseTapped) },
        )
        return
    }

    BillScanScreen(
        state = state,
        cameraState = cameraState,
        onCameraEvent = { event ->
            when (event) {
                is CameraEvent.PhotoCaptured -> viewModel.onEvent(BillScanEvent.PhotoCaptured(event.storageKey))
                is CameraEvent.EdgesDetected -> viewModel.onEvent(BillScanEvent.EdgesDetected(event.quad))
                is CameraEvent.Failed -> viewModel.onEvent(BillScanEvent.CameraFailed(event.failure))
                CameraEvent.Ready -> viewModel.onEvent(BillScanEvent.CameraReady)
            }
        },
        onClose = { viewModel.onEvent(BillScanEvent.CloseTapped) },
        onQuotaTapped = { viewModel.onEvent(BillScanEvent.QuotaTapped) },
        onCapture = cameraState::capture,
        onPickGallery = { viewModel.onEvent(BillScanEvent.GalleryTapped) },
        onManual = { viewModel.onEvent(BillScanEvent.ManualTapped) },
        onTargetSelected = { viewModel.onEvent(BillScanEvent.TargetSelected(it)) },
        onToggleEdgeLock = { viewModel.onEvent(BillScanEvent.EdgeLockToggled) },
    )
}

/**
 * The review route host — reads the captured bill, then saves what the owner confirmed and
 * hands the lines to the fairness check.
 */
@Composable
internal fun BillReviewRoute(navigationManager: NavigationManager, photoKey: String?) {
    val viewModel: BillReviewViewModel = koinViewModel { parametersOf(photoKey) }
    val state by viewModel.state.collectAsState()
    val carId by koinInject<ActiveCarProvider>().activeCarId.collectAsState()

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is BillReviewEffect.OpenFairness -> navigationManager.navigateTo(
                OdoDestination.Fairness(
                    items = effect.items,
                    logId = effect.logId,
                    carId = effect.carId,
                ),
            )
            BillReviewEffect.Retake -> navigationManager.back()
            // Same as the scanner's "Manual": the form takes this screen's place, so nothing
            // sends the owner back to a photo that could not be read.
            BillReviewEffect.OpenManualEntry -> carId?.let {
                navigationManager.navigateTo(
                    OdoDestination.ServiceLog.AddEdit(it.value),
                    popUpTo = OdoDestination.BillScanner.Review(photoKey),
                    inclusive = true,
                )
            }
            BillReviewEffect.NavigateBack -> navigationManager.back()
        }
    }

    BillReviewScreen(
        state = state,
        onWorkshopChange = { viewModel.onEvent(BillReviewEvent.WorkshopChanged(it)) },
        onDateChange = { viewModel.onEvent(BillReviewEvent.DateChanged(it)) },
        onOdometerChange = { viewModel.onEvent(BillReviewEvent.OdometerChanged(it)) },
        onSave = { viewModel.onEvent(BillReviewEvent.SaveTapped) },
        onRetake = { viewModel.onEvent(BillReviewEvent.RetakeTapped) },
        onBack = { viewModel.onEvent(BillReviewEvent.BackTapped) },
        // Straight to the viewer rather than through the ViewModel: enlarging the photo the
        // screen is already showing is a way of looking at it, not a decision the review flow
        // makes. Nothing about the extraction changes, and there is nothing to count.
        onOpenPhoto = { state.photoKey?.let { navigationManager.navigateTo(OdoDestination.FilePreview(it)) } },
    )
}

/**
 * The type a navigation key names, or null when it names none or one this build cannot read.
 *
 * The name travels as a string because a key is serialized into the back stack. An
 * unrecognised name falls back to what the read itself suggests, rather than failing.
 */
private fun String?.toDocumentType(): DocumentType? =
    this?.let { name -> DocumentType.entries.firstOrNull { it.name == name } }

/**
 * The navigation key's origin as the feature's own. Two enums rather than one shared type,
 * so the confirm step's use cases stay free of navigation types.
 */
private fun DocumentOrigin.toCaptureOrigin(): CaptureOrigin = when (this) {
    DocumentOrigin.Scanned -> CaptureOrigin.Scanned
    DocumentOrigin.Uploaded -> CaptureOrigin.Uploaded
}

/** The scanned-document confirm host — files the paper in the vault, then opens it there. */
@Composable
internal fun DocumentReviewRoute(
    navigationManager: NavigationManager,
    photoKey: String,
    documentType: String? = null,
    origin: CaptureOrigin = CaptureOrigin.Scanned,
) {
    val viewModel: DocumentReviewViewModel = koinViewModel {
        parametersOf(photoKey, documentType.toDocumentType(), origin)
    }
    val state by viewModel.state.collectAsState()

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            // The paper is filed, so every step that led here is finished: this screen, the
            // viewfinder behind it, and the vault's add screen behind that. They come off the
            // stack as the success screen goes on, which is what makes back from the success
            // screen leave the flow instead of stepping back into it.
            is DocumentReviewEffect.OpenDocument -> navigationManager.finishFlow(
                OdoDestination.Documents.AddSuccess(effect.documentId),
                ::isAddDocumentFlowStep,
            )
            DocumentReviewEffect.NavigateBack -> navigationManager.back()
        }
    }

    DocumentReviewScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onOpenPhoto = { navigationManager.navigateTo(OdoDestination.FilePreview(photoKey)) },
    )
}


/**
 * Terminal save-success host. "View in Service Log" / "Done" both exit the whole scan flow and
 * land back on the service log with a clean back stack.
 */
@Composable
internal fun SaveSuccessRoute(navigationManager: NavigationManager) {
    // Nothing routes here today: a saved scan goes straight to the fairness report, which is
    // the more useful confirmation. The key stays registered because the screen is what the
    // "save without a fairness check" path will land on.
    val carId by koinInject<ActiveCarProvider>().activeCarId.collectAsState()

    SaveSuccessScreen(
        workshop = "",
        dateLabel = "",
        onViewLog = { navigationManager.backToServiceLog(carId) },
        onDone = { navigationManager.backToServiceLog(carId) },
    )
}

/** Terminal report-success host — "Back to Service Log" resets to the log. */
@Composable
internal fun ReportSuccessRoute(navigationManager: NavigationManager) {
    val carId by koinInject<ActiveCarProvider>().activeCarId.collectAsState()

    ReportSuccessScreen(
        city = REVIEW_CITY,
        reportCount = 37,
        onBackToLog = { navigationManager.backToServiceLog(carId) },
    )
}

/**
 * Scan-error host — the AI couldn't read the bill. Retake returns to the camera;
 * "Enter manually" opens the manual form; back pops the flow.
 */
@Composable
internal fun ScanErrorRoute(navigationManager: NavigationManager) {
    val carId by koinInject<ActiveCarProvider>().activeCarId.collectAsState()

    ScanErrorScreen(
        onRetake = { navigationManager.back() },
        onEnterManually = {
            carId?.let {
                navigationManager.navigateTo(
                    OdoDestination.ServiceLog.AddEdit(it.value),
                    popUpTo = OdoDestination.BillScanner.ScanError,
                    inclusive = true,
                )
            }
        },
        onBack = { navigationManager.back() },
    )
}

/** Sample city on the report-success screen until the owner's city threads through. */
private const val REVIEW_CITY = "Pune"

/**
 * Exit the scan flow and reset to the car's service log with a clean back stack. With no
 * active car there is no log to reset to, so the flow is simply popped.
 */
private fun NavigationManager.backToServiceLog(carId: CarId?) {
    if (carId == null) {
        back()
        return
    }
    val log = OdoDestination.ServiceLog.List(carId.value)
    navigateTo(log, popUpTo = log, inclusive = true)
}

/** Where the paywall was opened from. A shipped analytics value — do not reword it. */
private const val PAYWALL_TRIGGER_SCANS = "SCANS_EXHAUSTED"
