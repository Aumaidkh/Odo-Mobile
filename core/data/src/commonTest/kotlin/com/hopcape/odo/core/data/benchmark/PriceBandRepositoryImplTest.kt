package com.hopcape.odo.core.data.benchmark

import arrow.core.Either
import com.hopcape.crashreporting.api.CrashRecorder
import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.api.Logger
import com.hopcape.logging.api.TraceContext
import com.hopcape.odo.core.data.observability.DataTelemetry
import com.hopcape.odo.core.domain.benchmark.BenchmarkBasis
import com.hopcape.odo.core.domain.benchmark.BenchmarkScope
import com.hopcape.odo.core.domain.benchmark.PriceBandQuery
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.domain.shared.VehicleSegment
import com.hopcape.odo.core.domain.shared.WorkshopTier
import com.hopcape.performance.api.PerformanceTracer
import com.hopcape.performance.api.Span
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The band, and the two ways of not having one.
 *
 * A `Left` is "we could not ask" and a `null` is "we asked and there is nothing". Collapsing
 * them would either put a retry in front of a job the tables simply do not cover, or show a
 * coverage gap as a network fault.
 */
class PriceBandRepositoryImplTest {

    /** The shape the deployed RPC actually returns, taken from a live dev call. */
    private val modelled = PriceBandDto(
        avgPaise = 168_000,
        p25Paise = 142_800,
        p75Paise = 193_200,
        sampleSize = 0,
        scope = "MODELLED",
        basis = "modelled",
        partsPaise = 90_000,
        labourHours = 1.5,
        labourPaisePerHour = 52_000,
    )

    @Test
    fun `a modelled band carries the sum behind it`() = runTest {
        val band = assertNotNull(repository(modelled).bandFor(query()).getOrNull())

        assertEquals(BenchmarkScope.MODELLED, band.scope)
        assertEquals(BenchmarkBasis.MODELLED, band.basis)
        assertEquals(0, band.sampleSize)
        val working = assertNotNull(band.working, "a modelled band has to show its working")
        assertEquals(90_000, working.partsPaise)
        assertEquals(1.5, working.labourHours)
        assertEquals(52_000, working.labourRatePerHour.paise)
    }

    /** `basis` comes back lower-cased and `scope` upper-cased. Both have to read. */
    @Test
    fun `the server's own casing is accepted`() = runTest {
        val band = assertNotNull(
            repository(modelled.copy(scope = "city_tier_segment", basis = "OBSERVED"))
                .bandFor(query()).getOrNull(),
        )

        assertEquals(BenchmarkScope.CITY_TIER_SEGMENT, band.scope)
        assertEquals(BenchmarkBasis.OBSERVED, band.basis)
    }

    /** Real bills have no sum behind them, and inventing one would be a fiction. */
    @Test
    fun `an observed band has no working`() = runTest {
        val observed = modelled.copy(
            scope = "CITY_TIER",
            basis = "observed",
            sampleSize = 14,
            partsPaise = null,
            labourHours = null,
            labourPaisePerHour = null,
        )

        val band = assertNotNull(repository(observed).bandFor(query()).getOrNull())

        assertNull(band.working)
        assertEquals(14, band.sampleSize)
    }

    /**
     * A rung this build has never heard of. Showing the band anyway would put a figure on
     * screen under a claim about where it came from that the app cannot make.
     */
    @Test
    fun `a scope this build does not know is no band at all`() = runTest {
        val band = repository(modelled.copy(scope = "SOME_NEW_RUNG")).bandFor(query())

        assertNull(band.getOrNull())
    }

    @Test
    fun `a missing basis is no band at all`() = runTest {
        assertNull(repository(modelled.copy(basis = null)).bandFor(query()).getOrNull())
    }

    /** The tables cover no such job. Not a failure, and not a retry. */
    @Test
    fun `nothing for this job is a null rather than an error`() = runTest {
        val result = repository(null).bandFor(query())

        assertNull(result.getOrNull())
        assertIs<Either.Right<*>>(result)
    }

    /** A server that could not be reached is the one case worth offering a retry for. */
    @Test
    fun `a lookup that throws is reported as unavailable`() = runTest {
        val result = repository(throwing = true).bandFor(query())

        assertIs<DomainError.LookupUnavailable>(result.leftOrNull())
    }

    /* ------------------------------ Fixtures ------------------------------ */

    private fun query() = PriceBandQuery(
        categorySlug = "ac_service",
        city = "Srinagar",
        segment = VehicleSegment.HATCHBACK,
        fuel = FuelType.PETROL,
        workshopTier = WorkshopTier.AUTHORISED,
    )

    private fun repository(dto: PriceBandDto? = null, throwing: Boolean = false) =
        PriceBandRepositoryImpl(
            remote = FakeRemote(dto, throwing),
            telemetry = DataTelemetry(NoopLogger, NoopTracer, NoopCrash),
        )

    private class FakeRemote(
        private val dto: PriceBandDto?,
        private val throwing: Boolean,
    ) : PriceBandRemoteDataSource {
        override suspend fun band(
            categorySlug: String,
            city: String,
            segment: String?,
            fuel: String?,
            workshopTier: String?,
        ): PriceBandDto? {
            if (throwing) error("postgrest exploded")
            return dto
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
