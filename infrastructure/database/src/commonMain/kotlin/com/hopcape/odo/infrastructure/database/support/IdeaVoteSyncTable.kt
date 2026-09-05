package com.hopcape.odo.infrastructure.database.support

import com.hopcape.odo.core.data.support.IdeaVoteDto
import com.hopcape.odo.core.data.support.IdeaVoteRemoteDataSource
import com.hopcape.odo.core.domain.owner.CurrentOwnerProvider
import com.hopcape.odo.infrastructure.database.db.Idea_votes
import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import com.hopcape.odo.infrastructure.database.sync.FetchResult
import com.hopcape.odo.infrastructure.database.sync.LocalRowState
import com.hopcape.odo.infrastructure.database.sync.SyncTable
import com.hopcape.odo.infrastructure.database.sync.toInstantOrNull
import com.hopcape.odo.infrastructure.database.sync.toSyncStatus
import kotlin.time.Instant

/**
 * `idea_votes` as the sync algorithm sees it.
 *
 * **The id is the pair, not a column.** The table's key is `(idea_id, owner_id)`, so the
 * runner's single-string id is the two joined — which is also how the server's unique index
 * is built. Pressing the pill twice can therefore never produce two rows racing each other.
 *
 * Pulled as well as pushed: a vote cast on another device is still this owner's vote, and the
 * pill has to show it.
 */
internal class IdeaVoteSyncTable(
    private val database: OdoDatabase,
    private val remote: IdeaVoteRemoteDataSource,
    private val currentOwner: CurrentOwnerProvider,
) : SyncTable<IdeaVoteDto> {

    private val queries get() = database.ideaVoteQueries

    override fun idOf(dto: IdeaVoteDto): String = key(dto.ideaId, dto.ownerId)

    override fun updatedAtOf(dto: IdeaVoteDto): Instant? = dto.updatedAt.toInstantOrNull()

    override suspend fun pending(): List<IdeaVoteDto> =
        queries.selectPending().executeAsList().map(Idea_votes::toDto)

    override suspend fun push(rows: List<IdeaVoteDto>): List<IdeaVoteDto> = remote.push(rows)

    override fun markSynced(id: String, remoteVersion: String) {
        val (ideaId, ownerId) = id.split()
        queries.markSynced(remoteVersion = remoteVersion, ideaId = ideaId, ownerId = ownerId)
    }

    override fun markConflict(id: String) {
        val (ideaId, ownerId) = id.split()
        queries.markConflict(ideaId = ideaId, ownerId = ownerId)
    }

    override suspend fun fetch(since: Instant?): FetchResult<IdeaVoteDto> =
        FetchResult.Rows(
            remote.fetch(
                ownerId = currentOwner.currentOwnerId().value,
                since = since?.toString(),
            ),
        )

    override fun localState(id: String): LocalRowState? {
        val (ideaId, ownerId) = id.split()
        return queries.selectSyncState(ideaId = ideaId, ownerId = ownerId)
            .executeAsOneOrNull()
            ?.let { row ->
                LocalRowState(row.sync_status.toSyncStatus(), row.updated_at.toInstantOrNull())
            }
    }

    override fun applyRemote(dto: IdeaVoteDto) {
        queries.upsertFromRemote(
            ideaId = dto.ideaId,
            ownerId = dto.ownerId,
            createdAt = dto.createdAt,
            updatedAt = dto.updatedAt,
            deletedAt = dto.deletedAt,
            remoteVersion = dto.updatedAt,
        )
    }

    private companion object {
        /**
         * The two halves of the key, joined.
         *
         * A separator that cannot appear in either half: both are UUIDs, and a `|` is not a
         * hex digit or a dash.
         */
        const val SEPARATOR = "|"

        fun key(ideaId: String, ownerId: String) = "$ideaId$SEPARATOR$ownerId"

        /**
         * Split back into the pair.
         *
         * `substringBefore`/`After` rather than an index: a string with no separator would
         * make `indexOf` answer -1 and `substring(0, -1)` throw, inside a sync transaction,
         * for an id nothing should ever have produced.
         */
        fun String.split(): Pair<String, String> =
            substringBefore(SEPARATOR) to substringAfter(SEPARATOR, missingDelimiterValue = "")
    }
}

private fun Idea_votes.toDto() = IdeaVoteDto(
    ideaId = idea_id,
    ownerId = owner_id,
    createdAt = created_at,
    updatedAt = updated_at,
    deletedAt = deleted_at,
)
