package com.hopcape.odo.feature.healthscore.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.car.ActiveCarProvider
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.health.model.HealthScore
import com.hopcape.odo.feature.healthscore.domain.model.HealthScoreSummary
import com.hopcape.odo.feature.healthscore.domain.usecase.ObserveHealthScoreUseCase
import com.hopcape.odo.feature.healthscore.domain.usecase.RecordHealthScoreUseCase
import com.hopcape.odo.feature.healthscore.presentation.state.Loadable
import com.hopcape.odo.feature.healthscore.presentation.state.valueOrNull
import com.hopcape.odo.feature.healthscore.resources.Res
import com.hopcape.odo.feature.healthscore.resources.hs_error_load_failed
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn

/**
 * State holder for the health-score detail. Holds [HealthScoreUiState], consumes
 * [HealthScoreEvent]s, and emits [HealthScoreEffect]s for the route host to navigate on.
 *
 * The car comes from [ActiveCarProvider] rather than a navigation key: the screen is
 * reached from Home's tile without naming a car, and every per-car surface answering
 * "which car?" for itself is how the app ends up opening someone else's.
 *
 * Recording history is a side effect of computing the score, and this is where it happens
 * because this is the only place the score is known. It is deliberately not part of the
 * read: [RecordHealthScoreUseCase] stores nothing when the score has not moved, and its
 * failures never reach the screen — the number on the dial is correct whether or not the
 * history behind it was kept, and the repository already reports a failed write.
 */
internal class HealthScoreViewModel(
    activeCar: ActiveCarProvider,
    observeHealthScore: ObserveHealthScoreUseCase,
    private val recordHealthScore: RecordHealthScoreUseCase,
    private val telemetry: HealthScoreTelemetry,
) : ViewModel() {

    private val _effects = Channel<HealthScoreEffect>(Channel.BUFFERED)
    val effects: Flow<HealthScoreEffect> = _effects.receiveAsFlow()

    /** Guards the opened event so a re-read does not count a second visit. */
    private var reportedOpen = false

    /**
     * The car's score.
     *
     * A failed read becomes [Loadable.Failed] rather than a zero: the local DB is the
     * source of truth, so a read that fails means the score is unknown, and a car that has
     * been maintained for a year does not deserve to be shown a 0 because a query broke.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<HealthScoreUiState> = activeCar.activeCarId
        .flatMapLatest { carId ->
            // No car yet means setup has not finished. There is nothing to score, and
            // nothing truthful to say about a car that does not exist.
            if (carId == null) {
                flowOf(HealthScoreUiState())
            } else {
                observeHealthScore(carId)
                    .onEach { summary -> keepHistory(carId, summary.score) }
                    .map { HealthScoreUiState(content = Loadable.Ready(it.toContent())) }
            }
        }
        .onEach(::reportOpened)
        .catch { cause ->
            telemetry.readFailed(cause)
            emit(HealthScoreUiState(content = Loadable.Failed(UiText(Res.string.hs_error_load_failed))))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
            initialValue = HealthScoreUiState(),
        )

    fun onEvent(event: HealthScoreEvent) = when (event) {
        HealthScoreEvent.BackTapped -> send(HealthScoreEffect.GoBack)

        HealthScoreEvent.InfoTapped -> {
            telemetry.infoOpened(currentScore())
            send(HealthScoreEffect.OpenInfo)
        }

        HealthScoreEvent.UnlockTapped -> {
            telemetry.unlockTapped(currentScore())
            send(HealthScoreEffect.OpenPaywall)
        }
    }

    /** Store the score if it has moved. A failed write is the repository's to report. */
    private suspend fun keepHistory(carId: CarId, score: HealthScore) {
        recordHealthScore(carId, score)
    }

    /**
     * The score the screen opened on, reported once per visit. The band is what matters in
     * aggregate; [HealthScoreContent.hasNothingLogged] separates a car in poor shape from
     * one Odo simply knows nothing about.
     */
    private fun reportOpened(state: HealthScoreUiState) {
        val content = state.content.valueOrNull ?: return
        if (reportedOpen) return
        reportedOpen = true
        telemetry.scoreOpened(
            score = content.score,
            band = content.band.name,
            isPro = content.isPro,
            hasNothingLogged = content.hasNothingLogged,
        )
    }

    private fun currentScore(): Int = state.value.content.valueOrNull?.score ?: 0

    private fun send(effect: HealthScoreEffect) {
        _effects.trySend(effect)
        Unit
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
    }
}

/** Domain summary to display state. Every decision about what to show is made here, once. */
private fun HealthScoreSummary.toContent(): HealthScoreContent = HealthScoreContent(
    score = score.total,
    band = score.band,
    note = note(),
    factors = score.factors,
    opportunity = score.biggestGap,
    isPro = isPro,
)

/**
 * What to say under the dial.
 *
 * A zero score is read as "nothing logged yet" rather than as a movement, because with no
 * evidence at all the number is a statement about the record, not about the car.
 */
private fun HealthScoreSummary.note(): HealthNote = when {
    score.total == 0 -> HealthNote.NothingLoggedYet
    delta == null -> HealthNote.NoHistoryYet
    else -> HealthNote.Delta(delta)
}
