package com.hopcape.odo.core.common

/**
 * Compile-time switches for work that is built but not shipped yet.
 *
 * A flag here is a release decision, not a runtime setting: it is a `const`, so the branch it
 * guards is decided when the APK is built and there is nothing to misconfigure on a device.
 * Every flag states what flipping it turns back on.
 *
 * Public for the same reason as [BuildInfo] — every module sits above `:core:common`.
 */
object FeatureFlags {

    /**
     * Whether the app tracks drives on its own and keeps the odometer current from them.
     *
     * False for 1.0. The whole feature is written and tested — `:core:triptracker`'s engine,
     * `:feature:auto-odometer`'s five screens, the garage card — but shipping it means asking
     * a first-time owner for background location, activity recognition and Bluetooth before
     * they have any reason to trust the app with them. 1.0 asks for none of the three: they
     * are not in the Android manifest at all, so even a code path that slipped through would
     * be denied by the platform rather than half-working.
     *
     * While this is false: the garage never shows the auto-odometer card or its status tile,
     * so there is no way into enrollment, and with no enrollment nothing ever calls
     * `TripTracker.setEnabled(true)`. The trip-logged redirect is off as well, so a trip row
     * that somehow existed would not take over the screen. The routes stay registered and the
     * Koin graph stays wired — unreachable, not removed.
     *
     * Flip it to true when the permission story is ready. Nothing here is rewritten.
     *
     * **Finding every site.** A project-wide search for `AUTO_ODOMETER_ENABLED` lists them,
     * and deleting this constant fails the build at each one:
     *
     * - `ObserveAutoOdometerCardState` (garage) — the one choke point for the card *and* the
     *   status tile. Returns `Hidden`, so no card, no tap, no telemetry, no navigation.
     * - `shouldRedirectToTripLogged` (`:shared`) — the app-shell redirect to M6.
     * - `AutoOdometerEndToEndTest` — each flow test is skipped by `assumeTrue`, with an
     *   `assumeFalse` twin asserting the garage card is absent. `AggregateOdometerVerification
     *   Test` is skipped the same way. Both compile either way.
     *
     * **Two things the search does not find**, because they are not Kotlin:
     *
     * - `androidApp/src/main/AndroidManifest.xml` — `ACCESS_FINE_LOCATION`,
     *   `ACCESS_COARSE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`, `ACTIVITY_RECOGNITION`,
     *   `BLUETOOTH_CONNECT`, `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_LOCATION` were all
     *   removed. `POST_NOTIFICATIONS` stays — document reminders use it.
     * - `core/triptracker/src/androidMain/AndroidManifest.xml` — the foreground service and
     *   the two broadcast receivers were removed. They had to go with the permissions: a
     *   service typed `location` without `FOREGROUND_SERVICE_LOCATION` is a lint error, and a
     *   manifest-declared receiver would keep waking the process for a car that nothing is
     *   listening for. Both files carry a comment pointing back here.
     */
    const val AUTO_ODOMETER_ENABLED = false

    /**
     * Whether Odo reads payment notifications to detect a fill on its own.
     *
     * False for this release. The detection itself is written and tested — the classifier, the
     * usual-spend band, the draft builder, the opt-in screen and the settings behind it — but
     * it needs `BIND_NOTIFICATION_LISTENER_SERVICE`, whose system dialog tells the owner Odo
     * will be able to read every notification including message text. That is a claim worth
     * getting reviewed before it ships, not one to discover in a policy rejection.
     *
     * Nothing about the feature depends on it. Smart refuel's other two channels — reading the
     * pump display, and the prefilled form — work in every market with no such permission, and
     * they are what the design leans on. Detection is the best case, not the floor.
     *
     * While this is false: the auto-detect screen is unreachable, so nothing ever writes
     * `detect_enabled`, and the notice source never emits — the listener service is not in the
     * manifest, so the OS has nothing to bind. The routes stay registered and the Koin graph
     * stays wired; unreachable, not removed.
     *
     * **Finding every site.** A project-wide search for `SMART_REFUEL_DETECT_ENABLED` lists
     * them, and deleting this constant fails the build at each one:
     *
     * - `ProfileEntryPoints` / the refuel entry provider — whether the auto-detect row and its
     *   destination are offered at all.
     * - `RefuelDetectionWorker` — the collector that turns a notice into a draft. Returns
     *   before subscribing, so nothing is read even if the service were somehow bound.
     *
     * **One thing the search does not find**, because it is not Kotlin:
     *
     * - `androidApp/src/main/AndroidManifest.xml` — the `<service>` entry for
     *   `RefuelNotificationListenerService`, with its `BIND_NOTIFICATION_LISTENER_SERVICE`
     *   permission and `android.service.notification.NotificationListenerService` intent
     *   filter, is deliberately absent. It has to go back with this flag: the class exists and
     *   compiles, but a service the manifest does not declare is one the OS will never bind,
     *   and a manifest that declares it is a manifest asking for the permission. The file
     *   carries a comment pointing back here.
     */
    const val SMART_REFUEL_DETECT_ENABLED = true
}
