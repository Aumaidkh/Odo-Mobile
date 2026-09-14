package com.hopcape.odo.feature.advisory.presentation.checklist

import arrow.core.left
import arrow.core.right
import arrow.core.Either
import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.analytics.api.ConsentStatus
import com.hopcape.analytics.api.UserTraits
import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.api.Logger
import com.hopcape.logging.api.TraceContext
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.feature.advisory.domain.checklist.PreServiceChecklist
import com.hopcape.odo.feature.advisory.domain.checklist.ServiceChecklistReader
import com.hopcape.odo.core.platform.file.PlatformDownloads
import com.hopcape.odo.core.platform.file.PlatformFileStore
import com.hopcape.odo.feature.advisory.presentation.AdvisoryTelemetry
import com.hopcape.performance.api.PerformanceTracer
import com.hopcape.performance.api.Span
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What the screen is handed, and what the owner is told when saving fails.
 *
 * A save that quietly does nothing is the worst outcome here: the owner walks into the
 * workshop believing they have the list on their phone.
 */
class ChecklistViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun aReadChecklistStopsLoadingAndIsNotEmpty() = runTest {
        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.isLoading)
        assertFalse(state.isEmpty)
        assertEquals(previewChecklist, state.checklist)
    }

    @Test
    fun aFailedReadIsAnEmptyScreenRatherThanAnAllClear() = runTest {
        val vm = viewModel(read = ServiceChecklistReader { DomainError.CarNotFound.left() })
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.state.value.isLoading)
        assertTrue(vm.state.value.isEmpty)
    }

    @Test
    fun aCarWithNothingDueKeepsTheScreenForWhatItSaysToRefuse() = runTest {
        val clear = previewChecklist.copy(
            checklist = previewChecklist.checklist.copy(due = emptyList()),
        )
        val vm = viewModel(read = ServiceChecklistReader { clear.right() })
        dispatcher.scheduler.advanceUntilIdle()

        // The anti-upsell list and the three questions are the half an owner is about to
        // need. Losing them because the first section is empty is losing the screen.
        assertFalse(vm.state.value.isEmpty)
    }

    @Test
    fun aScheduleThatSaidNothingIsAnEmptyScreen() = runTest {
        val nothing = previewChecklist.copy(
            checklist = PreServiceChecklist(due = emptyList(), notYet = emptyList()),
            scheduleUnavailable = true,
        )
        val vm = viewModel(read = ServiceChecklistReader { nothing.right() })
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.state.value.isEmpty)
    }

    @Test
    fun aCardThatProducedNoBytesFailsOutLoud() = runTest {
        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(ChecklistEvent.SaveClicked(null))

        assertEquals(ChecklistEffect.SaveFailed, vm.effects.first())
        assertFalse(vm.state.value.saving)
    }

    @Test
    fun aWrittenCardReachesTheOwnersDownloads() = runTest {
        val saved = mutableListOf<String>()
        val vm = viewModel(downloads = { key -> saved += key; Unit.right() })
        dispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(ChecklistEvent.SaveClicked(byteArrayOf(1, 2, 3)))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(ChecklistEffect.Saved, vm.effects.first())
        assertEquals(listOf(STORED_KEY), saved)
        assertFalse(vm.state.value.saving)
    }

    @Test
    fun aDownloadThatCouldNotBeWrittenIsSaidRatherThanSwallowed() = runTest {
        val vm = viewModel(downloads = { DomainError.LookupUnavailable.left() })
        dispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(ChecklistEvent.SaveClicked(byteArrayOf(1)))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(ChecklistEffect.SaveFailed, vm.effects.first())
    }

    private fun viewModel(
        read: ServiceChecklistReader = ServiceChecklistReader { previewChecklist.right() },
        downloads: (String) -> Either<DomainError, Unit> = { Unit.right() },
    ) = ChecklistViewModel(
        entry = "MANUAL",
        read = read,
        files = FakeFiles,
        downloads = PlatformDownloads { key, _, _ -> downloads(key) },
        telemetry = AdvisoryTelemetry(NoopLogger, NoopAnalytics, NoopTracer, IdGenerator { "id" }),
    )

    /** Writes nowhere and answers with the key it would have written under. */
    private object FakeFiles : PlatformFileStore {
        override suspend fun save(pickedRef: String, directory: String, fileName: String) = STORED_KEY.right()
        override suspend fun delete(storageKey: String) = Unit
        override suspend fun exists(storageKey: String) = true
        override suspend fun bytes(storageKey: String) = ByteArray(0).right()
        override suspend fun write(storageKey: String, bytes: ByteArray) = STORED_KEY.right()
    }

    private companion object {
        const val STORED_KEY = "export/odo-before-you-go-in.png"
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
