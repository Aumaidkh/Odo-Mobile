package com.hopcape.odo.feature.garage.domain.model

import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.servicelog.model.VerificationStatus
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.Distance
import com.hopcape.odo.core.domain.shared.sum
import kotlinx.datetime.LocalDate

/**
 * One logged service as the garage's home base needs it — the inline history under the
 * car card, not the service-log feature's full record.
 *
 * Garage-specific by design: it carries the handful of facts a summary row shows and
 * nothing more (no line items, no bill, no fairness verdict). What it *is* built from is
 * shared kernel — [ServiceLogId], [Amount], [Distance], [VerificationStatus],
 * [ServiceCategory], [LocalDate] — so a row can be opened in the service-log feature by
 * id, and rupees can never be confused with kilometres.
 */
internal data class ServiceHistoryEntry(
    val id: ServiceLogId,
    /** One-line "what was done", e.g. "Front brake pads". */
    val workDone: String,
    val servicedOn: LocalDate,
    val odometer: Distance,
    val amount: Amount,
    val verification: VerificationStatus,
    val category: ServiceCategory,
) {
    /** Bill-backed, so it carries a Verified badge (PRD trust model). */
    val isVerified: Boolean get() = verification == VerificationStatus.VERIFIED
}

/** The entries under a chip, newest first — the list the history section renders. */
internal fun List<ServiceHistoryEntry>.matching(facet: ServiceFacet): List<ServiceHistoryEntry> =
    filter { facet.accepts(it.category) }

/** What these entries cost in total — money math stays in [Amount], never a Double. */
internal fun List<ServiceHistoryEntry>.totalSpent(): Amount = map { it.amount }.sum()

/** How many of these are bill-backed — the trust half of the history summary. */
internal fun List<ServiceHistoryEntry>.verifiedCount(): Int = count { it.isVerified }
