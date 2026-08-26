package com.hopcape.odo

import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.rules.ActivityScenarioRule
import app.cash.sqldelight.db.SqlDriver
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.feature.dashboard.presentation.home.HomeTestTags
import org.koin.core.context.GlobalContext
import java.time.LocalDate

/**
 * The words Home puts on screen, mirrored from the dashboard's `strings.xml`.
 *
 * Copied rather than read, for the same reason as [TimelineCopy]: Compose Resources keeps a
 * feature's generated `Res` internal to its own module, so `:androidApp` cannot reach it.
 * Asserting on the copy an owner actually reads is the point.
 */
internal object HomeCopy {
    const val TAB = "Home"

    /* The header. */
    const val GREETING = "Hi, Rahul"

    /* The score card. */
    const val HEALTH_SCORE = "HEALTH SCORE"
    const val SEE_BREAKDOWN = "See breakdown"
    const val FIRST_MONTH = "Your first month of tracking."

    /* The two stat cards. */
    const val RUNNING_COST = "RUNNING COST"
    const val OVERCHARGE_CAUGHT = "OVERCHARGE CAUGHT"
    const val NO_OVERCHARGES = "No overcharges caught yet"
    const val ONE_BILL_FLAGGED = "1 bill flagged"
    const val NOT_ENOUGH_DISTANCE = "Not enough distance yet"

    /* The attention card. */
    const val NOTHING_DUE = "Nothing needs attention"
    const val NOTHING_DUE_SUB = "All documents valid · no service due"
    const val PUC_EXPIRED = "PUC has expired"
    const val SERVICE_OVERDUE = "Service overdue"

    /* The insight card. */
    const val INSIGHT_EYEBROW = "ODO INSIGHT"
    const val RESALE_EYEBROW = "RESALE READY"

    /* Recent activity. */
    const val RECENT = "RECENT ACTIVITY"
    const val TIMELINE_LINK = "Timeline"
    const val VERIFIED = "Verified"
    const val SELF_REPORTED = "Self-reported"

    /* The new-user path. */
    const val SCORE_WAITING = "Your score is waiting"
    const val SCAN_FIRST = "Scan your first bill"
    const val SETUP_CAR = "Car added"
    const val SETUP_BILL = "Scan your last bill"
    const val SETUP_DOCS = "Add insurance & PUC"

    /** "Insurance expires in 12 days". */
    fun expiresIn(name: String, days: Int) = "$name expires in $days days"

    /** "GET SET UP · 1 OF 3 DONE". */
    fun setUp(done: Int, total: Int = 3) = "GET SET UP · $done OF $total DONE"

    /** "All 3 services verified — your record beats most listings." */
    fun resaleReady(count: Int) = "All $count services verified — your record beats most listings."

    /** "2 services logged with no bill attached — scan one to unlock fairness checks." */
    fun noBills(count: Int) =
        "$count services logged with no bill attached — scan one to unlock fairness checks."
}

/**
 * The car's record as Home reads it.
 *
 * Every date here is **relative to today**, unlike the timeline's fixed ones. Home is a
 * dashboard of deadlines: whether a paper has lapsed and whether a service is overdue are
 * both answered against the day the test runs, so a fixed date would quietly stop meaning
 * what it meant the week it was written.
 */
internal object HomeFixtures {
    const val CAR_NAME = "Maruti Suzuki Swift VXI"

    /* A recent, bill-backed service — nothing overdue, nothing to flag. */
    const val RECENT_SERVICE_ID = "home-log-recent"
    const val RECENT_SERVICE_DAYS_AGO = 30L
    const val RECENT_SERVICE_KM = 52_000
    const val RECENT_SERVICE_PAISE = 320_000L
    const val RECENT_SERVICE_NOTE = "Oil change + filter"

    /* A service long past the six-month interval. */
    const val OVERDUE_SERVICE_ID = "home-log-overdue"
    const val OVERDUE_SERVICE_DAYS_AGO = 400L
    const val OVERDUE_SERVICE_KM = 41_000

    /* An entry the fairness check judged over the city average. */
    const val FLAGGED_SERVICE_ID = "home-log-flagged"
    const val FLAGGED_SERVICE_DAYS_AGO = 60L
    const val FLAGGED_SERVICE_KM = 50_000
    const val FLAGGED_SERVICE_PAISE = 480_000L

    /** What the stored verdict works out to: Rs. 4,800 paid against a Rs. 4,100 average. */
    const val FLAGGED_OVER = "Rs. 700"

    const val INSURANCE_ID = "home-doc-insurance"
    const val PUC_ID = "home-doc-puc"

    /** A PUC that ran out last week — lapsed however long the suite has been on the shelf. */
    const val PUC_LAPSED_DAYS_AGO = 7L

    /** An insurance renewal that is close enough to sit inside its 30-day window. */
    const val INSURANCE_EXPIRING_DAYS = 12L
}

private typealias HomeTestRule = AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>

/* ------------------------------ Database ------------------------------ */

