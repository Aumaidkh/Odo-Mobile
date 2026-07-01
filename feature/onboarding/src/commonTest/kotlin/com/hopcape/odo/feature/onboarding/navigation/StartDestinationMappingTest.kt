package com.hopcape.odo.feature.onboarding.navigation

import com.hopcape.odo.core.navigation.OdoDestination
import com.hopcape.odo.feature.onboarding.presentation.contract.StartDestination
import kotlin.test.Test
import kotlin.test.assertEquals

class StartDestinationMappingTest {

    @Test
    fun everyStartDestination_mapsToANavKey() {
        // M1: all surfaces land on Home until their features ship. The test pins
        // the current contract so a future re-point is a deliberate change.
        assertEquals(OdoDestination.Home, StartDestination.DASHBOARD.toOdoDestination())
        assertEquals(OdoDestination.Home, StartDestination.RESALE_PASSPORT.toOdoDestination())
        assertEquals(OdoDestination.Home, StartDestination.DOCUMENT_VAULT.toOdoDestination())
    }
}
