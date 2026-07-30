package com.hopcape.odo.feature.servicelog.presentation.ui.components

import androidx.compose.runtime.Composable
import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import com.hopcape.odo.feature.servicelog.presentation.state.WorkDone
import com.hopcape.odo.feature.servicelog.resources.Res
import com.hopcape.odo.feature.servicelog.resources.sl_cat_ac
import com.hopcape.odo.feature.servicelog.resources.sl_cat_battery
import com.hopcape.odo.feature.servicelog.resources.sl_cat_brakes
import com.hopcape.odo.feature.servicelog.resources.sl_cat_electrical
import com.hopcape.odo.feature.servicelog.resources.sl_cat_general_service
import com.hopcape.odo.feature.servicelog.resources.sl_cat_oil_change
import com.hopcape.odo.feature.servicelog.resources.sl_cat_other
import com.hopcape.odo.feature.servicelog.resources.sl_cat_suspension
import com.hopcape.odo.feature.servicelog.resources.sl_cat_tyres
import org.jetbrains.compose.resources.stringResource

/**
 * The localized name of a "what was done" tag. The single place the category vocabulary
 * becomes copy — the form's chips and every work-done line read it from here, so a category
 * can never be spelled two ways across the feature.
 */
@Composable
internal fun categoryLabel(category: ServiceCategory): String = stringResource(
    when (category) {
        ServiceCategory.OIL_CHANGE -> Res.string.sl_cat_oil_change
        ServiceCategory.BRAKES -> Res.string.sl_cat_brakes
        ServiceCategory.TYRES -> Res.string.sl_cat_tyres
        ServiceCategory.AC -> Res.string.sl_cat_ac
        ServiceCategory.BATTERY -> Res.string.sl_cat_battery
        ServiceCategory.SUSPENSION -> Res.string.sl_cat_suspension
        ServiceCategory.ELECTRICAL -> Res.string.sl_cat_electrical
        ServiceCategory.GENERAL_SERVICE -> Res.string.sl_cat_general_service
        ServiceCategory.OTHER -> Res.string.sl_cat_other
    },
)

/**
 * The work-done line as one string, or `null` when there is nothing to say. Resolved here
 * rather than in a ViewModel because [WorkDone.Tagged] is copy (see [WorkDone]).
 */
@Composable
internal fun WorkDone.asString(): String? = when (this) {
    WorkDone.Unspecified -> null
    is WorkDone.Described -> labels.joinToString(WORK_SEPARATOR)
    // Resolved through an inline `map` — `joinToString`'s transform is not inline, so a
    // composable lookup can't run inside it.
    is WorkDone.Tagged -> categories.map { categoryLabel(it) }.joinToString(WORK_SEPARATOR)
}

/** Joins several jobs into one line — "Oil change + oil filter". */
private const val WORK_SEPARATOR = " + "
