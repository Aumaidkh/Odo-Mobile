package com.hopcape.odo.infrastructure.database.entitlement

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.hopcape.odo.core.domain.entitlement.EntitlementOverrides
import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * [EntitlementOverrides] over the local `entitlement_override` mirror.
 *
 * Reads the device's copy rather than the server, so an owner who was granted Pro
 * yesterday still has it in a tunnel today. The sync engine is what keeps the
 * copy current.
 */
internal class EntitlementOverridesImpl(
    private val database: OdoDatabase,
    private val ownerId: () -> String?,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : EntitlementOverrides {

    override fun observe(): Flow<Map<String, Boolean>> =
        database.entitlementOverrideQueries
            // A device with no owner yet has no overrides, and a query with a null
            // id would match nothing anyway — the empty string keeps the SQL happy
            // and says the same thing.
            .selectForOwner(ownerId().orEmpty())
            .asFlow()
            .mapToList(dispatcher)
            .map { rows -> rows.associate { it.feature to (it.granted == 1L) } }
}
