package com.hopcape.odo.core.domain.cost.model

import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory

/**
 * What a rupee of running cost was spent on — the buckets the "where it goes" breakdown
 * shows.
 *
 * Coarser than [ServiceCategory] on purpose: nine workshop job types answer "what was
 * done", three buckets answer "where is the money going", and only the second question
 * fits a cost screen.
 *
 * [FUEL] never comes from a service log — fuel is not logged at all (the PRD drops manual
 * fuel entry as friction), it is estimated from distance driven, the city's fuel price and
 * the car's assumed efficiency. So [of] maps the logged categories onto the other two.
 */
enum class SpendCategory {
    FUEL,
    SERVICE,
    REPAIRS,
    ;

    companion object {
        /**
         * Which bucket a logged job type belongs to. Routine upkeep — an oil change, a
         * general service, tyres — is [SERVICE]; something that broke and was put right is
         * [REPAIRS]. [ServiceCategory.OTHER] falls to [SERVICE] rather than inventing a
         * fourth bucket for it.
         */
        fun of(category: ServiceCategory): SpendCategory = when (category) {
            ServiceCategory.BRAKES,
            ServiceCategory.SUSPENSION,
            ServiceCategory.ELECTRICAL,
            ServiceCategory.AC,
            ServiceCategory.BATTERY,
            -> REPAIRS

            ServiceCategory.OIL_CHANGE,
            ServiceCategory.GENERAL_SERVICE,
            ServiceCategory.TYRES,
            ServiceCategory.OTHER,
            -> SERVICE
        }

        /**
         * The bucket for a whole entry, from the tags it carries. One repair tag makes the
         * entry a repair — a visit that replaced the brake pads *and* changed the oil is
         * remembered for the brakes. An untagged entry reads as [SERVICE], the same default
         * the garage's history filter uses, so it cannot disappear from the breakdown.
         */
        fun forEntry(categories: Set<ServiceCategory>): SpendCategory =
            if (categories.any { of(it) == REPAIRS }) REPAIRS else SERVICE
    }
}
