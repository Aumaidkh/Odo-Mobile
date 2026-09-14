package com.hopcape.odo

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.sqldelight.db.SqlDriver
import arrow.core.right
import com.hopcape.odo.core.domain.benchmark.BenchmarkBasis
import com.hopcape.odo.core.domain.benchmark.BenchmarkScope
import com.hopcape.odo.core.domain.benchmark.PriceBand
import com.hopcape.odo.core.domain.benchmark.PriceBandRepository
import com.hopcape.odo.core.domain.schedule.ServiceInterval
import com.hopcape.odo.core.domain.schedule.ServiceIntervalRepository
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.navigation.NavigationManager
import com.hopcape.odo.core.navigation.OdoDestination
import com.hopcape.odo.core.navigation.navigateTo
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.dsl.module

/**
 * Takes the screenshot the checklist's PR carries, rather than describing the change.
 *
 * The schedule and the bands are injected. Both are Supabase reference tables and a local
 * debug build has no session, so a hand-driven screenshot photographs the empty state — the
 * exact failure `.github/screenshots/README.md` exists to stop. What is injected is the data
 * an owner with the reference tables entered would see.
 *
 * Run it and collect the files with the two commands in that README.
 */
@RunWith(AndroidJUnit4::class)
class ServiceChecklistScreenshotTest {

    private val rule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val chain: RuleChain = RuleChain
        // The runner pins every key to its compiled default, and this one is a launch gate
        // that ships closed. A suite that drives the feature says so in its own file.
        .outerRule(PinnedConfig("service_checklist_enabled", value = "true", compiledDefault = "false"))
        .around(
            DeviceState {
                resetHome()
                seedHomeOwner()
                // A real bill with lines on it, not a bare total: the checklist reads "when
                // was this job last done" through the matcher, and a total names no job.
                seedServicedJobs()
                installSchedule()
                installBands()
            },
        )
        .around(rule)

    @Test
    fun capturesTheChecklist() {
        // The dashboard first. A push before the nav host is collecting is a push that lands
        // nowhere, and the screen it should have replaced is still what gets photographed.
        rule.awaitText(HOME_TAB)

        rule.push(OdoDestination.ServiceChecklist(entry = "MANUAL"))
        rule.awaitText(HEADLINE)
        rule.captureScreen("service-checklist")

        // The three questions are the bottom of the card and the point of the screen, so
        // they get their own frame rather than being left below the fold.
        // The last question, not the heading: scrolling a heading into view leaves the
        // three lines under it below the fold, which is the whole section.
        rule.onNodeWithText(LAST_QUESTION).performScrollTo()
        rule.captureScreen("service-checklist-questions")
    }

    private companion object {
        const val HOME_TAB = "Home"
        const val HEADLINE = "This service should cover"
        const val LAST_QUESTION = "\"Write down what the final bill will be.\""
    }
}

private typealias ChecklistTestRule =
    AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>

/** Pushes a destination straight onto the back stack. */
private fun ChecklistTestRule.push(destination: OdoDestination) {
    runOnUiThread { GlobalContext.get().get<NavigationManager>().navigateTo(destination) }
}

/**
 * A bill whose lines name two scheduled jobs.
 *
 * Lines, not a bare total: the checklist reads "when was this job last done" through the same
 * matcher the bill check uses, and a total names no job at all.
 */
private fun seedServicedJobs() {
    val lines = """[{"label":"Engine oil + filter","category":"OIL_CHANGE","amount_paise":580000},""" +
        """{"label":"Air filter","category":"OTHER","amount_paise":95000}]"""
    val driver: SqlDriver = GlobalContext.get().get()
    driver.execute(
        null,
        "INSERT INTO service_logs (id, car_id, owner_id, service_date, odometer_km, " +
            "total_amount_paise, workshop_name, notes, source, bill_id, bill_photo_path, " +
            "fairness_snapshot, line_items, created_at, updated_at, sync_status) VALUES " +
            "('checklist-service', '${LogFixtures.CAR}', '${LogFixtures.OWNER}', '2024-06-01', " +
            "29000, 675000, 'Sharma Motors', NULL, 'SCANNED', NULL, " +
            "'bills/test-car/checklist-service.jpg', NULL, '$lines', " +
            "'$SEEDED_AT', '$SEEDED_AT', 'PENDING')",
        0,
    )
    driver.notifyListeners("cars", "profiles", "service_logs", "service_log_categories")
}

private const val SEEDED_AT = "2024-06-02T00:00:00Z"

/** The default rule set Track A3 enters, as a Maruti/Hyundai-free default. */
private fun installSchedule() {
    val schedule = listOf(
        ServiceInterval("engine_oil", "Engine oil + filter", km = 10_000),
        ServiceInterval("air_filter", "Air filter", km = 20_000),
        ServiceInterval("brake_fluid", "Brake fluid", months = 36),
        ServiceInterval("coolant", "Coolant flush", km = 60_000),
    ).associateBy { it.slug }
    GlobalContext.get().loadModules(
        listOf(module { single<ServiceIntervalRepository> { ServiceIntervalRepository { schedule.right() } } }),
        allowOverride = true,
    )
}

/** One band per job, so the cost line is a real sum rather than an absent card. */
private fun installBands() {
    val band = PriceBand(
        low = Amount.of(210_000).getOrNull()!!,
        typical = Amount.of(240_000).getOrNull()!!,
        high = Amount.of(275_000).getOrNull()!!,
        sampleSize = 0,
        scope = BenchmarkScope.MODELLED,
        basis = BenchmarkBasis.MODELLED,
    )
    GlobalContext.get().loadModules(
        listOf(module { single<PriceBandRepository> { PriceBandRepository { band.right() } } }),
        allowOverride = true,
    )
}
