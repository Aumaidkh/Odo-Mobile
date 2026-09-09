package com.hopcape.odo.core.data.owner

import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.owner.model.QuestionAnswer
import com.hopcape.odo.core.domain.owner.model.QuestionKey
import kotlinx.coroutines.flow.Flow

/**
 * Local storage for questionnaire answers. Owns how a row is written and read;
 * [QuestionnaireRepositoryImpl] owns what an operation means.
 *
 * Throws on storage failure, which the repository turns into a `DomainError`.
 */
interface QuestionAnswerLocalDataSource {

    /**
     * Make [values] the complete stored answer to [key], in one transaction.
     *
     * Atomic because it does three things at once: tombstone what is no longer selected,
     * insert what is new, revive what was deselected earlier.
     */
    suspend fun replaceAnswers(ownerId: OwnerId, key: QuestionKey, values: Set<String>)

    /** Every live answer on this device. Tombstoned rows are excluded. */
    fun observeAnswers(): Flow<List<QuestionAnswer>>

    /** The live answers for [key]. Empty when the question has not been answered. */
    suspend fun answersFor(key: QuestionKey): List<QuestionAnswer>
}
