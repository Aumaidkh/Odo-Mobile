package com.hopcape.odo.core.domain.challan.repository

import arrow.core.Either
import com.hopcape.odo.core.domain.car.model.RegistrationNumber
import com.hopcape.odo.core.domain.challan.model.Challan
import com.hopcape.odo.core.domain.challan.model.ChallanLookup
import com.hopcape.odo.core.domain.shared.DomainError
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

/**
 * Port for a vehicle's challans. The implementation lives in `:core:data` (local cache as
 * what screens read, a remote records source as the authority); the domain stays ignorant
 * of both.
 *
 * Two different questions live here on purpose:
 *
 *  - **The owner's own car** — [observe] / [refresh] / [markAllPendingPaid]. Cached
 *    locally so the garage row and the list answer instantly and offline, refreshed
 *    against the source when stale or asked.
 *  - **Anyone's plate** — [lookup]. Remote-only and ephemeral: a buyer's check on a
 *    stranger's vehicle is shown once and never written anywhere
 *    (docs: challan mockup 7, "Nothing saved — result is shown once").
 *
 * Swapping today's Supabase-backed source for a government API later is a new adapter
 * behind `ChallanRemoteDataSource` in `:core:data` — nothing above this port moves.
 */
interface ChallanRepository {

    /** The cached challans on [regNo], every status — newest first. */
    fun observe(regNo: RegistrationNumber): Flow<List<Challan>>

    /** When the records were last checked for [regNo]; `null` until the first check. */
    fun observeLastChecked(regNo: RegistrationNumber): Flow<Instant?>

    /**
     * Ask the records source afresh and replace the cache for [regNo].
     *
     * [DomainError.ChallanRecordsUnreachable] when the source is down — the caller shows
     * the last known result with its age rather than pretending to know nothing.
     */
    suspend fun refresh(regNo: RegistrationNumber): Either<DomainError, Unit>

    /**
     * "I've already paid these" — mark every online-payable challan on [regNo] paid,
     * locally at once and best-effort at the source. Court cases are untouched: a court
     * settles those, not a claim in an app.
     */
    suspend fun markAllPendingPaid(regNo: RegistrationNumber): Either<DomainError, Unit>

    /** One-off, remote-only check of an arbitrary [regNo]; persists nothing. */
    suspend fun lookup(regNo: RegistrationNumber): Either<DomainError, ChallanLookup>
}
