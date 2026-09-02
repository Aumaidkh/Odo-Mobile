package com.hopcape.odo.core.domain.owner.repository

import arrow.core.Either
import com.hopcape.odo.core.domain.owner.model.QuestionAnswer
import com.hopcape.odo.core.domain.owner.model.QuestionKey
import com.hopcape.odo.core.domain.shared.DomainError
import kotlinx.coroutines.flow.Flow

/**
 * Port for the owner's questionnaire answers (#394). Implemented in `:core:data`.
 *
 * No method takes an owner id: one device holds one signed-in owner, as with
 * [OwnerProfileRepository.observe].
 */
interface QuestionnaireRepository {

    /**
     * Store [values] as the complete answer to [key], replacing what was there.
     *
     * Replace, not append: omitting a value is how the owner removes it. An empty [values]
     * clears the key and is a valid answer meaning "none of these" — whether a question may
     * be left blank is the asking screen's rule, not this one's.
     */
    suspend fun save(key: QuestionKey, values: Set<String>): Either<DomainError, Unit>

    /**
     * Every answer the owner has given, across all questions.
     *
     * One stream rather than one per key, because the readers that matter want several
     * answers at once and the set of keys lives in a module this one cannot see.
     */
    fun observe(): Flow<List<QuestionAnswer>>

    /** The answers stored for [key]. Empty when the question has not been answered. */
    suspend fun answersFor(key: QuestionKey): Either<DomainError, List<QuestionAnswer>>
}
