package com.hopcape.odo.feature.challan.presentation

/** Tags for UI tests — the screens' words change with copy, these do not. */
object ChallanTestTags {
    const val LIST_SCREEN: String = "challan_list_screen"
    const val REFRESH_PILL: String = "challan_refresh_pill"
    const val TOTAL_CARD: String = "challan_total_card"
    const val COURT_CARD: String = "challan_court_card"
    const val PAY_BUTTON: String = "challan_pay_button"
    const val ALREADY_PAID: String = "challan_already_paid"
    const val CLEAN_STATE: String = "challan_clean_state"
    const val SOURCE_DOWN: String = "challan_source_down"
    const val OLDER_ROW: String = "challan_older_row"

    const val LOOKUP_FIELD: String = "challan_lookup_field"
    const val LOOKUP_CTA: String = "challan_lookup_cta"
    const val NOT_FOUND: String = "challan_not_found"

    const val RESULT_SCREEN: String = "challan_result_screen"
    const val RESULT_TRANSFER_CARD: String = "challan_result_transfer_card"

    fun challanCard(id: String): String = "challan_card_$id"
}
