package com.hopcape.odo.feature.auth.presentation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The number screen: validate, and get out of the way.
 *
 * The thing worth pinning down is what it no longer does. It has no session manager, so there
 * is nothing here that can wait on a network — which is the whole of #409.
 */
class PhoneViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun aValidNumberMovesOnAtOnceAndCarriesTheParsedForm() = runTest(dispatcher) {
        val viewModel = PhoneViewModel(requests = broker(), telemetry = silentAuthTelemetry())
        viewModel.onEvent(PhoneEvent.PhoneChanged("9812345678"))

        viewModel.onEvent(PhoneEvent.SendCodeClicked)

        // No dispatcher advance: nothing was awaited, which is the point.
        val effect = viewModel.effects.first()
        assertEquals(PhoneEffect.CodeSent("+919812345678"), effect)
        // And the button never spun, because there was nothing to spin for.
        assertTrue(viewModel.state.value.canSubmit)
    }

    @Test
    fun aNumberThatCannotBeParsedFailsOnThisScreenRatherThanTheNext() = runTest(dispatcher) {
        // Eleven digits with no country code: the parser refuses to guess which country an
        // SMS should go to, and this is the one refusal that still belongs on this screen.
        val viewModel = PhoneViewModel(requests = broker(), telemetry = silentAuthTelemetry())
        viewModel.onEvent(PhoneEvent.PhoneChanged("98123456789"))

        viewModel.onEvent(PhoneEvent.SendCodeClicked)

        assertIs<com.hopcape.odo.feature.auth.presentation.state.Submission.Failed>(
            viewModel.state.value.submission,
        )
    }

    @Test
    fun typingAgainClearsTheLastRefusal() = runTest(dispatcher) {
        val viewModel = PhoneViewModel(requests = broker(), telemetry = silentAuthTelemetry())
        viewModel.onEvent(PhoneEvent.PhoneChanged("98123456789"))
        viewModel.onEvent(PhoneEvent.SendCodeClicked)

        viewModel.onEvent(PhoneEvent.PhoneChanged("9812345678"))

        assertEquals(
            com.hopcape.odo.feature.auth.presentation.state.Submission.Idle,
            viewModel.state.value.submission,
        )
    }

    /**
     * A broker whose work never runs, on a dispatcher no test advances.
     *
     * Its own scheduler, deliberately: a bare `StandardTestDispatcher()` inside `runTest`
     * picks up the test's scheduler from the context and runs on the same clock.
     *
     * That is the assertion, not scaffolding: the number screen hands the request over and
     * carries on, so every test here passes with the provider never reached.
     */
    private fun broker() = testBroker(NeverAskedGateway, CoroutineScope(StandardTestDispatcher(TestCoroutineScheduler())))


}
