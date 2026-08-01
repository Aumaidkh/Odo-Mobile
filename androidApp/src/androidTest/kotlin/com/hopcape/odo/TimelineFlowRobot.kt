package com.hopcape.odo

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.feature.timeline.presentation.TimelineFilterStore
import com.hopcape.odo.feature.timeline.presentation.TimelineTestTags
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import app.cash.sqldelight.db.SqlDriver
import java.time.Instant
import java.time.LocalDate

/**
 * The words the timeline puts on screen, mirrored from its `strings.xml`.
 *
 * Copied rather than read, for the same reason as [HealthCopy]: Compose Resources keeps a
 * feature's generated `Res` internal to its own module, so `:androidApp` cannot reach it.
 * Asserting on the copy an owner actually reads is the point.
 */
internal object TimelineCopy {
    const val TAB = "Timeline"
    const val FILTER = "Filter timeline"
    const val SHARE = "Share timeline"

    /* Service cards. */
    const val VERIFIED = "Verified"
    const val SELF_REPORTED = "• Self-reported"
    const val ADD_BILL = "Add bill"

    /* Empty states. */
    const val EMPTY_TITLE = "Build your car’s story"
    const val FILTERED_EMPTY_TITLE = "Nothing matches this filter"

    /* The filter sheet. */
    const val FILTER_TITLE = "Show in timeline"
    const val FILTER_SERVICES = "Services & repairs"
    const val FILTER_DOCUMENTS = "Documents"

    /* Where the header actions land — the service log's own sheet and screens. */
    const val SHARE_SHEET_TITLE = "Share verified record"
    const val SCANNER_TITLE = "Scan bill"

    /* A service's detail, which a card opens. */
    const val DETAIL_TOTAL_PAID = "Total paid"

    /** "Swift VXI · 7 events since 2024". */
    fun subtitle(car: String, events: Int, sinceYear: Int) = "$car · $events events since $sinceYear"

    /** "Swift VXI · showing 4 of 7 events" while a filter is on. */
    fun filteredSubtitle(car: String, shown: Int, total: Int) = "$car · showing $shown of $total events"

    /** "Swift VXI · your car’s story starts here". */
    fun newUserSubtitle(car: String) = "$car · your car’s story starts here"

    fun milestone(car: String) = "$car added to Odo"

    fun documentAdded(name: String, validTill: String) = "$name added · valid till $validTill"

    fun documentRenewed(name: String, validTill: String) = "$name renewed · valid till $validTill"

    fun scoreRose(from: Int, to: Int) = "Health Score rose $from → $to"

    fun flaggedOver(amount: String) = "$amount above average"

    /** "Show 4 events" on the sheet's button. */
    fun showEvents(count: Int) = "Show $count events"
}

/**
 * The car, its services, its papers and its scores — and the feed they add up to.
 *
 * Service dates are fixed rather than relative: the timeline places events on the day they
 * happened and groups them by month, so a rolling date would move a row into another month
 * and take the ordering assertions with it. The score snapshots *are* relative, because a
 * move is only a move against the snapshot before it.
 */
internal object TimelineFixtures {
    const val CAR_NAME = "Maruti Suzuki Swift VXI"

    /** The day the car was added, from the seeded `cars.created_at`. */
    const val CAR_ADDED_YEAR = 2024

    /* Two services: one bill-backed and flagged, one with no bill at all. */
    const val VERIFIED_ID = "timeline-log-verified"
    const val VERIFIED_DATE = "2026-07-12"
    const val VERIFIED_KM = 54_000
    const val VERIFIED_PAISE = 320_000L

    const val FLAGGED_ID = "timeline-log-flagged"
    const val FLAGGED_DATE = "2026-07-08"
    const val FLAGGED_KM = 48_500
    const val FLAGGED_PAISE = 480_000L
    const val FLAGGED_OVER = "Rs. 700"

    const val SELF_REPORTED_ID = "timeline-log-self"
    const val SELF_REPORTED_DATE = "2026-06-21"
    const val SELF_REPORTED_KM = 52_100
    const val SELF_REPORTED_PAISE = 90_000L

    /* Papers. Two insurance policies, so the second reads as a renewal. */
    const val OLD_INSURANCE_ID = "timeline-doc-insurance-old"
    const val NEW_INSURANCE_ID = "timeline-doc-insurance-new"
    const val PUC_ID = "timeline-doc-puc"

