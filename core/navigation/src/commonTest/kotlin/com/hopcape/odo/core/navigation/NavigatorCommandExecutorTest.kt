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
    fun navigateTo_withoutSingleTop_allowsDuplicate() {
        val nav = navigator(OdoDestination.Home)

        nav.execute(NavigationCommand.NavigateTo(OdoDestination.Home, singleTop = false))

        assertEquals(listOf(OdoDestination.Home, OdoDestination.Home), nav.backStack.toList())
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
    fun finishFlow_neverPopsTheRoot() {
        val nav = navigator(OdoDestination.Documents.Add())

        nav.execute(
            NavigationCommand.FinishFlow(OdoDestination.Documents.Vault, ::isAddDocumentFlowStep),
        )

        assertEquals(
            listOf(OdoDestination.Documents.Add(), OdoDestination.Documents.Vault),
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
