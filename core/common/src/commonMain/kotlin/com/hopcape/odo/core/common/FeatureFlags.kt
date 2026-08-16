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
     * True. The garage shows the auto-odometer card and its status tile, enrollment is
     * reachable from there, and a finished trip redirects to the trip-logged screen.
     *
     * The feature needs background location, activity recognition and Bluetooth. All three
     * are asked for at runtime, in context — the owner reaches them through enrollment, not
     * at launch — and the permissions are declared in the Android manifest again.
     *
     * **Finding every site.** A project-wide search for `AUTO_ODOMETER_ENABLED` lists them,
     * and deleting this constant fails the build at each one:
     *
     * - `ObserveAutoOdometerCardState` (garage) — the one choke point for the card *and* the
     *   status tile. With the flag off it returns `Hidden`: no card, no tap, no navigation.
     * - `shouldRedirectToTripLogged` (`:shared`) — the app-shell redirect to M6.
     * - `AutoOdometerEndToEndTest` — each flow test is guarded by `assumeTrue`, with an
     *   `assumeFalse` twin asserting the garage card is absent. `AggregateOdometerVerification
     *   Test` is guarded the same way. Both compile either way.
     *
     * **Two things the search does not find**, because they are not Kotlin. Turning this flag
     * off again means emptying both, together:
     *
     * - `androidApp/src/main/AndroidManifest.xml` — `ACCESS_FINE_LOCATION`,
     *   `ACCESS_COARSE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`, `ACTIVITY_RECOGNITION`,
     *   `BLUETOOTH_CONNECT`, `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_LOCATION`.
     *   `POST_NOTIFICATIONS` stays either way — document reminders use it.
     * - `core/triptracker/src/androidMain/AndroidManifest.xml` — the foreground service and
     *   the two broadcast receivers. They have to move with the permissions: a service typed
     *   `location` without `FOREGROUND_SERVICE_LOCATION` is a lint error, and a
     *   manifest-declared receiver with the feature off would keep waking the process for a
     *   car that nothing is listening for.
     */
    const val AUTO_ODOMETER_ENABLED = true

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
