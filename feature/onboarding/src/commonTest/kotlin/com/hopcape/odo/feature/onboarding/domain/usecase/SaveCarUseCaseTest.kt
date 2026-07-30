package com.hopcape.odo.feature.onboarding.domain.usecase

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.car.repository.CarRepository
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.shared.DomainError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SaveCarUseCaseTest {

    private class FakeCarRepository : CarRepository {
        var addCount = 0
        var updateCount = 0
        var lastAdded: Car? = null
        override suspend fun add(car: Car): Either<DomainError, Car> {
            addCount++
            lastAdded = car
            return car.right()
        }

        override suspend fun update(car: Car): Either<DomainError, Car> {
            updateCount++
            lastAdded = car
            return car.right()
        }

        override fun observePrimaryCar(): Flow<Car?> = flowOf(lastAdded)
    }

    private class FixedIdGenerator(private val id: String) : IdGenerator {
        override fun newId(): String = id
    }

    private val ownerId = OwnerId("owner-1")

    private fun validCommand() = SaveCarCommand(
        make = "Maruti",
        model = "Swift",
        year = 2020,
        fuelType = FuelType.PETROL,
        odometerKm = 45_000,
    )

    @Test
    fun validCommand_persistsAndReturnsCar() = runTest {
        val repo = FakeCarRepository()
        val useCase = SaveCarUseCase(repo, FixedIdGenerator("car-1"))

        val result = useCase(validCommand(), ownerId)

        assertTrue(result.isRight(), "expected Right but was $result")
        assertEquals("car-1", result.getOrNull()?.id?.value)
        assertEquals(1, repo.addCount)
    }

    @Test
    fun invalidCommand_doesNotPersist() = runTest {
        val repo = FakeCarRepository()
        val useCase = SaveCarUseCase(repo, FixedIdGenerator("car-1"))

        val result = useCase(
            SaveCarCommand(make = null, model = null, year = null, fuelType = null, odometerKm = null),
            ownerId,
        )

        assertTrue(result.isLeft())
        assertEquals(0, repo.addCount)
    }

    @Test
    fun withoutAnExistingId_theCarIsInserted() = runTest {
        val repo = FakeCarRepository()

        val result = SaveCarUseCase(repo, FixedIdGenerator("car-1"))(validCommand(), ownerId)

        assertTrue(result.isRight(), "expected Right but was $result")
        assertEquals(1, repo.addCount)
        assertEquals(0, repo.updateCount)
    }

    @Test
    fun withAnExistingId_theSameCarIsUpdated() = runTest {
        // Stepping back to fix a detail must edit the stored car, not add a second one.
        val repo = FakeCarRepository()

        val result = SaveCarUseCase(repo, FixedIdGenerator("unused"))(
            command = validCommand(),
            ownerId = ownerId,
            existing = CarId("car-1"),
        )

        assertTrue(result.isRight(), "expected Right but was $result")
        assertEquals("car-1", result.getOrNull()?.id?.value)
        assertEquals(1, repo.updateCount)
        assertEquals(0, repo.addCount)
    }

    @Test
    fun updatingAnUnknownCar_reportsTheRepositoryFailure() = runTest {
        val repo = object : CarRepository by FakeCarRepository() {
            override suspend fun update(car: Car): Either<DomainError, Car> =
                DomainError.CarNotFound.left()
        }

        val result = SaveCarUseCase(repo, FixedIdGenerator("unused"))(
            command = validCommand(),
            ownerId = ownerId,
            existing = CarId("ghost"),
        )

        assertEquals(listOf(DomainError.CarNotFound), result.leftOrNull()?.toList())
    }
}
