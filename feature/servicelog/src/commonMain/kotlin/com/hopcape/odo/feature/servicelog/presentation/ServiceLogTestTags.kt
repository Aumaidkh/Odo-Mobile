package com.hopcape.odo.feature.servicelog.presentation

/**
 * Test tags for the controls an end-to-end test cannot reach by the words on them.
 *
 * Deliberately few. Copy is what an owner actually sees, so a test that finds a button by its
 * label is testing the product; a tag is only added where text would be ambiguous or absent —
 * a field whose editable node sits several layers inside a design-system component, or a row
 * that has to be identified as *that* entry rather than one that happens to share a workshop.
 *
 * Public because `:androidApp`'s instrumented tests reference these, which is the only reason
 * anything in this module is public besides the Koin module and the analytics schema.
 */
object ServiceLogTestTags {

    /* Add / edit form fields. Each tag sits on the component; the node that accepts text is
     * the `BasicTextField` inside it, matched by the test as an editable node in the subtree. */
    const val WORKSHOP_FIELD = "servicelog_workshop_field"
    const val DATE_FIELD = "servicelog_date_field"
    const val ODOMETER_FIELD = "servicelog_odometer_field"
    const val AMOUNT_FIELD = "servicelog_amount_field"
    const val SAVE = "servicelog_save"

    /** One entry's row, in either direction — the ledger card and the timeline node. */
    fun card(logId: String): String = "servicelog_card_$logId"
}
