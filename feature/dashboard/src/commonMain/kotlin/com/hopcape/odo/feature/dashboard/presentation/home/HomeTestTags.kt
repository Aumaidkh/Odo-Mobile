package com.hopcape.odo.feature.dashboard.presentation.home

/**
 * Test tags for Home, so the instrumented suite finds cards by identity rather than by the
 * copy on them.
 *
 * Public because `:androidApp`'s end-to-end suite is another Gradle module — the same reason
 * the other features' tag objects are. The names are a contract with those tests: renaming
 * one breaks a test that has no other way to point at the thing.
 */
object HomeTestTags {
    const val SCREEN = "home_screen"
    const val SKELETON = "home_skeleton"
    const val ERROR = "home_error"

    const val GREETING = "home_greeting"
    const val CAR_LINE = "home_car_line"
    const val PROFILE_BUTTON = "home_profile_button"
    const val BELL_BUTTON = "home_bell_button"

    const val HEALTH_CARD = "home_health_card"
    const val LOG_FILL_BUTTON = "home_log_fill_button"
    const val AUTO_DETECT_OFFER = "home_auto_detect_offer"
    const val AUTO_ODOMETER_OFFER = "home_auto_odometer_offer"
    const val SCORE = "home_score"
    const val BREAKDOWN_LINK = "home_breakdown_link"

    const val COST_CARD = "home_cost_card"
    const val OVERCHARGE_CARD = "home_overcharge_card"

    const val ATTENTION_CARD = "home_attention_card"
    const val INSIGHT_CARD = "home_insight_card"

    const val RECENT_ROW = "home_recent_row"
    const val TIMELINE_LINK = "home_timeline_link"

    /** The new-user path: the waiting dial, the checklist and its rows. */
    const val SCORE_WAITING = "home_score_waiting"
    const val CHECKLIST = "home_checklist"
    const val CHECKLIST_CAR = "home_checklist_car"
    const val CHECKLIST_BILL = "home_checklist_bill"
    const val CHECKLIST_DOCS = "home_checklist_docs"
    const val SCAN_FIRST_BUTTON = "home_scan_first_button"

    /** Setup never stored a car. */
    const val NO_CAR = "home_no_car"
    const val ADD_CAR_BUTTON = "home_add_car_button"
}
