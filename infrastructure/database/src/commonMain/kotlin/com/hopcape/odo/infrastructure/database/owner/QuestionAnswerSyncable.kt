package com.hopcape.odo.infrastructure.database.owner

import com.hopcape.odo.core.data.owner.QuestionAnswerDto
import com.hopcape.odo.core.sync.SyncEntity
import com.hopcape.odo.core.sync.Syncable
import com.hopcape.odo.core.sync.Synchronizer
import com.hopcape.odo.infrastructure.database.sync.SyncRunner

/**
 * `QuestionnaireRepositoryImpl`'s sync half, split out because the SQLDelight-backed
 * [SyncRunner] it wraps cannot live in `:core:data` without a dependency cycle — `SyncRunner`
 * and [QuestionAnswerSyncTable] both live here, next to the database they need.
 */
internal class QuestionAnswerSyncable(
    private val runner: SyncRunner<QuestionAnswerDto>,
) : Syncable {

    override val entity: SyncEntity = SyncEntity.PROFILE_ANSWERS

    override suspend fun pushTo(synchronizer: Synchronizer): Boolean = runner.push(synchronizer)

    override suspend fun pullFrom(synchronizer: Synchronizer): Boolean = runner.pull(synchronizer)
}
