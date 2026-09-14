package com.hopcape.odo.infrastructure.database.support

import com.hopcape.odo.core.data.support.IdeaVoteDto
import com.hopcape.odo.core.data.support.SupportTicketDto
import com.hopcape.odo.core.sync.SyncEntity
import com.hopcape.odo.core.sync.Syncable
import com.hopcape.odo.core.sync.Synchronizer
import com.hopcape.odo.infrastructure.database.sync.SyncRunner

/**
 * The sync halves of the two support tables, split out for the reason every other one is: the
 * SQLDelight-backed `SyncRunner` they wrap cannot live in `:core:data` without a cycle.
 */
internal class SupportTicketSyncable(
    private val runner: SyncRunner<SupportTicketDto>,
) : Syncable {

    override val entity: SyncEntity = SyncEntity.SUPPORT_TICKETS

    override suspend fun pushTo(synchronizer: Synchronizer): Boolean = runner.push(synchronizer)

    override suspend fun pullFrom(synchronizer: Synchronizer): Boolean = runner.pull(synchronizer)
}

internal class IdeaVoteSyncable(
    private val runner: SyncRunner<IdeaVoteDto>,
) : Syncable {

    override val entity: SyncEntity = SyncEntity.IDEA_VOTES

    override suspend fun pushTo(synchronizer: Synchronizer): Boolean = runner.push(synchronizer)

    override suspend fun pullFrom(synchronizer: Synchronizer): Boolean = runner.pull(synchronizer)
}
