package com.hopcape.odo.feature.onboarding.presentation.welcome

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

// Pointing Dispatchers.Main at the test scheduler is still an experimental coroutines API.
@OptIn(ExperimentalCoroutinesApi::class)
class WelcomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun continuing_goesStraightIntoCarSetup() = runTest(dispatcher) {
        val viewModel = WelcomeViewModel()

        viewModel.onEvent(WelcomeEvent.ContinueClicked)

        // No sign-in first: first run has to reach a working car without an account.
        assertEquals(WelcomeEffect.OpenCarSetup, viewModel.effects.first())
    }

    @Test
    fun theLegalLinks_areOffered() = runTest(dispatcher) {
        val viewModel = WelcomeViewModel()

        viewModel.onEvent(WelcomeEvent.TermsClicked)
        assertEquals(WelcomeEffect.OpenTerms, viewModel.effects.first())

        viewModel.onEvent(WelcomeEvent.PrivacyClicked)
        assertEquals(WelcomeEffect.OpenPrivacy, viewModel.effects.first())
    }
}
