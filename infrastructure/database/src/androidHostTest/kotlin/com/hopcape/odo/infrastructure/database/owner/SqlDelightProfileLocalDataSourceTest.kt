package com.hopcape.odo.infrastructure.database.owner

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import com.hopcape.odo.infrastructure.database.sync.SyncStatus
import com.hopcape.odo.core.domain.owner.model.OwnerEmail
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.owner.model.OwnerName
import com.hopcape.odo.core.domain.owner.model.OwnerProfile
import com.hopcape.odo.core.domain.owner.model.PhoneNumber
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
 * SQL behaviour for [SqlDelightProfileLocalDataSource] — including the query
 * [ProfileCityProvider] shares with it. Error mapping and sync scheduling live in
 * [OwnerProfileRepositoryImplTest] instead, against a fake port.
 */
class SqlDelightProfileLocalDataSourceTest {

    private val ownerId = OwnerId("owner-1")
    private val completedAt = Instant.parse("2026-07-30T10:15:00Z")
    private val phone = PhoneNumber.of("+919812345678").getOrNull()!!

    private lateinit var driver: JdbcSqliteDriver

    private fun newDb(): OdoDatabase {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        OdoDatabase.Schema.create(driver)
        return OdoDatabase(driver)
    }

    private fun local(db: OdoDatabase) = SqlDelightProfileLocalDataSource(database = db, dispatcher = Dispatchers.Unconfined)

    private data class Tombstone(val deletedAt: String?, val syncStatus: String)

    /** Reads a row past the `deleted_at IS NULL` filter every generated query applies. */
    private fun tombstoneOf(id: String): Tombstone? = driver.executeQuery(
        identifier = null,
        sql = "SELECT deleted_at, sync_status FROM profiles WHERE id = ?",
        mapper = { cursor ->
            QueryResult.Value(
                if (cursor.next().value) Tombstone(cursor.getString(0), cursor.getString(1)!!) else null,
            )
        },
        parameters = 1,
    ) { bindString(0, id) }.value

    private fun profile(
        name: String = "Rahul",
    ): OwnerProfile = OwnerProfile.new(
        id = ownerId,
        name = OwnerName.of(name).getOrNull()!!,
    )

    @Test
    fun save_thenObserve_readsBackWithPendingSync() = runTest {
        val db = newDb()
        val local = local(db)

        local.save(profile())

        val stored = local.observe().first()
        assertNotNull(stored)
        assertEquals("Rahul", stored.name?.value)

        val row = db.profileQueries.selectProfileById(ownerId.value).executeAsOne()
        assertEquals(SyncStatus.PENDING.name, row.sync_status)
        assertNull(row.remote_version)
    }

    @Test
    fun observe_beforeAnyProfile_emitsNull() = runTest {
        assertNull(local(newDb()).observe().first())
    }

    @Test
    fun save_twice_upsertsInsteadOfDuplicating() = runTest {
        val db = newDb()
        val local = local(db)

        local.save(profile())
        local.save(profile(name = "Rahul Sharma"))

        assertEquals("Rahul Sharma", local.observe().first()?.name?.value)
        // Keyed on the owner id, so the second save edits the same row.
        assertEquals(1, db.profileQueries.selectProfile().executeAsList().size)
    }

    @Test
    fun save_preservesCreatedAtAcrossEdits() = runTest {
        val db = newDb()
        val local = local(db)

        local.save(profile())
        val createdAt = db.profileQueries.selectProfileById(ownerId.value).executeAsOne().created_at

        local.save(profile())
        val afterEdit = db.profileQueries.selectProfileById(ownerId.value).executeAsOne()

        // The ignored insert is what protects this: the first write owns the creation
        // time, and only updated_at moves on an edit.
        assertEquals(createdAt, afterEdit.created_at)
    }

    @Test
    fun save_persistsOnboardingCompletion() = runTest {
        val db = newDb()
        val local = local(db)

        local.save(profile().completeOnboarding(completedAt))

        val stored = local.observe().first()
        assertEquals(completedAt, stored?.onboardingCompletedAt)
        assertTrue(stored?.hasCompletedOnboarding ?: false)
    }

