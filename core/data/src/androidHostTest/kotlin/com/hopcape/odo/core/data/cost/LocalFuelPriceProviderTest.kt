package com.hopcape.odo.core.data.cost

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hopcape.crashreporting.api.CrashRecorder
import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.api.Logger
import com.hopcape.logging.api.TraceContext
import com.hopcape.odo.core.data.db.OdoDatabase
import com.hopcape.odo.core.data.observability.DataTelemetry
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.cost.fuel.FuelEfficiencyPolicy
import com.hopcape.odo.core.domain.cost.fuel.FuelPriceSource
import com.hopcape.odo.core.domain.cost.fuel.FuelUnit
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.performance.api.PerformanceTracer
import com.hopcape.performance.api.Span
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalFuelPriceProviderTest {

    private val today = LocalDate(2026, 8, 20)

    @Test
    fun aSeededCity_hasAPriceForEveryFuelType() = runTest {
        val provider = provider(seededDb())

        FuelType.entries.forEach { fuelType ->
            val price = provider.priceFor("Pune", fuelType)
            assertNotNull(price, "$fuelType")
            assertEquals("Pune", price.city, "the city is echoed as the owner wrote it")
            assertEquals(fuelType, price.fuelType)
            assertEquals(FuelPriceSource.SEED, price.source)
            assertEquals(SEED_EFFECTIVE_DATE, price.effectiveDate)
            assertTrue(price.pricePerUnit.paise > 0, "$fuelType")
        }
    }

    @Test
    fun theCityIsMatchedIgnoringCaseAndPadding() = runTest {
        val price = provider(seededDb()).priceFor("  BENGALURU ", FuelType.PETROL)

        assertEquals(
            FUEL_PRICE_SEED.getValue("bengaluru").getValue(FuelType.PETROL),
            price?.pricePerUnit?.paise,
        )
        // Matched on a lowercase key, but reported back as the owner typed it: the key is
        // Odo's index, and a screen that printed it would read "in bengaluru".
        assertEquals("BENGALURU", price?.city)
    }

    @Test
    fun aWriteReachesAnyoneWatchingThePrices() = runTest {
        val provider = provider(seededDb())
        val changes = mutableListOf<Unit>()

        val job = launch { provider.priceChanges().collect { changes += it } }
        advanceUntilIdle()
        val onSubscribe = changes.size
        provider.setOverride(FuelType.PETROL, paise(11_000), today)
        advanceUntilIdle()
        job.cancel()

        assertEquals(1, onSubscribe, "a subscriber is told the current state first")
        assertTrue(changes.size > onSubscribe, "setting a rate has to reach the screen")
    }

    @Test
    fun anUncoveredCity_hasNoPriceAndIsReported() = runTest {
        val logger = RecordingLogger()

        val price = provider(seededDb(), logger).priceFor("Nagpur", FuelType.PETROL)

        assertNull(price)
        assertTrue(logger.events.any { it.endsWith("missing") }, logger.events.toString())
    }

    @Test
    fun aSeededPrice_turnsIntoAPlausibleRatePerKm() = runTest {
        val price = assertNotNull(provider(seededDb()).priceFor("Mumbai", FuelType.PETROL))

        assertEquals(FuelUnit.LITRE, price.unit)
        // Around ₹7/km on a petrol car — the figure the running-cost screen leads with.
        val rate = FuelEfficiencyPolicy.ratePerKm(price).paise
        assertTrue(rate in 500..900, "unexpected rate $rate paise/km")
    }

    @Test
    fun theOwnersOwnRate_beatsTheCityPrice() = runTest {
        val provider = provider(seededDb())

        provider.setOverride(FuelType.PETROL, paise(11_000), today)
        val price = assertNotNull(provider.priceFor("Pune", FuelType.PETROL))

        assertEquals(11_000L, price.pricePerUnit.paise)
        assertEquals(FuelPriceSource.OWNER, price.source)
        // Their rate is not a claim about a city, and it is dated the day they set it.
        assertNull(price.city)
        assertEquals(today, price.effectiveDate)
    }

    @Test
    fun theOwnersOwnRate_answersWithNoCitySet() = runTest {
        val provider = provider(seededDb())

        assertNull(provider.priceFor(null, FuelType.PETROL))
        provider.setOverride(FuelType.PETROL, paise(11_000), today)

        assertEquals(11_000L, provider.priceFor(null, FuelType.PETROL)?.pricePerUnit?.paise)
    }

    @Test
    fun theOwnersOwnRate_appliesOnlyToItsFuelType() = runTest {
        val provider = provider(seededDb())

        provider.setOverride(FuelType.PETROL, paise(11_000), today)

        assertEquals(FuelPriceSource.SEED, provider.priceFor("Pune", FuelType.DIESEL)?.source)
    }

    @Test
    fun settingTheRateTwice_keepsOneRow() = runTest {
        val provider = provider(seededDb())

        provider.setOverride(FuelType.PETROL, paise(11_000), today)
        provider.setOverride(FuelType.PETROL, paise(10_800), today)

        assertEquals(10_800L, provider.priceFor("Pune", FuelType.PETROL)?.pricePerUnit?.paise)
    }

    @Test
    fun clearingTheRate_fallsBackToTheCityPrice() = runTest {
        val provider = provider(seededDb())
        provider.setOverride(FuelType.PETROL, paise(11_000), today)

        provider.clearOverride(FuelType.PETROL)
        val price = assertNotNull(provider.priceFor("Pune", FuelType.PETROL))

        assertEquals(FuelPriceSource.SEED, price.source)
        assertEquals(FUEL_PRICE_SEED.getValue("pune").getValue(FuelType.PETROL), price.pricePerUnit.paise)
    }

    /** What a weekly refresh will look like: a newer row for the same city wins on date. */
    @Test
    fun aFresherPrice_winsOverAnOlderOne() = runTest {
        val db = seededDb()
        db.fuelPriceQueries.insertPrice(
            id = "remote-pune-petrol-2026-08-15",
            city = "pune",
            fuel_type = FuelType.PETROL.name,
            paise_per_unit = 10_900,
            effective_date = "2026-08-15",
            source = FuelPriceSource.REMOTE.name,
        )

        val price = assertNotNull(provider(db).priceFor("Pune", FuelType.PETROL))

        assertEquals(10_900L, price.pricePerUnit.paise)
        assertEquals(FuelPriceSource.REMOTE, price.source)
    }

    /** ...but never over the owner's, who typed theirs on purpose. */
    @Test
    fun aFresherPrice_doesNotOverruleTheOwner() = runTest {
        val db = seededDb()
        val provider = provider(db)
        provider.setOverride(FuelType.PETROL, paise(11_000), LocalDate(2026, 8, 1))
        db.fuelPriceQueries.insertPrice(
            id = "remote-pune-petrol-2026-08-15",
            city = "pune",
            fuel_type = FuelType.PETROL.name,
            paise_per_unit = 10_900,
            effective_date = "2026-08-15",
            source = FuelPriceSource.REMOTE.name,
        )

        assertEquals(FuelPriceSource.OWNER, provider(db).priceFor("Pune", FuelType.PETROL)?.source)
    }

    @Test
    fun seedingTwice_addsNothing() = runTest {
        val db = seededDb()
        val before = db.fuelPriceQueries.countSeededOn(SEED_EFFECTIVE_DATE.toString()).executeAsOne()

        seedFuelPrices(db)

        assertEquals(before, db.fuelPriceQueries.countSeededOn(SEED_EFFECTIVE_DATE.toString()).executeAsOne())
        assertEquals(FUEL_PRICE_SEED.values.sumOf { it.size }.toLong(), before)
    }

    /* ------------------------- fixtures ------------------------- */

    private fun seededDb(): OdoDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        OdoDatabase.Schema.create(driver)
        return OdoDatabase(driver).also(::seedFuelPrices)
    }

    private fun provider(db: OdoDatabase, logger: Logger = RecordingLogger()) = LocalFuelPriceProvider(
        database = db,
        telemetry = DataTelemetry(logger = logger, tracer = NoopTracer, crash = NoopCrash),
        dispatcher = Dispatchers.Unconfined,
    )

    private fun paise(value: Long): Amount = Amount.of(value).getOrNull()!!

    private class RecordingLogger : Logger {
        val events = mutableListOf<String>()
        override fun log(
            level: LogLevel,
            tag: String,
            event: String,
            traceContext: TraceContext?,
            fields: Map<String, Any?>,
        ) {
            events += event
        }

        override fun flush() = Unit
    }

    private class FakeSpan(
        override val spanId: String,
        override val traceId: String,
        override val parentSpanId: String?,
        override val name: String,
    ) : Span {
        override fun setAttribute(key: String, value: Any?): Span = this
    }

    private object NoopTracer : PerformanceTracer {
        override fun startSpan(name: String, traceId: String, parentSpanId: String?): Span =
            FakeSpan("span", traceId, parentSpanId, name)

        override fun endSpan(span: Span) = Unit
        override fun flush() = Unit
    }

    private object NoopCrash : CrashRecorder {
        override fun recordNonFatal(throwable: Throwable, customKeys: Map<String, Any?>) = Unit
        override fun leaveBreadcrumb(tag: String, message: String) = Unit
        override fun setCustomKey(key: String, value: Any?) = Unit
        override fun setUserId(userId: String?) = Unit
    }
}
