package com.hopcape.odo.core.data.owner

import com.hopcape.odo.core.data.sync.silentSyncTelemetry
import com.hopcape.odo.core.data.owner.FakeProfileRemoteDataSource
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hopcape.crashreporting.api.CrashRecorder
import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.api.Logger
import com.hopcape.logging.api.TraceContext
import com.hopcape.odo.core.data.db.OdoDatabase
import com.hopcape.odo.core.data.observability.DataTelemetry
import com.hopcape.odo.core.data.sync.SyncRunner
import com.hopcape.odo.core.data.sync.SyncStatus
import com.hopcape.odo.core.domain.owner.model.OnboardingGoal
import com.hopcape.odo.core.domain.owner.model.OwnerEmail
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.owner.model.OwnerName
import com.hopcape.odo.core.domain.owner.model.OwnerProfile
import com.hopcape.odo.core.sync.SyncEntity
import com.hopcape.odo.core.sync.SyncReason
import com.hopcape.odo.core.sync.SyncScheduler
import com.hopcape.performance.api.PerformanceTracer
import com.hopcape.performance.api.Span
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

    private lateinit var driver: JdbcSqliteDriver

    private fun newDb(): OdoDatabase {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        OdoDatabase.Schema.create(driver)
        return OdoDatabase(driver)
    }

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

    /** Records what the repository asked the scheduler for. */
    private class RecordingScheduler : SyncScheduler {
        val requested = mutableListOf<SyncReason>()
        override fun scheduleStartupSync() = Unit
        override fun requestSync(reason: SyncReason) { requested += reason }
    }

    private fun repo(db: OdoDatabase, scheduler: SyncScheduler = RecordingScheduler()) =
        OwnerProfileRepositoryImpl(
            local = SqlDelightProfileLocalDataSource(database = db, dispatcher = Dispatchers.Unconfined),
            telemetry = DataTelemetry(logger = NoopLogger, tracer = NoopTracer, crash = NoopCrash),
            scheduler = scheduler,
            runner = SyncRunner(
                entity = SyncEntity.PROFILES,
                table = ProfileSyncTable(
                    database = db,
                    remote = FakeProfileRemoteDataSource(),
                    ownerId = { null },
                ),
                database = db,
                telemetry = silentSyncTelemetry(),
            ),
        )

    private object NoopLogger : Logger {
        override fun log(
            level: LogLevel,
            tag: String,
            event: String,
            traceContext: TraceContext?,
            fields: Map<String, Any?>,
        ) = Unit
        override fun flush() = Unit
    }

    private class FakeSpan(
        override val spanId: String,
        override val traceId: String,
        override val parentSpanId: String?,
        override val name: String,
    ) : Span {
        override fun setAttribute(key: String, value: Any?): Span = this
    }

    private object NoopTracer : PerformanceTracer {
        override fun startSpan(name: String, traceId: String, parentSpanId: String?): Span =
            FakeSpan("span", traceId, parentSpanId, name)
        override fun endSpan(span: Span) = Unit
        override fun flush() = Unit
    }

    private object NoopCrash : CrashRecorder {
        override fun recordNonFatal(throwable: Throwable, customKeys: Map<String, Any?>) = Unit
        override fun leaveBreadcrumb(tag: String, message: String) = Unit
        override fun setCustomKey(key: String, value: Any?) = Unit
        override fun setUserId(userId: String?) = Unit
    }

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
            email = null,
            avatarPath = null,
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
            email = null,
            avatarPath = null,
            now = completedAt.toString(),
            syncStatus = SyncStatus.SYNCED.name,
        )

        val stored = repo(db).observe().first()
        assertEquals("Rahul", stored?.name?.value)
        assertNull(stored?.goal)
    }

    @Test
    fun save_persistsTheEditableDetails() = runTest {
        val repo = repo(newDb())
        val edited = profile()
            .withName(OwnerName.of("Rahul Deshmukh").getOrNull()!!)
            .withEmail(OwnerEmail.of("rahul@example.com").getOrNull())
            .withCity("Pune")
            .withAvatar("avatars/owner-1.jpg")

        assertTrue(repo.save(edited).isRight())

        val stored = repo.observe().first()
        assertEquals("Rahul Deshmukh", stored?.name?.value)
        assertEquals("rahul@example.com", stored?.email?.value)
        assertEquals("Pune", stored?.city)
        assertEquals("avatars/owner-1.jpg", stored?.avatarPath)
    }

    @Test
    fun save_clearedEmailAndAvatar_readBackAsAbsent() = runTest {
        val repo = repo(newDb())
        val withDetails = profile()
            .withEmail(OwnerEmail.of("rahul@example.com").getOrNull())
            .withAvatar("avatars/owner-1.jpg")
        assertTrue(repo.save(withDetails).isRight())

        assertTrue(repo.save(withDetails.withEmail(null).withAvatar(null)).isRight())

        val stored = repo.observe().first()
        assertNull(stored?.email)
        assertNull(stored?.avatarPath)
    }

    @Test
    fun save_asksForASync() = runTest {
        val scheduler = RecordingScheduler()

        assertTrue(repo(newDb(), scheduler).save(profile()).isRight())

        assertEquals(listOf(SyncReason.LocalWrite), scheduler.requested)
    }

    @Test
    fun delete_hidesTheProfileAndLeavesATombstone() = runTest {
        val db = newDb()
        val repo = repo(db)
        assertTrue(repo.save(profile()).isRight())

        assertTrue(repo.delete().isRight())

        // Gone as far as every reader is concerned — which is what sends the app back to
        // first-run setup — while the row survives for the sync engine to push.
        assertNull(repo.observe().first())
        assertNull(db.profileQueries.selectProfileById(ownerId.value).executeAsOneOrNull())
        val row = assertNotNull(tombstoneOf(ownerId.value), "the profile row should still exist")
        assertNotNull(row.deletedAt)
        assertEquals(SyncStatus.PENDING.name, row.syncStatus)
    }

    @Test
    fun delete_withNothingStored_succeeds() = runTest {
        assertTrue(repo(newDb()).delete().isRight())
    }
}
