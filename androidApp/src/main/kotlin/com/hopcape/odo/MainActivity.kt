package com.hopcape.odo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.hopcape.logging.api.HLogger
import com.hopcape.performance.api.APM

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            App()
        }

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
}
