package com.hopcape.odo.feature.costtracker.presentation

import com.hopcape.odo.core.domain.cost.model.SpendCategory

/**
 * Test tags for the cost-tracker controls an end-to-end test cannot reach by the words on
 * them.
 *
 * Deliberately few. Copy is what an owner sees, so a test that finds the period chips by
 * "3M" is testing the product; a tag is only added where the words repeat or where a test
 * has to assert something is *absent*. Every category row renders a rupee figure and a
 * per-km figure in the same shapes, and the trend badge is a bare percentage.
 *
 * Public because `:androidApp`'s instrumented tests reference these, which is the only
 * reason anything in this module is public besides the Koin module and the analytics schema.
 */
object CostTrackerTestTags {

    /** The headline rate. Tagged so a test can read it back, not just match a literal. */
    const val COST_PER_KM: String = "cost_per_km"

    /** The trend badge — tagged because "no badge" is a thing tests have to assert. */
    const val TREND_BADGE: String = "cost_trend_badge"

    /** The line that says what the fuel half was estimated from. */
    const val FUEL_NOTE: String = "cost_fuel_note"

    /** The sheet's price field, which collapses to a label every fuel type shares. */
    const val FUEL_RATE_FIELD: String = "cost_fuel_rate_field"

    /** One bucket's row in the "where it goes" breakdown. */
    fun categoryRow(category: SpendCategory): String = "cost_category_row_${category.name}"
}
