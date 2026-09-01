package com.hopcape.odo

import androidx.test.runner.AndroidJUnitRunner
import com.hopcape.odo.core.config.ConfigRegistry
import com.hopcape.odo.core.config.LocalConfigOverrides
import org.koin.mp.KoinPlatform

/**
 * The instrumentation runner, which exists to do one thing before any test runs: pin every
 * config key to the default compiled into this build.
 *
 * **Why.** [ConfigSource][com.hopcape.odo.core.config.ConfigSource] on a device is Firebase
 * Remote Config, so without this the suite reads whatever the console happens to hold. That
 * is not a hypothetical: `onboarding_video_enabled` was switched on for the growth
 * experiment, every first-run suite began opening on the video intro instead of the welcome
 * page, and four classes failed on a change no one made to this repository. Worse, the fetch
 * lands asynchronously — the first test of a run saw the old value and the rest saw the new
 * one, so the same suite failed differently depending on how fast the network was.
 *
 * Pinning uses [LocalConfigOverrides], which is already first in the resolution order and
 * already debug-only, so nothing about how a value is resolved changes here. The suite tests
 * the app; a console is not part of the app.
 *
 * A suite that wants a different value asks for it — see `pinConfig` in `ConfigRobot`. That
 * makes the variant under test something the file states rather than something the reader
 * has to know.
 */
class OdoTestRunner : AndroidJUnitRunner() {

    /**
     * The first moment the graph exists.
     *
     * Not `onStart`, which is the obvious-looking hook and the wrong one: it runs before
     * `OdoApplication.onCreate`, so `initKoin` has not been called and there is no override
     * store to write to. Overriding this instead pins the keys between the app finishing its
     * own start-up and the first test class being loaded, which is early enough that no
     * activity has read a value yet.
     */
    override fun callApplicationOnCreate(app: android.app.Application) {
        super.callApplicationOnCreate(app)
        pinEveryKeyToItsCompiledDefault()
        // Same reasoning as the config pin, one layer down: the fairness check reads city
        // benchmarks from a Supabase RPC, so without this the suite's verdicts depend on a
        // backend. It has to happen here rather than from a suite's own setup because
        // FairnessRepository is a single that captures the source when it is first built —
        // by the time any @Before runs, an earlier class may already have fixed it.
        runCatching { TestFairnessBenchmarks.bind() }
    }

    /**
     * Leave the device as the suite found it.
     *
     * The overrides are SharedPreferences, so without this a test run would silently
     * reconfigure the app for whoever picks the emulator up next — including the QA screen,
     * which would show twelve keys overridden and no reason why.
     */
    override fun finish(resultCode: Int, results: android.os.Bundle?) {
        overridesOrNull()?.clearAll()
        super.finish(resultCode, results)
    }

    private fun pinEveryKeyToItsCompiledDefault() {
        val overrides = overridesOrNull() ?: return
        val registry = KoinPlatform.getKoin().get<ConfigRegistry>()
        // `default` is already held in the raw string form the override store parses, which
        // is why this needs no per-type branch.
        registry.keys.forEach { overrides.set(it.key, it.default.toString()) }
    }

    /**
     * Null only if the graph is not up, which means the app failed to launch — a failure the
     * tests themselves report far better than a crash inside the runner would.
     */
    private fun overridesOrNull(): LocalConfigOverrides? =
        runCatching { KoinPlatform.getKoin().get<LocalConfigOverrides>() }.getOrNull()
}
