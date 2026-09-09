package com.hopcape.odo.core.data.challan

import com.hopcape.odo.core.domain.challan.model.Challan
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

/**
 * Local cache of a vehicle's challans plus the "when did we last ask" stamp. Hides the
 * SQLDelight database from [ChallanRepositoryImpl]: this owns how rows are read and
 * written, the repository owns what an operation means (error mapping, telemetry).
 *
 * A cache of the records source's answer, not owner content — no sync columns, no soft
 * delete (the `fuel_price` precedent). [replaceAll] swaps the whole answer for one plate
 * in a transaction, because the source's reply *is* the truth for that plate: a challan
 * missing from the fresh answer was paid or withdrawn, and keeping it would show a debt
 * that no longer exists.
 *
 * Writes throw on storage failure; the repository turns that into a
 * `DomainError.PersistenceFailure`.
 */
interface ChallanLocalDataSource {

    /** Replace every cached challan for [regNo] with [challans] and stamp [checkedAt]. */
    suspend fun replaceAll(regNo: String, challans: List<Challan>, checkedAt: Instant)

    /** The cached challans on [regNo], newest first, as they change. */
    fun observe(regNo: String): Flow<List<Challan>>

    /** When [regNo] was last checked against the source; `null` until the first check. */
    fun observeLastChecked(regNo: String): Flow<Instant?>

    /** Flip every `PENDING` row on [regNo] to `PAID`. Court cases are untouched. */
    suspend fun markAllPendingPaid(regNo: String)
}
