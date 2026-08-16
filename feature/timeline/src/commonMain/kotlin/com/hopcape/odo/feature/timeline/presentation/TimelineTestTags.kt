package com.hopcape.odo.feature.timeline.presentation

import com.hopcape.odo.feature.timeline.domain.model.ActivityCategory

/**
 * Test tags for the timeline, so the instrumented suite finds rows by identity rather than
 * by the copy on them.
 *
 * Public because `:androidApp`'s end-to-end suite is another Gradle module — the same reason
 * the other features' tag objects are. The names are a contract with those tests: renaming
 * one breaks a test that has no other way to point at the thing.
 */
object TimelineTestTags {
    const val FEED = "timeline_feed"
    const val FILTER_BUTTON = "timeline_filter_button"
    const val SHARE_BUTTON = "timeline_share_button"
    const val EMPTY_CTA = "timeline_empty_cta"
    const val FILTERED_EMPTY = "timeline_filtered_empty"
    const val SUBTITLE = "timeline_subtitle"

    /** A logged fuel fill. */
    const val FUEL_ROW = "timeline_fuel_row"

    /** A health-score move row. */
    const val SCORE_ROW = "timeline_score_row"

    /** The "car added" milestone card. */
    const val MILESTONE_ROW = "timeline_milestone_row"

    const val FILTER_ONLY_FLAGGED = "timeline_filter_only_flagged"
    const val FILTER_APPLY = "timeline_filter_apply"

    /** Shared by every service card, for counting how many the feed is showing. */
    const val SERVICE_ROW_PREFIX = "timeline_service_"

    /** Shared by every document row, for the same reason. */
    const val DOCUMENT_ROW_PREFIX = "timeline_document_"

    /** One service card, keyed by its entry id. */
    fun serviceRow(logId: String) = "$SERVICE_ROW_PREFIX$logId"

    /** The "Add bill" prompt on a self-reported card. */
    fun addBill(logId: String) = "timeline_add_bill_$logId"

    /** A document filing row, keyed by the document type it is about. */
    fun documentRow(type: String) = "$DOCUMENT_ROW_PREFIX$type"

    /*
     * The filter sheet's rows. Named values rather than a function over the category enum,
     * which stays internal to the feature: the tag still follows the enum, so renaming a
     * category renames its tag, and the suite has a name to reach for either way.
     */
    val FILTER_ROW_SERVICES: String = filterRow(ActivityCategory.SERVICES)
    val FILTER_ROW_DOCUMENTS: String = filterRow(ActivityCategory.DOCUMENTS)
    val FILTER_ROW_SCORE: String = filterRow(ActivityCategory.SCORE)
    val FILTER_ROW_MILESTONES: String = filterRow(ActivityCategory.MILESTONES)

    /** Every category row, in the order the sheet lists them. */
    val FILTER_ROWS: List<String> = ActivityCategory.entries.map(::filterRow)

    internal fun filterRow(category: ActivityCategory) = "timeline_filter_row_${category.name}"
}
