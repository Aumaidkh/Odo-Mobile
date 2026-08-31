package com.hopcape.odo.infrastructure.database.entitlement

import com.hopcape.odo.core.data.entitlement.EntitlementOverrideDto
import com.hopcape.odo.core.data.entitlement.EntitlementOverrideRemoteDataSource
import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import com.hopcape.odo.infrastructure.database.sync.FetchResult
import com.hopcape.odo.infrastructure.database.sync.LocalRowState
import com.hopcape.odo.infrastructure.database.sync.SyncTable
import com.hopcape.odo.infrastructure.database.sync.toInstantOrNull
import com.hopcape.odo.infrastructure.database.sync.toSyncStatus
import kotlin.time.Instant

/**
 * `entitlement_override` as the sync algorithm sees it — **pull only**.
 *
 * The same shape as [com.hopcape.odo.infrastructure.database.city.CitySyncTable] and for a
 * stronger reason: the server is the only writer here by design, so [pending] is always empty
 * and [push]/[markSynced]/[markConflict] exist only because [SyncTable] requires them.
 *
 * The row's identity is a composite — one owner, one feature — while [SyncTable] wants a single
 * string id. They are joined with a separator that cannot occur in either half: a feature name
 * is an identifier and an owner id is a uuid, so neither contains '|'.
 */
internal class EntitlementOverrideSyncTable(
    private val database: OdoDatabase,
    private val remote: EntitlementOverrideRemoteDataSource,
) : SyncTable<EntitlementOverrideDto> {

    private val queries get() = database.entitlementOverrideQueries

    override fun idOf(dto: EntitlementOverrideDto): String = "${dto.ownerId}$SEPARATOR${dto.feature}"

    override fun updatedAtOf(dto: EntitlementOverrideDto): Instant? = dto.grantedAt.toInstantOrNull()

    override suspend fun pending(): List<EntitlementOverrideDto> = emptyList()

    override suspend fun push(rows: List<EntitlementOverrideDto>): List<EntitlementOverrideDto> = rows

    override fun markSynced(id: String, remoteVersion: String) = Unit

    override fun markConflict(id: String) = Unit

    override suspend fun fetch(since: Instant?): FetchResult<EntitlementOverrideDto> =
        FetchResult.Rows(remote.fetchSince(since))

    override fun localState(id: String): LocalRowState? {
        val (ownerId, feature) = id.split(SEPARATOR, limit = 2).takeIf { it.size == 2 } ?: return null
        return queries.selectSyncState(ownerId = ownerId, feature = feature)
            .executeAsOneOrNull()
            ?.let { row -> LocalRowState(row.sync_status.toSyncStatus(), row.updated_at.toInstantOrNull()) }
    }

    override fun applyRemote(dto: EntitlementOverrideDto) {
        queries.insertFromRemote(
            owner_id = dto.ownerId,
            feature = dto.feature,
            granted = if (dto.granted) 1L else 0L,
            expires_at = dto.expiresAt,
            updated_at = dto.grantedAt,
        )
        queries.updateFromRemote(
            granted = if (dto.granted) 1L else 0L,
            expires_at = dto.expiresAt,
            updated_at = dto.grantedAt,
            owner_id = dto.ownerId,
            feature = dto.feature,
        )
    }

    private companion object {
        const val SEPARATOR = "|"
    }
}
