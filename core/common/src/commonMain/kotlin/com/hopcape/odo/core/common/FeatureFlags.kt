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
}
