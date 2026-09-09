package com.hopcape.odo.feature.garage.presentation.sheets.pdf

import arrow.core.getOrElse
import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.cost.model.CostBreakdown
import com.hopcape.odo.core.domain.cost.model.CostWindow
import com.hopcape.odo.core.domain.cost.model.RunningCost
import com.hopcape.odo.core.domain.cost.model.SpendCategory
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.document.model.DocumentValidity
import com.hopcape.odo.core.domain.health.model.HealthFactor
import com.hopcape.odo.core.domain.health.model.HealthFactorKind
import com.hopcape.odo.core.domain.owner.model.OwnerProfile
import com.hopcape.odo.core.domain.record.analysis.ServiceRecordBuilder
import com.hopcape.odo.core.domain.servicelog.model.LogSource
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.Distance
import com.hopcape.odo.feature.garage.domain.usecase.CarDetails
import com.hopcape.odo.feature.garage.domain.usecase.CarDetailsDocument
import com.hopcape.odo.feature.garage.domain.usecase.TEST_OWNER
import com.hopcape.odo.feature.garage.domain.usecase.testCar
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The printed vehicle details, asserted as a string — the same discipline as the service
 * log's document tests: no renderer, just everything that decides what the owner is about
 * to send. Each figure is the car's own, nothing the app never asked about is claimed, and
 * markup in an owner-written field cannot rewrite the document.
 */
class CarDetailsHtmlTest {

    private val today = LocalDate(2026, 8, 12)

    /** Plain English stand-ins for the resource lookups. */
    private val labels = CarDetailsLabels(
        headerPrefix = "Car details",
        issued = { date -> "Issued $date" },
        eyebrow = "Vehicle details",
        statOdometer = "Odometer",
        statAge = "Age",
        ageUnit = { years -> if (years == 1) "year" else "years" },
        statHealth = "Health score",
        statHealthUnit = "/100",
        statCost = "Running cost",
        statCostUnit = "/km",
        identification = "Identification",
        registration = "Registration",
        makeModel = "Make & model",
        modelYear = "Model year",
        fuel = "Fuel",
        fuelName = { fuel -> fuel.name.lowercase().replaceFirstChar(Char::uppercase) },
        ownership = "Ownership",
        owner = "Owner",
        ownedSince = "Owned since",
        cityOfUse = "City of use",
        odometerTrail = "Odometer trail",
        trailConsistent = "Consistent · no rollback",
        trailInconsistent = "A reading falls below an earlier one",
        documents = "Documents on file",
        columnDocument = "Document",
        columnDetails = "Details",
        columnValidTill = "Valid till",
        columnStatus = "Status",
        documentName = { type -> type.name.lowercase().replaceFirstChar(Char::uppercase) },
        statusValid = "Valid",
        statusExpiring = "Expiring soon",
        statusExpired = "Expired",
        statusOnFile = "On file",
        costs = "Running costs · last 12 months",
        columnCategory = "Category",
        columnAmount = "Amount",
        columnPerKm = "Per km",
        columnShare = "Share",
        costCategory = { category -> category.name.lowercase().replaceFirstChar(Char::uppercase) },
        costFuelEstimated = "estimated",
        costTotal = { km -> "Total · $km driven" },
        health = "Health score",
        healthPoints = { earned, max -> "$earned / $max" },
        factorName = { kind -> kind.name },
        serviceSummary = "Service summary",
        entries = "Entries on record",
        entriesValue = { total, verified, self -> "$total · $verified verified, $self self-reported" },
        lastService = "Last service",
        fullHistory = "Full history",
        fullHistoryValue = "See the service record export",
        howToRead = "How to read this document",
        howToReadRecorded = "Recorded",
        howToReadRecordedBody = "Entered by the owner or read from a document.",
        howToReadEstimated = "Estimated",
        howToReadEstimatedBody = "Fuel spend is an approximation.",
        howToReadScore = "Score",
        howToReadScoreBody = "Odo's own measure.",
        disclaimer = "Not an inspection report.",
        footer = "Details as recorded in the owner's Odo account.",
        documentTitle = { car -> "$car car details" },
    )

    private val fonts = CarDetailsHtml.Fonts(regularBase64 = "AAAA", boldBase64 = "BBBB")

    private fun rupees(paise: Long): Amount = Amount.of(paise).getOrElse { Amount.ZERO }

    private fun km(value: Int): Distance = Distance.of(value).getOrElse { error("bad km") }

    private fun entry(id: String, kmReading: Int, paise: Long, date: LocalDate): ServiceLogEntry =
        ServiceLogEntry.reconstitute(
            id = ServiceLogId(id),
            carId = testCar().id,
            ownerId = TEST_OWNER,
            serviceDate = date,
            odometerKm = kmReading,
            totalAmountPaise = paise,
            workshopName = "Sharma Motors",
            notes = null,
            source = LogSource.MANUAL,
            billId = null,
        )

