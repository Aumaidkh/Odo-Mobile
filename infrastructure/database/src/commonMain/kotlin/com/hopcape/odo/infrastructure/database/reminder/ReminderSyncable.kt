package com.hopcape.odo.infrastructure.database.reminder

import com.hopcape.odo.core.data.reminder.ReminderDto
import com.hopcape.odo.core.sync.SyncEntity
import com.hopcape.odo.core.sync.Syncable
import com.hopcape.odo.core.sync.Synchronizer
import com.hopcape.odo.infrastructure.database.sync.SyncRunner

/**
 * `ReminderRepositoryImpl`'s sync half, split out because the SQLDelight-backed
 * [SyncRunner] it wraps cannot live in `:core:data` without a dependency cycle —
 * `SyncRunner` and `ReminderSyncTable` both live here, next to the database they need.
 */
internal class ReminderSyncable(
    private val runner: SyncRunner<ReminderDto>,
) : Syncable {

    override val entity: SyncEntity = SyncEntity.REMINDERS

    override suspend fun syncWith(synchronizer: Synchronizer): Boolean = runner.run(synchronizer)
}
