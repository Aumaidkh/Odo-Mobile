package com.hopcape.odo.infrastructure.database.setup

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.logging.api.Logger
import com.hopcape.odo.core.common.runCatchingCancellable
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.setup.SetupDraft
import com.hopcape.odo.core.domain.setup.SetupDraftRepository
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import com.hopcape.odo.infrastructure.database.db.Setup_draft
import com.hopcape.performance.api.PerformanceTracer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlin.time.Instant

/** SQLDelight-backed [SetupDraftRepository] over the one-row `setup_draft` table. */
internal class SqlDelightSetupDraftRepository(
    private val database: OdoDatabase,
    private val logger: Logger,
    private val tracer: PerformanceTracer,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : SetupDraftRepository {

    private val queries get() = database.setupDraftQueries

    override fun observe(): Flow<SetupDraft?> =
        queries.selectDraft()
            .asFlow()
            .mapToOneOrNull(dispatcher)
            .map { it?.toDomain() }
            // A broken row reads as no draft: the owner sees the make grid, not a crash.
            .catch { e ->
                logger.error(TAG, "setup_draft_read_failed", fields = mapOf(ERROR to e::class.simpleName))
                emitAll(flowOf(null))
            }

    override suspend fun save(draft: SetupDraft): Either<DomainError, Unit> = write("setup_draft_save") {
        database.transaction {
            queries.deleteDraft()
            queries.insertDraft(
                make = draft.make,
                model_name = draft.modelName,
                model_variant = draft.modelVariant,
                year = draft.year?.toLong(),
                fuel_type = draft.fuel?.name,
                updated_at = draft.updatedAt.toString(),
            )
        }
    }

    override suspend fun clear(): Either<DomainError, Unit> = write("setup_draft_clear") {
        queries.deleteDraft()
    }

    private fun write(operation: String, block: () -> Unit): Either<DomainError, Unit> {
        val span = tracer.startSpan(operation, operation)
        return try {
            runCatchingCancellable(block).fold(
                onSuccess = { Unit.right() },
                onFailure = { e ->
                    logger.error(TAG, "${operation}_failed", fields = mapOf(ERROR to e::class.simpleName))
                    DomainError.PersistenceFailure(e.message).left()
                },
            )
        } finally {
            tracer.endSpan(span)
        }
    }

    private companion object {
        const val TAG = "SETUP_DRAFT"
        const val ERROR = "error"
    }
}

/** An unknown fuel name (from a later build) reads as unanswered rather than failing. */
private fun Setup_draft.toDomain(): SetupDraft = SetupDraft(
    make = make,
    modelName = model_name,
    modelVariant = model_variant,
    year = year?.toInt(),
    fuel = fuel_type?.let { name -> FuelType.entries.firstOrNull { it.name == name } },
    updatedAt = Instant.parse(updated_at),
)
