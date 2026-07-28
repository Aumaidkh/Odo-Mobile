package com.hopcape.odo.feature.garage.domain.model

import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory

/**
 * The garage's service-history filter chips.
 *
 * Not a second category system — a **curated grouping over** the shared kernel's
 * [ServiceCategory]. The garage shows a car's history at a glance, so it offers the three
 * cuts an owner actually thinks in (routine work, tyres, battery) rather than all nine
 * job types; the full breakdown is the service-log feature's job.
 *
 * Garage-specific, so it lives here and not in `:core:domain` — the kernel keeps the
 * categories, this module keeps its opinion about how to group them.
 */
internal enum class ServiceFacet(private val categories: Set<ServiceCategory>) {

    /** Everything on file — the default chip. */
    ALL(emptySet()),

    /** Routine workshop work: a periodic service, oil, brakes, AC, suspension, electrical. */
    SERVICE(
        setOf(
            ServiceCategory.GENERAL_SERVICE,
            ServiceCategory.OIL_CHANGE,
            ServiceCategory.BRAKES,
            ServiceCategory.AC,
            ServiceCategory.SUSPENSION,
            ServiceCategory.ELECTRICAL,
            ServiceCategory.OTHER,
        ),
    ),

    TYRES(setOf(ServiceCategory.TYRES)),

    BATTERY(setOf(ServiceCategory.BATTERY)),
    ;

    /** Whether an entry tagged [category] belongs under this chip. [ALL] takes everything. */
    fun accepts(category: ServiceCategory): Boolean = this == ALL || category in categories
}