    /* Scores, both under the shipped rules so they can be compared. */
    const val SCORE_BEFORE = 70
    const val SCORE_AFTER = 74

    /**
     * Three services + three papers + one score move + the milestone. The two insurance
     * policies are two events: filing last year's and renewing it this year both happened.
     */
    const val TOTAL_EVENTS = 8

    /** What is left with the documents unticked. */
    const val EVENTS_WITHOUT_DOCUMENTS = 5
}

private typealias TimelineTestRule = AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>

/* ------------------------------ Database ------------------------------ */

private fun timelineDriver(): SqlDriver = GlobalContext.get().get()

/**
 * Empty everything the feed reads.
 *
 * [resetHealthScore] already covers the profile, the car, the service log, the documents and
 * the score history — which is exactly the timeline's four sources.
 */
internal fun resetTimeline() = resetHealthScore()

/** A finished setup: an onboarded profile and one car, added on a known day. */
internal fun seedTimelineOwner() = seedOnboardedOwner()

/**
 * Empty only what the feed is *of* — the services, the papers and the scores — leaving the
 * profile and the car where they are.
 *
 * The car outlives each test on purpose. Where the app opens is read once per launch, and
 * the rule starts the activity before `@Before` runs, so a test that deleted the profile
 * would be driving an app that had already decided to open on the welcome carousel.
 */
internal fun clearTimelineData() = with(timelineDriver()) {
    execute(null, "DELETE FROM service_log_categories", 0)
    execute(null, "DELETE FROM service_logs", 0)
    execute(null, "DELETE FROM documents", 0)
    execute(null, "DELETE FROM health_scores", 0)
    announceTimelineWrites()
}

/** The two bill-backed services and the one with nothing behind it. */
internal fun seedTimelineHistory() {
    insertTimelineLog(
        id = TimelineFixtures.VERIFIED_ID,
        date = TimelineFixtures.VERIFIED_DATE,
        odometerKm = TimelineFixtures.VERIFIED_KM,
        amountPaise = TimelineFixtures.VERIFIED_PAISE,
        notes = "Oil change + filter",
        billPhotoPath = "/bills/${TimelineFixtures.VERIFIED_ID}.jpg",
    )
    insertTimelineLog(
        id = TimelineFixtures.FLAGGED_ID,
        date = TimelineFixtures.FLAGGED_DATE,
        odometerKm = TimelineFixtures.FLAGGED_KM,
        amountPaise = TimelineFixtures.FLAGGED_PAISE,
        notes = "Front brake pads",
        billPhotoPath = "/bills/${TimelineFixtures.FLAGGED_ID}.jpg",
        // The verdict as the fairness check froze it: Rs. 700 over the city average.
        fairnessSnapshot = OVERCHARGED_SNAPSHOT,
    )
    insertTimelineLog(
        id = TimelineFixtures.SELF_REPORTED_ID,
        date = TimelineFixtures.SELF_REPORTED_DATE,
        odometerKm = TimelineFixtures.SELF_REPORTED_KM,
        amountPaise = TimelineFixtures.SELF_REPORTED_PAISE,
        notes = "Wheel alignment",
    )
}

/** Last year's policy, this year's replacing it, and a PUC — one renewal and two firsts. */
internal fun seedTimelineDocuments() {
    seedTimelineDocument(
        id = TimelineFixtures.OLD_INSURANCE_ID,
        type = DocumentType.INSURANCE,
        addedOn = LocalDate.of(2025, 6, 1),
        expiresOn = LocalDate.of(2026, 5, 31),
    )
    seedTimelineDocument(
        id = TimelineFixtures.NEW_INSURANCE_ID,
        type = DocumentType.INSURANCE,
        addedOn = LocalDate.of(2026, 6, 1),
        expiresOn = LocalDate.of(2027, 6, 1),
    )
    seedTimelineDocument(
        id = TimelineFixtures.PUC_ID,
        type = DocumentType.PUC,
        addedOn = LocalDate.of(2026, 6, 2),
        expiresOn = LocalDate.of(2026, 11, 30),
    )
}

/**
 * A document filed on a chosen day.
 *
 * `created_at` is what the feed dates the row by, and it is written here as a real instant on
 * that day: [seedDocument] stamps today, which would pile every paper onto one row.
 */
