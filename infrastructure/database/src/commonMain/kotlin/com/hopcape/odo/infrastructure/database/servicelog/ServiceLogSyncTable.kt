package com.hopcape.odo.infrastructure.database.servicelog

import com.hopcape.odo.core.data.remote.RemoteBucket
import com.hopcape.odo.core.data.servicelog.ServiceLogDto
import com.hopcape.odo.core.data.servicelog.ServiceLogRemoteDataSource
import com.hopcape.odo.core.data.sync.BlobUploader
import com.hopcape.odo.core.data.sync.contentTypeOf
import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import com.hopcape.odo.infrastructure.database.db.Service_logs
import com.hopcape.odo.infrastructure.database.sync.FetchResult
import com.hopcape.odo.infrastructure.database.sync.LocalRowState
import com.hopcape.odo.infrastructure.database.sync.SyncStatus
import com.hopcape.odo.infrastructure.database.sync.SyncTable
import com.hopcape.odo.infrastructure.database.sync.orNullIfPlaceholder
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
    private val ownerId: () -> String?,
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

    /**
     * Scoped to the **owner**, not to one car.
     *
     * It used to read the active car off `ActiveCarProvider.activeCarId`, a StateFlow seeded
     * null and fed by a database query. On the first run after signing in, the engine wrote
     * the pulled cars and reached this table milliseconds later — before that flow had
     * re-emitted — so the fetch returned nothing, the pull reported success, and WorkManager
     * dropped the job with none of the owner's history fetched (issue #312). It also meant a
     * second car's rows never arrived, and that an account whose server rows all carry
     * `is_primary = false` never pulled here at all.
     *
     * The owner id comes from the session synchronously, so there is no flow to lose a race
     * with, and `owner_id` is the column row-level security already filters on server-side.
     */
    override suspend fun fetch(since: Instant?): FetchResult<ServiceLogDto> {
        val owner = ownerId().orNullIfPlaceholder() ?: return FetchResult.ScopeMissing(OWNER)
        return FetchResult.Rows(remote.fetchSince(owner, since))
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
        // The local key wins, and the remote path is only the fallback for a row this device
        // has never seen. The two are not the same kind of string: locally the photo is
        // `bills/{carId}/{logId}.jpg` under app storage, while the row on the server carries
        // the path it was uploaded to inside the `bill-photos` bucket ([uploadBlobs] swaps one
        // for the other on the way out). Taking the remote value here overwrote a key that
        // opens with one that does not, so an entry's bill stopped opening the moment its own
        // upload came back round — see [DocumentSyncTable.applyRemote], which does this too.
        val keptBillPhoto = localOnly?.bill_photo_path ?: dto.billPhotoPath
        val keptBillId = dto.billId ?: localOnly?.bill_id
        // The breakdown has nowhere on the server to come back from: `ServiceLogDto` carries
        // no lines, and the server keys its own on a `bills` row this client never creates.
        // A pull that dropped them would empty the card the owner scanned the bill for.
        val keptLineItems = localOnly?.line_items

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
            line_items = keptLineItems,
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
            line_items = keptLineItems,
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

    private companion object {
        /** Names the missing scope in a log. Never the value. */
        const val OWNER = "owner id"
    }
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
