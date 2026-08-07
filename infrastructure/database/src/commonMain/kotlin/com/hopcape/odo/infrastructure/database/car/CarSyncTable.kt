package com.hopcape.odo.infrastructure.database.car

import com.hopcape.odo.core.data.car.CarDto
import com.hopcape.odo.core.data.car.CarRemoteDataSource
import com.hopcape.odo.infrastructure.database.db.Cars
import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import com.hopcape.odo.infrastructure.database.sync.LocalRowState
import com.hopcape.odo.infrastructure.database.sync.SyncTable
import com.hopcape.odo.infrastructure.database.sync.orNullIfPlaceholder
import com.hopcape.odo.infrastructure.database.sync.toInstantOrNull
import com.hopcape.odo.infrastructure.database.sync.toSyncStatus
import com.hopcape.odo.core.sync.SyncEntity
import com.hopcape.odo.core.sync.observability.SyncTelemetry
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
    private val telemetry: SyncTelemetry,
    private val ownerId: () -> String?,
) : SyncTable<CarDto> {

    private val queries get() = database.carQueries

    override fun idOf(dto: CarDto): String = dto.id

    override fun updatedAtOf(dto: CarDto): Instant? = dto.updatedAt.toInstantOrNull()

    override suspend fun pending(): List<CarDto> =
        queries.selectPending().executeAsList().map(Cars::toDto)

    /**
     * Give a re-added car back the id the server already knows it by.
     *
     * The server keeps at most one live car per plate per owner. A reinstall takes the local
     * database with it, so onboarding the same car again mints a fresh id for a plate the
     * server still holds — and the push, which upserts on the primary key, becomes an INSERT
     * that breaks that rule. The server refuses it, and because a duplicate key is refused
     * the same way every time, the car and everything hanging off it would never sync again.
     *
     * So before the push, any never-synced car with a plate is looked up on the server, and
     * a match takes on the server's id locally. The push that follows is then an update of
     * the row that is already there. The local copy still wins on content: the owner typed
     * it just now, and that is newer than whatever the server has.
     *
     * Costs nothing in the normal case — a car that has synced once carries a
     * `remote_version`, and with no candidates the server is never asked.
     */
    override suspend fun reconcileBeforePush(rows: List<CarDto>): List<CarDto> {
        val owner = ownerId().orNullIfPlaceholder() ?: return rows
        val outgoing = adoptIdentities(owner, rows)
        reclaimPrimary(owner, outgoing)
        return outgoing
    }

    /** The plate-match adoption of §6.1.2. Answers the rows as they should now be pushed. */
    private suspend fun adoptIdentities(owner: String, rows: List<CarDto>): List<CarDto> {
        // `registration_number` is non-null here: the query filters on it.
        val candidates = queries.selectUnsyncedWithPlate().executeAsList()
            .map { row -> row.id to row.registration_number }
        if (candidates.isEmpty()) return rows

        // Live rows only: a soft-deleted car has released its plate, so re-adding it really
        // is a new row and the id it was given locally is the right one.
        val serverByPlate = remote.fetchSince(owner, since = null)
            .filter { it.deletedAt == null && it.registrationNumber != null }
            .associateBy { it.registrationNumber }

        // What each re-pointed row becomes: the server's row when the local one survived
        // under its id, or null when the local one was dropped as a duplicate.
        val outcome = mutableMapOf<String, CarDto?>()
        candidates.forEach { (localId, plate) ->
            val server = serverByPlate[plate] ?: return@forEach
            if (server.id == localId) return@forEach
            outcome[localId] = if (adoptIdentity(localId = localId, server = server)) server else null
        }
        if (outcome.isEmpty()) return rows
        telemetry.identityAdopted(SyncEntity.CARS, count = outcome.size)

        // The rows were read before the ids moved, so the ones that were re-pointed are
        // restated here rather than read back — the same values, under the server's identity.
        // A dropped row leaves the push entirely: the row it merged into is the one to send,
        // and it is already in this list if it had anything to say.
        return rows.mapNotNull { dto ->
            if (!outcome.containsKey(dto.id)) return@mapNotNull dto
            val server = outcome[dto.id] ?: return@mapNotNull null
            dto.copy(id = server.id, createdAt = server.createdAt)
        }
    }

    /**
     * Clear the primary flag on the server's other cars before this device's primary goes up.
     *
     * The server allows one primary car per owner. A fresh install onboards its car as
     * primary while the server may still hold a different primary from before the reinstall
     * — plate adoption cannot catch that pair when the plates differ — and the push is then
     * refused for good, taking every service log and document on this car down with it.
     *
     * The device wins on purpose: the local flag is the owner's most recent word on which
     * car is primary (the same tie-break the pull's conflict matrix uses). The demoted rows
     * get a fresh `updated_at` on the server, so every device pulls the change.
     */
    private suspend fun reclaimPrimary(owner: String, outgoing: List<CarDto>) {
        val primary = outgoing.firstOrNull { it.isPrimary && it.deletedAt == null } ?: return
        remote.demoteOtherPrimaries(ownerId = owner, keepCarId = primary.id)
        telemetry.primaryReclaimed(SyncEntity.CARS)
    }

    /**
     * Move the local car, and everything that names it, onto [server]'s id. Answers whether
     * the local row survived the move.
     *
     * One transaction: a car that moved while its service logs did not would leave the
     * owner's history pointing at a row that no longer exists. If the server's row is
     * already here — a pull got to it first — the local one is dropped instead, after its
     * children have been carried across, and this answers `false`.
     */
    private fun adoptIdentity(localId: String, server: CarDto): Boolean {
        var survived = false
        database.transaction {
            database.serviceLogQueries.repointCar(serverId = server.id, localId = localId)
            database.documentQueries.repointCar(serverId = server.id, localId = localId)
            database.fuelFillQueries.repointCar(serverId = server.id, localId = localId)
            database.healthScoreQueries.repointCar(serverId = server.id, localId = localId)
            database.reminderQueries.repointCar(serverId = server.id, localId = localId)

            if (queries.countById(server.id).executeAsOne() > 0) {
                queries.deleteById(localId)
            } else {
                queries.adoptServerIdentity(
                    serverId = server.id,
                    createdAt = server.createdAt,
                    localId = localId,
                )
                survived = true
            }
        }
        return survived
    }

    override suspend fun push(rows: List<CarDto>): List<CarDto> = remote.push(rows)

    override fun markSynced(id: String, remoteVersion: String) =
        queries.markSynced(remoteVersion = remoteVersion, id = id)

    override fun markConflict(id: String) = queries.markConflict(id)

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
        // Make room first, or the one-primary index refuses this row — and the INSERT OR
        // IGNORE refuses it *silently*: the device would keep re-pulling the server's primary
        // forever and never store it. No telemetry from here: this runs inside the pull's
        // transaction, where nothing may suspend.
        if (dto.isPrimary && dto.deletedAt == null) queries.demoteOthersFromRemote(keepId = dto.id)
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
