package com.hopcape.odo.feature.servicelog.presentation.share.pdf

import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import com.hopcape.odo.feature.servicelog.resources.Res
import com.hopcape.odo.feature.servicelog.resources.sl_bill_col_item
import com.hopcape.odo.feature.servicelog.resources.sl_bill_col_category
import com.hopcape.odo.feature.servicelog.resources.sl_bill_disclaimer
import com.hopcape.odo.feature.servicelog.resources.sl_bill_eyebrow
import com.hopcape.odo.feature.servicelog.resources.sl_bill_eyebrow_self
import com.hopcape.odo.feature.servicelog.resources.sl_bill_footer
import com.hopcape.odo.feature.servicelog.resources.sl_bill_header_prefix
import com.hopcape.odo.feature.servicelog.resources.sl_bill_items
import com.hopcape.odo.feature.servicelog.resources.sl_bill_notes
import com.hopcape.odo.feature.servicelog.resources.sl_bill_stat_date
import com.hopcape.odo.feature.servicelog.resources.sl_bill_stat_odo
import com.hopcape.odo.feature.servicelog.resources.sl_bill_stat_status
import com.hopcape.odo.feature.servicelog.resources.sl_bill_stat_total
import com.hopcape.odo.feature.servicelog.resources.sl_bill_title
import com.hopcape.odo.feature.servicelog.resources.sl_bill_total
import com.hopcape.odo.feature.servicelog.resources.sl_bill_workshop
import com.hopcape.odo.feature.servicelog.resources.sl_cat_ac
import com.hopcape.odo.feature.servicelog.resources.sl_cat_battery
import com.hopcape.odo.feature.servicelog.resources.sl_cat_brakes
import com.hopcape.odo.feature.servicelog.resources.sl_cat_electrical
import com.hopcape.odo.feature.servicelog.resources.sl_cat_general_service
import com.hopcape.odo.feature.servicelog.resources.sl_cat_oil_change
import com.hopcape.odo.feature.servicelog.resources.sl_cat_other
import com.hopcape.odo.feature.servicelog.resources.sl_cat_suspension
import com.hopcape.odo.feature.servicelog.resources.sl_cat_tyres
import com.hopcape.odo.feature.servicelog.resources.sl_record_col_amount
import com.hopcape.odo.feature.servicelog.resources.sl_record_fuel_cng
import com.hopcape.odo.feature.servicelog.resources.sl_record_fuel_diesel
import com.hopcape.odo.feature.servicelog.resources.sl_record_fuel_electric
import com.hopcape.odo.feature.servicelog.resources.sl_record_fuel_petrol
import com.hopcape.odo.feature.servicelog.resources.sl_record_issued
import com.hopcape.odo.feature.servicelog.resources.sl_record_status_self
import com.hopcape.odo.feature.servicelog.resources.sl_record_status_verified
import com.hopcape.odo.feature.servicelog.resources.sl_record_work_unspecified
import org.jetbrains.compose.resources.getString

/**
 * Every word the printed bill contains, already resolved — the single-entry counterpart of
 * [ServiceRecordLabels], and split off for the same reason: the document is built by a pure
 * string function that has no access to resources, so the copy is looked up first and handed
 * in. The strings shared with the record (statuses, fuel and category names, the issued
 * line) come from the same `sl_record_*` resources so the two documents can never word the
 * same fact differently.
 */
internal data class ServiceBillLabels(
    val headerPrefix: String,
    val issued: (date: String) -> String,
    /** For a verified entry. */
    val eyebrow: String,
    /** For an entry still on the owner's word. */
    val eyebrowSelfReported: String,
    val statDate: String,
    val statOdometer: String,
    val statStatus: String,
    val statTotal: String,
    val statusVerified: String,
    val statusSelfReported: String,
    val workshop: String,
    val notes: String,
    val items: String,
    val columnItem: String,
    val columnCategory: String,
    val columnAmount: String,
    val workUnspecified: String,
    val categoryName: (ServiceCategory) -> String,
    val fuelName: (FuelType) -> String,
    val total: String,
    val disclaimer: String,
    val footer: String,
    /** The name the file carries and the share sheet offers it under. */
    val documentTitle: (car: String) -> String,
) {
    companion object {
        /** Read every string the bill needs, once. Suspending for the same reason as
         * [ServiceRecordLabels.load]: resources are read outside a composition. */
        suspend fun load(): ServiceBillLabels {
            val issuedTemplate = getString(Res.string.sl_record_issued)
            val titleTemplate = getString(Res.string.sl_bill_title)

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

            return ServiceBillLabels(
                headerPrefix = getString(Res.string.sl_bill_header_prefix),
                issued = { date -> issuedTemplate.replace(TEXT_PLACEHOLDER, date) },
                eyebrow = getString(Res.string.sl_bill_eyebrow),
                eyebrowSelfReported = getString(Res.string.sl_bill_eyebrow_self),
                statDate = getString(Res.string.sl_bill_stat_date),
                statOdometer = getString(Res.string.sl_bill_stat_odo),
                statStatus = getString(Res.string.sl_bill_stat_status),
                statTotal = getString(Res.string.sl_bill_stat_total),
                statusVerified = getString(Res.string.sl_record_status_verified),
                statusSelfReported = getString(Res.string.sl_record_status_self),
                workshop = getString(Res.string.sl_bill_workshop),
                notes = getString(Res.string.sl_bill_notes),
                items = getString(Res.string.sl_bill_items),
                columnItem = getString(Res.string.sl_bill_col_item),
                columnCategory = getString(Res.string.sl_bill_col_category),
                columnAmount = getString(Res.string.sl_record_col_amount),
                workUnspecified = getString(Res.string.sl_record_work_unspecified),
                categoryName = { category -> categoryNames.getValue(category) },
                fuelName = { fuel -> fuelNames.getValue(fuel) },
                total = getString(Res.string.sl_bill_total),
                disclaimer = getString(Res.string.sl_bill_disclaimer),
                footer = getString(Res.string.sl_bill_footer),
                documentTitle = { car -> titleTemplate.replace(TEXT_PLACEHOLDER, car) },
            )
        }

        /** The placeholder the bill's own strings are written with. */
        private const val TEXT_PLACEHOLDER = "%1\$s"
    }
}
