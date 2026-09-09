package com.hopcape.odo.feature.questionnaire.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.core.domain.owner.model.QuestionKey
import com.hopcape.odo.core.domain.owner.repository.QuestionnaireRepository
import com.hopcape.odo.feature.questionnaire.QuestionRegistry
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Asks the questions named by [keys] and stores the answers.
 *
 * [keys] arrives as a constructor parameter from the navigation key, not a `SavedStateHandle`:
 * NavDisplay's entries have no `SavedStateRegistryOwner`, so asking for one throws.
 *
 * Answers already given are loaded first, so opening this from the profile screen shows what
 * the owner picked last time rather than a blank form.
 */
internal class QuestionnaireViewModel(
    private val keys: List<QuestionKey>,
    private val registry: QuestionRegistry,
    private val repository: QuestionnaireRepository,
    private val telemetry: QuestionnaireTelemetry,
) : ViewModel() {

    private val _state = MutableStateFlow(QuestionnaireUiState())
    val state: StateFlow<QuestionnaireUiState> = _state.asStateFlow()

    private val _effects = Channel<QuestionnaireEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        load()
    }

    fun onEvent(event: QuestionnaireEvent) = when (event) {
        is QuestionnaireEvent.OptionToggled -> onOptionToggled(event.key, event.value)
        QuestionnaireEvent.ContinueClicked -> onContinue()
        QuestionnaireEvent.BackClicked -> emit(QuestionnaireEffect.NavigateBack)
    }

    /**
     * Unknown keys are dropped by the registry rather than failing, so an older deep link asks
     * what it can. A read failure leaves the questions on screen with nothing pre-selected,
     * which is the same state a first-time owner sees.
     */
    private fun load() {
        val questions = registry.forKeys(keys)
        telemetry.opened(questions.map { it.key.value })
        viewModelScope.launch {
            val stored = questions.associate { question ->
                val answers = repository.answersFor(question.key).getOrNull().orEmpty()
                question.key to answers.map { it.value }.toSet()
            }
            _state.update { it.copy(questions = questions, answers = stored, isLoading = false) }
        }
    }

    private fun onOptionToggled(key: QuestionKey, value: String) {
        val mode = registry.find(key)?.selection ?: return
        _state.update { current ->
            current.copy(answers = current.answers + (key to current.selected(key).toggle(value, mode)))
        }
    }

    /**
     * Saves one question at a time and stops at the first failure, leaving the answers on
     * screen so the owner can retry. A partial save is not a problem here: each question is
     * stored independently, so the ones that landed stay landed.
     */
    private fun onContinue() {
        val current = _state.value
        if (!current.canContinue) return
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            for (question in current.questions) {
                val values = current.selected(question.key)
                val saved = repository.save(question.key, values)
                if (saved.isLeft()) {
                    saved.leftOrNull()?.let { telemetry.saveFailed(question.key.value, it) }
                    _state.update { it.copy(isSaving = false) }
                    emit(QuestionnaireEffect.SaveFailed)
                    return@launch
                }
                telemetry.answered(question.key.value, values.size)
            }
            telemetry.completed(current.questions.size)
            _state.update { it.copy(isSaving = false) }
            emit(QuestionnaireEffect.Finished)
        }
    }

    private fun emit(effect: QuestionnaireEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }
}
