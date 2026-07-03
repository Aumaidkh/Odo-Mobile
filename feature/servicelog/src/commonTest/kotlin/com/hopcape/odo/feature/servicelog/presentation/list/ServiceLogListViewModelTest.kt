package com.hopcape.odo.feature.servicelog.presentation.list

import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.feature.servicelog.domain.usecase.ObserveServiceLogsUseCase
import com.hopcape.odo.feature.servicelog.presentation.FakeServiceLogRepository
import com.hopcape.odo.feature.servicelog.presentation.TEST_CAR
import com.hopcape.odo.feature.servicelog.presentation.TEST_CITY
import com.hopcape.odo.feature.servicelog.presentation.testEntry
import com.hopcape.odo.feature.servicelog.presentation.testResolveFairness
import com.hopcape.odo.feature.servicelog.presentation.testTelemetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ServiceLogListViewModelTest {

    private lateinit var dispatcher: TestDispatcher

    @BeforeTest
    fun setUp() {
        dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun vm(
        repo: FakeServiceLogRepository,
        benchmarks: Map<ServiceCategory, Pair<Long, Int>> = emptyMap(),
    ) = ServiceLogListViewModel(
        ObserveServiceLogsUseCase(repo),
        testResolveFairness(benchmarks),
        TEST_CITY,
        testTelemetry(),
        TEST_CAR,
    )

    private fun ledgerOf(vm: ServiceLogListViewModel): ServiceLogListUiState.Content.Ledger =
        vm.state.value.content as ServiceLogListUiState.Content.Ledger

    @Test
    fun derivesSummaryFromEntries() = runTest(dispatcher) {
        val repo = FakeServiceLogRepository(
            initial = listOf(
                testEntry("1", km = 40_000, paise = 200_000, verified = true),
                testEntry("2", km = 54_000, paise = 320_000, verified = false),
            ),
        )
        val vm = vm(repo)
        advanceUntilIdle()

        val content = vm.state.value.content
        assertIs<ServiceLogListUiState.Content.Ledger>(content)
        assertEquals(2, content.summary.serviceCount)
        assertEquals(1, content.summary.verifiedCount)
        assertEquals(1, content.selfReportedCount)
        assertEquals(520_000L, content.summary.totalSpent.paise)
        assertEquals(2, content.visible.size)
    }

    @Test
    fun verifiedFilter_narrowsVisible() = runTest(dispatcher) {
        val repo = FakeServiceLogRepository(
            initial = listOf(
                testEntry("1", km = 40_000, verified = true),
                testEntry("2", km = 54_000, verified = false),
            ),
        )
        val vm = vm(repo)
        advanceUntilIdle()

        vm.onEvent(ServiceLogListEvent.FilterChanged(ServiceLogFilter.VERIFIED))
        assertEquals(ServiceLogFilter.VERIFIED, vm.state.value.filter)
        assertEquals(1, ledgerOf(vm).visible.size)
    }

    @Test
    fun flaggedFilter_showsOnlyOvercharges() = runTest(dispatcher) {
        val repo = FakeServiceLogRepository(
            initial = listOf(
                // Verified brakes at 3,300 vs city avg 2,400 → over → flagged.
                testEntry("1", km = 40_000, paise = 330_000, verified = true, categories = setOf(ServiceCategory.BRAKES)),
                // Verified oil change at 1,900 vs avg 1,900 → fair.
                testEntry("2", km = 54_000, paise = 190_000, verified = true, categories = setOf(ServiceCategory.OIL_CHANGE)),
            ),
        )
        val benchmarks = mapOf(
            ServiceCategory.BRAKES to (240_000L to 30),
            ServiceCategory.OIL_CHANGE to (190_000L to 30),
        )
        val vm = vm(repo, benchmarks)
        advanceUntilIdle()

        assertEquals(1, ledgerOf(vm).flaggedCount)
        assertEquals(90_000L, ledgerOf(vm).savings.overchargeTotal.paise)

        vm.onEvent(ServiceLogListEvent.FilterChanged(ServiceLogFilter.FLAGGED))
        val flagged = ledgerOf(vm).visible
        assertEquals(1, flagged.size)
        assertEquals(ServiceLogId("1"), flagged.single().entry.id)
    }

    @Test
    fun emptyRepo_isEmptyState() = runTest(dispatcher) {
        val vm = vm(FakeServiceLogRepository())
        advanceUntilIdle()
        assertEquals(ServiceLogListUiState.Content.Empty, vm.state.value.content)
    }

    @Test
    fun logClicked_emitsOpenDetail() = runTest(dispatcher) {
        val repo = FakeServiceLogRepository(initial = listOf(testEntry("1", km = 40_000)))
        val vm = vm(repo)
        val effects = mutableListOf<ServiceLogListEffect>()
        val job = launch { vm.effects.collect { effects += it } }
        advanceUntilIdle()

        vm.onEvent(ServiceLogListEvent.LogClicked(ServiceLogId("1")))
        advanceUntilIdle()

        assertTrue(effects.any { it is ServiceLogListEffect.OpenDetail && it.id == ServiceLogId("1") })
        job.cancel()
    }
}
