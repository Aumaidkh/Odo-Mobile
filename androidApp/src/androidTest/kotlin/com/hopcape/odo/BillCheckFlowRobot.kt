package com.hopcape.odo

import app.cash.sqldelight.db.SqlDriver
import org.koin.core.context.GlobalContext

/**
 * Seeds the bill the check reads.
 *
 * The reader takes a service-log entry, so a screenshot of the result needs a real one with
 * real line items — the JSON breakdown, not just the categories the fairness seeder writes.
 * The wording is Scene 1's bill from `docs/AI_ADVISORY_PLAN.md`, printed the way a workshop
 * prints it.
 */
internal object BillCheckFixtures {

    /** The entry the result screen is opened on. */
    const val BILL_ID = "billcheck-entry-1"

    /** An earlier entry, so a repeat has something to be a repeat of. */
    const val EARLIER_ID = "billcheck-entry-0"

    const val WORKSHOP = "Company Centre"
}

/** The bill in Scene 1: Rs. 18,400 across six lines. */
internal fun seedBillToCheck() {
    insertBill(
        id = BillCheckFixtures.EARLIER_ID,
        date = "2026-04-12",
        odometerKm = 9_000,
        totalPaise = 2_40_000,
        lineItems = """[{"label":"AC service","category":"AC","amount_paise":180000}]""",
    )
    insertBill(
        id = BillCheckFixtures.BILL_ID,
        date = "2026-08-12",
        odometerKm = 12_000,
        totalPaise = 18_40_000,
        lineItems = """
            [
              {"label":"Engine oil + filter","category":"OIL_CHANGE","amount_paise":580000},
              {"label":"Air filter","category":"OTHER","amount_paise":95000},
              {"label":"AC service","category":"AC","amount_paise":240000},
              {"label":"Throttle body","category":"OTHER","amount_paise":180000},
              {"label":"Injector cleaning","category":"OTHER","amount_paise":310000},
              {"label":"Labour + consumables","category":"OTHER","amount_paise":435000}
            ]
        """.trimIndent().replace("\n", "").replace("  ", ""),
    )
    billCheckDriver().notifyListeners("cars", "profiles", "service_logs", "service_log_categories")
}

private fun insertBill(
    id: String,
    date: String,
    odometerKm: Int,
    totalPaise: Long,
    lineItems: String,
) = with(billCheckDriver()) {
    execute(
        null,
        "INSERT INTO service_logs (id, car_id, owner_id, service_date, odometer_km, " +
            "total_amount_paise, workshop_name, notes, source, bill_id, bill_photo_path, " +
            "fairness_snapshot, line_items, created_at, updated_at, sync_status) VALUES " +
            "('$id', '${LogFixtures.CAR}', '${LogFixtures.OWNER}', '$date', $odometerKm, " +
            "$totalPaise, '${BillCheckFixtures.WORKSHOP}', NULL, 'SCANNED', NULL, " +
            // A bill photo, because the detail screen offers the check only on a verified
            // entry — the PRD's trust rule, and the gate this action already had.
            "'bills/test-car/$id.jpg', NULL, " +
            "'${lineItems.replace("'", "''")}', '$SEEDED_AT', '$SEEDED_AT', 'PENDING')",
        0,
    )
}

private fun billCheckDriver(): SqlDriver = GlobalContext.get().get()

private const val SEEDED_AT = "2026-08-13T00:00:00Z"
