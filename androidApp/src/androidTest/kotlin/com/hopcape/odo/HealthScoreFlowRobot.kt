package com.hopcape.odo

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.rules.ActivityScenarioRule
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.entitlement.EntitlementSource
import com.hopcape.odo.core.domain.entitlement.Entitlements
import com.hopcape.odo.core.domain.entitlement.Plan
import com.hopcape.odo.core.domain.health.model.HealthFactorKind
import com.hopcape.odo.feature.healthscore.presentation.HealthScoreTestTags
import kotlinx.coroutines.flow.flowOf
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import java.time.Instant
import java.time.LocalDate

/**
 * The words the health score puts on screen, mirrored from its `strings.xml`.
 *
 * Copied rather than read, for the same reason as [CostCopy]: Compose Resources keeps a
 * feature's generated `Res` internal to its own module, so `:androidApp` cannot reach it.
 * Asserting on the copy an owner actually reads is the point.
 */
internal object HealthCopy {
    const val HOME_TAB = "Home"
    const val HOME_SEE_BREAKDOWN = "See breakdown"
    const val TITLE = "Health Score"
    const val BACK = "Back"
    const val INFO = "How this is calculated"

    /* Bands, as the dial labels them. */
    const val BAND_NEEDS_CARE = "NEEDS CARE"
    const val BAND_FAIR = "FAIR"
    const val BAND_GOOD = "GOOD"

    /* Under the dial. */
    const val NOTHING_LOGGED = "Log a service or add a document to start building your score"

    /* What Home shows instead of a score while there is nothing to score. */
    const val HOME_SCORE_WAITING = "Your score is waiting"
    const val HOME_SCORE_WAITING_BODY =
        "Add one bill or document and Odo can start scoring your car’s health."

    /* Breakdown. */
    const val BREAKDOWN = "WHAT MAKES UP YOUR SCORE"
    const val FACTOR_MAINTENANCE = "Maintenance regularity"
    const val FACTOR_DOCUMENTATION = "Documentation"
    const val FACTOR_COST = "Cost fairness"
    const val FACTOR_HISTORY = "History completeness"
    const val OPPORTUNITY_COST = "Scan more bills so we can verify fair pricing."

    /* Free tier. */
    const val PAYWALL_TITLE = "See your full breakdown"
    const val PAYWALL_CTA = "Unlock with Pro"

    /* The paywall screen this lands on. The HEALTH_BREAKDOWN trigger has no framing of
     * its own yet, so it falls back to the generic one — the trigger still travels for
     * the paywall's analytics. */
    const val PAYWALL_SCREEN_HEADLINE = "Everything Odo can do, unlocked."

    /* "How your score works". */
    const val INFO_TITLE = "How your score works"
    const val INFO_CTA = "Got it"
    const val INFO_BAND_EXCELLENT = "Excellent"
    const val INFO_BAND_FAIR = "Fair"
    const val INFO_BAND_NEEDS_CARE = "Needs care"
    const val INFO_RANGE_EXCELLENT = "85–100"
    const val INFO_RANGE_GOOD = "70–84"
    const val INFO_RANGE_FAIR = "50–69"
    const val INFO_RANGE_NEEDS_CARE = "below 50"

    /** "35 pts" — a factor's weight in the explainer. */
    fun weight(points: Int) = "$points pts"

    /** "28 / 35" — a factor's earned points in the breakdown. */
    fun factorScore(earned: Int, max: Int) = "$earned / $max"

    /** The nudge's header, which names what the next action is worth. */
    fun opportunityLabel(points: Int) = "BIGGEST OPPORTUNITY · +$points POINTS"

    fun deltaUp(points: Int) = "$points points this month"

    fun deltaDown(points: Int) = "$points points — take action"
}

