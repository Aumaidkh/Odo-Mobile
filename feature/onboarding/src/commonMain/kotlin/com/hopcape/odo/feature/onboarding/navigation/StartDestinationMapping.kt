package com.hopcape.odo.feature.onboarding.navigation

import com.hopcape.odo.core.navigation.OdoDestination
import com.hopcape.odo.feature.onboarding.presentation.contract.StartDestination

/**
 * Translate onboarding's routing decision ([StartDestination], emitted as data by
 * the ViewModel) into an actual navigation key. This is the single seam between the
 * presentation layer's goal-based decision and the app's navigation graph.
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
