package com.hopcape.odo.feature.servicelog.presentation.form

import com.hopcape.odo.core.domain.owner.CurrentOwnerProvider
import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import com.hopcape.odo.feature.servicelog.domain.usecase.AddServiceLogUseCase
import com.hopcape.odo.feature.servicelog.domain.usecase.DeleteServiceLogUseCase
import com.hopcape.odo.feature.servicelog.domain.usecase.GetServiceLogUseCase
import com.hopcape.odo.feature.servicelog.domain.usecase.UpdateServiceLogUseCase
import com.hopcape.odo.feature.servicelog.presentation.FakeServiceLogRepository
import com.hopcape.odo.feature.servicelog.presentation.FixedIdGenerator
import com.hopcape.odo.feature.servicelog.presentation.TEST_CAR
import com.hopcape.odo.feature.servicelog.presentation.TEST_CLOCK
import com.hopcape.odo.feature.servicelog.presentation.TEST_OWNER
import com.hopcape.odo.feature.servicelog.presentation.testEntry
import com.hopcape.odo.feature.servicelog.presentation.testTelemetry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ServiceLogFormViewModelTest {

    private val owner = CurrentOwnerProvider { TEST_OWNER }
    private lateinit var dispatcher: TestDispatcher

    @BeforeTest
    fun setUp() {
        dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun vm(repo: FakeServiceLogRepository, editLogId: String? = null) = ServiceLogFormViewModel(
        addLog = AddServiceLogUseCase(repo, FixedIdGenerator("log-new"), TEST_CLOCK, TimeZone.UTC),
        updateLog = UpdateServiceLogUseCase(repo, TEST_CLOCK, TimeZone.UTC),
        getLog = GetServiceLogUseCase(repo),
        deleteLog = DeleteServiceLogUseCase(repo),
        owner = owner,
        clock = TEST_CLOCK,
        timeZone = TimeZone.UTC,
        telemetry = testTelemetry(),
        carId = TEST_CAR,
        editLogId = editLogId?.let { com.hopcape.odo.core.domain.servicelog.model.ServiceLogId(it) },
    )

    @Test
    fun validAdd_persistsAndEmitsSaved() = runTest(dispatcher) {
        val repo = FakeServiceLogRepository(carBaselineKm = 50_000)
        val vm = vm(repo)
        val effects = mutableListOf<ServiceLogFormEffect>()
        val job = launch { vm.effects.collect { effects += it } }
        advanceUntilIdle() // init: date defaults to today

        vm.onEvent(ServiceLogFormEvent.WorkshopChanged("Sharma Motors"))
        vm.onEvent(ServiceLogFormEvent.OdometerChanged("60000"))
        vm.onEvent(ServiceLogFormEvent.AmountChanged("3200"))
        vm.onEvent(ServiceLogFormEvent.Save)
        advanceUntilIdle()

        assertEquals(1, repo.addCount)
        assertTrue(ServiceLogFormEffect.Saved in effects)
        val saved = repo.entries.value.single()
        assertEquals(60_000, saved.odometer.km)
        assertEquals(320_000L, saved.totalAmount.paise) // ₹3,200 → paise
        job.cancel()
    }

    @Test
    fun categoryToggles_persistOnSave() = runTest(dispatcher) {
        val repo = FakeServiceLogRepository(carBaselineKm = 50_000)
        val vm = vm(repo)
        advanceUntilIdle()

        vm.onEvent(ServiceLogFormEvent.OdometerChanged("60000"))
        vm.onEvent(ServiceLogFormEvent.CategoryToggled(ServiceCategory.OIL_CHANGE))
        vm.onEvent(ServiceLogFormEvent.CategoryToggled(ServiceCategory.BRAKES))
        vm.onEvent(ServiceLogFormEvent.CategoryToggled(ServiceCategory.BRAKES)) // toggle back off
        vm.onEvent(ServiceLogFormEvent.Save)
        advanceUntilIdle()

        assertEquals(setOf(ServiceCategory.OIL_CHANGE), repo.entries.value.single().categories)
    }

    @Test
    fun missingOdometer_setsFieldError_andDoesNotPersist() = runTest(dispatcher) {
        val repo = FakeServiceLogRepository(carBaselineKm = 50_000)
        val vm = vm(repo)
        advanceUntilIdle()

        vm.onEvent(ServiceLogFormEvent.OdometerChanged(""))
        vm.onEvent(ServiceLogFormEvent.Save)
        advanceUntilIdle()

        assertNotNull(vm.state.value.odometer.error)
        assertEquals(0, repo.addCount)
    }

    @Test
    fun backwardsOdometer_setsFieldError() = runTest(dispatcher) {
        val repo = FakeServiceLogRepository(carBaselineKm = 50_000)
        val vm = vm(repo)
        advanceUntilIdle()

        vm.onEvent(ServiceLogFormEvent.OdometerChanged("40000"))
        vm.onEvent(ServiceLogFormEvent.Save)
        advanceUntilIdle()

        assertNotNull(vm.state.value.odometer.error)
        assertEquals(0, repo.addCount)
    }

    @Test
    fun futureDate_setsDateError() = runTest(dispatcher) {
        val repo = FakeServiceLogRepository(carBaselineKm = 50_000)
        val vm = vm(repo)
        advanceUntilIdle()

        vm.onEvent(ServiceLogFormEvent.OdometerChanged("60000"))
        vm.onEvent(ServiceLogFormEvent.DateChanged(LocalDate(2026, 7, 4))) // after TEST_CLOCK today
        vm.onEvent(ServiceLogFormEvent.Save)
        advanceUntilIdle()

        assertNotNull(vm.state.value.date.error)
        assertEquals(0, repo.addCount)
    }

    @Test
    fun edit_prefillsThenUpdates() = runTest(dispatcher) {
        val existing = testEntry("log-1", km = 54_000, paise = 320_000)
        val repo = FakeServiceLogRepository(initial = listOf(existing), carBaselineKm = 40_000)
        val vm = vm(repo, editLogId = "log-1")
        val effects = mutableListOf<ServiceLogFormEffect>()
        val job = launch { vm.effects.collect { effects += it } }
        advanceUntilIdle()

        assertTrue(vm.state.value.isEditing)
        assertEquals("54000", vm.state.value.odometer.value)

        vm.onEvent(ServiceLogFormEvent.OdometerChanged("55000"))
        vm.onEvent(ServiceLogFormEvent.Save)
        advanceUntilIdle()

        assertTrue(ServiceLogFormEffect.Saved in effects)
        assertEquals(55_000, repo.entries.value.single().odometer.km)
        job.cancel()
    }

    @Test
    fun edit_confirmDelete_emitsDeleted() = runTest(dispatcher) {
        val existing = testEntry("log-1", km = 54_000)
        val repo = FakeServiceLogRepository(initial = listOf(existing))
        val vm = vm(repo, editLogId = "log-1")
        val effects = mutableListOf<ServiceLogFormEffect>()
        val job = launch { vm.effects.collect { effects += it } }
        advanceUntilIdle()

        vm.onEvent(ServiceLogFormEvent.ConfirmDelete)
        advanceUntilIdle()

        assertTrue(ServiceLogFormEffect.Deleted in effects)
        assertEquals(1, repo.deleteCount)
        assertTrue(repo.entries.value.isEmpty())
        job.cancel()
    }

    @Test
    fun addMode_defaultsDateToToday() = runTest(dispatcher) {
        val vm = vm(FakeServiceLogRepository(carBaselineKm = 50_000))
        advanceUntilIdle()
        assertEquals(LocalDate(2026, 7, 3), vm.state.value.date.value)
        assertNull(vm.state.value.submitError)
    }
}
