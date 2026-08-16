package com.hopcape.odo.infrastructure.database.refuel

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.hopcape.odo.core.domain.refuel.PendingFill
import com.hopcape.odo.core.domain.refuel.PendingFillStore
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import com.hopcape.odo.infrastructure.database.db.Pending_fills
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Instant

/**
 * SQLDelight-backed [PendingFillStore]. Device-local; the table carries no sync columns.
 */
internal class SqlDelightPendingFillStore(
    private val database: OdoDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : PendingFillStore {

    private val queries get() = database.pendingFillQueries

    override fun observeOpen(): Flow<List<PendingFill>> =
        queries.selectOpenPendingFills()
            .asFlow()
            .mapToList(dispatcher)
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun open(): List<PendingFill> =
        queries.selectOpenPendingFills().executeAsList().map { it.toDomain() }

    override suspend fun remember(fill: PendingFill) {
        queries.rememberPendingFill(
            id = fill.id,
            draftPayload = fill.draftPayload,
            merchant = fill.merchant,
            amountPaise = fill.amount?.paise,
            detectedAt = fill.detectedAt.toString(),
        )
    }

    override suspend fun resolve(id: String, at: Instant) {
        queries.resolvePendingFill(resolvedAt = at.toString(), id = id)
    }

    override suspend fun pruneResolved(before: Instant) {
        queries.deletePendingFillsBefore(before.toString())
    }

    /**
     * A stored amount that will not reconstruct is dropped rather than shown as zero: "Rs. 0"
     * beside a merchant reads as a real payment the owner never made.
     */
    private fun Pending_fills.toDomain() = PendingFill(
        id = id,
        draftPayload = draft_payload,
        merchant = merchant,
        amount = amount_paise?.let { Amount.of(it).getOrNull() },
        detectedAt = Instant.parse(detected_at),
    )
}
