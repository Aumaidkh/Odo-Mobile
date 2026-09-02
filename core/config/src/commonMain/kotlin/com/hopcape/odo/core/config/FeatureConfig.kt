package com.hopcape.odo.core.config

/**
 * The app's feature switches, the first two of which used to be `const val`s in
 * `:core:common`.
 *
 * They are declared here, not in a feature module, because none has a single owner:
 * `auto_odometer_enabled` is read from `:shared`, `:feature:garage` and
 * `:feature:dashboard`, and a feature module depending on a sibling feature module is not
 * something this repo does anywhere. `:core:config` is the same altitude `FeatureFlags`
 * sat at.
 *
 * The kill switches default to **true**, because they were `true` in code, and a fresh
 * install with no network has to behave the way it behaves now. A launch flag defaults
 * to **false** for the same reason read backwards: the feature is not live yet, and no
 * fetch may ever land — see [challanEnabled].
 *
 * **Remote config only reaches code the installed APK already contains** and the
 * manifest already declares — see [refuelDetectEnabled] for the flag where that bites.
 */
@ConfigGroup("features")
interface FeatureConfig {

    /**
     * Whether the app tracks drives on its own and keeps the odometer current from them.
     *
     * A genuine kill switch. The permissions and the foreground service are declared in
     * both manifests, so turning this off in the console stops the garage card, enrollment
     * and the trip-logged redirect for everyone without a release. Turning it back on
     * works, because everything it needs already ships.
     */
    @Flag(
        key = "auto_odometer_enabled",
        default = true,
        owner = "platform",
        why = "Kill switch if automatic trip tracking misbehaves in the field",
    )
    val autoOdometerEnabled: Boolean

    /**
     * Whether Odo reads payment notifications to detect a fill on its own.
     *
     * **Turning this off works. Turning it on may not.** The `<service>` entry for the
     * notification listener is deliberately absent from the Android manifest, and a service
     * the manifest does not declare is one the OS will never bind — so on a build without
     * it, setting this key to true changes nothing. `RefuelDetectionWorker` checks whether
     * the listener can actually bind and says so in the logs, which is the difference
     * between a visible no-op and an invisible one.
     *
     * Adding that manifest entry puts `BIND_NOTIFICATION_LISTENER_SERVICE` in front of a
     * Play reviewer, so it is a release decision and not a config one.
     */
    @Flag(
        key = "refuel_detect_enabled",
        default = true,
        owner = "platform",
        why = "Kill switch for notification-based refuel detection",
    )
    val refuelDetectEnabled: Boolean

    /**
     * Whether the garage shows the challan row — pending traffic challans on the owner's
     * car, and the lookup screens behind it.
     *
     * The one launch flag here: it defaults to **false** and is flipped on from the
     * console when the feature is ready to be seen. Unlike [refuelDetectEnabled]'s
     * on-switch, turning this on works, because everything the row needs — the screens,
     * the repository, the challan tables — already ships in the APK. The flag only
     * decides whether the garage offers a way in.
     *
     * Off hides the garage row and with it every path into the challan screens, so after
     * launch the same key doubles as the kill switch. Read from `:feature:garage` at the
     * moment the challan summary is built, not observed live — a flip lands on the next
     * garage load, which is acceptable for a launch gate.
     */
    @Flag(
        key = "challan_check_enabled",
        default = false,
        owner = "growth",
        why = "Support Challan check for a vehicle",
    )
    val challanEnabled: Boolean
}
