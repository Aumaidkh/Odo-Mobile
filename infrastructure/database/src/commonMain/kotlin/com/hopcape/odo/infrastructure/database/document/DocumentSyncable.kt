package com.hopcape.odo.infrastructure.database.document

import com.hopcape.odo.core.data.document.DocumentDto
import com.hopcape.odo.core.sync.SyncEntity
import com.hopcape.odo.core.sync.Syncable
import com.hopcape.odo.core.sync.Synchronizer
import com.hopcape.odo.infrastructure.database.sync.SyncRunner

/**
 * `DocumentRepositoryImpl`'s sync half, split out because the SQLDelight-backed
 * [SyncRunner] it wraps cannot live in `:core:data` without a dependency cycle —
 * `SyncRunner` and `DocumentSyncTable` both live here, next to the database they need.
 */
internal class DocumentSyncable(
    private val runner: SyncRunner<DocumentDto>,
) : Syncable {

    override val entity: SyncEntity = SyncEntity.DOCUMENTS

    override suspend fun syncWith(synchronizer: Synchronizer): Boolean = runner.run(synchronizer)
}
