package com.hopcape.odo.core.data.document

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.data.observability.DataTelemetry
import com.hopcape.odo.core.data.sync.SyncRunner
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.document.model.Document
import com.hopcape.odo.core.domain.document.model.DocumentId
import com.hopcape.odo.core.domain.document.repository.DocumentRepository
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.sync.SyncEntity
import com.hopcape.odo.core.sync.SyncReason
import com.hopcape.odo.core.sync.SyncScheduler
import com.hopcape.odo.core.sync.Syncable
import com.hopcape.odo.core.sync.Synchronizer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

/**
 * [DocumentRepository] over a [DocumentLocalDataSource] — offline-first; the local store
 * is the source of truth. This layer owns what an operation means: mapping storage
 * failures to [DomainError], telemetry, and asking the scheduler for a sync after a
 * committed write. How the rows are read and written lives behind [local].
 *
 * This repository stores the *row* that describes a document. The file itself belongs to
 * the vault feature's `DocumentFileStore`, so deleting a row never touches bytes — the use
 * case that owns both does that, in the order it decides.
 */
internal class DocumentRepositoryImpl(
    private val local: DocumentLocalDataSource,
    private val telemetry: DataTelemetry,
    private val scheduler: SyncScheduler,
    private val runner: SyncRunner<DocumentDto>,
) : DocumentRepository, Syncable {

    override val entity: SyncEntity = SyncEntity.DOCUMENTS

    override suspend fun syncWith(synchronizer: Synchronizer): Boolean = runner.run(synchronizer)

    /**
     * Tell the scheduler there is something worth pushing. Called after a write has
     * committed — never before, or a sync would go looking for a row that is not there yet
     * — and only when it succeeded.
     *
     * Failure to *schedule* never fails the write: the data is safely local and `PENDING`,
     * and the next trigger will carry it.
     */
    private suspend fun requestSync(operation: String, id: String) {
        try {
            scheduler.requestSync(SyncReason.LocalWrite)
        } catch (e: Exception) {
            telemetry.crashed(DataTelemetry.DOCUMENT, "$operation.schedule", e, id)
        }
    }

    override fun observe(carId: CarId): Flow<List<Document>> =
        local.observeByCar(carId).reportingFailures(OP_OBSERVE_CAR, carId.value, empty = emptyList())

    override fun observe(id: DocumentId): Flow<Document?> =
        local.observeById(id).reportingFailures(OP_OBSERVE_ONE, id.value, empty = null)

    override suspend fun add(document: Document): Either<DomainError, Document> =
        telemetry.span(DataTelemetry.DOCUMENT, OP_ADD, document.id.value) {
            try {
                local.insert(document)
                requestSync(OP_ADD, document.id.value)
                document.right()
            } catch (e: Exception) {
                telemetry.crashed(DataTelemetry.DOCUMENT, OP_ADD, e, document.id.value)
                DomainError.PersistenceFailure(e.message).left()
            }
        }

    override suspend fun update(document: Document): Either<DomainError, Document> =
        telemetry.span(DataTelemetry.DOCUMENT, OP_UPDATE, document.id.value) {
            try {
                if (local.update(document)) {
                    requestSync(OP_UPDATE, document.id.value)
                    document.right()
                } else {
                    telemetry.failed(DataTelemetry.DOCUMENT, OP_UPDATE, DomainError.DocumentNotFound, document.id.value)
                    DomainError.DocumentNotFound.left()
                }
            } catch (e: Exception) {
                telemetry.crashed(DataTelemetry.DOCUMENT, OP_UPDATE, e, document.id.value)
                DomainError.PersistenceFailure(e.message).left()
            }
        }

    override suspend fun softDelete(id: DocumentId): Either<DomainError, Unit> =
        telemetry.span(DataTelemetry.DOCUMENT, OP_DELETE, id.value) {
            try {
                local.softDelete(id)
                requestSync(OP_DELETE, id.value)
                Unit.right()
            } catch (e: Exception) {
                telemetry.crashed(DataTelemetry.DOCUMENT, OP_DELETE, e, id.value)
                DomainError.PersistenceFailure(e.message).left()
            }
        }

    override suspend fun countForOwner(ownerId: OwnerId): Int =
        telemetry.span(DataTelemetry.DOCUMENT, OP_COUNT, ownerId.value) {
            try {
                local.countLiveForOwner(ownerId)
            } catch (e: Exception) {
                telemetry.crashed(DataTelemetry.DOCUMENT, OP_COUNT, e, ownerId.value)
                // Zero, not "too many": this count only gates the free-tier cap. Answering
                // high would reject a legitimate add with "limit reached", which is a lie
                // about why it failed. Answering zero lets the add proceed to the insert,
                // which fails on the same broken DB and says so honestly.
                0
            }
        }

    /**
     * A read that throws would otherwise tear down the collecting screen. The stream stays
     * alive on [empty] and the failure is reported, because a vault that quietly shows
     * nothing is a bug someone has to be able to see.
     */
    private fun <T> Flow<T>.reportingFailures(operation: String, id: String, empty: T): Flow<T> =
        catch { cause ->
            telemetry.crashed(DataTelemetry.DOCUMENT, operation, cause, id)
            emit(empty)
        }

    private companion object {
        const val OP_OBSERVE_CAR = "observeByCar"
        const val OP_OBSERVE_ONE = "observeById"
        const val OP_ADD = "add"
        const val OP_UPDATE = "update"
        const val OP_DELETE = "softDelete"
        const val OP_COUNT = "countForOwner"
    }
}
