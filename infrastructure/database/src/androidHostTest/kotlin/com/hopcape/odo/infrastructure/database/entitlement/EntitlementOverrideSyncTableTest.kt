package com.hopcape.odo.infrastructure.database.entitlement

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hopcape.odo.core.data.entitlement.EntitlementOverrideDto
import com.hopcape.odo.core.data.entitlement.EntitlementOverrideRemoteDataSource
import com.hopcape.odo.core.domain.entitlement.EntitlementOverrides
import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * What a pulled override row does to the local mirror.
 *
 * The withdrawal case is why this exists. Support pressed Clear, the server row went away, and
 * a delta pull has no way to mention a row that is not there — so the device kept the grant
 * forever. A withdrawal is a tombstone now and the mirror has to act on it.
 */
class EntitlementOverrideSyncTableTest {

    @Test
    fun `a granted override lands in the mirror`() = runTest {
        val db = newDb()

        table(db).applyRemote(override(granted = true))

        assertEquals(mapOf(PLAN to true), overrides(db).observe().first())
    }

    @Test
    fun `a later row replaces the one before it`() = runTest {
        val db = newDb()
        val syncTable = table(db)

        syncTable.applyRemote(override(granted = true))
        syncTable.applyRemote(override(granted = false, grantedAt = LATER))

        assertEquals(mapOf(PLAN to false), overrides(db).observe().first())
    }

    /** The bug: Clear in the admin panel never reached the device. */
    @Test
    fun `a withdrawn override is dropped from the mirror`() = runTest {
        val db = newDb()
        val syncTable = table(db)
        syncTable.applyRemote(override(granted = true))

        syncTable.applyRemote(override(granted = true, deletedAt = LATER, grantedAt = LATER))

        assertEquals(
            emptyMap(),
            overrides(db).observe().first(),
            "a cleared override must stop deciding anything",
        )
        assertNull(syncTable.localState("$OWNER|$PLAN"), "the row itself must be gone")
    }

    /**
     * A tombstone for a row this device never had is not an error — a reinstall pulls the
     * whole history, withdrawals included.
     */
    @Test
    fun `a withdrawal for a row we never had is harmless`() = runTest {
        val db = newDb()
        val syncTable = table(db)

        syncTable.applyRemote(override(granted = true, deletedAt = LATER))

        assertTrue(overrides(db).observe().first().isEmpty())
        assertNull(syncTable.localState("$OWNER|$PLAN"))
    }

    /** Re-granting after a withdrawal has to work, or a mistaken Clear is permanent. */
    @Test
    fun `an override granted again after a withdrawal comes back`() = runTest {
        val db = newDb()
        val syncTable = table(db)
        syncTable.applyRemote(override(granted = true))
        syncTable.applyRemote(override(granted = true, deletedAt = LATER, grantedAt = LATER))

        syncTable.applyRemote(override(granted = true, grantedAt = LATEST))

        assertEquals(mapOf(PLAN to true), overrides(db).observe().first())
        assertNotNull(syncTable.localState("$OWNER|$PLAN"))
    }

    /* ------------------------------ Fixtures ------------------------------ */

    private fun newDb(): OdoDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        OdoDatabase.Schema.create(driver)
        return OdoDatabase(driver)
    }

    private fun table(db: OdoDatabase) =
        EntitlementOverrideSyncTable(database = db, remote = NoRemote)

    private fun overrides(db: OdoDatabase) = EntitlementOverridesImpl(
        database = db,
        ownerId = { OWNER },
        dispatcher = Dispatchers.Unconfined,
    )

    private fun override(
        granted: Boolean,
        deletedAt: String? = null,
        grantedAt: String = GRANTED_AT,
    ) = EntitlementOverrideDto(
        ownerId = OWNER,
        feature = PLAN,
        granted = granted,
        expiresAt = null,
        deletedAt = deletedAt,
        grantedAt = grantedAt,
    )

    private object NoRemote : EntitlementOverrideRemoteDataSource {
        override suspend fun fetchSince(since: Instant?): List<EntitlementOverrideDto> = emptyList()
    }

    private companion object {
        const val OWNER = "11111111-1111-1111-1111-111111111111"
        const val PLAN = EntitlementOverrides.PLAN
        const val GRANTED_AT = "2026-01-01T00:00:00Z"
        const val LATER = "2026-02-01T00:00:00Z"
        const val LATEST = "2026-03-01T00:00:00Z"
    }
}
