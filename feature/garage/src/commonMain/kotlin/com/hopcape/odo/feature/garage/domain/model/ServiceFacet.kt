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

    /**
     * Whether an entry tagged with [entryCategories] belongs under this chip. [ALL] takes
     * everything.
     *
     * An entry can carry several tags (a periodic service that also replaced the battery),
     * so one tag matching is enough — it shows under both chips, which is what happened.
     *
     * An entry with no tags at all reads as [SERVICE]. That is where [ServiceCategory.OTHER]
     * already sits, and an untagged entry means the same thing: workshop work nobody
     * labelled. The alternative is a row that vanishes from every chip but [ALL], which
     * looks like a bug.
     */
    fun accepts(entryCategories: Set<ServiceCategory>): Boolean = when {
        this == ALL -> true
        entryCategories.isEmpty() -> this == SERVICE
        else -> entryCategories.any { it in categories }
    }
}
