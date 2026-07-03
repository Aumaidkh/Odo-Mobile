package com.hopcape.odo.core.domain.fairness.model

import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId

/**
 * A user's report that a flagged service was an overcharge — the "Report this
 * overcharge" action. Feeds back into the fairness pool / support, keyed to the entry
 * (and optionally the specific [category] line).
 */
data class OverchargeReport(
    val logId: ServiceLogId,
    val category: ServiceCategory? = null,
    val note: String? = null,
)
