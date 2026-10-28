package com.hopcape.odo.infrastructure.database.reminder

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.hopcape.odo.core.data.reminder.DeclaredExpiryLocalDataSource
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.reminder.model.DeclaredExpiry
import com.hopcape.odo.core.domain.reminder.model.ReminderKind
import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlin.time.Clock

internal class SqlDelightDeclaredExpiryLocalDataSource(
    private val database: OdoDatabase,
    private val clock: Clock = Clock.System,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : DeclaredExpiryLocalDataSource {

    private val queries get() = database.reminderQueries

    override fun observeByCar(carId: CarId): Flow<List<DeclaredExpiry>> =
        queries.selectDeclaredByCar(carId.value).asFlow().mapToList(dispatcher).map { rows ->
            rows.mapNotNull { row ->
                // A kind this build does not know is skipped rather than guessed.
                val kind = ReminderKind.entries.firstOrNull { it.name == row.reminder_type } ?: return@mapNotNull null
                val dueOn = runCatching { LocalDate.parse(row.due_date) }.getOrNull() ?: return@mapNotNull null
                DeclaredExpiry(row.id, carId, OwnerId(row.owner_id), kind, dueOn)
            }
        }

    override suspend fun replace(expiry: DeclaredExpiry) {
        val now = clock.now().toString()
        database.transaction {
            queries.softDeleteDeclared(now = now, carId = expiry.carId.value, reminderType = expiry.kind.name)
            queries.insertDeclared(
                id = expiry.id,
                carId = expiry.carId.value,
                ownerId = expiry.ownerId.value,
                reminderType = expiry.kind.name,
                dueDate = expiry.dueOn.toString(),
                now = now,
            )
        }
    }
}
