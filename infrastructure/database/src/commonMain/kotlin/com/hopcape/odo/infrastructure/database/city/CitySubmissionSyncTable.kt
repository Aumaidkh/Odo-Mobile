package com.hopcape.odo.infrastructure.database.city

import com.hopcape.odo.core.data.city.CitySubmissionDto
import com.hopcape.odo.core.data.city.CitySubmissionRemoteDataSource
import com.hopcape.odo.infrastructure.database.db.City_submissions
import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import com.hopcape.odo.infrastructure.database.sync.FetchResult
import com.hopcape.odo.infrastructure.database.sync.LocalRowState
import com.hopcape.odo.infrastructure.database.sync.SyncTable
import com.hopcape.odo.infrastructure.database.sync.toInstantOrNull
import com.hopcape.odo.infrastructure.database.sync.toSyncStatus
import kotlin.time.Instant

/**
 * `city_submissions` as the sync algorithm sees it — **push only**.
 *
 * [fetch] deliberately answers with nothing, the same way
 * [com.hopcape.odo.infrastructure.database.car.VehicleCatalogSubmissionSyncTable] does: a report
 * is something the owner filed once, it has no server-side lifecycle, and no screen reads it
 * back.
 */
internal class CitySubmissionSyncTable(
    private val database: OdoDatabase,
    private val remote: CitySubmissionRemoteDataSource,
) : SyncTable<CitySubmissionDto> {

    private val queries get() = database.citySubmissionQueries

    override fun idOf(dto: CitySubmissionDto): String = dto.id

    override fun updatedAtOf(dto: CitySubmissionDto): Instant? = dto.createdAt.toInstantOrNull()

    override suspend fun pending(): List<CitySubmissionDto> =
        queries.selectPending().executeAsList().map(City_submissions::toDto)

    override suspend fun push(rows: List<CitySubmissionDto>): List<CitySubmissionDto> = remote.push(rows)

    override fun markSynced(id: String, remoteVersion: String) =
        queries.markSynced(remoteVersion = remoteVersion, id = id)

    override fun markConflict(id: String) = queries.markConflict(id)

    /** Nothing to pull — see the class note. */
    override suspend fun fetch(since: Instant?): FetchResult<CitySubmissionDto> =
        FetchResult.Rows(emptyList())

    override fun localState(id: String): LocalRowState? =
        queries.selectSyncState(id).executeAsOneOrNull()?.let { row ->
            LocalRowState(row.sync_status.toSyncStatus(), row.updated_at.toInstantOrNull())
        }

    /** Unreachable while [fetch] is empty, but the interface requires an implementation. */
    override fun applyRemote(dto: CitySubmissionDto) = Unit
}

private fun City_submissions.toDto() = CitySubmissionDto(
    id = id,
    ownerId = owner_id,
    name = name,
    createdAt = created_at,
)
