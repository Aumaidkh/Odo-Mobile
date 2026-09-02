package com.hopcape.odo.infrastructure.database.challan

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.hopcape.odo.core.data.challan.ChallanLocalDataSource
import com.hopcape.odo.core.domain.challan.model.Challan
import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Instant

/**
 * SQLDelight-backed [ChallanLocalDataSource].
 *
 * [replaceAll] is one transaction: delete the plate's rows, insert the fresh answer,
 * stamp the check. A reader never sees the half-empty middle, and a crash between the
 * statements leaves the previous answer intact rather than a torn one.
 */
internal class SqlDelightChallanLocalDataSource(
    private val database: OdoDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ChallanLocalDataSource {

    private val queries get() = database.challanQueries

    override suspend fun replaceAll(regNo: String, challans: List<Challan>, checkedAt: Instant) {
        database.transaction {
            queries.deleteByRegNo(regNo)
            challans.forEach { challan ->
                queries.insertChallan(
                    id = challan.id.value,
                    reg_no = challan.regNo.value,
                    violation = challan.violation,
                    amount_paise = challan.amount.paise,
                    location = challan.location,
                    issued_on = challan.issuedOn.toString(),
                    status = challan.status.name,
                    court_name = challan.courtName,
                    next_hearing_on = challan.nextHearingOn?.toString(),
                )
            }
            val stamp = checkedAt.toString()
            queries.insertCheck(reg_no = regNo, checked_at = stamp)
            queries.updateCheck(checkedAt = stamp, regNo = regNo)
        }
    }

    override fun observe(regNo: String): Flow<List<Challan>> =
        queries.selectByRegNo(regNo)
            .asFlow()
            .mapToList(dispatcher)
            .map { rows -> rows.mapNotNull { it.toDomainOrNull() } }

    override fun observeLastChecked(regNo: String): Flow<Instant?> =
        queries.selectLastChecked(regNo)
            .asFlow()
            .mapToOneOrNull(dispatcher)
            .map { raw -> raw?.let { runCatching { Instant.parse(it) }.getOrNull() } }

    override suspend fun markAllPendingPaid(regNo: String) {
        queries.markAllPendingPaid(regNo)
    }
}
