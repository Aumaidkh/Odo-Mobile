package com.hopcape.odo.core.data.document

import com.hopcape.odo.core.data.db.Documents
import com.hopcape.odo.core.data.db.OdoDatabase
import com.hopcape.odo.core.data.remote.RemoteBucket
import com.hopcape.odo.core.data.sync.BlobUploader
import com.hopcape.odo.core.data.sync.LocalRowState
import com.hopcape.odo.core.data.sync.contentTypeOf
import com.hopcape.odo.core.data.sync.SyncTable
import com.hopcape.odo.core.data.sync.toInstantOrNull
import com.hopcape.odo.core.data.sync.toSyncStatus
import kotlin.time.Instant

/**
 * `documents` as the sync algorithm sees it.
 *
 * Rows only. The file each row names travels separately, through `RemoteFileStorage` — a
 * row whose bytes have not been uploaded yet still syncs and points at the path they will
 * land on.
 *
 * `doc_type` and `doc_source` are Kotlin constant names locally and lowercase Postgres enum
 * labels on the wire.
 */
internal class DocumentSyncTable(
    private val database: OdoDatabase,
    private val remote: DocumentRemoteDataSource,
    private val blobs: BlobUploader,
    private val carId: () -> String?,
) : SyncTable<DocumentDto> {

    private val queries get() = database.documentQueries

    override fun idOf(dto: DocumentDto): String = dto.id

    override fun updatedAtOf(dto: DocumentDto): Instant? = dto.updatedAt.toInstantOrNull()

    override suspend fun pending(): List<DocumentDto> =
        queries.selectPending().executeAsList().map(Documents::toDto)

    /**
     * The vault's papers, into the `documents` bucket.
     *
     * `storage_path` means two different things on the two sides: locally it is a key into
     * app storage, on the server it is a path inside the bucket. This is where one becomes
     * the other. A row whose upload failed keeps its local key and is pushed anyway — the
     * dates on it drive reminders and are worth having — and stays PENDING for a retry.
     */
    override suspend fun uploadBlobs(rows: List<DocumentDto>): List<DocumentDto> = rows.map { dto ->
        val objectPath = blobs.upload(
            bucket = RemoteBucket.DOCUMENTS,
            localKey = dto.storagePath,
            ownerId = dto.ownerId,
            carId = dto.carId,
            recordId = dto.id,
            contentType = contentTypeOf(dto.storagePath),
        )
        objectPath?.let { dto.copy(storagePath = it) } ?: dto
    }

    override suspend fun push(rows: List<DocumentDto>): List<DocumentDto> = remote.push(rows)

    override fun markSynced(id: String, remoteVersion: String) =
        queries.markSynced(remoteVersion = remoteVersion, id = id)

    override fun markConflict(id: String) = queries.markConflict(id)

    override suspend fun fetch(since: Instant?): List<DocumentDto> {
        val car = carId() ?: return emptyList()
        return remote.fetchSince(car, since)
    }

    override fun localState(id: String): LocalRowState? =
        queries.selectSyncState(id).executeAsOneOrNull()?.let { row ->
            LocalRowState(row.sync_status.toSyncStatus(), row.updated_at.toInstantOrNull())
        }

    override fun applyRemote(dto: DocumentDto) {
        // The local key stays local. Overwriting it with the bucket path would leave the
        // device unable to open a file it still has.
        val keptPath = queries.selectStoragePath(dto.id).executeAsOneOrNull()
            ?: dto.storagePath

        queries.insertFromRemote(
            id = dto.id,
            car_id = dto.carId,
            owner_id = dto.ownerId,
            doc_type = dto.docType.uppercase(),
            title = dto.title,
            storage_path = dto.storagePath,
            doc_source = dto.docSource.uppercase(),
            issued_date = dto.issuedDate,
            expiry_date = dto.expiryDate,
            created_at = dto.createdAt,
            updated_at = dto.updatedAt,
            deleted_at = dto.deletedAt,
            remote_version = dto.updatedAt,
        )
        queries.updateFromRemote(
            car_id = dto.carId,
            owner_id = dto.ownerId,
            doc_type = dto.docType.uppercase(),
            title = dto.title,
            storage_path = keptPath,
            doc_source = dto.docSource.uppercase(),
            issued_date = dto.issuedDate,
            expiry_date = dto.expiryDate,
            created_at = dto.createdAt,
            updated_at = dto.updatedAt,
            deleted_at = dto.deletedAt,
            remote_version = dto.updatedAt,
            id = dto.id,
        )
    }
}

private fun Documents.toDto() = DocumentDto(
    id = id,
    carId = car_id,
    ownerId = owner_id,
    docType = doc_type.lowercase(),
    title = title,
    storagePath = storage_path,
    docSource = doc_source.lowercase(),
    issuedDate = issued_date,
    expiryDate = expiry_date,
    createdAt = created_at,
    updatedAt = updated_at,
    deletedAt = deleted_at,
)
