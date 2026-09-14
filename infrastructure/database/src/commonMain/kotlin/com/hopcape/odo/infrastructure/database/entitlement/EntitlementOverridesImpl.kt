package com.hopcape.odo.infrastructure.database.entitlement

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.hopcape.odo.core.domain.entitlement.EntitlementOverrides
import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import com.hopcape.odo.infrastructure.database.sync.toInstantOrNull
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlin.time.Instant

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
    private val clock: Clock = Clock.System,
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
            .map { rows ->
                val now = clock.now()
                rows.filter { it.expires_at.stillInForceAt(now) }
                    .associate { it.feature to (it.granted == 1L) }
            }

    /**
     * Whether an override with this expiry still decides anything.
     *
     * A lapsed one is dropped rather than read as a revoke: the date is the end of the
     * decision, not a decision of its own. No date means it never lapses, and so does a date
     * that cannot be parsed — putting a paywall in front of a comped owner over a malformed
     * timestamp is the worse of the two mistakes.
     */
    private fun String?.stillInForceAt(now: Instant): Boolean {
        val expiry = this?.toInstantOrNull() ?: return true
        return expiry > now
    }
}
