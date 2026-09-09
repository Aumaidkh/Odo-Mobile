package com.hopcape.odo.infrastructure.database.challan

import com.hopcape.odo.core.domain.car.model.RegistrationNumber
import com.hopcape.odo.core.domain.challan.model.Challan
import com.hopcape.odo.core.domain.challan.model.ChallanId
import com.hopcape.odo.core.domain.challan.model.ChallanStatus
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.infrastructure.database.db.Challans
import kotlinx.datetime.LocalDate

/**
 * DB row → domain. The boundary where a generated [Challans] row becomes a [Challan]; the
 * domain never sees the row type.
 *
 * `null` for a row that no longer parses (a status value from a build yet to exist, a
 * mangled date). The repository already skips-and-reports unusable *remote* rows; a local
 * row can only have arrived through that gate, so an unreadable one here is corruption or
 * skew — and it costs only itself, never the list.
 */
internal fun Challans.toDomainOrNull(): Challan? {
    val plate = RegistrationNumber.of(reg_no) ?: return null
    val amount = Amount.of(amount_paise).getOrNull() ?: return null
    val issued = runCatching { LocalDate.parse(issued_on) }.getOrNull() ?: return null
    val parsedStatus = runCatching { ChallanStatus.valueOf(status) }.getOrNull() ?: return null
    return Challan(
        id = ChallanId(id),
        regNo = plate,
        violation = violation,
        amount = amount,
        location = location,
        issuedOn = issued,
        status = parsedStatus,
        courtName = court_name,
        nextHearingOn = next_hearing_on?.let { raw -> runCatching { LocalDate.parse(raw) }.getOrNull() },
    )
}
