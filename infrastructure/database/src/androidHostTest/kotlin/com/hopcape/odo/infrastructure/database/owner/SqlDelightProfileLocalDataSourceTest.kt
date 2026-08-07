package com.hopcape.odo.infrastructure.database.owner

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import com.hopcape.odo.infrastructure.database.sync.SyncStatus
import com.hopcape.odo.core.domain.owner.model.OnboardingGoal
import com.hopcape.odo.core.domain.owner.model.OwnerEmail
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.owner.model.OwnerName
import com.hopcape.odo.core.domain.owner.model.OwnerProfile
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
        goal: OnboardingGoal = OnboardingGoal.TRACK_COSTS,
    ): OwnerProfile = OwnerProfile.new(
        id = ownerId,
        name = OwnerName.of(name).getOrNull()!!,
        goal = goal,
    )

    @Test
    fun save_thenObserve_readsBackWithPendingSync() = runTest {
        val db = newDb()
        val local = local(db)

        local.save(profile())

        val stored = local.observe().first()
        assertNotNull(stored)
        assertEquals("Rahul", stored.name?.value)
        assertEquals(OnboardingGoal.TRACK_COSTS, stored.goal)

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

        local.save(profile(goal = OnboardingGoal.SELL_SOON))
        val afterEdit = db.profileQueries.selectProfileById(ownerId.value).executeAsOne()

        // The ignored insert is what protects this: the first write owns the creation
        // time, and only updated_at moves on an edit.
        assertEquals(createdAt, afterEdit.created_at)
        assertEquals(OnboardingGoal.SELL_SOON.name, afterEdit.onboarding_goal)
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
        // A row as the server's signup trigger would create it: no name, no goal.
        db.profileQueries.insertProfile(
            id = ownerId.value,
            fullName = null,
            onboardingGoal = null,
            onboardingCompletedAt = null,
            city = null,
            email = null,
            avatarPath = null,
            now = completedAt.toString(),
            syncStatus = SyncStatus.SYNCED.name,
        )

        val stored = local(db).observe().first()
        assertNotNull(stored)
        assertNull(stored.name)
        assertNull(stored.goal)
    }

    @Test
    fun observe_unknownStoredGoal_readsAsNotAnsweredRatherThanCrashing() = runTest {
        val db = newDb()
        // Written by a newer build, or corrupt. The goal only picks a landing surface,
        // so the profile stays usable without it.
        db.profileQueries.insertProfile(
            id = ownerId.value,
            fullName = "Rahul",
            onboardingGoal = "TIME_TRAVEL",
            onboardingCompletedAt = null,
            city = null,
            email = null,
            avatarPath = null,
            now = completedAt.toString(),
            syncStatus = SyncStatus.SYNCED.name,
        )

        val stored = local(db).observe().first()
        assertEquals("Rahul", stored?.name?.value)
        assertNull(stored?.goal)
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
