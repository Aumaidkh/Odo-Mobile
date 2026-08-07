package com.hopcape.odo.core.data.owner

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.data.observability.DataTelemetry
import com.hopcape.odo.core.domain.owner.model.OwnerProfile
import com.hopcape.odo.core.domain.owner.repository.OwnerProfileRepository
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.sync.SyncReason
import com.hopcape.odo.core.sync.SyncScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

/**
 * [OwnerProfileRepository] over a [ProfileLocalDataSource] — fully offline, like every
 * repository here. This layer owns what an operation means: mapping storage failures to
 * [DomainError], telemetry, and asking the scheduler for a sync after a committed write.
 * How the row is read and written lives behind [local].
 *
 * Not [Syncable][com.hopcape.odo.core.sync.Syncable] itself — the SQLDelight-backed
 * `SyncRunner` and `ProfileSyncTable` it would need live in `:infrastructure:database`,
 * which this module cannot depend on without a cycle. `ProfileSyncable`, in that module,
 * wraps the same runner this class used to hold.
 */
internal class OwnerProfileRepositoryImpl(
    private val local: ProfileLocalDataSource,
    private val telemetry: DataTelemetry,
    private val scheduler: SyncScheduler,
) : OwnerProfileRepository {

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
                local.save(profile)
                requestSync(OP_SAVE, profile.id.value)
                profile.right()
            } catch (e: Exception) {
                telemetry.crashed(DataTelemetry.PROFILE, OP_SAVE, e, profile.id.value)
                DomainError.PersistenceFailure(e.message).left()
            }
        }

    override fun observe(): Flow<OwnerProfile?> =
        local.observe().catch { e ->
            telemetry.crashed(DataTelemetry.PROFILE, OP_OBSERVE, e)
            emit(null)
        }

    override suspend fun delete(): Either<DomainError, Unit> =
        telemetry.span(DataTelemetry.PROFILE, OP_DELETE) {
            try {
                local.softDeleteAll()
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
