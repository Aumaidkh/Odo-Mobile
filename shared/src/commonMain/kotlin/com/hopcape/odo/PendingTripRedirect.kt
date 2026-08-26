package com.hopcape.odo

import com.hopcape.odo.core.config.FeatureConfig
import com.hopcape.odo.core.domain.trip.model.TripId
import com.hopcape.odo.core.navigation.OdoDestination

/**
 * Whether the app shell should push the trip-logged screen (M6) right now.
 *
 * The guard behind docs/AUTO_ODOMETER_PLAN.md §4.4's "only from the dashboard/garage tabs,
 * never interrupting an open form": true only when there is a pending trip *and* the owner
 * is standing on a bottom-nav root ([OdoDestination.TopLevel]). That second condition also
 * rules out redirecting onto the trip-logged screen while it is already on screen (it is not
 * a [OdoDestination.TopLevel] itself) and onto any nested screen, sheet or in-progress form.
 *
 * A plain function rather than logic inlined in `App.kt`'s `LaunchedEffect` so the rule is
 * unit-testable on its own — the effect itself only wires Compose state into it.
 *
 * Always false while `auto_odometer_enabled` is off: nothing records trips then, and
 * a leftover row from an earlier build must not take over the screen.
 */
internal fun shouldRedirectToTripLogged(
    currentDestination: OdoDestination?,
    pendingTripId: TripId?,
    config: FeatureConfig,
): Boolean = config.autoOdometerEnabled &&
    pendingTripId != null &&
    currentDestination is OdoDestination.TopLevel
