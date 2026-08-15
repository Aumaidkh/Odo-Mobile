package com.hopcape.odo.feature.billscanner.presentation.scan

import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.domain.scan.entitlement.ScanAllowance
import com.hopcape.odo.core.domain.scan.entitlement.ScanLimit
import com.hopcape.odo.core.navigation.ScanTarget
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
        qrDecoder = { error("no test here decodes a code") },
        ids = IdGenerator { "scan-1" },
        telemetry = testTelemetry(analytics),
    )

    @Test
    fun notNow_leavesTheFlow() = runTest {
        val vm = viewModel()

        vm.onEvent(BillScanEvent.PermissionDeclined)

        // The whole point of #114: declining used to drop through to the viewfinder, which had
        // no preview to show and nudged for the permission that had just been refused.
        assertEquals(BillScanEffect.NavigateBack, vm.effects.first())
    }

    @Test
    fun notNowAndClose_bothLeave() = runTest {
        val declining = viewModel()
        declining.onEvent(BillScanEvent.PermissionDeclined)

        val closing = viewModel()
        closing.onEvent(BillScanEvent.CloseTapped)

        // Two dismiss controls sit side by side on the rationale. One intent, so one outcome.
        assertEquals(declining.effects.first(), closing.effects.first())
    }

    @Test
    fun decliningIsReportedApartFromClosing() = runTest {
        val declined = RecordingAnalytics()
        viewModel(declined).onEvent(BillScanEvent.PermissionDeclined)

        val closed = RecordingAnalytics()
        viewModel(closed).onEvent(BillScanEvent.CloseTapped)

        // Both navigate to the same place, but "refused the camera" and "changed my mind about
        // scanning" are different drop-offs, and the funnel is what this feature is measured on.
        assertEquals(1, declined.count(BillScannerTelemetry.Event.CAMERA_DECLINED))
        assertEquals(0, closed.count(BillScannerTelemetry.Event.CAMERA_DECLINED))
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
        // A second visit asks again rather than remembering a refusal. Nothing carries the
        // decline forward now that it ends the screen, and this is what says so.
        viewModel().onEvent(BillScanEvent.PermissionDeclined)

        assertTrue(viewModel().state.value.showRationale)
    }
}
