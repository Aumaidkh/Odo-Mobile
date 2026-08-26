package com.hopcape.odo.feature.onboarding

import com.hopcape.odo.core.config.ConfigGroup
import com.hopcape.odo.core.config.Flag
import com.hopcape.odo.core.config.Value

/**
 * Which onboarding a new install is shown.
 *
 * Declared here rather than in `:shared`, which is the only reader today, because the
 * placement rule is "the lowest module every consumer already depends on" — `:shared`
 * depends on this module, and the video flow itself will live here when it is built.
 *
 * **The video flow does not exist yet.** See [videoEnabled].
 */
@ConfigGroup("onboarding")
interface OnboardingConfig {

    /**
     * Whether a new install is shown the video onboarding instead of the usual one.
     *
     * **Defaults to `false`, and turning it on currently changes nothing.** There is no
     * video flow in the app: no player, no screens, no assets. A remote flag can only reach
     * code the installed APK already contains, so until that ships this key can be set and
     * the usual flow still runs — which is why the app-shell logs `onboarding_video_not_built`
     * rather than falling through in silence. The same shape as `refuel_detect_enabled`, and
     * for the same reason.
     *
     * The default is `false` because that is what the app does today, and a default that
     * differs from current behaviour is a behaviour change on first run.
     *
     * Two things have to happen before this becomes a real switch: the video flow ships, and
     * the branch in `App.kt` points the video case at it.
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
