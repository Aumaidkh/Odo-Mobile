package com.hopcape.odo.core.domain.servicelog.model

import arrow.core.Either
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.DomainError

/**
 * One priced line on a service — an optional itemisation of the entry's total (e.g.
 * "Engine oil (5W-30)" · [ServiceCategory.OIL_CHANGE] · Rs. 1,900). Each line is tagged
 * with a [ServiceCategory] so the fairness check can compare it against the right city
 * average.
 *
 * Entered manually (no bill scanning). The entry's [ServiceLogEntry.totalAmount] stays
 * authoritative — lines are a breakdown and are not forced to sum to it.
 */
data class ServiceLogLineItem(
    val label: String?,
    val category: ServiceCategory,
    val amount: Amount,
) {
    companion object {
        fun of(label: String?, category: ServiceCategory, amountPaise: Long?): Either<DomainError, ServiceLogLineItem> =
            Amount.of(amountPaise).map { amount ->
                ServiceLogLineItem(label = label?.trim()?.ifBlank { null }, category = category, amount = amount)
            }
    }
}

/**
 * Raw, unvalidated line-item input from the form — validated into a [ServiceLogLineItem]
 * by [ServiceLogEntry.create].
 */
data class ServiceLogLineItemDraft(
    val label: String?,
    val category: ServiceCategory,
    val amountPaise: Long?,
)
