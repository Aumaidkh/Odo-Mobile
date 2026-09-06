package com.hopcape.odo.infrastructure.database.scan

import com.hopcape.odo.core.domain.scan.entitlement.CheckUsage
import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The bill-check tally, in its own table.
 *
 * One pinned row rather than a row per period: the cap is a lifetime one, so there is nothing
 * to key on. Insert-then-update in one transaction, because SQLite 3.18 — what minSdk 26
 * ships — has no UPSERT.
 */
internal class SqlDelightCheckUsage(
    private val database: OdoDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : CheckUsage {

    private val queries get() = database.billCheckUsageQueries

    /** No row yet is no checks spent, which is what a fresh install and a wipe both mean. */
    override suspend fun used(): Int = withContext(dispatcher) {
        queries.countAll().executeAsOneOrNull()?.toInt() ?: 0
    }

    override suspend fun recordCheck() = withContext(dispatcher) {
        database.transaction {
            queries.start()
            queries.increment()
        }
    }
}
