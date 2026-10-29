package com.hopcape.odo.core.data.reminder

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.data.observability.DataTelemetry
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.reminder.model.DeclaredExpiry
import com.hopcape.odo.core.domain.reminder.repository.DeclaredExpiryRepository
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.sync.SyncReason
import com.hopcape.odo.core.sync.SyncScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

internal class DeclaredExpiryRepositoryImpl(
    private val local: DeclaredExpiryLocalDataSource,
    private val telemetry: DataTelemetry,
    private val scheduler: SyncScheduler,
) : DeclaredExpiryRepository {

    override fun observe(carId: CarId): Flow<List<DeclaredExpiry>> =
        local.observeByCar(carId).catch { cause ->
            telemetry.crashed(DataTelemetry.REMINDER, OP_OBSERVE, cause, carId.value)
            emit(emptyList())
        }

    override suspend fun declare(expiry: DeclaredExpiry): Either<DomainError, Unit> =
        telemetry.span(DataTelemetry.REMINDER, OP_DECLARE, expiry.id) {
            try {
                local.replace(expiry)
                runCatching { scheduler.requestSync(SyncReason.LocalWrite) }
                Unit.right()
            } catch (e: Exception) {
                telemetry.crashed(DataTelemetry.REMINDER, OP_DECLARE, e, expiry.id)
                DomainError.PersistenceFailure(e.message).left()
            }
        }

    private companion object {
        const val OP_OBSERVE = "observeDeclared"
        const val OP_DECLARE = "declare"
    }
}
