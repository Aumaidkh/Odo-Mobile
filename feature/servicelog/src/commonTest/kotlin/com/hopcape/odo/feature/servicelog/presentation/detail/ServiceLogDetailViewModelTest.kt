package com.hopcape.odo.feature.servicelog.presentation.detail

import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.feature.servicelog.domain.usecase.DeleteServiceLogUseCase
import com.hopcape.odo.feature.servicelog.domain.usecase.GetServiceLogUseCase
import com.hopcape.odo.feature.servicelog.domain.usecase.ReportOverchargeUseCase
import com.hopcape.odo.feature.servicelog.presentation.FakeOverchargeReportRepository
import com.hopcape.odo.feature.servicelog.presentation.FakeServiceLogRepository
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ServiceLogDetailViewModelTest {

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
        id: String,
        reports: FakeOverchargeReportRepository = FakeOverchargeReportRepository(),
    ) = ServiceLogDetailViewModel(
        GetServiceLogUseCase(repo),
        DeleteServiceLogUseCase(repo),
        testResolveFairness(),
        ReportOverchargeUseCase(reports),
        TEST_CITY,
        testTelemetry(),
        ServiceLogId(id),
    )

    @Test
    fun loadsEntry() = runTest(dispatcher) {
        val repo = FakeServiceLogRepository(initial = listOf(testEntry("log-1", km = 54_000)))
        val vm = vm(repo, "log-1")
        advanceUntilIdle()

        val content = vm.state.value.content
        assertIs<ServiceLogDetailUiState.Content.Loaded>(content)
        assertEquals(ServiceLogId("log-1"), content.entry.id)
    }

    @Test
    fun confirmDelete_deletesAndEmitsBack() = runTest(dispatcher) {
        val repo = FakeServiceLogRepository(initial = listOf(testEntry("log-1", km = 54_000)))
        val vm = vm(repo, "log-1")
        val effects = mutableListOf<ServiceLogDetailEffect>()
        val job = launch { vm.effects.collect { effects += it } }
        advanceUntilIdle()

        vm.onEvent(ServiceLogDetailEvent.ConfirmDelete)
        advanceUntilIdle()

        assertEquals(1, repo.deleteCount)
        assertTrue(ServiceLogDetailEffect.Back in effects)
        job.cancel()
    }

    @Test
    fun editClicked_emitsOpenEdit() = runTest(dispatcher) {
        val repo = FakeServiceLogRepository(initial = listOf(testEntry("log-1", km = 54_000)))
        val vm = vm(repo, "log-1")
        val effects = mutableListOf<ServiceLogDetailEffect>()
        val job = launch { vm.effects.collect { effects += it } }
        advanceUntilIdle()

        vm.onEvent(ServiceLogDetailEvent.EditClicked)
        advanceUntilIdle()

        assertTrue(effects.any { it is ServiceLogDetailEffect.OpenEdit && it.id == ServiceLogId("log-1") })
        job.cancel()
    }

    @Test
    fun reportOvercharge_submitsAndMarksReported() = runTest(dispatcher) {
        val repo = FakeServiceLogRepository(initial = listOf(testEntry("log-1", km = 54_000)))
        val reports = FakeOverchargeReportRepository()
        val vm = vm(repo, "log-1", reports)
        advanceUntilIdle()

        vm.onEvent(ServiceLogDetailEvent.ReportOverchargeClicked)
        advanceUntilIdle()

        assertNotNull(reports.submitted)
        assertEquals(ServiceLogId("log-1"), reports.submitted?.logId)
        assertTrue(vm.state.value.reported)
    }
}
