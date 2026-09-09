package com.hopcape.odo.core.data.challan

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The challan records source, whatever stands behind it.
 *
 * Today that is Odo's own Supabase table (`SupabaseChallanRemoteDataSource`); the plan of
 * record is a government/third-party API later — which becomes a new adapter implementing
 * this same interface, and nothing above it moves. The contract is therefore written for
 * the *real* source's shape: a fetch can legitimately answer "no such vehicle", and any
 * call can fail because the source is down (thrown as an exception; the repository maps
 * it to `DomainError.ChallanRecordsUnreachable`).
 */
interface ChallanRemoteDataSource {

    /** Everything the source knows about [regNo] — including whether it knows it at all. */
    suspend fun fetch(regNo: String): ChallanFetchDto

    /**
     * Tell the source the owner says every online-payable challan on [regNo] is settled.
     * Best-effort against today's Supabase source; a real government source would drop
     * this (payment state is theirs) and the repository already treats it as advisory.
     */
    suspend fun markAllPendingPaid(regNo: String)
}

/** The source's whole answer for one plate. */
@Serializable
data class ChallanFetchDto(
    /** False when the plate itself is not in the records — a typo or a brand-new vehicle. */
    @SerialName("vehicle_known") val vehicleKnown: Boolean,
    @SerialName("challans") val challans: List<ChallanDto>,
)

/**
 * The wire shape of one challan — snake_case to match the Postgres columns
 * (the `challans` migration under `supabase/migrations/`).
 */
@Serializable
data class ChallanDto(
    /** The challan number itself — globally unique already. */
    @SerialName("id") val id: String,
    @SerialName("reg_no") val regNo: String,
    @SerialName("violation") val violation: String,
    @SerialName("amount_paise") val amountPaise: Long,
    @SerialName("location") val location: String? = null,
    /** ISO date, `2026-08-14`. */
    @SerialName("issued_on") val issuedOn: String,
    /** `PENDING` / `PAID` / `IN_COURT` — the CHECK constraint on the table. */
    @SerialName("status") val status: String,
    @SerialName("court_name") val courtName: String? = null,
    @SerialName("next_hearing_on") val nextHearingOn: String? = null,
)
