package com.hopcape.odo

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hopcape.odo.core.config.ConfigRefresher
import com.hopcape.odo.core.config.ConfigRegistry
import com.hopcape.odo.core.config.ConfigResolver
import com.hopcape.odo.core.config.ConfigSource
import com.hopcape.odo.core.config.FeatureConfig
import com.hopcape.odo.core.config.NoRemoteConfigSource
import com.hopcape.odo.feature.onboarding.OnboardingConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.mp.KoinPlatform

/**
 * The config system against the **real** application graph — every module `initKoin`
 * installs, started by `OdoApplication` exactly as it is on a device.
 *
 * The unit tests build two-module graphs, which proves the wiring they contain and nothing
 * about the other twenty-odd. A missing definition is a runtime failure, so compiling is
 * not evidence: this is what says the thing is actually wired.
 *
 * **The values here are the compiled defaults, not the console's.** [OdoTestRunner] pins
 * every key before the first test runs. Without it these assertions would be about whatever
 * Firebase Remote Config happened to hold that morning — which is how a growth experiment
 * switched on for real users came to fail four unrelated suites in this repository.
 */
@RunWith(AndroidJUnit4::class)
class ConfigGraphTest {

    private val koin get() = KoinPlatform.getKoin()

    @Test
    fun everyConfigTypeResolvesFromTheRealGraph() {
        assertNotNull(koin.get<ConfigRegistry>())
        assertNotNull(koin.get<ConfigResolver>())
        assertNotNull(koin.get<ConfigSource>())
        assertNotNull(koin.get<ConfigRefresher>())
        assertNotNull(koin.get<FeatureConfig>())
    }

    @Test
    fun theRegistryHoldsEveryKeyDeclaredAnywhere() {
        val keys = koin.get<ConfigRegistry>().keys.map { it.key }.toSet()

        assertEquals(
            setOf(
                "min_supported_version_code",
                "maintenance_mode",
                "maintenance_message",
                "legal_privacy_policy_url",
                "legal_terms_url",
                "legal_delete_account_url",
                "support_email",
                "auto_odometer_enabled",
                "refuel_detect_enabled",
                "challan_check_enabled",
                "plate_lookup_enabled",
                // Declared in :feature:onboarding — a third module contributing keys.
                "onboarding_video_enabled",
                "onboarding_video_refuel_url",
                "onboarding_video_scanner_url",
            ),
            keys,
        )
    }

    @Test
    fun noKeyIsDeclaredTwice() {
        // The debug policy is to fail on launch, so reaching this line at all is half the
        // assertion. The other half is that the registry agrees.
        assertEquals(emptyList<String>(), koin.get<ConfigRegistry>().duplicateKeys)
    }

    @Test
    fun theFirebaseSourceReplacedTheNoBackendOne() {
        assertNotSame(NoRemoteConfigSource, koin.get<ConfigSource>())
        assertNotSame(ConfigRefresher.None, koin.get<ConfigRefresher>())
    }

    @Test
    fun theOnboardingVariantResolvesAndDefaultsToTheUsualFlow() {
        // The compiled default, which is what a fresh install shows before its first fetch
        // lands and what it keeps forever if that fetch never does. Both intros are built
        // and both work; this is only about which one an unreached device opens.
        assertEquals(false, koin.get<OnboardingConfig>().videoEnabled)
    }

    @Test
    fun bothFeatureFlagsReadTheirCompiledDefaults() {
        // Pinned by the runner, so this is the default the declaration carries — which is
        // what a fresh install with no network sees.
        val config = koin.get<FeatureConfig>()

        assertTrue(config.autoOdometerEnabled)
        assertTrue(config.refuelDetectEnabled)
    }

    @Test
    fun theDebugOverrideStoreIsBound() {
        // Debug builds only, and this suite is one — the runner could not have pinned
        // anything otherwise, so every assertion above rests on this binding existing.
        val described = koin.get<ConfigResolver>().describeAll()

        // Against the registry rather than a literal count. A number here says nothing
        // theRegistryHoldsEveryKeyDeclaredAnywhere does not say more precisely, and it
        // fails on every key added afterwards — which is how it came to expect 12 of 14.
        assertEquals(koin.get<ConfigRegistry>().keys.map { it.key }.toSet(), described.map { it.key.key }.toSet())
        assertTrue(described.all { it.key.owner.isNotBlank() && it.key.why.isNotBlank() })
    }
}
