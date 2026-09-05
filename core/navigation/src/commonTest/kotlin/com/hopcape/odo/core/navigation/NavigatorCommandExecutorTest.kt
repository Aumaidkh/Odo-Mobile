package com.hopcape.odo.core.navigation

import androidx.navigation3.runtime.NavBackStack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Verifies the command → back-stack translation in isolation (no Compose). The
 * back stack is the architecture's source of truth, so these guard its semantics.
 */
class NavigatorCommandExecutorTest {

    private fun navigator(vararg start: OdoDestination): Navigator =
        OdoNavigator(NavBackStack(*start))

    @Test
    fun navigateTo_pushesDestination() {
        val nav = navigator(OdoDestination.Home)

        nav.execute(NavigationCommand.NavigateTo(OdoDestination.CarDetail("c1")))

        assertEquals(
            listOf(OdoDestination.Home, OdoDestination.CarDetail("c1")),
            nav.backStack.toList(),
        )
    }

    @Test
    fun navigateTo_singleTop_skipsDuplicateTop() {
        val nav = navigator(OdoDestination.Home)

        nav.execute(NavigationCommand.NavigateTo(OdoDestination.Home, singleTop = true))

        assertEquals(listOf(OdoDestination.Home), nav.backStack.toList())
    }

    @Test
    fun navigateTo_neverPutsTheSameKeyOnTheStackTwice() {
        // This used to assert the opposite — that `singleTop = false` pushed a duplicate. It
        // could not: Nav3 keys saved state by the destination, so a stack holding one key
        // twice throws out of SaveableStateHolder and kills the app. A detected-fill
        // notification hit it in the field, by replaying its launch intent on an activity
        // recreation.
        //
        // Nothing in the app passes `singleTop = false`, so the old assertion only ever
        // described a crash nobody had asked for.
        val nav = navigator(OdoDestination.Home)

        nav.execute(NavigationCommand.NavigateTo(OdoDestination.Home, singleTop = false))

        assertEquals(listOf(OdoDestination.Home), nav.backStack.toList())
    }

    @Test
    fun navigateTo_aDestinationAlreadyDeeperInTheStack_bringsItForward() {
        // The shape the crash actually had: the key was not on top, so the singleTop check
        // missed it, and pushing it again duplicated it. Popping back to it is the only
        // meaning the request can have, and it keeps that entry's state.
        val nav = navigator(OdoDestination.Home)
        nav.execute(NavigationCommand.NavigateTo(OdoDestination.Profile.Root))
        nav.execute(NavigationCommand.NavigateTo(OdoDestination.CarDetail("c1")))

        nav.execute(NavigationCommand.NavigateTo(OdoDestination.Profile.Root))

        assertEquals(
            listOf(OdoDestination.Home, OdoDestination.Profile.Root),
            nav.backStack.toList(),
        )
    }

    @Test
    fun navigateTo_popUpTo_resetsThenPushes_likeBottomTabReselect() {
        val nav = navigator(OdoDestination.Home)
        nav.execute(NavigationCommand.NavigateTo(OdoDestination.Profile.Root))
        nav.execute(NavigationCommand.NavigateTo(OdoDestination.CarDetail("c1")))

        // Reselect "Profile" tab: pop up to Home, then land on Profile.
        nav.execute(
            NavigationCommand.NavigateTo(
                destination = OdoDestination.Profile.Root,
                popUpTo = OdoDestination.Home,
                singleTop = true,
            ),
        )

        assertEquals(listOf(OdoDestination.Home, OdoDestination.Profile.Root), nav.backStack.toList())
    }

    @Test
    fun finishFlow_popsEveryStepOfTheFlow_thenLands() {
        val nav = navigator(
            OdoDestination.Home,
            OdoDestination.Documents.Vault,
            OdoDestination.BillScanner.Capture(target = ScanTarget.Document),
            OdoDestination.BillScanner.DocumentReview(photoKey = "p1"),
        )

        // Filing the paper ends the add flow: its steps go, the success screen lands on the
        // vault that opened it.
        nav.execute(
            NavigationCommand.FinishFlow(
                destination = OdoDestination.Documents.AddSuccess("d1"),
                belongsToFlow = ::isAddDocumentFlowStep,
            ),
        )

        assertEquals(
            listOf(
                OdoDestination.Home,
                OdoDestination.Documents.Vault,
                OdoDestination.Documents.AddSuccess("d1"),
            ),
            nav.backStack.toList(),
        )

        // Leaving the success screen pops it and reveals the vault — no second copy pushed.
        nav.execute(
            NavigationCommand.FinishFlow(OdoDestination.Documents.Vault, ::isAddDocumentFlowStep),
        )

        assertEquals(
            listOf(OdoDestination.Home, OdoDestination.Documents.Vault),
            nav.backStack.toList(),
        )
    }

