package com.hopcape.odo.feature.billscanner.presentation

/**
 * Test tags for the scanner's fields, which an end-to-end test cannot reach by the words on
 * them.
 *
 * Deliberately few. Copy is what an owner sees, so a test that finds a control by its words is
 * testing the product; a tag is only added where there are no words to find. Each of these is
 * an empty input whose label sits above it as separate text, so the field itself carries
 * nothing to aim at.
 *
 * Public because `:androidApp`'s instrumented tests reference these, which is the only reason
 * anything in this module is public besides the Koin module and the analytics schema.
 */
object BillScannerTestTags {

    /** What the owner is paying at the pump, in rupees. */
    const val PAY_AMOUNT_FIELD = "billscanner_pay_amount"

    /** The odometer reading taken at the pump. */
    const val FILL_ODOMETER_FIELD = "billscanner_fill_odometer"

    /** How much fuel went in. */
    const val FILL_QUANTITY_FIELD = "billscanner_fill_quantity"

    /** The scanned document's expiry — the one field the vault cannot do without. */
    const val DOCUMENT_EXPIRY_FIELD = "billscanner_document_expiry"
}
