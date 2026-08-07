package com.hopcape.odo.infrastructure.database.servicelog

import com.hopcape.odo.core.data.remote.RemoteBucket
import com.hopcape.odo.core.data.servicelog.ServiceLogDto
import com.hopcape.odo.core.data.servicelog.ServiceLogRemoteDataSource
import com.hopcape.odo.core.data.sync.BlobUploader
import com.hopcape.odo.core.data.sync.contentTypeOf
import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import com.hopcape.odo.infrastructure.database.db.Service_logs
import com.hopcape.odo.infrastructure.database.sync.LocalRowState
import com.hopcape.odo.infrastructure.database.sync.SyncStatus
import com.hopcape.odo.infrastructure.database.sync.SyncTable
import com.hopcape.odo.infrastructure.database.sync.toInstantOrNull
import com.hopcape.odo.infrastructure.database.sync.toSyncStatus
import kotlin.time.Instant

/**
 * `service_logs` as the sync algorithm sees it.
 *
 * The mapping is spelled out in both directions rather than being generated, because the
 * pull direction is where data gets lost quietly: a blind row replace would wipe any column
 * the server does not carry. Every field below is either copied from the server or
 * deliberately preserved, and there is no third option.
 */
internal class ServiceLogSyncTable(
    private val database: OdoDatabase,
    private val remote: ServiceLogRemoteDataSource,
    private val blobs: BlobUploader,
    private val carId: () -> String?,
) : SyncTable<ServiceLogDto> {

    private val queries get() = database.serviceLogQueries

    override fun idOf(dto: ServiceLogDto): String = dto.id

    override fun updatedAtOf(dto: ServiceLogDto): Instant? = dto.updatedAt.toInstantOrNull()

    override suspend fun pending(): List<ServiceLogDto> =
        queries.selectPending().executeAsList().map { row -> row.toDto(categoriesOf(row.id)) }

    /**
     * The bill photo, into the `bill-photos` bucket.
     *
     * This is what earns an entry its "Verified" badge in the Resale Passport, which is why
     * the bytes go first: a row claiming a photo the server does not have would put that
     * badge over nothing.
     */
    override suspend fun uploadBlobs(rows: List<ServiceLogDto>): List<ServiceLogDto> = rows.map { dto ->
        val objectPath = blobs.upload(
            bucket = RemoteBucket.BILL_PHOTOS,
            localKey = dto.billPhotoPath,
            ownerId = dto.ownerId,
            carId = dto.carId,
            recordId = dto.id,
            contentType = contentTypeOf(dto.billPhotoPath),
        )
        objectPath?.let { dto.copy(billPhotoPath = it) } ?: dto
    }

    override suspend fun push(rows: List<ServiceLogDto>): List<ServiceLogDto> = remote.push(rows)

    override fun markSynced(id: String, remoteVersion: String) =
        queries.markSynced(remoteVersion = remoteVersion, id = id)

    override fun markConflict(id: String) = queries.markConflict(id)

    override suspend fun fetch(since: Instant?): List<ServiceLogDto> {
        // Service logs hang off a car, so there is nothing to pull before one exists. A run
        // on a device with no car is a no-op rather than a query for every log on the
        // account.
        val car = carId() ?: return emptyList()
        return remote.fetchSince(car, since)
    }

    override fun localState(id: String): LocalRowState? =
        queries.selectSyncState(id).executeAsOneOrNull()?.let { row ->
            LocalRowState(
                syncStatus = row.sync_status.toSyncStatus(),
                updatedAt = row.updated_at.toInstantOrNull(),
            )
        }

    override fun applyRemote(dto: ServiceLogDto) {
        // Read before writing. The bill photo is a key into *this device's* storage, and the
        // bill id names an extraction the server has no row for — a server row that has
        // never carried either must not blank the copies already here. Blanking the photo
        // would turn a Verified entry into a self-reported one on a pull.
        val localOnly = queries.selectLocalOnly(dto.id).executeAsOneOrNull()
        val keptBillPhoto = dto.billPhotoPath ?: localOnly?.bill_photo_path
        val keptBillId = dto.billId ?: localOnly?.bill_id

        queries.insertFromRemote(
            id = dto.id,
            car_id = dto.carId,
            owner_id = dto.ownerId,
            service_date = dto.serviceDate,
            odometer_km = dto.odometerKm.toLong(),
            total_amount_paise = dto.totalAmountPaise,
            workshop_name = dto.workshopName,
            notes = dto.notes,
            source = dto.source.uppercase(),
            bill_id = keptBillId,
            bill_photo_path = keptBillPhoto,
            fairness_snapshot = dto.fairnessSnapshot,
            created_at = dto.createdAt,
            updated_at = dto.updatedAt,
            deleted_at = dto.deletedAt,
            remote_version = dto.updatedAt,
        )
        queries.updateFromRemote(
            car_id = dto.carId,
            owner_id = dto.ownerId,
            service_date = dto.serviceDate,
            odometer_km = dto.odometerKm.toLong(),
            total_amount_paise = dto.totalAmountPaise,
            workshop_name = dto.workshopName,
            notes = dto.notes,
            source = dto.source.uppercase(),
            bill_id = keptBillId,
            // The local bill photo is a device path; a server row that has never seen one
            // must not blank the copy this device already holds.
            bill_photo_path = keptBillPhoto,
            fairness_snapshot = dto.fairnessSnapshot,
            created_at = dto.createdAt,
            updated_at = dto.updatedAt,
            deleted_at = dto.deletedAt,
            remote_version = dto.updatedAt,
            id = dto.id,
        )

        // Categories are a projection of the entry, so they are replaced wholesale rather
        // than merged — the server's set is the set.
        queries.deleteCategoriesFor(dto.id)
        dto.categories.forEach { queries.insertCategory(dto.id, it.uppercase()) }
    }

    /** The tags on an entry, as the wire wants them: the Postgres enum labels are lowercase. */
    private fun categoriesOf(logId: String): List<String> =
        queries.selectCategoriesFor(logId).executeAsList().map { it.lowercase() }
}

/** DB row → wire shape. `source` and the tags are Kotlin constant names locally. */
private fun Service_logs.toDto(categories: List<String>) = ServiceLogDto(
    id = id,
    carId = car_id,
    ownerId = owner_id,
    serviceDate = service_date,
    odometerKm = odometer_km.toInt(),
    totalAmountPaise = total_amount_paise,
    workshopName = workshop_name,
    notes = notes,
    source = source.lowercase(),
    // Never transmitted: the id names an extraction, and the client creates no `bills`
    // rows (the table is reserved), so the server's fk_service_logs_bill would refuse
    // the whole row with a 409 that no retry can fix. The local column keeps the id;
    // the bills sync slice revisits this when rows exist to point at.
    billId = null,
    billPhotoPath = bill_photo_path,
    fairnessSnapshot = fairness_snapshot,
    categories = categories,
    createdAt = created_at,
    updatedAt = updated_at,
    deletedAt = deleted_at,
)