/**
 * The car, its history, its papers, and the score they add up to.
 *
 * Dates are relative to the day the test runs, because every rule the score applies is
 * relative to today: a service is overdue *now*, a policy lapses *now*. Fixed dates would
 * drift out of their windows and take the whole suite with them.
 *
 * The scores are asserted as literals because they are arithmetic over this seed and
 * nothing else — no seeded reference data, no network. A change to the point rules is
 * meant to fail here; that is the test's job, and `HealthScoreCalculator.RULES_VERSION`
 * moving is the reminder to come and re-read these numbers.
 */
internal object HealthFixtures {
    /** The car's baseline reading, dated far enough back to anchor everything. */
    const val BASELINE_KM = 30_000
    const val BASELINE_DAYS_AGO = 800L

    /** A repair with its bill attached, most of a year ago. */
    const val OLD_SERVICE_ID = "health-log-old"
    const val OLD_SERVICE_DAYS_AGO = 200L
    const val OLD_SERVICE_KM = 36_000
    const val OLD_SERVICE_PAISE = 500_000L

    /** A routine service with its bill attached, last month. */
    const val RECENT_SERVICE_ID = "health-log-recent"
    const val RECENT_SERVICE_DAYS_AGO = 30L
    const val RECENT_SERVICE_KM = 42_000
    const val RECENT_SERVICE_PAISE = 800_000L

    /* The papers. */
    const val INSURANCE_ID = "health-doc-insurance"
    const val PUC_ID = "health-doc-puc"
    const val RC_ID = "health-doc-rc"
    const val INSURANCE_DAYS_LEFT = 300L
    const val PUC_DAYS_LEFT = 100L

    /**
     * The score the seed adds up to: serviced on time and twice this year (35), all three
     * papers in force (30), no bill ever benchmarked (0), both entries bill-backed on a
     * clean timeline (11).
     */
    const val SCORE = 76
    const val MAINTENANCE_PTS = 35
    const val DOCUMENTATION_PTS = 30
    const val COST_PTS = 0
    const val HISTORY_PTS = 11

    /** Without the insurance, documentation loses its twelve and the band drops. */
    const val SCORE_WITHOUT_INSURANCE = 64
    const val DOCUMENTATION_WITHOUT_INSURANCE = 18

    /** Without the PUC, before it is added while the screen is open. */
    const val SCORE_WITHOUT_PUC = 66

    /** Driven far enough past the interval to lose most of the maintenance points. */
    const val OVERDUE_ODOMETER_KM = 56_000
    const val SCORE_WHEN_OVERDUE = 61
    const val MAINTENANCE_PTS_WHEN_OVERDUE = 20

    /** A month-old score to measure today's against. */
    const val BASELINE_SNAPSHOT_DAYS_AGO = 40L
    const val LOWER_SNAPSHOT_SCORE = 66
    const val HIGHER_SNAPSHOT_SCORE = 86
    const val DELTA_POINTS = 10
}

private typealias HealthTestRule = AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>

/* ------------------------------ Database ------------------------------ */

private fun healthDriver(): SqlDriver = GlobalContext.get().get()

/**
 * Empty everything the score reads, including its own history.
 *
 * [resetVault] covers the profile, the car, the service log and the documents; the
 * snapshots are this feature's table, and a leftover one would give the next test a delta
 * it never asked for.
 */
internal fun resetHealthScore() {
    resetVault()
    healthDriver().execute(null, "DELETE FROM health_scores", 0)
    healthDriver().notifyListeners("health_scores")
}

/**
 * A finished setup: an onboarded profile and one car reading 30,000 km, dated far enough
 * back that every later reading sits above it.
 */
