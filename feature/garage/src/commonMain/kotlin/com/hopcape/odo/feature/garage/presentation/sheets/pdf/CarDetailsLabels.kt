package com.hopcape.odo.feature.garage.presentation.sheets.pdf

import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.cost.model.SpendCategory
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.health.model.HealthFactorKind
import com.hopcape.odo.feature.garage.resources.Res
import com.hopcape.odo.feature.garage.resources.gr_cd_city
import com.hopcape.odo.feature.garage.resources.gr_cd_col_amount
import com.hopcape.odo.feature.garage.resources.gr_cd_col_category
import com.hopcape.odo.feature.garage.resources.gr_cd_col_details
import com.hopcape.odo.feature.garage.resources.gr_cd_col_document
import com.hopcape.odo.feature.garage.resources.gr_cd_col_per_km
import com.hopcape.odo.feature.garage.resources.gr_cd_col_share
import com.hopcape.odo.feature.garage.resources.gr_cd_col_status
import com.hopcape.odo.feature.garage.resources.gr_cd_col_valid_till
import com.hopcape.odo.feature.garage.resources.gr_cd_cost_fuel
import com.hopcape.odo.feature.garage.resources.gr_cd_cost_fuel_estimated
import com.hopcape.odo.feature.garage.resources.gr_cd_cost_repairs
import com.hopcape.odo.feature.garage.resources.gr_cd_cost_service
import com.hopcape.odo.feature.garage.resources.gr_cd_cost_total
import com.hopcape.odo.feature.garage.resources.gr_cd_costs
import com.hopcape.odo.feature.garage.resources.gr_cd_disclaimer
import com.hopcape.odo.feature.garage.resources.gr_cd_doc_insurance
import com.hopcape.odo.feature.garage.resources.gr_cd_doc_loan
import com.hopcape.odo.feature.garage.resources.gr_cd_doc_other
import com.hopcape.odo.feature.garage.resources.gr_cd_doc_puc
import com.hopcape.odo.feature.garage.resources.gr_cd_doc_rc
import com.hopcape.odo.feature.garage.resources.gr_cd_documents
import com.hopcape.odo.feature.garage.resources.gr_cd_entries
import com.hopcape.odo.feature.garage.resources.gr_cd_entries_value
import com.hopcape.odo.feature.garage.resources.gr_cd_eyebrow
import com.hopcape.odo.feature.garage.resources.gr_cd_factor_cost
import com.hopcape.odo.feature.garage.resources.gr_cd_factor_documentation
import com.hopcape.odo.feature.garage.resources.gr_cd_factor_history
import com.hopcape.odo.feature.garage.resources.gr_cd_factor_maintenance
import com.hopcape.odo.feature.garage.resources.gr_cd_footer
import com.hopcape.odo.feature.garage.resources.gr_cd_fuel
import com.hopcape.odo.feature.garage.resources.gr_cd_fuel_cng
import com.hopcape.odo.feature.garage.resources.gr_cd_fuel_diesel
import com.hopcape.odo.feature.garage.resources.gr_cd_fuel_electric
import com.hopcape.odo.feature.garage.resources.gr_cd_fuel_petrol
import com.hopcape.odo.feature.garage.resources.gr_cd_full_history
import com.hopcape.odo.feature.garage.resources.gr_cd_full_history_value
import com.hopcape.odo.feature.garage.resources.gr_cd_header_prefix
import com.hopcape.odo.feature.garage.resources.gr_cd_health
import com.hopcape.odo.feature.garage.resources.gr_cd_health_points
import com.hopcape.odo.feature.garage.resources.gr_cd_howto
import com.hopcape.odo.feature.garage.resources.gr_cd_howto_estimated
import com.hopcape.odo.feature.garage.resources.gr_cd_howto_estimated_body
import com.hopcape.odo.feature.garage.resources.gr_cd_howto_recorded
import com.hopcape.odo.feature.garage.resources.gr_cd_howto_recorded_body
import com.hopcape.odo.feature.garage.resources.gr_cd_howto_score
import com.hopcape.odo.feature.garage.resources.gr_cd_howto_score_body
import com.hopcape.odo.feature.garage.resources.gr_cd_identification
import com.hopcape.odo.feature.garage.resources.gr_cd_issued
import com.hopcape.odo.feature.garage.resources.gr_cd_last_service
import com.hopcape.odo.feature.garage.resources.gr_cd_make_model
import com.hopcape.odo.feature.garage.resources.gr_cd_model_year
import com.hopcape.odo.feature.garage.resources.gr_cd_odometer_trail
import com.hopcape.odo.feature.garage.resources.gr_cd_owned_since
import com.hopcape.odo.feature.garage.resources.gr_cd_owner
import com.hopcape.odo.feature.garage.resources.gr_cd_ownership
import com.hopcape.odo.feature.garage.resources.gr_cd_registration
import com.hopcape.odo.feature.garage.resources.gr_cd_service_summary
import com.hopcape.odo.feature.garage.resources.gr_cd_stat_age
import com.hopcape.odo.feature.garage.resources.gr_cd_stat_age_year
import com.hopcape.odo.feature.garage.resources.gr_cd_stat_age_years
import com.hopcape.odo.feature.garage.resources.gr_cd_stat_cost
import com.hopcape.odo.feature.garage.resources.gr_cd_stat_cost_unit
import com.hopcape.odo.feature.garage.resources.gr_cd_stat_health
import com.hopcape.odo.feature.garage.resources.gr_cd_stat_health_unit
import com.hopcape.odo.feature.garage.resources.gr_cd_stat_odo
import com.hopcape.odo.feature.garage.resources.gr_cd_status_expired
import com.hopcape.odo.feature.garage.resources.gr_cd_status_expiring
import com.hopcape.odo.feature.garage.resources.gr_cd_status_on_file
import com.hopcape.odo.feature.garage.resources.gr_cd_status_valid
import com.hopcape.odo.feature.garage.resources.gr_cd_title
import com.hopcape.odo.feature.garage.resources.gr_cd_trail_consistent
import com.hopcape.odo.feature.garage.resources.gr_cd_trail_inconsistent
import org.jetbrains.compose.resources.getString

