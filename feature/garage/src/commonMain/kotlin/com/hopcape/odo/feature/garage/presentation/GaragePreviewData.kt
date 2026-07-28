package com.hopcape.odo.feature.garage.presentation

import arrow.core.getOrElse
import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.car.model.Vin
import com.hopcape.odo.core.domain.document.model.Document
import com.hopcape.odo.core.domain.document.model.DocumentId
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.servicelog.model.VerificationStatus
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.Distance
import com.hopcape.odo.feature.garage.domain.model.GarageDocument
import com.hopcape.odo.feature.garage.domain.model.ServiceFacet
import com.hopcape.odo.feature.garage.domain.model.ServiceHistoryEntry
import kotlinx.datetime.LocalDate

private fun rupees(paise: Long): Amount = Amount.of(paise).getOrElse { Amount.ZERO }
private fun km(value: Int): Distance = Distance.of(value).getOrElse { error("bad sample km=$value") }

/** The day the sample data is read against — sample validity has to resolve somewhere. */
private val SampleToday = LocalDate(2026, 7, 28)

private val sampleCar: Car = Car.reconstitute(
    id = CarId("sample-car"),
    ownerId = OwnerId("sample-owner"),
    make = "Maruti Suzuki",
    model = "Swift",
    variant = "VXI",
    year = 2020,
    fuelType = FuelType.PETROL,
    registrationNumber = "MH12AB1234",
    odometerKm = 48_500,
    purchaseYear = 2020,
    nickname = null,
    isPrimary = true,
)

private fun document(
    id: String,
    type: DocumentType,
    expiresOn: LocalDate?,
) = Document(
    id = DocumentId(id),
    carId = sampleCar.id,
    type = type,
    title = null,
    issuedOn = null,
    expiresOn = expiresOn,
)

/** Insurance in force, a PUC inside its renewal window, and no RC uploaded at all. */
private val sampleDocuments: List<Document> = listOf(
    document("d1", DocumentType.INSURANCE, LocalDate(2027, 7, 3)),
    document("d2", DocumentType.PUC, LocalDate(2026, 8, 4)),
)

private val sampleHistory: List<ServiceHistoryEntry> = listOf(
    ServiceHistoryEntry(
        id = ServiceLogId("s1"),
        workDone = "Front brake pads",
        servicedOn = LocalDate(2026, 3, 2),
        odometer = km(48_500),
        amount = rupees(480_000),
        verification = VerificationStatus.VERIFIED,
        category = ServiceCategory.BRAKES,
    ),
    ServiceHistoryEntry(
        id = ServiceLogId("s2"),
        workDone = "Full periodic service",
        servicedOn = LocalDate(2025, 12, 18),
        odometer = km(43_200),
        amount = rupees(640_000),
        verification = VerificationStatus.SELF_REPORTED,
        category = ServiceCategory.GENERAL_SERVICE,
    ),
    ServiceHistoryEntry(
        id = ServiceLogId("s3"),
        workDone = "AC re-gas",
        servicedOn = LocalDate(2025, 8, 20),
        odometer = km(38_000),
        amount = rupees(240_000),
        verification = VerificationStatus.VERIFIED,
        category = ServiceCategory.AC,
    ),
    ServiceHistoryEntry(
        id = ServiceLogId("s4"),
        workDone = "New tyres",
        servicedOn = LocalDate(2025, 5, 11),
        odometer = km(35_400),
        amount = rupees(280_000),
        verification = VerificationStatus.VERIFIED,
        category = ServiceCategory.TYRES,
    ),
)

/**
 * Canned home-base state mirroring the mockup — for `@Preview`s and, until the aggregation
 * use case lands, the running route. The document rows are built the way the ViewModel
 * will build them: domain documents resolved against a day.
 */
internal fun sampleGarage(filter: ServiceFacet = ServiceFacet.ALL): GarageUiState = GarageUiState(
    car = sampleCar,
    vin = Vin.of("MA3ERLF1S00123456"),
    documents = GarageDocument.rowsFor(sampleDocuments, SampleToday),
    serviceHistory = sampleHistory,
    filter = filter,
    isLoading = false,
)

/** Sample empty state — no car yet. */
internal fun sampleEmptyGarage(): GarageUiState = GarageUiState(isLoading = false)
