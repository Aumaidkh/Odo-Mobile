package com.hopcape.odo.core.domain.challan.model

import com.hopcape.odo.core.domain.car.model.RegistrationNumber
import com.hopcape.odo.core.domain.shared.Amount
import kotlinx.datetime.LocalDate
import kotlin.jvm.JvmInline

/**
 * One traffic challan on a vehicle, as the records source reported it.
 *
 * External reference data, not owner-authored content — closer kin to `FuelPrice` than to
 * a service log. The records source (today Odo's own Supabase table, later a government
 * API behind the same port) is the authority; the local table is a cache of its answer,
 * which is why this is a plain data class rather than a `create`-validated aggregate:
 * there is no user input here to argue with, only rows to mirror or skip.
 *
 * [id] is the challan number itself (e.g. `MH1220260814004521`) — the one identifier
 * every party already shares, which is what makes the cache upsert idempotent.
 */
data class Challan(
    val id: ChallanId,
    val regNo: RegistrationNumber,
    /** What the notice says happened — "Red light violation", "No parking". */
    val violation: String,
    val amount: Amount,
    /** Where it was issued ("Baner Road, Pune"); the source may not carry one. */
    val location: String?,
    val issuedOn: LocalDate,
    val status: ChallanStatus,
    /** Which court holds it — only when [status] is [ChallanStatus.IN_COURT]. */
    val courtName: String?,
    /** The next hearing date — only when [status] is [ChallanStatus.IN_COURT]. */
    val nextHearingOn: LocalDate?,
) {
    /** Whether this challan still asks the owner for money they can pay online. */
    val isPayableOnline: Boolean get() = status == ChallanStatus.PENDING
}

/** The challan number, as issued — already globally unique. */
@JvmInline
value class ChallanId(val value: String)

/**
 * Where a challan stands. `IN_COURT` is deliberately not a flavour of pending: a court
 * case cannot be paid online, so every "total pending" figure and pay CTA must exclude
 * it — the type keeps that rule in one place ([Challan.isPayableOnline]).
 */
enum class ChallanStatus { PENDING, PAID, IN_COURT }