/**
 * Every word the printed vehicle-details document contains, already resolved — the same
 * split as the service log's record labels, for the same reason: the document is built by
 * a pure string function with no access to resources, so the copy is looked up first and
 * handed in. That keeps [CarDetailsHtml] testable on the host JVM.
 */
internal data class CarDetailsLabels(
    val headerPrefix: String,
    val issued: (date: String) -> String,
    val eyebrow: String,
    val statOdometer: String,
    val statAge: String,
    val ageUnit: (years: Int) -> String,
    val statHealth: String,
    val statHealthUnit: String,
    val statCost: String,
    val statCostUnit: String,
    val identification: String,
    val registration: String,
    val makeModel: String,
    val modelYear: String,
    val fuel: String,
    val fuelName: (FuelType) -> String,
    val ownership: String,
    val owner: String,
    val ownedSince: String,
    val cityOfUse: String,
    val odometerTrail: String,
    val trailConsistent: String,
    val trailInconsistent: String,
    val documents: String,
    val columnDocument: String,
    val columnDetails: String,
    val columnValidTill: String,
    val columnStatus: String,
    val documentName: (DocumentType) -> String,
    val statusValid: String,
    val statusExpiring: String,
    val statusExpired: String,
    val statusOnFile: String,
    val costs: String,
    val columnCategory: String,
    val columnAmount: String,
    val columnPerKm: String,
    val columnShare: String,
    val costCategory: (SpendCategory) -> String,
    val costFuelEstimated: String,
    val costTotal: (kmDriven: String) -> String,
    val health: String,
    val healthPoints: (earned: Int, max: Int) -> String,
    val factorName: (HealthFactorKind) -> String,
    val serviceSummary: String,
    val entries: String,
    val entriesValue: (total: Int, verified: Int, selfReported: Int) -> String,
    val lastService: String,
    val fullHistory: String,
    val fullHistoryValue: String,
    val howToRead: String,
    val howToReadRecorded: String,
    val howToReadRecordedBody: String,
    val howToReadEstimated: String,
    val howToReadEstimatedBody: String,
    val howToReadScore: String,
    val howToReadScoreBody: String,
    val disclaimer: String,
    val footer: String,
    /** The name the file carries and the share sheet offers it under. */
    val documentTitle: (car: String) -> String,
) {
    companion object {
        /** Read every string the document needs, once — outside any composition. */
        suspend fun load(): CarDetailsLabels {
            val issuedTemplate = getString(Res.string.gr_cd_issued)
            val titleTemplate = getString(Res.string.gr_cd_title)
            val entriesTemplate = getString(Res.string.gr_cd_entries_value)
            val costTotalTemplate = getString(Res.string.gr_cd_cost_total)
            val pointsTemplate = getString(Res.string.gr_cd_health_points)
            val yearSingular = getString(Res.string.gr_cd_stat_age_year)
            val yearPlural = getString(Res.string.gr_cd_stat_age_years)

            val documentNames = mapOf(
                DocumentType.INSURANCE to getString(Res.string.gr_cd_doc_insurance),
                DocumentType.PUC to getString(Res.string.gr_cd_doc_puc),
                DocumentType.RC to getString(Res.string.gr_cd_doc_rc),
                DocumentType.LOAN to getString(Res.string.gr_cd_doc_loan),
                DocumentType.OTHER to getString(Res.string.gr_cd_doc_other),
                // Never printed — the document does not carry the owner's licence — but
                // mapped so a lookup can never fail.
                DocumentType.LICENCE to getString(Res.string.gr_cd_doc_other),
            )

            val fuelNames = mapOf(
                FuelType.PETROL to getString(Res.string.gr_cd_fuel_petrol),
                FuelType.DIESEL to getString(Res.string.gr_cd_fuel_diesel),
                FuelType.CNG to getString(Res.string.gr_cd_fuel_cng),
                FuelType.ELECTRIC to getString(Res.string.gr_cd_fuel_electric),
            )

            val categoryNames = mapOf(
                SpendCategory.FUEL to getString(Res.string.gr_cd_cost_fuel),
                SpendCategory.SERVICE to getString(Res.string.gr_cd_cost_service),
                SpendCategory.REPAIRS to getString(Res.string.gr_cd_cost_repairs),
            )

            val factorNames = mapOf(
                HealthFactorKind.MAINTENANCE to getString(Res.string.gr_cd_factor_maintenance),
                HealthFactorKind.DOCUMENTATION to getString(Res.string.gr_cd_factor_documentation),
                HealthFactorKind.COST_EFFICIENCY to getString(Res.string.gr_cd_factor_cost),
                HealthFactorKind.HISTORY to getString(Res.string.gr_cd_factor_history),
            )

            return CarDetailsLabels(
                headerPrefix = getString(Res.string.gr_cd_header_prefix),
                issued = { date -> issuedTemplate.replace(TEXT_PLACEHOLDER, date) },
                eyebrow = getString(Res.string.gr_cd_eyebrow),
                statOdometer = getString(Res.string.gr_cd_stat_odo),
                statAge = getString(Res.string.gr_cd_stat_age),
                ageUnit = { years -> if (years == 1) yearSingular else yearPlural },
                statHealth = getString(Res.string.gr_cd_stat_health),
                statHealthUnit = getString(Res.string.gr_cd_stat_health_unit),
                statCost = getString(Res.string.gr_cd_stat_cost),
                statCostUnit = getString(Res.string.gr_cd_stat_cost_unit),
                identification = getString(Res.string.gr_cd_identification),
                registration = getString(Res.string.gr_cd_registration),
                makeModel = getString(Res.string.gr_cd_make_model),
                modelYear = getString(Res.string.gr_cd_model_year),
                fuel = getString(Res.string.gr_cd_fuel),
                fuelName = { fuel -> fuelNames.getValue(fuel) },
                ownership = getString(Res.string.gr_cd_ownership),
                owner = getString(Res.string.gr_cd_owner),
                ownedSince = getString(Res.string.gr_cd_owned_since),
                cityOfUse = getString(Res.string.gr_cd_city),
                odometerTrail = getString(Res.string.gr_cd_odometer_trail),
                trailConsistent = getString(Res.string.gr_cd_trail_consistent),
                trailInconsistent = getString(Res.string.gr_cd_trail_inconsistent),
                documents = getString(Res.string.gr_cd_documents),
                columnDocument = getString(Res.string.gr_cd_col_document),
                columnDetails = getString(Res.string.gr_cd_col_details),
                columnValidTill = getString(Res.string.gr_cd_col_valid_till),
                columnStatus = getString(Res.string.gr_cd_col_status),
                documentName = { type -> documentNames.getValue(type) },
                statusValid = getString(Res.string.gr_cd_status_valid),
                statusExpiring = getString(Res.string.gr_cd_status_expiring),
                statusExpired = getString(Res.string.gr_cd_status_expired),
                statusOnFile = getString(Res.string.gr_cd_status_on_file),
                costs = getString(Res.string.gr_cd_costs),
                columnCategory = getString(Res.string.gr_cd_col_category),
                columnAmount = getString(Res.string.gr_cd_col_amount),
                columnPerKm = getString(Res.string.gr_cd_col_per_km),
                columnShare = getString(Res.string.gr_cd_col_share),
                costCategory = { category -> categoryNames.getValue(category) },
                costFuelEstimated = getString(Res.string.gr_cd_cost_fuel_estimated),
                costTotal = { km -> costTotalTemplate.replace(TEXT_PLACEHOLDER, km) },
                health = getString(Res.string.gr_cd_health),
                healthPoints = { earned, max ->
                    pointsTemplate
                        .replace(FIRST_COUNT_PLACEHOLDER, earned.toString())
                        .replace(SECOND_COUNT_PLACEHOLDER, max.toString())
                },
                factorName = { kind -> factorNames.getValue(kind) },
                serviceSummary = getString(Res.string.gr_cd_service_summary),
                entries = getString(Res.string.gr_cd_entries),
                entriesValue = { total, verified, selfReported ->
                    entriesTemplate
                        .replace(FIRST_COUNT_PLACEHOLDER, total.toString())
                        .replace(SECOND_COUNT_PLACEHOLDER, verified.toString())
                        .replace(THIRD_COUNT_PLACEHOLDER, selfReported.toString())
                },
                lastService = getString(Res.string.gr_cd_last_service),
                fullHistory = getString(Res.string.gr_cd_full_history),
                fullHistoryValue = getString(Res.string.gr_cd_full_history_value),
                howToRead = getString(Res.string.gr_cd_howto),
                howToReadRecorded = getString(Res.string.gr_cd_howto_recorded),
                howToReadRecordedBody = getString(Res.string.gr_cd_howto_recorded_body),
                howToReadEstimated = getString(Res.string.gr_cd_howto_estimated),
                howToReadEstimatedBody = getString(Res.string.gr_cd_howto_estimated_body),
                howToReadScore = getString(Res.string.gr_cd_howto_score),
                howToReadScoreBody = getString(Res.string.gr_cd_howto_score_body),
                disclaimer = getString(Res.string.gr_cd_disclaimer),
                footer = getString(Res.string.gr_cd_footer),
                documentTitle = { car -> titleTemplate.replace(TEXT_PLACEHOLDER, car) },
            )
        }

        /** The placeholders the document's own strings are written with. */
        private const val TEXT_PLACEHOLDER = "%1\$s"
        private const val FIRST_COUNT_PLACEHOLDER = "%1\$d"
        private const val SECOND_COUNT_PLACEHOLDER = "%2\$d"
        private const val THIRD_COUNT_PLACEHOLDER = "%3\$d"
    }
}
