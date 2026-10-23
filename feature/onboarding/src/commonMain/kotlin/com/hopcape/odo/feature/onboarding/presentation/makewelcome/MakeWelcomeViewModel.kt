package com.hopcape.odo.feature.onboarding.presentation.makewelcome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.feature.onboarding.OnboardingConfig
import com.hopcape.odo.feature.onboarding.domain.usecase.LoadMakeChoicesUseCase
import com.hopcape.odo.feature.onboarding.presentation.OnboardingTelemetry
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** State holder for the v2 Welcome: loads the make tiles and turns a tap into setup. */
internal class MakeWelcomeViewModel(
    private val loadMakes: LoadMakeChoicesUseCase,
    private val config: OnboardingConfig,
    private val telemetry: OnboardingTelemetry,
) : ViewModel() {

    private val _state = MutableStateFlow(MakeWelcomeUiState(title = config.welcomeTitle.ifBlank { null }))
    val state: StateFlow<MakeWelcomeUiState> = _state.asStateFlow()

    private val _effects = Channel<MakeWelcomeEffect>(Channel.BUFFERED)
    val effects: Flow<MakeWelcomeEffect> = _effects.receiveAsFlow()

    init {
        telemetry.welcomeShown(variant = OnboardingTelemetry.Variant.FRESH)
        viewModelScope.launch {
            // The catalog is bundled, so a failed read only leaves the grid with "Other".
            val choices = telemetry.makesLoad { loadMakes() }
            if (choices != null) _state.update { it.copy(tiles = choices.tiles, allMakes = choices.all) }
        }
    }

    fun onEvent(event: MakeWelcomeEvent) {
        when (event) {
            is MakeWelcomeEvent.MakeTapped -> chooseMake(event.make, OnboardingTelemetry.MakeSource.TILE)
            MakeWelcomeEvent.OtherTapped -> {
                telemetry.makeOtherOpened()
                _state.update { it.copy(otherOpen = true) }
            }
            MakeWelcomeEvent.OtherDismissed -> _state.update { it.copy(otherOpen = false) }
            is MakeWelcomeEvent.OtherPicked -> {
                _state.update { it.copy(otherOpen = false) }
                chooseMake(event.make, OnboardingTelemetry.MakeSource.OTHER)
            }
            MakeWelcomeEvent.SignInClicked -> {
                telemetry.signInFromWelcome()
                emit(MakeWelcomeEffect.OpenSignIn)
            }
            MakeWelcomeEvent.TermsClicked -> {
                telemetry.legalOpened(TERMS)
                emit(MakeWelcomeEffect.OpenTerms)
            }
            MakeWelcomeEvent.PrivacyClicked -> {
                telemetry.legalOpened(PRIVACY)
                emit(MakeWelcomeEffect.OpenPrivacy)
            }
        }
    }

    private fun chooseMake(make: String, source: String) {
        val trimmed = make.trim()
        if (trimmed.isEmpty()) return
        telemetry.welcomeCompleted(makeSource = source, make = trimmed)
        emit(MakeWelcomeEffect.OpenSetup(trimmed))
    }

    private fun emit(effect: MakeWelcomeEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }

    private companion object {
        const val TERMS = "terms"
        const val PRIVACY = "privacy"
    }
}
