package com.hopcape.odo.core.data.document

import com.hopcape.odo.core.data.sync.BlobUploader
import com.hopcape.odo.core.data.sync.SyncRunner
import com.hopcape.odo.core.sync.SyncEntity
import com.hopcape.odo.core.sync.Syncable
import com.hopcape.odo.core.sync.Synchronizer
import com.hopcape.odo.core.sync.observability.SyncTelemetry
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.data.db.OdoDatabase
import com.hopcape.odo.core.data.observability.DataTelemetry
import com.hopcape.odo.core.data.sync.SyncStatus
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.document.model.Document
import com.hopcape.odo.core.domain.document.model.DocumentId
import com.hopcape.odo.core.domain.document.repository.DocumentRepository
import com.hopcape.odo.core.domain.owner.model.OwnerId
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
 * SQLDelight-backed [DocumentRepository] — offline-first. The local DB is the source of
 * truth; every write lands `sync_status = PENDING` for the engine that arrives in M5, and
 * nothing here waits on a network call.
 *
 * This repository stores the *row* that describes a document. The file itself belongs to
 * the vault feature's `DocumentFileStore`, so deleting a row never touches bytes — the use
 * case that owns both does that, in the order it decides.
 *
 * [remote] is held but not yet driven: pushing and pulling belong to `Syncable.syncWith`,
 * which lands with the engine. It is wired now so the seam exists before there is anything
 * behind it, rather than being retrofitted later.
 *
 * Timestamps are client-stamped (offline-first; the server reconciles on sync).
 */
internal class DocumentRepositoryImpl(
    private val database: OdoDatabase,
    private val telemetry: DataTelemetry,
    private val scheduler: SyncScheduler,
    private val remote: DocumentRemoteDataSource,
    private val syncTelemetry: SyncTelemetry,
    private val blobs: BlobUploader,
    private val carId: () -> String?,
    private val clock: Clock = Clock.System,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : DocumentRepository, Syncable {

    private val queries get() = database.documentQueries

    override val entity: SyncEntity = SyncEntity.DOCUMENTS

    private val runner = SyncRunner(
        entity = SyncEntity.DOCUMENTS,
        table = DocumentSyncTable(database = database, remote = remote, blobs = blobs, carId = carId),
        database = database,
        telemetry = syncTelemetry,
        clock = clock,
    )

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
        queries.selectByCar(carId.value)
            .asFlow()
            .mapToList(dispatcher)
            .map { rows -> rows.map { it.toDomain() } }
            .reportingFailures(OP_OBSERVE_CAR, carId.value, empty = emptyList())

    override fun observe(id: DocumentId): Flow<Document?> =
        queries.selectById(id.value)
            .asFlow()
            .mapToOneOrNull(dispatcher)
            .map { row -> row?.toDomain() }
            .reportingFailures(OP_OBSERVE_ONE, id.value, empty = null)

    override suspend fun add(document: Document): Either<DomainError, Document> =
        telemetry.span(DataTelemetry.DOCUMENT, OP_ADD, document.id.value) {
            try {
                val now = clock.now().toString()
                queries.insertDocument(
                    id = document.id.value,
                    carId = document.carId.value,
                    ownerId = document.ownerId.value,
                    docType = document.type.name,
                    title = document.title?.value,
                    storagePath = document.storagePath,
                    docSource = document.source.name,
                    issuedDate = document.issuedOn?.toString(),
                    expiryDate = document.expiresOn?.toString(),
                    now = now,
                    syncStatus = SyncStatus.PENDING.name,
                )
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
                val now = clock.now().toString()
                // The existence check shares the write's transaction, so a document deleted
                // between them cannot turn a DocumentNotFound into a silent no-op.
                val result = database.transactionWithResult {
                    if (queries.selectLiveId(document.id.value).executeAsOneOrNull() == null) {
                        DomainError.DocumentNotFound.left()
                    } else {
                        queries.updateDocument(
                            docType = document.type.name,
                            title = document.title?.value,
                            storagePath = document.storagePath,
                            docSource = document.source.name,
                            issuedDate = document.issuedOn?.toString(),
                            expiryDate = document.expiresOn?.toString(),
                            updatedAt = now,
                            // An edited row has to reach the server again.
                            syncStatus = SyncStatus.PENDING.name,
                            id = document.id.value,
                        )
                        document.right()
                    }
                }
                result
                    .onRight { requestSync(OP_UPDATE, document.id.value) }
                    .onLeft { telemetry.failed(DataTelemetry.DOCUMENT, OP_UPDATE, it, document.id.value) }
            } catch (e: Exception) {
                telemetry.crashed(DataTelemetry.DOCUMENT, OP_UPDATE, e, document.id.value)
                DomainError.PersistenceFailure(e.message).left()
            }
        }

    override suspend fun softDelete(id: DocumentId): Either<DomainError, Unit> =
        telemetry.span(DataTelemetry.DOCUMENT, OP_DELETE, id.value) {
            try {
                val now = clock.now().toString()
                // The tombstone stays PENDING so the deletion itself reaches the server.
                queries.softDeleteDocument(
                    deletedAt = now,
                    syncStatus = SyncStatus.PENDING.name,
                    id = id.value,
                )
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
                queries.countLiveForOwner(ownerId.value).executeAsOne().toInt()
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
