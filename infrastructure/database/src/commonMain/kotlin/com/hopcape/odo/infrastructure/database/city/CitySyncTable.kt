package com.hopcape.odo.infrastructure.database.city

import com.hopcape.odo.core.data.city.CityDto
import com.hopcape.odo.core.data.city.CityRemoteDataSource
import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import com.hopcape.odo.infrastructure.database.sync.FetchResult
import com.hopcape.odo.infrastructure.database.sync.LocalRowState
import com.hopcape.odo.infrastructure.database.sync.SyncTable
import com.hopcape.odo.infrastructure.database.sync.toInstantOrNull
import com.hopcape.odo.infrastructure.database.sync.toSyncStatus
import kotlin.time.Instant

/**
 * `city` as the sync algorithm sees it — **pull only**.
 *
 * No `owner_id`: `cities` is a shared, public table everybody reads the same rows from, so
 * unlike [com.hopcape.odo.infrastructure.database.car.CarSyncTable], [fetch] never needs to
 * name a scope — every row is fair game for every device. That is also why this table is never
 * edited locally: [pending] is always empty, and [push]/[markSynced]/[markConflict] exist only
 * because [SyncTable] requires them.
 */
internal class CitySyncTable(
    private val database: OdoDatabase,
    private val remote: CityRemoteDataSource,
) : SyncTable<CityDto> {

    private val queries get() = database.cityQueries

    override fun idOf(dto: CityDto): String = dto.id

    override fun updatedAtOf(dto: CityDto): Instant? = dto.updatedAt.toInstantOrNull()

    override suspend fun pending(): List<CityDto> = emptyList()

    override suspend fun push(rows: List<CityDto>): List<CityDto> = rows

    override fun markSynced(id: String, remoteVersion: String) = Unit

    override fun markConflict(id: String) = Unit

    override suspend fun fetch(since: Instant?): FetchResult<CityDto> =
        FetchResult.Rows(remote.fetchSince(since))

    override fun localState(id: String): LocalRowState? =
        queries.selectSyncState(id).executeAsOneOrNull()?.let { row ->
            LocalRowState(row.sync_status.toSyncStatus(), row.updated_at.toInstantOrNull())
        }

    override fun applyRemote(dto: CityDto) {
        queries.insertFromRemote(
            id = dto.id,
            name = dto.name,
            state = dto.state,
            tier = dto.tier.toLong(),
            is_active = if (dto.isActive) 1L else 0L,
            updated_at = dto.updatedAt,
        )
        queries.updateFromRemote(
            name = dto.name,
            state = dto.state,
            tier = dto.tier.toLong(),
            is_active = if (dto.isActive) 1L else 0L,
            updated_at = dto.updatedAt,
            id = dto.id,
        )
    }
}
