package com.hopcape.odo.feature.onboarding.presentation.welcome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State holder for the pre-onboarding intro carousel. Holds [WelcomeUiState],
 * consumes [WelcomeEvent]s, and emits one-shot [WelcomeEffect]s.
 *
 * The carousel is purely informational — no domain, no persistence. Its only job is
 * to track which slide is showing and to decide, as data, when the intro is over
 * (finished on the last slide or skipped). Routing itself lives in the route host,
 * so this ViewModel never touches navigation or Compose types — mirroring
 * [com.hopcape.odo.feature.onboarding.presentation.OnboardingViewModel].
 *
 * Observability is delegated to [WelcomeTelemetry] behind intent-named calls, so this
 * file reads as pure carousel logic.
 */
internal class WelcomeViewModel(
    private val telemetry: WelcomeTelemetry,
) : ViewModel() {

    private val _state = MutableStateFlow(WelcomeUiState())
    val state: StateFlow<WelcomeUiState> = _state.asStateFlow()

    private val _effects = Channel<WelcomeEffect>(Channel.BUFFERED)
    val effects: Flow<WelcomeEffect> = _effects.receiveAsFlow()

    init {
        telemetry.welcomeShown()
    }

    fun onEvent(event: WelcomeEvent) {
        when (event) {
            is WelcomeEvent.PageChanged -> goToPage(event.index)
            WelcomeEvent.NextClicked -> onNext()
            WelcomeEvent.BackClicked -> goToPage(_state.value.currentIndex - 1)
            WelcomeEvent.SkipClicked -> onSkip()
        }
    }

    /** Advance to the next slide, or finish the carousel if already on the last. */
    private fun onNext() {
        val current = _state.value
        if (current.isLastPage) {
            telemetry.completed()
            finish()
        } else {
            goToPage(current.currentIndex + 1)
        }
    }

    private fun onSkip() {
        telemetry.skipped(_state.value.currentPage)
        finish()
    }

    /**
     * Move to [index], clamped to a valid slide so the pager can never overshoot.
     * A genuine change is a slide view — reported once (a no-op move is not).
     */
    private fun goToPage(index: Int) {
        val target = index.coerceIn(0, _state.value.pages.lastIndex)
        if (target == _state.value.currentIndex) return
        _state.update { it.copy(currentIndex = target) }
        telemetry.slideViewed(_state.value.currentPage, target)
    }

    private fun finish() {
        // Rest at the last slide so if the user backs out of the first setup step,
        // the retained carousel resumes on its final "Get Started" slide — not the
        // start — regardless of whether they finished it or skipped from mid-way.
        _state.update { it.copy(currentIndex = it.pages.lastIndex) }
        viewModelScope.launch { _effects.send(WelcomeEffect.NavigateToOnboarding) }
    }
}
