package com.hopcape.odo.feature.questionnaire.presentation

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.analytics.api.ConsentStatus
import com.hopcape.analytics.api.UserTraits
import com.hopcape.logging.api.HLogger
import com.hopcape.odo.core.domain.owner.model.QuestionAnswer
import com.hopcape.odo.core.domain.owner.model.QuestionKey
import com.hopcape.odo.core.domain.owner.repository.QuestionnaireRepository
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.feature.questionnaire.QuestionKeys
import com.hopcape.odo.feature.questionnaire.odoQuestions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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
import kotlin.test.assertTrue
import kotlin.time.Instant

// Pointing Dispatchers.Main at the test scheduler is still an experimental coroutines API.
@OptIn(ExperimentalCoroutinesApi::class)
class QuestionnaireViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val goal = QuestionKeys.Goal

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun itAsksOnlyTheQuestionsTheRouteNamed() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(listOf(goal), viewModel.state.value.questions.map { it.key })
    }

    /** An older deep link should ask what it can rather than refuse to open. */
    @Test
    fun anUnknownKeyIsSkipped() = runTest(dispatcher) {
        val viewModel = viewModel(keys = listOf(goal, QuestionKey("nope.v1")))
        advanceUntilIdle()

        assertEquals(listOf(goal), viewModel.state.value.questions.map { it.key })
    }

    @Test
    fun answersAlreadyGivenArePreselected() = runTest(dispatcher) {
        val repository = FakeRepository(stored = mapOf(goal to listOf("TRACK_COSTS")))

        val viewModel = viewModel(repository = repository)
        advanceUntilIdle()

        assertEquals(setOf("TRACK_COSTS"), viewModel.state.value.selected(goal))
    }

    /** The goal question is SINGLE, so a second tap replaces rather than adds. */
    @Test
    fun pickingOnASingleQuestionReplaces() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onEvent(QuestionnaireEvent.OptionToggled(goal, "TRACK_COSTS"))
        viewModel.onEvent(QuestionnaireEvent.OptionToggled(goal, "SELL_SOON"))

        assertEquals(setOf("SELL_SOON"), viewModel.state.value.selected(goal))
    }

    @Test
    fun continueIsBlockedUntilEveryQuestionIsAnswered() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        assertFalse(viewModel.state.value.canContinue)

        viewModel.onEvent(QuestionnaireEvent.OptionToggled(goal, "TRACK_COSTS"))

        assertTrue(viewModel.state.value.canContinue)
    }

    @Test
    fun continuingStoresTheAnswerAndFinishes() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = viewModel(repository = repository)
        advanceUntilIdle()
        viewModel.onEvent(QuestionnaireEvent.OptionToggled(goal, "SELL_SOON"))

        viewModel.onEvent(QuestionnaireEvent.ContinueClicked)
        advanceUntilIdle()

        assertEquals(mapOf(goal to setOf("SELL_SOON")), repository.saved)
        assertEquals(QuestionnaireEffect.Finished, viewModel.effects.first())
    }

    /** The answers stay on screen so the owner can retry rather than re-pick. */
    @Test
    fun aFailedWriteReportsAndKeepsTheAnswers() = runTest(dispatcher) {
        val repository = FakeRepository(failWrites = true)
        val viewModel = viewModel(repository = repository)
        advanceUntilIdle()
        viewModel.onEvent(QuestionnaireEvent.OptionToggled(goal, "SELL_SOON"))

        viewModel.onEvent(QuestionnaireEvent.ContinueClicked)
        advanceUntilIdle()

        assertEquals(QuestionnaireEffect.SaveFailed, viewModel.effects.first())
        assertEquals(setOf("SELL_SOON"), viewModel.state.value.selected(goal))
        assertFalse(viewModel.state.value.isSaving)
    }

    @Test
    fun continuingWithNothingPickedDoesNothing() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = viewModel(repository = repository)
        advanceUntilIdle()

        viewModel.onEvent(QuestionnaireEvent.ContinueClicked)
        advanceUntilIdle()

        assertTrue(repository.saved.isEmpty())
    }

    @Test
    fun backLeavesWithoutSaving() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = viewModel(repository = repository)
        advanceUntilIdle()
        viewModel.onEvent(QuestionnaireEvent.OptionToggled(goal, "SELL_SOON"))

        viewModel.onEvent(QuestionnaireEvent.BackClicked)

        assertEquals(QuestionnaireEffect.NavigateBack, viewModel.effects.first())
        assertTrue(repository.saved.isEmpty())
    }

    @Test
    fun completingTheQuestionnaireIsMeasurable() = runTest(dispatcher) {
        val analytics = RecordingAnalytics()
        val viewModel = viewModel(analytics = analytics)
        advanceUntilIdle()
        viewModel.onEvent(QuestionnaireEvent.OptionToggled(goal, "TRACK_COSTS"))

        viewModel.onEvent(QuestionnaireEvent.ContinueClicked)
        advanceUntilIdle()

        assertEquals(
            listOf(
                QuestionnaireTelemetry.Event.OPENED,
                QuestionnaireTelemetry.Event.ANSWERED,
                QuestionnaireTelemetry.Event.COMPLETED,
            ),
            analytics.names,
        )
    }

    private fun viewModel(
        keys: List<QuestionKey> = listOf(QuestionKeys.Goal),
        repository: QuestionnaireRepository = FakeRepository(),
        analytics: AnalyticsTracker = RecordingAnalytics(),
    ) = QuestionnaireViewModel(
        keys = keys,
        registry = odoQuestions(),
        repository = repository,
        telemetry = QuestionnaireTelemetry(logger = HLogger.asLogger(), analytics = analytics),
    )

    private class FakeRepository(
        private val stored: Map<QuestionKey, List<String>> = emptyMap(),
        private val failWrites: Boolean = false,
    ) : QuestionnaireRepository {
        val saved = mutableMapOf<QuestionKey, Set<String>>()

        override suspend fun save(key: QuestionKey, values: Set<String>): Either<DomainError, Unit> =
            if (failWrites) {
                DomainError.PersistenceFailure("nope").left()
            } else {
                saved[key] = values
                Unit.right()
            }

        override fun observe(): Flow<List<QuestionAnswer>> = flowOf(emptyList())

        override suspend fun answersFor(key: QuestionKey): Either<DomainError, List<QuestionAnswer>> =
            stored[key].orEmpty()
                .map { QuestionAnswer(key, it, Instant.parse("2026-09-02T00:00:00Z")) }
                .right()
    }

    private class RecordingAnalytics : AnalyticsTracker {
        val names = mutableListOf<String>()

        override fun track(eventName: String, properties: Map<String, Any?>) {
            names += eventName
        }

        override fun identify(traits: UserTraits) = Unit
        override fun setConsent(status: ConsentStatus) = Unit
        override fun flush() = Unit
    }
}
