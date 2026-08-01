package com.hopcape.odo.feature.garage.domain.usecase

import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.domain.car.lookup.RegisteredVehicle
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.car.model.ModelYear
import com.hopcape.odo.core.domain.shared.DomainError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AddCarUseCaseTest {

    private fun useCase(cars: FakeCarRepository) =
        AddCarUseCase(cars = cars, idGenerator = FixedIdGenerator("new-car"), owner = ownerProvider())

    private fun command(
        make: String? = "Maruti Suzuki",
        model: String? = "Swift",
        year: Int? = 2020,
        fuelType: FuelType? = FuelType.PETROL,
        odometerKm: Int? = 45_000,
    ) = AddCarCommand(
        make = make,
        model = model,
        year = year,
        fuelType = fuelType,
        odometerKm = odometerKm,
        variant = "VXI",
        registrationNumber = "mh 12 ab 1234",
    )

    @Test
    fun validAnswers_storeTheCar() = runTest {
        val cars = FakeCarRepository()

        val result = useCase(cars)(command())

        assertTrue(result.isRight(), "expected Right but was $result")
        val car = cars.added.single()
        assertEquals("new-car", car.id.value)
        assertEquals(TEST_OWNER, car.ownerId)
        assertEquals(45_000, car.odometer.km)
        // Normalized on the way in.
        assertEquals("MH12AB1234", car.registrationNumber?.value)
    }

    /** One car in the garage means the car just added is the one every screen is about. */
    @Test
    fun theAddedCar_becomesThePrimaryOne() = runTest {
        val cars = FakeCarRepository()

        useCase(cars)(command())

        assertTrue(cars.added.single().isPrimary)
    }

    @Test
    fun everyMissingAnswer_isReportedAtOnce() = runTest {
        val cars = FakeCarRepository()

        val result = useCase(cars)(command(make = null, model = null, odometerKm = null))

        val errors = result.leftOrNull()!!
        assertEquals(3, errors.size, "expected all three failures but was $errors")
        assertTrue(errors.any { it is DomainError.BlankMake })
        assertTrue(errors.any { it is DomainError.BlankModel })
        assertTrue(errors.any { it is DomainError.MissingOdometer })
        assertTrue(cars.added.isEmpty())
    }

    @Test
    fun aFailedWrite_reachesTheCaller() = runTest {
        val result = useCase(FakeCarRepository(failing = true))(command())

        assertIs<DomainError.PersistenceFailure>(result.leftOrNull()?.head)
    }
}

class UpdateCarDetailsUseCaseTest {

    private fun command(
        make: String? = "Hyundai",
        model: String? = "i20",
        year: Int? = 2022,
        fuelType: FuelType? = FuelType.DIESEL,
        nickname: String? = "Chhoti",
    ) = CarDetailsCommand(
        make = make,
        model = model,
        year = year,
        fuelType = fuelType,
        variant = "Asta",
        registrationNumber = "dl 01 cd 5678",
        nickname = nickname,
    )

    @Test
    fun editedDetails_areStored() = runTest {
        val cars = FakeCarRepository(testCar(odometerKm = 45_000))

        val result = UpdateCarDetailsUseCase(cars)(TEST_CAR, command())

        assertTrue(result.isRight(), "expected Right but was $result")
        val saved = cars.updated.single()
        assertEquals("Hyundai", saved.make)
        assertEquals("i20", saved.model)
        assertEquals("Asta", saved.variant)
        assertEquals(2022, saved.year.value)
        assertEquals(FuelType.DIESEL, saved.fuelType)
        assertEquals("DL01CD5678", saved.registrationNumber?.value)
        assertEquals("Chhoti", saved.nickname)
    }

    /** The reading moves through the update sheet only, so an edit must not touch it. */
    @Test
    fun theOdometer_isCarriedOverUntouched() = runTest {
        val cars = FakeCarRepository(testCar(odometerKm = 45_000))

        UpdateCarDetailsUseCase(cars)(TEST_CAR, command())

        assertEquals(45_000, cars.updated.single().odometer.km)
    }

    @Test
    fun theOwnerAndPrimaryFlag_areCarriedOverUntouched() = runTest {
        val cars = FakeCarRepository(testCar(isPrimary = true))

        UpdateCarDetailsUseCase(cars)(TEST_CAR, command())

        val saved = cars.updated.single()
        assertEquals(TEST_OWNER, saved.ownerId)
        assertTrue(saved.isPrimary)
    }

