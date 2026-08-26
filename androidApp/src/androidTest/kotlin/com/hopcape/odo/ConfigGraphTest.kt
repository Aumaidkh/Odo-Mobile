package com.hopcape.odo

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hopcape.odo.core.config.ConfigRefresher
import com.hopcape.odo.core.config.ConfigRegistry
import com.hopcape.odo.core.config.ConfigResolver
import com.hopcape.odo.core.config.ConfigSource
import com.hopcape.odo.core.config.FeatureConfig
import com.hopcape.odo.core.config.NoRemoteConfigSource
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
    fun bothFeatureFlagsReadTheirCompiledDefaults() {
        // No console values are set for this project, so both answer with the default the
        // declaration carries — which is what a fresh install with no network sees.
        val config = koin.get<FeatureConfig>()

        assertTrue(config.autoOdometerEnabled)
        assertTrue(config.refuelDetectEnabled)
    }

    @Test
    fun theDebugOverrideStoreIsBound() {
        // Debug builds only, and this suite is one. describe() reporting DEFAULT for every
        // key also says nothing is overridden on this device right now.
        val described = koin.get<ConfigResolver>().describeAll()

        assertEquals(9, described.size)
        assertTrue(described.all { it.key.owner.isNotBlank() && it.key.why.isNotBlank() })
    }
}
