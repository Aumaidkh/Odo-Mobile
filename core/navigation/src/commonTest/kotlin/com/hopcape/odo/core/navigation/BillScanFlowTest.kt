package com.hopcape.odo.core.navigation

import androidx.navigation3.runtime.NavBackStack
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What comes off the stack when a scan ends.
 *
 * The success screen is terminal, so back from whatever follows it has to leave the errand.
 * It used to be pushed under the service log instead, and back walked the owner into a
 * success screen they had already dismissed.
 */
class BillScanFlowTest {

    private val carId = "c1"
    private val log = OdoDestination.ServiceLog.List(carId)

    private fun navigator(vararg start: OdoDestination): Navigator =
        OdoNavigator(NavBackStack(*start))

    /** Collects commands rather than delivering them, so a test can apply them in order. */
    private class RecordingNavigationManager : NavigationManager {
        val recorded = mutableListOf<NavigationCommand>()
        override val commands: SharedFlow<NavigationCommand> = MutableSharedFlow()
        override fun navigate(command: NavigationCommand) {
            recorded += command
        }
    }

    /** Runs [leaveBillScan] against [this] the way the host would. */
    private fun Navigator.leaveBillScanFor(destination: OdoDestination?) {
        val manager = RecordingNavigationManager()
        manager.leaveBillScan(destination)
        manager.recorded.forEach(::execute)
    }

    /* ------------------------------- what counts as a step ------------------------------- */

    @Test
    fun bothTerminalSuccessScreens_areSteps() {
        assertTrue(isBillScanFlowStep(OdoDestination.BillScanner.SaveSuccess))
        assertTrue(isBillScanFlowStep(OdoDestination.BillScanner.ReportSuccess))
    }

    @Test
    fun photographingADocument_isNotAStep() {
        // The same viewfinder files papers in the vault; that errand pops with its own set.
        assertFalse(
            isBillScanFlowStep(OdoDestination.BillScanner.Capture(target = ScanTarget.Document)),
        )
    }

    /* ---------------------------------- leaving the flow ---------------------------------- */

    @Test
    fun leavingForTheLog_whenTheLogWasNeverOnTheStack_dropsTheSuccessScreen() {
        // A scan started from Home, which is most of them. The log is not on the stack, so
        // popping "up to" it matches nothing and leaves the whole errand in place.
        val nav = navigator(OdoDestination.Home)
        nav.execute(NavigationCommand.NavigateTo(OdoDestination.BillScanner.Capture()))
        nav.execute(NavigationCommand.NavigateTo(OdoDestination.BillScanner.SaveSuccess))

        nav.leaveBillScanFor(log)

        // Back from the log has to leave the errand, not step into a success screen the owner
        // has already dismissed.
        assertEquals(listOf(OdoDestination.Home, log), nav.backStack.toList())
    }

    @Test
    fun leavingForTheLog_itWasStartedFrom_landsOnItWithNothingInBetween() {
        val nav = navigator(OdoDestination.Home, log)
        nav.execute(NavigationCommand.NavigateTo(OdoDestination.BillScanner.Capture()))
        nav.execute(NavigationCommand.NavigateTo(OdoDestination.BillScanner.SaveSuccess))

        nav.leaveBillScanFor(log)

        assertEquals(listOf(OdoDestination.Home, log), nav.backStack.toList())
    }

    @Test
    fun leavingForTheLog_openedFromItsOwnForm_bringsTheLogForward() {
        // The form sends the owner for a photo too, which leaves the log deeper in the stack.
        // Pushing it again would put one key on the stack twice, and Nav3 crashes on that.
        val nav = navigator(OdoDestination.Home, log)
        nav.execute(NavigationCommand.NavigateTo(OdoDestination.ServiceLog.AddEdit(carId)))
        nav.execute(NavigationCommand.NavigateTo(OdoDestination.BillScanner.Capture()))
        nav.execute(NavigationCommand.NavigateTo(OdoDestination.BillScanner.SaveSuccess))

        nav.leaveBillScanFor(log)

        assertEquals(listOf(OdoDestination.Home, log), nav.backStack.toList())
    }

    @Test
    fun leavingWithNoCar_returnsToWhateverOpenedTheScanner() {
        // No active car means there is no log to open, so the errand is simply over.
        val nav = navigator(OdoDestination.Home)
        nav.execute(NavigationCommand.NavigateTo(OdoDestination.BillScanner.Capture()))
        nav.execute(NavigationCommand.NavigateTo(OdoDestination.BillScanner.SaveSuccess))

        nav.leaveBillScanFor(null)

        assertEquals(listOf(OdoDestination.Home), nav.backStack.toList())
    }
}
