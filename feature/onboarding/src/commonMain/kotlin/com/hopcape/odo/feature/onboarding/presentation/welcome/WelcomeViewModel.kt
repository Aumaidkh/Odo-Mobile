package com.hopcape.odo.feature.onboarding.presentation.welcome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.feature.onboarding.presentation.OnboardingTelemetry
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * State holder for the Welcome pitch. It holds no state (see [WelcomeEvent]) — it turns the
 * four things the owner can tap into [WelcomeEffect]s the route host performs.
 *
 * Thin on purpose, and still worth existing: it is where the funnel begins. The pitch is the
 * first screen of the acquisition funnel, so how many owners see it versus continue past it is
 * the top of every first-run number — hence [OnboardingTelemetry] here rather than only in the
 * setup flow.
 */
internal class WelcomeViewModel(
    private val telemetry: OnboardingTelemetry,
) : ViewModel() {

    private val _effects = Channel<WelcomeEffect>(Channel.BUFFERED)
    val effects: Flow<WelcomeEffect> = _effects.receiveAsFlow()

    init {
        telemetry.welcomeShown()
    }

    fun onEvent(event: WelcomeEvent) {
        val effect = when (event) {
            WelcomeEvent.ContinueClicked -> {
                telemetry.welcomeCompleted()
                WelcomeEffect.OpenCarSetup
            }

            WelcomeEvent.SignInClicked -> {
                telemetry.signInFromWelcome()
                WelcomeEffect.OpenSignIn
            }

            WelcomeEvent.TermsClicked -> {
                telemetry.legalOpened(TERMS)
                WelcomeEffect.OpenTerms
            }

            WelcomeEvent.PrivacyClicked -> {
                telemetry.legalOpened(PRIVACY)
                WelcomeEffect.OpenPrivacy
            }
        }
        viewModelScope.launch { _effects.send(effect) }
    }

    private companion object {
        const val TERMS = "terms"
        const val PRIVACY = "privacy"
    }
}
