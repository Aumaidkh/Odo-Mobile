package com.hopcape.odo.feature.support.presentation.idea

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.core.domain.support.FeatureIdea
import com.hopcape.odo.core.domain.support.FeatureIdeaRepository
import com.hopcape.odo.core.domain.support.IdeaStatus as DomainIdeaStatus
import com.hopcape.odo.core.domain.support.TicketKind
import com.hopcape.odo.feature.support.domain.usecase.CastIdeaVoteUseCase
import com.hopcape.odo.feature.support.domain.usecase.SubmitTicketUseCase
import com.hopcape.odo.feature.support.presentation.SupportTelemetry
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal sealed interface IdeaEffect {

    data object NavigateBack : IdeaEffect

    /** Sent. There is no confirmation screen for an idea — the list it joins is the answer. */
    data object Sent : IdeaEffect

    data object Failed : IdeaEffect

    /** The vote was not written. The pill snaps back on its own; this is what says why. */
    data object VoteFailed : IdeaEffect
}

/**
 * The idea box and the list beside it.
 *
 * The list is collected, not fetched once: a vote writes locally and the flow re-emits, so the
 * pill answers the tap without waiting for a server. A refresh is asked for on open and its
 * failure is swallowed — what is already cached is still worth showing, and an error over a
 * list of other people's ideas helps nobody.
 */
internal class SuggestIdeaViewModel(
    private val ideas: FeatureIdeaRepository,
    private val castVote: CastIdeaVoteUseCase,
    private val submit: SubmitTicketUseCase,
    private val telemetry: SupportTelemetry,
) : ViewModel() {

    private val _state = MutableStateFlow(IdeaUiState())
    val state: StateFlow<IdeaUiState> = _state.asStateFlow()

    private val _effects = Channel<IdeaEffect>(Channel.BUFFERED, BufferOverflow.DROP_OLDEST)
    val effects = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            ideas.observe().collect { list ->
                _state.update { it.copy(ideas = list.map(FeatureIdea::toRow)) }
            }
        }
        viewModelScope.launch {
            ideas.refresh().onLeft { telemetry.ideasRefreshFailed(it) }
        }
    }

    fun onEvent(event: IdeaEvent) {
        when (event) {
            IdeaEvent.BackClicked -> emit(IdeaEffect.NavigateBack)
            is IdeaEvent.TextChanged -> _state.update { it.copy(text = event.text) }
            is IdeaEvent.VoteToggled -> vote(event.id)
            IdeaEvent.SendClicked -> send()
        }
    }

    private fun vote(id: String) {
        val current = _state.value.ideas.firstOrNull { it.id == id } ?: return
        val wanted = !current.voted
        // The flow is what redraws the pill. Writing locally and letting the read answer is
        // one source of truth; setting the state here too would make two, and they drift.
        viewModelScope.launch {
            castVote(id, wanted).fold(
                // Counted after it is written, not before. A vote on the dashboard that was
                // never saved is a number nobody can act on.
                ifLeft = { error ->
                    telemetry.voteFailed(error)
                    emit(IdeaEffect.VoteFailed)
                },
                ifRight = { telemetry.ideaVoted(wanted) },
            )
        }
    }

    private fun send() {
        val current = _state.value
        // Two taps before the button redraws are two ideas in the queue.
        if (!current.canSend) return
        val text = current.text
        _state.update { it.copy(sending = true) }
        viewModelScope.launch {
            telemetry.timingSubmit { submit(kind = TicketKind.IDEA, body = text) }.fold(
                ifLeft = { error ->
                    telemetry.submitFailed(TicketKind.IDEA, error)
                    _state.update { it.copy(sending = false) }
                    emit(IdeaEffect.Failed)
                },
                ifRight = {
                    telemetry.ticketSubmitted(TicketKind.IDEA, attachments = 0, logsAttached = false)
                    _state.update { it.copy(sending = false, text = "") }
                    emit(IdeaEffect.Sent)
                },
            )
        }
    }

    private fun emit(effect: IdeaEffect) {
        _effects.trySend(effect)
    }
}

/** The domain's idea as the row draws it. One mapping, so the two cannot drift apart. */
private fun FeatureIdea.toRow(): IdeaRow = IdeaRow(
    id = id,
    title = title,
    status = when (status) {
        DomainIdeaStatus.UNDER_REVIEW -> IdeaStatus.UNDER_REVIEW
        DomainIdeaStatus.IN_PROGRESS -> IdeaStatus.IN_PROGRESS
        DomainIdeaStatus.SHIPPING -> IdeaStatus.SHIPPING
        DomainIdeaStatus.SHIPPED -> IdeaStatus.SHIPPED
    },
    votes = votes,
    voted = voted,
)