    @Test
    fun observe_readsBackASignupShapedRowWithNoAnswersYet() = runTest {
        val db = newDb()
        // A row as the server's signup trigger would create it: no name yet.
        db.profileQueries.insertProfile(
            id = ownerId.value,
            fullName = null,
            onboardingCompletedAt = null,
            city = null,
            email = null,
            avatarPath = null,
            sharesPrices = 1,
            now = completedAt.toString(),
            syncStatus = SyncStatus.SYNCED.name,
            phone = null,
        )

        val stored = local(db).observe().first()
        assertNotNull(stored)
        assertNull(stored.name)
    }

    /* ---------------------------- the owner's phone number ---------------------------- */

    @Test
    fun recordPhone_writesTheNumberOntoTheProfileAndLeavesItPending() = runTest {
        val db = newDb()
        val local = local(db)
        local.save(profile())
        db.profileQueries.markSynced(remoteVersion = "v1", id = ownerId.value)

        local.recordPhone(ownerId, phone)

        assertEquals(phone.value, local.observe().first()?.phone?.value)
        // PENDING is the whole point: the next push is what puts the number on the server,
        // which has no other way of learning it after the account was made.
        assertEquals(SyncStatus.PENDING.name, tombstoneOf(ownerId.value)?.syncStatus)
    }

    @Test
    fun recordPhone_createsARowWhenSetupHasNotRunYet() = runTest {
        val db = newDb()
        val local = local(db)

        // Signed in before finishing setup: there is nothing to attach the number to yet.
        local.recordPhone(ownerId, phone)

        val stored = local.observe().first()
        assertEquals(phone.value, stored?.phone?.value)
        // And the row it made must not read as a finished setup, or the app opens on Home
        // with no car.
        assertNull(stored?.name)
        assertTrue(stored?.hasCompletedOnboarding == false)
    }

    @Test
    fun recordPhone_leavesAnAlreadyCorrectRowAlone() = runTest {
        val db = newDb()
        val local = local(db)
        local.save(profile())
        local.recordPhone(ownerId, phone)
        db.profileQueries.markSynced(remoteVersion = "v1", id = ownerId.value)

        // Every launch calls this. Marking a correct row dirty would ask the server to accept
        // a change that isn't one, on every relaunch, forever.
        local.recordPhone(ownerId, phone)

        assertEquals(SyncStatus.SYNCED.name, tombstoneOf(ownerId.value)?.syncStatus)
    }

    @Test
    fun save_doesNotWipeTheNumberItWasNeverTold() = runTest {
        val db = newDb()
        val local = local(db)
        local.recordPhone(ownerId, phone)

        // Every screen that edits a profile builds one from what it asked for, and none of
        // them ask for the phone. A plain assignment here would undo the sign-in.
        local.save(profile(name = "Rahul Sharma"))

        val stored = local.observe().first()
        assertEquals("Rahul Sharma", stored?.name?.value)
        assertEquals(phone.value, stored?.phone?.value)
    }

    /**
     * An install that predates the column gets it from 5.sqm, not from Profile.sq — a `.sq`
     * alone would leave that phone querying a column it never created.
     *
     * The table is written out as it stood at version 5 rather than created from the schema,
     * because the schema already has the column and would prove nothing.
     */
    @Test
    fun anExistingInstallGetsTheColumnFromTheMigration() = runTest {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.execute(
            identifier = null,
            sql = """
                CREATE TABLE profiles (
                    id TEXT NOT NULL PRIMARY KEY,
                    full_name TEXT,
                    onboarding_completed_at TEXT,
                    city TEXT,
                    email TEXT,
                    avatar_path TEXT,
                    shares_prices INTEGER NOT NULL DEFAULT 1,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL,
                    deleted_at TEXT,
                    remote_version TEXT,
                    sync_status TEXT NOT NULL DEFAULT 'PENDING'
                )
            """.trimIndent(),
            parameters = 0,
        )

        // A real database at version 5 has this too — 4.sqm created it — and the migration
        // that folds the old balances into `purchase_claims` reads it. Without it here the
        // chain below fails on a table the fixture forgot rather than on anything real.
        driver.execute(
            identifier = null,
            sql = """
                CREATE TABLE record_export_credits (
                    id        INTEGER NOT NULL PRIMARY KEY,
                    remaining INTEGER NOT NULL
                )
            """.trimIndent(),
            parameters = 0,
        )

        // All the way to current, not just to 6. The generated queries select every column
        // the schema has, so stopping at the migration under test leaves them asking for
        // columns a later one adds — which is a failure about the newest column rather
        // than about the one this test is here for.
        OdoDatabase.Schema.migrate(driver, oldVersion = 5L, newVersion = OdoDatabase.Schema.version).await()

        val db = OdoDatabase(driver)
        local(db).recordPhone(ownerId, phone)
        assertEquals(phone.value, local(db).observe().first()?.phone?.value)
    }

