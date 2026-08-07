package com.hopcape.odo.infrastructure.firebase.analytics

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.initialize
import platform.Foundation.NSBundle

/**
 * Configures the native Firebase app on iOS. Unlike Android — where FirebaseInitProvider
 * reads google-services.json and configures the app automatically before any of our code
 * runs — iOS has no equivalent, and calling a FirebaseAnalytics method before this has run
 * is undefined by the native SDK's own rules. Guarded on GoogleService-Info.plist actually
 * being in the bundle, so an app with no Firebase project configured still launches
 * cleanly instead of crashing at startup.
 *
 * Returns whether Firebase is usable, so the caller can skip adding [FirebaseAnalyticsSink]
 * to `AnalyticsConfig.destinations` entirely rather than adding a destination whose calls
 * would hit an unconfigured SDK — [RealFirebaseAnalyticsGateway]'s lazy, fail-safe lookup
 * is the fallback for any other way Firebase fails to initialize, not the first line of
 * defense against this specific, expected "no plist yet" state.
 *
 * Call once, before constructing [FirebaseAnalyticsSink] — `MainViewController` does this
 * ahead of `HAnalytics.init`.
 */
fun configureFirebaseForIos(onDiagnostic: (String) -> Unit = {}): Boolean {
    val hasConfig = NSBundle.mainBundle.pathForResource("GoogleService-Info", ofType = "plist") != null
    if (!hasConfig) {
        onDiagnostic("firebase: GoogleService-Info.plist not found — Firebase destination disabled")
        return false
    }
    return runCatching { Firebase.initialize() }
        .onFailure { onDiagnostic("firebase: initialize failed — ${it::class.simpleName}") }
        .isSuccess
}
