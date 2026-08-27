package com.hopcape.odo.infrastructure.firebase.remoteconfig

import com.hopcape.odo.core.config.ConfigRefresher
import com.hopcape.odo.core.config.ConfigRegistry
import com.hopcape.odo.core.domain.appstatus.MaintenanceSeverity
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

private val FETCHED_AT = Instant.fromEpochMilliseconds(1_700_000_000_000)

class RemoteConfigAppStatusSourceTest {

    @Test
    fun `never fetched returns null so the provider keeps its previous verdict`() = runTest {
        assertNull(source(lastFetchAt = null).fetch())
    }

    @Test
    fun `a fresh full_block maps straight through`() = runTest {
        val status = source(
            AppStatusConfigContribution.MIN_SUPPORTED_VERSION_CODE to "42",
            AppStatusConfigContribution.MAINTENANCE_MODE to "full_block",
            AppStatusConfigContribution.MAINTENANCE_MESSAGE to "down for migration",
        ).fetch()

        assertEquals(42L, status?.minSupportedVersionCode)
        assertEquals(MaintenanceSeverity.FULL_BLOCK, status?.maintenance)
        assertEquals("down for migration", status?.maintenanceMessage)
        assertEquals(FETCHED_AT, status?.fetchedAt)
    }

    @Test
    fun `an unrecognised maintenance_mode fails open to OFF`() = runTest {
        val status = source(AppStatusConfigContribution.MAINTENANCE_MODE to "definitely_a_typo").fetch()

        assertEquals(MaintenanceSeverity.OFF, status?.maintenance)
    }

    @Test
    fun `a missing min version code defaults to zero which blocks nothing`() = runTest {
        assertEquals(0L, source().fetch()?.minSupportedVersionCode)
    }

    @Test
    fun `a blank maintenance message reads as no message rather than an empty string`() = runTest {
        val status = source(AppStatusConfigContribution.MAINTENANCE_MESSAGE to "   ").fetch()

        assertNull(status?.maintenanceMessage)
    }

    @Test
    fun `defaults are declared for every key the class reads`() {
        // The declaration is now the only place these keys exist, so this asserts the
        // generated contribution rather than a hand-written map.
        val defaults = ConfigRegistry(listOf(AppStatusConfigContribution)).defaults()

        assertEquals(
            setOf(
                AppStatusConfigContribution.MIN_SUPPORTED_VERSION_CODE,
                AppStatusConfigContribution.MAINTENANCE_MODE,
                AppStatusConfigContribution.MAINTENANCE_MESSAGE,
            ),
            defaults.keys,
        )
        assertEquals(0L, defaults[AppStatusConfigContribution.MIN_SUPPORTED_VERSION_CODE])
        assertEquals("OFF", defaults[AppStatusConfigContribution.MAINTENANCE_MODE])
    }

    private fun source(
        vararg values: Pair<String, String>,
        lastFetchAt: Instant? = FETCHED_AT,
    ): RemoteConfigAppStatusSource {
        val gateway = FakeGateway(values.toMap().toMutableMap(), lastFetchAt)
        return RemoteConfigAppStatusSource(
            gateway = gateway,
            config = AppStatusConfigImpl(resolverOver(gateway, AppStatusConfigContribution)),
            refresher = ConfigRefresher.None,
        )
    }
}
