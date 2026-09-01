package com.hopcape.odo.core.config

/**
 * The two feature switches that used to be `const val`s in `:core:common`.
 *
 * They are declared here, not in a feature module, because neither has a single owner:
 * `auto_odometer_enabled` is read from `:shared`, `:feature:garage` and
 * `:feature:dashboard`, and a feature module depending on a sibling feature module is not
 * something this repo does anywhere. `:core:config` is the same altitude `FeatureFlags`
 * sat at.
 *
 * Both default to **true**, because both were `true` in code, and a fresh install with no
 * network has to behave the way it behaves now.
 *
 * **Remote config turns things off, never on.** A flag can only reach code the installed
 * APK already contains and the manifest already declares — see [refuelDetectEnabled].
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
}
