package com.hopcape.odo.feature.billscanner.presentation.scan

import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.domain.scan.entitlement.ScanAllowance
import com.hopcape.odo.core.domain.scan.entitlement.ScanLimit
import com.hopcape.odo.core.navigation.ScanTarget
import com.hopcape.odo.feature.billscanner.domain.usecase.ScanPumpDisplayUseCase
import com.hopcape.odo.core.platform.permission.CameraPermissionStatus
import com.hopcape.odo.feature.billscanner.presentation.BillScannerTelemetry
import com.hopcape.odo.feature.billscanner.presentation.RecordingAnalytics
import com.hopcape.odo.feature.billscanner.presentation.UnusedFileStore
import com.hopcape.odo.feature.billscanner.presentation.testTelemetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * What the camera permission decides, which on this screen is everything.
 *
 * The scanner is a viewfinder. Without the camera there is nothing for it to show, so the
 * permission does not change what the screen looks like — it decides whether there is a screen
 * at all. These pin the two halves of that: the rationale stands in front until the camera is
 * allowed, and both ways of refusing leave.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BillScanViewModelTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        analytics: RecordingAnalytics = RecordingAnalytics(),
        target: ScanTarget = ScanTarget.Bill,
    ) = BillScanViewModel(
        initialTarget = target,
        allowance = ScanAllowance { ScanLimit.UpTo(max = 3, used = 1) },
        cropper = { _, _ -> error("no test here captures a photo") },
        files = UnusedFileStore,
        scanPumpDisplay = ScanPumpDisplayUseCase(
            extractor = { error("no test here reads a pump") },
            ids = IdGenerator { "scan-1" },
            clock = object : Clock {
                override fun now(): Instant = Instant.fromEpochMilliseconds(0)
            },
        ),
        ids = IdGenerator { "scan-1" },
        telemetry = testTelemetry(analytics),
    )

    @Test
    fun notNow_showsTheGalleryState_andStaysOpen() = runTest {
        val analytics = RecordingAnalytics()
        val vm = viewModel(analytics)
        vm.onEvent(BillScanEvent.PermissionChanged(CameraPermissionStatus.Askable))

        vm.onEvent(BillScanEvent.PermissionDeclined)

        assertTrue(vm.state.value.showGalleryOnly)
        assertFalse(vm.state.value.showRationale)
        assertEquals(1, analytics.count(BillScannerTelemetry.Event.CAMERA_DECLINED))
    }

    @Test
    fun aDeniedPrompt_showsTheGalleryState() = runTest {
        val analytics = RecordingAnalytics()
        val vm = viewModel(analytics)
        vm.onEvent(BillScanEvent.PermissionChanged(CameraPermissionStatus.Askable))

        vm.onEvent(BillScanEvent.CameraRequested)
        vm.onEvent(BillScanEvent.Resumed)

        assertTrue(vm.state.value.showGalleryOnly)
        assertEquals(1, analytics.count(BillScannerTelemetry.Event.CAMERA_DECLINED))
    }

    @Test
    fun aGrantedPrompt_opensTheViewfinder() = runTest {
        val vm = viewModel()
        vm.onEvent(BillScanEvent.CameraRequested)
        vm.onEvent(BillScanEvent.PermissionChanged(CameraPermissionStatus.Granted))
        vm.onEvent(BillScanEvent.Resumed)

        assertFalse(vm.state.value.showGalleryOnly)
        assertTrue(vm.state.value.cameraGranted)
    }

    @Test
    fun aResumeWithoutAPrompt_changesNothing() = runTest {
        val vm = viewModel()
        vm.onEvent(BillScanEvent.PermissionChanged(CameraPermissionStatus.Askable))

        vm.onEvent(BillScanEvent.Resumed)

        assertTrue(vm.state.value.showRationale)
    }

    @Test
    fun onlyCloseLeaves() = runTest {
        val vm = viewModel()
        vm.onEvent(BillScanEvent.PermissionDeclined)

        vm.onEvent(BillScanEvent.CloseTapped)

        assertEquals(BillScanEffect.NavigateBack, vm.effects.first())
    }

    @Test
    fun theGalleryIsAttributedToWhereItWasOpened() = runTest {
        val analytics = RecordingAnalytics()
        val vm = viewModel(analytics)
        vm.onEvent(BillScanEvent.PermissionChanged(CameraPermissionStatus.Askable))

        vm.onEvent(BillScanEvent.GalleryTapped)
        vm.onEvent(BillScanEvent.PermissionDeclined)
        vm.onEvent(BillScanEvent.GalleryTapped)
        vm.onEvent(BillScanEvent.PermissionChanged(CameraPermissionStatus.Granted))
        vm.onEvent(BillScanEvent.GalleryTapped)

        assertEquals(
            listOf("rationale", "denied", "viewfinder"),
            analytics.properties(BillScannerTelemetry.Event.GALLERY_OPENED)
                .map { it[BillScannerTelemetry.Key.SOURCE] },
        )
        assertEquals(BillScanEffect.PickFromGallery, vm.effects.first())
    }

    @Test
    fun theRationaleIsReportedOncePerLook_withBlocked() = runTest {
        val analytics = RecordingAnalytics()
        val vm = viewModel(analytics)

        vm.onEvent(BillScanEvent.PermissionChanged(CameraPermissionStatus.Askable))
        vm.onEvent(BillScanEvent.PermissionChanged(CameraPermissionStatus.Askable))
        vm.onEvent(BillScanEvent.PermissionChanged(CameraPermissionStatus.Blocked))

        assertEquals(
            listOf(false, true),
            analytics.properties(BillScannerTelemetry.Event.CAMERA_RATIONALE_SHOWN)
                .map { it[BillScannerTelemetry.Key.BLOCKED] },
        )
    }

    @Test
    fun aGrantedCamera_neverReportsTheRationale() = runTest {
        val analytics = RecordingAnalytics()
        viewModel(analytics).onEvent(BillScanEvent.PermissionChanged(CameraPermissionStatus.Granted))

        assertEquals(0, analytics.count(BillScannerTelemetry.Event.CAMERA_RATIONALE_SHOWN))
    }

    @Test
    fun theRationaleStandsInFrontOfEveryUngrantedState() = runTest {
        val vm = viewModel()

        // Including Blocked, where the rationale is the screen that offers settings — the only
        // thing left that can help once the system has stopped prompting.
        listOf(
            CameraPermissionStatus.Askable,
            CameraPermissionStatus.Blocked,
        ).forEach { status ->
            vm.onEvent(BillScanEvent.PermissionChanged(status))
            assertTrue(vm.state.value.showRationale, "$status should still be behind the rationale")
        }

        vm.onEvent(BillScanEvent.PermissionChanged(CameraPermissionStatus.Granted))

        assertFalse(vm.state.value.showRationale)
    }

    @Test
    fun decliningDoesNotStickAcrossAFreshScan() = runTest {
        // A second visit asks again rather than remembering a refusal.
        viewModel().onEvent(BillScanEvent.PermissionDeclined)

        assertTrue(viewModel().state.value.showRationale)
    }
}