internal fun seedHealthOwner() = with(healthDriver()) {
    execute(
        null,
        "INSERT INTO profiles (id, full_name, onboarding_goal, onboarding_completed_at, city, " +
            "created_at, updated_at, sync_status) VALUES ('${LogFixtures.OWNER}', 'Rahul', " +
            "'SAVE_MONEY', '$HEALTH_SEEDED_AT', 'Pune', '$HEALTH_SEEDED_AT', '$HEALTH_SEEDED_AT', 'PENDING')",
        0,
    )
    execute(
        null,
        "INSERT INTO cars (id, owner_id, make, model, variant, year, fuel_type, registration_number, " +
            "current_odometer_km, is_primary, created_at, updated_at, sync_status) VALUES " +
            "('${LogFixtures.CAR}', '${LogFixtures.OWNER}', 'Maruti Suzuki', 'Swift', 'VXI', 2020, " +
            "'PETROL', 'MH12AB1234', ${HealthFixtures.BASELINE_KM}, 1, " +
            "'${daysAgoDate(HealthFixtures.BASELINE_DAYS_AGO)}T00:00:00Z', '$HEALTH_SEEDED_AT', 'PENDING')",
        0,
    )
    announceHealthWrites()
}

/** Two services, both with a bill attached, rising with their dates. */
internal fun seedHealthHistory() {
    insertHealthLog(
        id = HealthFixtures.OLD_SERVICE_ID,
        daysAgo = HealthFixtures.OLD_SERVICE_DAYS_AGO,
        odometerKm = HealthFixtures.OLD_SERVICE_KM,
        amountPaise = HealthFixtures.OLD_SERVICE_PAISE,
        category = "BRAKES",
    )
    insertHealthLog(
        id = HealthFixtures.RECENT_SERVICE_ID,
        daysAgo = HealthFixtures.RECENT_SERVICE_DAYS_AGO,
        odometerKm = HealthFixtures.RECENT_SERVICE_KM,
        amountPaise = HealthFixtures.RECENT_SERVICE_PAISE,
        category = "OIL_CHANGE",
    )
}

/**
 * The three papers the score counts. [withInsurance] and [withPuc] leave one out, which is
 * how the documentation rules are exercised: the points are supposed to follow what is
 * actually on file and in date.
 */
internal fun seedHealthDocuments(
    withInsurance: Boolean = true,
    withPuc: Boolean = true,
    insuranceExpiresInDays: Long = HealthFixtures.INSURANCE_DAYS_LEFT,
) {
    if (withInsurance) {
        seedDocument(
            id = HealthFixtures.INSURANCE_ID,
            type = DocumentType.INSURANCE,
            expiresOn = LocalDate.now().plusDays(insuranceExpiresInDays),
        )
    }
    if (withPuc) {
        seedDocument(
            id = HealthFixtures.PUC_ID,
            type = DocumentType.PUC,
            expiresOn = LocalDate.now().plusDays(HealthFixtures.PUC_DAYS_LEFT),
        )
    }
    seedDocument(id = HealthFixtures.RC_ID, type = DocumentType.RC)
}

/** Add the missing PUC while the screen is open — the score has to notice. */
internal fun addPucNow() {
    seedDocument(
        id = HealthFixtures.PUC_ID,
        type = DocumentType.PUC,
        expiresOn = LocalDate.now().plusDays(HealthFixtures.PUC_DAYS_LEFT),
    )
}

/**
 * Move the car's own reading, the way the garage does, without logging a service. The
 * distance half of the service interval is measured against exactly this.
 */
internal fun driveCarTo(odometerKm: Int) = with(healthDriver()) {
    execute(
        null,
        "UPDATE cars SET current_odometer_km = $odometerKm, odometer_updated_at = '${nowIso()}' " +
            "WHERE id = '${LogFixtures.CAR}'",
        0,
    )
    announceHealthWrites()
}

/**
 * A score the car had [daysAgo] days ago — what today's is compared against.
 *
 * The components have to add up to [score], not just the `score` column: the mapper reads
 * a stored score back from its breakdown, so a row whose columns disagree with its total
 * resolves to the breakdown. Filling the factors in weight order is the shortest way to
 * write a row that means what it says.
 */
