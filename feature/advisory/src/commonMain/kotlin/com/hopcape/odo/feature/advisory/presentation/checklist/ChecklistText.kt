package com.hopcape.odo.feature.advisory.presentation.checklist

import androidx.compose.runtime.Composable
import com.hopcape.odo.core.designsystem.units.LocalOdoDistanceFormat
import com.hopcape.odo.feature.advisory.domain.checklist.ChecklistItem
import com.hopcape.odo.feature.advisory.domain.checklist.ChecklistReason
import com.hopcape.odo.feature.advisory.domain.checklist.CounterUpsell
import com.hopcape.odo.feature.advisory.domain.checklist.ItemLabel
import com.hopcape.odo.feature.advisory.resources.Res
import com.hopcape.odo.feature.advisory.resources.adv_check_km_to_go
import com.hopcape.odo.feature.advisory.resources.adv_check_last_done_km
import com.hopcape.odo.feature.advisory.resources.adv_check_last_done_months
import com.hopcape.odo.feature.advisory.resources.adv_check_months_to_go
import com.hopcape.odo.feature.advisory.resources.adv_check_never_recorded
import com.hopcape.odo.feature.advisory.resources.adv_check_not_in_schedule
import com.hopcape.odo.feature.advisory.resources.adv_check_services_on
import com.hopcape.odo.feature.advisory.resources.adv_check_upsell_ac_disinfectant
import com.hopcape.odo.feature.advisory.resources.adv_check_upsell_engine_flush
import com.hopcape.odo.feature.advisory.resources.adv_check_upsell_injector_cleaning
import com.hopcape.odo.feature.advisory.resources.adv_check_upsell_underbody_coating
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The copy for one row.
 *
 * Kept out of the composables so the mapping from a number to a sentence is one file, and out
 * of the domain so the wording is a display decision. Distances go through
 * [LocalOdoDistanceFormat] — an owner who reads in miles reads this in miles too.
 */
@Composable
internal fun ChecklistItem.title(): String = when (val l = label) {
    is ItemLabel.FromSchedule -> l.name
    is ItemLabel.Upsell -> stringResource(l.upsell.string())
}

/**
 * The line under the name.
 *
 * "Two services on" wins over "last done 11,000 km ago" when the record shows both: an owner
 * counts services, not kilometres, and it is the sentence the counter cannot argue with.
 */
@Composable
internal fun ChecklistItem.subtitle(): String {
    val distance = LocalOdoDistanceFormat.current
    return when (val r = reason) {
        is ChecklistReason.LastDoneKmAgo ->
            if (servicesSince >= 2) {
                pluralStringResource(Res.plurals.adv_check_services_on, servicesSince, servicesSince)
            } else {
                stringResource(Res.string.adv_check_last_done_km, distance.format(r.km))
            }

        is ChecklistReason.LastDoneMonthsAgo ->
            pluralStringResource(Res.plurals.adv_check_last_done_months, r.months, r.months)

        ChecklistReason.NeverRecorded -> stringResource(Res.string.adv_check_never_recorded)
        is ChecklistReason.KmToGo -> stringResource(Res.string.adv_check_km_to_go, distance.format(r.km))
        is ChecklistReason.MonthsToGo ->
            pluralStringResource(Res.plurals.adv_check_months_to_go, r.months, r.months)
        ChecklistReason.NotInSchedule -> stringResource(Res.string.adv_check_not_in_schedule)
    }
}

private fun CounterUpsell.string() = when (this) {
    CounterUpsell.INJECTOR_CLEANING -> Res.string.adv_check_upsell_injector_cleaning
    CounterUpsell.ENGINE_FLUSH -> Res.string.adv_check_upsell_engine_flush
    CounterUpsell.AC_DISINFECTANT -> Res.string.adv_check_upsell_ac_disinfectant
    CounterUpsell.UNDERBODY_COATING -> Res.string.adv_check_upsell_underbody_coating
}