    private fun details(
        car: Car? = testCar(),
        entries: List<ServiceLogEntry> = emptyList(),
        factors: List<HealthFactor> = emptyList(),
        runningCost: RunningCost? = null,
        documents: List<CarDetailsDocument> = emptyList(),
        city: String? = "Pune",
    ): CarDetails = CarDetails(
        record = ServiceRecordBuilder.build(
            car = car,
            owner = OwnerProfile.reconstitute(id = TEST_OWNER, name = "Rahul Deshmukh", onboardingCompletedAt = null),
            entries = entries,
            documents = emptyList(),
            scores = emptyList(),
            today = today,
            zone = TimeZone.UTC,
        ).let { record ->
            // The score arrives via snapshots in production; the tests state it directly
            // beside the factors it must agree with.
            if (factors.isEmpty()) record else record.copy(healthScore = factors.sumOf { it.earned })
        },
        factors = factors,
        runningCost = runningCost,
        documents = documents,
        city = city,
    )

    private fun html(details: CarDetails): String = CarDetailsHtml.build(details, labels, fonts)

    private fun assertPrinted(page: String, expected: String, message: String? = null) =
        assertTrue(page.contains(expected), message ?: "the document does not contain: $expected")

    /* ------------------------- masthead and figures ------------------------- */

    @Test
    fun `the masthead names the car and the header repeats`() {
        val page = html(details())

        assertPrinted(page, "Vehicle details")
        assertPrinted(page, "Maruti Suzuki Swift VXI")
        assertPrinted(page, "MH 12 AB 1234", "the plate is grouped the way a plate is read")
        assertPrinted(page, "Issued 12 Aug 2026")
        assertPrinted(page, "<thead>", "a thead is what both print engines repeat per page")
        assertPrinted(page, "<tfoot>")
    }

    @Test
    fun `the four figures are the car's own`() {
        val page = html(
            details(
                factors = listOf(
                    HealthFactor.of(HealthFactorKind.MAINTENANCE, 28),
                    HealthFactor.of(HealthFactorKind.DOCUMENTATION, 24),
                    HealthFactor.of(HealthFactorKind.COST_EFFICIENCY, 14),
                    HealthFactor.of(HealthFactorKind.HISTORY, 8),
                ),
                runningCost = runningCost(perKmPaise = 460),
            ),
        )

        assertPrinted(page, "45,000", "the odometer the garage holds")
        assertPrinted(page, ">6<span class=\"stat-unit\">years</span>", "2026 minus a 2020 model year")
        assertPrinted(page, ">74<span class=\"stat-unit\">/100</span>", "the four factors summed")
        assertPrinted(page, "Rs. 4.6<span class=\"stat-unit\">/km</span>", "the ₹/km rate to its natural precision")
    }

    @Test
    fun `a car nobody has scored prints a dash not a zero`() {
        val page = html(details())

        assertFalse(page.contains("/100"), "the unit belongs to a figure that isn't there")
        assertPrinted(page, "—")
    }

    @Test
    fun `no rate is printed when the calculator refused one`() {
        val page = html(details(runningCost = runningCost(perKmPaise = null)))

        assertFalse(page.contains("/km</span>"), "a rate the math refused must not be invented")
    }

    /* ------------------------- identification and ownership ------------------------- */

    @Test
    fun `identification prints what the app collected and nothing it never asked`() {
        val page = html(details())

        assertPrinted(page, "Registration")
        assertPrinted(page, "Make &amp; model")
        assertPrinted(page, ">2020<")
        assertPrinted(page, "Petrol")
        assertFalse(page.contains("VIN"), "Odo never asked for a VIN")
        assertFalse(page.lowercase().contains("engine number"), "or an engine number")
        assertFalse(page.lowercase().contains("transmission"), "or a transmission")
        assertFalse(page.lowercase().contains("accident"), "and must not answer for one")
    }

    @Test
    fun `ownership names the owner the city and the odometer trail`() {
        val page = html(
            details(entries = listOf(entry("a", 31_400, 510_000, LocalDate(2025, 2, 11)))),
        )

        assertPrinted(page, "Rahul Deshmukh")
        assertPrinted(page, "Pune")
        assertPrinted(page, "Consistent · no rollback")
    }

    @Test
    fun `a car with no services makes no claim about its odometer trail`() {
        val page = html(details())

        assertFalse(
            page.contains("Consistent · no rollback"),
            "there are no entries to be consistent across",
        )
    }

    /* ------------------------- the papers ------------------------- */

    @Test
    fun `each paper prints its label its date and its standing`() {
        val page = html(
            details(
                documents = listOf(
                    CarDetailsDocument(
                        type = DocumentType.INSURANCE,
                        title = "SafeDrive comprehensive",
                        validity = DocumentValidity.Valid(until = LocalDate(2027, 7, 3), daysLeft = 325),
                    ),
                    CarDetailsDocument(
                        type = DocumentType.PUC,
                        title = null,
                        validity = DocumentValidity.Expired(since = LocalDate(2026, 1, 1), daysAgo = 223),
                    ),
                ),
            ),
        )

        assertPrinted(page, "SafeDrive comprehensive", "the owner's own label for the paper")
        assertPrinted(page, "3 Jul 2027")
        assertPrinted(page, ">Valid<")
        assertPrinted(page, "class=\"c-state bad\">Expired<", "an expired paper must not read as cover")
    }

