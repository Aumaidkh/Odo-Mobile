package com.hopcape.odo.core.data.owner

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.data.observability.DataTelemetry
import com.hopcape.odo.core.domain.owner.CurrentOwnerProvider
import com.hopcape.odo.core.domain.owner.model.QuestionAnswer
import com.hopcape.odo.core.domain.owner.model.QuestionKey
import com.hopcape.odo.core.domain.owner.repository.QuestionnaireRepository
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.sync.SyncReason
import com.hopcape.odo.core.sync.SyncScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

/**
 * [QuestionnaireRepository] over a [QuestionAnswerLocalDataSource]. Offline-first: the local
 * store is the source of truth and writes land `PENDING`.
 *
 * The owner id is read at write time, not injected once, so an answer given before sign-in is
 * stamped with the offline placeholder and moved across by adoption later (SYNC_DESIGN §9).
 */
internal class QuestionnaireRepositoryImpl(
    private val local: QuestionAnswerLocalDataSource,
    private val currentOwner: CurrentOwnerProvider,
    private val telemetry: DataTelemetry,
    private val scheduler: SyncScheduler,
) : QuestionnaireRepository {

    override suspend fun save(key: QuestionKey, values: Set<String>): Either<DomainError, Unit> =
        telemetry.span(DataTelemetry.QUESTIONNAIRE, OP_SAVE, key.value) {
            try {
                local.replaceAnswers(currentOwner.currentOwnerId(), key, values)
                requestSync(OP_SAVE, key.value)
                Unit.right()
            } catch (e: Exception) {
                telemetry.crashed(DataTelemetry.QUESTIONNAIRE, OP_SAVE, e, key.value)
                DomainError.PersistenceFailure(e.message).left()
            }
        }

    /**
     * A read failure becomes an empty list rather than a broken stream. Callers already handle
     * "not answered yet"; none of them can do anything with the exception.
     */
    override fun observe(): Flow<List<QuestionAnswer>> =
        local.observeAnswers()
            .catch { e ->
                telemetry.crashed(DataTelemetry.QUESTIONNAIRE, OP_OBSERVE, e)
                emit(emptyList())
            }

    override suspend fun answersFor(key: QuestionKey): Either<DomainError, List<QuestionAnswer>> =
        telemetry.span(DataTelemetry.QUESTIONNAIRE, OP_ANSWERS_FOR, key.value) {
            try {
                local.answersFor(key).right()
            } catch (e: Exception) {
                telemetry.crashed(DataTelemetry.QUESTIONNAIRE, OP_ANSWERS_FOR, e, key.value)
                DomainError.PersistenceFailure(e.message).left()
            }
        }

    /**
     * Ask for a sync after a successful write. A scheduling failure never fails the write: the
     * row is local and `PENDING`, and the next trigger carries it.
     */
    private suspend fun requestSync(operation: String, id: String) {
        try {
            scheduler.requestSync(SyncReason.LocalWrite)
        } catch (e: Exception) {
            telemetry.crashed(DataTelemetry.QUESTIONNAIRE, "$operation.schedule", e, id)
        }
    }

    private companion object {
        const val OP_SAVE = "saveAnswer"
        const val OP_OBSERVE = "observeAnswers"
        const val OP_ANSWERS_FOR = "answersForKey"
    }
}
