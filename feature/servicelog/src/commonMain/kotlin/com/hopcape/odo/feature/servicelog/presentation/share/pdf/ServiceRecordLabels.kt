package com.hopcape.odo.feature.servicelog.presentation.share.pdf

import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
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
import com.hopcape.odo.feature.servicelog.resources.sl_record_car_added
import com.hopcape.odo.feature.servicelog.resources.sl_record_col_amount
import com.hopcape.odo.feature.servicelog.resources.sl_record_col_date
import com.hopcape.odo.feature.servicelog.resources.sl_record_col_odo
import com.hopcape.odo.feature.servicelog.resources.sl_record_col_status
import com.hopcape.odo.feature.servicelog.resources.sl_record_col_work
import com.hopcape.odo.feature.servicelog.resources.sl_record_disclaimer
import com.hopcape.odo.feature.servicelog.resources.sl_record_doc_added
import com.hopcape.odo.feature.servicelog.resources.sl_record_doc_expired
import com.hopcape.odo.feature.servicelog.resources.sl_record_doc_insurance
import com.hopcape.odo.feature.servicelog.resources.sl_record_doc_loan
import com.hopcape.odo.feature.servicelog.resources.sl_record_doc_on_file
import com.hopcape.odo.feature.servicelog.resources.sl_record_doc_other
import com.hopcape.odo.feature.servicelog.resources.sl_record_doc_puc
import com.hopcape.odo.feature.servicelog.resources.sl_record_doc_rc
import com.hopcape.odo.feature.servicelog.resources.sl_record_doc_renewed
import com.hopcape.odo.feature.servicelog.resources.sl_record_doc_valid_till
import com.hopcape.odo.feature.servicelog.resources.sl_record_documents
import com.hopcape.odo.feature.servicelog.resources.sl_record_empty
import com.hopcape.odo.feature.servicelog.resources.sl_record_eyebrow
import com.hopcape.odo.feature.servicelog.resources.sl_record_footer
import com.hopcape.odo.feature.servicelog.resources.sl_record_fuel_cng
import com.hopcape.odo.feature.servicelog.resources.sl_record_fuel_diesel
import com.hopcape.odo.feature.servicelog.resources.sl_record_fuel_electric
import com.hopcape.odo.feature.servicelog.resources.sl_record_fuel_petrol
import com.hopcape.odo.feature.servicelog.resources.sl_record_header_prefix
import com.hopcape.odo.feature.servicelog.resources.sl_record_howto
import com.hopcape.odo.feature.servicelog.resources.sl_record_howto_odometer
import com.hopcape.odo.feature.servicelog.resources.sl_record_howto_self
import com.hopcape.odo.feature.servicelog.resources.sl_record_howto_verified
import com.hopcape.odo.feature.servicelog.resources.sl_record_issued
import com.hopcape.odo.feature.servicelog.resources.sl_record_odometer
import com.hopcape.odo.feature.servicelog.resources.sl_record_odometer_consistent
import com.hopcape.odo.feature.servicelog.resources.sl_record_odometer_inconsistent
import com.hopcape.odo.feature.servicelog.resources.sl_record_owned_since
import com.hopcape.odo.feature.servicelog.resources.sl_record_owner
import com.hopcape.odo.feature.servicelog.resources.sl_record_ownership
import com.hopcape.odo.feature.servicelog.resources.sl_record_stat_entries
import com.hopcape.odo.feature.servicelog.resources.sl_record_stat_health
import com.hopcape.odo.feature.servicelog.resources.sl_record_stat_health_unit
import com.hopcape.odo.feature.servicelog.resources.sl_record_stat_since
import com.hopcape.odo.feature.servicelog.resources.sl_record_stat_verified
import com.hopcape.odo.feature.servicelog.resources.sl_record_stat_verified_of
import com.hopcape.odo.feature.servicelog.resources.sl_record_status_self
import com.hopcape.odo.feature.servicelog.resources.sl_record_status_verified
import com.hopcape.odo.feature.servicelog.resources.sl_record_timeline
import com.hopcape.odo.feature.servicelog.resources.sl_record_title
import com.hopcape.odo.feature.servicelog.resources.sl_record_total
import com.hopcape.odo.feature.servicelog.resources.sl_record_work_unspecified
import org.jetbrains.compose.resources.getString

