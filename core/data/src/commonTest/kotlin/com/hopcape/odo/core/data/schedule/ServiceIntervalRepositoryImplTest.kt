package com.hopcape.odo.core.data.schedule

import com.hopcape.crashreporting.api.CrashRecorder
import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.api.Logger
import com.hopcape.logging.api.TraceContext
import com.hopcape.odo.core.data.observability.DataTelemetry
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.performance.api.PerformanceTracer
import com.hopcape.performance.api.Span
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * The default schedule, and what a make's own row does to it.
 *
 * `service_schedule` holds one default set plus per-brand exceptions, so resolving is half of
 * what this repository does — the other half is refusing to invent an interval.
 */
class ServiceIntervalRepositoryImplTest {

    private val rows = listOf(
        row(brand = null, slug = "engine_oil", name = "Engine oil + filter", km = 10_000),
        row(brand = null, slug = "air_filter", name = "Air filter", km = 20_000),
        row(brand = "Hyundai", slug = "engine_oil", name = "Engine oil + filter", km = 15_000),
    )

    @Test
    fun aMakeWithNoExceptionGetsTheDefaultSet() = runTest {
        val intervals = repository(rows).intervals("Maruti Suzuki").getOrNull()

        assertEquals(setOf("engine_oil", "air_filter"), intervals?.keys)
        assertEquals(10_000, intervals?.get("engine_oil")?.km)
    }

    @Test
    fun aMakesOwnRowOverridesTheDefaultAndLeavesTheRestAlone() = runTest {
        val intervals = repository(rows).intervals("hyundai").getOrNull()

        assertEquals(15_000, intervals?.get("engine_oil")?.km)
        assertEquals(20_000, intervals?.get("air_filter")?.km)
    }

    @Test
    fun anotherMakesExceptionIsNeverApplied() = runTest {
        val intervals = repository(rows).intervals("Maruti Suzuki").getOrNull()

        // Hyundai's own 15,000 km rule must not reach a Maruti. This is the resolution
        // rule's worst failure: it is silent, and the figure it produces looks plausible.
        assertEquals(10_000, intervals?.get("engine_oil")?.km)
    }

    @Test
    fun aBrandOrSlugTypedWithStrayCasingOrSpaceStillApplies() = runTest {
        val typed = listOf(row(brand = " HYUNDAI ", slug = " Engine_Oil ", name = "Engine oil", km = 15_000))

        val intervals = repository(typed).intervals("hyundai").getOrNull()

        // Neither column has a foreign key, so nothing stops an admin typing either form.
        assertEquals(15_000, intervals?.get("engine_oil")?.km)
    }

    @Test
    fun aRowWithNeitherFigureIsDroppedRatherThanCarriedAsAnEmptyInterval() = runTest {
        val intervals = repository(listOf(row(null, "wipers", "Wipers"))).intervals(null).getOrNull()

        assertNull(intervals?.get("wipers"))
    }

    @Test
    fun aScheduleThatCouldNotBeReadIsAFailureRatherThanAnEmptySchedule() = runTest {
        val result = repository(rows, throwing = true).intervals(null)

        assertIs<DomainError.LookupUnavailable>(result.leftOrNull())
    }

    private fun repository(rows: List<ServiceIntervalDto>, throwing: Boolean = false) =
        ServiceIntervalRepositoryImpl(
            remote = FakeRemote(rows, throwing),
            telemetry = DataTelemetry(NoopLogger, NoopTracer, NoopCrash),
        )

    private fun row(brand: String?, slug: String, name: String, km: Int? = null, months: Int? = null) =
        ServiceIntervalDto(brand = brand, slug = slug, displayName = name, intervalKm = km, intervalMonths = months)

    private class FakeRemote(
        private val rows: List<ServiceIntervalDto>,
        private val throwing: Boolean,
    ) : ServiceIntervalRemoteDataSource {
        override suspend fun schedule(): List<ServiceIntervalDto> {
            if (throwing) error("postgrest exploded")
            return rows
        }
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
}
