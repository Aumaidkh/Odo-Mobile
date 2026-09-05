package com.hopcape.odo.core.data.support

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.data.observability.DataTelemetry
import com.hopcape.odo.core.domain.owner.CurrentOwnerProvider
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.domain.support.FeatureIdea
import com.hopcape.odo.core.domain.support.FeatureIdeaRepository
import com.hopcape.odo.core.sync.SyncReason
import com.hopcape.odo.core.sync.SyncScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * The curated list, read locally and refreshed from the server.
 *
 * **Reading and voting go opposite ways.** The catalogue is the panel's to write, so a refresh
 * replaces what is stored; a vote is the only thing this device puts back, and it lands
 * `PENDING` like every other local write.
 */
internal class FeatureIdeaRepositoryImpl(
    private val local: FeatureIdeaLocalDataSource,
    private val remote: FeatureIdeaRemoteDataSource,
    private val currentOwner: CurrentOwnerProvider,
    private val telemetry: DataTelemetry,
    private val scheduler: SyncScheduler,
) : FeatureIdeaRepository {

    override fun observe(): Flow<List<FeatureIdea>> = flow {
        emitAll(local.observe(currentOwner.currentOwnerId()))
    }.catch { e ->
        telemetry.crashed(DataTelemetry.FEATURE_IDEA, OP_OBSERVE, e)
        emit(emptyList())
    }

    /**
     * Ask the server for a fresher catalogue.
     *
     * A failure leaves what is stored alone and is reported, not shown: the screen already has
     * a list, and an error over other people's ideas helps nobody.
     */
    override suspend fun refresh(): Either<DomainError, Unit> =
        telemetry.span(DataTelemetry.FEATURE_IDEA, OP_REFRESH) {
            try {
                local.replaceCatalogue(remote.ideas())
                Unit.right()
            } catch (e: Exception) {
                telemetry.crashed(DataTelemetry.FEATURE_IDEA, OP_REFRESH, e)
                DomainError.LookupUnavailable.left()
            }
        }

    override suspend fun vote(ideaId: String, voted: Boolean): Either<DomainError, Unit> =
        telemetry.span(DataTelemetry.FEATURE_IDEA, OP_VOTE, ideaId) {
            try {
                local.setVote(currentOwner.currentOwnerId(), ideaId, voted)
                scheduler.requestSync(SyncReason.LocalWrite)
                Unit.right()
            } catch (e: Exception) {
                telemetry.crashed(DataTelemetry.FEATURE_IDEA, OP_VOTE, e, ideaId)
                DomainError.PersistenceFailure(e.message).left()
            }
        }

    private companion object {
        const val OP_OBSERVE = "observe"
        const val OP_REFRESH = "refresh"
        const val OP_VOTE = "vote"
    }
}
