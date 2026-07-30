package com.hopcape.odo.feature.onboarding.domain.usecase

import arrow.core.Either
import arrow.core.right
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.domain.car.model.Car
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

class AddCarUseCaseTest {

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

    private fun validCommand() = AddCarCommand(
        make = "Maruti",
        model = "Swift",
        year = 2020,
        fuelType = FuelType.PETROL,
        odometerKm = 45_000,
    )

    @Test
    fun validCommand_persistsAndReturnsCar() = runTest {
        val repo = FakeCarRepository()
        val useCase = AddCarUseCase(repo, FixedIdGenerator("car-1"))

        val result = useCase(validCommand(), ownerId)

        assertTrue(result.isRight(), "expected Right but was $result")
        assertEquals("car-1", result.getOrNull()?.id?.value)
        assertEquals(1, repo.addCount)
    }

    @Test
    fun invalidCommand_doesNotPersist() = runTest {
        val repo = FakeCarRepository()
        val useCase = AddCarUseCase(repo, FixedIdGenerator("car-1"))

        val result = useCase(
            AddCarCommand(make = null, model = null, year = null, fuelType = null, odometerKm = null),
            ownerId,
        )

        assertTrue(result.isLeft())
        assertEquals(0, repo.addCount)
    }
}
