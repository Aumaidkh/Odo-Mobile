package com.hopcape.odo

import com.hopcape.odo.core.config.LocalConfigOverrides
import org.junit.rules.ExternalResource
import org.koin.mp.KoinPlatform

/**
 * Choosing the config a suite runs under.
 *
 * [OdoTestRunner] has already pinned every key to its compiled default, so a suite only
 * names the keys where it wants something else — and naming it in the file is the point. A
 * suite that drives the video intro should say so, rather than passing or failing on what a
 * console holds that morning.
 */

/** Set [key] for the rest of this run. The value is raw, the same form a default is written in. */
internal fun pinConfig(key: String, raw: String) {
    overrides().set(key, raw)
}

/** Back to the compiled default — not "unset", which would let the remote value through. */
internal fun unpinConfig(key: String, compiledDefault: String) {
    overrides().set(key, compiledDefault)
}

private fun overrides(): LocalConfigOverrides = KoinPlatform.getKoin().get()

/**
 * A [pinConfig] that undoes itself, ordered so it runs before the activity launches.
 *
 * Where the app opens is decided once per launch, so a flag that selects a start destination
 * has to be set before there is an activity to ask. Chain it outside the compose rule:
 *
 * ```
 * private val compose = createAndroidComposeRule<MainActivity>()
 * @get:Rule val rules = RuleChain.outerRule(PinnedConfig(KEY to "true", default = "false")).around(compose)
 * ```
 */
internal class PinnedConfig(
    private val key: String,
    private val value: String,
    private val compiledDefault: String,
) : ExternalResource() {

    override fun before() = pinConfig(key, value)

    override fun after() = unpinConfig(key, compiledDefault)
}
