package com.hopcape.odo.infrastructure.database.owner

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.data.owner.QuestionAnswerLocalDataSource
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.owner.model.QuestionAnswer
import com.hopcape.odo.core.domain.owner.model.QuestionKey
import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import com.hopcape.odo.infrastructure.database.db.Profile_answers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * SQLDelight-backed [QuestionAnswerLocalDataSource]. Every write stamps `updated_at` and
 * leaves the row `PENDING`.
 *
 * Ids are minted here, not by the caller: the domain identifies an answer by key and value,
 * and only this class knows whether a row already exists.
 */
internal class SqlDelightQuestionAnswerLocalDataSource(
    private val database: OdoDatabase,
    private val idGenerator: IdGenerator,
    private val clock: Clock = Clock.System,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : QuestionAnswerLocalDataSource {

    private val queries get() = database.profileAnswerQueries

    /**
     * Tombstone what is gone, then insert-or-revive what is selected.
     *
     * The order matters: reversed, the tombstone step would undo a row it had just revived,
     * because by then that row is live for this key.
     */
    override suspend fun replaceAnswers(ownerId: OwnerId, key: QuestionKey, values: Set<String>) {
        val now = clock.now().toString()
        database.transaction {
            // An empty set clears the key. It needs its own statement because `NOT IN ()` is
            // not valid SQL.
            if (values.isEmpty()) {
                queries.softDeleteAnswersForKey(
                    deletedAt = now,
                    ownerId = ownerId.value,
                    questionKey = key.value,
                )
            } else {
                queries.softDeleteAnswersForKeyExcept(
                    deletedAt = now,
                    ownerId = ownerId.value,
                    questionKey = key.value,
                    keptValues = values.toList(),
                )
            }

            for (value in values) {
                queries.insertAnswer(
                    id = idGenerator.newId(),
                    ownerId = ownerId.value,
                    questionKey = key.value,
                    answerValue = value,
                    now = now,
                )
                // Also runs on the row just inserted, where it writes the same values back.
                queries.reviveAnswer(
                    now = now,
                    ownerId = ownerId.value,
                    questionKey = key.value,
                    answerValue = value,
                )
            }
        }
    }

    override fun observeAnswers(): Flow<List<QuestionAnswer>> =
        queries.selectAnswers()
            .asFlow()
            .mapToList(dispatcher)
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun answersFor(key: QuestionKey): List<QuestionAnswer> =
        queries.selectAnswersForKey(key.value).executeAsList().map { it.toDomain() }
}

private fun Profile_answers.toDomain() = QuestionAnswer(
    key = QuestionKey(question_key),
    value = answer_value,
    answeredAt = Instant.parse(answered_at),
)
