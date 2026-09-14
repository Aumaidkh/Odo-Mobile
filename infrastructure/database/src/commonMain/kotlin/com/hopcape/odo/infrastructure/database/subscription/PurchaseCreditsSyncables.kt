package com.hopcape.odo.infrastructure.database.subscription

import com.hopcape.odo.core.data.subscription.CreditSpendDto
import com.hopcape.odo.core.data.subscription.PurchaseClaimDto
import com.hopcape.odo.core.sync.SyncEntity
import com.hopcape.odo.core.sync.Syncable
import com.hopcape.odo.core.sync.Synchronizer
import com.hopcape.odo.infrastructure.database.sync.SyncRunner

/** `purchase_claims` in the sync engine's list. */
internal class PurchaseClaimSyncable(
    private val runner: SyncRunner<PurchaseClaimDto>,
) : Syncable {

    override val entity: SyncEntity = SyncEntity.PURCHASE_CLAIMS

    override suspend fun pushTo(synchronizer: Synchronizer): Boolean = runner.push(synchronizer)

    override suspend fun pullFrom(synchronizer: Synchronizer): Boolean = runner.pull(synchronizer)
}

/** `credit_spends` in the sync engine's list. */
internal class CreditSpendSyncable(
    private val runner: SyncRunner<CreditSpendDto>,
) : Syncable {

    override val entity: SyncEntity = SyncEntity.CREDIT_SPENDS

    override suspend fun pushTo(synchronizer: Synchronizer): Boolean = runner.push(synchronizer)

    override suspend fun pullFrom(synchronizer: Synchronizer): Boolean = runner.pull(synchronizer)
}
