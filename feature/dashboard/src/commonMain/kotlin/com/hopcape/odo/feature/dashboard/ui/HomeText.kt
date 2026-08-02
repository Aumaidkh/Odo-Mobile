package com.hopcape.odo.feature.dashboard.ui

import androidx.compose.runtime.Composable
import com.hopcape.odo.core.designsystem.units.LocalOdoDistanceFormat
import com.hopcape.odo.core.domain.activity.model.ActivityEvent
import com.hopcape.odo.core.domain.alerts.model.CarAttention
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.health.model.HealthBand
import com.hopcape.odo.core.domain.insight.model.CarInsight
import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import com.hopcape.odo.core.domain.servicelog.model.VerificationStatus
import com.hopcape.odo.core.domain.servicelog.model.WorkDone
import com.hopcape.odo.core.domain.shared.formatDate
import com.hopcape.odo.core.domain.shared.formatDayMonth
import com.hopcape.odo.feature.dashboard.resources.Res
import com.hopcape.odo.feature.dashboard.resources.hm_attn_clear
import com.hopcape.odo.feature.dashboard.resources.hm_attn_clear_sub
import com.hopcape.odo.feature.dashboard.resources.hm_attn_doc_expiring
import com.hopcape.odo.feature.dashboard.resources.hm_attn_doc_expiring_sub
import com.hopcape.odo.feature.dashboard.resources.hm_attn_doc_expiring_today
import com.hopcape.odo.feature.dashboard.resources.hm_attn_doc_expiring_tomorrow
import com.hopcape.odo.feature.dashboard.resources.hm_attn_doc_lapsed
import com.hopcape.odo.feature.dashboard.resources.hm_attn_doc_lapsed_sub
import com.hopcape.odo.feature.dashboard.resources.hm_attn_service_due
import com.hopcape.odo.feature.dashboard.resources.hm_attn_service_due_both
import com.hopcape.odo.feature.dashboard.resources.hm_attn_service_due_days
import com.hopcape.odo.feature.dashboard.resources.hm_attn_service_due_km
import com.hopcape.odo.feature.dashboard.resources.hm_attn_service_overdue
import com.hopcape.odo.feature.dashboard.resources.hm_attn_service_overdue_both
import com.hopcape.odo.feature.dashboard.resources.hm_attn_service_overdue_days
import com.hopcape.odo.feature.dashboard.resources.hm_attn_service_overdue_km
import com.hopcape.odo.feature.dashboard.resources.hm_band_excellent
import com.hopcape.odo.feature.dashboard.resources.hm_band_fair
import com.hopcape.odo.feature.dashboard.resources.hm_band_good
import com.hopcape.odo.feature.dashboard.resources.hm_band_poor
import com.hopcape.odo.feature.dashboard.resources.hm_category_ac
import com.hopcape.odo.feature.dashboard.resources.hm_category_battery
import com.hopcape.odo.feature.dashboard.resources.hm_category_brakes
import com.hopcape.odo.feature.dashboard.resources.hm_category_electrical
import com.hopcape.odo.feature.dashboard.resources.hm_category_general_service
import com.hopcape.odo.feature.dashboard.resources.hm_category_oil_change
import com.hopcape.odo.feature.dashboard.resources.hm_category_other
import com.hopcape.odo.feature.dashboard.resources.hm_category_suspension
import com.hopcape.odo.feature.dashboard.resources.hm_category_tyres
import com.hopcape.odo.feature.dashboard.resources.hm_cost_above
import com.hopcape.odo.feature.dashboard.resources.hm_cost_below
import com.hopcape.odo.feature.dashboard.resources.hm_cost_flat
import com.hopcape.odo.feature.dashboard.resources.hm_cost_no_trend
import com.hopcape.odo.feature.dashboard.resources.hm_cost_not_enough
import com.hopcape.odo.feature.dashboard.resources.hm_doc_insurance
import com.hopcape.odo.feature.dashboard.resources.hm_doc_licence
import com.hopcape.odo.feature.dashboard.resources.hm_doc_loan
import com.hopcape.odo.feature.dashboard.resources.hm_doc_other
import com.hopcape.odo.feature.dashboard.resources.hm_doc_puc
import com.hopcape.odo.feature.dashboard.resources.hm_doc_rc
import com.hopcape.odo.feature.dashboard.resources.hm_health_down
import com.hopcape.odo.feature.dashboard.resources.hm_health_first
import com.hopcape.odo.feature.dashboard.resources.hm_health_steady
import com.hopcape.odo.feature.dashboard.resources.hm_health_up
import com.hopcape.odo.feature.dashboard.resources.hm_insight_cost_down
import com.hopcape.odo.feature.dashboard.resources.hm_insight_cost_up
import com.hopcape.odo.feature.dashboard.resources.hm_insight_missing_doc
import com.hopcape.odo.feature.dashboard.resources.hm_insight_no_bills
import com.hopcape.odo.feature.dashboard.resources.hm_insight_no_bills_one
import com.hopcape.odo.feature.dashboard.resources.hm_insight_resale
import com.hopcape.odo.feature.dashboard.resources.hm_overcharge_many
import com.hopcape.odo.feature.dashboard.resources.hm_overcharge_none
import com.hopcape.odo.feature.dashboard.resources.hm_overcharge_one
import com.hopcape.odo.feature.dashboard.resources.hm_recent_car_added
import com.hopcape.odo.feature.dashboard.resources.hm_recent_doc_added
import com.hopcape.odo.feature.dashboard.resources.hm_recent_doc_renewed
import com.hopcape.odo.feature.dashboard.resources.hm_recent_meta
import com.hopcape.odo.feature.dashboard.resources.hm_recent_score
import com.hopcape.odo.feature.dashboard.resources.hm_recent_self_reported
import com.hopcape.odo.feature.dashboard.resources.hm_recent_verified
import com.hopcape.odo.feature.dashboard.resources.hm_work_unspecified
import org.jetbrains.compose.resources.stringResource

