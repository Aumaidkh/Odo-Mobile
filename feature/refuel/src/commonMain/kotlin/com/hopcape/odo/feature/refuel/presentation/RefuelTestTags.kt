package com.hopcape.odo.feature.refuel.presentation

/**
 * Test tags for the controls the instrumented suite drives.
 *
 * Only the ones a robot has to find by identity rather than by copy. Everything else is
 * asserted through its text, which is what an owner actually sees.
 */
internal object RefuelTestTags {
    const val AMOUNT_FIELD = "refuel_amount_field"
    const val RATE_INFO = "refuel_rate_info"
    const val RATE_SET = "refuel_rate_set"
    const val RATE_FIELD = "refuel_rate_field"
    const val QUANTITY_FIELD = "refuel_quantity_field"
    const val CONFIRM_BUTTON = "refuel_confirm_button"
    const val LOG_AMOUNT_FIELD = "refuel_log_amount_field"
    const val LOG_DONE_BUTTON = "refuel_log_done_button"

    /** One unanswered detection, keyed so a test can drive a specific row. */
    fun pendingRow(id: String): String = "refuel_pending_row_$id"
}