    @Test
    fun `an empty vault drops the documents section rather than printing it empty`() {
        val page = html(details())

        assertFalse(page.contains("Documents on file"), "a heading over nothing is noise")
    }

    /* ------------------------- running costs ------------------------- */

    private fun runningCost(perKmPaise: Long?): RunningCost = RunningCost(
        window = CostWindow.endingOn(today, months = 12),
        kmDriven = km(22_200),
        maintenanceSpend = rupees(3_600_000),
        fuelSpend = rupees(6_600_000),
        perKm = perKmPaise?.let(::rupees),
        categories = listOf(
            CostBreakdown(SpendCategory.FUEL, rupees(6_600_000), perKmPaise?.let { rupees(297) }),
            CostBreakdown(SpendCategory.SERVICE, rupees(1_900_000), perKmPaise?.let { rupees(86) }),
            CostBreakdown(SpendCategory.REPAIRS, rupees(1_700_000), perKmPaise?.let { rupees(77) }),
        ),
        shortfall = null,
    )

    @Test
    fun `the cost table prints each bucket with its share and fuel is marked estimated`() {
        val page = html(details(runningCost = runningCost(perKmPaise = 460)))

        assertPrinted(page, "Running costs · last 12 months")
        assertPrinted(page, "Fuel<span class=\"qualifier\"> · estimated</span>", "arithmetic must not read as receipts")
        assertPrinted(page, "Rs. 66,000")
        assertPrinted(page, "Rs. 2.97")
        assertPrinted(page, ">65%</td>", "fuel's share of the total")
        assertPrinted(page, "Total · 22,200 km driven")
        assertPrinted(page, "Rs. 1,02,000", "the total, in Indian grouping")
    }

    @Test
    fun `a car with nothing spent prints no cost table`() {
        val page = html(details(runningCost = null))

        assertFalse(page.contains("Running costs"), "a table of zeros claims knowledge Odo does not have")
    }

    /* ------------------------- the score's breakdown ------------------------- */

    @Test
    fun `the factor bars agree with the headline score`() {
        val page = html(
            details(
                factors = listOf(
                    HealthFactor.of(HealthFactorKind.MAINTENANCE, 28),
                    HealthFactor.of(HealthFactorKind.DOCUMENTATION, 24),
                    HealthFactor.of(HealthFactorKind.COST_EFFICIENCY, 14),
                    HealthFactor.of(HealthFactorKind.HISTORY, 8),
                ),
            ),
        )

        assertPrinted(page, "74 / 100")
        assertPrinted(page, "MAINTENANCE")
        assertPrinted(page, "28 / 35")
        assertPrinted(page, "width:80%", "28 of 35 fills four fifths of the bar")
        assertPrinted(page, "8 / 15")
    }

    @Test
    fun `a car nobody has scored prints no breakdown`() {
        val page = html(details())

        assertFalse(page.contains("class=\"factors\""), "no factors means no bars")
    }

    /* ------------------------- service summary ------------------------- */

    @Test
    fun `the summary counts the services and dates the newest`() {
        val page = html(
            details(
                entries = listOf(
                    entry("a", 31_400, 510_000, LocalDate(2025, 2, 11)),
                    entry("b", 54_000, 320_000, LocalDate(2026, 7, 12)),
                ),
            ),
        )

        assertPrinted(page, "2 · 0 verified, 2 self-reported")
        assertPrinted(page, "12 Jul 2026 · 54,000 km", "the newest service, not the oldest")
        assertPrinted(page, "See the service record export")
    }

    @Test
    fun `a car with no services prints no summary`() {
        val page = html(details())

        assertFalse(page.contains("Entries on record"), "there is nothing to summarise")
    }

    /* ------------------------- safety and shape ------------------------- */

    @Test
    fun `an owner-written field carrying markup cannot rewrite the document`() {
        val page = html(details(car = testCar(nickname = "<script>alert(1)</script>")))

        assertFalse(page.contains("<script>alert"), "the name must arrive as text, not as markup")
        assertPrinted(page, "&lt;script&gt;alert(1)&lt;/script&gt;")
    }

    @Test
    fun `the document is self-contained A4 and set in the brand face`() {
        val page = html(details())

        assertPrinted(page, "@font-face")
        assertPrinted(page, "data:font/ttf;base64,AAAA", "the regular weight is embedded")
        assertPrinted(page, "data:font/ttf;base64,BBBB", "the bold weight is embedded")
        assertFalse(page.contains("http://"), "nothing is fetched")
        assertFalse(page.contains("https://"), "nothing is fetched")
        assertFalse(page.contains("<script"), "the document is shared offline and runs nothing")
        assertPrinted(page, "@page { size: A4;")
    }
}