private fun homeDriver(): SqlDriver = GlobalContext.get().get()

/**
 * Empty everything Home reads.
 *
 * [resetTimeline] already covers the profile, the car, the service log, the documents and
 * the score history — which is every source the dashboard aggregates.
 */
internal fun resetHome() = resetTimeline()

/** A finished setup: an onboarded profile and one car. */
internal fun seedHomeOwner() = seedOnboardedOwner()

/**
 * Empty only what the dashboard is *of*, leaving the profile and the car in place.
 *
 * The car outlives each test on purpose: where the app opens is read once per launch, and
 * the rule starts the activity before `@Before` runs, so a test that deleted the profile
 * would be driving an app that had already decided to open on the welcome carousel.
 */
internal fun clearHomeData() = clearTimelineData()

/** A bill-backed service [daysAgo] days back — recent enough to leave nothing overdue. */
internal fun seedHomeService(
    id: String = HomeFixtures.RECENT_SERVICE_ID,
    daysAgo: Long = HomeFixtures.RECENT_SERVICE_DAYS_AGO,
    odometerKm: Int = HomeFixtures.RECENT_SERVICE_KM,
    amountPaise: Long = HomeFixtures.RECENT_SERVICE_PAISE,
    notes: String = HomeFixtures.RECENT_SERVICE_NOTE,
    withBill: Boolean = true,
    overcharged: Boolean = false,
) = with(homeDriver()) {
    val photo = if (withBill) "'/bills/$id.jpg'" else "NULL"
    val fairness = if (overcharged) "'$HOME_OVERCHARGED_SNAPSHOT'" else "NULL"
    execute(
        null,
        "INSERT INTO service_logs (id, car_id, owner_id, service_date, odometer_km, total_amount_paise, " +
            "workshop_name, notes, source, bill_id, bill_photo_path, fairness_snapshot, created_at, " +
            "updated_at, sync_status) VALUES ('$id', '${LogFixtures.CAR}', '${LogFixtures.OWNER}', " +
            "'${daysAgoDate(daysAgo)}', $odometerKm, $amountPaise, 'Sharma Motors', '$notes', 'MANUAL', " +
            "NULL, $photo, $fairness, '$HOME_SEEDED_AT', '$HOME_SEEDED_AT', 'PENDING')",
        0,
    )
    announceHomeWrites()
}

/**
 * A paper in the vault, expiring [expiresInDays] from today — negative for one that lapsed.
 *
 * The expiry is relative because that is the whole question Home asks of a document.
 */
internal fun seedHomeDocument(
    id: String,
    type: DocumentType,
    expiresInDays: Long,
    addedDaysAgo: Long = 200,
) = with(homeDriver()) {
    val added = daysAgoDate(addedDaysAgo)
    execute(
        null,
        "INSERT INTO documents (id, car_id, owner_id, doc_type, title, storage_path, doc_source, " +
            "issued_date, expiry_date, created_at, updated_at, sync_status) VALUES " +
            "('$id', '${LogFixtures.CAR}', '${LogFixtures.OWNER}', '${type.name}', NULL, " +
            "'documents/${LogFixtures.CAR}/$id.pdf', 'UPLOADED', NULL, '${daysAgoDate(-expiresInDays)}', " +
            "'${added}T09:00:00Z', '${added}T09:00:00Z', 'PENDING')",
        0,
    )
    announceHomeWrites()
}

/** Every paper the score counts, all comfortably in force — the all-clear fixture. */
internal fun seedHomeValidDocuments() {
    seedHomeDocument(HomeFixtures.INSURANCE_ID, DocumentType.INSURANCE, expiresInDays = 300)
    seedHomeDocument(HomeFixtures.PUC_ID, DocumentType.PUC, expiresInDays = 120)
}

private fun daysAgoDate(days: Long): LocalDate = LocalDate.now().minusDays(days)

/** Tell SQLDelight these tables changed — seeds are hand-written SQL, which goes in behind its back. */
private fun announceHomeWrites() = homeDriver().notifyListeners(
    "cars",
    "profiles",
    "service_logs",
    "documents",
    "health_scores",
)

/** A fixed write timestamp; nothing on Home reads it. */
private const val HOME_SEEDED_AT = "2026-07-01T00:00:00Z"

/**
 * A stored fairness snapshot in the shape the data layer reads back: Rs. 4,800 paid against
 * a Rs. 4,100 Pune average, which is Rs. 700 over once the ±10% band is applied.
 */
private const val HOME_OVERCHARGED_SNAPSHOT =
    """{"city":"Pune","checked_at":"$HOME_SEEDED_AT","items":[{"category":"BRAKES",""" +
        """"amount_paise":480000,"city_average_paise":410000,"sample_size":240}]}"""

/* ------------------------------ Navigation ------------------------------ */

/** The first frame waits on the start-destination read, and on a cold start on the seed. */
private const val HOME_START_UP_TIMEOUT_MILLIS = 20_000L

