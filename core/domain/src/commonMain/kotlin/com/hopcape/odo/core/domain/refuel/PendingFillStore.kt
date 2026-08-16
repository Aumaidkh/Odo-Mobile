package com.hopcape.odo.core.domain.refuel

import com.hopcape.odo.core.domain.shared.Amount
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

/**
 * Port for fills that were detected but never resolved.
 *
 * The reason detection can be trusted at all. A notification is not storage — the owner swipes
 * it away, the shade is cleared, the phone reboots — and Android keeps no record of what was
 * dismissed, so nothing can read it back. Writing the detection here the moment it happens is
 * what turns the notification into a shortcut rather than the only copy.
 *
 * Device-local, like everything else detection owns. A pending fill is a question this phone
 * is waiting to ask; the answer is a `fuel_fills` row, and that is what syncs.
 */
interface PendingFillStore {

    /** Unanswered questions, newest first. */
    fun observeOpen(): Flow<List<PendingFill>>

    /** The same, for a caller with no screen to collect from. */
    suspend fun open(): List<PendingFill>

    /**
     * Write a detection down.
     *
     * Does nothing when [PendingFill.id] is already known, so re-reading a notification that
     * is still in the shade cannot reopen a question the owner has answered, and cannot
     * overwrite a draft on its way to being confirmed.
     */
    suspend fun remember(fill: PendingFill)

    /**
     * Mark a question answered — confirmed as a fill, or rejected as not fuel.
     *
     * The row stays. The listener re-reads the shade whenever it reconnects, and a deleted row
     * would let the same payment be offered again minutes later.
     */
    suspend fun resolve(id: String, at: Instant)

    /** Drop answered rows older than [before]. Housekeeping, so the table cannot grow forever. */
    suspend fun pruneResolved(before: Instant)
}

/**
 * One detected fill, waiting for an answer.
 *
 * [draftPayload] is opaque here: the refuel feature owns the encoding, and this port only has
 * to keep the string intact between the moment it was detected and the moment the owner is
 * asked about it — which may be days and several process deaths later.
 *
 * [merchant] and [amount] are held separately even though the payload contains them, because
 * the list that asks the owner has to render a row without decoding anything.
 */
data class PendingFill(
    val id: String,
    val draftPayload: String,
    val merchant: String?,
    val amount: Amount?,
    val detectedAt: Instant,
)
