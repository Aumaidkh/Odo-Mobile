package com.hopcape.odo.core.data.owner

import com.hopcape.odo.core.data.sync.SyncRunner
import com.hopcape.odo.core.sync.SyncEntity
import com.hopcape.odo.core.sync.Syncable
import com.hopcape.odo.core.sync.Synchronizer
import com.hopcape.odo.core.sync.observability.SyncTelemetry
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.data.db.OdoDatabase
import com.hopcape.odo.core.data.observability.DataTelemetry
import com.hopcape.odo.core.data.sync.SyncStatus
import com.hopcape.odo.core.domain.owner.model.OwnerProfile
import com.hopcape.odo.core.domain.owner.repository.OwnerProfileRepository
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.sync.SyncReason
import com.hopcape.odo.core.sync.SyncScheduler
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

/**
 * SQLDelight-backed [OwnerProfileRepository] — fully offline, like every repository here.
 * The local DB is the source of truth and rows are written `sync_status = PENDING` for the
 * sync engine that lands in M5.
 *
 * Timestamps are client-stamped (offline-first; the server reconciles on sync).
 */
internal class OwnerProfileRepositoryImpl(
    private val database: OdoDatabase,
    private val telemetry: DataTelemetry,
    private val scheduler: SyncScheduler,
    private val remote: ProfileRemoteDataSource,
    private val syncTelemetry: SyncTelemetry,
    private val ownerId: () -> String?,
    private val clock: Clock = Clock.System,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : OwnerProfileRepository, Syncable {

    private val queries get() = database.profileQueries

    override val entity: SyncEntity = SyncEntity.PROFILES

    private val runner = SyncRunner(
        entity = SyncEntity.PROFILES,
        table = ProfileSyncTable(database = database, remote = remote, ownerId = ownerId),
        database = database,
        telemetry = syncTelemetry,
        clock = clock,
    )

    override suspend fun syncWith(synchronizer: Synchronizer): Boolean = runner.run(synchronizer)

    /**
     * Tell the scheduler there is something worth pushing. Called after a write has
     * committed, never before, and only when it succeeded.
     *
     * Failure to *schedule* never fails the write: the data is safely local and `PENDING`,
     * and the next trigger will carry it.
     */
    private suspend fun requestSync(operation: String, id: String?) {
        try {
            scheduler.requestSync(SyncReason.LocalWrite)
        } catch (e: Exception) {
            telemetry.crashed(DataTelemetry.PROFILE, "$operation.schedule", e, id)
        }
    }

    override suspend fun save(profile: OwnerProfile): Either<DomainError, OwnerProfile> =
        telemetry.span(DataTelemetry.PROFILE, OP_SAVE, profile.id.value) {
            try {
                val now = clock.now().toString()
                val fullName = profile.name?.value
                val goal = profile.goal?.name
                val completedAt = profile.onboardingCompletedAt?.toString()
                val email = profile.email?.value
                // Insert-then-update rather than an UPSERT: `ON CONFLICT ... DO UPDATE`
                // needs SQLite 3.24 and minSdk 26 ships 3.18. The insert is ignored when
                // the row already exists (which is what preserves created_at), so on an
                // edit the UPDATE is what writes. One transaction, so no reader sees the
                // half-written state.
                database.transaction {
                    queries.insertProfile(
                        id = profile.id.value,
                        fullName = fullName,
                        onboardingGoal = goal,
                        onboardingCompletedAt = completedAt,
                        city = profile.city,
                        email = email,
                        avatarPath = profile.avatarPath,
                        now = now,
                        syncStatus = SyncStatus.PENDING.name,
                    )
                    queries.updateProfile(
                        fullName = fullName,
                        onboardingGoal = goal,
                        onboardingCompletedAt = completedAt,
                        city = profile.city,
                        email = email,
                        avatarPath = profile.avatarPath,
                        updatedAt = now,
                        syncStatus = SyncStatus.PENDING.name,
                        id = profile.id.value,
                    )
                }
                requestSync(OP_SAVE, profile.id.value)
                profile.right()
            } catch (e: Exception) {
                telemetry.crashed(DataTelemetry.PROFILE, OP_SAVE, e, profile.id.value)
                DomainError.PersistenceFailure(e.message).left()
            }
        }

    override fun observe(): Flow<OwnerProfile?> =
        queries.selectProfile()
            .asFlow()
            .mapToOneOrNull(dispatcher)
            .map { row -> row?.toDomain() }
            .catch { e ->
                telemetry.crashed(DataTelemetry.PROFILE, OP_OBSERVE, e)
                emit(null)
            }

    override suspend fun delete(): Either<DomainError, Unit> =
        telemetry.span(DataTelemetry.PROFILE, OP_DELETE) {
            try {
                val now = clock.now().toString()
                queries.softDeleteProfiles(deletedAt = now, syncStatus = SyncStatus.PENDING.name)
                requestSync(OP_DELETE, null)
                Unit.right()
            } catch (e: Exception) {
                telemetry.crashed(DataTelemetry.PROFILE, OP_DELETE, e)
                DomainError.PersistenceFailure(e.message).left()
            }
        }

    private companion object {
        const val OP_SAVE = "save"
        const val OP_OBSERVE = "observe"
        const val OP_DELETE = "delete"
    }
}
