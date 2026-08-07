package com.hopcape.odo.core.data.fairness

import com.hopcape.crashreporting.api.CrashRecorder
import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.api.Logger
import com.hopcape.logging.api.TraceContext
import com.hopcape.odo.core.data.observability.DataTelemetry
import com.hopcape.odo.core.domain.fairness.model.FairnessConfidence
import com.hopcape.odo.core.domain.fairness.model.FairnessOutcome
import com.hopcape.odo.core.domain.fairness.model.FairnessQuery
import com.hopcape.odo.core.domain.fairness.model.FairnessQueryItem
import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.performance.api.PerformanceTracer
import com.hopcape.performance.api.Span
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [FairnessRepositoryImpl] never touches SQLDelight — it reads through
 * [FairnessRemoteDataSource] only — so unlike the other repositories in this package it has
 * no local-data-source split.
 */
class FairnessRepositoryImplTest {

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

    private val telemetry = DataTelemetry(NoopLogger, NoopTracer, NoopCrash)

    @Test
    fun benchmarks_comeBackKeyedByCategory() = runTest {
        val repo = FairnessRepositoryImpl(FakeFairnessRemoteDataSource(), telemetry)

        val estimates = repo.estimates(setOf(ServiceCategory.BRAKES, ServiceCategory.OIL_CHANGE), "Pune")

        assertEquals(2, estimates.size)
        val brakes = assertNotNull(estimates[ServiceCategory.BRAKES])
        assertEquals(340_000L, brakes.cityAverage.paise)
        assertEquals("Pune", brakes.city)
    }

    @Test
    fun aCategoryWithNoBenchmark_isAbsentRatherThanNull() = runTest {
        val repo = FairnessRepositoryImpl(FakeFairnessRemoteDataSource(), telemetry)

        val estimates = repo.estimates(setOf(ServiceCategory.ELECTRICAL), "Pune")

        assertTrue(estimates.isEmpty(), "absent means no data — the caller does one lookup, not two")
    }

    @Test
    fun aThinlySampledBenchmark_reportsLowConfidence() = runTest {
        val repo = FairnessRepositoryImpl(FakeFairnessRemoteDataSource(), telemetry)

        val ac = assertNotNull(repo.estimates(setOf(ServiceCategory.AC), "Pune")[ServiceCategory.AC])

        // The PRD's guardrail: under five data points there is no confident verdict.
        assertEquals(FairnessConfidence.LOW, ac.confidence)
        // …which is exactly when the range has to be there: it is all the screen can show.
        val range = assertNotNull(ac.range)
        assertTrue(range.low.paise <= ac.cityAverage.paise && ac.cityAverage.paise <= range.high.paise)
    }

    @Test
    fun aHalfRange_isNoRangeAtAll() = runTest {
        val halfPriced = object : FairnessRemoteDataSource {
            override suspend fun estimates(categories: List<String>, city: String) = listOf(
                FairnessEstimateDto(
                    category = ServiceCategory.BRAKES.name,
                    city = city,
                    cityAveragePaise = 340_000,
                    sampleSize = 24,
                    p25Paise = 290_000,
                    p75Paise = null,
                ),
            )
        }

        val brakes = assertNotNull(
            FairnessRepositoryImpl(halfPriced, telemetry).estimates(setOf(ServiceCategory.BRAKES), "Pune")[ServiceCategory.BRAKES],
        )

        assertNull(brakes.range, "one end of a range cannot be drawn")
    }

    @Test
    fun aBackwardsRange_isDropped() = runTest {
        val backwards = object : FairnessRemoteDataSource {
            override suspend fun estimates(categories: List<String>, city: String) = listOf(
                FairnessEstimateDto(
                    category = ServiceCategory.BRAKES.name,
                    city = city,
                    cityAveragePaise = 340_000,
                    sampleSize = 24,
                    p25Paise = 395_000,
                    p75Paise = 290_000,
                ),
            )
        }

        val brakes = assertNotNull(
            FairnessRepositoryImpl(backwards, telemetry).estimates(setOf(ServiceCategory.BRAKES), "Pune")[ServiceCategory.BRAKES],
        )

        assertNull(brakes.range)
    }

    @Test
    fun theAnalyzer_judgesAQueryAgainstTheSameBenchmarks() = runTest {
        val analyzer = RepositoryFairnessAnalyzer(FairnessRepositoryImpl(FakeFairnessRemoteDataSource(), telemetry))

        val report = analyzer.analyze(
            FairnessQuery(
                city = "Pune",
                items = listOf(
                    FairnessQueryItem(
                        label = "Oil change",
                        category = ServiceCategory.OIL_CHANGE,
                        amount = Amount.of(280_000).getOrNull()!!,
                    ),
                ),
            ),
        )

        val over = assertIs<FairnessOutcome.Over>(report.outcome)
        assertEquals(70_000L, over.by.paise)
    }

    @Test
    fun theAnalyzer_claimsNothingWithoutABenchmark() = runTest {
        val analyzer = RepositoryFairnessAnalyzer(FairnessRepositoryImpl(FakeFairnessRemoteDataSource(), telemetry))

        val report = analyzer.analyze(
            FairnessQuery(
                city = "Pune",
                items = listOf(
                    FairnessQueryItem(
                        label = "Rewiring",
                        category = ServiceCategory.ELECTRICAL,
                        amount = Amount.of(280_000).getOrNull()!!,
                    ),
                ),
            ),
        )

        assertEquals(FairnessOutcome.NoBenchmark, report.outcome)
    }

    @Test
    fun askingForNothing_doesNotHitTheSource() = runTest {
        var calls = 0
        val counting = object : FairnessRemoteDataSource {
            override suspend fun estimates(categories: List<String>, city: String): List<FairnessEstimateDto> {
                calls++
                return emptyList()
            }
        }

        FairnessRepositoryImpl(counting, telemetry).estimates(emptySet(), "Pune")

        assertEquals(0, calls)
    }

    @Test
    fun aFailingSource_yieldsNoVerdictRatherThanAnError() = runTest {
        val broken = object : FairnessRemoteDataSource {
            override suspend fun estimates(categories: List<String>, city: String): List<FairnessEstimateDto> =
                error("RPC down")
        }

        val estimates = FairnessRepositoryImpl(broken, telemetry).estimates(setOf(ServiceCategory.BRAKES), "Pune")

        assertTrue(estimates.isEmpty(), "'we don't know' is a legitimate answer; throwing at the caller is not")
    }
}
