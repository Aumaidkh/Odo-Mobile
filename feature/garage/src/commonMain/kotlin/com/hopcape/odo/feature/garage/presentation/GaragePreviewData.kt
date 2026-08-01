package com.hopcape.odo.feature.garage.presentation

import arrow.core.getOrElse
import com.hopcape.odo.core.domain.car.catalog.CarModel as CatalogModel
import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.document.model.Document
import com.hopcape.odo.core.domain.document.model.DocumentId
import com.hopcape.odo.core.domain.document.model.DocumentSource
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.servicelog.model.VerificationStatus
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.Distance
import com.hopcape.odo.core.domain.shared.WorkshopName
import com.hopcape.odo.feature.garage.domain.model.GarageDocument
import com.hopcape.odo.feature.garage.domain.model.ServiceFacet
import com.hopcape.odo.feature.garage.domain.model.ServiceHistoryEntry
import com.hopcape.odo.feature.garage.presentation.state.FormField
import com.hopcape.odo.feature.garage.presentation.state.Loadable
import com.hopcape.odo.feature.garage.resources.Res
import com.hopcape.odo.feature.garage.resources.gr_error_load_failed
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
    addedOn = LocalDate(2026, 1, 5),
)

private fun document(
    id: String,
    type: DocumentType,
    expiresOn: LocalDate?,
) = Document.reconstitute(
    id = DocumentId(id),
    ownerId = sampleCar.ownerId,
    carId = sampleCar.id,
    type = type,
    storagePath = "sample/$id.pdf",
    source = DocumentSource.UPLOADED,
    title = null,
    issuedOn = null,
    expiresOn = expiresOn,
    addedOn = LocalDate(2026, 6, 1),
)

/** Insurance in force, a PUC inside its renewal window, and no RC uploaded at all. */
private val sampleDocuments: List<Document> = listOf(
    document("d1", DocumentType.INSURANCE, LocalDate(2027, 7, 3)),
    document("d2", DocumentType.PUC, LocalDate(2026, 8, 4)),
)

private fun workshop(name: String): WorkshopName? = WorkshopName.of(name).getOrElse { null }

private val sampleHistory: List<ServiceHistoryEntry> = listOf(
    ServiceHistoryEntry(
        id = ServiceLogId("s1"),
        servicedOn = LocalDate(2026, 3, 2),
        odometer = km(48_500),
        amount = rupees(480_000),
        verification = VerificationStatus.VERIFIED,
        categories = setOf(ServiceCategory.BRAKES),
        workshopName = workshop("Sharma Motors"),
    ),
    ServiceHistoryEntry(
        id = ServiceLogId("s2"),
        servicedOn = LocalDate(2025, 12, 18),
        odometer = km(43_200),
        amount = rupees(640_000),
        verification = VerificationStatus.SELF_REPORTED,
        categories = setOf(ServiceCategory.GENERAL_SERVICE, ServiceCategory.OIL_CHANGE),
        workshopName = workshop("Maruti Service Centre"),
    ),
    ServiceHistoryEntry(
        id = ServiceLogId("s3"),
        servicedOn = LocalDate(2025, 8, 20),
        odometer = km(38_000),
        amount = rupees(240_000),
        verification = VerificationStatus.VERIFIED,
        categories = setOf(ServiceCategory.AC),
        workshopName = null,
    ),
    ServiceHistoryEntry(
        id = ServiceLogId("s4"),
        servicedOn = LocalDate(2025, 5, 11),
        odometer = km(35_400),
        amount = rupees(280_000),
        verification = VerificationStatus.VERIFIED,
        categories = setOf(ServiceCategory.TYRES),
        workshopName = workshop("MRF Tyre Point"),
    ),
)

/**
 * Canned home-base state mirroring the mockup — for `@Preview`s. The document rows are
 * built the way the ViewModel builds them: domain documents resolved against a day.
 */
internal fun sampleGarage(filter: ServiceFacet = ServiceFacet.ALL): GarageUiState = GarageUiState(
    content = Loadable.Ready(
        GarageContent(
            car = sampleCar,
            documents = GarageDocument.rowsFor(sampleDocuments, SampleToday),
            history = sampleHistory,
        ),
    ),
    filter = filter,
)

/** Sample empty state — read, but no car set up yet. */
internal fun sampleEmptyGarage(): GarageUiState = GarageUiState(
    content = Loadable.Ready(GarageContent(car = null, documents = emptyList(), history = emptyList())),
)

/** Sample failed read — the local DB could not be opened. */
internal fun sampleFailedGarage(): GarageUiState =
    GarageUiState(content = Loadable.Failed(UiText(Res.string.gr_error_load_failed)))

// --- Car forms ------------------------------------------------------------------

private val sampleOptions = CarFormOptions(
    makes = listOf("Maruti Suzuki", "Hyundai", "Tata", "Mahindra", "Honda"),
    popularMakes = listOf("Maruti Suzuki", "Hyundai", "Tata"),
    years = (2005..2026).toList().reversed(),
    fuelTypes = FuelType.entries,
    models = listOf(CatalogModel("Swift"), CatalogModel("Swift", "VXI"), CatalogModel("Baleno", "Zeta")),
)

private val sampleFields = CarFormFields(
    make = FormField("Maruti Suzuki"),
    model = FormField(CatalogModel("Swift", "VXI")),
    year = FormField(2020),
    fuel = FormField(FuelType.PETROL),
    registration = FormField("MH12AB1234"),
)

/** A part-filled add form, the shape it takes before a plate has been looked up. */
internal fun sampleAddCar(): AddCarUiState = AddCarUiState(
    fields = sampleFields,
    options = sampleOptions,
    odometer = FormField(22_300L),
)

/** The edit form, filled from a stored car. */
internal fun sampleEditCar(): EditCarUiState = EditCarUiState(
    form = Loadable.Ready(sampleFields.copy(nickname = FormField("Chhoti"))),
    options = sampleOptions,
)
