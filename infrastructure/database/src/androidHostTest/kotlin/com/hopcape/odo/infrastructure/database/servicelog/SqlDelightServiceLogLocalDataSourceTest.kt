package com.hopcape.odo.infrastructure.database.servicelog

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import arrow.core.getOrElse
import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import com.hopcape.odo.infrastructure.database.sync.SyncStatus
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.fairness.model.FairnessEstimate
import com.hopcape.odo.core.domain.fairness.model.FairnessOutcome
import com.hopcape.odo.core.domain.fairness.model.FairnessQuery
import com.hopcape.odo.core.domain.fairness.model.FairnessQueryItem
import com.hopcape.odo.core.domain.fairness.model.FairnessRange
import com.hopcape.odo.core.domain.fairness.model.FairnessReport
import com.hopcape.odo.core.domain.fairness.model.FairnessSnapshot
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.servicelog.model.LogSource
import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.shared.Amount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * SQL behaviour for [SqlDelightServiceLogLocalDataSource] — categories, the odometer
 * timeline's math, and live-collection. Error mapping and sync scheduling live in
 * [ServiceLogRepositoryImplTest] instead, against a fake port.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SqlDelightServiceLogLocalDataSourceTest {

    private val carId = CarId("car-1")
    private val ownerId = OwnerId("owner-1")

    private class FixedClock(private val instant: Instant) : Clock {
        override fun now(): Instant = instant
    }

    private fun newDb(): OdoDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        OdoDatabase.Schema.create(driver)
        return OdoDatabase(driver)
    }

    private fun local(db: OdoDatabase, now: String = "2026-07-30T10:00:00Z") =
        SqlDelightServiceLogLocalDataSource(
            database = db,
            clock = FixedClock(Instant.parse(now)),
            dispatcher = Dispatchers.Unconfined,
        )

    /** A car row is the odometer timeline's baseline, so most tests need one. */
    private fun OdoDatabase.seedCar(
        odometerKm: Long = 45_000,
        createdAt: String = "2026-01-01T09:00:00Z",
        odometerUpdatedAt: String? = createdAt,
    ) {
        carQueries.insertCar(
            id = carId.value,
            owner_id = ownerId.value,
            make = "Maruti Suzuki",
            model = "Swift",
            variant = null,
            year = 2020,
            fuel_type = "PETROL",
            registration_number = null,
            current_odometer_km = odometerKm,
            purchase_year = null,
            nickname = null,
            is_primary = 1,
            odometer_updated_at = odometerUpdatedAt,
            created_at = createdAt,
            updated_at = createdAt,
            deleted_at = null,
            remote_version = null,
            sync_status = SyncStatus.PENDING.name,
        )
    }

    private fun amt(paise: Long) = Amount.of(paise).getOrElse { Amount.ZERO }

    private fun entry(
        id: String = "log-1",
        day: Int = 15,
        odometerKm: Int = 50_000,
        totalPaise: Long = 330_000,
        categories: Set<ServiceCategory> = setOf(ServiceCategory.BRAKES),
        fairness: FairnessSnapshot? = null,
        billPhotoRef: String? = null,
        source: LogSource = LogSource.MANUAL,
        workshopName: String? = "Sharma Motors",
    ) = ServiceLogEntry.reconstitute(
        id = ServiceLogId(id),
        carId = carId,
        ownerId = ownerId,
        serviceDate = LocalDate(2026, 6, day),
        odometerKm = odometerKm,
        totalAmountPaise = totalPaise,
        workshopName = workshopName,
        notes = "front pads",
        source = source,
        billId = null,
        categories = categories,
        billPhotoRef = billPhotoRef,
        fairness = fairness,
    )

    private fun snapshot(paidPaise: Long, averagePaise: Long, sampleSize: Int = 30) = FairnessSnapshot(
        report = FairnessReport.of(
            query = FairnessQuery(
                city = "Pune",
                items = listOf(FairnessQueryItem("Front pads", ServiceCategory.BRAKES, amt(paidPaise))),
            ),
            estimates = mapOf(
                ServiceCategory.BRAKES to FairnessEstimate(
                    category = ServiceCategory.BRAKES,
                    city = "Pune",
                    cityAverage = amt(averagePaise),
                    sampleSize = sampleSize,
                    range = FairnessRange(low = amt(averagePaise - 40_000), high = amt(averagePaise + 50_000)),
                ),
            ),
        ),
        checkedAt = Instant.parse("2026-07-03T10:00:00Z"),
    )

    /* ------------------------- round trip ------------------------- */

    @Test
    fun addedEntry_comesBackWithEveryField() = runTest {
        val db = newDb().apply { seedCar() }
        local(db).insert(entry())

        val stored = local(db).observeByCar(carId).first().single()
        assertEquals("log-1", stored.id.value)
        assertEquals(LocalDate(2026, 6, 15), stored.serviceDate)
        assertEquals(50_000, stored.odometer.km)
        assertEquals(330_000L, stored.totalAmount.paise)
        assertEquals("Sharma Motors", stored.workshopName?.value)
        assertEquals("front pads", stored.notes?.value)
        assertEquals(setOf(ServiceCategory.BRAKES), stored.categories)
    }

    @Test
    fun categories_roundTripAsASet() = runTest {
        val db = newDb().apply { seedCar() }
        val tags = setOf(ServiceCategory.BRAKES, ServiceCategory.OIL_CHANGE, ServiceCategory.AC)

        local(db).insert(entry(categories = tags))

        assertEquals(tags, local(db).observeByCar(carId).first().single().categories)
    }

    @Test
    fun anEntryWithNoCategories_readsBackEmptyRatherThanFailing() = runTest {
        val db = newDb().apply { seedCar() }

        local(db).insert(entry(categories = emptySet()))

        assertEquals(emptySet(), local(db).observeByCar(carId).first().single().categories)
    }

    @Test
    fun fairnessSnapshot_survivesTheRoundTrip() = runTest {
        val db = newDb().apply { seedCar() }

        local(db).insert(entry(fairness = snapshot(paidPaise = 330_000, averagePaise = 240_000)))

        val stored = assertNotNull(local(db).observeById(ServiceLogId("log-1")).first()?.fairness)
        // The verdict is not stored — it is rebuilt from the frozen estimate, and must come
        // back identical.
        val over = stored.outcome as FairnessOutcome.Over
        assertEquals(90_000L, over.by.paise)
        assertEquals(240_000L, stored.report.items.single().cityAverage?.paise)
        assertEquals(30, stored.report.sampleSize)
        // The spread is frozen with the average: a thin pool shows it instead of a verdict,
        // and it must not move under the owner between reads.
        val range = assertNotNull(stored.report.items.single().estimate?.range)
        assertEquals(200_000L, range.low.paise)
        assertEquals(290_000L, range.high.paise)
        assertEquals(Instant.parse("2026-07-03T10:00:00Z"), stored.checkedAt)
    }

    @Test
    fun entriesComeBackNewestFirst() = runTest {
        val db = newDb().apply { seedCar() }
        val local = local(db)

        local.insert(entry(id = "old", day = 1, odometerKm = 46_000))
        local.insert(entry(id = "new", day = 20, odometerKm = 52_000))

        assertEquals(listOf("new", "old"), local.observeByCar(carId).first().map { it.id.value })
    }

    /* ------------------------- writes ------------------------- */

    @Test
    fun writesLandPending() = runTest {
        val db = newDb().apply { seedCar() }
        local(db).insert(entry())

        val row = db.serviceLogQueries.selectById("log-1").executeAsOne()
        assertEquals(SyncStatus.PENDING.name, row.sync_status)
        assertEquals("2026-07-30T10:00:00Z", row.created_at)
    }

    /**
     * The row setup writes when the owner remembers their last service.
     *
     * `source` is a plain TEXT column, so a third constant needs no migration locally — but
     * nothing had ever stored one, and a service with no workshop, no categories and no money
     * is a shape this table had not been asked to hold either. All three round-trip or the
     * step silently stores nothing.
     */
    @Test
    fun aDeclaredServiceWithNoMoneyRoundTrips() = runTest {
        val db = newDb().apply { seedCar() }
        local(db).insert(
            entry(
                totalPaise = 0,
                categories = emptySet(),
                workshopName = null,
                source = LogSource.DECLARED,
            ),
        )

        val stored = local(db).observeById(ServiceLogId("log-1")).first()

        assertEquals(LogSource.DECLARED, stored?.source)
        assertEquals(0L, stored?.totalAmount?.paise)
        assertNull(stored?.workshopName)
        assertEquals(50_000, stored?.odometer?.km)
        // It has to reach the server like any other row, which is what makes the missing
        // Supabase enum value a sync failure rather than a cosmetic gap.
        assertEquals(SyncStatus.PENDING.name, db.serviceLogQueries.selectById("log-1").executeAsOne().sync_status)
    }

    @Test
    fun anEdit_rewritesCategoriesAndStampsTheParent() = runTest {
        val db = newDb().apply { seedCar() }
        local(db).insert(entry(categories = setOf(ServiceCategory.BRAKES)))

        // Mark the row SYNCED so the edit's return to PENDING is observable.
        db.serviceLogQueries.updateServiceLog(
            serviceDate = "2026-06-15", odometerKm = 50_000, totalAmountPaise = 330_000,
            workshopName = "Sharma Motors", notes = "front pads", source = "MANUAL",
            billId = null, billPhotoPath = null, fairnessSnapshot = null,
            lineItems = null,
            updatedAt = "2026-07-29T00:00:00Z", syncStatus = SyncStatus.SYNCED.name, id = "log-1",
        )

        val edited = local(db, now = "2026-07-31T08:00:00Z")
            .update(entry(categories = setOf(ServiceCategory.OIL_CHANGE, ServiceCategory.AC)))

        assertTrue(edited)
        val stored = local(db).observeById(ServiceLogId("log-1")).first()
        assertEquals(setOf(ServiceCategory.OIL_CHANGE, ServiceCategory.AC), stored?.categories)

        // A tag-only change still has to reach the server, so the parent goes back to PENDING.
        val row = db.serviceLogQueries.selectById("log-1").executeAsOne()
        assertEquals(SyncStatus.PENDING.name, row.sync_status)
        assertEquals("2026-07-31T08:00:00Z", row.updated_at)
    }

    @Test
    fun update_unknownEntry_answersFalse() = runTest {
        val db = newDb().apply { seedCar() }

        assertFalse(local(db).update(entry(id = "ghost")))
    }

    @Test
    fun softDelete_hidesTheEntryFromBothObservers() = runTest {
        val db = newDb().apply { seedCar() }
        val local = local(db)
        local.insert(entry())

        local.softDelete(ServiceLogId("log-1"))

        assertEquals(emptyList(), local.observeByCar(carId).first())
        assertNull(local.observeById(ServiceLogId("log-1")).first())
        // The tombstone survives, PENDING, so the deletion itself can sync.
        val row = db.serviceLogQueries.selectById("log-1").executeAsOneOrNull()
        assertNull(row, "the query filters tombstones")
    }

    /* ------------------------- odometer timeline ------------------------- */

    @Test
    fun odometerReadings_coalesceTheCarBaselineWithItsLogs() = runTest {
        val db = newDb().apply { seedCar(odometerKm = 45_000, createdAt = "2026-01-01T09:00:00Z") }
        val local = local(db)
        local.insert(entry(id = "log-1", day = 15, odometerKm = 50_000))

        val readings = local.odometerReadings(carId)

        assertEquals(2, readings.size)
        val baseline = readings.single { it.logId == null }
        assertEquals(45_000, baseline.odometer.km)
        // The car's instant is truncated to the date the timeline compares on.
        assertEquals(LocalDate(2026, 1, 1), baseline.date)
        val log = readings.single { it.logId != null }
        assertEquals(50_000, log.odometer.km)
        assertEquals(LocalDate(2026, 6, 15), log.date)
    }

    /**
     * The car's reading is dated from when it was last written down, not from when the car
     * was added. Otherwise the first odometer update would leave the timeline claiming a
     * reading was taken months before it was, and every backdated log after it would be
     * measured against a day that never happened.
     */
    @Test
    fun odometerReadings_dateTheCarFromItsLastOdometerUpdate() = runTest {
        val db = newDb().apply {
            seedCar(
                odometerKm = 61_500,
                createdAt = "2026-01-01T09:00:00Z",
                odometerUpdatedAt = "2026-06-20T18:00:00Z",
            )
        }

        val baseline = local(db).odometerReadings(carId).single { it.logId == null }

        assertEquals(LocalDate(2026, 6, 20), baseline.date)
        assertEquals(61_500, baseline.odometer.km)
    }

    /** Cars written before the column existed have no stamp; the day they were added stands in. */
    @Test
    fun odometerReadings_fallBackToTheDayTheCarWasAdded() = runTest {
        val db = newDb().apply {
            seedCar(createdAt = "2026-01-01T09:00:00Z", odometerUpdatedAt = null)
        }

        val baseline = local(db).odometerReadings(carId).single { it.logId == null }

        assertEquals(LocalDate(2026, 1, 1), baseline.date)
    }

    @Test
    fun odometerReadings_excludeDeletedEntries() = runTest {
        val db = newDb().apply { seedCar() }
        val local = local(db)
        local.insert(entry(id = "log-1", odometerKm = 50_000))
        local.softDelete(ServiceLogId("log-1"))

        val readings = local.odometerReadings(carId)

        assertEquals(1, readings.size, "only the car baseline should remain")
        assertNull(readings.single().logId)
    }

    @Test
    fun odometerReadings_isEmptyForAnUnknownCar() = runTest {
        val db = newDb()

        assertEquals(emptyList(), local(db).odometerReadings(CarId("nope")))
    }

    @Test
    fun observeOdometerReadings_emitsTheSameTimeline() = runTest {
        val db = newDb().apply { seedCar(odometerKm = 45_000, createdAt = "2026-01-01T09:00:00Z") }
        val local = local(db)
        local.insert(entry(id = "log-1", day = 15, odometerKm = 50_000))

        val readings = local.observeOdometerReadings(carId).first()

        assertEquals(2, readings.size)
        assertEquals(45_000, readings.single { it.logId == null }.odometer.km)
        assertEquals(50_000, readings.single { it.logId != null }.odometer.km)
    }

    /** The car's own reading moves from the garage, and the cost screen has to see it. */
    @Test
    fun observeOdometerReadings_reEmitsWhenTheCarsReadingChanges() = runTest {
        val db = newDb().apply { seedCar(odometerKm = 45_000) }
        val local = local(db)

        val before = local.observeOdometerReadings(carId).first().single().odometer.km
        db.carQueries.updateCar(
            make = "Maruti Suzuki",
            model = "Swift",
            variant = null,
            year = 2020,
            fuelType = "PETROL",
            registrationNumber = null,
            odometerKm = 48_000,
            purchaseYear = null,
            nickname = null,
            isPrimary = 1,
            odometerUpdatedAt = "2026-07-01T09:00:00Z",
            updatedAt = "2026-07-01T09:00:00Z",
            syncStatus = SyncStatus.PENDING.name,
            id = carId.value,
        )
        val after = local.observeOdometerReadings(carId).first().single().odometer.km

        assertEquals(45_000, before)
        assertEquals(48_000, after)
    }

    /**
     * The garage card collects this stream while services are being logged. Each write must
     * reach an already-open collector at once — a card one write behind is the reported bug.
     */
    @Test
    fun aLiveCollector_seesEachNewServiceLogImmediately() = runTest {
        val db = newDb().apply { seedCar(odometerKm = 5_100) }
        val local = local(db)

        val highestSeen = mutableListOf<Int>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            local.observeOdometerReadings(carId).collect { readings ->
                highestSeen += readings.maxOf { it.odometer.km }
            }
        }

        local.insert(entry(id = "log-1", day = 10, odometerKm = 5_150))
        local.insert(entry(id = "log-2", day = 11, odometerKm = 5_160))
        local.insert(entry(id = "log-3", day = 12, odometerKm = 5_190))
        collector.cancel()

        assertEquals(listOf(5_100, 5_150, 5_160, 5_190), highestSeen)
    }

    @Test
    fun observeOdometerReadings_isEmptyForAnUnknownCar() = runTest {
        val db = newDb()

        assertTrue(local(db).observeOdometerReadings(CarId("nope")).first().isEmpty())
    }
}
