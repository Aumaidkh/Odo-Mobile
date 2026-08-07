package com.hopcape.odo.core.data.servicelog

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.hopcape.odo.core.data.db.OdoDatabase
import com.hopcape.odo.core.data.sync.SyncStatus
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.servicelog.model.OdometerReading
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlin.time.Clock

/**
 * SQLDelight-backed [ServiceLogLocalDataSource] — fully offline. The local DB is the
 * source of truth; every write stamps `updated_at` and leaves the row
 * `sync_status = PENDING` for the sync engine.
 *
 * Timestamps are client-stamped here (offline-first; the server reconciles on sync).
 */
internal class SqlDelightServiceLogLocalDataSource(
    private val database: OdoDatabase,
    private val clock: Clock = Clock.System,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ServiceLogLocalDataSource {

    private val queries get() = database.serviceLogQueries

    override suspend fun insert(entry: ServiceLogEntry) {
        val now = clock.now().toString()
        database.transaction {
            queries.insertServiceLog(
                id = entry.id.value,
                carId = entry.carId.value,
                ownerId = entry.ownerId.value,
                serviceDate = entry.serviceDate.toString(),
                odometerKm = entry.odometer.km.toLong(),
                totalAmountPaise = entry.totalAmount.paise,
                workshopName = entry.workshopName?.value,
                notes = entry.notes?.value,
                source = entry.source.name,
                billId = entry.billId?.value,
                billPhotoPath = entry.billPhotoRef,
                fairnessSnapshot = entry.fairness?.toJson(),
                now = now,
                syncStatus = SyncStatus.PENDING.name,
            )
            writeCategories(entry)
        }
    }

    override suspend fun update(entry: ServiceLogEntry): Boolean {
        val now = clock.now().toString()
        return database.transactionWithResult {
            if (queries.selectById(entry.id.value, ::serviceLogFromRow).executeAsOneOrNull() == null) {
                return@transactionWithResult false
            }
            queries.updateServiceLog(
                serviceDate = entry.serviceDate.toString(),
                odometerKm = entry.odometer.km.toLong(),
                totalAmountPaise = entry.totalAmount.paise,
                workshopName = entry.workshopName?.value,
                notes = entry.notes?.value,
                source = entry.source.name,
                billId = entry.billId?.value,
                billPhotoPath = entry.billPhotoRef,
                fairnessSnapshot = entry.fairness?.toJson(),
                updatedAt = now,
                // An edited row has to reach the server again.
                syncStatus = SyncStatus.PENDING.name,
                id = entry.id.value,
            )
            // Categories are rewritten wholesale with their parent. Because the parent
            // row is always written too, a category-only edit still bumps `updated_at`
            // and goes PENDING — without that, a tag change would never sync.
            writeCategories(entry)
            true
        }
    }

    override suspend fun softDelete(id: ServiceLogId) {
        val now = clock.now().toString()
        // The tombstone stays PENDING so the deletion itself reaches the server; the
        // categories stay with it, since a restored row would need them back.
        queries.softDeleteServiceLog(deletedAt = now, syncStatus = SyncStatus.PENDING.name, id = id.value)
    }

    override fun observeByCar(carId: CarId): Flow<List<ServiceLogEntry>> =
        queries.selectByCar(carId.value, ::serviceLogFromRow)
            .asFlow()
            .mapToList(dispatcher)

    override fun observeById(id: ServiceLogId): Flow<ServiceLogEntry?> =
        queries.selectById(id.value, ::serviceLogFromRow)
            .asFlow()
            .mapToOneOrNull(dispatcher)

    override suspend fun odometerReadings(carId: CarId): List<OdometerReading> =
        queries.selectOdometerReadings(carId.value, ::odometerReadingFromRow).executeAsList()

    override fun observeOdometerReadings(carId: CarId): Flow<List<OdometerReading>> =
        queries.selectOdometerReadings(carId.value, ::odometerReadingFromRow)
            .asFlow()
            .mapToList(dispatcher)

    /** Delete-and-reinsert, always inside the caller's transaction. */
    private fun writeCategories(entry: ServiceLogEntry) {
        queries.deleteCategoriesFor(entry.id.value)
        entry.categories.forEach { category ->
            queries.insertCategory(serviceLogId = entry.id.value, category = category.name)
        }
    }
}
