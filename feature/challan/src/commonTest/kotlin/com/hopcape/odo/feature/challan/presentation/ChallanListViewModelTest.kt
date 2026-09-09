package com.hopcape.odo.feature.challan.presentation

import com.hopcape.odo.core.domain.car.ActiveCarProvider
import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.car.repository.CarRepository
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.challan.model.ChallanStatus
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.feature.challan.FakeChallanRepository
import com.hopcape.odo.feature.challan.FixedClock
import com.hopcape.odo.feature.challan.challan
import com.hopcape.odo.feature.challan.domain.usecase.MarkChallansPaidUseCase
import com.hopcape.odo.feature.challan.domain.usecase.ObserveChallanOverviewUseCase
import com.hopcape.odo.feature.challan.domain.usecase.RefreshChallansUseCase
import com.hopcape.odo.feature.challan.presentation.list.ChallanListEvent
import com.hopcape.odo.feature.challan.presentation.list.ChallanListViewModel
import com.hopcape.odo.feature.challan.presentation.state.Loadable
import com.hopcape.odo.feature.challan.presentation.state.valueOrNull
import com.hopcape.odo.feature.challan.testTelemetry
import arrow.core.Either
import arrow.core.right
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class ChallanListViewModelTest {

    private val now = Instant.parse("2026-08-22T12:00:00Z")

    private class FakeActiveCar(carId: CarId?) : ActiveCarProvider {
        private val _id = MutableStateFlow(carId)
        override val activeCarId: StateFlow<CarId?> = _id
    }

    private class FakeCarRepository(private val car: Car?) : CarRepository {
        override suspend fun add(car: Car): Either<DomainError, Car> = car.right()
        override suspend fun update(car: Car): Either<DomainError, Car> = car.right()
        override fun observePrimaryCar(): Flow<Car?> = flowOf(car)
        override fun observe(id: CarId): Flow<Car?> = flowOf(car)
        override suspend fun softDelete(id: CarId): Either<DomainError, Unit> = Either.Right(Unit)
    }

    private fun testCar(): Car = Car.reconstitute(
        id = CAR_ID,
        ownerId = OwnerId("owner-1"),
        make = "Maruti",
        model = "Swift",
        variant = "VXI",
        year = 2023,
        fuelType = FuelType.PETROL,
        registrationNumber = "MH12AB1234",
        odometerKm = 54_120,
        purchaseYear = 2023,
        nickname = null,
        isPrimary = true,
        addedOn = null,
    )

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(repo: FakeChallanRepository) = ChallanListViewModel(
        activeCar = FakeActiveCar(CAR_ID),
        cars = FakeCarRepository(testCar()),
        observeOverview = ObserveChallanOverviewUseCase(challans = repo, clock = FixedClock(now)),
        refresh = RefreshChallansUseCase(challans = repo, clock = FixedClock(now)),
        markPaid = MarkChallansPaidUseCase(challans = repo),
        telemetry = testTelemetry(),
        clock = FixedClock(now),
    )

    @Test
    fun twoPendingInOneYear_readAsTheFlatSection() = runTest {
        val repo = FakeChallanRepository(
            challans = listOf(
                challan(id = "A", amountPaise = 1_000_00, issuedOn = LocalDate(2026, 8, 14)),
                challan(id = "B", amountPaise = 500_00, issuedOn = LocalDate(2026, 6, 22)),
            ),
            lastChecked = now - 2.hours,
        )
        val content = viewModel(repo).state.first { it.content is Loadable.Ready }.content.valueOrNull!!

        assertEquals("MH 12 AB 1234", content.regNo)
        assertEquals(1, content.sections.size)
        assertFalse(content.sections.single().compact)
        assertEquals(2, content.sections.single().rows.size)
        assertEquals("Rs. 1,500", content.totalPending?.amount)
        assertTrue(content.totalPending?.segments.orEmpty().isEmpty())
        assertNull(content.older)
        assertNull(content.clean)
        assertTrue(content.offerAlreadyPaid)
    }

    @Test
    fun challansAcrossYears_groupByYear_andCollapseTheOld() = runTest {
        val repo = FakeChallanRepository(
            challans = listOf(
                challan(id = "A", issuedOn = LocalDate(2026, 8, 14), amountPaise = 1_000_00),
                challan(id = "B", issuedOn = LocalDate(2026, 6, 22), amountPaise = 500_00),
                challan(id = "C", issuedOn = LocalDate(2025, 9, 18), amountPaise = 1_000_00),
                challan(id = "D", issuedOn = LocalDate(2023, 2, 4), amountPaise = 700_00),
                challan(id = "E", issuedOn = LocalDate(2024, 12, 8), amountPaise = 500_00),
            ),
            lastChecked = now - 2.hours,
        )
        val content = viewModel(repo).state.first { it.content is Loadable.Ready }.content.valueOrNull!!

        // 2026 and 2025 get sections; 2023 + 2024 collapse into Older.
        assertEquals(listOf(true, true), content.sections.map { it.compact })
        assertEquals(2, content.sections[0].rows.size)
        assertEquals(1, content.sections[1].rows.size)
        val older = assertNotNull(content.older)
        assertEquals(2, older.rows.size)
        assertEquals("Rs. 1,200", older.amount)
        // The hero bar splits the same three ways.
        assertEquals(3, content.totalPending?.segments?.size)
        assertFalse(content.offerAlreadyPaid)
    }

    @Test
    fun aCourtCase_isPinned_andExcludedFromTheTotal() = runTest {
        val repo = FakeChallanRepository(
            challans = listOf(
                challan(id = "A", amountPaise = 1_000_00),
                challan(
                    id = "C",
                    status = ChallanStatus.IN_COURT,
                    amountPaise = 5_000_00,
                    courtName = "Shivajinagar, Pune",
                    nextHearingOn = LocalDate(2026, 9, 4),
                ),
            ),
            lastChecked = now - 2.hours,
        )
        val content = viewModel(repo).state.first { it.content is Loadable.Ready }.content.valueOrNull!!

        assertEquals(1, content.courtCases.size)
        // House date style: no zero padding, like every other date in the app.
        assertEquals("4 Sep 2026", content.courtCases.single().nextHearing)
        assertEquals("Rs. 1,000", content.totalPending?.amount)
        // With a court case above, "I've already paid these" would be ambiguous.
        assertFalse(content.offerAlreadyPaid)
    }

    @Test
    fun nothingPending_isTheCleanState_withItsStats() = runTest {
        val repo = FakeChallanRepository(
            challans = listOf(
                challan(id = "A", status = ChallanStatus.PAID, amountPaise = 2_500_00, issuedOn = LocalDate(2026, 3, 1)),
            ),
            lastChecked = now - 2.hours,
        )
        val content = viewModel(repo).state.first { it.content is Loadable.Ready }.content.valueOrNull!!

        val clean = assertNotNull(content.clean)
        assertNotNull(clean.lastChecked)
        assertNotNull(clean.clearedThisYear)
        assertNull(content.totalPending)
        assertTrue(content.sections.isEmpty())
    }

    @Test
    fun aStaleCheck_refreshesItselfOnOpen() = runTest {
        val repo = FakeChallanRepository(lastChecked = now - 8.days, clock = FixedClock(now))
        val viewModel = viewModel(repo)
        viewModel.state.first { it.content is Loadable.Ready }

        assertEquals(1, repo.refreshCount)
    }

    @Test
    fun aFreshCheck_doesNotRefreshOnOpen() = runTest {
        val repo = FakeChallanRepository(lastChecked = now - 2.hours, clock = FixedClock(now))
        val viewModel = viewModel(repo)
        viewModel.state.first { it.content is Loadable.Ready }

        assertEquals(0, repo.refreshCount)
    }

    @Test
    fun theSourceBeingDown_isSaidBesideTheLastKnownAnswer() = runTest {
        val repo = FakeChallanRepository(
            challans = listOf(challan(id = "A")),
            lastChecked = now - 3.days,
            clock = FixedClock(now),
        )
        repo.sourceDown = true
        val viewModel = viewModel(repo)
        viewModel.state.first { it.content is Loadable.Ready }
        viewModel.onEvent(ChallanListEvent.RefreshTapped)

        val state = viewModel.state.first { it.sourceDown }
        // The failure does not erase what was known.
        assertIs<Loadable.Ready<*>>(state.content)
    }

    @Test
    fun alreadyPaid_marksEveryPendingChallan() = runTest {
        val repo = FakeChallanRepository(
            challans = listOf(challan(id = "A"), challan(id = "B", amountPaise = 500_00)),
            lastChecked = now - 2.hours,
        )
        val viewModel = viewModel(repo)
        viewModel.state.first { it.content is Loadable.Ready }
        viewModel.onEvent(ChallanListEvent.AlreadyPaidTapped)

        val content = viewModel.state.first { it.content.valueOrNull?.clean != null }.content.valueOrNull!!
        assertNotNull(content.clean)
        assertTrue(repo.challansFlow.value.all { it.status == ChallanStatus.PAID })
    }

    private companion object {
        val CAR_ID = CarId("car-1")
    }
}
