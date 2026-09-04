package com.hopcape.odo.feature.billcheck.presentation.result

import arrow.core.left
import arrow.core.right
import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.analytics.api.ConsentStatus
import com.hopcape.analytics.api.UserTraits
import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.api.Logger
import com.hopcape.logging.api.TraceContext
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.feature.billcheck.domain.BillCheckFixtures
import com.hopcape.odo.feature.billcheck.domain.BillCheckReader
import com.hopcape.odo.feature.billcheck.presentation.BillCheckTelemetry
import com.hopcape.performance.api.PerformanceTracer
import com.hopcape.performance.api.Span
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The wall, and the way back through it.
 *
 * The masked result is where the offer is made, so the one thing that must not happen is an
 * owner paying on the sheet and finding the screen behind it still masked.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BillCheckViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun anOwnerWithACheckToSpendSeesTheFindings() = runTest(dispatcher) {
        val viewModel = viewModel(unlocked = true)
        advanceUntilIdle()

        val ready = assertIs<BillCheckUiState.Content.Ready>(viewModel.state.value.content)
        assertFalse(ready.locked)
    }

    @Test
    fun anOwnerWithNoneLeftSeesTheWall() = runTest(dispatcher) {
        val viewModel = viewModel(unlocked = false)
        advanceUntilIdle()

        val ready = assertIs<BillCheckUiState.Content.Ready>(viewModel.state.value.content)
        assertTrue(ready.locked)
    }

    /**
     * The offer is made on a sheet drawn over this screen, so buying there leaves the result
     * behind it masked until something asks again. Nothing else on the screen ever would.
     */
    @Test
    fun aCheckBoughtOnTheSheetUnmasksTheResult() = runTest(dispatcher) {
        var hasCheck = false
        val viewModel = viewModel(unlocked = { hasCheck })
        advanceUntilIdle()
        assertTrue((viewModel.state.value.content as BillCheckUiState.Content.Ready).locked)

        hasCheck = true
        viewModel.onEvent(BillCheckEvent.Resumed)
        advanceUntilIdle()

        assertFalse((viewModel.state.value.content as BillCheckUiState.Content.Ready).locked)
    }

    /**
     * Only ever unmasks. A check spent elsewhere while this screen sat behind a sheet must not
     * take back an answer already on screen — the owner paid for this reading of this bill.
     */
    @Test
    fun anAnswerAlreadyGivenIsNeverTakenBack() = runTest(dispatcher) {
        var hasCheck = true
        val viewModel = viewModel(unlocked = { hasCheck })
        advanceUntilIdle()

        hasCheck = false
        viewModel.onEvent(BillCheckEvent.Resumed)
        advanceUntilIdle()

        assertFalse((viewModel.state.value.content as BillCheckUiState.Content.Ready).locked)
    }

    /** The wall is where the offer is made, so reaching it opens the offer (D3). */
    @Test
    fun theWallOpensTheOffers() = runTest(dispatcher) {
        val viewModel = viewModel(unlocked = false)
        advanceUntilIdle()

        assertEquals(BillCheckEffect.OpenOffers, viewModel.effects.first())
    }

    @Test
    fun aReadThatFailsOffersARetry() = runTest(dispatcher) {
        val viewModel = viewModel(reader = { DomainError.PersistenceFailure().left() })
        advanceUntilIdle()

        assertIs<BillCheckUiState.Content.Failed>(viewModel.state.value.content)
    }

    /** A reader that throws is the same outcome as one that refuses, and never a crash. */
    @Test
    fun aReaderThatThrowsFailsTheScreenRatherThanTheApp() = runTest(dispatcher) {
        val viewModel = viewModel(reader = { error("reader exploded") })
        advanceUntilIdle()

        assertIs<BillCheckUiState.Content.Failed>(viewModel.state.value.content)
    }

    /* ------------------------------ Fixtures ------------------------------ */

    private fun viewModel(
        reader: BillCheckReader = BillCheckReader { BillCheckFixtures.monthSix.right() },
        unlocked: Boolean,
    ) = viewModel(reader = reader, unlocked = { unlocked })

    private fun viewModel(
        reader: BillCheckReader = BillCheckReader { BillCheckFixtures.monthSix.right() },
        unlocked: suspend () -> Boolean = { true },
    ) = BillCheckViewModel(
        billId = "bill-1",
        reader = reader,
        unlocked = unlocked,
        telemetry = BillCheckTelemetry(NoopLogger, NoopAnalytics, NoopTracer, FixedIds),
    )

    private object FixedIds : IdGenerator {
        override fun newId(): String = "id"
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

    private object NoopAnalytics : AnalyticsTracker {
        override fun identify(traits: UserTraits) = Unit
        override fun track(eventName: String, properties: Map<String, Any?>) = Unit
        override fun setConsent(status: ConsentStatus) = Unit
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

}