internal fun seedHealthSnapshot(daysAgo: Long, score: Int) = with(healthDriver()) {
    var left = score
    val points = listOf(35, 30, 20, 15).map { weight ->
        minOf(left, weight).also { left -= it }
    }
    execute(
        null,
        "INSERT INTO health_scores (id, car_id, owner_id, score, maintenance_pts, documentation_pts, " +
            "cost_efficiency_pts, history_pts, algo_version, computed_at, created_at, updated_at, " +
            "sync_status) VALUES ('snapshot-$daysAgo', '${LogFixtures.CAR}', '${LogFixtures.OWNER}', " +
            "$score, ${points[0]}, ${points[1]}, ${points[2]}, ${points[3]}, 'rule-v1', " +
            "'${daysAgoInstant(daysAgo)}', '$HEALTH_SEEDED_AT', '$HEALTH_SEEDED_AT', 'PENDING')",
        0,
    )
    announceHealthWrites()
}

/** How many scores the car has on file — what "kept once, not on every read" is asserted on. */
internal fun healthSnapshotCount(): Long = healthDriver().executeQuery(
    identifier = null,
    sql = "SELECT COUNT(*) FROM health_scores WHERE car_id = '${LogFixtures.CAR}'",
    mapper = { cursor -> QueryResult.Value(if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L) },
    parameters = 0,
).value

/** The most recent score on file, or `null` when the car has none. */
internal fun latestSnapshotScore(): Long? = healthDriver().executeQuery(
    identifier = null,
    sql = "SELECT score FROM health_scores WHERE car_id = '${LogFixtures.CAR}' " +
        "ORDER BY computed_at DESC LIMIT 1",
    mapper = { cursor -> QueryResult.Value(if (cursor.next().value) cursor.getLong(0) else null) },
    parameters = 0,
).value

private fun insertHealthLog(
    id: String,
    daysAgo: Long,
    odometerKm: Int,
    amountPaise: Long,
    category: String,
) = with(healthDriver()) {
    // Every seeded service carries a bill photo: a Verified entry is what the history
    // factor pays for, and the scanner that would attach one is M2.
    execute(
        null,
        "INSERT INTO service_logs (id, car_id, owner_id, service_date, odometer_km, total_amount_paise, " +
            "workshop_name, notes, source, bill_id, bill_photo_path, fairness_snapshot, created_at, " +
            "updated_at, sync_status) VALUES ('$id', '${LogFixtures.CAR}', '${LogFixtures.OWNER}', " +
            "'${daysAgoDate(daysAgo)}', $odometerKm, $amountPaise, 'Sharma Motors', NULL, 'MANUAL', NULL, " +
            "'/bills/$id.jpg', NULL, '$HEALTH_SEEDED_AT', '$HEALTH_SEEDED_AT', 'PENDING')",
        0,
    )
    execute(null, "INSERT INTO service_log_categories (service_log_id, category) VALUES ('$id', '$category')", 0)
    announceHealthWrites()
}

/** Tell SQLDelight that these tables changed — seeds are hand-written SQL, which goes in behind its back. */
private fun announceHealthWrites() = healthDriver().notifyListeners(
    "cars",
    "profiles",
    "service_logs",
    "service_log_categories",
    "documents",
    "health_scores",
)

private fun daysAgoDate(days: Long): String = LocalDate.now().minusDays(days).toString()

private fun daysAgoInstant(days: Long): String = Instant.now().minusSeconds(days * SECONDS_PER_DAY).toString()

private fun nowIso(): String = Instant.now().toString()

private const val SECONDS_PER_DAY = 24L * 60 * 60

/** A fixed write timestamp; nothing under test reads it. */
private const val HEALTH_SEEDED_AT = "2026-07-01T00:00:00Z"

/* ------------------------------ Entitlement ------------------------------ */