    @Test
    fun everyInvalidAnswer_isReportedAtOnce() = runTest {
        val cars = FakeCarRepository(testCar())

        val result = UpdateCarDetailsUseCase(cars)(TEST_CAR, command(make = "  ", fuelType = null))

        val errors = result.leftOrNull()!!
        assertEquals(2, errors.size, "expected both failures but was $errors")
        assertTrue(cars.updated.isEmpty())
    }

    @Test
    fun noSuchCar_isCarNotFound() = runTest {
        val result = UpdateCarDetailsUseCase(FakeCarRepository(car = null))(CarId("ghost"), command())

        assertIs<DomainError.CarNotFound>(result.leftOrNull()?.head)
    }
}

class RemoveCarUseCaseTest {

    @Test
    fun removingACar_deletesIt() = runTest {
        val cars = FakeCarRepository(testCar())

        val result = RemoveCarUseCase(cars)(TEST_CAR)

        assertTrue(result.isRight(), "expected Right but was $result")
        assertEquals(listOf(TEST_CAR), cars.deleted)
    }

    @Test
    fun aFailedDelete_reachesTheCaller() = runTest {
        val result = RemoveCarUseCase(FakeCarRepository(testCar(), failing = true))(TEST_CAR)

        assertIs<DomainError.PersistenceFailure>(result.leftOrNull())
    }
}

class VehicleCatalogUseCasesTest {

    @Test
    fun theCatalogSnapshot_carriesEveryPickerList() = runTest {
        val snapshot = LoadVehicleCatalogUseCase(FakeVehicleCatalog())()

        assertEquals(listOf("Maruti Suzuki", "Hyundai", "Tata"), snapshot.makes)
        assertEquals(listOf("Maruti Suzuki", "Hyundai"), snapshot.popularMakes)
        assertEquals(listOf(2026, 2025, 2024), snapshot.years)
        assertEquals(FuelType.entries, snapshot.fuelTypes)
    }

    @Test
    fun modelsAreFetchedForTheChosenMake() = runTest {
        val catalog = FakeVehicleCatalog()

        val models = LoadCarModelsUseCase(catalog)("Maruti Suzuki")

        assertEquals(listOf("Swift", "Swift VXI"), models.map { it.displayName })
        assertEquals(listOf("Maruti Suzuki"), catalog.modelLookups)
    }

    @Test
    fun aBlankMake_asksTheCatalogNothing() = runTest {
        val catalog = FakeVehicleCatalog()

        assertTrue(LoadCarModelsUseCase(catalog)("   ").isEmpty())
        assertTrue(LoadCarModelsUseCase(catalog)(null).isEmpty())
        assertTrue(catalog.modelLookups.isEmpty())
    }
}

class LookupPlateUseCaseTest {

    private val found = RegisteredVehicle(
        make = "Hyundai",
        model = "i20",
        variant = "Asta",
        year = ModelYear.of(2022).getOrNull()!!,
        fuelType = FuelType.PETROL,
    )

    @Test
    fun theTypedPlate_reachesTheRegistryNormalized() = runTest {
        val registry = FakeVehicleRegistryLookup(found.right())

        val result = LookupPlateUseCase(registry)("mh 12 ab 1234")

        assertEquals(found, result.getOrNull())
        assertEquals(listOf("MH12AB1234"), registry.plates)
    }

    @Test
    fun aPlateThatIsNotOne_neverReachesTheRegistry() = runTest {
        val registry = FakeVehicleRegistryLookup(found.right())

        val result = LookupPlateUseCase(registry)("   ")

        assertIs<DomainError.BlankRegistrationNumber>(result.leftOrNull())
        assertTrue(registry.plates.isEmpty(), "a plate with nothing in it is a wasted round trip")
    }

    /** There is no registry in the MVP, so this is the answer the form actually gets. */
    @Test
    fun anUnavailableRegistry_isReportedAsIs() = runTest {
        val registry = FakeVehicleRegistryLookup(DomainError.LookupUnavailable.left())

        val result = LookupPlateUseCase(registry)("MH12AB1234")

        assertIs<DomainError.LookupUnavailable>(result.leftOrNull())
    }
}