internal fun seedTimelineDocument(
    id: String,
    type: DocumentType,
    addedOn: LocalDate,
    expiresOn: LocalDate?,
) = with(timelineDriver()) {
    val expiry = expiresOn?.let { "'$it'" } ?: "NULL"
    execute(
        null,
        "INSERT INTO documents (id, car_id, owner_id, doc_type, title, storage_path, doc_source, " +
            "issued_date, expiry_date, created_at, updated_at, sync_status) VALUES " +
            "('$id', '${LogFixtures.CAR}', '${LogFixtures.OWNER}', '${type.name}', NULL, " +
            "'documents/${LogFixtures.CAR}/$id.pdf', 'UPLOADED', NULL, $expiry, " +
            "'${addedOn}T09:00:00Z', '${addedOn}T09:00:00Z', 'PENDING')",
        0,
    )
    announceTimelineWrites()
}

/**
 * Two scores taken on different days, the second higher than the first.
 *
 * Relative to today so both land on the current month's section, and dated by whole days so
 * they cannot collapse into one row the way two scores on one day would.
 */
internal fun seedTimelineScores(
    beforeDaysAgo: Long = 2,
    afterDaysAgo: Long = 1,
    before: Int = TimelineFixtures.SCORE_BEFORE,
    after: Int = TimelineFixtures.SCORE_AFTER,
    afterAlgoVersion: String = "rule-v1",
    idPrefix: String = "timeline-score",
) {
    insertTimelineSnapshot("$idPrefix-before", beforeDaysAgo, before, "rule-v1")
    insertTimelineSnapshot("$idPrefix-after", afterDaysAgo, after, afterAlgoVersion)
}

/** Log a service while the tab is open — the feed has to notice. */
internal fun addServiceNow(id: String, date: String, odometerKm: Int, amountPaise: Long, notes: String) {
    insertTimelineLog(id = id, date = date, odometerKm = odometerKm, amountPaise = amountPaise, notes = notes)
}

private fun insertTimelineSnapshot(id: String, daysAgo: Long, score: Int, algoVersion: String) =
    with(timelineDriver()) {
        var left = score
        val points = listOf(35, 30, 20, 15).map { weight -> minOf(left, weight).also { left -= it } }
        execute(
            null,
            "INSERT INTO health_scores (id, car_id, owner_id, score, maintenance_pts, documentation_pts, " +
                "cost_efficiency_pts, history_pts, algo_version, computed_at, created_at, updated_at, " +
                "sync_status) VALUES ('$id', '${LogFixtures.CAR}', '${LogFixtures.OWNER}', $score, " +
                "${points[0]}, ${points[1]}, ${points[2]}, ${points[3]}, '$algoVersion', " +
                "'${daysAgoInstantAtNoon(daysAgo)}', '$TIMELINE_SEEDED_AT', '$TIMELINE_SEEDED_AT', 'PENDING')",
            0,
        )
        announceTimelineWrites()
    }

private fun insertTimelineLog(
    id: String,
    date: String,
    odometerKm: Int,
    amountPaise: Long,
    notes: String,
    billPhotoPath: String? = null,
    fairnessSnapshot: String? = null,
) = with(timelineDriver()) {
    val photo = billPhotoPath?.let { "'$it'" } ?: "NULL"
    val fairness = fairnessSnapshot?.let { "'$it'" } ?: "NULL"
    execute(
        null,
        "INSERT INTO service_logs (id, car_id, owner_id, service_date, odometer_km, total_amount_paise, " +
            "workshop_name, notes, source, bill_id, bill_photo_path, fairness_snapshot, created_at, " +
            "updated_at, sync_status) VALUES ('$id', '${LogFixtures.CAR}', '${LogFixtures.OWNER}', " +
            "'$date', $odometerKm, $amountPaise, 'Sharma Motors', '$notes', 'MANUAL', NULL, $photo, " +
            "$fairness, '$TIMELINE_SEEDED_AT', '$TIMELINE_SEEDED_AT', 'PENDING')",
        0,
    )
    announceTimelineWrites()
}

/** Tell SQLDelight these tables changed — seeds are hand-written SQL, which goes in behind its back. */
private fun announceTimelineWrites() = timelineDriver().notifyListeners(
    "cars",
    "profiles",
    "service_logs",
    "service_log_categories",
    "documents",
    "health_scores",
)

private fun daysAgoInstantAtNoon(days: Long): String =
    Instant.now().minusSeconds(days * SECONDS_PER_DAY).toString()

private const val SECONDS_PER_DAY = 24L * 60 * 60

