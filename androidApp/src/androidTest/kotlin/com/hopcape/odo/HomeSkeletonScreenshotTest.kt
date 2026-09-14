package com.hopcape.odo

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.cost.fuel.FuelPrice
import com.hopcape.odo.core.domain.cost.fuel.FuelPriceProvider
import com.hopcape.odo.core.domain.showcase.ShowcaseHookId
import com.hopcape.odo.core.domain.showcase.ShowcaseSeenStore
import com.hopcape.odo.feature.dashboard.presentation.home.HomeTestTags
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.dsl.module

/**
 * The Home skeleton, held still long enough to photograph (#435).
 *
 * It is normally on screen for a few frames, which is not long enough to look at — and is most
 * of why it drifted from the header it stands for.
 *
 * **How it is held.** `ObserveHomeUseCase` combines the car's record with the owner and the
 * fuel prices, so a `priceChanges()` that never emits keeps the combine — and therefore Home —
 * in `Loadable.Loading` for as long as the test wants. One narrow port; nothing else about the
 * screen is touched.
 *
 * **Its own class, on purpose.** Koin's overrides are process-scoped, so a stall installed here
 * would still be in front of the port for any later test in the same run. [HomeDashboardScreenshotTest]
 * takes the loaded half from a clean process.
 *
 * The device is seeded with a service and papers because the skeleton stands in for the
 * *scored* dashboard; a bare owner gets the new-user screen, which it does not claim to match.
 */
@RunWith(AndroidJUnit4::class)
class HomeSkeletonScreenshotTest {

    private val rule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val chain: RuleChain = RuleChain
        // The stall goes in before the activity launches. Installed from the test body it
        // would land after Home had already read the prices and left Loading.
        .outerRule(DeviceState { seedAScoredDashboard(); holdHomeLoading() })
        .around(rule)

    /**
     * Captured with [Screenshots.capture] rather than `captureScreen`: the sweep is an
     * infinite animation, so Compose's `waitForIdle` never returns while the skeleton is up.
     */
    @Test
    fun theLoadingSkeleton() {
        rule.waitUntil(SKELETON_TIMEOUT_MILLIS) {
            rule.onAllNodes(hasTestTag(HomeTestTags.SKELETON)).fetchSemanticsNodes().isNotEmpty()
        }

        Screenshots.capture("home-skeleton")
    }

    private companion object {
        const val SKELETON_TIMEOUT_MILLIS = 10_000L
    }
}

/** The other half of the pair — the dashboard the skeleton is standing in for. */
@RunWith(AndroidJUnit4::class)
class HomeDashboardScreenshotTest {

    private val rule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val chain: RuleChain = RuleChain
        .outerRule(DeviceState { seedAScoredDashboard() })
        .around(rule)

    @Test
    fun theLoadedDashboard() {
        rule.openHome()

        rule.captureScreen("home-loaded")
    }
}

/** An owner with a car, a serviced history and valid papers — the scored dashboard. */
internal fun seedAScoredDashboard() {
    resetHome()
    seedHomeOwner()
    seedHomeService()
    seedHomeValidDocuments()
    silenceTheCoachMarks()
}

/**
 * Mark every coach mark seen. They dim the screen behind them, and a picture of Home under a
 * scrim is a picture of the coach mark rather than of the dashboard.
 */
private fun silenceTheCoachMarks() {
    val store = GlobalContext.get().get<ShowcaseSeenStore>()
    runBlocking { ShowcaseHookId.entries.forEach { store.markSeen(it) } }
}

/** Keep Home in its loading state by starving the combine that feeds it. */
internal fun holdHomeLoading() {
    GlobalContext.get().loadModules(
        listOf(
            module {
                single<FuelPriceProvider> {
                    object : FuelPriceProvider {
                        override suspend fun priceFor(city: String?, fuelType: FuelType): FuelPrice? = null
                        override fun priceChanges(): Flow<Unit> = emptyFlow()
                    }
                }
            },
        ),
        allowOverride = true,
    )
}
