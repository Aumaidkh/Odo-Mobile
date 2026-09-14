package com.hopcape.odo.infrastructure.database.owner

import com.hopcape.odo.core.data.owner.QuestionAnswerDto
import com.hopcape.odo.core.data.owner.QuestionAnswerRemoteDataSource
import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import com.hopcape.odo.infrastructure.database.db.Profile_answers
import com.hopcape.odo.infrastructure.database.sync.FetchResult
import com.hopcape.odo.infrastructure.database.sync.LocalRowState
import com.hopcape.odo.infrastructure.database.sync.SyncTable
import com.hopcape.odo.infrastructure.database.sync.orNullIfPlaceholder
import com.hopcape.odo.infrastructure.database.sync.toInstantOrNull
import com.hopcape.odo.infrastructure.database.sync.toSyncStatus
import kotlin.time.Instant

/**
 * `profile_answers` as the sync algorithm sees it.
 *
 * Rows only, so [uploadBlobs] and `reconcileBeforePush` stay at their no-op defaults. Scoped
 * to the owner, not a car: an answer describes the person.
 */
internal class QuestionAnswerSyncTable(
    private val database: OdoDatabase,
    private val remote: QuestionAnswerRemoteDataSource,
    private val ownerId: () -> String?,
) : SyncTable<QuestionAnswerDto> {

    private val queries get() = database.profileAnswerQueries

    override fun idOf(dto: QuestionAnswerDto): String = dto.id

    override fun updatedAtOf(dto: QuestionAnswerDto): Instant? = dto.updatedAt.toInstantOrNull()

    override suspend fun pending(): List<QuestionAnswerDto> =
        queries.selectPending().executeAsList().map(Profile_answers::toDto)

    override suspend fun push(rows: List<QuestionAnswerDto>): List<QuestionAnswerDto> =
        remote.push(rows)

    override fun markSynced(id: String, remoteVersion: String) =
        queries.markSynced(remoteVersion = remoteVersion, id = id)

    override fun markConflict(id: String) = queries.markConflict(id)

    override suspend fun fetch(since: Instant?): FetchResult<QuestionAnswerDto> {
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

    /**
     * The insert can be ignored on the unique triple, not only the primary key: the same
     * answer written offline on two devices is two ids for one row. The update then matches
     * nothing, the local row stays `PENDING`, and the server refuses it as a conflict. Both
     * rows say the same thing, so the device keeps the id it can still reconcile.
     */
    override fun applyRemote(dto: QuestionAnswerDto) {
        queries.insertFromRemote(
            id = dto.id,
            owner_id = dto.ownerId,
            question_key = dto.questionKey,
            answer_value = dto.value,
            answered_at = dto.answeredAt,
            created_at = dto.createdAt,
            updated_at = dto.updatedAt,
            deleted_at = dto.deletedAt,
            remote_version = dto.updatedAt,
        )
        queries.updateFromRemote(
            owner_id = dto.ownerId,
            question_key = dto.questionKey,
            answer_value = dto.value,
            answered_at = dto.answeredAt,
            created_at = dto.createdAt,
            updated_at = dto.updatedAt,
            deleted_at = dto.deletedAt,
            remote_version = dto.updatedAt,
            id = dto.id,
        )
    }

    private companion object {
        /** Names the missing scope in a log. Never the value. */
        const val OWNER = "owner id"
    }
}

private fun Profile_answers.toDto() = QuestionAnswerDto(
    id = id,
    ownerId = owner_id,
    questionKey = question_key,
    value = answer_value,
    answeredAt = answered_at,
    createdAt = created_at,
    updatedAt = updated_at,
    deletedAt = deleted_at,
)
