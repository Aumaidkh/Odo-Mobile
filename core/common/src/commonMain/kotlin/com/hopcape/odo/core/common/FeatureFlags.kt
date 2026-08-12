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
     * Whether the app can sell Odo Pro.
     *
     * False for 1.0. The paywall screen and its call sites are written, but nothing behind
     * them takes money yet, so a "Start Pro" would end on a button that cannot do anything.
     * While this is false two things hold: no screen names Pro, the free plan, or a price,
     * and no button reaches the `OdoDestination.Paywall` route. The upsell CTAs that remain
     * visible are disabled and marked "coming soon".
     *
     * Flip it to true when RevenueCat lands. The paywall, the upsell cards, the Pro copy and
     * the navigation calls are all still here, guarded by this flag — nothing is rewritten.
     *
     * **Removing it.** This flag is meant to live for one release, not to stay as a switch.
     * Everything it guards landed in a single squash-merged commit, so `git revert` on that
     * commit undoes all of it at once — the flag, the guards and the two reworded strings —
     * and is the first thing to try.
     *
     * **Finding every site**, when the revert conflicts or you would rather do it by hand.
     * Nothing was commented out or deleted, so a project-wide search for `PAYWALL_ENABLED`
     * lists all of them, and the list cannot go stale: delete this constant and the build
     * fails at every site that reads it. What is behind each one:
     *
     * - `ProfileScreen` — the plan card (`ProPlanCard` / `GoProCard`). Hidden, not removed.
     * - `ExportDataSheet` (profile) and `ExportSheet` (garage) — buttons disabled under a
     *   "coming soon" badge; the garage sheet's "part of Odo Pro" note is hidden.
     * - `HealthScoreScreen` — the breakdown is never locked while this is false.
     * - `BillScanUiState.showQuota` — the "2 of 3 free" pill is off.
     * - The end-to-end tests — the paywall ones are skipped by `assumeTrue`, and each has an
     *   `assumeFalse` twin asserting what 1.0 ships. Both halves compile either way.
     *
     * **Two things the search finds only by comment**, because they are copy rather than
     * code, and both say so where they sit: `dv_error_limit_reached` (reworded to stop
     * naming the free plan) and `DocumentVaultFlowRobot.LIMIT_REACHED`, which asserts it.
     *
     * **Also delete when it goes true:** the now-unused `pf_coming_soon`, `gr_ex_coming_soon`
     * and `GarageCopy.EXPORT_COMING_SOON` strings.
     *
     * **Not guarded by this flag**, and a separate decision: `AlwaysProEntitlement` still
     * answers true (so nothing is content-locked) and `FreeTierDocumentAllowance` still caps
     * documents at 3. Both are stubs that a real entitlement adapter replaces.
     */
    const val PAYWALL_ENABLED = false

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
     * Whether the scanner can read a payment QR and hand it to a UPI app.
     *
     * False for 1.0. The flow is written and tested end to end — read the code, parse the
     * `upi://pay` grammar, launch a chooser, read the response back, log the fuel fill it
     * bought — but it moves the owner's money, and 1.0 is not the release to ask them to
     * trust that with. It ships in the next one.
     *
     * While this is false the scanner offers two modes instead of three. `ScanTarget.PaymentQr`
     * is unreachable, and that single fact turns the whole feature off: the frame analyser
     * stays on document edges rather than QR codes, so no code is ever read; the gallery path
     * never decodes one; and `BillScanEffect.OpenPayment` — the only thing that navigates to
     * `OdoDestination.BillScanner.PayAtPump` — is never emitted. The route stays registered
     * and the Koin graph stays wired: unreachable, not removed.
     *
     * Flip it to true when payments are ready. Nothing here is rewritten.
     *
     * **Finding every site.** A project-wide search for `PAY_VIA_QR_ENABLED` lists them, and
     * deleting this constant fails the build at each one:
     *
     * - `scanTargets` (`BillScanUiState.kt`) — the one choke point. The mode chips are built
     *   from it, and `BillScanViewModel` checks every target against it, both on the way in
     *   (a deep link carrying `ScanTarget.PaymentQr` lands on `Bill`) and on every switch.
     * - `BillScannerEndToEndTest` — the QR and pay-at-pump tests are skipped by `assumeTrue`,
     *   with an `assumeFalse` twin asserting the chip is absent. Both compile either way.
     *
     * **One thing the search does not find**, because it is not Kotlin:
     * `androidApp/src/main/AndroidManifest.xml` — the `<queries>` block declaring the
     * `upi:` VIEW intent was removed. It is Android 11+ package visibility, not a permission,
     * but it exists only so this feature can ask "can anything here take a payment", and a
     * manifest that still asks is a manifest that still describes a feature the app does not
     * have. Put it back with the flag; without it `resolveActivity` answers null on a phone
     * with four UPI apps installed. The file carries a comment pointing here.
     *
     * **Not touched:** `CAMERA`. Bills and documents need it, and it was never QR's alone.
     */
    const val PAY_VIA_QR_ENABLED = false
}
