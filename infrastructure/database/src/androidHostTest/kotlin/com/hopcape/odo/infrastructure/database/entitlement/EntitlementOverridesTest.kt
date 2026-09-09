package com.hopcape.odo.infrastructure.database.entitlement

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hopcape.odo.core.domain.entitlement.EntitlementOverrides
import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What the local override mirror is allowed to say.
 *
 * An override is a decision a person made, and both halves of it matter: whether it grants,
 * and whether it is still in force. The expiry was written to the row and read by nobody, so
 * a comp given an end date stayed in force forever.
 */
class EntitlementOverridesTest {

    @Test
    fun `a grant with no expiry is in force`() = runTest {
        val db = newDb()
        db.seed(granted = true, expiresAt = null)

        assertEquals(mapOf(PLAN to true), overrides(db).observe().first())
    }

    @Test
    fun `a revoke is a decision and not an absence`() = runTest {
        val db = newDb()
        db.seed(granted = false, expiresAt = null)

        assertEquals(mapOf(PLAN to false), overrides(db).observe().first())
    }

    @Test
    fun `a grant that has not expired yet is in force`() = runTest {
        val db = newDb()
        db.seed(granted = true, expiresAt = FAR_FUTURE)

        assertEquals(mapOf(PLAN to true), overrides(db).observe().first())
    }

    /** The bug: the column was written and never read. */
    @Test
    fun `an expired grant decides nothing`() = runTest {
        val db = newDb()
        db.seed(granted = true, expiresAt = FAR_PAST)

        assertTrue(
            overrides(db).observe().first().isEmpty(),
            "an override past its expiry is nobody's decision",
        )
    }

    /**
     * An expired revoke lapses the same way. Support cut somebody off until a date, and the
     * date is the end of it — leaving it in force would keep a paying owner locked out.
     */
    @Test
    fun `an expired revoke decides nothing`() = runTest {
        val db = newDb()
        db.seed(granted = false, expiresAt = FAR_PAST)

        assertTrue(overrides(db).observe().first().isEmpty())
    }

    @Test
    fun `another owner's override is not read`() = runTest {
        val db = newDb()
        db.seed(granted = true, expiresAt = null, ownerId = "someone-else")

        assertTrue(overrides(db).observe().first().isEmpty())
    }

    /* ------------------------------ Fixtures ------------------------------ */

    private fun newDb(): OdoDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        OdoDatabase.Schema.create(driver)
        return OdoDatabase(driver)
    }

    private fun overrides(db: OdoDatabase) = EntitlementOverridesImpl(
        database = db,
        ownerId = { OWNER },
        dispatcher = Dispatchers.Unconfined,
    )

    private fun OdoDatabase.seed(
        granted: Boolean,
        expiresAt: String?,
        ownerId: String = OWNER,
        feature: String = PLAN,
    ) = entitlementOverrideQueries.insertFromRemote(
        owner_id = ownerId,
        feature = feature,
        granted = if (granted) 1L else 0L,
        expires_at = expiresAt,
        updated_at = GRANTED_AT,
    )

    private companion object {
        const val OWNER = "11111111-1111-1111-1111-111111111111"
        const val PLAN = EntitlementOverrides.PLAN
        const val GRANTED_AT = "2026-01-01T00:00:00Z"
        const val FAR_PAST = "2020-01-01T00:00:00Z"
        const val FAR_FUTURE = "2099-01-01T00:00:00Z"
    }
}