/**
 * Every word the printed record contains, already resolved.
 *
 * The document is built by a pure string function that has no access to resources, so the
 * copy is looked up first and handed in. That keeps [ServiceRecordHtml] testable on the host
 * JVM, and keeps the record's wording in `strings.xml` with the rest of the feature's rather
 * than inlined in a template.
 *
 * The parameterised lines are functions rather than format strings so the template never
 * does its own substitution — the placeholder lives in the resource, and it is filled in here
 * where the resource system can see it.
 */
internal data class ServiceRecordLabels(
    val headerPrefix: String,
    val issued: (date: String) -> String,
    val eyebrow: String,
    val statHealth: String,
    val statHealthUnit: String,
    val statEntries: String,
    val statVerified: String,
    val statVerifiedOf: (total: Int) -> String,
    val statSince: String,
    val ownership: String,
    val owner: String,
    val ownedSince: String,
    val odometer: String,
    val odometerConsistent: String,
    val odometerInconsistent: String,
    val documents: String,
    val documentName: (DocumentType) -> String,
    val validTill: (date: String) -> String,
    val onFile: String,
    val expired: (date: String) -> String,
    val timeline: String,
    val columnDate: String,
    val columnOdometer: String,
    val columnWork: String,
    val columnStatus: String,
    val columnAmount: String,
    val statusVerified: String,
    val statusSelfReported: String,
    val documentAdded: (document: String) -> String,
    val documentRenewed: (document: String) -> String,
    val carAdded: String,
    val workUnspecified: String,
    val categoryName: (ServiceCategory) -> String,
    val fuelName: (FuelType) -> String,
    val total: String,
    val howToRead: String,
    val howToReadVerified: String,
    val howToReadSelfReported: String,
    val howToReadOdometer: String,
    val disclaimer: String,
    val footer: String,
    val empty: String,
    /** The name the file carries and the share sheet offers it under. */
    val documentTitle: (car: String) -> String,
) {
    companion object {
        /**
         * Read every string the record needs, once.
         *
         * Suspending because that is how a resource is read outside a composition, and the
         * document is built in a ViewModel rather than while drawing.
         */
        suspend fun load(): ServiceRecordLabels {
            // Read unformatted, so the placeholder survives into the string and can be filled
            // per call site. A document with forty rows then does forty substitutions rather
            // than forty resource reads.
            val issuedTemplate = getString(Res.string.sl_record_issued)
            val verifiedOfTemplate = getString(Res.string.sl_record_stat_verified_of)
            val validTillTemplate = getString(Res.string.sl_record_doc_valid_till)
            val expiredTemplate = getString(Res.string.sl_record_doc_expired)
            val addedTemplate = getString(Res.string.sl_record_doc_added)
            val renewedTemplate = getString(Res.string.sl_record_doc_renewed)
            val titleTemplate = getString(Res.string.sl_record_title)

            val documentNames = mapOf(
                DocumentType.INSURANCE to getString(Res.string.sl_record_doc_insurance),
                DocumentType.PUC to getString(Res.string.sl_record_doc_puc),
                DocumentType.RC to getString(Res.string.sl_record_doc_rc),
                DocumentType.LOAN to getString(Res.string.sl_record_doc_loan),
                DocumentType.OTHER to getString(Res.string.sl_record_doc_other),
                // Never printed — the record does not carry the owner's licence — but mapped
                // so a lookup can never fail.
                DocumentType.LICENCE to getString(Res.string.sl_record_doc_other),
            )

            val categoryNames = mapOf(
                ServiceCategory.OIL_CHANGE to getString(Res.string.sl_cat_oil_change),
                ServiceCategory.BRAKES to getString(Res.string.sl_cat_brakes),
                ServiceCategory.TYRES to getString(Res.string.sl_cat_tyres),
                ServiceCategory.AC to getString(Res.string.sl_cat_ac),
                ServiceCategory.BATTERY to getString(Res.string.sl_cat_battery),
                ServiceCategory.SUSPENSION to getString(Res.string.sl_cat_suspension),
                ServiceCategory.ELECTRICAL to getString(Res.string.sl_cat_electrical),
                ServiceCategory.GENERAL_SERVICE to getString(Res.string.sl_cat_general_service),
                ServiceCategory.OTHER to getString(Res.string.sl_cat_other),
            )

            val fuelNames = mapOf(
                FuelType.PETROL to getString(Res.string.sl_record_fuel_petrol),
                FuelType.DIESEL to getString(Res.string.sl_record_fuel_diesel),
                FuelType.CNG to getString(Res.string.sl_record_fuel_cng),
                FuelType.ELECTRIC to getString(Res.string.sl_record_fuel_electric),
            )

            return ServiceRecordLabels(
                headerPrefix = getString(Res.string.sl_record_header_prefix),
                issued = issuedTemplate.filledWith(),
                eyebrow = getString(Res.string.sl_record_eyebrow),
                statHealth = getString(Res.string.sl_record_stat_health),
                statHealthUnit = getString(Res.string.sl_record_stat_health_unit),
                statEntries = getString(Res.string.sl_record_stat_entries),
                statVerified = getString(Res.string.sl_record_stat_verified),
                statVerifiedOf = { total -> verifiedOfTemplate.replace(COUNT_PLACEHOLDER, total.toString()) },
                statSince = getString(Res.string.sl_record_stat_since),
                ownership = getString(Res.string.sl_record_ownership),
                owner = getString(Res.string.sl_record_owner),
                ownedSince = getString(Res.string.sl_record_owned_since),
                odometer = getString(Res.string.sl_record_odometer),
                odometerConsistent = getString(Res.string.sl_record_odometer_consistent),
                odometerInconsistent = getString(Res.string.sl_record_odometer_inconsistent),
                documents = getString(Res.string.sl_record_documents),
                documentName = { type -> documentNames.getValue(type) },
                validTill = validTillTemplate.filledWith(),
                onFile = getString(Res.string.sl_record_doc_on_file),
                expired = expiredTemplate.filledWith(),
                timeline = getString(Res.string.sl_record_timeline),
                columnDate = getString(Res.string.sl_record_col_date),
                columnOdometer = getString(Res.string.sl_record_col_odo),
                columnWork = getString(Res.string.sl_record_col_work),
                columnStatus = getString(Res.string.sl_record_col_status),
                columnAmount = getString(Res.string.sl_record_col_amount),
                statusVerified = getString(Res.string.sl_record_status_verified),
                statusSelfReported = getString(Res.string.sl_record_status_self),
                documentAdded = addedTemplate.filledWith(),
                documentRenewed = renewedTemplate.filledWith(),
                carAdded = getString(Res.string.sl_record_car_added),
                workUnspecified = getString(Res.string.sl_record_work_unspecified),
                categoryName = { category -> categoryNames.getValue(category) },
                fuelName = { fuel -> fuelNames.getValue(fuel) },
                total = getString(Res.string.sl_record_total),
                howToRead = getString(Res.string.sl_record_howto),
                howToReadVerified = getString(Res.string.sl_record_howto_verified),
                howToReadSelfReported = getString(Res.string.sl_record_howto_self),
                howToReadOdometer = getString(Res.string.sl_record_howto_odometer),
                disclaimer = getString(Res.string.sl_record_disclaimer),
                footer = getString(Res.string.sl_record_footer),
                empty = getString(Res.string.sl_record_empty),
                documentTitle = titleTemplate.filledWith(),
            )
        }

        /** The placeholders the record's own strings are written with. */
        private const val TEXT_PLACEHOLDER = "%1\$s"
        private const val COUNT_PLACEHOLDER = "%1\$d"

        /** Turns a once-read template into something a value can be dropped into. */
        private fun String.filledWith(): (String) -> String {
            val template = this
            return { value -> template.replace(TEXT_PLACEHOLDER, value) }
        }
    }
}
