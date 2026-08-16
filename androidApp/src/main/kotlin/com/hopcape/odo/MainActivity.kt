package com.hopcape.odo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.content.Intent
import com.hopcape.logging.api.HLogger
import com.hopcape.odo.core.navigation.NavigationCommand
import com.hopcape.odo.core.navigation.NavigationManager
import com.hopcape.odo.feature.refuel.RefuelDeepLinks
import com.hopcape.odo.widget.WidgetLaunch
import com.hopcape.performance.api.APM
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val navigationManager: NavigationManager by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            App()
        }

        // After setContent, so the host is already collecting when the command is emitted.
        // The manager buffers, so the ordering is belt and braces rather than load-bearing.
        handleLaunchIntent(intent)

        // The cold-start span closes only once the first meaningful frame has
        // actually been drawn — not at Application.onCreate() — so it measures the
        // real "time to interactive". decorView.post runs after the first layout/draw
        // pass; reportFullyDrawn() is Android's standard "UI is usable now" signal.
        window.decorView.post {
            reportFullyDrawn()
            val app = application as OdoApplication
            APM.endSpan(app.coldStartSpan)
            HLogger.tag("APP_LIFECYCLE").i("app_fully_drawn", mapOf("appSessionId" to app.appSessionId))
        }
    }

    /**
     * A widget tap while the app is already running.
     *
     * The activity is `singleTop`, so a second tap delivers here instead of creating another
     * instance — without this the owner would tap "Scan pump" on a running app and watch
     * nothing happen.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLaunchIntent(intent)
    }

    /**
     * What the app was opened *for*, when it was opened by something other than the launcher.
     *
     * Two callers today: a widget button, and Edit on a detected-fill notification. The
     * detected fill is checked first because it carries a draft — an owner who tapped Edit is
     * mid-flow, and dropping them on the home screen loses the fill they were about to
     * confirm.
     */
    private fun handleLaunchIntent(intent: Intent?) {
        val confirm = RefuelDeepLinks.confirmDestinationFor(
            intent?.getStringExtra(RefuelDeepLinks.EXTRA_DRAFT),
        )
        if (confirm != null) {
            // Consume it before navigating. The activity holds on to its launch intent, and
            // this method runs on every `onCreate` — including the ones nobody asked for: a
            // theme or font-size change, a locale change, a restore after process death. Each
            // of those re-read the same extra and pushed the same key onto a back stack that
            // had just been restored with it already on top, and two identical keys is a crash
            // in Compose's SaveableStateHolder ("Key … was used multiple times"), not a
            // duplicate screen.
            //
            // A launch intent is an instruction to do something once. Clearing the extra is
            // what makes it one — `setIntent` in `onNewIntent` hands us the same object, so
            // this mutates exactly the copy a later recreation would read.
            intent?.removeExtra(RefuelDeepLinks.EXTRA_DRAFT)
            navigationManager.navigate(NavigationCommand.NavigateTo(confirm))
            return
        }
        // Same reasoning for the widget: its action would otherwise reopen the pump scanner
        // every time the activity was rebuilt.
        if (WidgetLaunch.handle(intent, navigationManager)) intent?.action = null
    }
}
