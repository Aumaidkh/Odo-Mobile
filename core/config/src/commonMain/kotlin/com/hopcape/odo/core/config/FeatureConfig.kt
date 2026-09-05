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
        why = "Shows the garage's challan row; off hides every path into the challan screens",
    )
    val challanEnabled: Boolean

    /**
     * Whether a typed-in plate is resolved against cars belonging to **other** owners.
     *
     * Only this last tier is gated. Answering from the owner's own cars, on the device or
     * on the server, needs no switch — it shows them what they already told us.
     *
     * **On by default.** The `resolve_plate` RPC is reachable without a session by design
     * (issue #392), so the daily counters in `plate_lookup_charge` are the whole of the
     * defence — watch them after the first release rather than after the first complaint.
     * Setting this key to false in the console is the kill switch, and it works: the tier
     * is built at construction, so a flip lands on the next launch.
     *
     * Turning it on where the migration has not run answers nothing rather than failing —
     * PostgREST 404s an absent function, which the adapter reads as `LookupUnavailable`.
     */
    @Flag(
        key = "plate_lookup_enabled",
        default = true,
        owner = "platform",
        why = "Gate on resolving a plate against other owners' cars, and the kill switch after",
    )
    val plateLookupEnabled: Boolean

    /**
     * Whether a bill line the app's rules could not name is sent to the model to be named.
     *
     * **Off by default, and this one has to be.** Off, nothing leaves the device: the check
     * runs on the rule table alone and an unnamed line stays unchecked, exactly as it does
     * today. On, the wording printed on a bill goes to an Edge Function and from there to
     * Google — which is a sentence the privacy policy owes before any release carries it
     * (AI_ADVISORY_PLAN, #261). The flag is what keeps those two facts in step.
     *
     * It is also the spend switch on the app's side. The Edge Function meters itself per owner
     * and per day, but the cheapest call is the one nobody makes.
     *
     * Read at the moment a line comes back unnamed, so a flip lands on the next check.
     */
    @Flag(
        key = "advisory_classifier_enabled",
        default = false,
        owner = "platform",
        why = "Gate on sending unmatched bill lines to the model, and the kill switch after",
    )
    val advisoryClassifierEnabled: Boolean

    /**
     * Whether a logged bill can be checked against what the job normally costs.
     *
     * A launch flag, like [challanEnabled], and off for the same reason: everything the
     * check needs ships in the APK, but the answer it gives is only as good as the
     * reference tables behind it, and those are still being entered by hand
     * (`docs/AI_ADVISORY_PLAN.md` Track A). A check that says "we could not price any of
     * this" is worse than no button.
     *
     * Off closes both ways in — the button on a service entry's detail, and the landing a
     * finished scan takes. A scan still saves and still lands somewhere; it lands on the
     * saved-success screen instead. Nothing about scanning, saving or the record changes.
     *
     * Read when the screen is built, so a flip lands on the next entry opened or the next
     * bill scanned.
     */
    @Flag(
        key = "bill_check_enabled",
        default = false,
        owner = "growth",
        why = "Opens the bill check to owners once the price tables can answer; kill switch after",
    )
    val billCheckEnabled: Boolean

    /**
     * Whether the pre-service checklist is offered — "Before you go in", the list of what a
     * service should cover, what it should not, and roughly what it should cost.
     *
     * A launch flag, off until the maker's schedule is entered. The screen builds its list
     * from `service_schedule`, so with that table empty it has nothing to show, and the one
     * moment it exists for is the walk from the car park to the counter — a blank screen
     * there is worse than no entry point.
     *
     * Off closes all three doors at once: Home's attention card goes back to opening the
     * service log, Home's own "Before you go in" card stays down, and the garage's actions
     * sheet drops its row. The destination itself stays registered rather than removed, the
     * same way the refuel routes are handled — unreachable, not absent.
     */
    @Flag(
        key = "service_checklist_enabled",
        default = false,
        owner = "growth",
        why = "Opens the pre-service checklist once the maker's schedule is entered; kill switch after",
    )
    val serviceChecklistEnabled: Boolean
}
