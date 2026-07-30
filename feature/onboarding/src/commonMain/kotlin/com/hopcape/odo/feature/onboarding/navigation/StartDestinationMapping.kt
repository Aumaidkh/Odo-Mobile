package com.hopcape.odo.feature.onboarding.navigation

import com.hopcape.odo.core.navigation.OdoDestination
import com.hopcape.odo.feature.onboarding.presentation.state.StartDestination

/**
 * The one seam that turns onboarding's routing *decision* into an actual navigation key.
 *
 * The decision itself is presentation's ([StartDestination], chosen from the owner's goal);
 * this mapping is navigation's, and it lives here so presentation never sees an
 * [OdoDestination].
 *
 * The Dashboard / Resale-Passport / Document-Vault surfaces don't exist yet
 * (later features / Phase 2), so all three currently land on [OdoDestination.Home].
 * Point each at its real destination here as those features ship — callers don't change.
 */
internal fun StartDestination.toOdoDestination(): OdoDestination = when (this) {
    StartDestination.DASHBOARD -> OdoDestination.Home
    StartDestination.RESALE_PASSPORT -> OdoDestination.Home // TODO: ResalePassport destination (Phase 2)
    StartDestination.DOCUMENT_VAULT -> OdoDestination.Home // TODO: DocumentVault destination
}