/**
 * The copy for the domain values Home renders. It lives in the UI because that is where
 * `strings.xml` is: a [CarAttention], a [CarInsight] and a [HealthBand] are domain values
 * whose wording is a product decision, and the domain has no business holding it.
 */

/** "GOOD" — what the dial calls the number under it. */
@Composable
internal fun bandText(band: HealthBand): String = stringResource(
    when (band) {
        HealthBand.POOR -> Res.string.hm_band_poor
        HealthBand.FAIR -> Res.string.hm_band_fair
        HealthBand.GOOD -> Res.string.hm_band_good
        HealthBand.EXCELLENT -> Res.string.hm_band_excellent
    },
)

/** "Up 6 points this month." — or, with no month-old baseline, what is true instead. */
@Composable
internal fun healthNoteText(delta: Int?): String = when {
    delta == null -> stringResource(Res.string.hm_health_first)
    delta > 0 -> stringResource(Res.string.hm_health_up, delta)
    delta < 0 -> stringResource(Res.string.hm_health_down, -delta)
    else -> stringResource(Res.string.hm_health_steady)
}

/**
 * The cost card's footer.
 *
 * The comparison is against the quarter before, never a city average — the running cost is
 * the owner's own history, and calling it "above average" would claim a benchmark Odo does
 * not have for ₹/km.
 */
@Composable
internal fun costTrendText(percentChange: Int?, hasRate: Boolean): String = when {
    !hasRate -> stringResource(Res.string.hm_cost_not_enough)
    percentChange == null -> stringResource(Res.string.hm_cost_no_trend)
    percentChange > 0 -> stringResource(Res.string.hm_cost_above, percentChange)
    percentChange < 0 -> stringResource(Res.string.hm_cost_below, -percentChange)
    else -> stringResource(Res.string.hm_cost_flat)
}

/** "2 bills flagged", or the honest zero. */
@Composable
internal fun overchargeSubText(count: Int): String = when (count) {
    0 -> stringResource(Res.string.hm_overcharge_none)
    1 -> stringResource(Res.string.hm_overcharge_one)
    else -> stringResource(Res.string.hm_overcharge_many, count)
}

/** The attention card's headline; `null` renders the all-clear title. */
@Composable
internal fun attentionTitle(attention: CarAttention?): String = when (attention) {
    null -> stringResource(Res.string.hm_attn_clear)
    is CarAttention.DocumentLapsed ->
        stringResource(Res.string.hm_attn_doc_lapsed, stringResource(attention.type.labelResource()))

    is CarAttention.DocumentExpiring -> {
        val name = stringResource(attention.type.labelResource())
        when (attention.daysLeft) {
            0 -> stringResource(Res.string.hm_attn_doc_expiring_today, name)
            1 -> stringResource(Res.string.hm_attn_doc_expiring_tomorrow, name)
            else -> stringResource(Res.string.hm_attn_doc_expiring, name, attention.daysLeft)
        }
    }

    is CarAttention.ServiceOverdue -> stringResource(Res.string.hm_attn_service_overdue)
    is CarAttention.ServiceDue -> stringResource(Res.string.hm_attn_service_due)
}

