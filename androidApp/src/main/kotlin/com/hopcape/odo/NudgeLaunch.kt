package com.hopcape.odo

import android.content.Intent
import com.hopcape.odo.core.common.notification.NotificationOpen
import com.hopcape.odo.core.navigation.NavigationCommand
import com.hopcape.odo.core.navigation.NavigationManager
import com.hopcape.odo.core.navigation.OdoDestination
import com.hopcape.odo.core.platform.notification.EngagementNudge

/** Routes a nudge tap to its screen, then drops the extra so recreation does not repeat it. */
internal object NudgeLaunch {

    fun handle(intent: Intent?, navigationManager: NavigationManager): Boolean {
        val nudge = intent?.getStringExtra(NotificationOpen.EXTRA_NUDGE) ?: return false
        intent.removeExtra(NotificationOpen.EXTRA_NUDGE)
        val destination = when (nudge) {
            EngagementNudge.FIRST_SCORE.name -> OdoDestination.BillScanner.Capture()
            else -> return false
        }
        navigationManager.navigate(NavigationCommand.NavigateTo(destination))
        return true
    }
}
