package com.hopcape.odo.feature.onboarding.presentation.welcome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * State holder for the Welcome pitch. It holds no state (see [WelcomeEvent]) — it turns the
 * three things the owner can tap into [WelcomeEffect]s the route host performs.
 *
 * Thin on purpose, and still worth existing: it is the seam where "onboarding started"
 * analytics and the first-run marker belong, and having it means the screen never learns
 * what a destination is.
 */
internal class WelcomeViewModel : ViewModel() {

    private val _effects = Channel<WelcomeEffect>(Channel.BUFFERED)
    val effects: Flow<WelcomeEffect> = _effects.receiveAsFlow()

    fun onEvent(event: WelcomeEvent) {
        val effect = when (event) {
            WelcomeEvent.ContinueClicked -> WelcomeEffect.OpenCarSetup
            WelcomeEvent.TermsClicked -> WelcomeEffect.OpenTerms
            WelcomeEvent.PrivacyClicked -> WelcomeEffect.OpenPrivacy
        }
        viewModelScope.launch { _effects.send(effect) }
    }
}