/** The line under the attention headline — the date or the distance behind it. */
@Composable
internal fun attentionSubtitle(attention: CarAttention?): String = when (attention) {
    null -> stringResource(Res.string.hm_attn_clear_sub)
    is CarAttention.DocumentLapsed ->
        stringResource(Res.string.hm_attn_doc_lapsed_sub, formatDate(attention.since))

    is CarAttention.DocumentExpiring ->
        stringResource(Res.string.hm_attn_doc_expiring_sub, formatDate(attention.until))

    is CarAttention.ServiceOverdue -> {
        val km = attention.kmOverdue
        val days = attention.daysOverdue
        val distance = LocalOdoDistanceFormat.current
        when {
            km == null || km == 0 -> stringResource(Res.string.hm_attn_service_overdue_days, days)
            days == 0 -> stringResource(Res.string.hm_attn_service_overdue_km, distance.format(km))
            else -> stringResource(Res.string.hm_attn_service_overdue_both, days, distance.format(km))
        }
    }

    is CarAttention.ServiceDue -> {
        val km = attention.kmLeft
        val days = attention.daysLeft
        val distance = LocalOdoDistanceFormat.current
        when {
            km == null -> stringResource(Res.string.hm_attn_service_due_days, days)
            // Distance alone once the date is far off: a service 900 km away but four
            // months out is a distance problem, not a date one.
            days > DUE_SOON_DAYS_FOR_KM_ONLY -> stringResource(Res.string.hm_attn_service_due_km, distance.format(km))
            else -> stringResource(Res.string.hm_attn_service_due_both, distance.format(km), days)
        }
    }
}

/** The insight card's body. */
@Composable
internal fun insightText(insight: CarInsight): String = when (insight) {
    is CarInsight.ResaleReady -> stringResource(Res.string.hm_insight_resale, insight.serviceCount)
    is CarInsight.CostMoved -> if (insight.percentChange > 0) {
        stringResource(Res.string.hm_insight_cost_up, insight.percentChange)
    } else {
        stringResource(Res.string.hm_insight_cost_down, -insight.percentChange)
    }

    is CarInsight.NoBillsAttached -> if (insight.serviceCount == 1) {
        stringResource(Res.string.hm_insight_no_bills_one)
    } else {
        stringResource(Res.string.hm_insight_no_bills, insight.serviceCount)
    }

    is CarInsight.DocumentMissing ->
        stringResource(Res.string.hm_insight_missing_doc, stringResource(insight.type.labelResource()))
}

/** The recent row's title. */
@Composable
internal fun recentTitle(event: ActivityEvent): String = when (event) {
    is ActivityEvent.Service -> workDoneText(event.workDone)
    is ActivityEvent.DocumentFiled -> {
        val name = stringResource(event.document.labelResource())
        if (event.isRenewal) {
            stringResource(Res.string.hm_recent_doc_renewed, name)
        } else {
            stringResource(Res.string.hm_recent_doc_added, name)
        }
    }

    is ActivityEvent.ScoreChanged ->
        stringResource(Res.string.hm_recent_score, event.from.value, event.to.value)

    is ActivityEvent.CarAdded -> stringResource(Res.string.hm_recent_car_added, event.carName)
}

/** "12 Jul · Verified" — the day, and how much the entry can be trusted. */
@Composable
internal fun recentMeta(event: ActivityEvent): String {
    val day = formatDayMonth(event.date)
    val service = event as? ActivityEvent.Service ?: return day
    val trust = if (service.verification == VerificationStatus.VERIFIED) {
        stringResource(Res.string.hm_recent_verified)
    } else {
        stringResource(Res.string.hm_recent_self_reported)
    }
    return stringResource(Res.string.hm_recent_meta, day, trust)
}

/** The service row's title — the owner's own words when there are any, else the tags. */
@Composable
private fun workDoneText(workDone: WorkDone): String = when (workDone) {
    is WorkDone.Described -> workDone.labels.joinToString(" + ")
    // Resolved with `map` (inline) before joining: joinToString's transform is not inline,
    // so a @Composable call cannot happen inside it.
    is WorkDone.Tagged -> workDone.categories.map { it.label() }.joinToString(" + ")
    WorkDone.Unspecified -> stringResource(Res.string.hm_work_unspecified)
}

internal fun DocumentType.labelResource() = when (this) {
    DocumentType.INSURANCE -> Res.string.hm_doc_insurance
    DocumentType.PUC -> Res.string.hm_doc_puc
    DocumentType.RC -> Res.string.hm_doc_rc
    DocumentType.LICENCE -> Res.string.hm_doc_licence
    DocumentType.LOAN -> Res.string.hm_doc_loan
    DocumentType.OTHER -> Res.string.hm_doc_other
}

@Composable
private fun ServiceCategory.label(): String = stringResource(
    when (this) {
        ServiceCategory.OIL_CHANGE -> Res.string.hm_category_oil_change
        ServiceCategory.BRAKES -> Res.string.hm_category_brakes
        ServiceCategory.TYRES -> Res.string.hm_category_tyres
        ServiceCategory.AC -> Res.string.hm_category_ac
        ServiceCategory.BATTERY -> Res.string.hm_category_battery
        ServiceCategory.SUSPENSION -> Res.string.hm_category_suspension
        ServiceCategory.ELECTRICAL -> Res.string.hm_category_electrical
        ServiceCategory.GENERAL_SERVICE -> Res.string.hm_category_general_service
        ServiceCategory.OTHER -> Res.string.hm_category_other
    },
)

/** Past this many days left, only the distance is worth quoting. */
private const val DUE_SOON_DAYS_FOR_KM_ONLY = 30