    @Test
    fun finishFlow_replacesTheRoot_whenTheFlowOwnsIt() {
        // First run rooted at the video intro (issue #352): every entry on the stack
        // belongs to the flow, so finishing must not leave the intro underneath the
        // landing destination — back from there would replay the whole first run.
        val nav = navigator(OdoDestination.WelcomeVideo, OdoDestination.Onboarding)

        nav.execute(NavigationCommand.FinishFlow(OdoDestination.Home, ::isFirstRunStep))

        assertEquals(listOf(OdoDestination.Home), nav.backStack.toList())
    }

    @Test
    fun finishFlow_pushesTarget_whenTheFlowWasOpenedElsewhere() {
        val nav = navigator(
            OdoDestination.Home,
            OdoDestination.Garage.Home,
            OdoDestination.Documents.Add(),
            OdoDestination.Documents.AddSuccess("d1"),
        )

        nav.execute(
            NavigationCommand.FinishFlow(OdoDestination.Documents.Vault, ::isAddDocumentFlowStep),
        )

        assertEquals(
            listOf(OdoDestination.Home, OdoDestination.Garage.Home, OdoDestination.Documents.Vault),
            nav.backStack.toList(),
        )
    }

    @Test
    fun finishFlow_leavesAnotherFlowsStepsAlone() {
        // A bill capture is the service log's flow, not the vault's; a document add cannot
        // start on top of one, and if it somehow did, the bill's step would stay.
        val nav = navigator(
            OdoDestination.Home,
            OdoDestination.BillScanner.Capture(target = ScanTarget.Bill),
            OdoDestination.Documents.AddSuccess("d1"),
        )

        nav.execute(
            NavigationCommand.FinishFlow(OdoDestination.Documents.Vault, ::isAddDocumentFlowStep),
        )

        assertEquals(
            listOf(
                OdoDestination.Home,
                OdoDestination.BillScanner.Capture(target = ScanTarget.Bill),
                OdoDestination.Documents.Vault,
            ),
            nav.backStack.toList(),
        )
    }

    @Test
    fun finishFlow_neverLeavesTheStackEmpty_evenWhenTheFlowOwnsTheOnlyEntry() {
        // This used to assert the opposite — that a flow-owned root survived under the
        // landing destination. That guarantee existed to keep the stack non-empty, but
        // FinishFlow pushes its destination in the same command, so replacing the root is
        // safe — and keeping it is the bug that made back replay first run (#352).
        val nav = navigator(OdoDestination.Documents.Add())

        nav.execute(
            NavigationCommand.FinishFlow(OdoDestination.Documents.Vault, ::isAddDocumentFlowStep),
        )

        assertEquals(listOf(OdoDestination.Documents.Vault), nav.backStack.toList())
    }

    @Test
    fun leaveFlow_dropsTheErrandAndLandsOnWhateverOpenedIt() {
        val nav = navigator(
            OdoDestination.Home,
            OdoDestination.Garage.Home,
            OdoDestination.BillScanner.Capture(),
            OdoDestination.BillScanner.Review("p1"),
            OdoDestination.BillCheck.Result(billId = "log-1"),
        )

        nav.execute(NavigationCommand.LeaveFlow(::isBillScanFlowStep))

        // "Done" on the report ends the whole scan, so the confirm step for a bill that is
        // already saved does not sit behind it.
        assertEquals(
            listOf(OdoDestination.Home, OdoDestination.Garage.Home),
            nav.backStack.toList(),
        )
    }

    @Test
    fun leaveFlow_landsOnTheScreenThatAskedForTheCheck() {
        // The same report, reached from a stored entry rather than from a scan: there is no
        // scan to drop, and leaving is a step back to the entry it was about.
        val nav = navigator(
            OdoDestination.Home,
            OdoDestination.ServiceLog.List("car-1"),
            OdoDestination.ServiceLog.Detail(logId = "log-1", carId = "car-1"),
            OdoDestination.BillCheck.Result(billId = "log-1"),
        )

        nav.execute(NavigationCommand.LeaveFlow(::isBillScanFlowStep))

        assertEquals(
            listOf(
                OdoDestination.Home,
                OdoDestination.ServiceLog.List("car-1"),
                OdoDestination.ServiceLog.Detail(logId = "log-1", carId = "car-1"),
            ),
            nav.backStack.toList(),
        )
    }

    @Test
    fun back_popsTop_butNeverTheRoot() {
        val nav = navigator(OdoDestination.Home)
        nav.execute(NavigationCommand.NavigateTo(OdoDestination.CarDetail("c1")))

        nav.execute(NavigationCommand.Back)
        assertEquals(listOf(OdoDestination.Home), nav.backStack.toList())
        assertFalse(nav.canGoBack)

        // Popping the root is a no-op.
        nav.execute(NavigationCommand.Back)
        assertEquals(listOf(OdoDestination.Home), nav.backStack.toList())
    }
}
