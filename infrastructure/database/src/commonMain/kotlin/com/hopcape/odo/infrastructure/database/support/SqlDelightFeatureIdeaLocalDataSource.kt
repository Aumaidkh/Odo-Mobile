package com.hopcape.odo.infrastructure.database.support

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.hopcape.odo.core.data.support.FeatureIdeaDto
import com.hopcape.odo.core.data.support.FeatureIdeaLocalDataSource
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.support.FeatureIdea
import com.hopcape.odo.core.domain.support.IdeaStatus
import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import com.hopcape.odo.infrastructure.database.db.SelectWithVotes
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

/**
 * The curated catalogue and this owner's votes on it.
 *
 * The vote count shown is the server's, not a local tally: a count assembled from one device
 * is not a count, and the trigger that keeps it runs in the same transaction as the vote.
 *
 * So the number moves on the next pull, not on the tap. What answers the tap is `voted` — the
 * pill turns solid and its caption changes — which is feedback the owner can see without the
 * app inventing a figure it would then have to correct.
 */
internal class SqlDelightFeatureIdeaLocalDataSource(
    private val database: OdoDatabase,
    private val clock: Clock = Clock.System,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : FeatureIdeaLocalDataSource {

    private val ideas get() = database.featureIdeaQueries
    private val votes get() = database.ideaVoteQueries

    override fun observe(ownerId: OwnerId): Flow<List<FeatureIdea>> =
        ideas.selectWithVotes(ownerId.value)
            .asFlow()
            .mapToList(dispatcher)
            .map { rows -> rows.map { it.toDomain() } }

    /**
     * Insert then update, in one transaction.
     *
     * SQLite 3.18 — which is what minSdk 26 gets — has no UPSERT, so this is the pair the
     * project writes everywhere else. The update is what takes a vote back off: the row stays
     * and is tombstoned, because the server has to hear that it was withdrawn.
     */
    override suspend fun setVote(ownerId: OwnerId, ideaId: String, voted: Boolean) {
        val now = clock.now().toString()
        database.transaction {
            votes.insertVote(ideaId = ideaId, ownerId = ownerId.value, now = now)
            votes.setVote(
                deletedAt = if (voted) null else now,
                now = now,
                ideaId = ideaId,
                ownerId = ownerId.value,
            )
        }
    }

    /**
     * Replace the whole catalogue.
     *
     * Cleared first, so an idea the server no longer sends stops being shown. The votes are a
     * different table and are left alone — an owner's vote is theirs whatever the panel does
     * with the list.
     */
    override suspend fun replaceCatalogue(catalogue: List<FeatureIdeaDto>) {
        database.transaction {
            ideas.deleteAllRows()
            catalogue.forEach { dto ->
                ideas.upsertFromRemote(
                    id = dto.id,
                    title = dto.title,
                    status = dto.status,
                    votes = dto.votes,
                    createdAt = dto.createdAt,
                    updatedAt = dto.updatedAt,
                    deletedAt = dto.deletedAt,
                )
            }
        }
    }

    private fun SelectWithVotes.toDomain() = FeatureIdea(
        id = id,
        title = title,
        // A status a newer build introduced reads as under review rather than dropping the
        // row: the title and the count are still worth showing.
        status = IdeaStatus.entries.firstOrNull { it.name == status } ?: IdeaStatus.UNDER_REVIEW,
        votes = votes.toInt(),
        voted = voted,
    )
}
