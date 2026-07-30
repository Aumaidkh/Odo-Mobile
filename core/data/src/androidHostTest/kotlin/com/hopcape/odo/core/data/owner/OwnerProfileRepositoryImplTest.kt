package com.hopcape.odo.core.data.owner

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hopcape.odo.core.data.db.OdoDatabase
import com.hopcape.odo.core.data.sync.SyncStatus
import com.hopcape.odo.core.domain.owner.model.OnboardingGoal
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

class OwnerProfileRepositoryImplTest {

    private val ownerId = OwnerId("owner-1")
    private val completedAt = Instant.parse("2026-07-30T10:15:00Z")

    private fun newDb(): OdoDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        OdoDatabase.Schema.create(driver)
        return OdoDatabase(driver)
    }

    private fun repo(db: OdoDatabase) =
        OwnerProfileRepositoryImpl(database = db, dispatcher = Dispatchers.Unconfined)

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
        val repo = repo(db)

        assertTrue(repo.save(profile()).isRight())

        val stored = repo.observe().first()
        assertNotNull(stored)
        assertEquals("Rahul", stored.name?.value)
        assertEquals(OnboardingGoal.TRACK_COSTS, stored.goal)

        val row = db.profileQueries.selectProfileById(ownerId.value).executeAsOne()
        assertEquals(SyncStatus.PENDING.name, row.sync_status)
        assertNull(row.remote_version)
    }

    @Test
    fun observe_beforeAnyProfile_emitsNull() = runTest {
        assertNull(repo(newDb()).observe().first())
    }

    @Test
    fun save_twice_upsertsInsteadOfDuplicating() = runTest {
        val db = newDb()
        val repo = repo(db)

        assertTrue(repo.save(profile()).isRight())
        assertTrue(repo.save(profile(name = "Rahul Sharma")).isRight())

        assertEquals("Rahul Sharma", repo.observe().first()?.name?.value)
        // Keyed on the owner id, so the second save edits the same row.
        assertEquals(1, db.profileQueries.selectProfile().executeAsList().size)
    }

    @Test
    fun save_preservesCreatedAtAcrossEdits() = runTest {
        val db = newDb()
        val repo = repo(db)

        assertTrue(repo.save(profile()).isRight())
        val createdAt = db.profileQueries.selectProfileById(ownerId.value).executeAsOne().created_at

        assertTrue(repo.save(profile(goal = OnboardingGoal.SELL_SOON)).isRight())
        val afterEdit = db.profileQueries.selectProfileById(ownerId.value).executeAsOne()

        // The ignored insert is what protects this: the first write owns the creation
        // time, and only updated_at moves on an edit.
        assertEquals(createdAt, afterEdit.created_at)
        assertEquals(OnboardingGoal.SELL_SOON.name, afterEdit.onboarding_goal)
    }

    @Test
    fun save_persistsOnboardingCompletion() = runTest {
        val repo = repo(newDb())

        assertTrue(repo.save(profile().completeOnboarding(completedAt)).isRight())

        val stored = repo.observe().first()
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
            now = completedAt.toString(),
            syncStatus = SyncStatus.SYNCED.name,
        )

        val stored = repo(db).observe().first()
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
            now = completedAt.toString(),
            syncStatus = SyncStatus.SYNCED.name,
        )

        val stored = repo(db).observe().first()
        assertEquals("Rahul", stored?.name?.value)
        assertNull(stored?.goal)
    }
}