/** A fixed write timestamp; nothing under test reads it. */
private const val TIMELINE_SEEDED_AT = "2026-07-01T00:00:00Z"

/**
 * A stored fairness snapshot in the shape the data layer reads back: Rs. 4,800 paid against
 * a Rs. 4,100 Pune average, which is Rs. 700 over once the ±10% band is applied.
 *
 * The verdict itself is absent on purpose, as in the service log's seeds — the column stores
 * the evidence and `FairnessReport.of` derives the verdict, so a snapshot written here is
 * judged by the rule the app would apply to one it wrote itself.
 */
private const val OVERCHARGED_SNAPSHOT =
    """{"city":"Pune","checked_at":"$TIMELINE_SEEDED_AT","items":[{"category":"BRAKES",""" +
        """"amount_paise":480000,"city_average_paise":410000,"sample_size":240}]}"""

/* ------------------------------ The filter ------------------------------ */

/**
 * Put the filter back to showing everything.
 *
 * The store is a Koin `single` and the whole suite runs in one process, so a test that
 * unticks a category would otherwise hide it from every test that follows. Replacing the
 * binding is the same move the health suite makes for the entitlement port.
 */
internal fun resetTimelineFilter() {
    GlobalContext.get().loadModules(
        listOf(module { single { TimelineFilterStore() } }),
        allowOverride = true,
    )
}

/* ------------------------------ Navigation ------------------------------ */

/** The first frame waits on the start-destination read, and on a cold start on the seed. */
private const val TIMELINE_START_UP_TIMEOUT_MILLIS = 20_000L

/**
 * Open the Timeline tab from wherever the app started.
 *
 * The bar item is reached by its icon's content description rather than its label, because
 * the tab and the screen it opens are both called "Timeline" and a text match finds both.
 */
internal fun TimelineTestRule.openTimeline() {
    awaitLabel(TimelineCopy.TAB, TIMELINE_START_UP_TIMEOUT_MILLIS)
    onNodeWithContentDescription(TimelineCopy.TAB).performClick()
    awaitTimelineLoaded()
}

/** Step off the tab, so coming back proves what the tab kept. */
internal fun TimelineTestRule.leaveForHomeTab() {
    onNodeWithContentDescription(HOME_TAB_LABEL).performClick()
    awaitGone(TimelineCopy.FILTER_TITLE)
}

/** Come back to the Timeline tab from another one. */
internal fun TimelineTestRule.returnToTimeline() {
    onNodeWithContentDescription(TimelineCopy.TAB).performClick()
    awaitTimelineLoaded()
}

/** The feed replaces the spinner a frame after the tab does, so wait for the feed itself. */
internal fun TimelineTestRule.awaitTimelineLoaded() {
    waitUntil(TIMELINE_TIMEOUT_MILLIS) {
        onAllNodes(hasTestTag(TimelineTestTags.FEED)).fetchSemanticsNodes().isNotEmpty()
    }
}

internal fun TimelineTestRule.openTimelineFilter() {
    onNodeWithTag(TimelineTestTags.FILTER_BUTTON).performClick()
    awaitText(TimelineCopy.FILTER_TITLE)
}

internal fun TimelineTestRule.dismissTimelineFilter() {
    onNodeWithTag(TimelineTestTags.FILTER_APPLY).performClick()
    awaitGone(TimelineCopy.FILTER_TITLE)
}

/** Tick or untick one of the sheet's category rows, by its tag. */
internal fun TimelineTestRule.toggleFilterCategory(rowTag: String) {
    onNodeWithTag(rowTag).performClick()
    waitForIdle()
}

internal fun TimelineTestRule.toggleOnlyFlagged() {
    onNodeWithTag(TimelineTestTags.FILTER_ONLY_FLAGGED).performClick()
    waitForIdle()
}

internal fun TimelineTestRule.openServiceFromTimeline(id: String) {
    scrollFeedTo(TimelineTestTags.serviceRow(id))
    onNodeWithTag(TimelineTestTags.serviceRow(id)).performClick()
}

internal fun TimelineTestRule.tapAddBill(id: String) {
    scrollFeedTo(TimelineTestTags.serviceRow(id))
    onNodeWithTag(TimelineTestTags.addBill(id), useUnmergedTree = true).performClick()
}

internal fun TimelineTestRule.shareFromTimeline() {
    onNodeWithTag(TimelineTestTags.SHARE_BUTTON).performClick()
}

