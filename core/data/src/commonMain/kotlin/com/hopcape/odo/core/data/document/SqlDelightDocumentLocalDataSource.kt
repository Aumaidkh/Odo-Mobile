package com.hopcape.odo.core.data.document

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.hopcape.odo.core.data.db.OdoDatabase
import com.hopcape.odo.core.data.sync.SyncStatus
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.document.model.Document
import com.hopcape.odo.core.domain.document.model.DocumentId
import com.hopcape.odo.core.domain.owner.model.OwnerId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

/**
 * SQLDelight-backed [DocumentLocalDataSource] — fully offline. The local DB is the source
 * of truth; every write stamps `updated_at` and leaves the row `sync_status = PENDING`
 * for the sync engine.
 *
 * Timestamps are client-stamped here (offline-first; the server reconciles on sync).
 */
internal class SqlDelightDocumentLocalDataSource(
    private val database: OdoDatabase,
    private val clock: Clock = Clock.System,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : DocumentLocalDataSource {

    private val queries get() = database.documentQueries

    override suspend fun insert(document: Document) {
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
    }

    override suspend fun update(document: Document): Boolean {
        val now = clock.now().toString()
        return database.transactionWithResult {
            if (queries.selectLiveId(document.id.value).executeAsOneOrNull() == null) {
                return@transactionWithResult false
            }
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
            true
        }
    }

    override suspend fun softDelete(id: DocumentId) {
        val now = clock.now().toString()
        // The tombstone stays PENDING so the deletion itself reaches the server.
        queries.softDeleteDocument(deletedAt = now, syncStatus = SyncStatus.PENDING.name, id = id.value)
    }

    override fun observeByCar(carId: CarId): Flow<List<Document>> =
        queries.selectByCar(carId.value)
            .asFlow()
            .mapToList(dispatcher)
            .map { rows -> rows.map { it.toDomain() } }

    override fun observeById(id: DocumentId): Flow<Document?> =
        queries.selectById(id.value)
            .asFlow()
            .mapToOneOrNull(dispatcher)
            .map { row -> row?.toDomain() }

    override suspend fun countLiveForOwner(ownerId: OwnerId): Int =
        queries.countLiveForOwner(ownerId.value).executeAsOne().toInt()
}
