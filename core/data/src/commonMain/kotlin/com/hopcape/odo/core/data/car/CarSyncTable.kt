package com.hopcape.odo.core.data.car

import com.hopcape.odo.core.data.db.Cars
import com.hopcape.odo.core.data.db.OdoDatabase
import com.hopcape.odo.core.data.sync.LocalRowState
import com.hopcape.odo.core.data.sync.SyncTable
import com.hopcape.odo.core.data.sync.orNullIfPlaceholder
import com.hopcape.odo.core.data.sync.toInstantOrNull
import com.hopcape.odo.core.data.sync.toSyncStatus
import kotlin.time.Instant

/**
 * `cars` as the sync algorithm sees it.
 *
 * Owner-scoped: a car *is* the scope everything else hangs off, so there is nothing
 * narrower to key the pull on.
 *
 * `fuel_type` is stored locally as the Kotlin constant name and on the wire as the Postgres
 * enum label, which is lowercase. The conversion happens here, in both directions, because
 * this is the only place that knows both vocabularies.
 */
internal class CarSyncTable(
    private val database: OdoDatabase,
    private val remote: CarRemoteDataSource,
    private val ownerId: () -> String?,
) : SyncTable<CarDto> {

    private val queries get() = database.carQueries

    override fun idOf(dto: CarDto): String = dto.id

    override fun updatedAtOf(dto: CarDto): Instant? = dto.updatedAt.toInstantOrNull()

    override suspend fun pending(): List<CarDto> =
        queries.selectPending().executeAsList().map(Cars::toDto)

    override suspend fun push(rows: List<CarDto>): List<CarDto> = remote.push(rows)

    override fun markSynced(id: String, remoteVersion: String) =
        queries.markSynced(remoteVersion = remoteVersion, id = id)

    override suspend fun fetch(since: Instant?): List<CarDto> {
        // Signed out, or not signed in yet — either way there is nothing to ask for.
        val owner = ownerId().orNullIfPlaceholder() ?: return emptyList()
        return remote.fetchSince(owner, since)
    }

    override fun localState(id: String): LocalRowState? =
        queries.selectSyncState(id).executeAsOneOrNull()?.let { row ->
            LocalRowState(row.sync_status.toSyncStatus(), row.updated_at.toInstantOrNull())
        }

    override fun applyRemote(dto: CarDto) {
        queries.insertFromRemote(
            id = dto.id,
            owner_id = dto.ownerId,
            make = dto.make,
            model = dto.model,
            variant = dto.variant,
            year = dto.year.toLong(),
            fuel_type = dto.fuelType.uppercase(),
            registration_number = dto.registrationNumber,
            current_odometer_km = dto.currentOdometerKm.toLong(),
            purchase_year = dto.purchaseYear?.toLong(),
            nickname = dto.nickname,
            is_primary = if (dto.isPrimary) 1L else 0L,
            odometer_updated_at = dto.odometerUpdatedAt,
            created_at = dto.createdAt,
            updated_at = dto.updatedAt,
            deleted_at = dto.deletedAt,
            remote_version = dto.updatedAt,
        )
        queries.updateFromRemote(
            owner_id = dto.ownerId,
            make = dto.make,
            model = dto.model,
            variant = dto.variant,
            year = dto.year.toLong(),
            fuel_type = dto.fuelType.uppercase(),
            registration_number = dto.registrationNumber,
            current_odometer_km = dto.currentOdometerKm.toLong(),
            purchase_year = dto.purchaseYear?.toLong(),
            nickname = dto.nickname,
            is_primary = if (dto.isPrimary) 1L else 0L,
            odometer_updated_at = dto.odometerUpdatedAt,
            created_at = dto.createdAt,
            updated_at = dto.updatedAt,
            deleted_at = dto.deletedAt,
            remote_version = dto.updatedAt,
            id = dto.id,
        )
    }
}

private fun Cars.toDto() = CarDto(
    id = id,
    ownerId = owner_id,
    make = make,
    model = model,
    variant = variant,
    year = year.toInt(),
    fuelType = fuel_type.lowercase(),
    registrationNumber = registration_number,
    currentOdometerKm = current_odometer_km.toInt(),
    purchaseYear = purchase_year?.toInt(),
    nickname = nickname,
    isPrimary = is_primary == 1L,
    odometerUpdatedAt = odometer_updated_at,
    createdAt = created_at,
    updatedAt = updated_at,
    deletedAt = deleted_at,
)
