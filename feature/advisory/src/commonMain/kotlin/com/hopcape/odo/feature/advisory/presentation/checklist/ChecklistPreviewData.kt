package com.hopcape.odo.feature.advisory.presentation.checklist

import com.hopcape.odo.core.domain.shared.AmountRange
import com.hopcape.odo.feature.advisory.domain.checklist.ChecklistCost
import com.hopcape.odo.feature.advisory.domain.checklist.ChecklistItem
import com.hopcape.odo.feature.advisory.domain.checklist.ChecklistReason
import com.hopcape.odo.feature.advisory.domain.checklist.CounterUpsell
import com.hopcape.odo.feature.advisory.domain.checklist.ItemLabel
import com.hopcape.odo.feature.advisory.domain.checklist.PreServiceChecklist
import com.hopcape.odo.feature.advisory.domain.checklist.ServiceChecklist

/** The scene the plan is written around: a three-year-old i20 at 42,000 km. */
internal val previewChecklist = ServiceChecklist(
    carName = "i20",
    ageYears = 3,
    odometerKm = 42_000,
    checklist = PreServiceChecklist(
        due = listOf(
            ChecklistItem(
                slug = "engine_oil",
                label = ItemLabel.FromSchedule("Engine oil + filter"),
                reason = ChecklistReason.LastDoneKmAgo(11_000),
            ),
            ChecklistItem(
                slug = "air_filter",
                label = ItemLabel.FromSchedule("Air filter"),
                reason = ChecklistReason.LastDoneKmAgo(22_000),
                servicesSince = 2,
            ),
            ChecklistItem(
                slug = "brake_fluid",
                label = ItemLabel.FromSchedule("Brake fluid"),
                reason = ChecklistReason.LastDoneMonthsAgo(36),
            ),
        ),
        notYet = listOf(
            ChecklistItem(
                slug = "coolant",
                label = ItemLabel.FromSchedule("Coolant flush"),
                reason = ChecklistReason.KmToGo(20_000),
            ),
            ChecklistItem(
                slug = CounterUpsell.INJECTOR_CLEANING.slug,
                label = ItemLabel.Upsell(CounterUpsell.INJECTOR_CLEANING),
                reason = ChecklistReason.NotInSchedule,
            ),
        ),
    ),
    cost = ChecklistCost(
        range = AmountRange.ofPaise(650_000, 820_000),
        pricedItems = 3,
        dueItems = 3,
    ),
)