/* ------------------------------ Assertions ------------------------------ */

/**
 * How many rows the feed is showing whose tag starts with [tagPrefix].
 *
 * A prefix rather than the whole tag, because the rows that matter most are keyed by the id
 * of the thing they show — counting "how many service cards" has no single tag to ask for.
 */
internal fun TimelineTestRule.rowCount(tagPrefix: String): Int =
    onAllNodes(hasTestTagPrefix(tagPrefix), useUnmergedTree = true).fetchSemanticsNodes().size

private fun hasTestTagPrefix(prefix: String) = SemanticsMatcher("testTag starts with '$prefix'") { node ->
    node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true
}

/**
 * Scroll the feed until the row tagged [tagPrefix] is on screen, failing if the feed does
 * not hold one.
 *
 * The feed is a `LazyColumn`, so a row below the fold is not composed and simply asking for
 * it would report it missing. Scrolling to it is both how a row is reached and how its
 * presence is proven.
 */
internal fun TimelineTestRule.scrollFeedTo(tagPrefix: String) {
    onNodeWithTag(TimelineTestTags.FEED).performScrollToNode(hasTestTagPrefix(tagPrefix))
    waitForIdle()
}

/**
 * Wait for the row tagged [tagPrefix], scrolling the feed to find it.
 *
 * Retried rather than wrapped in `waitUntil`, whose condition may only *read* the tree —
 * scrolling from inside one deadlocks against the frame it is waiting for. The retry is what
 * covers the frame or two between a database write and the row appearing.
 */
internal fun TimelineTestRule.awaitRow(tagPrefix: String) = retrying("row '$tagPrefix' never appeared") {
    scrollFeedTo(tagPrefix)
}

/**
 * Assert the feed holds no row tagged [tagPrefix] — what a filter is asserted on.
 *
 * Proven by scrolling the whole feed looking for one and requiring that to fail: a plain "no
 * node found" would also pass for a row that is merely below the fold, which is exactly what
 * a filter test must not accept.
 */
internal fun TimelineTestRule.assertRowAbsent(tagPrefix: String) =
    retrying("row '$tagPrefix' is still on the feed") {
        check(runCatching { scrollFeedTo(tagPrefix) }.isFailure) { "found '$tagPrefix'" }
    }

/**
 * Run [action] until it stops throwing, or give up with [message].
 *
 * The feed is driven by a database read, so the first attempt can land a frame early. Each
 * attempt waits for the composition to settle first.
 */
private fun TimelineTestRule.retrying(message: String, action: () -> Unit) {
    val deadline = System.currentTimeMillis() + TIMELINE_TIMEOUT_MILLIS
    var last: Throwable? = null
    while (System.currentTimeMillis() < deadline) {
        waitForIdle()
        runCatching(action).onSuccess { return }.onFailure { last = it }
        Thread.sleep(RETRY_INTERVAL_MILLIS)
    }
    throw AssertionError(message, last)
}

private const val RETRY_INTERVAL_MILLIS = 100L

/**
 * Assert the header line reads [text].
 *
 * Scrolls to the top and retries: the header is item 0 of a lazy feed, so right after a
 * scroll it can still be composed while positioned above the viewport, which reads as
 * "found but not displayed".
 */
internal fun TimelineTestRule.assertSubtitle(text: String) = retrying("header never read '$text'") {
    scrollFeedToTop()
    onNodeWithTag(TimelineTestTags.SUBTITLE, useUnmergedTree = true).assertTextEquals(text)
}

/** Back to the top of the feed, where the header line is. */
internal fun TimelineTestRule.scrollFeedToTop() {
    onNodeWithTag(TimelineTestTags.FEED).performScrollToIndex(0)
    waitForIdle()
}

/**
 * Assert a service card on the timeline carries [text] — its title, its badge, or its
 * flagged amount. Named for this feed because the garage's robot has its own card.
 */
internal fun TimelineTestRule.assertTimelineServiceShows(id: String, text: String) {
    onNode(
        hasTestTag(TimelineTestTags.serviceRow(id)) and hasAnyDescendant(hasText(text)),
        useUnmergedTree = true,
    ).assertExists()
}

/** The Home tab's icon label, for stepping off the timeline and back. */
private const val HOME_TAB_LABEL = "Home"

/** Long enough for a database read and a screen transition, short enough to fail fast. */
private const val TIMELINE_TIMEOUT_MILLIS = 10_000L