/**
 * Put the owner on Pro or the free plan for the rest of the test.
 *
 * The shipped source answers free for everyone until billing lands, so the Pro half of this
 * screen is unreachable in the running app — overriding the port is the only way to walk
 * both. Every test sets it explicitly rather than relying on the default, because the
 * definition outlives the test that changed it.
 */
internal fun setProEntitlement(isPro: Boolean) {
    val entitlements = Entitlements(if (isPro) Plan.PRO else Plan.FREE)
    GlobalContext.get().loadModules(
        listOf(
            module {
                single<EntitlementSource> {
                    object : EntitlementSource {
                        override fun observe() = flowOf(entitlements)
                        override suspend fun refresh() = Unit
                    }
                }
            },
        ),
        allowOverride = true,
    )
}

/* ------------------------------ Navigation ------------------------------ */

/** The first frame waits on the start-destination read, and on a cold start on the seed. */
private const val HEALTH_START_UP_TIMEOUT_MILLIS = 20_000L

/** Open the score the way an owner does: from Home's health card. */
internal fun HealthTestRule.openHealthScore() {
    openHomeTab()
    awaitText(HealthCopy.HOME_SEE_BREAKDOWN)
    onNodeWithText(HealthCopy.HOME_SEE_BREAKDOWN).performClick()
    awaitText(HealthCopy.TITLE)
}

/**
 * Home itself, which is as far as a car with nothing logged gets: the breakdown link is
 * part of the health card, and Home shows the setup checklist instead of one until there
 * is something to score.
 */
internal fun HealthTestRule.openHomeTab() {
    awaitText(HealthCopy.HOME_TAB, HEALTH_START_UP_TIMEOUT_MILLIS)
    onNodeWithText(HealthCopy.HOME_TAB).performClick()
}

internal fun HealthTestRule.leaveHealthScore() {
    onNodeWithContentDescription(HealthCopy.BACK).performClick()
    awaitGone(HealthCopy.TITLE)
}

internal fun HealthTestRule.openScoreInfo() {
    onNodeWithContentDescription(HealthCopy.INFO).performClick()
    awaitText(HealthCopy.INFO_TITLE)
}

internal fun HealthTestRule.dismissScoreInfo() {
    onNodeWithText(HealthCopy.INFO_CTA).performClick()
    awaitGone(HealthCopy.INFO_TITLE)
}

internal fun HealthTestRule.tapUnlock() {
    onNodeWithText(HealthCopy.PAYWALL_CTA).performScrollTo().performClick()
}

/* ------------------------------ Assertions ------------------------------ */

/** The number in the dial as the owner reads it, or `null` before the score has landed. */
internal fun HealthTestRule.healthScore(): String? = onAllNodes(
    hasTestTag(HealthScoreTestTags.SCORE),
    useUnmergedTree = true,
).fetchSemanticsNodes()
    .firstOrNull()
    ?.config
    ?.getOrNull(SemanticsProperties.Text)
    ?.firstOrNull()
    ?.text

/** Wait until the dial reads [score] — the figures land a frame after the screen does. */
internal fun HealthTestRule.awaitHealthScore(score: Int) {
    waitUntil(HEALTH_TIMEOUT_MILLIS) { healthScore() == score.toString() }
}

/** Assert one factor's row says [text] — its title, or its earned-out-of-max figure. */
internal fun HealthTestRule.assertFactorRowShows(kind: HealthFactorKind, text: String) {
    onNode(
        hasTestTag(HealthScoreTestTags.factorRow(kind)) and hasAnyDescendant(hasText(text)),
        useUnmergedTree = true,
    ).assertExists()
}

/** Scroll a line into view before reading or tapping it — the breakdown runs past the fold. */
internal fun HealthTestRule.scrollToHealthText(text: String) {
    onNodeWithText(text).performScrollTo()
}

/** Long enough for a database read and a recomputation, short enough to fail fast. */
private const val HEALTH_TIMEOUT_MILLIS = 5_000L
