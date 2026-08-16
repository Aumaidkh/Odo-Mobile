package com.hopcape.odo.widget

import android.content.Intent
import com.hopcape.odo.core.navigation.NavigationCommand
import com.hopcape.odo.core.navigation.NavigationManager
import com.hopcape.odo.core.navigation.OdoDestination
import com.hopcape.odo.core.navigation.ScanTarget

/**
 * Turns a widget tap into a navigation, once the app is actually running.
 *
 * The widget cannot navigate; it can only start the activity. So the intent carries an action
 * string and this is where that string becomes a destination — after the Koin graph exists and
 * the navigation host is collecting.
 *
 * An action this build does not recognise is ignored rather than guessed at. A launcher can
 * hold a pending intent across an app update, and opening the wrong screen is worse than
 * opening the app where it would have opened anyway.
 */
internal object WidgetLaunch {

    const val ACTION_SCAN_PUMP = "com.hopcape.odo.widget.SCAN_PUMP"
    const val ACTION_LOG_FILL = "com.hopcape.odo.widget.LOG_FILL"
    const val ACTION_ODOMETER = "com.hopcape.odo.widget.ODOMETER"

    /**
     * Navigate for [intent], if it names something this build knows.
     *
     * @return whether anything was navigated to, so the caller can tell a widget launch apart
     *   from an ordinary one.
     */
    fun handle(intent: Intent?, navigationManager: NavigationManager): Boolean {
        val destination = when (intent?.action) {
            ACTION_SCAN_PUMP ->
                OdoDestination.BillScanner.Capture(target = ScanTarget.PumpDisplay)

            ACTION_LOG_FILL -> OdoDestination.Refuel.Log
            ACTION_ODOMETER -> OdoDestination.Garage.Home
            else -> return false
        }
        navigationManager.navigate(NavigationCommand.NavigateTo(destination))
        return true
    }
}