/** Long enough for a database read and a screen transition, short enough to fail fast. */
private const val HOME_TIMEOUT_MILLIS = 10_000L

/**
 * Open Home from wherever the app started.
 *
 * Home is the start destination for an onboarded owner, so this usually only waits — but a
 * test that navigated away still comes back through the same door.
 */
internal fun HomeTestRule.openHome() {
    awaitLabel(HomeCopy.TAB, HOME_START_UP_TIMEOUT_MILLIS)
    onNodeWithContentDescription(HomeCopy.TAB).performClick()
    awaitHomeLoaded()
}

/** Come back to Home from another tab. */
internal fun HomeTestRule.returnToHome() = openHome()

/**
 * Wait for the dashboard itself rather than the tab.
 *
 * The skeleton is on screen a frame before the record lands, and both carry the screen tag,
 * so waiting on the screen alone would let an assertion run against grey boxes.
 */
internal fun HomeTestRule.awaitHomeLoaded() {
    waitUntil(HOME_TIMEOUT_MILLIS) {
        onAllNodes(hasTestTag(HomeTestTags.SKELETON)).fetchSemanticsNodes().isEmpty() &&
            onAllNodes(hasTestTag(HomeTestTags.GREETING)).fetchSemanticsNodes().isNotEmpty()
    }
}

/*
 * Every tap brings its target into view first.
 *
 * Home is one long scroll and most of these sit below the fold. `performClick` does not
 * scroll: it injects a tap at the node's centre, and for a node outside the viewport that
 * centre is off-screen, so the gesture lands on nothing. Nothing throws — the node exists and
 * the click is delivered — and the test fails several lines later on the destination screen
 * never arriving, which is a long way from the cause.
 *
 * [scrollIntoView] rather than `performScrollTo` because not every one of these lives in the
 * scroller: the empty-state cards are drawn in a box that fills the screen and has no scroll
 * action at all, and asking to scroll inside it throws. "Already in view" and "scrolled into
 * view" are the same outcome here, so the distinction is not worth making the caller carry.
 */
private fun SemanticsNodeInteraction.scrollIntoView(): SemanticsNodeInteraction = apply {
    runCatching { performScrollTo() }
}

internal fun HomeTestRule.tapSeeBreakdown() =
    onNodeWithTag(HomeTestTags.BREAKDOWN_LINK).scrollIntoView().performClick()

internal fun HomeTestRule.tapAttention() =
    onNodeWithTag(HomeTestTags.ATTENTION_CARD).scrollIntoView().performClick()

internal fun HomeTestRule.tapRecent() =
    onNodeWithTag(HomeTestTags.RECENT_ROW).scrollIntoView().performClick()

internal fun HomeTestRule.tapTimelineLink() =
    onNodeWithTag(HomeTestTags.TIMELINE_LINK).scrollIntoView().performClick()

internal fun HomeTestRule.tapScanFirstBill() =
    onNodeWithTag(HomeTestTags.SCAN_FIRST_BUTTON).scrollIntoView().performClick()

internal fun HomeTestRule.tapChecklistDocuments() =
    onNodeWithTag(HomeTestTags.CHECKLIST_DOCS).scrollIntoView().performClick()

/* ------------------------------ Assertions ------------------------------ */

/**
 * Assert the card tagged [tag] carries [text].
 *
 * Matched against the unmerged tree: a tappable card merges its children away while a plain
 * one keeps them, so a text assertion on the tagged node works for one and not the other.
 *
 * Matched as a substring, unlike the timeline's rows: Home writes several facts into one
 * line ("02 Aug · Verified", "Lapsed on 25 Jul · renew now"), and asserting the whole line
 * would mean rebuilding a date the test only knows relative to today.
 */
internal fun HomeTestRule.assertCardShows(tag: String, text: String) {
    onNode(
        hasTestTag(tag) and hasAnyDescendant(hasText(text, substring = true)),
        useUnmergedTree = true,
    ).assertExists()
}

/**
 * Wait for [text] to appear inside the card tagged [tag].
 *
 * Home re-reads on every write, so an assertion straight after a seed can land a frame
 * early. Retried rather than wrapped in `waitUntil`, which is the shape the timeline's robot
 * settled on for the same reason.
 */
internal fun HomeTestRule.awaitCardShows(tag: String, text: String) =
    retryingOnHome("card '$tag' never read '$text'") {
        assertCardShows(tag, text)
    }

private fun HomeTestRule.retryingOnHome(message: String, action: () -> Unit) {
    val deadline = System.currentTimeMillis() + HOME_TIMEOUT_MILLIS
    var last: Throwable? = null
    while (System.currentTimeMillis() < deadline) {
        waitForIdle()
        runCatching(action).onSuccess { return }.onFailure { last = it }
        Thread.sleep(HOME_RETRY_INTERVAL_MILLIS)
    }
    throw AssertionError(message, last)
}

private const val HOME_RETRY_INTERVAL_MILLIS = 100L
