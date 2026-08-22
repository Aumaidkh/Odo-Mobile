package com.hopcape.odo.infrastructure.firebase.remoteconfig

import com.hopcape.odo.core.domain.appstatus.MaintenanceSeverity
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

private val FETCHED_AT = Instant.fromEpochMilliseconds(1_700_000_000_000)

class RemoteConfigAppStatusSourceTest {

    @Test
    fun `never fetched returns null letting the provider keep its previous verdict`() = runTest {
        val source = RemoteConfigAppStatusSource(FakeGateway(lastFetchAt = null))

        assertNull(source.fetch())
    }

    @Test
    fun `a fresh full_block maps straight through`() = runTest {
        val gateway = FakeGateway(
            lastFetchAt = FETCHED_AT,
            values = mapOf(
                RemoteConfigAppStatusSource.KEY_MIN_SUPPORTED_VERSION_CODE to 42L,
                RemoteConfigAppStatusSource.KEY_MAINTENANCE_MODE to "full_block",
                RemoteConfigAppStatusSource.KEY_MAINTENANCE_MESSAGE to "down for migration",
            ),
        )

        val status = RemoteConfigAppStatusSource(gateway).fetch()

        assertEquals(42L, status?.minSupportedVersionCode)
        assertEquals(MaintenanceSeverity.FULL_BLOCK, status?.maintenance)
        assertEquals("down for migration", status?.maintenanceMessage)
        assertEquals(FETCHED_AT, status?.fetchedAt)
    }

    @Test
    fun `an unrecognised maintenance_mode fails open to OFF`() = runTest {
        val gateway = FakeGateway(
            lastFetchAt = FETCHED_AT,
            values = mapOf(RemoteConfigAppStatusSource.KEY_MAINTENANCE_MODE to "definitely_a_typo"),
        )

        val status = RemoteConfigAppStatusSource(gateway).fetch()

        assertEquals(MaintenanceSeverity.OFF, status?.maintenance)
    }

    @Test
    fun `a missing min version code defaults to zero which blocks nothing`() = runTest {
        val gateway = FakeGateway(lastFetchAt = FETCHED_AT, values = emptyMap())

        val status = RemoteConfigAppStatusSource(gateway).fetch()

        assertEquals(0L, status?.minSupportedVersionCode)
    }

    @Test
    fun `a blank maintenance message reads as no message not an empty string`() = runTest {
        val gateway = FakeGateway(
            lastFetchAt = FETCHED_AT,
            values = mapOf(RemoteConfigAppStatusSource.KEY_MAINTENANCE_MESSAGE to "   "),
        )

        val status = RemoteConfigAppStatusSource(gateway).fetch()

        assertNull(status?.maintenanceMessage)
    }

    private class FakeGateway(
        override val lastFetchAt: Instant?,
        private val values: Map<String, Any> = emptyMap(),
    ) : FirebaseRemoteConfigGateway {
        override suspend fun fetchAndActivate(): Boolean = true
        override fun long(key: String): Long? = values[key] as? Long
        override fun string(key: String): String? = values[key] as? String
    }
}
