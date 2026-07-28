package com.hopcape.odo.core.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Verifies the command → back-stack translation in isolation (no Compose). The
 * back stack is the architecture's source of truth, so these guard its semantics.
 */
class NavigatorCommandExecutorTest {

    private fun navigator(vararg start: NavKey): Navigator =
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
