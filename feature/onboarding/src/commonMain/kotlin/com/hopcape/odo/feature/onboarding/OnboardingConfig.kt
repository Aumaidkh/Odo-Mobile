package com.hopcape.odo.feature.onboarding

import com.hopcape.odo.core.config.ConfigGroup
import com.hopcape.odo.core.config.Flag
import com.hopcape.odo.core.config.Value

/**
 * Which onboarding a new install is shown.
 *
 * Declared here rather than in `:shared`, which is the only reader today, because the
 * placement rule is "the lowest module every consumer already depends on" — `:shared`
 * depends on this module, and the video flow lives here too.
 */
@ConfigGroup("onboarding")
interface OnboardingConfig {

    /**
     * Whether a new install is shown the video onboarding instead of the usual one.
     *
     * Both intros are built and both end in the same car setup, so this only chooses which
     * page a first launch opens on. `onboardingStartDestination` in `:shared` is the branch;
     * a build that is too old to contain the video screens simply never sees the key, which
     * is the ordinary limit on any remote flag.
     *
     * The default is `false` because that is the flow that predates the flag, and a default
     * that differs from previous behaviour is a behaviour change on first run. It is only
     * the answer for a device the console has not reached — instrumented tests pin it there
     * deliberately, and opt in per suite when the video intro is what they are testing.
     */
    @Flag(
        key = "onboarding_video_enabled",
        default = false,
        owner = "growth",
        why = "Shows the video onboarding to new installs instead of the usual flow",
    )
    val videoEnabled: Boolean

    /**
     * The Smart Refuel clip, streamed.
     *
     * Remote rather than bundled so the clip can be re-cut without a release, which is the
     * whole reason it is a URL. The cost is that a first launch with no network has no video
     * — so blank, unreachable and "still loading" all resolve to the same thing on screen:
     * the page's own title and copy, with Continue working. The video is the decoration, not
     * the step.
     */
    @Value(
        key = "onboarding_video_refuel_url",
        default = "",
        owner = "growth",
        why = "The Smart Refuel clip shown on the first video onboarding page",
    )
    val refuelVideoUrl: String

    /** The Bill Scanner clip. Same rules as [refuelVideoUrl]. */
    @Value(
        key = "onboarding_video_scanner_url",
        default = "",
        owner = "growth",
        why = "The Bill Scanner clip shown on the second video onboarding page",
    )
    val scannerVideoUrl: String
}
