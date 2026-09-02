package com.hopcape.odo.core.data.car

import com.hopcape.crashreporting.api.CrashRecorder
import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.api.Logger
import com.hopcape.logging.api.TraceContext
import com.hopcape.odo.core.data.observability.DataTelemetry
import com.hopcape.odo.core.domain.car.lookup.RegisteredVehicle
import com.hopcape.odo.core.domain.car.lookup.VehicleSource
import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.car.model.ModelYear
import com.hopcape.odo.core.domain.car.model.RegistrationNumber
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.performance.api.PerformanceTracer
import com.hopcape.performance.api.Span
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class LocalVehicleRegistryLookupTest {

    @Test
    fun aStoredPlate_answersWithTheOwnersOwnRecord() = runTest {
        val lookup = lookup(stored = swift)

        val vehicle = lookup.lookup(PLATE).getOrNull()

        assertEquals(swift, vehicle)
        // Not a claim about somebody else's car: this tier only ever reads this device.
        assertEquals(VehicleSource.OWN_RECORD, vehicle?.source)
    }

    @Test
    fun anUnstoredPlate_isNotFoundSoTheNextTierIsAsked() = runTest {
        assertIs<DomainError.RegistrationNotFound>(lookup(stored = null).lookup(PLATE).leftOrNull())
    }

    @Test
    fun theCurrentOwnerIsWhatIsAskedAbout() = runTest {
        val source = RecordingCars(stored = swift)
        lookup(cars = source, owner = OwnerId("owner-7")).lookup(PLATE)

        assertEquals(OwnerId("owner-7"), source.askedOwner)
        assertEquals(PLATE, source.askedPlate)
    }

    @Test
    fun aFailedRead_isUnavailableRatherThanNoRecord() = runTest {
        // A database that fell over has not said the plate is unknown. Reporting it as
        // "no record" would send the owner to manual entry over a problem that may fix
        // itself on the next keystroke.
        val lookup = lookup(cars = RecordingCars(throws = IllegalStateException("db closed")))

        assertIs<DomainError.LookupUnavailable>(lookup.lookup(PLATE).leftOrNull())
    }

    private fun lookup(
        cars: RecordingCars = RecordingCars(),
        owner: OwnerId = OwnerId("owner-1"),
    ) = LocalVehicleRegistryLookup(cars = cars, owners = { owner }, telemetry = telemetry)

    private fun lookup(stored: RegisteredVehicle?) = lookup(cars = RecordingCars(stored = stored))

    private val telemetry = DataTelemetry(NoopLogger, NoopTracer, NoopCrash)

    private class RecordingCars(
        private val stored: RegisteredVehicle? = null,
        private val throws: Throwable? = null,
    ) : CarLocalDataSource {
        var askedOwner: OwnerId? = null
            private set
        var askedPlate: RegistrationNumber? = null
            private set

        override suspend fun vehicleByRegistration(
            ownerId: OwnerId,
            registrationNumber: RegistrationNumber,
        ): RegisteredVehicle? {
            throws?.let { throw it }
            askedOwner = ownerId
            askedPlate = registrationNumber
            return stored
        }

        override suspend fun insert(car: Car) = Unit
        override suspend fun update(car: Car) = true
        override suspend fun softDelete(id: CarId) = Unit
        override fun observePrimary(): Flow<Car?> = flowOf(null)
        override fun observeById(id: CarId): Flow<Car?> = flowOf(null)
    }

    private object NoopLogger : Logger {
        override fun log(
            level: LogLevel,
            tag: String,
            event: String,
            traceContext: TraceContext?,
            fields: Map<String, Any?>,
        ) = Unit
        override fun flush() = Unit
    }

    private class NoopSpan(
        override val spanId: String,
        override val traceId: String,
        override val parentSpanId: String?,
        override val name: String,
    ) : Span {
        override fun setAttribute(key: String, value: Any?): Span = this
    }

    private object NoopTracer : PerformanceTracer {
        override fun startSpan(name: String, traceId: String, parentSpanId: String?): Span =
            NoopSpan("span", traceId, parentSpanId, name)
        override fun endSpan(span: Span) = Unit
        override fun flush() = Unit
    }

    private object NoopCrash : CrashRecorder {
        override fun recordNonFatal(throwable: Throwable, customKeys: Map<String, Any?>) = Unit
        override fun leaveBreadcrumb(tag: String, message: String) = Unit
        override fun setCustomKey(key: String, value: Any?) = Unit
        override fun setUserId(userId: String?) = Unit
    }

    private companion object {
        val PLATE = RegistrationNumber.of("MH12AB1234")!!

        val swift = RegisteredVehicle(
            make = "Maruti Suzuki",
            model = "Swift",
            variant = "VXI",
            year = ModelYear.of(2020).getOrNull()!!,
            fuelType = FuelType.PETROL,
            source = VehicleSource.OWN_RECORD,
        )
    }
}
