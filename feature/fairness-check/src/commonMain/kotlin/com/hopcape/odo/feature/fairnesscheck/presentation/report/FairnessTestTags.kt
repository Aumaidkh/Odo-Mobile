package com.hopcape.odo.feature.fairnesscheck.presentation.report

/**
 * Test tags for the fairness report, so the instrumented suite finds cards by identity
 * rather than by the copy on them.
 *
 * Public because `:androidApp`'s end-to-end suite is another Gradle module — the same reason
 * the other features' tag objects are. The names are a contract with those tests: renaming
 * one breaks a test that has no other way to point at the thing.
 */
object FairnessTestTags {
    const val SCREEN = "fairness_screen"
    const val SKELETON = "fairness_skeleton"
    const val ERROR = "fairness_error"
    const val RETRY_BUTTON = "fairness_retry_button"

    const val NO_CITY = "fairness_no_city"
    const val SET_CITY_BUTTON = "fairness_set_city_button"

    /** The hero card, one tag per outcome so a test asserts the verdict, not the wording. */
    const val HERO_OVER = "fairness_hero_over"
    const val HERO_FAIR = "fairness_hero_fair"
    const val HERO_THIN = "fairness_hero_thin"
    const val HERO_NO_BENCHMARK = "fairness_hero_no_benchmark"
    const val THIN_RANGE = "fairness_thin_range"

    const val COMPARISON = "fairness_comparison"
    const val BASIS = "fairness_basis"
    const val BREAKDOWN = "fairness_breakdown"
    const val BREAKDOWN_ROW = "fairness_breakdown_row"

    const val REPORT_BUTTON = "fairness_report_button"
    const val DONE_BUTTON = "fairness_done_button"
}
