package com.hopcape.odo.feature.timeline.ui

import androidx.compose.runtime.Composable
import com.hopcape.odo.core.domain.activity.model.ActivityEvent
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import com.hopcape.odo.core.domain.servicelog.model.WorkDone
import com.hopcape.odo.core.domain.shared.formatDate
import com.hopcape.odo.feature.timeline.resources.Res
import com.hopcape.odo.feature.timeline.resources.tl_category_ac
import com.hopcape.odo.feature.timeline.resources.tl_category_battery
import com.hopcape.odo.feature.timeline.resources.tl_category_brakes
import com.hopcape.odo.feature.timeline.resources.tl_category_electrical
import com.hopcape.odo.feature.timeline.resources.tl_category_general_service
import com.hopcape.odo.feature.timeline.resources.tl_category_oil_change
import com.hopcape.odo.feature.timeline.resources.tl_category_other
import com.hopcape.odo.feature.timeline.resources.tl_category_suspension
import com.hopcape.odo.feature.timeline.resources.tl_category_tyres
import com.hopcape.odo.feature.timeline.resources.tl_doc_added
import com.hopcape.odo.feature.timeline.resources.tl_doc_added_valid_till
import com.hopcape.odo.feature.timeline.resources.tl_doc_insurance
import com.hopcape.odo.feature.timeline.resources.tl_doc_licence
import com.hopcape.odo.feature.timeline.resources.tl_doc_loan
import com.hopcape.odo.feature.timeline.resources.tl_doc_other
import com.hopcape.odo.feature.timeline.resources.tl_doc_puc
import com.hopcape.odo.feature.timeline.resources.tl_doc_rc
import com.hopcape.odo.feature.timeline.resources.tl_doc_renewed
import com.hopcape.odo.feature.timeline.resources.tl_doc_renewed_valid_till
import com.hopcape.odo.feature.timeline.resources.tl_work_unspecified
import org.jetbrains.compose.resources.stringResource

/**
 * The copy for the domain values on the feed. It lives in the UI because that is where
 * `strings.xml` is: [WorkDone.Tagged] and [DocumentType] are domain values whose wording is
 * a product decision, and the domain has no business holding either.
 */

/** The service card's title — the owner's own words when there are any, else the tags. */
@Composable
internal fun workDoneText(workDone: WorkDone): String = when (workDone) {
    is WorkDone.Described -> workDone.labels.joinToString(" + ")
    // Resolved with `map` (inline) before joining: joinToString's transform is not inline,
    // so a @Composable call cannot happen inside it.
    is WorkDone.Tagged -> workDone.categories.map { it.label() }.joinToString(" + ")
    WorkDone.Unspecified -> stringResource(Res.string.tl_work_unspecified)
}

/** "PUC renewed · valid till 30 Nov 2026", or "PUC added · …" for the first one of its type. */
@Composable
internal fun documentText(event: ActivityEvent.DocumentFiled): String {
    val name = stringResource(event.document.labelResource())
    val validTill = event.validTill
    return when {
        validTill != null && event.isRenewal ->
            stringResource(Res.string.tl_doc_renewed_valid_till, name, formatDate(validTill))

        validTill != null -> stringResource(Res.string.tl_doc_added_valid_till, name, formatDate(validTill))
        event.isRenewal -> stringResource(Res.string.tl_doc_renewed, name)
        else -> stringResource(Res.string.tl_doc_added, name)
    }
}

private fun DocumentType.labelResource() = when (this) {
    DocumentType.INSURANCE -> Res.string.tl_doc_insurance
    DocumentType.PUC -> Res.string.tl_doc_puc
    DocumentType.RC -> Res.string.tl_doc_rc
    DocumentType.LICENCE -> Res.string.tl_doc_licence
    DocumentType.LOAN -> Res.string.tl_doc_loan
    DocumentType.OTHER -> Res.string.tl_doc_other
}

@Composable
private fun ServiceCategory.label(): String = stringResource(
    when (this) {
        ServiceCategory.OIL_CHANGE -> Res.string.tl_category_oil_change
        ServiceCategory.BRAKES -> Res.string.tl_category_brakes
        ServiceCategory.TYRES -> Res.string.tl_category_tyres
        ServiceCategory.AC -> Res.string.tl_category_ac
        ServiceCategory.BATTERY -> Res.string.tl_category_battery
        ServiceCategory.SUSPENSION -> Res.string.tl_category_suspension
        ServiceCategory.ELECTRICAL -> Res.string.tl_category_electrical
        ServiceCategory.GENERAL_SERVICE -> Res.string.tl_category_general_service
        ServiceCategory.OTHER -> Res.string.tl_category_other
    },
)
