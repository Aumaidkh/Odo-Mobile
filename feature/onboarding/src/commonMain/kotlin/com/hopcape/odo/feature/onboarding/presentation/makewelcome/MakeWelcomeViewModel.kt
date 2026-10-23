package com.hopcape.odo.feature.onboarding.presentation.makewelcome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.core.domain.setup.SetupDraft
import com.hopcape.odo.core.domain.setup.SetupDraftRepository
import com.hopcape.odo.feature.onboarding.OnboardingConfig
import com.hopcape.odo.feature.onboarding.domain.usecase.LoadMakeChoicesUseCase
import com.hopcape.odo.feature.onboarding.presentation.OnboardingTelemetry
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * State holder for the v2 Welcome: loads the make tiles and turns a tap into setup. When an
 * unfinished setup draft exists it offers to carry on instead (S1r).
 */
internal class MakeWelcomeViewModel(
    private val loadMakes: LoadMakeChoicesUseCase,
    private val config: OnboardingConfig,
    private val drafts: SetupDraftRepository,
    private val clock: Clock,
    private val telemetry: OnboardingTelemetry,
) : ViewModel() {

    private val _state = MutableStateFlow(MakeWelcomeUiState(title = config.welcomeTitle.ifBlank { null }))
    val state: StateFlow<MakeWelcomeUiState> = _state.asStateFlow()

    private val _effects = Channel<MakeWelcomeEffect>(Channel.BUFFERED)
    val effects: Flow<MakeWelcomeEffect> = _effects.receiveAsFlow()

    /** The draft behind [MakeWelcomeUiState.resume], kept to name the step it resumes at. */
    private var draft: SetupDraft? = null

    init {
        viewModelScope.launch {
            draft = currentDraft()
            _state.update { it.copy(resume = draft?.toResume(), ready = true) }
            telemetry.welcomeShown(
                variant = if (draft == null) OnboardingTelemetry.Variant.FRESH else OnboardingTelemetry.Variant.RESUME,
            )
        }
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
            MakeWelcomeEvent.ContinueSetupClicked -> {
                val resumed = draft ?: return
                telemetry.resumed(step = if (resumed.modelName == null) STEP_MODEL else STEP_YEAR_FUEL)
                emit(MakeWelcomeEffect.OpenSetup(resumed.make, resume = true))
            }
            MakeWelcomeEvent.PickDifferentCarClicked -> {
                telemetry.resumeReset()
                draft = null
                _state.update { it.copy(resume = null) }
                viewModelScope.launch { drafts.clear() }
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

    /** A make tap starts a new draft, replacing any older one. */
    private fun chooseMake(make: String, source: String) {
        val trimmed = make.trim()
        if (trimmed.isEmpty()) return
        telemetry.welcomeCompleted(makeSource = source, make = trimmed)
        viewModelScope.launch {
            drafts.save(SetupDraft(make = trimmed, updatedAt = clock.now()))
            _effects.send(MakeWelcomeEffect.OpenSetup(trimmed))
        }
    }

    /** The saved draft, or null. A stale one is cleared and treated as absent. */
    private suspend fun currentDraft(): SetupDraft? {
        val saved = drafts.observe().first() ?: return null
        if (!saved.isStale(clock.now())) return saved
        drafts.clear()
        return null
    }

    private fun emit(effect: MakeWelcomeEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }

    private companion object {
        const val TERMS = "terms"
        const val PRIVACY = "privacy"
        const val STEP_MODEL = "model"
        const val STEP_YEAR_FUEL = "year_fuel"
    }
}

private fun SetupDraft.toResume(): ResumeDraft = ResumeDraft(make = make, model = modelName)