    @Test
    fun save_persistsTheEditableDetails() = runTest {
        val db = newDb()
        val local = local(db)
        val edited = profile()
            .withName(OwnerName.of("Rahul Deshmukh").getOrNull()!!)
            .withEmail(OwnerEmail.of("rahul@example.com").getOrNull())
            .withCity("Pune")
            .withAvatar("avatars/owner-1.jpg")

        local.save(edited)

        val stored = local.observe().first()
        assertEquals("Rahul Deshmukh", stored?.name?.value)
        assertEquals("rahul@example.com", stored?.email?.value)
        assertEquals("Pune", stored?.city)
        assertEquals("avatars/owner-1.jpg", stored?.avatarPath)
    }

    @Test
    fun save_persistsPriceSharingOnBothWrites() = runTest {
        val db = newDb()
        val local = local(db)

        // The first save inserts, the second updates — the switch has to survive both, and
        // only the second is what an owner turning it off actually runs.
        local.save(profile().withPriceSharing(false))
        assertEquals(false, local.observe().first()?.sharesPricesAnonymously)

        local.save(profile().withPriceSharing(true))
        assertEquals(true, local.observe().first()?.sharesPricesAnonymously)
    }

    @Test
    fun save_clearedEmailAndAvatar_readBackAsAbsent() = runTest {
        val db = newDb()
        val local = local(db)
        val withDetails = profile()
            .withEmail(OwnerEmail.of("rahul@example.com").getOrNull())
            .withAvatar("avatars/owner-1.jpg")
        local.save(withDetails)

        local.save(withDetails.withEmail(null).withAvatar(null))

        val stored = local.observe().first()
        assertNull(stored?.email)
        assertNull(stored?.avatarPath)
    }

    @Test
    fun softDeleteAll_hidesTheProfileAndLeavesATombstone() = runTest {
        val db = newDb()
        val local = local(db)
        local.save(profile())

        local.softDeleteAll()

        // Gone as far as every reader is concerned — which is what sends the app back to
        // first-run setup — while the row survives for the sync engine to push.
        assertNull(local.observe().first())
        assertNull(db.profileQueries.selectProfileById(ownerId.value).executeAsOneOrNull())
        val row = assertNotNull(tombstoneOf(ownerId.value), "the profile row should still exist")
        assertNotNull(row.deletedAt)
        assertEquals(SyncStatus.PENDING.name, row.syncStatus)
    }

    @Test
    fun softDeleteAll_withNothingStored_doesNotThrow() = runTest {
        local(newDb()).softDeleteAll()
    }

    /* ------------------------- shared with ProfileCityProvider ------------------------- */

    @Test
    fun currentCity_isNullUntilTheOwnerSetsOne() = runTest {
        val db = newDb()
        local(db).save(profile())

        assertNull(local(db).currentCity())
    }

    @Test
    fun currentCity_isReadBackOnceSet() = runTest {
        val db = newDb()
        local(db).save(profile().withCity("Pune"))

        assertEquals("Pune", local(db).currentCity())
    }

    @Test
    fun currentCity_withNoProfileAtAll_isNull() = runTest {
        assertNull(local(newDb()).currentCity())
    }
}
